package l1j.server.server.model.network.core.transport;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.diagnostics.CPerfMon;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
/** Real Windows IOCP/FFM/native L1 codec; game-world owner is a fixture. */
public final class CTransportSessionWindowsTest {
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static void until(BooleanSupplier f)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!f.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(1);check(f.getAsBoolean(),"session stress deadline");}
 static final class Owner extends CUserSocket {
  final CSocket channel;final CIOPacketCodec codec;final AtomicInteger frames=new AtomicInteger();final CountDownLatch closed=new CountDownLatch(1);
  Owner(CSocket c){channel=c;codec=new CIOPacketCodec(c,73461);c.bindUserSocketOwner(this);c.pauseNativeUser(false);}
  @Override public boolean transportNativePacket(long sequence,java.nio.ByteBuffer bytes,int length){if(length>0)check(channel.releaseUserPacket(sequence),"stress packet release");frames.incrementAndGet();return true;}
  @Override public void onTransportTerminated(){closed.countDown();}
  @Override public void close(){channel.close();}
 }
 public static void main(String[] args)throws Exception{
  ConcurrentHashMap<Integer,Owner> owners=new ConcurrentHashMap<>();
  try(CIOServer server=new CIOServer(new InetSocketAddress("127.0.0.1",0),32,4,32,c->owners.put(c.remoteAddress().getPort(),new Owner(c)),0,1);
      Socket first=new Socket("127.0.0.1",server.localAddress().getPort());Socket second=new Socket("127.0.0.1",server.localAddress().getPort())){
   until(()->owners.size()==2);Owner a=owners.get(first.getLocalPort()),b=owners.get(second.getLocalPort());
   check(!a.channel.setAccountIdentity(1),"MAC-required auth admitted missing identity");
   check(a.channel.setMacIdentity(0xaabbccddeeffL)&&a.channel.setAccountIdentity(1)&&a.channel.setCharacterEntered(true),"verified MAC account admission");
   check(!b.channel.setMacIdentity(0xaabbccddeeffL)&&!b.channel.setAccountIdentity(2),"MAC cap bypassed");
   a.close();check(a.closed.await(10,TimeUnit.SECONDS),"MAC owner terminal");
   check(b.channel.setMacIdentity(0xaabbccddeeffL)&&b.channel.setAccountIdentity(2),"MAC capacity not released");
   check(!a.channel.setAccountIdentity(3),"stale socket identity admitted");
  }
  System.out.println("PASS Windows MAC policy: unidentified authentication denied, verified one-time binding/cap, terminal capacity reuse and stale handle rejection");
  owners.clear();AtomicInteger nextAccount=new AtomicInteger(1000);AtomicInteger completed=new AtomicInteger();
  long started=System.nanoTime();
  try(CIOServer server=new CIOServer(new InetSocketAddress("127.0.0.1",0),128,8,128,c->{
    Owner own=new Owner(c);check(c.setAccountIdentity(nextAccount.incrementAndGet())&&c.setCharacterEntered(true)&&c.setUserState(2),"stress account admission");owners.put(c.remoteAddress().getPort(),own);
   });ExecutorService clients=Executors.newVirtualThreadPerTaskExecutor()){
   byte[] frame=new byte[512];new Random(173).nextBytes(frame);frame[0]=0;frame[1]=2;
   byte[] wire=new byte[512*128];for(int at=0;at<wire.length;at+=512)System.arraycopy(frame,0,wire,at,512);
   long stressDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
   for(int round=0;round<8||System.nanoTime()<stressDeadline;round++){
    List<Future<?>> futures=new ArrayList<>();
    for(int n=0;n<32;n++)futures.add(clients.submit(()->{
     try(Socket socket=new Socket("127.0.0.1",server.localAddress().getPort())){
      int port=socket.getLocalPort();until(()->owners.containsKey(port));Owner own=owners.get(port);
      socket.getOutputStream().write(wire);
      try { until(()->own.frames.get()==128); }
      catch(Throwable failure) {
       System.err.println("SESSION_STALL port="+port+" id="+own.channel.id()+" frames="+own.frames.get()+" open="+own.channel.isOpen()
         +" state="+Arrays.toString(own.channel.userSocketState())+" registry="+Arrays.toString(server.registryDiagnostics())
         +" workers="+Arrays.toString(server.threadDiagnostics())+" pools="+Arrays.toString(server.poolDiagnostics())
         +" send="+Arrays.toString(server.sendDiagnostics()));
       throw failure;
      }
      check(own.channel.setUserState(1)&&own.channel.setCharacterEntered(true)&&own.channel.setUserState(2),"restart/reenter transition");
      own.close();check(own.closed.await(10,TimeUnit.SECONDS),"stress terminal");owners.remove(port,own);completed.incrementAndGet();
     }catch(Exception failure){throw new CompletionException(failure);}
    }));
    for(Future<?> future:futures)future.get(30,TimeUnit.SECONDS);
    until(()->server.registryDiagnostics()[0]==0&&server.registryDiagnostics()[1]==0);
    long[] counts=server.accountRegistryDiagnostics();check(counts[0]==0&&counts[1]==0&&counts[2]==0,"stress retained identity maps");
   }
   until(()->server.performanceDiagnostics()[0]>=2);
   CPerfMon.Snapshot perf=CPerfMon.Read(server);
   check(perf.owner()==15&&(perf.availabilityFlags()&7)==7&&perf.workingSetBytes()>0&&perf.totalPhysicalBytes()>0&&perf.processCpuPermille()<=1000,"actual Windows CPerfMon CPU/memory sampling");
  }
  check(completed.get()>=256&&owners.isEmpty(),"session stress count");
  System.out.println("PASS Windows concurrent sessions:connections="+completed.get()+",concurrent=32,L1Frames="+(completed.get()*128L)+",bytes="+(completed.get()*65536L)+",auth/enter/restart/close,zero active/retiring/Playing/EnterChar entries;elapsedMs="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
  System.out.println("PASS Windows CPerfMon:actual owner15,system/process memory and process CPU availability; sustained production load and live DB are outside this bounded loopback test");
 }
}
