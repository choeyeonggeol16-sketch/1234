#pragma once
#include "cio_core.hpp"
#include <chrono>
#include <mutex>
#include <unordered_map>
#include <vector>
inline std::uint64_t monotonicMs()noexcept{return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();}
// Confirmed signature: CMwmrQueue<CThreadLocalTimer::CSlot,4009>::Pop.
// A single queue replaces the former TimerBlockPool ring. Storage budget and
// cancellation positions remain L1 adapter data, not recovered class layout.
template<class T,std::size_t Capacity> class CMwmrQueue {
 struct Position{unsigned live=0,read=0;bool leased=false;};
 static std::size_t checked(std::size_t n){if(!n||n>Capacity)throw std::bad_alloc();return n;}
 const std::size_t capacity_;
 SpinLock gate_;std::unique_ptr<T[]>storage_;
 std::unique_ptr<T*[]>ring_;std::unique_ptr<Position[]>positions_;
 std::uint64_t read_=0,write_=0,errors_=0;
 bool indexOf(T* p,std::size_t& index)const noexcept{
  auto address=reinterpret_cast<std::uintptr_t>(p),base=reinterpret_cast<std::uintptr_t>(storage_.get());
  if(address<base||address-base>=capacity_*sizeof(T)||(address-base)%sizeof(T))return false;
  index=(address-base)/sizeof(T);return true;
 }
public:
 explicit CMwmrQueue(std::size_t capacity=Capacity):capacity_(checked(capacity)),storage_(new T[capacity_]),ring_(new T*[Capacity]{}),positions_(new Position[capacity_]),write_(capacity_){
  for(std::size_t i=0;i<capacity_;++i)ring_[i]=&storage_[i];
 }
 // Internal cancellation metadata is accessed only for a currently leased slot.
 Position& position(T* p)noexcept{return positions_[p-storage_.get()];}
 T* Pop()noexcept{
  SpinGuard lock(gate_);if(read_==write_)return nullptr;
  auto& slot=ring_[read_%Capacity];auto* block=slot;std::size_t index;
  if(!indexOf(block,index)||positions_[index].leased){++errors_;return nullptr;}
  slot=nullptr;++read_;positions_[index]={0,0,true};return block;
 }
 // Return is an adapter name. The binary confirms the corresponding routine
 // at0x925630, but does not disclose its original C++ method name.
 bool Return(T* p)noexcept{
  SpinGuard lock(gate_);std::size_t index;
  if(!indexOf(p,index)||!positions_[index].leased||write_-read_>=capacity_||ring_[write_%Capacity]){++errors_;return false;}
  ring_[write_%Capacity]=p;++write_;positions_[index]={};return true;
 }
 std::size_t inUse()noexcept{SpinGuard lock(gate_);return static_cast<std::size_t>(capacity_-(write_-read_));}
 std::uint64_t faults()noexcept{SpinGuard lock(gate_);return errors_;}
};
// Original32-byte deadline/object/message/argument record; type name unconfirmed.
struct TimerHeapRecord {
 std::uint64_t deadline;CIOObject* object;std::uint32_t message,padding=0;std::uintptr_t argument;
};
static_assert(sizeof(TimerHeapRecord)==32);
// 0x925374: owner17 posts every timer; others run100 inline then post one.
// The binary proves the owner value, not the business role of that thread.
class TimerDispatchPolicy {
 unsigned counter_=0;
public:
 bool post(unsigned owner)noexcept{
  if(owner==17)return true;
  if(counter_++==100){counter_=0;return true;}
  return false;
 }
};
class CThreadLocalTimer {
public:
 // Signature at0xd75420 names this nested type; fields are verified at0x92533c.
 struct CSlot {
  std::array<CIOObject*,1000>objects{};
  std::array<std::uint32_t,1000>messages{};
  std::array<std::uintptr_t,1000>arguments{};
  std::uint32_t count=0;CSlot* next=nullptr;
 };
 using SlotPool=CMwmrQueue<CSlot,4009>;
private:
 static constexpr std::uint64_t Tick=50,Slots=200;
 struct Entry{
  std::uint64_t token,tick;CIOObject* object;std::uint32_t message=0;bool heap=false;std::size_t index=0;
  CSlot* block=nullptr;unsigned slot=0;
 };
 std::mutex gate_;std::uint64_t cursor_;std::size_t near_=0;
 std::unique_ptr<SlotPool>ownedPool_;SlotPool* pool_;
 std::array<CSlot*,Slots>buckets_{};
 std::vector<TimerHeapRecord>heap_;
 std::unordered_map<std::uint64_t,std::unique_ptr<Entry>>entries_;
 static bool before(const TimerHeapRecord& a,const TimerHeapRecord& b)noexcept{return a.deadline<b.deadline||(a.deadline==b.deadline&&a.argument<b.argument);}
 void swapHeap(std::size_t a,std::size_t b)noexcept{
  std::swap(heap_[a],heap_[b]);entries_.find(heap_[a].argument)->second->index=a;entries_.find(heap_[b].argument)->second->index=b;
 }
 void up(std::size_t i)noexcept{while(i&&before(heap_[i],heap_[(i-1)/2])){auto p=(i-1)/2;swapHeap(i,p);i=p;}}
 void down(std::size_t i)noexcept{for(;;){auto c=i*2+1;if(c>=heap_.size())return;if(c+1<heap_.size()&&before(heap_[c+1],heap_[c]))++c;
  if(!before(heap_[c],heap_[i]))return;swapHeap(c,i);i=c;}}
 void removeHeap(std::size_t i)noexcept{
  swapHeap(i,heap_.size()-1);heap_.pop_back();
  if(i<heap_.size()){if(i&&before(heap_[i],heap_[(i-1)/2]))up(i);else down(i);}
 }
 void compact(CSlot* block)noexcept{
  unsigned out=0;
  for(unsigned i=0;i<block->count;++i)if(block->objects[i]){
   auto token=block->arguments[i];auto* p=entries_.find(token)->second.get();
   block->objects[out]=block->objects[i];block->messages[out]=block->messages[i];block->arguments[out]=token;p->slot=out++;
  }
  block->count=out;pool_->position(block).read=0;
 }
 void appendBucket(Entry* p){
  auto*& head=buckets_[p->tick%Slots];auto* block=head;
  if(block->count==1000&&pool_->position(block).live<1000)compact(block);
  if(block->count==1000){block=pool_->Pop();if(!block)throw std::bad_alloc();block->count=0;block->next=head;head=block;}
  auto at=block->count++;block->objects[at]=p->object;block->messages[at]=p->message;block->arguments[at]=p->token;
  ++pool_->position(block).live;p->block=block;p->slot=at;p->heap=false;++near_;
 }
 void removeBucket(Entry* p)noexcept{
  auto* block=p->block;block->objects[p->slot]=nullptr;--near_;auto& state=pool_->position(block);
  if(--state.live)return;
  auto*& head=buckets_[p->tick%Slots];
  if(head==block&&!block->next){block->count=0;state.read=0;return;}
  auto** link=&head;while(*link!=block)link=&(*link)->next;*link=block->next;pool_->Return(block);
 }
 void place(Entry* p){
  if(p->tick<cursor_+Slots)appendBucket(p);
  else{p->heap=true;p->index=heap_.size();heap_.push_back({p->tick*Tick,p->object,p->message,0,p->token});up(p->index);}
 }
public:
 explicit CThreadLocalTimer(std::uint64_t now=monotonicMs(),SlotPool* shared=nullptr):cursor_(now/Tick),ownedPool_(shared?nullptr:new SlotPool(Slots+409)),pool_(shared?shared:ownedPool_.get()){
  try{for(auto& head:buckets_){head=pool_->Pop();if(!head)throw std::bad_alloc();head->count=0;head->next=nullptr;}}
  catch(...){for(auto* head:buckets_)if(head)pool_->Return(head);throw;}
 }
 ~CThreadLocalTimer(){
  for(auto& item:entries_){item.second->object->unlinkTimer();item.second->object->release(ObjectReferenceType::ORT_TIMER);}
  for(auto* head:buckets_)while(head){auto* next=head->next;pool_->Return(head);head=next;}
 }
 // Original AddTimer(object,deadline,message,argument) order. bool admission,
 // uint64 monotonic time and cancellation key are the existing L1 contract.
 bool AddTimer(CIOObject* object,std::uint64_t deadline,int message,std::uint64_t argument)noexcept{
  if(!object)return false;const auto token=argument;
  std::lock_guard<std::mutex> lock(gate_);if(entries_.size()>=65536||entries_.find(token)!=entries_.end())return false;
  try{
   auto entry=std::make_unique<Entry>();entry->token=token;entry->object=object;entry->message=static_cast<std::uint32_t>(message);entry->tick=std::max(cursor_,deadline/Tick+(deadline%Tick!=0));auto* p=entry.get();
   entries_.emplace(token,std::move(entry));try{place(p);}catch(...){entries_.erase(token);return false;}
   object->retain(ObjectReferenceType::ORT_TIMER);object->linkTimer();return true;
  }catch(...){return false;}
 }
 CIOObject* cancel(std::uint64_t token)noexcept{
  std::lock_guard<std::mutex> lock(gate_);auto found=entries_.find(token);if(found==entries_.end())return nullptr;
  auto* p=found->second.get();auto* object=p->object;if(p->heap)removeHeap(p->index);else removeBucket(p);entries_.erase(found);object->unlinkTimer();return object;
 }
 CIOObject* takeDue(std::uint64_t now)noexcept{
  std::lock_guard<std::mutex> lock(gate_);auto end=now/Tick;
  while(cursor_<=end){
   if(entries_.empty()){cursor_=end+1;return nullptr;}
   if(!near_&&!heap_.empty()&&heap_.front().deadline/Tick>=cursor_+Slots)cursor_=std::min(end+1,heap_.front().deadline/Tick-(Slots-1));
   if(cursor_>end)return nullptr;
   while(!heap_.empty()&&heap_.front().deadline/Tick<cursor_+Slots){
    auto* p=entries_.find(heap_.front().argument)->second.get();auto old=p->index;
    try{appendBucket(p);}catch(...){return nullptr;}removeHeap(old);
   }
   auto* head=buckets_[cursor_%Slots];auto& position=pool_->position(head);
   if(position.live){
    while(position.read<head->count&&!head->objects[position.read])++position.read;
    auto token=head->arguments[position.read++];auto found=entries_.find(token);auto* p=found->second.get();auto* object=p->object;
    removeBucket(p);entries_.erase(found);object->unlinkTimer();return object;
   }
   ++cursor_;
  }
  return nullptr;
 }
 // Dispatch is confirmed by both original signature overloads. The callback
 // supplies L1 NativeTask execution; removal still precedes external callbacks.
 template<class Execute> void Dispatch(std::uint64_t now,Execute&& execute)noexcept{
  while(auto* due=takeDue(now)){execute(due);due->release(ObjectReferenceType::ORT_TIMER);}
 }
 std::size_t size()noexcept{std::lock_guard<std::mutex> lock(gate_);return entries_.size();}
 std::size_t blocksInUse()noexcept{return pool_->inUse();}
};

static_assert(offsetof(CThreadLocalTimer::CSlot,messages)==0x1f40);
static_assert(offsetof(CThreadLocalTimer::CSlot,arguments)==0x2ee0);
static_assert(offsetof(CThreadLocalTimer::CSlot,count)==0x4e20);
static_assert(offsetof(CThreadLocalTimer::CSlot,next)==0x4e28&&sizeof(CThreadLocalTimer::CSlot)==0x4e30);
