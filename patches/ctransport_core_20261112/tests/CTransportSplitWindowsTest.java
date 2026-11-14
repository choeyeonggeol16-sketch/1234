package l1j.server.server.model.network.core.transport;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.stability.StressMetrics;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static l1j.server.server.model.network.core.stability.StressMetrics.*;

/** Identical completion-ack protocol for same-JVM and separate-client-JVM cases. */
public final class CTransportSplitWindowsTest {
    static final int BATCH=64,SIZE=512;
    static final class Owner extends CUserSocket {
        final CSocket channel;final CIOPacketCodec codec;final Semaphore done=new Semaphore(0);
        final CountDownLatch closed=new CountDownLatch(1);volatile long frames;
        Owner(CSocket c,int account){channel=c;codec=new CIOPacketCodec(c,73461);c.bindUserSocketOwner(this);
            check(c.setAccountIdentity(account)&&c.setCharacterEntered(true)&&c.setUserState(2),"identity");c.pauseNativeUser(false);}
        @Override public boolean transportNativePacket(long sequence,ByteBuffer data,int bytes){
            check(bytes>0&&channel.releaseUserPacket(sequence),"decoder lease");if(++frames%BATCH==0)done.release();return true;}
        @Override public void onTransportTerminated(){closed.countDown();}
        @Override public void close(){channel.close();}
    }
    static Map<String,Object> clients(int dataPort,int controlPort,int count,int seconds)throws Exception {
        CountDownLatch ready=new CountDownLatch(count),start=new CountDownLatch(1);AtomicLong deadline=new AtomicLong();
        List<Future<long[]>> futures=new ArrayList<>();long[] samples=new long[1000000];AtomicInteger sampleCount=new AtomicInteger();
        byte[] wire=new byte[SIZE*BATCH];new Random(17).nextBytes(wire);
        for(int at=0;at<wire.length;at+=SIZE){wire[at]=(byte)SIZE;wire[at+1]=(byte)(SIZE>>>8);}
        try(ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor()){
            for(int i=0;i<count;i++)futures.add(workers.submit(()->{
                try(Socket data=new Socket("127.0.0.1",dataPort);Socket control=new Socket("127.0.0.1",controlPort)){
                    data.setTcpNoDelay(true);control.setTcpNoDelay(true);control.setSoTimeout(15000);
                    DataOutputStream request=new DataOutputStream(control.getOutputStream());DataInputStream ack=new DataInputStream(control.getInputStream());
                    request.writeInt(data.getLocalPort());request.flush();check(ack.readInt()==1,"control admission");ready.countDown();
                    check(start.await(20,TimeUnit.SECONDS),"client start");long batches=0;
                    do{long began=System.nanoTime();data.getOutputStream().write(wire);check(ack.readLong()==++batches*BATCH,"completion count/order");
                        int slot=sampleCount.getAndIncrement();if(slot<samples.length)samples[slot]=System.nanoTime()-began;
                    }while(System.nanoTime()<deadline.get());
                    return new long[]{batches*BATCH};
                }
            }));
            check(ready.await(30,TimeUnit.SECONDS),"client admission barrier");long cpu=cpuTime(),began=System.nanoTime();deadline.set(began+seconds*1_000_000_000L);start.countDown();long frames=0;
            for(Future<long[]> future:futures)frames+=future.get(seconds+30,TimeUnit.SECONDS)[0];long elapsed=System.nanoTime()-began;
            Map<String,Object> result=result("client",frames,frames*SIZE,elapsed,Arrays.copyOf(samples,Math.min(samples.length,sampleCount.get())));
            result.put("client_cpu_ms",(cpuTime()-cpu)/1e6);result.put("latency_samples_total",sampleCount.get());return result;
        }finally{start.countDown();}
    }
    static Map<String,Object> run(boolean split,int clients,int seconds,String name)throws Exception {
        ConcurrentHashMap<Integer,CompletableFuture<Owner>> owners=new ConcurrentHashMap<>();List<Owner> accepted=new CopyOnWriteArrayList<>();AtomicInteger account=new AtomicInteger();
        AtomicReference<Throwable> failure=new AtomicReference<>();AtomicBoolean running=new AtomicBoolean(true);List<Socket> controls=new CopyOnWriteArrayList<>();List<Future<?>> pumps=new ArrayList<>();
        try(CIOServer server=new CIOServer(new InetSocketAddress("127.0.0.1",0),256,4,256,c->{
            Owner owner=new Owner(c,account.incrementAndGet());accepted.add(owner);owners.computeIfAbsent(c.remoteAddress().getPort(),x->new CompletableFuture<>()).complete(owner);
        });ServerSocket control=new ServerSocket(0,256,InetAddress.getLoopbackAddress());ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor()){
            until(()->server.performanceDiagnostics()[0]>0,5000,"performance startup");long baseline=server.objectAllocatorDiagnostics()[1];control.setSoTimeout(1000);
            Future<?> accept=workers.submit(()->{
                while(running.get())try{
                    Socket peer=control.accept();controls.add(peer);peer.setTcpNoDelay(true);peer.setSoTimeout(20000);
                    synchronized(pumps){pumps.add(workers.submit(()->{
                        try(peer){DataInputStream input=new DataInputStream(peer.getInputStream());DataOutputStream output=new DataOutputStream(peer.getOutputStream());
                            Owner owner=owners.computeIfAbsent(input.readInt(),x->new CompletableFuture<>()).get(20,TimeUnit.SECONDS);output.writeInt(1);output.flush();
                            long frames=0;while(running.get()&&owner.channel.isOpen())if(owner.done.tryAcquire(100,TimeUnit.MILLISECONDS)){output.writeLong(frames+=BATCH);output.flush();}
                        }catch(Throwable error){if(running.get())failure.compareAndSet(null,error);}
                    }));}
                }catch(SocketTimeoutException retry){}catch(Throwable error){if(running.get())failure.compareAndSet(null,error);}
            });
            Map<String,Object> before=memory(server);StressMetrics.Sampler sampler=new StressMetrics.Sampler(server);long cpu=cpuTime(),began=System.nanoTime();Instant wall=Instant.now();Map<String,Object> client;
            Process child=null;
            try{
                if(split){Path result=Path.of("stability-split-client-"+name+".json");Files.deleteIfExists(result);
                    child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"--enable-preview","-Xms64m","-Xmx256m","-cp",System.getProperty("java.class.path"),CTransportSplitWindowsTest.class.getName(),"client",Integer.toString(server.localAddress().getPort()),Integer.toString(control.getLocalPort()),Integer.toString(clients),Integer.toString(seconds),result.toString()).inheritIO().start();
                    check(child.waitFor(seconds+60,TimeUnit.SECONDS)&&child.exitValue()==0,"client process failed");
                    client=readClient(result);
                }else client=clients(server.localAddress().getPort(),control.getLocalPort(),clients,seconds);
                long elapsed=System.nanoTime()-began;Map<String,Object> result=new LinkedHashMap<>(client);result.put("name",name);result.put("separate_client_jvm",split);result.put("clients",clients);result.put("frame_bytes",SIZE);
                measured(name,wall,Instant.now(),elapsed,cpuTime()-cpu,result);result.put("parent_elapsed_ms",elapsed/1e6);result.put("peak_server_process",sampler.finish());result.put("before",before);
                check(accepted.size()==clients,"accepted count");until(()->accepted.stream().allMatch(o->o.closed.getCount()==0),15000,"client close completion");
                check(accepted.stream().mapToLong(o->o.frames).sum()==((Number)client.get("frames")).longValue(),"server/client count mismatch");
                for(Owner owner:accepted)owner.codec.close();quiesce(server,baseline);result.put("after",memory(server));check(failure.get()==null,"control pump failed: "+failure.get());
                System.out.println("MEASURE "+name+" "+client.get("frames")+" frames "+client.get("elapsed_ms")+" ms "+client.get("latency"));return result;
            }finally{
                running.set(false);sampler.close();if(child!=null&&child.isAlive())child.destroyForcibly();control.close();for(Socket peer:controls)try{peer.close();}catch(IOException ignored){}
                accept.get(5,TimeUnit.SECONDS);synchronized(pumps){for(Future<?> pump:pumps)pump.get(5,TimeUnit.SECONDS);}
            }
        }
    }
    // Client output is a small fixed schema; avoid adding a production JSON dependency.
    static Map<String,Object> readClient(Path file)throws Exception {
        String json=Files.readString(file);Map<String,Object> result=new LinkedHashMap<>(),latency=new LinkedHashMap<>();
        for(String key:List.of("frames","wire_bytes","elapsed_ms","frames_per_second","mib_per_second","client_cpu_ms","latency_samples_total")){
            var m=java.util.regex.Pattern.compile("\""+key+"\":([0-9.Ee+-]+)").matcher(json);check(m.find(),"client result "+key);result.put(key,Double.valueOf(m.group(1)));}
        for(String key:List.of("p50_ms","p95_ms","p99_ms","max_ms","samples")){
            var m=java.util.regex.Pattern.compile("\""+key+"\":([0-9.Ee+-]+)").matcher(json);check(m.find(),"client latency "+key);latency.put(key,Double.valueOf(m.group(1)));}
        result.put("latency",latency);return result;
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("client")){write(Path.of(args[5]),clients(Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]),Integer.parseInt(args[4])));return;}
        Map<String,Object> report=new LinkedHashMap<>();report.put("environment",environment());report.put("scope","server production DLL/FFM/L1 decoder;game owner fixture;separate client JVM shares host CPUs;64-frame completion ACK over separate TCP control socket in BOTH modes;not comparable to prior semaphore latency");List<Object> cases=new ArrayList<>();report.put("cases",cases);
        try{cases.add(run(false,16,2,"warmup"));cases.add(run(false,64,8,"same-jvm"));cases.add(run(true,64,8,"split-jvm"));cases.add(run(true,64,8,"split-jvm-repeat"));report.put("status","passed");}
        catch(Throwable failure){report.put("status","failed");report.put("failure",failure.toString());throw failure;}
        finally{write(Path.of("stability-split.json"),report);}
    }
}
