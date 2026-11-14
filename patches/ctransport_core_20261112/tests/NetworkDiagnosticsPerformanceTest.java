package l1j.server.server.model.network.core.diagnostics;

import l1j.server.server.model.network.core.stability.StressMetrics;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static l1j.server.server.model.network.core.stability.StressMetrics.*;

/** Exercises the production diagnostics hot path; not a network throughput benchmark. */
public final class NetworkDiagnosticsPerformanceTest {
    static Map<String,Object> run(int workers,int seconds)throws Exception {
        CountDownLatch ready=new CountDownLatch(workers),start=new CountDownLatch(1);AtomicLong deadline=new AtomicLong();
        AtomicReference<Throwable> error=new AtomicReference<>();long[] totals=new long[workers];long[][] latencies=new long[workers][];Thread[] threads=new Thread[workers];
        for(int n=0;n<workers;n++){final int index=n;threads[n]=Thread.ofPlatform().start(()->{
            try{var trace=CNetworkDiagnostics.open("benchmark-"+index);check(trace!=null,"trace admission");
                for(int i=0;i<20000;i++){var work=CNetworkDiagnostics.begin(trace,70,512);CNetworkDiagnostics.admitted(512);CNetworkDiagnostics.end(work);}
                long[] latency=new long[100000];ready.countDown();check(start.await(20,TimeUnit.SECONDS),"start");int samples=0,batches=0;long count=0;
                do{long began=System.nanoTime();for(int j=0;j<64;j++){var work=CNetworkDiagnostics.begin(trace,70,512);CNetworkDiagnostics.admitted(512);CNetworkDiagnostics.end(work);}count+=64;
                    if((batches++&15)==0&&samples<latency.length)latency[samples++]=System.nanoTime()-began;
                }while(System.nanoTime()<deadline.get());
                totals[index]=count;latencies[index]=Arrays.copyOf(latency,samples);CNetworkDiagnostics.close(trace);
            }catch(Throwable failure){error.compareAndSet(null,failure);ready.countDown();}
        });}
        check(ready.await(30,TimeUnit.SECONDS)&&error.get()==null,"warmup "+error.get());CNetworkDiagnostics.resetCounts();CNetworkDiagnostics.resetTiming();CNetworkDiagnostics.resetTraffic();
        long cpu=cpuTime(),began=System.nanoTime();deadline.set(began+seconds*1_000_000_000L);start.countDown();
        for(Thread thread:threads){thread.join((seconds+20)*1000L);check(!thread.isAlive(),"worker stalled");}long elapsed=System.nanoTime()-began;
        check(error.get()==null,"worker failure "+error.get());long total=Arrays.stream(totals).sum();
        check(CNetworkDiagnostics.packetCounts().contains("opcode=70 count="+total+"\n"),"exact count lost");
        check(CNetworkDiagnostics.timings().contains("opcode=70 completed="+total+" "),"exact completion lost");
        check(CNetworkDiagnostics.traffic().contains(total*512+" bytes"),"exact traffic lost");
        long[] combined=new long[Arrays.stream(latencies).mapToInt(a->a.length).sum()];int at=0;for(long[] a:latencies){System.arraycopy(a,0,combined,at,a.length);at+=a.length;}
        Map<String,Object> result=result("diagnostics-"+workers,total,total*512,elapsed,combined);result.put("workers",workers);result.put("process_cpu_ms",(cpuTime()-cpu)/1e6);result.put("latency_kind","64 begin/admitted/end operations;not network packet RTT");return result;
    }
    public static void main(String[] args)throws Exception {
        Map<String,Object> report=new LinkedHashMap<>();report.put("environment",environment());report.put("label",args[0]);report.put("cases",List.of(run(1,3),run(4,3),run(8,3)));report.put("status","passed");write(Path.of("stability-perf-"+args[0]+".json"),report);
    }
}
