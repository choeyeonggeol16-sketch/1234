package l1j.server.server.model.network.core.connection;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.transport.CIOServer;
import l1j.server.server.model.network.core.transport.CIOPacketCodecReference;
import l1j.server.server.model.network.packet.CPacketHandler;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static l1j.server.server.model.network.core.stability.StressMetrics.*;

/** Production Java FIFO/pools/FFM/DLL;game owner and controlled handler are fixtures. */
public final class CUserSocketSoakWindowsTest {
    static byte[] wire(int opcode,int seed)throws Exception {
        Random random=new Random(46);
        for(int attempt=0;attempt<8192;attempt++){
            byte[] frame=new byte[10];random.nextBytes(frame);frame[0]=10;frame[1]=0;
            try(CIOPacketCodecReference codec=new CIOPacketCodecReference(seed)){
                ByteBuffer data=ByteBuffer.allocateDirect(10);data.put(frame).flip();codec.feed(data);int n=codec.next();
                if(n>=4&&(codec.plaintext().get(0)&255)==opcode)return frame;
            }
        }throw new AssertionError("ciphertext candidate not found");
    }
    static Map<String,Object> blocked(int count)throws Exception {
        int opcode=Opcodes.C_ALIVE&255;CountDownLatch entered=new CountDownLatch(count),release=new CountDownLatch(1),fast=new CountDownLatch(1);
        AtomicReference<Throwable> error=new AtomicReference<>();
        try(CSharedBroadcastWindowsTest.World world=new CSharedBroadcastWindowsTest.World()){
            byte[][] frames=new byte[4][];for(int n=0;n<4;n++)frames[n]=wire(opcode,73461+n);
            CPacketHandler.HANDLERS[opcode]=(buffer,client)->{
                boolean slow=false;for(int n=0;n<count;n++)slow|=client==world.clients[n];
                if(slow){entered.countDown();try{check(release.await(5,TimeUnit.SECONDS),"held handler release");}catch(Throwable failure){error.set(failure);}}
                else fast.countDown();return true;
            };
            try{
                for(int n=0;n<count;n++){
                    world.peers[n].getOutputStream().write(frames[n]);final long remaining=count-n-1;
                    until(()->entered.getCount()<=remaining,5000,"handler did not enter native worker");
                }
                long[] threads=world.server.threadDiagnostics();int active=0;for(int n=0;n<8;n++)if(threads[n*3+2]!=0)active++;
                long began=System.nanoTime();world.peers[3].getOutputStream().write(frames[3]);
                boolean beforeRelease=fast.await(count==1?2000:250,TimeUnit.MILLISECONDS);
                check(beforeRelease==(count==1),"unexpected unrelated packet progress");
                release.countDown();check(fast.await(5,TimeUnit.SECONDS),"packet failed to recover after handler release");
                check(error.get()==null,"injected handler failure: "+error.get());
                return Map.of("blocked_workers",count,"native_callbacks_active",active,"other_connection_progressed_before_release",beforeRelease,"other_connection_observed_ms",(System.nanoTime()-began)/1e6,"scope","actual CUserSocket inbound dispatch;injected waiting handler;not an observed production DB stall");
            }finally{release.countDown();CPacketHandler.HANDLERS[opcode]=null;}
        }
    }
    public static void main(String[] args)throws Exception {
        int seconds=args.length==0?180:Integer.parseInt(args[0]);check(seconds>=1&&seconds<=600,"soak duration");
        Map<String,Object> report=new LinkedHashMap<>();report.put("environment",environment());report.put("requested_duration_seconds",seconds);List<Object> snapshots=new ArrayList<>();report.put("memory_waves",snapshots);
        List<WeakReference<CUserSocket>> refs=new ArrayList<>();long began=System.nanoTime();int waves=0;
        try(CIOServer probe=new CIOServer(new InetSocketAddress("127.0.0.1",0),8,1,8,c->c.close(),0)){
            until(()->probe.performanceDiagnostics()[0]>0,5000,"probe sampler");long baseline=probe.objectAllocatorDiagnostics()[1];report.put("baseline",memory(probe));
            report.put("handler_blocking",List.of(blocked(1),blocked(2)));quiesce(probe,baseline);
            long load=System.nanoTime(),deadline=load+seconds*1_000_000_000L;
            do{
                refs.addAll(CUserSocketStabilityWindowsTest.cycle(256));waves++;quiesce(probe,baseline);
                if(waves%10==0){System.gc();Thread.sleep(25);Map<String,Object> row=memory(probe);row.put("wave",waves);row.put("closed_java_users",refs.size());row.put("weak_users_remaining",refs.stream().filter(x->x.get()!=null).count());snapshots.add(row);}
            }while(System.nanoTime()<deadline);
            report.put("soak_elapsed_ms",(System.nanoTime()-load)/1e6);System.gc();until(()->refs.stream().allMatch(x->x.get()==null),10000,"closed user GC retention");
            quiesce(probe,baseline);report.put("final",memory(probe));report.put("collected_java_users",refs.size());report.put("status","passed");
        }catch(Throwable failure){report.put("status","failed");report.put("failure",failure.toString());throw failure;}
        finally{report.put("completed_waves",waves);report.put("verified_frames",(long)waves*1024);report.put("total_elapsed_ms",(System.nanoTime()-began)/1e6);write(Path.of("stability-soak.json"),report);}
    }
}
