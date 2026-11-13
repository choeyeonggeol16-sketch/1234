// Read-only observation of ABI8 production code using its existing API fixture.
// Run from project root: g++ -std=c++17 -O0 -pthread -Itests
// research/post-abi8-audit/reproduce_queue_report_gap.cpp -o /tmp/queue-report-gap
#define main full_native_suite_unused
#include "../../tests/native_engine_test.cpp"
#undef main
int main(){
 Fixture fixture;auto [socket,id]=fixture.connect();
 auto* c=fixture.server->find(id);ObjectRef socketHold(c,false);
 auto* blocker=new SerialProbe;ObjectRef blockerHold(blocker,false);
 std::thread owner([&]{check(c->execution_.AddExecutable(blocker,SerialProbe::Block,nullptr),"block serializer");});
 until([&]{return blocker->entered.load();});
 unsigned char source[256]{};check(CIO_Server_Send(fixture.server,id,source,sizeof(source)),"queued source admission");
 std::uint64_t row[12]{},stats[9]{};check(CTransport_Server_ReportSockets(fixture.server,row,1,12)==1,"socket report");
 check(CTransport_Server_GetSendDiagnostics(fixture.server,stats,9),"global diagnostics");
 unsigned reserved,requests;
 {std::lock_guard<std::mutex> lock(c->gate);reserved=c->reservedSendBytes;requests=c->pendingSendRequests;}
 blocker->releaseBlocked=true;owner.join();until([&]{return fixture.state.sent==256;});
 check(reserved==256&&requests==1&&stats[7]==1&&row[8]==0,"ABI8 queued request observation changed");
 std::cout<<"CONFIRMED ABI8 report gap:per-socket reportedWriteBytes="<<row[8]
          <<",actualReservedBytes="<<reserved<<",actualPendingRequests="<<requests
          <<",globalPendingRequests="<<stats[7]<<". SEND still completes correctly.\n";
}
