#define CTRANSPORT_TEST
#include "../native/CTransport.cpp"
#include <cassert>
#include <iostream>
#include <set>
#include <type_traits>
struct MemoryObjectTestAccess {
 static unsigned count(const MemoryObject& p){return p.references_.load();}
 static void count(MemoryObject& p,unsigned n){p.references_.store(n);}
 static std::ptrdiff_t idOffset(const MemoryObject& p){return reinterpret_cast<const char*>(&p.objectId_)-reinterpret_cast<const char*>(&p);}
};
struct MemoryProbe final:MemoryObject {
 std::atomic<unsigned>& destroyed;
 explicit MemoryProbe(std::atomic<unsigned>& n):destroyed(n){}
 MemoryProbe(const MemoryProbe& source):MemoryObject(source),destroyed(source.destroyed){}
 ~MemoryProbe()override{++destroyed;}
};
int main(){
 static_assert(!std::is_copy_constructible_v<CIOSocket>);
 static_assert(!std::is_copy_constructible_v<ObjectRef>);
 std::atomic<unsigned> destroyed{0};
 auto* first=new MemoryProbe(destroyed);first->retain();auto* copy=new MemoryProbe(*first);
 assert(first->objectId()!=copy->objectId()&&MemoryObjectTestAccess::idOffset(*first)==0x10);
 assert(MemoryObjectTestAccess::count(*first)==2&&MemoryObjectTestAccess::count(*copy)==1);
 copy->release();first->release();assert(destroyed==1);first->release();assert(destroyed==2);
 std::set<std::uint32_t> ids;std::mutex idsGate;
 std::vector<std::thread> threads;
 for(unsigned t=0;t<4;++t)threads.emplace_back([&]{
  for(unsigned n=0;n<1000;++n){auto* p=new MemoryProbe(destroyed);auto id=p->objectId();p->release();
   std::lock_guard<std::mutex> lock(idsGate);assert(id&&ids.insert(id).second);}
 });
 for(auto& t:threads)t.join();assert(ids.size()==4000&&destroyed==4002);
 std::uint64_t before[3],after[3];MemoryObject::referenceDiagnostics(before);
 auto* invalid=new MemoryProbe(destroyed);auto invalidId=invalid->objectId();
 // Inject counter corruption while the object is still alive. This does not
 // claim that dereferencing a pointer after deletion can be made safe.
 MemoryObjectTestAccess::count(*invalid,0);invalid->release();
 MemoryObject::referenceDiagnostics(after);
 assert(after[0]==before[0]+1&&after[1]==invalidId&&after[2]==0);
 assert(MemoryObjectTestAccess::count(*invalid)==0&&destroyed==4002);
 threads.clear();for(unsigned t=0;t<4;++t)threads.emplace_back([&]{for(unsigned i=0;i<1000;++i)invalid->release();});
 for(auto& t:threads)t.join();MemoryObject::referenceDiagnostics(after);
 assert(after[0]==before[0]+4001&&MemoryObjectTestAccess::count(*invalid)==0);
 MemoryObjectTestAccess::count(*invalid,1);invalid->release();assert(destroyed==4003);
 CIOBufferPool pool(8);assert(pool.initializeOwner(0,4,4));CIOBufferPool::OwnerScope owner(pool,0);
 for(auto bytes:{8192u,16384u}){
  CIOBuffer* p[4];for(auto& b:p){b=pool.acquire(bytes);assert(b);}for(auto b:p)pool.recycle(b);
  // Corrupt three free entries without destroying their containing slab.
  p[0]->used=7;p[1]->references.store(2);p[2]->next=p[0];
  auto* good=pool.acquire(bytes);assert(good==p[3]);
  std::uint64_t state[9],q[2];pool.poolSnapshot(state,q);auto family=bytes==16384u?1:0;
  assert(q[family]==3&&state[family*4+1]==1&&state[family*4+2]-state[family*4+3]==1);
  assert(!pool.acquire(bytes)); // Only healthy block is currently held.
  std::thread late1([&]{pool.recycle(p[1]);}),late2([&]{pool.recycle(p[1]);});
  pool.recycle(good);
  for(unsigned i=0;i<1000;++i){auto* b=pool.acquire(bytes);assert(b==good);pool.recycle(b);}
  late1.join();late2.join();pool.poolSnapshot(state,q);
  assert(q[family]==3&&state[family*4+1]==0&&state[family*4+2]==state[family*4+3]);
 }
 assert(pool.corruptions()==6);
 L1Callbacks callbacks;Server server(&callbacks);std::uint64_t values[25];
 std::fill(std::begin(values),std::end(values),0xfeedbeefULL);
 assert(L1Interop_GetDiagnostics(&server,values,8)&&values[8]==0xfeedbeefULL);
 assert(L1Interop_GetDiagnostics(&server,values,18)&&values[8]==0x4354504c00010000ULL&&values[18]==0xfeedbeefULL);
 assert(L1Interop_GetDiagnostics(&server,values,24)&&values[18]==0x4354464100010000ULL&&values[24]==0xfeedbeefULL);
 assert(values[19]==0&&values[20]==0&&values[21]==after[0]&&values[22]==invalidId&&values[23]==0);
 std::cout<<"PASS fresh MemoryObject IDs/ref1 copy/+0x10 layout,4000 concurrent IDs,4001 live-object reference faults without counter wrap;8K/16K invalid-entry skip,late-return quarantine,2000 reuse cycles,zero borrowed after drain,legacy8/18 and fault24 diagnostic canaries\n";
}
