package l1j.server.server.model.network.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.model.network.core.CNetworkDiagnostics.PacketKind;

public final class ExtendedPacketDiagnosticsTest {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  if(args.length!=0){
   check(!CIOConfig.DIAGNOSTICS_ENABLED,"disabled fixture");
   check(CNetworkDiagnostics.begin(null,81,8)==null&&!CNetworkDiagnostics.subcommand(81,PacketKind.ACTION_UI,1001),"disabled diagnostics active");
   check(!CNetworkDiagnostics.workers().contains("opcode="),"disabled work registered");
   System.out.println("PASS disabled extended diagnostics:no work or packet records");return;
  }
  check(!CNetworkDiagnostics.subcommand(81,PacketKind.ACTION_UI,1001),"out-of-context tag accepted");
  var trace=CNetworkDiagnostics.open("extended-contract",71);check(trace!=null,"trace");
  for(int id:new int[]{1001,1002}){
   var work=CNetworkDiagnostics.begin(trace,81,8);
   check(!CNetworkDiagnostics.subcommand(137,PacketKind.EXTENDED,4),"foreign opcode relabeled current work");
   check(!CNetworkDiagnostics.subcommand(81,PacketKind.CRAFT,256)&&!CNetworkDiagnostics.subcommand(81,PacketKind.ACTION_UI,-1)&&!CNetworkDiagnostics.subcommand(81,PacketKind.ACTION_UI,65536),"invalid command accepted");
   check(CNetworkDiagnostics.subcommand(81,PacketKind.ACTION_UI,id),"real tag rejected");
   check(!CNetworkDiagnostics.subcommand(81,PacketKind.CRAFT,2),"second tag replaced actual route");
   check(CNetworkDiagnostics.workers().contains("opcode=81 protocol=ACTION_UI command="+id),"active command absent");
   CNetworkDiagnostics.end(work);
  }
  String history=CNetworkDiagnostics.recent(trace.id);
  check(history.contains("protocol=ACTION_UI command=1001")&&history.contains("protocol=ACTION_UI command=1002")&&history.lines().filter(s->s.startsWith("#")).count()==2,"subcommands merged or duplicate ring events");
  check(CNetworkDiagnostics.packetCounts().contains("opcode=81 count=2")&&CNetworkDiagnostics.timings().contains("opcode=81 completed=2"),"base counter/timing meaning changed");
  var outer=CNetworkDiagnostics.begin(trace,81,8);CNetworkDiagnostics.subcommand(81,PacketKind.ACTION_UI,1001);
  var inner=CNetworkDiagnostics.begin(trace,137,8);CNetworkDiagnostics.subcommand(137,PacketKind.EXTENDED,65535);
  check(CNetworkDiagnostics.workers().contains("protocol=EXTENDED command=65535"),"nested unsigned command");
  CNetworkDiagnostics.end(inner);check(CNetworkDiagnostics.workers().contains("protocol=ACTION_UI command=1001"),"parent command not restored");CNetworkDiagnostics.end(outer);
  // Keep one decoder active while the same connection's ring wraps.
  var stale=CNetworkDiagnostics.begin(trace,81,8);
  for(int i=0;i<129;i++){var w=CNetworkDiagnostics.begin(trace,137,8);CNetworkDiagnostics.subcommand(137,PacketKind.EXTENDED,7);CNetworkDiagnostics.end(w);}
  CNetworkDiagnostics.subcommand(81,PacketKind.ACTION_UI,44444);CNetworkDiagnostics.end(stale);
  check(!CNetworkDiagnostics.recent(trace.id).contains("44444"),"old decoder relabeled reused ring slot");
  // Overflow must not attach an untracked nested command to its parent.
  List<CNetworkDiagnostics.Work> stack=new ArrayList<>();
  for(int i=0;i<32;i++)stack.add(CNetworkDiagnostics.begin(trace,81,8));
  var overflow=CNetworkDiagnostics.begin(trace,81,8);
  check(!CNetworkDiagnostics.subcommand(81,PacketKind.ACTION_UI,33333),"overflow relabeled parent");
  CNetworkDiagnostics.end(overflow);
  check(CNetworkDiagnostics.subcommand(81,PacketKind.ACTION_UI,1002),"parent lost after overflow");
  Collections.reverse(stack);for(var w:stack)CNetworkDiagnostics.end(w);
  check(!CNetworkDiagnostics.workers().contains("opcode="),"work left active");
  check(Arrays.stream(outer.traces).allMatch(Objects::isNull)&&Arrays.stream(outer.kinds).allMatch(Objects::isNull),"finished work retained trace metadata");
  CNetworkDiagnostics.resetCounts();AtomicReference<Throwable> failure=new AtomicReference<>();List<Thread> threads=new ArrayList<>();
  for(int t=0;t<8;t++){final boolean a=(t&1)==0;Thread thread=new Thread(()->{try{for(int i=0;i<1000;i++){
   var w=CNetworkDiagnostics.begin(trace,a?81:137,8);
   try{check(CNetworkDiagnostics.subcommand(a?81:137,a?PacketKind.ACTION_UI:PacketKind.EXTENDED,a?1001:7),"concurrent tag");}finally{CNetworkDiagnostics.end(w);}
  }}catch(Throwable ex){failure.compareAndSet(null,ex);}});threads.add(thread);thread.start();}
  for(Thread t:threads)t.join();check(failure.get()==null,"concurrent failure:"+failure.get());
  history=CNetworkDiagnostics.recent(trace.id);check(history.lines().filter(s->s.startsWith("#")).count()==128,"ring bound changed");
  for(String line:history.split("\n"))if(line.startsWith("#")){
   check(!line.contains("opcode=81")||line.contains("protocol=ACTION_UI command=1001"),"mixed concurrent route");
   check(!line.contains("opcode=137")||line.contains("protocol=EXTENDED command=7"),"mixed concurrent route");
  }
  check(CNetworkDiagnostics.packetCounts().contains("opcode=81 count=4000")&&CNetworkDiagnostics.packetCounts().contains("opcode=137 count=4000"),"base counters lost");
  CNetworkDiagnostics.close(trace);check(CNetworkDiagnostics.recent(trace.id).contains("protocol="),"commands lost at close");
  var noTrace=CNetworkDiagnostics.begin(null,81,8);check(CNetworkDiagnostics.subcommand(81,PacketKind.CRAFT,2),"capped/null trace work");CNetworkDiagnostics.end(noTrace);
  System.out.println("PASS extended diagnostics:one-event128 ring,distinct commands,base count/time preserved,nested restoration,overflow isolation,stale-slot guard,8000 concurrent tags,closed retention,bounded metadata release");
 }
}
