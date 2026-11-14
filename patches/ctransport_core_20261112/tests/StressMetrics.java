package l1j.server.server.model.network.core.stability;

import l1j.server.server.model.network.core.transport.CIOServer;
import java.lang.management.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import jdk.jfr.*;
import jdk.jfr.consumer.*;

/** Measurement helpers only; no mock timing or production policy changes. */
public final class StressMetrics {
    private StressMetrics() {}
    public static void check(boolean value,String reason) { if(!value)throw new AssertionError(reason); }
    public static void until(BooleanSupplier predicate,long timeoutMillis,String reason)throws Exception {
        long deadline=System.nanoTime()+timeoutMillis*1_000_000L;
        while(!predicate.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(10);
        check(predicate.getAsBoolean(),reason);
    }
    public static Map<String,Object> environment() {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("utc",Instant.now().toString());v.put("os",System.getProperty("os.name")+" "+System.getProperty("os.version"));
        v.put("java",System.getProperty("java.runtime.version"));v.put("cpus",Runtime.getRuntime().availableProcessors());
        v.put("cpu",System.getenv("PROCESSOR_IDENTIFIER"));v.put("scope","same-process Windows localhost; clients and correctness verifier share CPU; game/DB are fixtures");
        return v;
    }
    public static Map<String,Object> memory(CIOServer server) {
        Map<String,Object> v=new LinkedHashMap<>();MemoryUsage heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        v.put("heap_used",heap.getUsed());v.put("heap_committed",heap.getCommitted());
        v.put("non_heap_used",ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());
        v.put("loaded_classes",ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
        for(BufferPoolMXBean p:ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class))if(p.getName().equals("direct")){
            v.put("direct_used",p.getMemoryUsed());v.put("direct_buffers",p.getCount());
        }
        long[] a=server.objectAllocatorDiagnostics(),p=server.poolDiagnostics(),r=server.referenceDiagnostics(),s=server.sendDiagnostics();
        check(a.length>=16&&p.length>=24&&r.length>=16,"missing native measurement extensions");
        v.put("object_live",a[1]);v.put("object_live_bytes",a[2]);v.put("object_allocations",a[5]);v.put("object_returns",a[6]);
        v.put("object_alloc_failures",a[7]);v.put("object_errors",a[8]);v.put("object_cached_bytes",a[9]);
        v.put("pool_borrowed_8k",p[10]);v.put("pool_borrowed_16k",p[14]);v.put("pool_cached_bytes",p[17]);
        v.put("pool_exhaustions",p[1]);v.put("pool_init_failures",p[2]);v.put("pool_corruptions",p[3]);
        v.put("pool_quarantined",p[19]+p[20]);v.put("reference_errors",p[21]);
        v.put("verifier_active",r[2]);v.put("verifier_suspects",r[4]);v.put("verifier_skipped",r[5]);
        v.put("timer_errors",r[12]);v.put("ort_mismatches",r[13]);v.put("send_pending",s[7]);v.put("send_owner_errors",s[8]);
        long[] perf=server.performanceDiagnostics();
        v.put("perf_samples",perf[0]);v.put("cpu_permille",perf[3]);v.put("working_set",perf[4]);v.put("private_bytes",perf[5]);
        v.put("perf_availability_flags",perf[15]);
        return v;
    }
    public static void healthy(CIOServer server) {
        long[] a=server.objectAllocatorDiagnostics(),p=server.poolDiagnostics(),r=server.referenceDiagnostics(),s=server.sendDiagnostics();
        check(a[7]==0&&a[8]==0,"object allocator failure/corruption");
        check(p[1]==0&&p[2]==0&&p[3]==0&&p[19]==0&&p[20]==0&&p[21]==0,"native buffer pool/reference fault");
        check(r[4]==0&&r[12]==0&&r[13]==0,"reference verifier/timer/ORT fault");
        check(s[8]==0,"native SEND serializer owner violation");
    }
    public static void quiesce(CIOServer server,long baselineLive)throws Exception {
        until(()->{
            long[] p=server.poolDiagnostics(),r=server.referenceDiagnostics(),a=server.objectAllocatorDiagnostics(),g=server.registryDiagnostics(),s=server.sendDiagnostics();
            return g[0]==0&&g[1]==0&&p[10]==0&&p[14]==0&&r[2]==0&&a[1]==baselineLive&&s[7]==0;
        },40000,"connections/pools/object lifetimes failed to return to baseline: "+memory(server));
        healthy(server);long[] accounts=server.accountRegistryDiagnostics();
        check(accounts[0]==0&&accounts[1]==0&&accounts[2]==0,"account/character registry retained connections");
    }
    public static Map<String,Object> latency(long[] values) {
        long[] sorted=values.clone();Arrays.sort(sorted);Map<String,Object> v=new LinkedHashMap<>();
        for(int percentile:new int[]{50,95,99})v.put("p"+percentile+"_ms",sorted[Math.min(sorted.length-1,(int)Math.ceil(sorted.length*percentile/100.0)-1)]/1e6);
        v.put("max_ms",sorted[sorted.length-1]/1e6);v.put("samples",sorted.length);return v;
    }
    public static Map<String,Object> result(String name,long frames,long bytes,long elapsed,long[] latency) {
        Map<String,Object> v=new LinkedHashMap<>();v.put("name",name);v.put("frames",frames);v.put("wire_bytes",bytes);
        v.put("elapsed_ms",elapsed/1e6);v.put("frames_per_second",frames*1e9/elapsed);v.put("mib_per_second",bytes*1e9/elapsed/1048576.0);
        if(latency!=null)v.put("latency",latency(latency));return v;
    }
    public static void write(Path path,Object value)throws Exception {Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,json(value)+"\n");}
    private static String json(Object value) {
        if(value==null)return "null";
        if(value instanceof String s)return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t")+"\"";
        if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map<?,?> m){StringJoiner j=new StringJoiner(",","{","}");m.forEach((k,v)->j.add(json(k.toString())+":"+json(v)));return j.toString();}
        if(value instanceof Iterable<?> a){StringJoiner j=new StringJoiner(",","[","]");for(Object v:a)j.add(json(v));return j.toString();}
        return json(value.toString());
    }
    public static final class Profile implements AutoCloseable {
        private final Recording recording;private final Path file;
        public Profile(String name)throws Exception {
            file=Path.of("stability-"+name+".jfr");recording=new Recording(Configuration.getConfiguration("profile"));
            // Never put CI environment variables or arbitrary JVM properties into a published recording.
            recording.disable("jdk.InitialEnvironmentVariable");recording.disable("jdk.InitialSystemProperty");recording.disable("jdk.InitialSecurityProperty");
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
            recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1));
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1));recording.start();
        }
        public Map<String,Object> finish()throws Exception {
            recording.stop();recording.dump(file);recording.close();Map<String,Long> frames=new HashMap<>();long samples=0,parks=0,gcs=0;
            try(RecordingFile input=new RecordingFile(file)){
                while(input.hasMoreEvents()){
                    RecordedEvent event=input.readEvent();String name=event.getEventType().getName();
                    if(name.equals("jdk.ThreadPark"))parks++;if(name.equals("jdk.GarbageCollection"))gcs++;
                    if(!name.equals("jdk.ExecutionSample")&&!name.equals("jdk.NativeMethodSample"))continue;samples++;
                    RecordedStackTrace stack=event.getStackTrace();if(stack==null)continue;
                    for(RecordedFrame frame:stack.getFrames()){
                        String type=frame.getMethod().getType().getName();
                        if(type.startsWith("l1j.server.server.model.network.core.")&&!type.contains("Test")&&!type.contains("StressMetrics")&&!type.contains("CIOPacketCodecReference")){
                            frames.merge(type+"."+frame.getMethod().getName(),1L,Long::sum);break;
                        }
                    }
                }
            }
            List<Object> top=new ArrayList<>();frames.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(12).forEach(e->top.add(Map.of("method",e.getKey(),"samples",e.getValue())));
            return Map.of("execution_samples",samples,"sampled_core_methods",top,"thread_park_events",parks,"gc_events",gcs,"recording",file.toString(),"limit","statistical Java stack samples;not an exclusive native CPU profile");
        }
        public void close(){recording.close();}
    }
}
