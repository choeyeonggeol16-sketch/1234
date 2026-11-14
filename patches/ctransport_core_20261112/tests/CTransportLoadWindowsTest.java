package l1j.server.server.model.network.core.transport;

import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.stability.StressMetrics;
import static l1j.server.server.model.network.core.stability.StressMetrics.*;

/** Actual Windows DLL/FFM/L1 decoder;game packet owner is the existing smoke fixture. */
public final class CTransportLoadWindowsTest {
    static final int BATCH=64;
    static final int[] FRAGMENTS={1,3,11,137,4093};
    static long digest(long value,ByteBuffer bytes,int length){value^=length;for(int i=0;i<length;i++)value=(value^(bytes.get(i)&255))*0x100000001b3L;return value;}
    static final class Owner extends CUserSocket {
        final CSocket channel;final CIOPacketCodec codec;final Semaphore completed=new Semaphore(0);
        final CountDownLatch closed=new CountDownLatch(1);long frames,checksum=0xcbf29ce484222325L;final boolean integrity;
        Owner(CSocket channel,int account,boolean integrity){
            this.integrity=integrity;
            this.channel=channel;codec=new CIOPacketCodec(channel,73461);channel.bindUserSocketOwner(this);
            check(channel.setAccountIdentity(account)&&channel.setCharacterEntered(true)&&channel.setUserState(2),"benchmark identity admission");
            channel.pauseNativeUser(false);
        }
        @Override public boolean transportNativePacket(long sequence,ByteBuffer bytes,int length){
            check(length>0,"empty decoded packet");if(integrity)checksum=digest(checksum,bytes,length);
            check(channel.releaseUserPacket(sequence),"packet decoder/release failure");
            if(++frames%BATCH==0)completed.release();return true;
        }
        @Override public void onTransportTerminated(){closed.countDown();}
        @Override public void close(){channel.close();}
    }
    static Map<String,Object> run(int workers,int clients,int each,int size,boolean fragmented,String name)throws Exception {
        return run(workers,clients,each,size,fragmented,false,name);
    }
    static Map<String,Object> run(int workers,int clients,int each,int size,boolean fragmented,boolean integrity,String name)throws Exception {
        ConcurrentHashMap<Integer,CompletableFuture<Owner>> owners=new ConcurrentHashMap<>();AtomicInteger account=new AtomicInteger();
        List<Socket> sockets=new CopyOnWriteArrayList<>();List<Future<Long>> futures=new ArrayList<>();AtomicLong deadline=new AtomicLong();AtomicInteger samples=new AtomicInteger();
        try(CIOServer server=new CIOServer(new InetSocketAddress("127.0.0.1",0),512,workers,512,channel->{
            Owner owner=new Owner(channel,account.incrementAndGet(),integrity);owners.computeIfAbsent(channel.remoteAddress().getPort(),x->new CompletableFuture<>()).complete(owner);
        });ExecutorService pool=Executors.newVirtualThreadPerTaskExecutor()){
            until(()->server.performanceDiagnostics()[0]>0,5000,"native memory sampler startup");
            long baseline=server.objectAllocatorDiagnostics()[1];Map<String,Object> before=memory(server);
            CountDownLatch ready=new CountDownLatch(clients),start=new CountDownLatch(1);
            long[] durations=new long[200000];
            byte[] frame=new byte[size];new Random(173).nextBytes(frame);frame[0]=(byte)size;frame[1]=(byte)(size>>>8);
            byte[] wire=new byte[size*BATCH];for(int i=0;i<BATCH;i++)System.arraycopy(frame,0,wire,i*size,size);
            for(int index=0;index<clients;index++){
                final int client=index;futures.add(pool.submit(()->{
                    try(Socket socket=new Socket("127.0.0.1",server.localAddress().getPort());CIOPacketCodecReference reference=integrity?new CIOPacketCodecReference(73461):null){
                        sockets.add(socket);socket.setTcpNoDelay(true);socket.setSoTimeout(15000);
                        Owner owner=owners.computeIfAbsent(socket.getLocalPort(),x->new CompletableFuture<>()).get(15,TimeUnit.SECONDS);
                        OutputStream output=socket.getOutputStream();ready.countDown();check(start.await(15,TimeUnit.SECONDS),"benchmark start barrier");
                        int batch=0;long expected=0xcbf29ce484222325L;ByteBuffer input=integrity?ByteBuffer.allocateDirect(size):null;
                        do{
                            if(integrity)for(int packet=0;packet<BATCH;packet++){
                                input.clear();input.put(frame).flip();reference.feed(input);int length=reference.next();check(length>0,"reference decoder did not produce packet");expected=digest(expected,reference.plaintext(),length);
                            }
                            long began=System.nanoTime();
                            if(fragmented){int at=0;for(int n=0;at<wire.length;n++){int bytes=Math.min(wire.length-at,FRAGMENTS[n%FRAGMENTS.length]);output.write(wire,at,bytes);at+=bytes;}}
                            else output.write(wire);
                            check(owner.completed.tryAcquire(15,TimeUnit.SECONDS),"native packet completion timed out");
                            if(integrity)check(owner.checksum==expected,"decoded payload checksum/order mismatch");
                            int sample=samples.getAndIncrement();if(sample<durations.length)durations[sample]=System.nanoTime()-began;batch++;
                        }while(batch<each/BATCH||System.nanoTime()<deadline.get());
                        check(owner.frames==(long)batch*BATCH,"native packet loss/duplicate");owner.close();check(owner.closed.await(15,TimeUnit.SECONDS),"native packet close timed out");owner.codec.close();
                        return (long)batch*BATCH;
                    }catch(Exception e){throw new CompletionException(e);}
                }));
            }
            try{
                check(ready.await(20,TimeUnit.SECONDS),"benchmark connection admission timed out");
                Instant wall=Instant.now();long cpu=cpuTime(),began=System.nanoTime(),total=0;deadline.set(began+(name.equals("warmup")||integrity?0:3_000_000_000L));
                StressMetrics.Sampler sampler=new StressMetrics.Sampler(server);start.countDown();
                for(Future<Long> future:futures)total+=future.get(90,TimeUnit.SECONDS);
                long elapsed=System.nanoTime()-began,cpuNanos=cpuTime()-cpu;Instant ended=Instant.now();Map<String,Long> peak=sampler.finish();
                Map<String,Object> result=result(name,total,total*size,elapsed,Arrays.copyOf(durations,Math.min(samples.get(),durations.length)));
                measured(name,wall,ended,elapsed,cpuNanos,result);result.put("peak_samples",peak);result.put("latency_samples_total",samples.get());
                result.put("requested_workers",workers);result.put("clients",clients);result.put("frame_bytes",size);
                result.put("payload_checksum_verified",integrity);
                result.put("latency_kind","64-frame socket write through final decoder callback;includes client wakeup;not an RTT");
                long[] threads=server.threadDiagnostics();int observed=0;for(int n=0;n<8;n++)if(threads[n*3]>0)observed++;result.put("observed_io_workers",observed);
                result.put("before",before);long cleanup=System.nanoTime();quiesce(server,baseline);result.put("cleanup_ms",(System.nanoTime()-cleanup)/1e6);result.put("after",memory(server));
                System.out.println("MEASURE "+name+" frames="+total+" elapsed_ms="+result.get("elapsed_ms")+" fps="+result.get("frames_per_second")+" "+result.get("latency"));
                return result;
            }finally{
                start.countDown();for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}for(Future<?> future:futures)future.cancel(true);
            }
        }
    }
    public static void main(String[] args)throws Exception {
        Map<String,Object> report=new LinkedHashMap<>();List<Object> results=new ArrayList<>();report.put("environment",environment());report.put("cases",results);
        long began=System.nanoTime();try(StressMetrics.Profile profile=new StressMetrics.Profile("rx")){
            results.add(run(2,8,1024,512,false,"warmup"));
            results.add(run(1,16,16384,512,false,"rx-workers1"));
            results.add(run(2,16,16384,512,false,"rx-workers2"));
            results.add(run(8,16,16384,512,false,"rx-workers8"));
            results.add(run(8,64,4096,512,false,"rx-clients64"));
            results.add(run(4,8,2048,8192,true,"rx-fragmented8192"));
            results.add(run(1,16,16384,512,false,"rx-workers1-repeat"));
            results.add(run(4,8,8192,512,false,true,"rx-checksum512"));
            results.add(run(4,4,1024,8192,true,true,"rx-checksum8192"));
            report.put("profile",profile.finish());report.put("status","passed");
        }catch(Throwable failure){report.put("status","failed");report.put("failure",failure.toString());throw failure;}
        finally{report.put("total_elapsed_ms",(System.nanoTime()-began)/1e6);write(Path.of("stability-rx.json"),report);}
    }
}
