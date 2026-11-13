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
/** Production CUserSocket/codec/buffer pool/FFM/DLL; six game dependency fixtures. */
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
 static Object field(CUserSocket owner,String name)throws Exception{Field f=CUserSocket.class.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 static void setPause(CUserSocket owner,boolean value)throws Exception{Field f=CUserSocket.class.getDeclaredField("_sendReadPaused");f.setAccessible(true);f.setBoolean(owner,value);}
 static int phase(CUserSocket owner){try{return ((AtomicInteger)field(owner,"nativePacketPhase")).get();}catch(Exception e){throw new AssertionError(e);}}
 static final class Session implements AutoCloseable {
  final AtomicReference<CUserSocket> owner=new AtomicReference<>();final AtomicReference<CIOClient> client=new AtomicReference<>();
  final AtomicReference<CSocket> channel=new AtomicReference<>();final CountDownLatch closed=new CountDownLatch(1);
  final CIOBufferPool pool;final CIOServer server;final Socket peer;
  Session(boolean ready,boolean failCapture)throws Exception{
   this(ready,failCapture,new CIOBufferPool(8,64,4,4));
  }
  Session(boolean ready,boolean failCapture,CIOBufferPool buffers)throws Exception{
   pool=buffers;
   CIOThreadCallback.INSTANCE.queued.set(null);
   server=new CIOServer(new InetSocketAddress("127.0.0.1",0),8,2,32,c->{
    CUserSocket socket=new CUserSocket(c,pool,CIOThreadCallback.INSTANCE,"127.0.0.1",0,256,x->closed.countDown());
    CIOClient user=new CIOClient(socket);user.ready=ready;user.failCapture=failCapture;socket.bindClient(user);socket.SetKey(73461);socket.configureNativeDeadlines(false);
    client.set(user);channel.set(c);socket.startRead();owner.set(socket);
   });peer=new Socket("127.0.0.1",server.localAddress().getPort());until(()->owner.get()!=null);
  }
  public void close()throws Exception{peer.close();server.close();check(closed.await(5,TimeUnit.SECONDS),"production close observer stranded");check(pool.usedRecvSlots()==0&&pool.usedSendSlots()==0,"production pooled lease leak");}
 }
 static byte[] expectedPacket(CIOPacketCodecReference codec,byte[] payload,boolean plain){
  ByteBuffer data=ByteBuffer.allocateDirect(payload.length+2).order(ByteOrder.LITTLE_ENDIAN);data.putShort((short)(payload.length+2)).put(payload);
  if(!plain)codec.encrypt(data,2,payload.length);byte[] wire=new byte[data.capacity()];data.clear();data.get(wire);return wire;
 }
 static void checkRead(Session session,byte[] expected)throws Exception{byte[] actual=session.peer.getInputStream().readNBytes(expected.length);check(Arrays.equals(actual,expected),"production native S2C cipher/frame order mismatch");}
 public static void main(String[] args)throws Exception{
  try(Session session=new Session(true,false)){
   CUserSocket owner=session.owner.get();
   check(owner.bindVerifiedMacIdentity(0x8122334455667788L),"production identity-provider hook");
   owner.onAccountChanged(new l1j.server.server.Account());
   owner.onCharacterChanged(new l1j.server.server.model.Instance.L1PcInstance());
   long[] state=session.channel.get().userSocketState();
   check(state.length==14&&state[11]==1&&state[12]==1&&state[13]==1,"production account/character/MAC hook not native");
   owner.onSessionStateChanged(l1j.server.server.model.network.utill.ConnectionState.AUTHENTICATED);
   check(session.server.accountRegistryDiagnostics()[1]==1&&session.server.accountRegistryDiagnostics()[2]==0,"production character select transition");
   owner.onAccountChanged(null);check(session.server.accountRegistryDiagnostics()[1]==0,"production account clear");
  }
  System.out.println("PASS production CUserSocket account/character/MAC provider hooks and restart/account-clear identities through real Windows FFM");
  try(Session session=new Session(true,false)){
   session.peer.setSoTimeout(5000);check(session.server.closeGracefully(session.channel.get().id(),5000),"direct graceful admission");
   try{check(session.peer.getInputStream().read()==-1,"direct graceful EOF");}
   catch(Exception failure){throw new AssertionError("direct native graceful terminal:send="+Arrays.toString(session.server.sendDiagnostics()),failure);}
   session.peer.shutdownOutput();
   check(session.closed.await(5,TimeUnit.SECONDS),"direct graceful production owner terminal");
   long[] send=session.server.sendDiagnostics();check(send[4]==1&&send[5]==0,"direct graceful mode");
  }
  System.out.println("PASS real Windows native graceful close without output:normal EOF and production owner cleanup");
  try(Session session=new Session(true,false);CIOPacketCodecReference codec=new CIOPacketCodecReference(73461)){
   session.peer.setSoTimeout(5000);CUserSocket owner=session.owner.get();
   byte[] key={1,2,3,4,5,6,7,8};
   check(owner.tryWritePacket(session.client.get(),new l1j.server.server.model.network.packet.BytesWritablePacket(key)),"production first plain packet admission");checkRead(session,expectedPacket(codec,key,true));
   byte[] data=new byte[65533];new Random(518).nextBytes(data);
   check(owner.tryWritePacket(session.client.get(),new l1j.server.server.model.network.packet.BytesWritablePacket(data)),"production max S2C frame admission");checkRead(session,expectedPacket(codec,data,false));
   int[] objects={100,101,102,103,104,105,106,107};int[] actions={1,2,3,4,5,6,7,8};
   check(owner.tryWriteActionBatch(objects,actions,0,8),"production packed action admission");
   for(int i=0;i<8;i++){ByteBuffer body=ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);body.put((byte)Opcodes.S_ACTION).putInt(objects[i]).put((byte)actions[i]);checkRead(session,expectedPacket(codec,body.array(),false));}
   byte[] last={9,8,7,6};owner.requestClientClose(new l1j.server.server.model.network.packet.BytesWritablePacket(last));checkRead(session,expectedPacket(codec,last,false));
   try{check(session.peer.getInputStream().read()==-1,"graceful close did not deliver EOF after final packet");}
   catch(Exception failure){throw new AssertionError("graceful terminal:send="+Arrays.toString(session.server.sendDiagnostics())+",close="+Arrays.toString(session.client.get().closeStack),failure);}
   session.peer.shutdownOutput();
   check(session.closed.await(5,TimeUnit.SECONDS),"production graceful terminal");
   long[] send=session.server.sendDiagnostics();check(send[2]==10&&send[3]==1&&send[4]==1&&send[5]==0&&send[7]==0&&send[8]==0,"production cipher/normal-close diagnostics");
  }
  System.out.println("PASS production native SEND:plain first packet,max65535 S2C frame,8 packed action frames and final packet match unchanged codec;graceful final-byte delivery+EOF without RST;zero pending and owner violations");
  try(Session session=new Session(true,false);CIOPacketCodecReference codec=new CIOPacketCodecReference(73461)){
   session.peer.setSoTimeout(5000);CUserSocket owner=session.owner.get();byte[] key={1,2,3,4,5,6,7,8};
   check(owner.tryWritePacket(session.client.get(),new l1j.server.server.model.network.packet.BytesWritablePacket(key)),"const path plain key");
   checkRead(session,expectedPacket(codec,key,true));until(owner::isWriteIdle);
   CountDownLatch entered=new CountDownLatch(2),releaseWorkers=new CountDownLatch(1);
   CIOObject blocker=new CIOObject(){public void run(){entered.countDown();try{check(releaseWorkers.await(5,TimeUnit.SECONDS),"const test worker release");}catch(InterruptedException e){throw new AssertionError(e);}}public void cancel(){throw new AssertionError("running const test blocker cancelled");}};
   check(session.server.postCompletion(blocker),"const first blocker");until(()->entered.getCount()==1);
   check(session.server.postCompletion(blocker)&&entered.await(5,TimeUnit.SECONDS),"const second blocker");
   byte[] first=new byte[20000],second=new byte[20000],last={6,5,4,3,2,1};new Random(918).nextBytes(first);new Random(719).nextBytes(second);
   try{
    for(byte[] bytes:new byte[][]{first,second,last})check(owner.tryWritePacket(session.client.get(),new l1j.server.server.model.network.packet.BytesWritablePacket(bytes)),"const queued production packet");
   }finally{releaseWorkers.countDown();}
   for(byte[] bytes:new byte[][]{first,second,last})checkRead(session,expectedPacket(codec,bytes,false));
   until(owner::isWriteIdle);long[] send=session.server.sendDiagnostics();
   check(send[0]==3&&send[2]==3&&send[3]==1&&send[7]==0&&send[8]==0,"production gather did not use one constant-buffer request or cipher ownership failed");
  }
  System.out.println("PASS production CSocket::__async_AsyncSendConstBuffer:blocked completions force two queued frames into one gather;20K frame crosses16K pool blocks;unchanged codec and FIFO match;CIOBufferEx leases release");
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
  try(Session session=new Session(false,false)){
   session.peer.getOutputStream().write(loginWire);until(()->CIOThreadCallback.INSTANCE.queued.get()!=null);
   var queued=CIOThreadCallback.INSTANCE.queued.getAndSet(null);setPause(session.owner.get(),true);queued.run();
   check(phase(session.owner.get())==1,"queued owner did not yield to SEND pressure");
   session.owner.get().close();check(session.closed.await(5,TimeUnit.SECONDS)&&phase(session.owner.get())==0,"owner yield/close stranded lease");
  }
  try(Session session=new Session(true,true)){
   session.peer.getOutputStream().write(gameWire);check(session.closed.await(5,TimeUnit.SECONDS)&&phase(session.owner.get())==0,"session capture exception stranded reserved native lease");
  }
  check(calls.get()==2,"cancelled or failed packet reached handler");
  System.out.println("PASS actual production CUserSocket + Windows DLL: running handler retains plaintext through I/O terminal; queued session/SEND-pressure leases cancel; shutdown and session-capture exception release once; no pooled leases retained (game/account/scheduler/filter fixtures)");
 }
}
