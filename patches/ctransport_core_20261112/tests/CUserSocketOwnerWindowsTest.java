package l1j.server.server.model.network.core;
import java.net.*;
import java.nio.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.packet.CPacketHandler;
/** Production CIOUserSocket/codec/buffer pool/FFM/DLL; six game dependency fixtures. */
public final class CUserSocketOwnerWindowsTest {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static void until(BooleanSupplier test)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!test.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(1);check(test.getAsBoolean(),"production owner deadline");}
 static byte[] wire(int opcode)throws Exception{
  Random random=new Random(46);
  for(int attempt=0;attempt<8192;attempt++){
   byte[] frame=new byte[10];random.nextBytes(frame);frame[0]=10;frame[1]=0;
   try(CIOPacketCodecReference codec=new CIOPacketCodecReference(73461)){
    ByteBuffer data=ByteBuffer.allocateDirect(10);data.put(frame).flip();codec.feed(data);int n=codec.next();
    if(n>=4&&(codec.plaintext().get(0)&255)==opcode)return frame;
   }
  }throw new AssertionError("no reference ciphertext candidate");
 }
 static Object field(CIOUserSocket owner,String name)throws Exception{Field f=CIOUserSocket.class.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static void setPause(CIOUserSocket owner,boolean value)throws Exception{Field f=CIOUserSocket.class.getDeclaredField("_sendReadPaused");f.setAccessible(true);f.setBoolean(owner,value);}
 static int phase(CIOUserSocket owner){try{return ((AtomicInteger)field(owner,"nativePacketPhase")).get();}catch(Exception e){throw new AssertionError(e);}}
 static final class Session implements AutoCloseable {
  final AtomicReference<CIOUserSocket> owner=new AtomicReference<>();final AtomicReference<CIOClient> client=new AtomicReference<>();
  final AtomicReference<CTransportSocket> channel=new AtomicReference<>();final CountDownLatch closed=new CountDownLatch(1);
  final CIOBufferPool pool=new CIOBufferPool(8,64,4,4);final CTransportServer server;final Socket peer;
  Session(boolean ready,boolean failCapture)throws Exception{
   CIOThreadCallback.INSTANCE.queued.set(null);
   server=new CTransportServer(new InetSocketAddress("127.0.0.1",0),8,2,32,c->{
    CIOUserSocket socket=new CIOUserSocket(c,pool,CIOThreadCallback.INSTANCE,"127.0.0.1",0,256,x->closed.countDown());
    CIOClient user=new CIOClient(socket);user.ready=ready;user.failCapture=failCapture;socket.bindClient(user);socket.initializeCipher(73461);socket.configureNativeDeadlines(false);
    client.set(user);channel.set(c);socket.startRead();owner.set(socket);
   });peer=new Socket("127.0.0.1",server.localAddress().getPort());until(()->owner.get()!=null);
  }
  public void close()throws Exception{peer.close();server.close();check(closed.await(5,TimeUnit.SECONDS),"production close observer stranded");check(pool.usedRecvSlots()==0&&pool.usedSendSlots()==0,"production pooled lease leak");}
 }
 public static void main(String[] args)throws Exception{
  int game=Opcodes.C_ALIVE&255,login=Opcodes.C_LOGIN&255;byte[] gameWire=wire(game),loginWire=wire(login);
  AtomicInteger calls=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  AtomicReference<Throwable> failure=new AtomicReference<>();
  CPacketHandler.HANDLERS[game]=(buffer,client)->{calls.incrementAndGet();return true;};
  try(Session session=new Session(true,false)){
   session.peer.getOutputStream().write(gameWire);until(()->calls.get()==1);until(()->phase(session.owner.get())==0);
   check(session.channel.get().userSocketState()[6]==0,"inline production handler retained native lease");
  }
  CPacketHandler.HANDLERS[login]=(buffer,client)->{
   calls.incrementAndGet();entered.countDown();try{check(release.await(5,TimeUnit.SECONDS),"handler release");buffer.readUnsignedByte();}catch(Throwable e){failure.set(e);}return true;
  };
  try(Session session=new Session(false,false)){
   session.peer.getOutputStream().write(loginWire);until(()->CIOThreadCallback.INSTANCE.queued.get()!=null);
   var task=CIOThreadCallback.INSTANCE.queued.getAndSet(null);Thread sessionThread=new Thread(task);sessionThread.start();check(entered.await(5,TimeUnit.SECONDS),"production direct handler did not run");
   session.owner.get().close();until(()->session.channel.get().isNativeIoDrained());check(session.closed.getCount()==1,"running plaintext released before handler finished");
   check(session.server.registryDiagnostics()[1]==1,"running lease removed from retired registry");release.countDown();
   check(session.closed.await(5,TimeUnit.SECONDS),"running handler release did not finish close");until(()->session.server.registryDiagnostics()[1]==0);
   check(failure.get()==null&&calls.get()==2&&phase(session.owner.get())==0,"running handler lease mismatch");sessionThread.join(5000);check(!sessionThread.isAlive(),"session owner did not exit");
  }
  CPacketHandler.HANDLERS[login]=(buffer,client)->{calls.incrementAndGet();return true;};
  try(Session session=new Session(false,false)){
   session.peer.getOutputStream().write(loginWire);until(()->CIOThreadCallback.INSTANCE.queued.get()!=null);check(phase(session.owner.get())==2,"session packet was not queued");
   var queued=CIOThreadCallback.INSTANCE.queued.getAndSet(null);session.server.close();
   check(session.closed.await(5,TimeUnit.SECONDS)&&phase(session.owner.get())==0,"shutdown did not cancel queued production packet");queued.run();check(calls.get()==2,"cancelled session task ran after shutdown");
  }
  try(Session session=new Session(true,false)){
   // Field-only pressure is injected after startRead: the next real native
   // callback must retain one waiting lease without dispatching a handler.
   setPause(session.owner.get(),true);session.peer.getOutputStream().write(gameWire);until(()->phase(session.owner.get())==1);
   session.owner.get().close();check(session.closed.await(5,TimeUnit.SECONDS)&&phase(session.owner.get())==0,"SEND-pressure waiting lease stranded");
  }
  try(Session session=new Session(true,true)){
   session.peer.getOutputStream().write(gameWire);check(session.closed.await(5,TimeUnit.SECONDS)&&phase(session.owner.get())==0,"session capture exception stranded reserved native lease");
  }
  check(calls.get()==2,"cancelled or failed packet reached handler");
  System.out.println("PASS actual production CIOUserSocket + Windows DLL: running handler retains plaintext through I/O terminal; queued session/SEND-pressure leases cancel; shutdown and session-capture exception release once; no pooled leases retained (game/account/scheduler/filter fixtures)");
 }
}
