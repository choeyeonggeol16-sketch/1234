from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1];SRC=ROOT/'input/src';PKG='l1j/server/server/model/network/core'
FIXTURES={
'CIOBufferPool.java':'package l1j.server.server.model.network.core; final class CIOBufferPool {}',
'CIOWinSocket.java':'package l1j.server.server.model.network.core; final class CIOWinSocket { boolean isOpen(){return true;} void close(){} }',
'CIOUserSocket.java':'''package l1j.server.server.model.network.core;
final class CIOUserSocket {
 interface Closed {void onClosed(CIOUserSocket s);}
 boolean isCloseComplete(){return true;} boolean isOpen(){return true;}
 void close(){} void startRead(){}
}''',
'CIOWinServer.java':'''package l1j.server.server.model.network.core;
import java.io.IOException;import java.net.InetSocketAddress;import java.util.function.Consumer;
final class CIOWinServer {
 volatile boolean terminated;
 CIOWinServer(InetSocketAddress a,int m,int t,int b,Consumer<CIOWinSocket> c){}
 static boolean isCompletionThread(){return false;}
 InetSocketAddress localAddress()throws IOException{return null;}
 void stopAccepting()throws IOException{} boolean isAcceptStopped(){return true;}
 void close()throws IOException{} boolean isTerminated(){return terminated;}
}''',
'AwaitContractProbe.java':'''package l1j.server.server.model.network.core;
import java.lang.reflect.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
public final class AwaitContractProbe {
 static void set(Object o,String n,Object v)throws Exception{Field f=CIOServer.class.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 public static void main(String[] a)throws Exception{
  Class<?> uc=Class.forName("sun.misc.Unsafe");Field uf=uc.getDeclaredField("theUnsafe");uf.setAccessible(true);Object u=uf.get(null);
  CIOServer server=(CIOServer)uc.getMethod("allocateInstance",Class.class).invoke(u,CIOServer.class);
  CIOWinServer fake=new CIOWinServer(null,1,1,1,null);set(server,"nativeServer",fake);set(server,"closed",new AtomicBoolean(true));
  CountDownLatch start=new CountDownLatch(1);
  Thread finisher=new Thread(()->{try{start.await();Thread.sleep(150);fake.terminated=true;}catch(InterruptedException e){throw new RuntimeException(e);}});
  finisher.start();long at=System.nanoTime();start.countDown();boolean done=server.awaitTermination(1,TimeUnit.SECONDS);long elapsed=System.nanoTime()-at;finisher.join();
  if(done)throw new AssertionError("Expected original method to return false without awaiting");
  System.out.println("REPRODUCED original CIOServer.awaitTermination: requested 1000ms, native terminates after ~150ms; returned false in "+TimeUnit.NANOSECONDS.toMillis(elapsed)+"ms.");
  System.out.println("Scope: original public method with a fake native-server lifecycle, constructor bypassed; no Windows IOCP runtime.");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='aden-await-') as tmp:
 root=Path(tmp);p=root/PKG;p.mkdir(parents=True);build=root/'build';build.mkdir();files=[]
 q=p/'CIOServer.java';q.write_bytes((SRC/PKG/q.name).read_bytes());files.append(q)
 for name,content in FIXTURES.items():q=p/name;q.write_text(content);files.append(q)
 with (ROOT/'logs/await-source-probe.log').open('w') as out:
  subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-source','21','-target','21','-d',str(build),*[str(x) for x in files]],check=True,stdout=out,stderr=subprocess.STDOUT)
  subprocess.run(['java','-cp',str(build),PKG.replace('/','.')+'.AwaitContractProbe'],check=True,stdout=out,stderr=subprocess.STDOUT)
print('Original awaitTermination non-waiting behavior reproduced.')
