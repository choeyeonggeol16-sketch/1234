#pragma once
#include "cio_core.hpp"
#include <chrono>
#include <list>
#include <mutex>
#include <unordered_map>
#include <vector>
namespace ctransport {
inline std::uint64_t monotonicMs()noexcept{
 return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
}
// Recovered at 0x926844: per-owner 200 slots, 50ms increments, an overflow
// heap for deadlines outside the wheel horizon. Indexed cancellation and a
// 64-bit monotonic clock adapt this contract to external FFM callers.
class TimerWheel {
 static constexpr std::uint64_t Tick=50,Slots=200;
 struct Entry{
  std::uint64_t token,tick;CIOObject* object;bool heap=false;std::size_t index=0;
  std::list<Entry*>::iterator link;
 };
 std::mutex gate_;std::uint64_t cursor_;std::size_t near_=0;
 std::array<std::list<Entry*>,Slots>buckets_;
 std::vector<Entry*>heap_;
 std::unordered_map<std::uint64_t,std::unique_ptr<Entry>>entries_;
 static bool before(Entry* a,Entry* b)noexcept{return a->tick<b->tick||(a->tick==b->tick&&a->token<b->token);}
 void swapHeap(std::size_t a,std::size_t b)noexcept{std::swap(heap_[a],heap_[b]);heap_[a]->index=a;heap_[b]->index=b;}
 void up(std::size_t i)noexcept{while(i&&before(heap_[i],heap_[(i-1)/2])){auto p=(i-1)/2;swapHeap(i,p);i=p;}}
 void down(std::size_t i)noexcept{for(;;){auto c=i*2+1;if(c>=heap_.size())return;if(c+1<heap_.size()&&before(heap_[c+1],heap_[c]))++c;
  if(!before(heap_[c],heap_[i]))return;swapHeap(c,i);i=c;}}
 void unlink(Entry* p)noexcept{
  if(!p->heap){buckets_[p->tick%Slots].erase(p->link);--near_;return;}
  auto i=p->index;swapHeap(i,heap_.size()-1);heap_.pop_back();
  if(i<heap_.size()){if(i&&before(heap_[i],heap_[(i-1)/2]))up(i);else down(i);}
 }
 void place(Entry* p){
  if(p->tick<cursor_+Slots){auto& list=buckets_[p->tick%Slots];list.push_back(p);p->link=std::prev(list.end());p->heap=false;++near_;}
  else{p->heap=true;p->index=heap_.size();heap_.push_back(p);up(p->index);}
 }
public:
 explicit TimerWheel(std::uint64_t now=monotonicMs()):cursor_(now/Tick){}
 ~TimerWheel(){for(auto& item:entries_)item.second->object->release();}
 bool schedule(std::uint64_t token,CIOObject* object,std::uint64_t deadline)noexcept{
  std::lock_guard<std::mutex> lock(gate_);
  if(entries_.size()>=65536||entries_.find(token)!=entries_.end())return false;
  try{
   auto entry=std::make_unique<Entry>();entry->token=token;entry->object=object;
   entry->tick=std::max(cursor_,deadline/Tick+(deadline%Tick!=0));auto* p=entry.get();
   entries_.emplace(token,std::move(entry));
   try{place(p);}catch(...){entries_.erase(token);return false;}
   object->retain();return true;
  }catch(...){return false;}
 }
 // Returned object carries the wheel's reference; caller must release it.
 CIOObject* cancel(std::uint64_t token)noexcept{
  std::lock_guard<std::mutex> lock(gate_);auto found=entries_.find(token);if(found==entries_.end())return nullptr;
  auto* p=found->second.get();auto* object=p->object;unlink(p);entries_.erase(found);return object;
 }
 CIOObject* takeDue(std::uint64_t now)noexcept{
  std::lock_guard<std::mutex> lock(gate_);auto end=now/Tick;
  while(cursor_<=end){
   if(entries_.empty()){cursor_=end+1;return nullptr;}
   if(!near_&&!heap_.empty()&&heap_.front()->tick>=cursor_+Slots)
    cursor_=std::min(end+1,heap_.front()->tick-(Slots-1));
   if(cursor_>end)return nullptr;
   while(!heap_.empty()&&heap_.front()->tick<cursor_+Slots){
    auto* p=heap_.front();
    // Allocate the list node before removing the heap entry: OOM leaves a
    // valid pending timer for a later tick, without losing its owner reference.
    try{auto& list=buckets_[p->tick%Slots];list.push_back(p);auto link=std::prev(list.end());unlink(p);p->heap=false;p->link=link;++near_;}
    catch(...){return nullptr;}
   }
   auto& list=buckets_[cursor_%Slots];
   if(!list.empty()){
    auto* p=list.front();auto* object=p->object;auto token=p->token;unlink(p);entries_.erase(token);return object;
   }
   ++cursor_;
  }
  return nullptr;
 }
 std::size_t size()noexcept{std::lock_guard<std::mutex> lock(gate_);return entries_.size();}
};
} // namespace ctransport
