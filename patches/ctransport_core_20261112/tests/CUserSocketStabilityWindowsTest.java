package l1j.server.server.model.network.core.connection;

import l1j.server.server.model.network.core.transport.CIOServer;
import l1j.server.server.model.network.core.transport.CIOPacketCodecReference;
import l1j.server.server.model.network.core.stability.StressMetrics;
import java.io.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static l1j.server.server.model.network.core.stability.StressMetrics.*;

/** Real production CUserSocket,pools,SEND FIFO,cipher,FFM and Windows DLL. */
public final class CUserSocketStabilityWindowsTest {
    static Map<String,Object> send(CSharedBroadcastWindowsTest.World world,int each,int size,String name)throws Exception {
        final int clients=world.users.length,bodySize=size-2,window=4;
        long[] latency=new long[clients*each];AtomicLongArray[] sent=new AtomicLongArray[clients];
        List<Future<?>> tasks=new ArrayList<>();CountDownLatch ready=new CountDownLatch(clients*2),start=new CountDownLatch(1);
        Thread[] threads=new Thread[clients*2];AtomicIntegerArray progress=new AtomicIntegerArray(clients*2);
        Map<String,Object> before=memory(world.server);
        try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()){
            for(int n=0;n<clients;n++){
                final int client=n;sent[n]=new AtomicLongArray(each);Semaphore permits=new Semaphore(window);
                tasks.add(executor.submit(()->{
                    threads[client*2]=Thread.currentThread();
                    byte[] actual=new byte[size];ByteBuffer expected=ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
                    byte[] tail=new byte[bodySize-13];for(int k=0;k<tail.length;k++)tail[k]=(byte)(k*17+9);
                    InputStream input=world.peers[client].getInputStream();ready.countDown();check(start.await(10,TimeUnit.SECONDS),"reader start barrier");
                    for(int sequence=0;sequence<each;sequence++){
                        for(int at=0;at<size;){int count=input.read(actual,at,size-at);check(count>0,"premature S2C EOF");at+=count;}
                        long ended=System.nanoTime(),stamp=sent[client].get(sequence);check(stamp>0,"unpublished S2C timestamp");
                        expected.clear();expected.putShort((short)size).put((byte)0x46).putInt(sequence).putLong(stamp).put(tail);
                        world.codecs[client].encrypt(expected,2,bodySize);
                        for(int k=0;k<size;k++)if(actual[k]!=expected.get(k))throw new AssertionError("S2C byte/cipher/FIFO mismatch client="+client+" seq="+sequence+" offset="+k);
                        latency[client*each+sequence]=ended-stamp;permits.release();
                        progress.set(client*2,sequence+1);
                    }
                    return null;
                }));
                tasks.add(executor.submit(()->{
                    threads[client*2+1]=Thread.currentThread();
                    byte[] tail=new byte[bodySize-13];for(int k=0;k<tail.length;k++)tail[k]=(byte)(k*17+9);
                    CUserSocket owner=world.users[client];ready.countDown();check(start.await(10,TimeUnit.SECONDS),"writer start barrier");
                    for(int sequence=0;sequence<each;sequence++){
                        check(permits.tryAcquire(15,TimeUnit.SECONDS),"S2C writer window stalled");
                        CIOFrame frame=owner.beginDirectFrame(size);check(frame!=null,"bounded S2C admission rejected");boolean committed=false;
                        try{
                            long stamp=System.nanoTime();sent[client].set(sequence,stamp);
                            frame.buffer().writeC(0x46);frame.buffer().writeD(sequence);frame.buffer().writeLong(stamp);frame.buffer().writeByte(tail);
                            check(committed=owner.commitDirectFrame(frame),"S2C commit failed");
                            progress.set(client*2+1,sequence+1);
                        }finally{if(!committed)owner.abortDirectFrame(frame);}
                    }
                    return null;
                }));
            }
            check(ready.await(10,TimeUnit.SECONDS),"S2C admission barrier");Instant wall=Instant.now();long cpu=cpuTime(),began=System.nanoTime();StressMetrics.Sampler sampler=new StressMetrics.Sampler(world.server);start.countDown();
            try{for(Future<?> task:tasks)task.get(90,TimeUnit.SECONDS);}
            catch(Throwable failure){
                Map<String,Object> dump=new LinkedHashMap<>();dump.put("phase",name);List<Object> users=new ArrayList<>();
                for(int n=0;n<clients;n++){
                    Map<String,Object> row=new LinkedHashMap<>();CUserSocket user=world.users[n];row.put("client",n);row.put("read_frames",progress.get(n*2));row.put("sent_frames",progress.get(n*2+1));row.put("open",user.isOpen());
                    for(Field field:CUserSocket.class.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&(field.getType().isPrimitive()||field.getType()==AtomicInteger.class)){
                        field.setAccessible(true);row.put(field.getName(),String.valueOf(field.get(user)));
                    }
                    Field channelField=CUserSocket.class.getDeclaredField("_channel");channelField.setAccessible(true);Object channel=channelField.get(user);Map<String,Object> channelState=new LinkedHashMap<>();
                    for(Field field:channel.getClass().getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&(field.getType().isPrimitive()||field.getName().equals("writeOwner"))){field.setAccessible(true);channelState.put(field.getName(),String.valueOf(field.get(channel)));}row.put("channel",channelState);
                    Field callbacks=CUserSocket.class.getDeclaredField("_socketCallbacks");callbacks.setAccessible(true);long[] slots=(long[])callbacks.get(user);Field head=CUserSocket.class.getDeclaredField("_socketHead");head.setAccessible(true);row.put("head_slot",slots[(int)head.getLong(user)&511]);
                    for(int k=0;k<2;k++)if(threads[n*2+k]!=null)row.put(k==0?"reader_stack":"writer_stack",Arrays.toString(threads[n*2+k].getStackTrace()));users.add(row);
                }
                dump.put("users",users);List<String> errors=new ArrayList<>();
                for(Future<?> task:tasks)if(task.isDone()&&!task.isCancelled())try{task.get();}catch(Exception error){errors.add(error.toString());failure.addSuppressed(error);}
                dump.put("other_task_errors",errors);write(Path.of("stability-failure.json"),dump);dump.put("memory",memory(world.server));dump.put("java_pool",Arrays.deepToString(world.pool.snapshot()));write(Path.of("stability-failure.json"),dump);
                for(Socket socket:world.peers)try{socket.close();}catch(IOException ignored){}for(Future<?> task:tasks)task.cancel(true);throw failure;
            }
            long elapsed=System.nanoTime()-began,cpuNanos=cpuTime()-cpu;Instant ended=Instant.now();Map<String,Long> peak=sampler.finish();until(()->Arrays.stream(world.users).allMatch(CUserSocket::isWriteIdle),15000,"S2C drain");
            healthy(world.server);check(world.pool.usedSendSlots()==0,"S2C Java lease retained");
            Map<String,Object> result=result(name,(long)clients*each,(long)clients*each*size,elapsed,latency);
            measured(name,wall,ended,elapsed,cpuNanos,result);result.put("peak_samples",peak);
            result.put("clients",clients);result.put("window",window);result.put("frame_bytes",size);result.put("latency_kind","producer timestamp through client read;includes localhost stack/client scheduling;all bytes compared using separate rolling-key state in the same supplied codec DLL");
            result.put("before",before);result.put("after",memory(world.server));
            System.out.println("MEASURE "+name+" frames="+result.get("frames")+" elapsed_ms="+result.get("elapsed_ms")+" fps="+result.get("frames_per_second")+" "+result.get("latency"));return result;
        }finally{start.countDown();}
    }
    static List<WeakReference<CUserSocket>> cycle(int each)throws Exception {
        List<WeakReference<CUserSocket>> references=new ArrayList<>();
        try(CSharedBroadcastWindowsTest.World world=new CSharedBroadcastWindowsTest.World()){
            send(world,each,15,"reconnect-cycle");
            for(CUserSocket user:world.users)references.add(new WeakReference<>(user));
        }
        return references;
    }
    static Map<String,Object> pressure()throws Exception {
        long began=System.nanoTime();AtomicInteger accepted=new AtomicInteger(),refused=new AtomicInteger();
        try(CSharedBroadcastWindowsTest.World world=new CSharedBroadcastWindowsTest.World();ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()){
            for(Socket peer:world.peers)peer.setReceiveBufferSize(1024);
            byte[] payload=new byte[4094];List<Future<?>> tasks=new ArrayList<>();CountDownLatch loaded=new CountDownLatch(world.users.length);
            for(CUserSocket user:world.users)tasks.add(executor.submit(()->{
                for(int n=0;n<10000&&!user.isCloseComplete();n++){
                    CIOFrame frame=user.beginSafeDirectFrame(4096,512*1024);
                    if(frame==null){refused.incrementAndGet();break;}
                    boolean committed=false;
                    try{frame.buffer().writeByte(payload);committed=user.commitDirectFrame(frame);if(committed)accepted.incrementAndGet();}
                    finally{if(!committed)user.abortDirectFrame(frame);}
                    if(!committed)break;
                }
                loaded.countDown();return null;
            }));
            check(loaded.await(15,TimeUnit.SECONDS),"slow reader limit did not settle");
            check(accepted.get()>0&&refused.get()>0,"slow reader did not exercise backpressure");
            // Exercise abortive close and producers racing final shutdown.
            for(Socket peer:world.peers){peer.setSoLinger(true,0);peer.close();}
            for(Future<?> task:tasks)task.get(15,TimeUnit.SECONDS);
            until(()->Arrays.stream(world.users).allMatch(CUserSocket::isCloseComplete),15000,"slow peer close drain");
            check(world.pool.usedSendSlots()==0&&world.pool.usedRecvSlots()==0,"slow peer Java lease leak");
            long[] faults=world.server.poolDiagnostics();check(faults[3]==0&&faults[19]==0&&faults[20]==0&&faults[21]==0&&world.server.sendDiagnostics()[8]==0,"slow peer corruption/owner fault");
            Map<String,Object> result=new LinkedHashMap<>();result.put("name","slow-reader-and-RST");result.put("elapsed_ms",(System.nanoTime()-began)/1e6);
            result.put("accepted_frames",accepted.get());result.put("backpressure_refusals",refused.get());result.put("after",memory(world.server));
            System.out.println("MEASURE slow-reader-and-RST accepted="+accepted+" refused="+refused);return result;
        }
    }
    static Map<String,Object> closeRace(boolean localClose)throws Exception {
        long began=System.nanoTime();CountDownLatch held=new CountDownLatch(4),release=new CountDownLatch(1);
        AtomicInteger accepted=new AtomicInteger();
        try(CSharedBroadcastWindowsTest.World world=new CSharedBroadcastWindowsTest.World();ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()){
            List<Future<?>> futures=new ArrayList<>();
            for(CUserSocket user:world.users)futures.add(executor.submit(()->{
                CIOFrame frame=user.beginDirectFrame(64);check(frame!=null,"race reservation failed");boolean committed=false;
                try{
                    held.countDown();check(release.await(15,TimeUnit.SECONDS),"race producer fence");
                    frame.buffer().writeByte(new byte[62]);committed=user.commitDirectFrame(frame);
                    // Native close-start is an early observer notification, not
                    // the Java CLOSED transition. An earlier reservation may
                    // still be admitted and then fail asynchronously. Explicit
                    // Java close must reject; both paths must release all leases.
                    if(localClose)check(!committed,"closed connection accepted held encoder");
                    if(committed)accepted.incrementAndGet();
                }finally{if(!committed)user.abortDirectFrame(frame);}
                return null;
            }));
            try{
                check(held.await(10,TimeUnit.SECONDS),"race encoders not held");
                for(Socket peer:world.peers){peer.setSoLinger(true,0);peer.close();}
                until(()->Arrays.stream(world.users).allMatch(CUserSocket::isCloseStarted),10000,"RST did not enter close");
                if(localClose)for(CUserSocket user:world.users)user.close();
                release.countDown();for(Future<?> future:futures)future.get(15,TimeUnit.SECONDS);
                until(()->Arrays.stream(world.users).allMatch(CUserSocket::isCloseComplete),10000,"held encoder close did not finish");
                check(world.pool.usedSendSlots()==0&&world.pool.usedRecvSlots()==0,"held encoder lease leak");healthy(world.server);
                return Map.of("name",localClose?"java-close-with-four-held-encoders":"RST-with-four-held-encoders","elapsed_ms",(System.nanoTime()-began)/1e6,"admitted_reserved_frames",accepted.get(),"after",memory(world.server));
            }finally{release.countDown();}
        }
    }
    public static void main(String[] args)throws Exception {
        Map<String,Object> report=new LinkedHashMap<>();List<Object> cases=new ArrayList<>();report.put("environment",environment());report.put("cases",cases);
        long began=System.nanoTime();
        try(StressMetrics.Profile profile=new StressMetrics.Profile("java");CIOServer probe=new CIOServer(new InetSocketAddress("127.0.0.1",0),8,1,8,c->c.close(),0)){
            until(()->probe.performanceDiagnostics()[0]>0,5000,"probe sampler startup");
            long baseline=probe.objectAllocatorDiagnostics()[1];report.put("baseline",memory(probe));
            try(CSharedBroadcastWindowsTest.World world=new CSharedBroadcastWindowsTest.World()){
                cases.add(send(world,1024,15,"warmup"));
                for(int round=1;round<=3;round++)cases.add(send(world,262144,15,"production-S2C-small-"+round));
                cases.add(send(world,32768,4096,"production-S2C-4k"));
            }
            quiesce(probe,baseline);report.put("after_send_cleanup",memory(probe));
            List<WeakReference<CUserSocket>> references=new ArrayList<>();List<Object> waves=new ArrayList<>();
            for(int wave=0;wave<8;wave++){
                references.addAll(cycle(256));quiesce(probe,baseline);System.gc();Thread.sleep(1100);
                Map<String,Object> snapshot=memory(probe);snapshot.put("wave",wave+1);snapshot.put("weak_users_remaining",references.stream().filter(x->x.get()!=null).count());waves.add(snapshot);
            }
            report.put("reconnect_memory_waves",waves);System.gc();until(()->references.stream().allMatch(x->x.get()==null),10000,"closed Java users retained by GC roots");
            report.put("collected_java_users",references.size());cases.add(pressure());quiesce(probe,baseline);
            cases.add(closeRace(false));quiesce(probe,baseline);cases.add(closeRace(true));quiesce(probe,baseline);
            System.gc();Thread.sleep(1100);report.put("final",memory(probe));report.put("profile",profile.finish());report.put("status","passed");
        }catch(Throwable failure){report.put("status","failed");report.put("failure",failure.toString());throw failure;}
        finally{report.put("total_elapsed_ms",(System.nanoTime()-began)/1e6);write(Path.of("stability-java.json"),report);}
    }
}
