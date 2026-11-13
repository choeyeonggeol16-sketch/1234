#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <memory>
#include <mutex>
#include <new>
#include <thread>
#include <vector>
#if defined(__SANITIZE_ADDRESS__)
#include <sanitizer/asan_interface.h>
#define OBJECT_POISON(p,n) __asan_poison_memory_region(p,n)
#define OBJECT_UNPOISON(p,n) __asan_unpoison_memory_region(p,n)
#else
#define OBJECT_POISON(p,n) ((void)0)
#define OBJECT_UNPOISON(p,n) ((void)0)
#endif
// CSmartQueue is confirmed by0x74dce9/0x74dbd7. Names Alloc/Free and the
// owning ObjectAllocator type are adapter names;original symbol unconfirmed.
// Original starts with1 item/19 slots,doubles item capacity,and adds the new
// capacity to ring slots. At most18 concurrent queue operations;growth waits
// for them to leave. The adapter scans nonnull slots at growth, preserving
// returns when empty-pop tickets have passed the write cursor.
class CSmartQueue {
 struct Chunk{void* base;std::size_t bytes;};
 std::unique_ptr<std::atomic<void*>[]> slots_;
 std::vector<Chunk> chunks_;
 std::atomic<std::uint64_t> read_{~std::uint64_t(0)},write_{0};
 std::atomic<unsigned> users_{0};std::atomic<bool> exclusive_{false};
 std::mutex growth_;
 std::size_t itemCount_=0,capacity_=0,stride_;
 std::atomic<std::uint64_t> reserved_{0},grows_{0};
 bool enter()noexcept{
  for(;;){while(exclusive_.load(std::memory_order_acquire))std::this_thread::yield();
   auto n=users_.fetch_add(1,std::memory_order_acq_rel);
   if(n<18&&!exclusive_.load(std::memory_order_acquire))return true;
   users_.fetch_sub(1,std::memory_order_release);std::this_thread::yield();}
 }
 void leave()noexcept{users_.fetch_sub(1,std::memory_order_release);}
 bool CreateItems(std::size_t observed)noexcept{
  std::lock_guard<std::mutex> lock(growth_);exclusive_.store(true,std::memory_order_release);
  while(users_.load(std::memory_order_acquire))std::this_thread::yield();
  struct Unlock{std::atomic<bool>& value;~Unlock(){value.store(false,std::memory_order_release);}}unlock{exclusive_};
  if(capacity_!=observed)return true;
  const auto total=itemCount_?itemCount_*2:1,newCapacity=capacity_?capacity_+total:19;
  if(total<itemCount_||newCapacity<capacity_||total>std::numeric_limits<std::size_t>::max()/stride_)return false;
  std::size_t freeCount=0;for(std::size_t n=0;n<capacity_;++n)if(slots_[n].load(std::memory_order_relaxed))++freeCount;
  if(freeCount>=total)return true;
  const auto added=total-freeCount;
  try{
   auto ring=std::make_unique<std::atomic<void*>[]>(newCapacity);
   for(std::size_t n=0;n<newCapacity;++n)ring[n].store(nullptr,std::memory_order_relaxed);
   auto* chunk=std::malloc(added*stride_);if(!chunk)return false;
   try{chunks_.push_back({chunk,added*stride_});}catch(...){std::free(chunk);return false;}
   std::size_t index=0;
   // Preserve FIFO order of outstanding return tickets,then any slots made
   // unreachable by empty-pop tickets. Each raw block is present only once.
   const auto start=(read_.load(std::memory_order_relaxed)+1)%(capacity_?capacity_:1);
   for(std::size_t i=0;i<capacity_;++i){auto* p=slots_[(start+i)%capacity_].load(std::memory_order_relaxed);if(p)ring[index++].store(p,std::memory_order_relaxed);}
   for(std::size_t i=0;i<added;++i)ring[index++].store(static_cast<unsigned char*>(chunk)+i*stride_,std::memory_order_relaxed);
   // Every allocation's private prefix is constructed on first use,not by
   // interpreting previously uninitialized slab contents.
   for(std::size_t i=0;i<added;++i)std::memset(static_cast<unsigned char*>(chunk)+i*stride_,0,16);
   slots_=std::move(ring);capacity_=newCapacity;itemCount_=total;
   read_.store(~std::uint64_t(0),std::memory_order_relaxed);write_.store(index-1,std::memory_order_relaxed);
   reserved_.fetch_add(added*stride_,std::memory_order_relaxed);++grows_;return true;
  }catch(...){return false;}
 }
public:
 explicit CSmartQueue(std::size_t stride):stride_(stride){if(!CreateItems(0))throw std::bad_alloc();}
 ~CSmartQueue(){for(const auto& c:chunks_){OBJECT_UNPOISON(c.base,c.bytes);std::free(c.base);}}
 CSmartQueue(const CSmartQueue&)=delete;
 void* Alloc()noexcept{
  for(;;){enter();const auto observed=capacity_;void* result=nullptr;
   if(observed){const auto n=read_.fetch_add(1,std::memory_order_relaxed)+1;result=slots_[n%observed].exchange(nullptr,std::memory_order_acq_rel);}
   leave();if(result)return result;if(!CreateItems(observed))return nullptr;}
 }
 void Free(void* pointer)noexcept{
  enter();const auto n=write_.fetch_add(1,std::memory_order_relaxed)+1;auto& slot=slots_[n%capacity_];void* expected=nullptr;
  while(!slot.compare_exchange_weak(expected,pointer,std::memory_order_release,std::memory_order_relaxed)){expected=nullptr;std::this_thread::yield();}
  leave();
 }
 std::uint64_t reserved()const noexcept{return reserved_.load(std::memory_order_relaxed);}
 std::uint64_t grows()const noexcept{return grows_.load(std::memory_order_relaxed);}
};
class ObjectAllocator {
 struct alignas(16) Prefix{void* extension;std::uint32_t state;std::uint32_t bytes;};
 static_assert(sizeof(Prefix)==16&&offsetof(Prefix,bytes)==12);
 static constexpr std::uint32_t Live=0xc7100000,Mask=0xffff0000,Large=0xfffe,Aligned=0xfffd;
 struct Bucket{
  const std::size_t bytes;CSmartQueue queue;
  std::atomic<std::uint64_t> live{0},allocations{0},returns{0};
  explicit Bucket(std::size_t n):bytes(n),queue((n+12+15)&~std::size_t(15)){}
 };
 std::array<std::unique_ptr<Bucket>,49> pools_;
 std::array<std::uint8_t,32769> lookup_{};
 std::atomic<std::uint64_t> live_{0},bytes_{0},largeLive_{0},largeBytes_{0},allocations_{0},returns_{0},failures_{0},errors_{0};
 static Prefix* prefix(const void* p)noexcept{return reinterpret_cast<Prefix*>(reinterpret_cast<std::uintptr_t>(p)-16);}
 ObjectAllocator(){
  for(unsigned i=0;i<49;++i)pools_[i]=std::make_unique<Bucket>(ClassSize(i));
  unsigned index=0;for(unsigned n=0;n<=32768;++n){while(pools_[index]->bytes<n)++index;lookup_[n]=static_cast<std::uint8_t>(index);}
 }
public:
 static constexpr unsigned ReportWords=16+49*6;
 static constexpr std::uint64_t Marker=0x43544f4100010000ULL;
 static constexpr std::size_t ClassSize(unsigned i)noexcept{return std::size_t(4+(i&3))<<((i>>2)+1);}
 static ObjectAllocator& Instance(){static ObjectAllocator value;return value;}
 void* Alloc(std::size_t size,std::size_t alignment=16)noexcept{
  if(!size)size=1;if(size>std::numeric_limits<std::uint32_t>::max()-4||alignment<1||(alignment&(alignment-1))){++failures_;return nullptr;}
  const auto total=size+4;Prefix* h=nullptr;std::uint32_t kind=Large;
  if(alignment>16){
   if(alignment>std::numeric_limits<std::size_t>::max()-size-24){++failures_;return nullptr;}
   auto* raw=static_cast<unsigned char*>(std::malloc(size+24+alignment));if(!raw){++failures_;return nullptr;}
   auto address=(reinterpret_cast<std::uintptr_t>(raw)+24+alignment-1)&~(alignment-1);h=prefix(reinterpret_cast<void*>(address));
   reinterpret_cast<void**>(h)[-1]=raw;kind=Aligned;
  }else if(total<=32768){kind=lookup_[total];h=static_cast<Prefix*>(pools_[kind]->queue.Alloc());}
  else h=static_cast<Prefix*>(std::malloc(size+16));
  if(!h){++failures_;return nullptr;}
  OBJECT_UNPOISON(h+1,size);::new(static_cast<void*>(h))Prefix{nullptr,Live|kind,static_cast<std::uint32_t>(total)};
  ++live_;bytes_.fetch_add(total);++allocations_;
  if(kind<49){++pools_[kind]->live;++pools_[kind]->allocations;}else{++largeLive_;largeBytes_.fetch_add(total);}
  return h+1;
 }
 void Free(void* pointer)noexcept{
  if(!pointer)return;auto* h=prefix(pointer);const auto state=h->state;const auto kind=state&0xffffu,total=h->bytes;
  if((state&Mask)!=Live||total<5||(kind>=49&&kind!=Large&&kind!=Aligned)){++errors_;return;}
  h->state=0;h->extension=nullptr;--live_;bytes_.fetch_sub(total);++returns_;
  if(kind<49){--pools_[kind]->live;++pools_[kind]->returns;OBJECT_POISON(pointer,total-4);pools_[kind]->queue.Free(h);}
  else{--largeLive_;largeBytes_.fetch_sub(total);auto* raw=kind==Aligned?reinterpret_cast<void**>(h)[-1]:h;std::free(raw);}
 }
 static void*& Extension(void* p)noexcept{return prefix(p)->extension;}
 static std::uint32_t AllocationBytes(const void* p)noexcept{return prefix(p)->bytes;}
 void Snapshot(std::uint64_t* out)const noexcept{
  std::fill(out,out+ReportWords,0);out[0]=Marker;out[1]=live_;out[2]=bytes_;out[3]=largeLive_;out[4]=largeBytes_;out[5]=allocations_;out[6]=returns_;out[7]=failures_;out[8]=errors_;out[10]=49;out[11]=32768;out[12]=16;out[13]=6;
  for(unsigned i=0;i<49;++i){auto& b=*pools_[i];auto at=16+i*6;out[at]=b.bytes;out[at+1]=b.live;out[at+2]=b.allocations;out[at+3]=b.returns;out[at+4]=b.queue.reserved();out[at+5]=b.queue.grows();out[9]+=out[at+4];}
 }
};
class ObjectAllocated {
public:
 static void* operator new(std::size_t n){auto* p=ObjectAllocator::Instance().Alloc(n);if(!p)throw std::bad_alloc();return p;}
 static void* operator new(std::size_t n,const std::nothrow_t&)noexcept{try{return ObjectAllocator::Instance().Alloc(n);}catch(...){return nullptr;}}
 static void* operator new(std::size_t n,std::align_val_t alignment){auto* p=ObjectAllocator::Instance().Alloc(n,static_cast<std::size_t>(alignment));if(!p)throw std::bad_alloc();return p;}
 static void* operator new(std::size_t n,std::align_val_t alignment,const std::nothrow_t&)noexcept{try{return ObjectAllocator::Instance().Alloc(n,static_cast<std::size_t>(alignment));}catch(...){return nullptr;}}
 static void* operator new(std::size_t,void* p)noexcept{return p;}
 static void operator delete(void* p)noexcept{ObjectAllocator::Instance().Free(p);}
 static void operator delete(void* p,const std::nothrow_t&)noexcept{operator delete(p);}
 static void operator delete(void* p,std::size_t)noexcept{operator delete(p);}
 static void operator delete(void* p,std::align_val_t)noexcept{operator delete(p);}
 static void operator delete(void* p,std::align_val_t,const std::nothrow_t&)noexcept{operator delete(p);}
 static void operator delete(void* p,std::size_t,std::align_val_t)noexcept{operator delete(p);}
 static void operator delete(void*,void*)noexcept{}
};
