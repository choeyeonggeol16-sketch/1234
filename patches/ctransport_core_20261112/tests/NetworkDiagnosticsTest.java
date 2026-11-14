package l1j.server.server.model.network.core.diagnostics;

import l1j.server.server.model.network.core.config.CIOConfig;
import java.util.*;
public final class NetworkDiagnosticsTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  check(CIOConfig.MAC_LIMIT==3&&CIOConfig.LOGIN_TIMEOUT_MS==1234,"config file not loaded");
  check(CIOConfig.DIAGNOSTICS_MAX_CONNECTIONS==2,"system property must override file");
  var a=CNetworkDiagnostics.open("one",42);var b=CNetworkDiagnostics.open("two",43);
  check(a!=null&&b!=null&&CNetworkDiagnostics.open("over-cap")==null,"trace capacity");
  var outer=CNetworkDiagnostics.begin(a,7,4);
  var inner=CNetworkDiagnostics.begin(b,8,8);
  check(CNetworkDiagnostics.workers().contains("opcode=8"),"nested active context");
  CNetworkDiagnostics.end(inner);
  check(CNetworkDiagnostics.workers().contains("opcode=7"),"outer context not restored");
  CNetworkDiagnostics.resetTiming();CNetworkDiagnostics.end(outer);
  check(!CNetworkDiagnostics.timings().contains("opcode=7"),"pre-reset work polluted new epoch");
  CNetworkDiagnostics.resetCounts();
  for(int i=0;i<200;i++){var work=CNetworkDiagnostics.begin(a,i&255,4);CNetworkDiagnostics.end(work);}
  String recent=CNetworkDiagnostics.recent(a.id);
  check(recent.lines().filter(l->l.startsWith("#")).count()==128&&recent.contains("#201 ")&&!recent.contains("#1 "),"ring wrap/order/bounds");
  List<Thread> threads=new ArrayList<>();
  for(int i=0;i<8;i++){Thread t=new Thread(()->{for(int j=0;j<1000;j++){var work=CNetworkDiagnostics.begin(b,255,4);CNetworkDiagnostics.end(work);}});threads.add(t);t.start();}
  for(Thread thread:threads)thread.join();
  check(CNetworkDiagnostics.packetCounts().contains("opcode=255 count=8000"),"concurrent counts lost");
  check(CNetworkDiagnostics.timings().contains("opcode=255 completed=8000"),"concurrent completions lost");
  CNetworkDiagnostics.admitted(17);check(CNetworkDiagnostics.traffic().contains("17 bytes"),"admitted-byte meaning");
  CNetworkDiagnostics.resetTraffic();check(CNetworkDiagnostics.traffic().contains("0 bytes"),"traffic reset");
  CNetworkDiagnostics.javaClose(a,CNetworkDiagnostics.CloseReason.SEND_LIMIT);
  CNetworkDiagnostics.nativeClose(a,3,10054);
  CNetworkDiagnostics.nativeClose(a,2,995);
  CNetworkDiagnostics.javaClose(a,CNetworkDiagnostics.CloseReason.SERVER_CLOSE);
  String cause=CNetworkDiagnostics.recent(a.id);
  check(cause.contains("javaReason=SEND_LIMIT")&&cause.contains("nativeCause=WRITE_ERROR operation=3 error=10054"),"first cause overwritten by cleanup/cancellation");
  CNetworkDiagnostics.nativeClose(b,2,0);
  CNetworkDiagnostics.javaClose(b,CNetworkDiagnostics.CloseReason.SERVER_CLOSE);
  check(CNetworkDiagnostics.recent(b.id).contains("javaReason=NONE nativeCause=PEER_EOF"),"peer shutdown mislabeled as server close");
  CNetworkDiagnostics.close(a);CNetworkDiagnostics.close(a);CNetworkDiagnostics.close(b);
  check(CNetworkDiagnostics.connections().contains("진단 연결 수=0")&&CNetworkDiagnostics.recent(a.id).contains("closed=true"),"close/history cleanup");
  check(CNetworkDiagnostics.connections().contains("javaReason=SEND_LIMIT"),"closed cause absent from socket report");
  CNetworkDiagnostics.nativeClose(null,2,995);CNetworkDiagnostics.javaClose(null,CNetworkDiagnostics.CloseReason.SERVER_CLOSE);
  check(CNetworkDiagnostics.open("reused")!=null,"trace capacity not returned");
  for(int i=0;i<80;i++){var trace=CNetworkDiagnostics.open("closed-"+i);check(trace!=null,"capacity reuse");CNetworkDiagnostics.close(trace);}
  check(!CNetworkDiagnostics.recent(a.id).contains("peer=one"),"closed history unbounded");
  System.out.println("PASS config/no-DB defaults+override;bounded128 ring/64 closed history;8000 concurrent samples;nested worker restore;reset epoch isolation;trace close/capacity reuse;admission byte semantics");
 }
}
