import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.lang.reflect.*;
import l1j.server.server.ThreadPool;

public class ThreadPoolRegression {
 static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
 static void await(CountDownLatch gate)throws Exception { check(gate.await(3,TimeUnit.SECONDS),"latch timeout"); }
 static Object field(Object o,String name)throws Exception {
  Field f=ThreadPool.class.getDeclaredField(name);f.setAccessible(true);return f.get(o);
 }
 static ThreadPool pool()throws Exception {
  var c=ThreadPool.class.getDeclaredConstructor();c.setAccessible(true);return c.newInstance();
 }
 static void reject(Runnable r) {
  try { r.run();throw new AssertionError("submission accepted"); }catch(RejectedExecutionException expected){}
 }
 static void idle(CountDownLatch gate) { boolean interrupted=false;
  for(;;)try{gate.await();break;}catch(InterruptedException expected){interrupted=true;}
  if(interrupted)Thread.currentThread().interrupt();
 }
 static void cap()throws Exception {
  var c=Class.forName("l1j.server.server.ThreadPool$BoundedScheduledExecutor").getDeclaredConstructor(int.class,int.class,ThreadFactory.class,RejectedExecutionHandler.class);c.setAccessible(true);
  var s=(ScheduledThreadPoolExecutor)c.newInstance(1,2,Executors.defaultThreadFactory(),new ThreadPoolExecutor.AbortPolicy());s.setRemoveOnCancelPolicy(true);
  try { var first=s.schedule(()->{},1,TimeUnit.DAYS);s.schedule(()->{},1,TimeUnit.DAYS);
   reject(()->s.schedule(()->{},1,TimeUnit.DAYS));check(s.getQueue().size()==2,"scheduler cap");
   first.cancel(false);check(s.getQueue().size()==1,"cancelled scheduler job retained");
  }finally{s.shutdownNow();check(s.awaitTermination(3,TimeUnit.SECONDS),"tiny scheduler termination");}
  System.out.println("PASS bounded scheduler admission and immediate cancel removal");
 }
 static void drain()throws Exception {
  var p=pool();var entered=new CountDownLatch(1);var gate=new CountDownLatch(1);var ran=new AtomicBoolean();
  p.execute((Runnable)()->{entered.countDown();idle(gate);});await(entered);
  p.execute((Runnable)()->ran.set(true));
  var delayed=p.schedule(()->{throw new AssertionError("late timer executed");},TimeUnit.DAYS.toMillis(1));
  var recurring=p.scheduleAtFixedRate(()->{},10000,10000);
  try {
   long t=System.nanoTime();check(!p.shutdown(40,TimeUnit.MILLISECONDS),"premature shutdown success");
   check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-t)<1000,"shutdown budget ignored");
   var instant=(ThreadPoolExecutor)field(p,"_instantPool");
   check(!ran.get()&&instant.getQueue().size()==1,"accepted instant work dropped");
   check(delayed.isCancelled()&&recurring.isCancelled(),"timers not cancelled");
   reject(()->p.execute((Runnable)()->{}));reject(()->p.scheduleRequired(()->{},0));
   reject(()->p.scheduleAtFixedRateRequired(()->{},0,1));reject(()->p.scheduleWithFixedDelayRequired(()->{},0,1));
   reject(()->p.scheduleAtFixedRateLongRequired(()->{},0,1));reject(()->p.schedulePriorityTaskAtFixedRateRequired(()->{},0,1));
   check(p.schedule(()->{},0)==null,"nullable API compatibility lost");
   var raw=new Thread(()->{throw new AssertionError("late thread started");});
   reject(()->p.startDedicatedThread(raw));reject(()->p.execute(raw));
   check(raw.getState()==Thread.State.NEW,"rejected Thread started");
  }finally{gate.countDown();check(p.shutdown(3,TimeUnit.SECONDS),"drain did not complete");}
  check(ran.get(),"queued instant work never executed");
  System.out.println("PASS shutdown timeout retains accepted work; retry drains; timers and post-stop admission handled");
 }
 static void blocking()throws Exception {
  var p=pool();var entered=new CountDownLatch(1);var gate=new CountDownLatch(1);var virtual=new AtomicBoolean();
  var work=p.scheduleBlocking(()->{virtual.set(Thread.currentThread().isVirtual());entered.countDown();idle(gate);},0);
  try {
   await(entered);check(virtual.get(),"blocking job is not on virtual thread");
   var timer=new CountDownLatch(1);p.scheduleRequired(timer::countDown,0);await(timer);
   check(work.cancel(true),"cancel failed");
   var permits=(Semaphore)field(p,"blockingSlots");
   check(permits.availablePermits()==255,"running cancellation released capacity early");
   check(!p.shutdown(40,TimeUnit.MILLISECONDS),"running cancelled job mistaken for terminated");
  }finally{gate.countDown();check(p.shutdown(3,TimeUnit.SECONDS),"blocking job not joined");}
  check(((Semaphore)field(p,"blockingSlots")).availablePermits()==256,"running reservation leak");
  System.out.println("PASS sleeping event does not block scheduler; cancelled work tracked until actual exit");
 }
 static void blockingCap()throws Exception {
  var p=pool();var jobs=new ArrayList<Future<?>>();
  try {
   for(int i=0;i<256;i++)jobs.add(p.scheduleBlocking(()->{},TimeUnit.DAYS.toMillis(1)));
   reject(()->p.scheduleBlocking(()->{},0));jobs.get(0).cancel(true);
   jobs.add(p.scheduleBlocking(()->{},TimeUnit.DAYS.toMillis(1)));
   check(((ScheduledThreadPoolExecutor)field(p,"_scheduler")).getQueue().size()==256,"cancelled launch retained");
  }finally{for(var f:jobs)f.cancel(true);check(p.shutdown(3,TimeUnit.SECONDS),"reserved jobs did not retire");}
  check(((Semaphore)field(p,"blockingSlots")).availablePermits()==256,"reserved permit leak");
  System.out.println("PASS blocking admission capped at 256; cancelling pending jobs returns capacity");
 }
 static void selfAwait()throws Exception {
  var p=pool();var checked=new CountDownLatch(3);var errors=new ConcurrentLinkedQueue<Throwable>();
  Runnable attempt=()->{try{p.shutdown(1,TimeUnit.SECONDS);errors.add(new AssertionError("self await accepted"));}
    catch(IllegalStateException expected){if(p.isShutdown())errors.add(new AssertionError("self wait began shutdown"));}
    finally{checked.countDown();}};
  try {
   p.execute(attempt);p.scheduleBlocking(attempt,0);p.scheduleRequired(attempt,0);await(checked);check(errors.isEmpty(),"self wait errors "+errors);
  }finally{check(p.shutdown(3,TimeUnit.SECONDS),"self wait fixture cleanup");}
  System.out.println("PASS pool and virtual workers reject self-await before stopping admission");
 }
 static void dedicated()throws Exception {
  var p=pool();var entered=new CountDownLatch(1);var interrupted=new CountDownLatch(1);var done=new CountDownLatch(1);var result=new AtomicBoolean();
  p.startDedicatedThread(new Thread(()->{entered.countDown();try{Thread.sleep(60000);}catch(InterruptedException ok){interrupted.countDown();}},"managed-legacy"));await(entered);
  p.startDedicatedThread(new Thread(()->{try{result.set(p.shutdown(3,TimeUnit.SECONDS));}finally{done.countDown();}},"shutdown-countdown"));
  await(done);await(interrupted);check(result.get(),"dedicated shutdown coordinator joined itself");
  check(p.shutdown(3,TimeUnit.SECONDS),"dedicated exit not observed");
  System.out.println("PASS dedicated shutdown caller can coordinate; other dedicated threads interrupted and joined");
 }
 static void race()throws Exception {
  var p=pool();var ready=new CountDownLatch(1);var error=new AtomicReference<Throwable>();
  var producer=new Thread(()->{ready.countDown();try{for(int i=0;i<1000;i++)p.scheduleBlocking(()->{},0).cancel(true);}
    catch(RejectedExecutionException expected){}catch(Throwable t){error.set(t);}},"cancellation-racer");
  producer.start();await(ready);check(p.shutdown(3,TimeUnit.SECONDS),"raced shutdown not complete");producer.join(3000);
  check(!producer.isAlive()&&error.get()==null,"producer race failure "+error.get());
  check(((Semaphore)field(p,"blockingSlots")).availablePermits()==256,"race permit leak");
  System.out.println("PASS concurrent submission, launch, cancellation and shutdown leave no reservations");
 }
 static void admissionRace()throws Exception {
  var p=pool();var accepted=new AtomicInteger();var ran=new AtomicInteger();var error=new AtomicReference<Throwable>();
  var ready=new CountDownLatch(3);var start=new CountDownLatch(1);var producers=new ArrayList<Thread>();
  for(int j=0;j<3;j++) {
   var t=new Thread(()->{ready.countDown();idle(start);try{for(int i=0;i<2000;i++){p.execute((Runnable)ran::incrementAndGet);accepted.incrementAndGet();}}
    catch(RejectedExecutionException expected){}catch(Throwable failure){error.set(failure);}});
   producers.add(t);t.start();
  }
  await(ready);start.countDown();
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
  while(accepted.get()<100&&System.nanoTime()<deadline)Thread.onSpinWait();
  check(p.shutdown(3,TimeUnit.SECONDS),"concurrent instant drain timed out");
  for(var t:producers){t.join(3000);check(!t.isAlive(),"producer did not exit");}
  check(error.get()==null&&accepted.get()==ran.get()&&ran.get()>=100,"admitted work lost during race");
  System.out.println("PASS concurrent instant admission/shutdown executes every accepted job ("+ran.get()+")");
 }
 public static void main(String[] args)throws Exception {
  java.util.logging.Logger.getLogger(ThreadPool.class.getName()).setLevel(java.util.logging.Level.OFF);
  cap();drain();blocking();blockingCap();selfAwait();dedicated();race();admissionRace();
  System.out.println("Actual ThreadPool, ThreadConfig, ConfigReader, TimeUtil, PacketThreadFactory compiled and executed on Java 21.");
 }
}
