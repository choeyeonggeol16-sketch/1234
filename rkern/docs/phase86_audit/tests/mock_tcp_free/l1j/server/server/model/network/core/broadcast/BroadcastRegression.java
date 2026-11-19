package l1j.server.server.model.network.core.broadcast;
import java.nio.*;import java.util.concurrent.atomic.AtomicInteger;
import l1j.server.server.model.network.core.transport.CIOServer;
import l1j.server.server.model.network.core.connection.CUserSocket;
public class BroadcastRegression {
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static CSharedBroadcast.NativeWireWriter frameWriter(int size,AtomicInteger calls){
  return b->{calls.incrementAndGet(); for(int i=2;i<size;i++)b.put((byte)(i*17+3));};
 }
 static void verify(CUserSocket t,int n){check(t.frames.size()==1,"recipient frames");byte[] arr=t.frames.get(0);
  check(arr.length==n,"length"+n+" got "+arr.length);
  check(((arr[0]&255)|((arr[1]&255)<<8))==n,"header");
  for(int i=2;i<n;i++)check(arr[i]==(byte)(i*17+3),"payload at "+i);
 }
 static void testSmall(int size) {
  CIOServer a=new CIOServer(), b=new CIOServer(); CUserSocket owner=new CUserSocket(a), foreign=new CUserSocket(b);
  CUserSocket noConst=new CUserSocket(a);noConst.constCapable=false;
  AtomicInteger calls=new AtomicInteger();
  var shared=CAsyncSendConstBufferExceptSelf.forWritten(owner,42,frameWriter(size,calls));
  check(!shared.send(owner,42),"self filtered");
  check(shared.send(owner,7),"first accepted");
  check(shared.send(foreign,8),"other owner accepted");
  check(shared.send(noConst,9),"const unsupported accepted");
  shared.close();shared.close();
  check(calls.get()==1,"small encode once"); check(owner.nativeAllocs==1,"one owner alloc");
  check(owner.sharedSends==1 && foreign.directSends==1 && noConst.directSends==1,"mixed routes");
  verify(owner,size);verify(foreign,size);verify(noConst,size);
  check(a.releases==0,"owner ref stays with active shared lease");owner.drain();
  check(a.releases==1,"owner released after drain");
  boolean closed=false;try{shared.send(owner,3);}catch(IllegalStateException e){closed=true;}check(closed,"closed send refused");
 }
 static void testLarge(int size) {
  CIOServer a=new CIOServer(), b=new CIOServer(); CUserSocket owner=new CUserSocket(a),foreign=new CUserSocket(b);
  AtomicInteger calls=new AtomicInteger();
  var shared=CAsyncSendConstBufferExceptSelf.forWritten(owner,42,frameWriter(size,calls));
  check(shared.send(owner,7),"first large");check(shared.send(foreign,8),"second large");shared.close();
  verify(owner,size);verify(foreign,size);check(owner.directSends==1&&foreign.directSends==1,"large route");
  check(calls.get()==2,"once failed short then one long encode");
  check(a.releases==1,"short attempted source returned");
 }
 static void testOversized() {
  CIOServer a=new CIOServer();CUserSocket s=new CUserSocket(a);
  var shared=CAsyncSendConstBufferExceptSelf.forWritten(s,42,frameWriter(65536,new AtomicInteger()));
  boolean bad=false;try{shared.send(s,7);}catch(RuntimeException e){bad=true;}finally{shared.close();}
  check(bad,"oversize rejected");check(a.releases==1,"16KiB source returned on error");
 }
 static void testLazyNoRecipients() {
   CIOServer a=new CIOServer();CUserSocket socket=new CUserSocket(a);socket.connected=false;
   AtomicInteger calls=new AtomicInteger();
   var shared=CAsyncSendConstBufferExceptSelf.forWritten(socket,42,frameWriter(64,calls));
   check(shared==null,"disconnected returns null");
   socket.connected=true;
   shared=CAsyncSendConstBufferExceptSelf.forWritten(socket,42,frameWriter(64,calls));
   check(!shared.send(socket,42),"sender exclusion");shared.close();
   check(calls.get()==0&&a.releases==0&&socket.nativeAllocs==0,"no allocation without viewer");
 }
 static void testParallelFanout() throws Exception {
   CIOServer a=new CIOServer();CUserSocket[] sockets=new CUserSocket[128];
   for(int i=0;i<sockets.length;i++)sockets[i]=new CUserSocket(a);
   AtomicInteger calls=new AtomicInteger();
   var shared=CAsyncSendConstBufferExceptSelf.forWritten(sockets[0],42,frameWriter(497,calls));
   try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
    var futures=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
    for(int i=0;i<sockets.length;i++) {
     final int index=i;
     futures.add(pool.submit(()->shared.send(sockets[index],index+100)));
    }
    for(var f:futures)check(f.get(),"parallel admitted");
   }
   shared.close();check(calls.get()==1,"parallel single source");
   for(var s:sockets){verify(s,497);s.drain();}
   check(a.releases==1,"parallel release only once");
 }

 static void testFiveThousand() {
  final int clients=5000;
  CIOServer sharedServer=new CIOServer();
  CUserSocket[] recipients=new CUserSocket[clients];
  for(int i=0;i<clients;i++)recipients[i]=new CUserSocket(sharedServer);
  AtomicInteger encoded=new AtomicInteger();
  CAsyncSendConstBuffer first=new CAsyncSendConstBuffer(frameWriter(128,encoded),-1,Integer.MAX_VALUE);
  CAsyncSendConstBuffer second=new CAsyncSendConstBuffer(frameWriter(384,encoded),-1,Integer.MAX_VALUE);
  long t=System.nanoTime();
  for(int i=0;i<clients;i++) {
   check(first.send(recipients[i],i),"5000 first accepted: "+i);
   check(second.send(recipients[i],i),"5000 second accepted: "+i);
  }
  first.close();second.close();
  check(encoded.get()==2,"5000 fanout encoded only twice");
  check(sharedServer.releases==0,"5000 leases remain alive until drain");
  for(int i=0;i<clients;i++) {
   CUserSocket s=recipients[i];
   check(s.nativeAllocs==(i==0?2:0),"only initial owner native allocs");
   check(s.frames.size()==2,"2 FIFO frames per recipient");
   check(s.frames.get(0).length==128 && s.frames.get(1).length==384,"FIFO order preserved");
   check(s.sharedSends==2,"shared fanout admitted");
   s.drain();
  }
  check(sharedServer.releases==2,"5000 leases released once per source");
  System.out.printf("Broadcast: 5000 synthetic recipients, 2 frames/person, 2 assemblies, refs clean, %.2f ms (mock, not IOCP)%n",(System.nanoTime()-t)/1e6);
 }
 public static void main(String[]a) throws Exception {testSmall(3);testSmall(8192);testSmall(16384);
  testLarge(16385);testLarge(65535);testOversized();testLazyNoRecipients();testParallelFanout();testFiveThousand();
  System.out.println("Broadcast: 8 boundary/concurrency + 5000 mock fanout PASS");}
}
