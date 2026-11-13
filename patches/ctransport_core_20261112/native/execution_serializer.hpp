#pragma once
#include "cio_core.hpp"
#include <array>
#include <atomic>
#include <cstdint>
// Original48-byte prefix,head/tail/pending/owner/capacity/Args/Funcs offsets
// verified at0x45c708/0x45c7ac. L1 ticket generations and held targets live
// outside the prefix to retain safe concurrent publication and cancellation.
class CExecutionSerializerFast {
 using Function=void(*)(MemoryObject*,void*)noexcept;
 static constexpr unsigned Slots=512,Limit=Slots-8,None=0xffffffffu;
 struct Arguments final:ObjectAllocated{std::array<void*,Slots> values{};};
 struct Functions final:ObjectAllocated{std::array<std::atomic<Function>,Slots> values{};};
 struct Tickets final:ObjectAllocated{
  std::array<MemoryObject*,Slots> objects{};
  std::array<std::atomic<std::uint64_t>,Slots> available{};
  Tickets(){for(unsigned i=0;i<Slots;++i)available[(i+1)%Slots].store(i,std::memory_order_relaxed);}
 };
 std::uint64_t head_=0; //0
 std::atomic<std::uint64_t> tail_{0}; //8
 std::atomic<unsigned> pending_{0},owner_{None}; //10/14
 unsigned original18_=0,capacity_=Slots; //18/1c
 void** arguments_=nullptr;std::atomic<Function>* functions_=nullptr; //20/28
 inline static std::atomic<unsigned> nextThread_{1};
 static unsigned ThreadIdentity()noexcept{for(;;){auto n=nextThread_.fetch_add(1);if(n!=None)return n;}}
 inline static thread_local unsigned thread_=ThreadIdentity();
 Tickets& tickets()noexcept{return *static_cast<Tickets*>(ObjectAllocator::Extension(arguments_));}
 friend struct ObjectAbiTestAccess;
public:
 CExecutionSerializerFast(){
  auto args=std::make_unique<Arguments>();auto functions=std::make_unique<Functions>();auto metadata=std::make_unique<Tickets>();
  ObjectAllocator::Extension(args.get())=metadata.release();arguments_=args.release()->values.data();functions_=functions.release()->values.data();
 }
 ~CExecutionSerializerFast(){delete &tickets();delete reinterpret_cast<Arguments*>(arguments_);delete reinterpret_cast<Functions*>(functions_);}
 CExecutionSerializerFast(const CExecutionSerializerFast&)=delete;
 CExecutionSerializerFast& operator=(const CExecutionSerializerFast&)=delete;
 int AddExecutable(MemoryObject* object,Function function,void* argument,const wchar_t* =nullptr)noexcept{
  if(!object||!function)return -1;
  if(owner_.load(std::memory_order_acquire)==thread_){function(object,argument);return 1;}
  auto count=pending_.load(std::memory_order_acquire);
  do{if(count>=Limit)return -1;}while(!pending_.compare_exchange_weak(count,count+1,std::memory_order_acq_rel));
  object->retain(ObjectReferenceType::ORT_SERIALIZED_MESSAGE);
  const auto ticket=tail_.fetch_add(1,std::memory_order_relaxed),index=(ticket+1)%Slots;auto& metadata=tickets();
  while(metadata.available[index].load(std::memory_order_acquire)!=ticket)std::this_thread::yield();
  metadata.objects[index]=object;arguments_[index]=argument;functions_[index].store(function,std::memory_order_release);
  if(count)return 1;
  owner_.store(thread_,std::memory_order_release);
  for(;;){
   const auto current=head_++,at=(current+1)%Slots;Function execute;
   while(!(execute=functions_[at].load(std::memory_order_acquire)))std::this_thread::yield();
   auto* target=metadata.objects[at];auto* data=arguments_[at];execute(target,data);
   metadata.objects[at]=nullptr;arguments_[at]=nullptr;functions_[at].store(nullptr,std::memory_order_relaxed);
   metadata.available[at].store(current+Slots,std::memory_order_release);target->release(ObjectReferenceType::ORT_SERIALIZED_MESSAGE);
   owner_.store(None,std::memory_order_release);if(pending_.fetch_sub(1,std::memory_order_acq_rel)==1)return 1;owner_.store(thread_,std::memory_order_release);
  }
 }
 // Explicit L1 bool admission adapter;one actual execution engine.
 bool TryAddExecutable(MemoryObject* object,Function function,void* argument)noexcept{return AddExecutable(object,function,argument)==1;}
 bool IsOwner()const noexcept{return owner_.load(std::memory_order_acquire)==thread_;}
 unsigned Pending()const noexcept{return pending_.load(std::memory_order_acquire);}
};
static_assert(sizeof(CExecutionSerializerFast)==0x30);
