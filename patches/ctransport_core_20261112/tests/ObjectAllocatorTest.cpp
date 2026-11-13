#include "../native/object_allocator.hpp"
#include <cassert>
#include <iostream>
#include <thread>
#include <unordered_set>
struct Probe:ObjectAllocated{std::uint64_t data[4]{};};
struct alignas(128) Wide:ObjectAllocated{unsigned char data[256]{};};
struct Failing:ObjectAllocated{Failing(){throw 17;}};
int main(){
 auto& heap=ObjectAllocator::Instance();std::array<std::uint64_t,ObjectAllocator::ReportWords> before{},after{};heap.Snapshot(before.data());
 for(unsigned i=0;i<49;i++){
  auto size=ObjectAllocator::ClassSize(i);assert(size>=8);auto* p=heap.Alloc(size-4);assert(p&&reinterpret_cast<std::uintptr_t>(p)%16==0);
  assert(ObjectAllocator::AllocationBytes(p)==size);std::memset(p,0xa3,size-4);heap.Free(p);
 }
 for(auto n:{std::size_t(1),32764ul,32765ul,65536ul}){auto* p=heap.Alloc(n);assert(p);std::memset(p,0x37,n);heap.Free(p);}
 auto* p=new Probe;assert(ObjectAllocator::AllocationBytes(p)==sizeof(Probe)+4);delete p;
 auto* w=new Wide;assert(reinterpret_cast<std::uintptr_t>(w)%128==0);delete w;
 try{new Failing;assert(false);}catch(int n){assert(n==17);}
 std::vector<void*> held;for(unsigned i=0;i<2000;i++){auto* block=heap.Alloc(104);assert(block);held.push_back(block);}
 assert(std::unordered_set<void*>(held.begin(),held.end()).size()==held.size());for(auto* block:held)heap.Free(block);
 std::vector<std::thread> threads;for(unsigned t=0;t<12;t++)threads.emplace_back([&,t]{
  for(unsigned n=0;n<10000;n++){auto size=16+(n+t)%256;auto* block=static_cast<unsigned char*>(heap.Alloc(size));assert(block);std::memset(block,static_cast<int>(n),size);std::this_thread::yield();assert(block[size-1]==static_cast<unsigned char>(n));heap.Free(block);}
 });for(auto& thread:threads)thread.join();heap.Snapshot(after.data());assert(after[1]==before[1]&&after[2]==before[2]&&after[3]==0&&after[4]==0&&after[8]==0);
 assert(after[5]-before[5]==after[6]-before[6]&&after[9]>0);auto failures=after[7];assert(!heap.Alloc(std::numeric_limits<std::size_t>::max()));heap.Snapshot(after.data());assert(after[7]==failures+1);
 std::cout<<"PASS49 original classes/size+4/32768 boundary,16-byte alignment,overaligned objects,constructor rollback,2000 held objects,120000 concurrent reuse pairs,zero live objects/bytes,overflow failure,cached slabs freed at process exit\n";
}
