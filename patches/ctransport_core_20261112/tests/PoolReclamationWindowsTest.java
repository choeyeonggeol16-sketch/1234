package l1j.server.server.model.network.core.connection;

import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.diagnostics.CPoolDiagnostics;
import java.util.concurrent.*;
import static l1j.server.server.model.network.core.connection.CUserSocketOwnerWindowsTest.*;
public final class PoolReclamationWindowsTest {
 static long used(long[] p){check(p.length==24&&p[8]==0x4354504c00010000L&&p[18]==0x4354464100010000L,"pool/fault extension marker");check(p[19]==0&&p[20]==0&&p[21]==0,"unexpected native pool/reference fault");check(p[10]==p[11]-p[12]&&p[14]==p[15]-p[16],"native ring snapshot mismatch");return p[10]+p[14];}
 public static void main(String[] args)throws Exception{
  try(Session s=new Session(true,false)){
   until(()->used(s.server.poolDiagnostics())>0);
   byte[] frame={10,0,1,2,3,4,5,6,7,8};CAsyncSendConstBuffer.Source source=s.server.createConstBuffer(frame);check(source!=null,"shared source");
   try{
    check(s.server.liveConstBuffers()==1,"shared holder count");s.peer.close();check(s.closed.await(5,TimeUnit.SECONDS),"owner close");
    until(()->{long[] p=s.server.registryDiagnostics();return p[0]+p[1]==0;});
    check(used(s.server.poolDiagnostics())==1,"shared source not sole remaining pool holder");
    CPoolDiagnostics monitor=new CPoolDiagnostics();String text=monitor.sample(0,s.pool.snapshot(),s.server.poolDiagnostics(),new long[]{0,0,0,0,1,0});
    check(text.contains("보유 중")&&!text.contains("미회수 의심"),"legitimate shared source falsely marked leak");
   }finally{source.release();}
   until(()->used(s.server.poolDiagnostics())==0);
   for(long[] row:s.pool.snapshot())check(row[2]==0&&row[3]==row[4],"Java borrow/return imbalance after owner drain");
   long[] prefix=s.server.diagnostics();check(prefix.length==8&&prefix[3]==0,"legacy8 diagnostics changed / pool corruption");
   System.out.println("PASS Windows actual pool reclamation:18-value extension and legacy8 prefix;shared source survives closed socket,valid holder reported,final release returns all native blocks and Java leases;reserved slabs retained separately");
  }
 }
}
