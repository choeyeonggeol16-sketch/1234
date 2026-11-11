from pathlib import Path
import subprocess,tempfile
R=Path(__file__).resolve().parents[1];P='l1j/server/server/model/network/';C=P+'core/'
stubs={}
def stub(path,body):stubs[path+'.java']='package '+path.rsplit('/',1)[0].replace('/','.')+';\n'+body
stub('l1j/server/Config','public class Config {public static int MAX_ONLINE_USERS=100; public static boolean shutdownCheck=true;}')
stub('l1j/server/server/Account','public class Account {public String getName(){return "test";}}')
stub('l1j/server/server/Opcodes','public class Opcodes {public static byte[] S_KEYBYTES={1};public static long getSeed(){return 1;}}')
stub('l1j/server/server/ThreadPool','''public class ThreadPool {public static boolean reject;static final ThreadPool I=new ThreadPool();public static ThreadPool getInstance(){return I;}public void execute(Runnable r){if(reject)throw new java.util.concurrent.RejectedExecutionException("fixture");r.run();}}''')
stub('l1j/server/server/Controller/LoginController','''public class LoginController {static final LoginController I=new LoginController();public static LoginController getInstance(){return I;}public int getOnlinePlayerCount(){return 0;}public void setMaxAllowedOnlinePlayers(int n){}public void logout(l1j.server.server.model.network.core.CIOClient c){}}''')
stub('l1j/server/server/model/Instance/L1PcInstance','''public class L1PcInstance {public final java.util.concurrent.atomic.AtomicInteger logouts=new java.util.concurrent.atomic.AtomicInteger();public boolean isPrivateShop(){return false;}public boolean isAutoClanjoin(){return false;}public void logout(){logouts.incrementAndGet();}public String getName(){return "fixture";}}''')
stub(P+'buffer/ReadBuffer','public class ReadBuffer {}');stub(P+'buffer/WriteBuffer','public class WriteBuffer {}')
stub(P+'packet/WritablePacket','public class WritablePacket<T> {}')
stub(P+'packet/ServerPacket','public class ServerPacket extends WritablePacket<l1j.server.server.model.network.core.CIOClient> {}')
stub(P+'packet/BytesWritablePacket','public class BytesWritablePacket extends WritablePacket<l1j.server.server.model.network.core.CIOClient> {public BytesWritablePacket(byte[] b){}}')
stub(P+'client/manager/NetworkClientCounter','''public class NetworkClientCounter {static final NetworkClientCounter I=new NetworkClientCounter();public static NetworkClientCounter getInstance(){return I;}public int isPermission(java.net.InetAddress a){return 1;}public void decrease(java.net.InetAddress a){}}''')
stub(P+'thread/CIOThreadCallback','''public class CIOThreadCallback {public interface WheelTask {void run();boolean isCancelled();}public static int currentShardIndex(){return -1;}public int bindNetworkIngress(l1j.server.server.model.network.utill.ConnectionConfig c){return 1;}public int networkIngressPressure(int i){return 0;}public boolean scheduleLoginTimeout(int n,WheelTask t){return true;}public boolean scheduleNetworkTimeout(int n,WheelTask t,long d){return true;}}''')
stub(P+'utill/ConnectionState','public enum ConnectionState {CONNECTED,AUTHENTICATED,IN_GAME,OUT_GAME,DISCONNECTED}')
stub(P+'utill/ConnectionConfig','public class ConnectionConfig {public boolean usePreLoginTimeout;public static int PACKET_LIMIT_PER_SEC=200;}')
stub('l1j/server/server/model/character/pss/PssConfig','public class PssConfig {public static class Settings {}}')
stub('l1j/server/server/model/character/pss/ClientAutomationSession','public class ClientAutomationSession {public boolean clientDriven(){return true;}}')
stub('l1j/server/server/model/character/pss/PlaySupport2019','public class PlaySupport2019 {public static void onClientDetached(l1j.server.server.model.network.core.CIOClient c){}}')
stub('l1j/server/server/model/character/pss/window/PlaySupportWindow','public class PlaySupportWindow {public static class Session {public Session(l1j.server.server.model.network.core.CIOClient c){}public void close(){}}}')
stub('l1j/server/server/packets/Protocol/Packet/SC_CraftListAllAck','public class SC_CraftListAllAck {public static void onDisconnect(l1j.server.server.model.network.core.CIOClient c){}}')
stub('l1j/server/server/packets/Protocol/Stream/ProtoOutputStream','public class ProtoOutputStream {public boolean isCreated(){return true;}public void createProtoBytes(){}public byte[] getProtoBytes(){return new byte[]{1};}public void dispose(){}}')
stub(C+'sc/packet/SC_Disconnect','public class SC_Disconnect {public static void send(l1j.server.server.model.network.core.CIOClient c){}}')
stub(C+'DominationHybrid','public class DominationHybrid {public static void onClientClosed(CIOClient c){}}')
stub(C+'CIOWinServer','class CIOWinServer {static boolean isCompletionThread(){return false;}}')
stub(C+'CIOWinSocket','class CIOWinSocket {java.net.InetSocketAddress remoteAddress(){return new java.net.InetSocketAddress("127.0.0.1",12345);}}')
stub(C+'CIOBufferPool','class CIOBufferPool {}')
stub(C+'CIOServer','class CIOServer {interface SocketFactory {CIOUserSocket CreateSocket(CIOWinSocket s,CIOBufferPool p,CIOUserSocket.Closed cb)throws Exception;}}')
stub(C+'CIOUserSocket','''class CIOUserSocket {
 interface Closed {void onClosed(CIOUserSocket s);}interface NetworkDeadline {boolean scheduleEarlier(long d);}
 private final Closed cb;private CIOClient client;volatile boolean open=true,complete,disconnect;boolean held=true;
 static final long NETWORK_WRITE_STALL_NANOS=30000000000L;
 CIOUserSocket(CIOWinSocket s,CIOBufferPool p,l1j.server.server.model.network.thread.CIOThreadCallback sch,String ip,int sh,int q,Closed cb){this.cb=cb;}
 synchronized void completePacket(){held=false;completeClose();}
 synchronized void completeClose(){if(!open&&!held&&!complete){complete=true;cb.onClosed(this);}}
 void close(){open=false;completeClose();}boolean isCloseComplete(){return complete;}boolean isOpen(){return open;}
 void bindClient(CIOClient c){client=c;}CIOClient client(){return client;}String getRemoteAddress(){return "127.0.0.1";}
 boolean isClientConnected(){return open&&!disconnect;}synchronized boolean beginClientDisconnect(){if(disconnect)return false;disconnect=true;return true;}
 void finishClientDisconnect(){close();}void initializeCipher(long s){}void clearCipher(){}
 boolean tryWritePacket(CIOClient c,l1j.server.server.model.network.packet.WritablePacket<?> p){return true;}
 boolean encrypt(l1j.server.server.model.network.buffer.WriteBuffer b,int o,int l){return true;}
 boolean decrypt(l1j.server.server.model.network.buffer.ReadBuffer b,int o,int l){return true;}
 void enqueuePacket(l1j.server.server.model.network.packet.WritablePacket<?> p){}int drainOnOwner(){return 0;}void onSendDrainedOwner(){}
 boolean hasDrainWorkOnOwner(){return false;}boolean whenWriteDrained(Runnable r){return true;}void cancelWriteDrainCallback(){}void read(){}
 void requestClientClose(l1j.server.server.model.network.packet.WritablePacket<?> p){close();}int affinityShard(){return 0;}int getEstimateQueueSize(){return 0;}
 void bindNetworkDeadline(NetworkDeadline d){}long networkWriteProgress(){return 0;}boolean hasPendingNetworkSend(){return false;}
 boolean networkDeadlineExpired(long n){return false;}long nextNetworkDeadlineDelayMs(long n,long d){return d;}
}''')
stub(C+'LifecycleTest','''public class LifecycleTest {
 public static void main(String[] args)throws Exception{
  java.util.logging.Logger.getLogger(CIOClient.class.getName()).setLevel(java.util.logging.Level.OFF);
  for(int mode=0;mode<4;mode++){
   CIOSocketFactory f=new CIOSocketFactory(new l1j.server.server.model.network.thread.CIOThreadCallback(),new l1j.server.server.model.network.utill.ConnectionConfig());
   java.util.concurrent.atomic.AtomicInteger closed=new java.util.concurrent.atomic.AtomicInteger();
   CIOUserSocket socket=f.CreateSocket(new CIOWinSocket(),new CIOBufferPool(),s->closed.incrementAndGet());
   CIOClient client=socket.client();l1j.server.server.model.Instance.L1PcInstance pc=new l1j.server.server.model.Instance.L1PcInstance();client.setActiveChar(pc);
   l1j.server.server.ThreadPool.reject=mode==3;
   if(mode==0)client.kick();else if(mode==1)client.close();else if(mode==2)client.disconnect();else socket.close();
   if(pc.logouts.get()!=0||client.isLogoutComplete()||closed.get()!=0)throw new AssertionError("logout before packet drain mode="+mode);
   client.close();client.disconnect(); // repeats must not bypass the delayed terminal callback
   if(pc.logouts.get()!=0)throw new AssertionError("repeat bypass");
   socket.completePacket();
   client.kick();client.close();client.disconnect();socket.completePacket();
   if(pc.logouts.get()!=1||!client.isLogoutComplete()||closed.get()!=1)throw new AssertionError("cleanup not exactly once mode="+mode);
  }
  System.out.println("PASS actual CIOClient + CIOSocketFactory: kick/direct close/disconnect/remote close wait for packet drain; repeated requests clean up exactly once; rejected executor fallback waits too.");
  System.out.println("Native transport, game objects and thread pool are fixtures; Windows integration not exercised.");
 }
}''')
with tempfile.TemporaryDirectory(prefix='aden-life-') as tmp:
 root=Path(tmp);paths=[]
 for path,content in stubs.items():
  p=root/(path+'.java' if not path.endswith('.java') else path);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content);paths.append(p)
 for name in ('CIOClient.java','CIOSocketFactory.java'):
  p=root/C/(name);p.write_bytes((R/'src'/C/name).read_bytes());paths.append(p)
 build=root/'build';build.mkdir()
 with (R/'logs/lifecycle-test.log').open('w') as out:
  subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-source','21','-target','21','-d',str(build),*[str(p) for p in paths]],check=True,stdout=out,stderr=subprocess.STDOUT)
  subprocess.run(['java','-cp',str(build),C.replace('/','.')+'LifecycleTest'],check=True,stdout=out,stderr=subprocess.STDOUT)
print('Lifecycle regression test passed.')
