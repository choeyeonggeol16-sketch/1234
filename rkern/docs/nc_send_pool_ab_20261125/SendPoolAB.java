package l1j.server.server.model.network.core.connection;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.Arrays;

public final class SendPoolAB {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    private static void checkCapacity() throws Exception {
        var pool = new CIOBufferPool(1, 130, 1, 5, 2);
        var all = new CIOBufferEx[130];
        var identities = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<CIOBufferEx,Boolean>());
        for (int i=0;i<all.length;i++) {
            all[i]=pool.acquireSend(256);
            check(all[i]!=null && identities.add(all[i]), "every slot usable exactly once");
        }
        check(pool.acquireSend(256)==null, "bounded capacity");
        check(pool.usedSmallSendSlots()==130, "used slots");
        var executor=Executors.newFixedThreadPool(4);
        try {
            for (var lease:all) executor.submit(()->pool.release(lease)).get(5,TimeUnit.SECONDS);
            check(pool.usedSendSlots()==0, "cross-thread return");
            for(int i=0;i<all.length;i++) {
                all[i]=pool.acquireSend(256);
                check(all[i]!=null && identities.remove(all[i]),"reuse unique slot");
            }
            check(identities.isEmpty(),"all physical slots recovered");
            for(var lease:all) pool.release(lease);
            for(int i=0;i<5;i++) all[i]=pool.acquireSend(20000);
            check(pool.acquireSend(20000)==null,"large capacity");
            for(int i=0;i<5;i++) {check(all[i]!=null,"large lease");pool.release(all[i]);}
            check(pool.usedSendSlots()==0,"all leases released");
            check(pool.allocatedDirectBytes()<=pool.maxDirectBytes(),"memory cap");
        } finally {executor.shutdownNow();}
    }
    private static void checkGeneration() {
        CIOSlotPool pool;
        try { pool=CIOSlotPool.class.getDeclaredConstructor(int.class,int.class,int.class,boolean.class).newInstance(16,1,0,true); }
        catch(NoSuchMethodException old) { pool=new CIOSlotPool(16,1,0); }
        catch(ReflectiveOperationException bad) {throw new AssertionError(bad);}
        int slot=pool.acquire(), gen=pool.generation(slot);
        check(pool.validate(slot,gen),"active generation");
        pool.release(slot,gen);
        check(!pool.validate(slot,gen),"stale generation invalid");
        check(pool.acquire()==slot,"same slot recycled");
        try {pool.release(slot,gen); throw new AssertionError("stale release succeeded");}
        catch(IllegalStateException expected){}
        pool.release(slot,pool.generation(slot));
        check(pool.freeCount()==1,"generation return");
    }
    // Each producer borrows BATCH slots and writes real payload bytes. In cross
    // mode each consumer returns a neighbour's batch. Barriers are identical
    // between builds; no per-operation task or collection allocation.
    private static final int BATCH=32;
    private static void measure(int threads, boolean cross, int seconds) throws Exception {
        var pool=new CIOBufferPool(1,2048,1,16,2);
        var batch=new CIOBufferEx[threads][BATCH];
        var barrier=new CyclicBarrier(threads);
        var start=new CountDownLatch(1);
        var failure=new AtomicReference<Throwable>();
        long[] counts=new long[threads], cpu=new long[threads];
        long[] end={0}, begin={0};
        var bean=java.lang.management.ManagementFactory.getThreadMXBean();
        Thread[] workers=new Thread[threads];
        for(int j=0;j<threads;j++){
            final int id=j;
            workers[j]=Thread.ofPlatform().unstarted(()->{
                try{
                    start.await();
                    long cpuStart=bean.getCurrentThreadCpuTime();
                    long rounds=0;
                    do{
                        CIOBufferEx[] mine=batch[id];
                        for(int i=0;i<BATCH;i++){
                            var lease=pool.acquireSend(256);
                            check(lease!=null,"unexpected exhaustion");
                            mine[i]=lease;
                            lease.buffer().putInt(2,id).putInt(6,i);
                        }
                        if(cross) barrier.await(10,TimeUnit.SECONDS);
                        CIOBufferEx[] returned=batch[cross?(id+threads-1)%threads:id];
                        for(int i=0;i<BATCH;i++){
                            var lease=returned[i];
                            check(lease.buffer().getInt(2)==(cross?(id+threads-1)%threads:id) &&
                                  lease.buffer().getInt(6)==i,"payload ownership");
                            pool.release(lease);
                            returned[i]=null;
                        }
                        if(cross) barrier.await(10,TimeUnit.SECONDS);
                        rounds++;
                        if(cross) {
                            // All parties choose the same stop boundary.
                            if(id==0) begin[0]=System.nanoTime();
                            barrier.await(10,TimeUnit.SECONDS);
                            if(begin[0]>=end[0]) break;
                        } else if(System.nanoTime()>=end[0]) break;
                    }while(true);
                    cpu[id]=bean.getCurrentThreadCpuTime()-cpuStart;
                    counts[id]=rounds*BATCH;
                }catch(Throwable t){failure.compareAndSet(null,t);barrier.reset();}
            });
            workers[j].start();
        }
        long started=System.nanoTime();
        end[0]=started+seconds*1_000_000_000L;
        start.countDown();
        for(var t:workers){t.join((seconds+15L)*1000L);check(!t.isAlive(),"worker timeout");}
        if(failure.get()!=null)throw new AssertionError(failure.get());
        long elapsed=System.nanoTime()-started, operations=Arrays.stream(counts).sum();
        check(pool.usedSendSlots()==0,"all measured leases returned");
        System.out.printf(java.util.Locale.ROOT,"POOL_RESULT %d %s %.3f %.3f %d %d%n",threads,cross,
                operations*1e9/elapsed,(double)Arrays.stream(cpu).sum()/operations,pool.allocatedDirectBytes(),operations);
    }
    public static void main(String[] args)throws Exception {
        checkCapacity();checkGeneration();
        if(args.length==0){System.out.println("PASS pool capacity, cross-thread return, stale generation, direct memory cap");return;}
        int threads=Integer.parseInt(args[0]);boolean cross=Boolean.parseBoolean(args[1]);
        measure(threads,cross,2); // warm JVM
        measure(threads,cross,4);
    }
}
