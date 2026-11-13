#pragma once
#include <array>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <thread>
// Labels/indices0..30 are read from the original table at0xcc8430.
// The enum type name and packed file IDs are L1 adapter interfaces.
enum class ObjectReferenceType : unsigned {
 ORT_ANONYMOUS,ORT_CREATE_DELETE,ORT_TIMER,ORT_IO_RETAIN,ORT_IOCP_MESSAGE_RETAIN,
 ORT_OBJECT_MAP,ORT_OBJECT_NAME_MAP,ORT_OBJECT_INDEX,ORT_OBJECT_STORAGE,ORT_FIND_OBJECT,
 ORT_TL_MAP,ORT_SHARED_CONTAINER,ORT_SMART_ID,ORT_SERIALIZED_WORLD_THREAD,ORT_TEMP_USE,
 ORT_CACHED,ORT_FUNCTION_LOCAL,ORT_FUNCTION_CHAIN,ORT_EVENT_RETAIN,ORT_INVENTORY_CHANGE_MAP,
 ORT_IO_SOCKET_WRITE,ORT_NPC_RELATED,ORT_VERIFIER_RETAIN,ORT_CLASS_MEMBER,ORT_SOCKET_MAP,
 ORT_ACCOUNT_MAP,ORT_USER_SOCKET_MAP,ORT_USER_SOCKET_PLAY_MAP,ORT_USER_SOCKET_CHAR_MAP,
 ORT_SERIALIZED_MESSAGE,ORT_TDD_USE,Count
};
inline constexpr unsigned ReferenceKinds=31;
constexpr bool referenceSuffix(const char* value,const char* ending)noexcept{
 if(!value)return false;std::size_t n=0,m=0;while(value[n])++n;while(ending[m])++m;
 if(m>n)return false;for(std::size_t i=0;i<m;++i)if(value[n-m+i]!=ending[i])return false;return true;
}
constexpr std::uint32_t referenceFile(const char* path)noexcept{
 return referenceSuffix(path,"CTransport.cpp")?1:referenceSuffix(path,"cio_core.hpp")?2:
 referenceSuffix(path,"execution_serializer.hpp")?3:referenceSuffix(path,"user_registry.hpp")?4:
 referenceSuffix(path,"object_map.hpp")?5:referenceSuffix(path,"timer_wheel.hpp")?6:
 referenceSuffix(path,"Test.cpp")||referenceSuffix(path,"native_engine_test.cpp")?7:0;
}
constexpr std::uint64_t referenceSite(const char* file,unsigned line)noexcept{return std::uint64_t(referenceFile(file))<<32|line;}
class ReferenceTrace {
 struct Slot {
  std::atomic<const char*> addFile{nullptr},releaseFile{nullptr};
  std::atomic<std::int32_t> count{0};
  std::atomic<std::uint32_t> addLine{0},releaseLine{0},sequence{0};
 };
 static_assert(sizeof(Slot)==32&&offsetof(Slot,count)==0x10&&offsetof(Slot,addLine)==0x14&&offsetof(Slot,releaseLine)==0x18);
 std::array<Slot,ReferenceKinds> slots_{};
 inline static std::atomic<std::uint64_t> mismatches_{0};
 static unsigned index(ObjectReferenceType type)noexcept{auto n=static_cast<unsigned>(type);return n<ReferenceKinds?n:0;}
 struct Write {
  Slot& slot;explicit Write(Slot& value):slot(value){auto n=slot.sequence.load(std::memory_order_relaxed);
   for(;;){if(!(n&1)&&slot.sequence.compare_exchange_weak(n,n+1,std::memory_order_acquire))break;n=slot.sequence.load(std::memory_order_relaxed);std::this_thread::yield();}}
  ~Write(){slot.sequence.fetch_add(1,std::memory_order_release);}
 };
public:
 void add(ObjectReferenceType type,const char* file,unsigned line)noexcept{
  auto& slot=slots_[index(type)];Write hold(slot);slot.count.fetch_add(1,std::memory_order_relaxed);slot.addFile.store(file,std::memory_order_relaxed);slot.addLine.store(line,std::memory_order_relaxed);
 }
 void release(ObjectReferenceType type,const char* file,unsigned line)noexcept{
  auto& slot=slots_[index(type)];Write hold(slot);slot.releaseFile.store(file,std::memory_order_relaxed);slot.releaseLine.store(line,std::memory_order_relaxed);
  if(slot.count.fetch_sub(1,std::memory_order_relaxed)<=0)mismatches_.fetch_add(1,std::memory_order_relaxed);
 }
 void undoRelease(ObjectReferenceType type)noexcept{auto& slot=slots_[index(type)];Write hold(slot);slot.count.fetch_add(1,std::memory_order_relaxed);}
 std::int64_t snapshot(std::uint64_t* out)const noexcept{
  std::int64_t total=0;
  for(unsigned i=0;i<ReferenceKinds;++i){const auto& s=slots_[i];std::int32_t n;std::uint64_t add,release;
   for(;;){const auto stamp=s.sequence.load(std::memory_order_acquire);if(stamp&1){std::this_thread::yield();continue;}
    n=s.count.load(std::memory_order_relaxed);add=referenceSite(s.addFile.load(std::memory_order_relaxed),s.addLine.load(std::memory_order_relaxed));release=referenceSite(s.releaseFile.load(std::memory_order_relaxed),s.releaseLine.load(std::memory_order_relaxed));
    if(s.sequence.load(std::memory_order_acquire)==stamp)break;
   }
   total+=n;out[i*3]=static_cast<std::uint64_t>(static_cast<std::int64_t>(n));out[i*3+1]=add;out[i*3+2]=release;
  }return total;
 }
 static std::uint64_t mismatches()noexcept{return mismatches_.load(std::memory_order_relaxed);}
};
static_assert(sizeof(ReferenceTrace)==31*32);
inline constexpr unsigned ReferenceRecordWords=12+ReferenceKinds*3,ReferenceRecordCount=16,ReferenceHeaderWords=16;
inline constexpr unsigned ReferenceReportWords=ReferenceHeaderWords+ReferenceRecordWords*ReferenceRecordCount;
inline constexpr std::uint64_t ReferenceReportMarker=0x4354524600010000ULL;
#include <algorithm>
#include <mutex>
class ReferenceDiagnostics {
 static constexpr unsigned Budget=128;
 std::mutex gate_;std::array<std::array<std::uint64_t,ReferenceRecordWords>,ReferenceRecordCount> recent_{};
 std::uint64_t sequence_=0;unsigned size_=0;
public:
 std::atomic<std::uint64_t> started{0},active{0},checked{0},suspects{0},skipped{0},cancelled{0};
 bool reserve()noexcept{
  auto n=active.load(std::memory_order_relaxed);
  do{if(n>=Budget){++skipped;return false;}}
  while(!active.compare_exchange_weak(n,n+1,std::memory_order_acq_rel));return true;
 }
 void record(std::array<std::uint64_t,ReferenceRecordWords>& data)noexcept{
  std::lock_guard<std::mutex> lock(gate_);data[0]=++sequence_;recent_[(sequence_-1)%ReferenceRecordCount]=data;size_=std::min(size_+1,ReferenceRecordCount);
 }
 void snapshot(std::uint64_t* out,std::uint64_t timerErrors)noexcept{
  std::fill(out,out+ReferenceReportWords,0);out[0]=ReferenceReportMarker;out[1]=started;out[2]=active;out[3]=checked;out[4]=suspects;out[5]=skipped;out[6]=cancelled;
  out[9]=ReferenceKinds;out[10]=ReferenceRecordWords;out[11]=ReferenceRecordCount;out[12]=timerErrors;out[13]=ReferenceTrace::mismatches();out[15]=Budget;
  std::lock_guard<std::mutex> lock(gate_);out[7]=sequence_;out[8]=size_;
  for(unsigned i=0;i<size_;++i){auto index=(sequence_-size_+i)%ReferenceRecordCount;std::copy(recent_[index].begin(),recent_[index].end(),out+ReferenceHeaderWords+i*ReferenceRecordWords);}
 }
};
