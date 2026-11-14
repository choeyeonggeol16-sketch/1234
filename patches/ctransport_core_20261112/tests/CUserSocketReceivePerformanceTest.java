package l1j.server.server.model.network.core.connection;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import static l1j.server.server.model.network.core.stability.StressMetrics.*;

/** Real CUserSocket/native decoder/FFM. Observer is in the game-client fixture. */
public final class CUserSocketReceivePerformanceTest {
    static Map<String,Object> run(int seconds,String name)throws Exception {
        final int clients=4,batch=64,size=512;
        CountDownLatch ready=new CountDownLatch(clients),start=new CountDownLatch(1);AtomicLong deadline=new AtomicLong();AtomicReference<Throwable> error=new AtomicReference<>();
        byte[] wire=new byte[size*batch];new Random(173).nextBytes(wire);for(int n=0;n<wire.length;n+=size){wire[n]=(byte)size;wire[n+1]=(byte)(size>>>8);}
        long[] totals=new long[clients];long[][] samples=new long[clients][];
        try(ExecutorService sessions=Executors.newSingleThreadExecutor();ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor()){
            CIOThreadCallback.INSTANCE.performanceExecutor=sessions;
            try(CSharedBroadcastWindowsTest.World world=new CSharedBroadcastWindowsTest.World()){
                List<Future<?>> futures=new ArrayList<>();AtomicLong[] observed=new AtomicLong[clients];
                for(int n=0;n<clients;n++){
                    final int index=n;AtomicLong count=new AtomicLong();observed[n]=count;Semaphore completed=new Semaphore(0);
                    world.clients[n].packetObserver=()->{if(count.incrementAndGet()%batch==0)completed.release();};
                    futures.add(workers.submit(()->{
                        try{long[] latency=new long[100000];int stored=0,batches=0;ready.countDown();check(start.await(15,TimeUnit.SECONDS),"RX start");
                            do{long began=System.nanoTime();world.peers[index].getOutputStream().write(wire);
                                check(completed.tryAcquire(15,TimeUnit.SECONDS),"production RX stalled client="+index+" observed="+count.get());
                                if(stored<latency.length)latency[stored++]=System.nanoTime()-began;batches++;
                            }while(System.nanoTime()<deadline.get());totals[index]=(long)batches*batch;samples[index]=Arrays.copyOf(latency,stored);
                        }catch(Throwable failure){error.compareAndSet(null,failure);throw new RuntimeException(failure);}
                    }));
                }
                check(ready.await(15,TimeUnit.SECONDS),"RX admission");long cpu=cpuTime(),began=System.nanoTime();Instant wall=Instant.now();deadline.set(began+seconds*1_000_000_000L);start.countDown();
                for(Future<?> future:futures)future.get(seconds+30,TimeUnit.SECONDS);long elapsed=System.nanoTime()-began,cpuNanos=cpuTime()-cpu;
                check(error.get()==null,"RX failure "+error.get());for(int n=0;n<clients;n++)check(observed[n].get()==totals[n],"RX loss/duplicate");healthy(world.server);
                long total=Arrays.stream(totals).sum();long[] latency=new long[Arrays.stream(samples).mapToInt(a->a.length).sum()];int at=0;for(long[] a:samples){System.arraycopy(a,0,latency,at,a.length);at+=a.length;}
                Map<String,Object> result=result(name,total,total*size,elapsed,latency);measured(name,wall,Instant.now(),elapsed,cpuNanos,result);
                result.put("scope","production CUserSocket/native decoder/FFM;4 peers,2 IO workers;game rate/filter-entry observer and one session-executor fixture;not world/DB load");
                result.put("latency_kind","64-frame write through last frame rate/filter entry and client wakeup;not RTT or completed game handler time");result.put("after",memory(world.server));return result;
            }finally{CIOThreadCallback.INSTANCE.performanceExecutor=null;}
        }finally{start.countDown();}
    }
    public static void main(String[] args)throws Exception {
        Map<String,Object> report=new LinkedHashMap<>();report.put("environment",environment());report.put("label",args[0]);List<Object> cases=new ArrayList<>();report.put("cases",cases);
        try{run(1,"warmup");for(int n=0;n<2;n++)cases.add(run(4,"receive-"+n));report.put("status","passed");}
        catch(Throwable failure){report.put("status","failed");report.put("failure",failure.toString());throw failure;}
        finally{write(Path.of("stability-rxowner-"+args[0]+".json"),report);}
    }
}
