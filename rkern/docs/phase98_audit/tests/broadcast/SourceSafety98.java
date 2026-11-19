package l1j.server.server.model.network.core.broadcast;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.transport.CIOServer;
public class SourceSafety98 {
 static void check(boolean ok, String what){if(!ok)throw new AssertionError(what);}
 static CAsyncSendConstBuffer.Source src(CIOServer server){return new CAsyncSendConstBuffer.Source(server,MemorySegment.NULL,new byte[]{3,0,1});}
 static void testDoubleRelease() {
  var owner=new CIOServer();var source=src(owner);
  source.release();
  boolean threw=false;try{source.release();}catch(IllegalStateException e){threw=true;}
  check(threw,"double release must throw");
  threw=false;try{source.retain();}catch(IllegalStateException e){threw=true;}
  check(threw,"invalid released Source must never resurrect");
  check(owner.releases.get()==1,"native released exactly once");
 }
 static void testWriterFailure() {
  var owner=new CIOServer();owner.throwOnRelease=true;
  var target=new CUserSocket(owner);
  IllegalArgumentException original=new IllegalArgumentException("writer failure");
  Throwable caught=null;
  try { CAsyncSendConstBuffer.openWrittenSource(target,b->{throw original;}); }
  catch(Throwable ex){caught=ex;}
  check(caught==original,"original encoder exception must survive cleanup failure");
  check(caught.getSuppressed().length==1,"cleanup failure must be suppressed");
  check(owner.releases.get()==1,"Native release attempted once");
 }
 static void testOverflowFallbackAndFailedCleanup() {
  var owner=new CIOServer();
  var target=new CUserSocket(owner);
  byte[] bytes=new byte[16383];
  var writer=(CSharedBroadcast.NativeWireWriter) out->out.put(bytes);
  try(var shared=new CAsyncSendConstBuffer(writer,-1,Integer.MAX_VALUE)) {
   check(shared.send(target,1),"valid large overflow must reach existing frozen direct path");
   check(shared.send(target,2),"large frame is shared among recipients");
  }
  check(owner.releases.get()==1,"small failed slab must be released before large fallback");
  var broken=new CIOServer();broken.throwOnRelease=true;
  IllegalStateException rejected=null;
  try(var shared=new CAsyncSendConstBuffer(writer,-1,Integer.MAX_VALUE)) {
   shared.send(new CUserSocket(broken),1);
  }catch(IllegalStateException failure){ rejected=failure; }
  check(rejected!=null,"oversized Native source release failure must abort fallback");
  check(rejected.getCause() instanceof java.nio.BufferOverflowException,
        "overflow cause must be preserved");
  check(rejected.getCause().getSuppressed().length==1,
        "native cleanup error must remain attached to overflow");
  check(broken.releases.get()==1,"failed Native cleanup attempted exactly once");
 }
 static void testValidRefcount() throws Exception {
  var owner=new CIOServer();var source=src(owner);
  int workers=8,reps=10000;
  ExecutorService pool=Executors.newFixedThreadPool(workers);
  CountDownLatch start=new CountDownLatch(1);
  Future<?>[] results=new Future<?>[workers];
  for(int i=0;i<workers;i++)results[i]=pool.submit(()->{
   try{start.await();for(int k=0;k<reps;k++){source.retain();source.release();}}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
  });
  start.countDown();
  try{for(var f:results)f.get(15,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  check(owner.releases.get()==0,"owner ref must remain");
  source.release();
  check(owner.releases.get()==1,"native must release once after all references");
 }
 public static void main(String[] args) throws Exception {
  testDoubleRelease();testWriterFailure();testValidRefcount();testOverflowFallbackAndFailedCleanup();
  System.out.println("SourceSafety98: PASS (double release, suppressed native cleanup, concurrent refs, large overflow)");
 }
}
