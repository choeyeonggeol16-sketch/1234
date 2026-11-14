package l1j.server.server.model.network.core.connection;

import l1j.server.server.model.network.core.diagnostics.CPoolDiagnostics;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static l1j.server.server.model.network.core.connection.CUserSocketOwnerWindowsTest.*;
public final class ReferenceDiagnosticsWindowsTest {
 static void await(BooleanSupplier check)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!check.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(5);check(check.getAsBoolean(),"native reference verifier deadline");}
 public static void main(String[] args)throws Exception{
  try(Session s=new Session(true,false)){
   check(s.channel.get().setAccountIdentity(45321),"native account identity");s.peer.close();check(s.closed.await(5,TimeUnit.SECONDS),"native terminal");
   await(()->{long[] r=s.server.referenceDiagnostics();return r.length==1696&&r[3]>=1&&r[2]==0;});
   long[] heap=s.server.objectAllocatorDiagnostics();check(heap.length==310&&heap[0]==0x43544f4100010000L&&heap[1]>=1&&heap[8]==0&&heap[10]==49,"native object allocator diagnostics");
   check(CPoolDiagnostics.objectAllocatorReport(heap).contains("프로세스 전체"),"actual allocator rendering");
   long[] r=s.server.referenceDiagnostics();int at=16;
   check(r[0]==0x4354524600010000L&&r[8]==1&&r[4]==0&&r[12]==0&&r[13]==0,"native diagnostic marker/fault/false positive");
   check(r[at+8]==0&&r[at+6]==1&&r[at+7]==0&&r[at+5]==45321&&r[at+10]==1,"native healthy final count/identity");
   for(int k=0;k<31;k++)check(r[at+12+k*3]==(k==22?1:0),"unbalanced native reason "+k);
   check(CPoolDiagnostics.referenceReport(r).contains("ORT_VERIFIER_RETAIN=1"),"actual manager reference rendering");
   check(s.server.poolDiagnostics().length==24&&s.server.diagnostics().length==8,"legacy ABI prefix changed");
  }
  try(Session s=new Session(true,false)){
   s.peer.close();check(s.closed.await(5,TimeUnit.SECONDS),"shutdown terminal");long start=System.nanoTime();s.server.close();
   check(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(4),"shutdown waited for5-second verifier timer");
  }
  System.out.println("PASS actual Windows FFM reference verifier:5-second wheel dispatch,31 balanced reason slots,account identity,own-reference exclusion,legacy prefixes,bounded manager rendering,stop cancels verifier without timer wait");
 }
}
