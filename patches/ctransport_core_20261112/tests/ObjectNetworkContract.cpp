#define main nativeFixtureMain
#include "native_engine_test.cpp"
#undef main
int main(){
 {
  Fixture f;f.state.userProfile=true;auto pair=f.connect();auto* c=static_cast<CUserSocket*>(f.server->find(pair.second));ObjectRef hold(c,false);
  c->Send("c c",0x12,0x34);until([&]{return f.state.sent==4;});
  auto wire=fakewin::wire(pair.first);check(wire==std::vector<unsigned char>({4,0,static_cast<unsigned char>(0x12^0xa7),static_cast<unsigned char>(0x34^0xa7)}),"original Send->SendV formatting did not reach L1 cipher/FIFO");
  check(c->l1().lastSending==0x120004,"original OnSending slot not invoked before cipher");
  std::array<std::uint64_t,93> reasons{};
  c->PostIOCompletion(9);until([&]{c->ReferenceSnapshot(reasons.data());return reasons[4*3]==0;});
  auto refs=c->referenceCount();fakewin::failPosts=1;c->PostIOCompletion2(10);check(c->referenceCount()==refs,"original posting failure lost/retained a reference");
  check(CIOSocketEx_Close(f.server,pair.second),"contract close");until([&]{return f.state.closed==1;});
 }
 std::array<std::uint64_t,ObjectAllocator::ReportWords> heap{};ObjectAllocator::Instance().Snapshot(heap.data());check(heap[1]==0&&heap[2]==0&&heap[8]==0,"original virtual network path retained native objects");
 std::cout<<"PASS actual native original Send/SendV->L1 cipher/FIFO,OnSending before encryption,PostIOCompletion/2 success and rollback,terminal cleanup,zero live allocator objects\n";
}
