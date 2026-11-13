#define main nativeFixtureMain
#include "native_engine_test.cpp"
#undef main
struct MemoryObjectTestAccess {
 static std::ptrdiff_t timerOffset(const MemoryObject& p){return reinterpret_cast<const char*>(&p.inTimer_)-reinterpret_cast<const char*>(&p);}
};
struct TimerGuardProbe final:MemoryObject {
 bool* dead;explicit TimerGuardProbe(bool* p):dead(p){}~TimerGuardProbe()override{*dead=true;}
};
static auto referenceSnapshot(Server* s){std::array<std::uint64_t,ReferenceReportWords> out{};s->referenceDiagnostics.snapshot(out.data(),MemoryObject::timerErrors());return out;}
int main(){
 std::cout.setf(std::ios::unitbuf);
 {
  ReferenceTrace trace;std::vector<std::thread> threads;
  for(unsigned t=0;t<8;++t)threads.emplace_back([&]{for(unsigned i=0;i<10000;++i){trace.add(ObjectReferenceType::ORT_CLASS_MEMBER,"native_engine_test.cpp",21);trace.release(ObjectReferenceType::ORT_CLASS_MEMBER,"native_engine_test.cpp",22);}});
  for(auto& t:threads)t.join();std::array<std::uint64_t,93> slots{};check(trace.snapshot(slots.data())==0,"parallel reason imbalance");
  check(slots[23*3+1]==referenceSite("native_engine_test.cpp",21)&&slots[23*3+2]==referenceSite("native_engine_test.cpp",22),"incoherent source site");
  auto before=ReferenceTrace::mismatches();trace.release(ObjectReferenceType::ORT_CLASS_MEMBER,"native_engine_test.cpp",24);
  check(trace.snapshot(slots.data())==-1&&ReferenceTrace::mismatches()==before+1,"reason underflow not diagnosed");
  bool dead=false;auto* obj=new TimerGuardProbe(&dead);check(sizeof(MemoryObject)==24&&MemoryObjectTestAccess::timerOffset(*obj)==0xc,"timer/base layout");
  auto errors=MemoryObject::timerErrors();obj->linkTimer();check(obj->release()==1&&!dead&&MemoryObject::timerErrors()==errors+1,"linked timer object freed");
  obj->unlinkTimer();check(obj->release()==0&&dead,"unlinked object not reclaimed");
  ReferenceDiagnostics ring;for(unsigned n=0;n<128;++n)check(ring.reserve(),"verifier budget early");check(!ring.reserve()&&ring.active==128,"verifier budget unbounded");
  for(unsigned n=0;n<40;++n){std::array<std::uint64_t,ReferenceRecordWords> row{};row[2]=n;ring.record(row);}
  std::array<std::uint64_t,ReferenceReportWords+1> report{};report.back()=1234;ring.snapshot(report.data(),0);
  check(report.back()==1234&&report[8]==16&&report[16]==25&&report[16+15*ReferenceRecordWords]==40,"recent ring bounds/order");
 }
 auto mismatchBefore=ReferenceTrace::mismatches();
 {
  Fixture f;f.state.userProfile=true;auto pair=f.connect();check(L1Interop_BindAccount(f.server,pair.second,9031),"account bind");
  check(CIOSocketEx_Close(f.server,pair.second),"close");until([&]{return f.server->referenceDiagnostics.checked>0&&f.server->referenceDiagnostics.active==0;});
  auto r=referenceSnapshot(f.server);auto at=16u;check(r[at+8]==0&&r[at+6]==1&&r[at+7]==0&&r[at+5]==9031&&r[at+10]==1,"healthy verifier false positive/identity");
  for(unsigned k=0;k<31;++k)check(r[at+12+k*3]==(k==22?1:0),"healthy reason imbalance");
  for(unsigned n:{8u,18u,24u,24u+ReferenceReportWords}){std::array<std::uint64_t,25+ReferenceReportWords> canary;canary.fill(0xace55);
   check(L1Interop_GetDiagnostics(f.server,canary.data(),n)&&canary[n]==0xace55,"legacy diagnostics overrun");if(n>24)check(canary[24]==ReferenceReportMarker,"extended report marker");}
 }
 {
  Fixture f;f.state.userProfile=true;auto pair=f.connect();auto* c=f.server->find(pair.second);c->retain(ObjectReferenceType::ORT_CLASS_MEMBER);c->release(ObjectReferenceType::ORT_FIND_OBJECT);
  check(CIOSocketEx_Close(f.server,pair.second),"extra hold close");until([&]{return f.server->referenceDiagnostics.checked>0&&f.server->referenceDiagnostics.active==0;});
  auto r=referenceSnapshot(f.server);check(r[24]==1&&r[23]==1&&r[16+12+23*3]==1,"unexplained reference not identified");
  c->release(ObjectReferenceType::ORT_CLASS_MEMBER);
 }
 {
  Fixture f;f.state.userProfile=true;f.state.holdPacket=true;auto pair=f.connect();L1Interop_PauseRead(f.server,pair.second,0);fakewin::feed(pair.first,l1Frame(128,7));until([&]{return f.state.heldSequence!=0;});
  check(CIOSocketEx_Close(f.server,pair.second),"held packet close");until([&]{return f.server->referenceDiagnostics.checked>0;});auto r=referenceSnapshot(f.server);
  check(r[24]==3&&(r[25]>>32&16)&&r[16+12+11*3]==1&&r[4]==0,"normal packet hold called leak");
  check(L1Interop_ReleasePacket(f.server,pair.second,f.state.heldSequence),"held release");check(L1Interop_StopServer(f.server),"stop while verifier rechecks");
  check(f.server->referenceDiagnostics.active==0&&f.server->referenceDiagnostics.cancelled==1,"shutdown retained verifier task");
 }
 check(ReferenceTrace::mismatches()==mismatchBefore,"production socket reason ownership unbalanced");
 std::cout<<"PASS reference trace:80000 concurrent pairs,coherent file/line,negative-kind diagnostic,timer +0xc/final-free guard,128 budget/16 records,healthy/extra/packet references,account identity,5/25s phases accelerated in fixture,cancel-on-stop,legacy8/18/24/1720 canaries;no forced free\n";
}
