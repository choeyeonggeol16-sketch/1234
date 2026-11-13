#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <memory>
#include <new>
#include <thread>
#include <utility>
#include <vector>
#include <type_traits>
#include "reference_trace.hpp"
#include "object_allocator.hpp"
#if defined(_MSC_VER)
#include <source_location>
#define CIO_SOURCE_FILE std::source_location::current().file_name()
#define CIO_SOURCE_LINE std::source_location::current().line()
#else
#define CIO_SOURCE_FILE __builtin_FILE()
#define CIO_SOURCE_LINE __builtin_LINE()
#endif
constexpr std::size_t BufferBytes=8192, LargeBufferBytes=16384, MaxQueuedBytes=2u*1024u*1024u;
class MemoryObject : public ObjectAllocated {
 std::atomic<unsigned> references_{1},inTimer_{0};
 alignas(8) const std::uint32_t objectId_;
 inline static std::atomic<std::uint32_t> nextObjectId_{0};
 inline static std::atomic<std::uint64_t> referenceErrors_{0},lastReferenceError_{0},timerErrors_{0};
 friend struct MemoryObjectTestAccess;
 friend struct ObjectAbiTestAccess;
 void error(unsigned previous,bool timer=false)noexcept{
  lastReferenceError_.store(std::uint64_t(objectId_)<<32|previous,std::memory_order_relaxed);
  referenceErrors_.fetch_add(1,std::memory_order_release);if(timer)timerErrors_.fetch_add(1,std::memory_order_relaxed);
 }
protected:
 virtual ~MemoryObject()=default;
 MemoryObject* retainTracked(ReferenceTrace* trace,ObjectReferenceType type,const char* file,unsigned line)noexcept{
  references_.fetch_add(1,std::memory_order_relaxed);if(trace)trace->add(type,file,line);return this;
 }
 unsigned releaseTracked(ReferenceTrace* trace,ObjectReferenceType type,const char* file,unsigned line)noexcept{
  auto previous=references_.load(std::memory_order_relaxed);
  if(!previous||(previous==1&&inTimer_.load(std::memory_order_acquire))){error(previous,previous!=0);return previous;}
  // Update while this caller still owns its physical hold. Updating after the
  // decrement would allow a concurrent final release to destroy the trace.
  if(trace)trace->release(type,file,line);
  for(;;){
   if(!previous||(previous==1&&inTimer_.load(std::memory_order_acquire))){if(trace)trace->undoRelease(type);error(previous,previous!=0);return previous;}
   if(references_.compare_exchange_weak(previous,previous-1,std::memory_order_acq_rel,std::memory_order_relaxed))break;
  }
  auto remaining=previous-1;if(!remaining){void* storage=this;this->~MemoryObject();ObjectAllocator::Instance().Free(storage);}return remaining;
 }
public:
 MemoryObject()noexcept:objectId_(nextObjectId_.fetch_add(1,std::memory_order_relaxed)+1){}
 MemoryObject(const MemoryObject&)noexcept:MemoryObject(){}
 MemoryObject& operator=(const MemoryObject&)=delete;
 std::uint32_t objectId()const noexcept{return objectId_;}
 unsigned referenceCount()const noexcept{return references_.load(std::memory_order_acquire);}
 unsigned timerCount()const noexcept{return inTimer_.load(std::memory_order_acquire);}
 void linkTimer()noexcept{inTimer_.fetch_add(1,std::memory_order_release);}
 void unlinkTimer()noexcept{
  auto n=inTimer_.load(std::memory_order_relaxed);
  do{if(!n){error(referenceCount(),true);return;}}
  while(!inTimer_.compare_exchange_weak(n,n-1,std::memory_order_acq_rel,std::memory_order_relaxed));
 }
 // MSVC x64 slots0 destructor,1 retain,2 release,3 reference report.
 virtual MemoryObject* retain(const char* file,unsigned line,ObjectReferenceType type)noexcept{return retainTracked(nullptr,type,file,line);}
 virtual unsigned release(const char* file,unsigned line,ObjectReferenceType type,bool=false)noexcept{return releaseTracked(nullptr,type,file,line);}
 virtual void ReportReferences(const char*,unsigned)const noexcept{}
 MemoryObject* retain(ObjectReferenceType type=ObjectReferenceType::ORT_ANONYMOUS,const char* file=CIO_SOURCE_FILE,unsigned line=CIO_SOURCE_LINE)noexcept{return retain(file,line,type);}
 unsigned release(ObjectReferenceType type=ObjectReferenceType::ORT_ANONYMOUS,const char* file=CIO_SOURCE_FILE,unsigned line=CIO_SOURCE_LINE)noexcept{return release(file,line,type,false);}
 static void referenceDiagnostics(std::uint64_t* out)noexcept{
  out[0]=referenceErrors_.load(std::memory_order_acquire);auto last=lastReferenceError_.load(std::memory_order_relaxed);out[1]=last>>32;out[2]=last&0xffffffffu;
 }
 static std::uint64_t timerErrors()noexcept{return timerErrors_.load(std::memory_order_relaxed);}
};
template<class T>inline void retainReference(T* p,ObjectReferenceType type,const char* file,unsigned line)noexcept{
 if constexpr(std::is_base_of_v<MemoryObject,T>)p->retain(type,file,line);else p->retain();
}
template<class T>inline void releaseReference(T* p,ObjectReferenceType type,const char* file,unsigned line)noexcept{
 if constexpr(std::is_base_of_v<MemoryObject,T>)p->release(type,file,line);else p->release();
}
struct IOCompletionContext {
 inline static thread_local void* operation=nullptr;
 inline static thread_local unsigned error=0;
};
class CIOObject : public MemoryObject {
protected: ~CIOObject()override=default;
public:
 using CompletionPoster=bool(*)(CIOObject*,unsigned,OVERLAPPED*)noexcept;
 inline static std::atomic<CompletionPoster> completionPoster{nullptr};
 // The original12-slot CIOObject prefix. Wrapper names without original
 // signatures are adapter names,not recovered symbols.
 virtual void TimerExpired(int)noexcept{} //4
 virtual void OnTimerExpired(int message)noexcept{if(timerCount())unlinkTimer();TimerExpired(message);} //5
 virtual void TimerExpired2(int,std::int64_t)noexcept{} //6
 virtual void OnTimerExpired2(int message,std::int64_t value)noexcept{if(timerCount())unlinkTimer();TimerExpired2(message,value);} //7
 virtual void PostIOCompletion(unsigned bytes,OVERLAPPED* raw)noexcept{retain(ObjectReferenceType::ORT_IOCP_MESSAGE_RETAIN);auto fn=completionPoster.load();if(!fn||!fn(this,bytes,raw))release(ObjectReferenceType::ORT_IOCP_MESSAGE_RETAIN);} //8
 virtual void OnWaitCallback()noexcept{} //9
 virtual void OnIOCallback(int,unsigned message,OVERLAPPED* data)noexcept{ //10
  if(data)OnTimerExpired2(static_cast<int>(message),reinterpret_cast<std::intptr_t>(data));else OnTimerExpired(static_cast<int>(message));
  release(ObjectReferenceType::ORT_TIMER);
 }
 virtual void PostIOCompletion2(int message)noexcept{PostIOCompletion(static_cast<unsigned>(message),nullptr);} //11
 void PostIOCompletion(int message)noexcept{PostIOCompletion2(message);}
 // Preserve typed L1 completion/error context without adding a vtable slot.
 void OnIOCallback(void* operation,bool ok,unsigned bytes,unsigned error,OVERLAPPED* raw=nullptr)noexcept{
  auto* previous=IOCompletionContext::operation;auto oldError=IOCompletionContext::error;
  IOCompletionContext::operation=operation;IOCompletionContext::error=error;
  OnIOCallback(static_cast<int>(ok),bytes,raw?raw:static_cast<OVERLAPPED*>(operation));
  IOCompletionContext::operation=previous;IOCompletionContext::error=oldError;
 }
};
class ObjectRef {
 MemoryObject* p_;ObjectReferenceType type_;const char* file_;unsigned line_;
public:
 explicit ObjectRef(MemoryObject* p,bool add=true,ObjectReferenceType type=ObjectReferenceType::Count,const char* file=CIO_SOURCE_FILE,unsigned line=CIO_SOURCE_LINE)noexcept
 :p_(p),type_(type==ObjectReferenceType::Count?(add?ObjectReferenceType::ORT_FUNCTION_LOCAL:ObjectReferenceType::ORT_FIND_OBJECT):type),file_(file),line_(line){if(p_&&add)p_->retain(type_,file_,line_);}
 ~ObjectRef(){if(p_)p_->release(type_,file_,line_);}
 ObjectRef(const ObjectRef&)=delete;ObjectRef& operator=(const ObjectRef&)=delete;
};
class SpinLock {
 std::atomic_flag held_=ATOMIC_FLAG_INIT;
public:
 void lock()noexcept{unsigned spins=0;while(held_.test_and_set(std::memory_order_acquire))if(++spins%64==0)std::this_thread::yield();}
 void unlock()noexcept{held_.clear(std::memory_order_release);}
};
class SpinGuard {SpinLock& lock_;public:explicit SpinGuard(SpinLock& l)noexcept:lock_(l){lock_.lock();}~SpinGuard(){lock_.unlock();}};
struct CIOBuffer;
template<std::size_t Bytes> struct CIOBufferEx {
 std::array<unsigned char,16> prefix{};
 std::array<unsigned char,Bytes> payload{};
 std::uint32_t used=0,padding=0;
 CIOBuffer* next=nullptr;
 std::atomic<unsigned> references{0};
 std::array<unsigned char,16> reserved{};
 unsigned owner=0;
};
static_assert(offsetof(CIOBufferEx<8192>,used)==0x2010);
static_assert(offsetof(CIOBufferEx<8192>,next)==0x2018);
static_assert(offsetof(CIOBufferEx<8192>,references)==0x2020);
static_assert(offsetof(CIOBufferEx<8192>,owner)==0x2034);
static_assert(sizeof(CIOBufferEx<8192>)==0x2038);
static_assert(sizeof(CIOBufferEx<16384>)==0x4038);
// Views add the adapter's partial-send offset without changing the recovered
// slab stride or payload/length/next/ref/owner field positions.
struct CIOBuffer {
 struct Payload{unsigned char* bytes;unsigned char* data()noexcept{return bytes;}const unsigned char* data()const noexcept{return bytes;}}data;
 std::atomic<unsigned>& references;
 std::uint32_t& used;std::size_t offset=0;CIOBuffer*& next;
 unsigned& owner;unsigned bucket;const std::size_t capacity;bool quarantined=false;
 template<std::size_t Bytes> CIOBuffer(CIOBufferEx<Bytes>& raw,unsigned id,unsigned family)
 :data{raw.payload.data()},references(raw.references),used(raw.used),next(raw.next),owner(raw.owner),bucket(family),capacity(Bytes){owner=id;}
};
// 0x6b5094/0x6b4fb8 allocate zeroed contiguous slabs plus pointer rings.
// Pop never grows the pool. CAS-serialized returns go to the stamped owner.
class CIOBufferPool {
 static constexpr unsigned NativeOwners=64,OwnerCount=128;
 struct Ring{
  SpinLock gate;std::unique_ptr<CIOBuffer*[]> slots;
  std::unique_ptr<CIOBufferEx<8192>[]>small;
  std::unique_ptr<CIOBufferEx<16384>[]>large;
  std::vector<CIOBuffer> views;
  std::uint64_t read=0,write=0,quarantined=0;std::size_t capacity=0;
 };
 std::unique_ptr<Ring[]>rings_{new Ring[OwnerCount*2]};
 std::atomic<std::size_t>allocated_{0};const std::size_t limit_;const std::uint64_t generation_;
 std::atomic<unsigned>external_{0};inline static std::atomic<std::uint64_t>nextGeneration_{1};
 struct Binding{std::uint64_t generation;unsigned owner;};
 inline static thread_local Binding binding_{0,0};
 inline static thread_local std::array<Binding,8>externalBindings_{};
 inline static thread_local unsigned externalCacheCursor_=0;
 std::size_t foreign8_=32,foreign16_=32;
 std::atomic<std::uint64_t>exhaustions_{0},initializationFailures_{0},corruptions_{0};
 unsigned owner()noexcept{
  if(binding_.generation==generation_)return binding_.owner;
  for(const auto& cached:externalBindings_)if(cached.generation==generation_)return cached.owner;
  Binding registered{generation_,NativeOwners+external_.fetch_add(1)%NativeOwners};
  externalBindings_[externalCacheCursor_++%externalBindings_.size()]=registered;return registered.owner;
 }
 bool initializeRing(Ring& ring,unsigned id,unsigned family,std::size_t count)noexcept{
  if(ring.capacity)return true;
  if(!count||count>limit_){++initializationFailures_;return false;}
  auto total=allocated_.load();
  do{if(count>limit_-total){++initializationFailures_;return false;}}while(!allocated_.compare_exchange_weak(total,total+count));
  try{
   ring.slots.reset(new CIOBuffer*[count]{});ring.views.reserve(count);
   if(family){ring.large.reset(new CIOBufferEx<16384>[count]);for(std::size_t i=0;i<count;++i)ring.views.emplace_back(ring.large[i],id,family);}
   else{ring.small.reset(new CIOBufferEx<8192>[count]);for(std::size_t i=0;i<count;++i)ring.views.emplace_back(ring.small[i],id,family);}
   for(std::size_t i=0;i<count;++i)ring.slots[i]=&ring.views[i];
   ring.capacity=count;return true;
  }catch(...){ring.views.clear();ring.slots.reset();ring.small.reset();ring.large.reset();allocated_.fetch_sub(count);++initializationFailures_;return false;}
 }
public:
 class OwnerScope{Binding saved_;public:OwnerScope(CIOBufferPool& p,unsigned o)noexcept:saved_(binding_){binding_={p.generation_,o%NativeOwners};}~OwnerScope(){binding_=saved_;}};
 explicit CIOBufferPool(std::size_t limit):limit_(limit),generation_(nextGeneration_.fetch_add(1)){}
 bool initializeOwner(unsigned id,std::size_t small,std::size_t large)noexcept{
  if(id>=NativeOwners||(!small&&!large))return false;
  for(unsigned family=0;family<2;++family){if(!(family?large:small))continue;auto& ring=rings_[id*2+family];SpinGuard lock(ring.gate);
   if(!initializeRing(ring,id,family,family?large:small))return false;}
  return true;
 }
 bool initialize(unsigned workers,std::size_t total16)noexcept{
  if(!workers||workers>NativeOwners)return false;
  // Original native 8K pools:300/owner. Configured total16 / worker count.
  // Original owner17 setup:8K=300,16K=200; use it for external producers.
  foreign8_=300;foreign16_=200;
  for(unsigned id=0;id<workers;++id)for(unsigned family=0;family<2;++family){
   auto& ring=rings_[id*2+family];SpinGuard lock(ring.gate);
   if(!initializeRing(ring,id,family,family?std::max<std::size_t>(1,total16/workers):300))return false;
  }
  return true;
 }
 CIOBuffer* acquire(std::size_t bytes=BufferBytes)noexcept{
  if(bytes!=BufferBytes&&bytes!=LargeBufferBytes)return nullptr;
  unsigned id=owner(),family=bytes==LargeBufferBytes?1:0;auto& r=rings_[id*2+family];SpinGuard hold(r.gate);
  // External thread registration initializes a complete fixed-size ring once.
  if(!r.capacity&&!initializeRing(r,id,family,std::min(limit_,family?foreign16_:foreign8_)))return nullptr;
  while(r.read-r.write<r.capacity){
   auto& slot=r.slots[r.read%r.capacity];auto* p=slot;
   if(!p){++corruptions_;return nullptr;}
   slot=nullptr;++r.read;
   if(p->references.load()!=0||p->used||p->next||p->quarantined){
    // Original Alloc consumes invalid nonnull entries and tries the next.
    // A late release must never put a discarded block back in circulation.
    p->quarantined=true;++r.quarantined;++corruptions_;continue;
   }
   p->owner=id;p->offset=0;p->references.store(1,std::memory_order_release);return p;
  }
  ++exhaustions_;return nullptr;
 }
 static void retain(CIOBuffer* p)noexcept{if(p)p->references.fetch_add(1,std::memory_order_relaxed);}
 void recycle(CIOBuffer* p)noexcept{
  if(!p)return;auto previous=p->references.load(std::memory_order_relaxed);
  do{if(previous==0){++corruptions_;return;}}
  while(!p->references.compare_exchange_weak(previous,previous-1,std::memory_order_acq_rel,std::memory_order_relaxed));
  if(previous!=1)return;
  if(p->owner>=OwnerCount||p->bucket>1){++corruptions_;return;}
  auto& r=rings_[p->owner*2+p->bucket];SpinGuard hold(r.gate);
  if(p->quarantined)return;
  p->used=0;p->offset=0;p->next=nullptr;
  if(!r.capacity){++corruptions_;return;}
  auto& slot=r.slots[r.write%r.capacity];
  if(r.write>=r.read||slot){++corruptions_;return;}
  slot=p;++r.write;
 }
 unsigned currentOwner()noexcept{return owner();}
 std::size_t allocated()const noexcept{return allocated_.load();}
 std::uint64_t exhaustions()const noexcept{return exhaustions_.load();}
 std::uint64_t initializationFailures()const noexcept{return initializationFailures_.load();}
 std::uint64_t corruptions()const noexcept{return corruptions_.load();}
 // Snapshot existing ring positions; no extra counter on the I/O hot path.
 // Per family: capacity, borrowed blocks, cumulative acquire, final return.
 void poolSnapshot(std::uint64_t* out,std::uint64_t* quarantined=nullptr)noexcept{
  std::fill(out,out+9,0);
  if(quarantined)std::fill(quarantined,quarantined+2,0);
  for(unsigned id=0;id<OwnerCount;++id)for(unsigned family=0;family<2;++family){
   auto& r=rings_[id*2+family];SpinGuard hold(r.gate);auto* f=out+family*4;
   f[0]+=r.capacity;f[1]+=r.read-r.write-r.quarantined;f[2]+=r.read-r.quarantined;f[3]+=r.write;
   if(quarantined)quarantined[family]+=r.quarantined;
   out[8]+=r.capacity*(family?sizeof(CIOBufferEx<16384>):sizeof(CIOBufferEx<8192>));
  }
 }
};
// Observed read path: shared -> copy remaining bytes, unique -> compact in place.
class ReceiveWindow {
 CIOBufferPool& pool_;CIOBuffer* block_=nullptr;const std::size_t capacity_;
public:
 ReceiveWindow(CIOBufferPool& p,std::size_t c)noexcept:pool_(p),capacity_(c){}
 ~ReceiveWindow(){clear();}
 void clear()noexcept{if(block_)pool_.recycle(block_);block_=nullptr;}CIOBuffer* block()const noexcept{return block_;}
 bool prepare()noexcept{
  if(!block_){block_=pool_.acquire(capacity_);return block_!=nullptr;}
  auto remaining=block_->used-block_->offset;
  if(block_->references.load(std::memory_order_acquire)>1){
   auto* next=pool_.acquire(capacity_);if(!next)return false;
   if(remaining)std::memcpy(next->data.data(),block_->data.data()+block_->offset,remaining);
   next->used=remaining;pool_.recycle(block_);block_=next;
  }else{
   if(remaining&&block_->offset)std::memmove(block_->data.data(),block_->data.data()+block_->offset,remaining);
   block_->used=remaining;block_->offset=0;
  }
  return block_->used<block_->capacity;
 }
 bool append(std::size_t n)noexcept{if(!block_||n>block_->capacity-block_->used)return false;block_->used+=n;return true;}
 void consume(std::size_t n)noexcept{block_->offset+=std::min(n,block_->used-block_->offset);}
};
struct Span{const unsigned char* data;std::size_t size;};
class SendChain {
 CIOBuffer* head_=nullptr;CIOBuffer* tail_=nullptr;std::size_t bytes_=0,blockBytes_;
public:
 explicit SendChain(std::size_t bytes=BufferBytes)noexcept:blockBytes_(bytes){}
 SendChain(const SendChain&)=delete;SendChain& operator=(const SendChain&)=delete;
 CIOBuffer* head()const noexcept{return head_;}std::size_t size()const noexcept{return bytes_;}
 // Transfer a prepared chain without copying encrypted payloads or changing
 // ownership of the in-flight head. Both chains use the same server pool.
 bool adopt(CIOBufferPool& pool,SendChain& source)noexcept{
  if(source.bytes_>MaxQueuedBytes-bytes_)return false;
  if(!source.head_)return true;
  auto* first=source.head_;auto* last=source.tail_;
  if(tail_&&tail_!=head_&&first->used<=tail_->capacity-tail_->used){
   std::memcpy(tail_->data.data()+tail_->used,first->data.data(),first->used);tail_->used+=first->used;
   auto* merged=first;first=first->next;if(last==merged)last=tail_;pool.recycle(merged);
  }
  if(first){if(tail_)tail_->next=first;else head_=first;tail_=last;}
  bytes_+=source.bytes_;source.head_=source.tail_=nullptr;source.bytes_=0;return true;
 }
 class Cursor {
  CIOBuffer* block_;std::size_t at_=0;
 public:
  explicit Cursor(SendChain& chain)noexcept:block_(chain.head_){}
  unsigned char* contiguous(std::size_t n)noexcept{
   return block_&&n<=block_->used-at_?block_->data.data()+at_:nullptr;
  }
  void advance(std::size_t n)noexcept{
   while(n){const auto count=std::min(n,static_cast<std::size_t>(block_->used)-at_);at_+=count;n-=count;
    if(at_==block_->used){block_=block_->next;at_=0;}}
  }
  void copy(const unsigned char* source,std::size_t n)noexcept{
   while(n){const auto count=std::min(n,static_cast<std::size_t>(block_->used)-at_);
    std::memcpy(block_->data.data()+at_,source,count);source+=count;n-=count;advance(count);}
  }
 };
 bool append(CIOBufferPool& pool,const Span* spans,std::size_t count)noexcept{
  std::size_t total=0;for(std::size_t i=0;i<count;++i){if((!spans[i].data&&spans[i].size)||spans[i].size>MaxQueuedBytes-total)return false;total+=spans[i].size;}
  if(total>MaxQueuedBytes-bytes_)return false;if(!total)return true;
  CIOBuffer* first=nullptr;CIOBuffer* last=nullptr;
  for(std::size_t i=0;i<count;++i){std::size_t at=0;while(at<spans[i].size){
   if(!last||last->used==last->capacity){auto* p=pool.acquire(blockBytes_);if(!p){while(first){auto* next=first->next;pool.recycle(first);first=next;}return false;}
    if(last)last->next=p;else first=p;last=p;}
   auto n=std::min(last->capacity-last->used,spans[i].size-at);std::memcpy(last->data.data()+last->used,spans[i].data+at,n);last->used+=n;at+=n;
  }}
  if(tail_&&tail_!=head_&&first->used<=tail_->capacity-tail_->used){std::memcpy(tail_->data.data()+tail_->used,first->data.data(),first->used);tail_->used+=first->used;
   auto* merged=first;first=first->next;if(last==merged)last=tail_;pool.recycle(merged);}
  if(first){if(tail_)tail_->next=first;else head_=first;tail_=last;}bytes_+=total;return true;
 }
 bool complete(std::size_t n,CIOBuffer*& retired)noexcept{
  retired=nullptr;if(!head_||n==0||n>head_->used-head_->offset)return false;bytes_-=n;head_->offset+=n;
  if(head_->offset==head_->used){retired=head_;head_=head_->next;retired->next=nullptr;if(!head_)tail_=nullptr;}return true;
 }
 void clear(CIOBufferPool& pool)noexcept{while(head_){auto* next=head_->next;pool.recycle(head_);head_=next;}tail_=nullptr;bytes_=0;}
};
struct IoState {
 bool closeRetiring=false,closing=false,readPosted=false,writePosted=false,readDelivering=false,writeDelivering=false,acceptDelivering=true,closeQueued=false,paused=true;
 bool canRead()const noexcept{return !closing&&!paused&&!readPosted&&!readDelivering&&!acceptDelivering;}
 bool canWrite()const noexcept{return !closing&&!writePosted&&!writeDelivering&&!acceptDelivering;}
 bool canClose()const noexcept{return closing&&!closeRetiring&&!readPosted&&!writePosted&&!readDelivering&&!writeDelivering&&!acceptDelivering&&!closeQueued;}
};
