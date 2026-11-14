package l1j.server.server.model.network.core.connection;

import l1j.server.server.model.network.core.transport.CIOPacketCodecReference;

import l1j.server.server.model.network.core.diagnostics.CNetworkDiagnostics;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_Emblem;
import static l1j.server.server.model.network.core.connection.CUserSocketOwnerWindowsTest.*;

/** Real Windows DLL/FFM, production owner/frame/pool and emblem encoder. */
public final class CUserSocketSafeSendWindowsTest {
 static final int MARGIN=512*1024, THRESHOLD=1536*1024;
 static List<CIOFrame> reserve(CUserSocket owner,int bytes) {
  List<CIOFrame> held=new ArrayList<>();
  while(bytes>0){int n=Math.min(65535,bytes);check(n>2,"reservation remainder");
   CIOFrame frame=owner.beginSafeDirectFrame(n,0);check(frame!=null,"real byte reservation failed");held.add(frame);bytes-=n;}
  return held;
 }
 static void release(CUserSocket owner,List<CIOFrame> held) {for(CIOFrame f:held)owner.abortDirectFrame(f);held.clear();}
 static CNetworkDiagnostics.Trace trace(CUserSocket owner)throws Exception{return (CNetworkDiagnostics.Trace)field(owner,"networkTrace");}
 static String recent(CUserSocket owner)throws Exception{return CNetworkDiagnostics.recent(trace(owner).id);}
 static Session session()throws Exception{return new Session(true,false,new CIOBufferPool(8,128,4,64));}
 public static void main(String[] args)throws Exception {
  Path dir=Path.of("emblem"),file=dir.resolve("2147483001");
  boolean hadDir=Files.exists(dir);Files.createDirectories(dir);check(!Files.exists(file),"test emblem already exists");
  byte[] emblem={3,7,11,19,23,41,59};Files.write(file,emblem,StandardOpenOption.CREATE_NEW);
  try {
   try(Session s=session();CIOPacketCodecReference codec=new CIOPacketCodecReference(73461)) {
    s.peer.setSoTimeout(5000);CUserSocket owner=s.owner.get();List<CIOFrame> held=reserve(owner,THRESHOLD);
    check(owner.pendingSendBytes()==THRESHOLD,"real reserved-byte boundary");
    CIOFrame edge=owner.beginSafeDirectFrame(10,MARGIN);check(edge!=null,"equal threshold must be allowed");
    owner.abortDirectFrame(edge);check(owner.isClientConnected(),"optional abort closed socket");
    CIOFrame over=owner.beginSafeDirectFrame(3,0);check(over!=null,"over-threshold reservation");held.add(over);
    long ticket=(long)field(owner,"_socketTicket");int slots=s.pool.usedSendSlots(),frames=owner.pendingSendFrames(),bytes=owner.pendingSendBytes();
    long soft=CUserSocket.softSendRejections();int reject=(int)field(owner,"_lastSendReject");
    check(!SC_Emblem.send(s.client.get(),2147483001),"congested emblem must be skipped");
    check(owner.isClientConnected()&&owner.pendingSendBytes()==bytes&&owner.pendingSendFrames()==frames&&s.pool.usedSendSlots()==slots,"soft refusal changed leases/accounting/connection");
    check((long)field(owner,"_socketTicket")==ticket&&(int)field(owner,"_lastSendReject")==reject&&CUserSocket.softSendRejections()==soft+1,"soft refusal reserved FIFO or contaminated hard failure reason");
    release(owner,held);check(owner.pendingSendBytes()==0&&s.pool.usedSendSlots()==0,"optional cancellation rollback leak");
    // A skipped or aborted optional frame must not consume the first-plain rule/key.
    byte[] key={1,2,3,4,5,6,7,8};
    check(owner.tryWritePacket(s.client.get(),new l1j.server.server.model.network.packet.BytesWritablePacket(key)),"plain key after optional refusal");
    checkRead(s,expectedPacket(codec,key,true));until(owner::isWriteIdle);
    check(SC_Emblem.send(s.client.get(),2147483001),"uncongested emblem rejected");
    ByteBuffer payload=ByteBuffer.allocate(7+emblem.length).order(ByteOrder.LITTLE_ENDIAN);
    payload.put((byte)Opcodes.S_EMBLEM).putInt(2147483001).putShort((short)emblem.length).put(emblem);
    checkRead(s,expectedPacket(codec,payload.array(),false));until(owner::isWriteIdle);
    byte[] after={11,22,33,44,55,66,77,88};
    check(owner.tryWritePacket(s.client.get(),new l1j.server.server.model.network.packet.BytesWritablePacket(after)),"required packet after emblem");
    checkRead(s,expectedPacket(codec,after,false));until(owner::isWriteIdle);
   }
   System.out.println("PASS SafeSend real1.5MiB reservations:inclusive boundary,emblem-only skip,no ticket/lease/hard-reason mutation,optional abort,unchanged plain-first+cipher FIFO and emblem wire");
   try(Session s=session()) {
    CUserSocket owner=s.owner.get();List<CIOFrame> held=reserve(owner,THRESHOLD);
    CountDownLatch ready=new CountDownLatch(16),go=new CountDownLatch(1);List<CIOFrame> accepted=Collections.synchronizedList(new ArrayList<>());
    AtomicReference<Throwable> error=new AtomicReference<>();List<Thread> threads=new ArrayList<>();
    for(int i=0;i<16;i++){Thread t=new Thread(()->{ready.countDown();try{check(go.await(5,TimeUnit.SECONDS),"producer gate");CIOFrame f=owner.beginSafeDirectFrame(1024,MARGIN);if(f!=null)accepted.add(f);}catch(Throwable ex){error.compareAndSet(null,ex);}});threads.add(t);t.start();}
    check(ready.await(5,TimeUnit.SECONDS),"producer readiness");go.countDown();for(Thread t:threads){t.join(5000);check(!t.isAlive(),"producer stuck");}
    check(error.get()==null&&accepted.size()==1&&owner.pendingSendBytes()==THRESHOLD+1024,"concurrent precheck/reservation race");
    release(owner,held);release(owner,accepted);check(owner.isClientConnected()&&owner.pendingSendBytes()==0,"concurrent rollback");
   }
   System.out.println("PASS16 concurrent optional producers:one atomic boundary admission,no oversubscription,no connection close");
   try(Session s=new Session(true,false,new CIOBufferPool(8,1,4,1))) {
    CUserSocket owner=s.owner.get();CIOFrame first=owner.beginSafeDirectFrame(10,0);check(first!=null,"pool first");
    check(owner.beginSafeDirectFrame(10,0)==null&&owner.isClientConnected()&&owner.pendingSendBytes()==10&&s.pool.usedSendSlots()==1,"pool-full optional rollback");
    owner.abortDirectFrame(first);check(owner.pendingSendBytes()==0&&s.pool.usedSendSlots()==0,"pool cancellation leak");
    CIOFrame required=owner.beginDirectFrame(10);check(required!=null&&!required.optional(),"reused frame retained optional flag");
    owner.abortDirectFrame(required);check(!owner.isOpen()&&recent(owner).contains("javaReason=ENCODING_ERROR"),"required abort policy changed");
   }
   try(Session s=session()) {
    CUserSocket owner=s.owner.get();List<CIOFrame> held=reserve(owner,2*1024*1024);
    check(owner.beginDirectFrame(10)==null&&!owner.isOpen(),"required SEND hard cap no longer closes");
    release(owner,held);check(recent(owner).contains("javaReason=SEND_LIMIT"),"hard cap close cause missing");
   }
   System.out.println("PASS optional pool exhaustion rollback and reused frame reset;required abort/hard-cap policies preserved with explicit close causes");
   try(Session s=session()) {
    CUserSocket owner=s.owner.get();s.peer.shutdownOutput();check(s.closed.await(5,TimeUnit.SECONDS),"peer EOF close");
    check(recent(owner).contains("nativeCause=PEER_EOF operation=2 error=0")&&recent(owner).contains("javaReason=NONE"),"real FFM EOF status lost/mislabeled");
    owner.onNativeCloseStatus(2,995);check(recent(owner).contains("nativeCause=PEER_EOF"),"late cancellation replaced EOF");
   }
   try(Session s=session()) {
    CUserSocket owner=s.owner.get();s.peer.getOutputStream().write(new byte[]{1,0});
    check(s.closed.await(5,TimeUnit.SECONDS),"invalid framing close");
    check(recent(owner).contains("nativeCause=PROTOCOL_ERROR operation=2 error=13"),"native parser error lost through FFM");
   }
   try(Session s=session()) {
    CUserSocket owner=s.owner.get();s.peer.setSoLinger(true,0);s.peer.close();
    check(s.closed.await(5,TimeUnit.SECONDS),"RST close");String report=recent(owner);
    check(report.contains("nativeCause=READ_ERROR operation=2 error=")&&!report.contains("error=0\n"),"native RST error lost");
   }
   System.out.println("PASS real Windows close causes:peer EOF,invalid L1 frame,RST;history persists after close;late cancellation cannot replace first status");
  } finally {Files.deleteIfExists(file);if(!hadDir)Files.deleteIfExists(dir);}
 }
}
