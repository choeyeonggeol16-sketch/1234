from pathlib import Path
import tempfile,subprocess
R=Path(__file__).resolve().parents[1];P='l1j/server/server/model/network/core/'
f={
'CIOBufferPool.java':'class CIOBufferPool {}',
'CIOWinSocket.java':'class CIOWinSocket {boolean isOpen(){return true;}void close(){}}',
'CIOUserSocket.java':'''class CIOUserSocket {
 interface Closed {void onClosed(CIOUserSocket s);}
 final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1), release=new java.util.concurrent.CountDownLatch(1);
 final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
 boolean isCloseComplete(){return true;}boolean isOpen(){return true;}void startRead(){}
 void close(){calls.incrementAndGet();entered.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}}
}''',
'CIOWinServer.java':'''class CIOWinServer {
 volatile boolean terminated;volatile long budget;final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
 final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(0);
 CIOWinServer(java.net.InetSocketAddress a,int m,int t,int b,java.util.function.Consumer<CIOWinSocket> c){}
 static boolean isCompletionThread(){return false;}
 java.net.InetSocketAddress localAddress()throws java.io.IOException{return null;}
 void stopAccepting()throws java.io.IOException{}boolean isAcceptStopped(){return true;}java.io.IOException shutdownFailure(){return null;}
 void closeWithBudget(long n)throws java.io.IOException {calls.incrementAndGet();budget=n;entered.countDown();try{release.await();}catch(InterruptedException e){throw new java.io.IOException(e);}terminated=true;}
 boolean isTerminated(){return terminated;}
}''',
'ShutdownTest.java':'''import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.lang.reflect.*;
public class ShutdownTest {
 static void set(Object o,String name,Object v)throws Exception{Field f=CIOServer.class.getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
 static CIOServer server(CIOWinServer n,CIOUserSocket c)throws Exception{
  Class<?> u=Class.forName("sun.misc.Unsafe");Field f=u.getDeclaredField("theUnsafe");f.setAccessible(true);
  CIOServer s=(CIOServer)u.getMethod("allocateInstance",Class.class).invoke(f.get(null),CIOServer.class);
  set(s,"nativeServer",n);set(s,"closed",new AtomicBoolean());set(s,"acceptingStopped",new AtomicBoolean());
  Set<CIOUserSocket> conns=java.util.concurrent.ConcurrentHashMap.newKeySet();if(c!=null)conns.add(c);set(s,"connections",conns);return s;
 }
 static CIOWinServer nativeServer(){return new CIOWinServer(null,1,1,1,null);}
 static class Call {
  final CountDownLatch returned=new CountDownLatch(1);volatile Throwable error;volatile long ms;final Thread thread;
  Call(CIOServer s,long budget){thread=new Thread(()->{long at=System.nanoTime();try{s.close(budget,TimeUnit.MILLISECONDS);}catch(Throwable t){error=t;}finally{ms=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-at);returned.countDown();}});thread.setDaemon(true);thread.start();}
 }
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 static void completed(CIOServer s)throws Exception{
  Field f=CIOServer.class.getDeclaredField("shutdownAttempt");f.setAccessible(true);Object a=f.get(s);Field d=a.getClass().getDeclaredField("done");d.setAccessible(true);
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(!d.getBoolean(a)){check(System.nanoTime()<end,"coordinator did not retire");Thread.sleep(1);}
 }
 public static void main(String[] args)throws Exception{
  CIOWinServer n=nativeServer();CIOUserSocket c=new CIOUserSocket();CIOServer s=server(n,c);Call call=new Call(s,50);
  try{check(c.entered.await(1,TimeUnit.SECONDS),"connection close not started");check(call.returned.await(500,TimeUnit.MILLISECONDS),"caller exceeded budget while connection close blocked");check(call.error instanceof IllegalStateException,"timeout not reported");check(n.calls.get()==0&&!n.terminated,"native memory/stop touched before connection drain");}
  finally{c.release.countDown();}completed(s);check(n.calls.get()==0,"expired coordinator entered native phase");
  s.close(1,TimeUnit.SECONDS);check(n.terminated&&s.awaitTermination(0,TimeUnit.SECONDS),"explicit retry failed");
  System.out.println("PASS blocked Java connection close: caller returned timeout in "+call.ms+"ms; no native phase after expiry; retry succeeds.");
  n=nativeServer();c=new CIOUserSocket();s=server(n,c);Call first=new Call(s,1500);
  try{check(c.entered.await(1,TimeUnit.SECONDS),"first caller not active");Call second=new Call(s,40);check(second.returned.await(500,TimeUnit.MILLISECONDS),"second caller blocked on close monitor");check(second.error instanceof IllegalStateException&&c.calls.get()==1,"coordinator duplicated or no independent timeout");}
  finally{c.release.countDown();}check(first.returned.await(1,TimeUnit.SECONDS)&&first.error==null,"first caller failed");check(n.calls.get()==1,"native shutdown duplicated");
  System.out.println("PASS concurrent callers: independent short deadline; one coordinator and one native shutdown.");
  n=nativeServer();n.release=new CountDownLatch(1);s=server(n,null);call=new Call(s,50);
  try{check(n.entered.await(1,TimeUnit.SECONDS),"native phase not reached");check(call.returned.await(500,TimeUnit.MILLISECONDS)&&call.error instanceof IllegalStateException,"blocked native call traps caller");check(!n.terminated,"native memory released during blocked call");}
  finally{n.release.countDown();}completed(s);check(s.awaitTermination(1,TimeUnit.SECONDS),"native completion not observed");
  System.out.println("PASS blocked native phase: caller deadline honored; coordinator/resources retained until native completion.");
  n=nativeServer();c=new CIOUserSocket();s=server(n,c);final CIOUserSocket delayed=c;Thread release=new Thread(()->{try{delayed.entered.await();Thread.sleep(60);delayed.release.countDown();}catch(InterruptedException e){throw new RuntimeException(e);}});release.start();s.close(500,TimeUnit.MILLISECONDS);release.join();
  check(n.budget>0&&n.budget<TimeUnit.MILLISECONDS.toNanos(470),"connection close time not deducted");
  System.out.println("PASS remaining budget: native phase received "+TimeUnit.NANOSECONDS.toMillis(n.budget)+"ms from a 500ms total after delayed connection close.");
  System.out.println("Actual CIOServer tested; native server/socket dependencies are fixtures; no Windows IOCP runtime.");
 }
}'''}
with tempfile.TemporaryDirectory(prefix='aden-shutdown-') as tmp:
 root=Path(tmp);p=root/P;p.mkdir(parents=True);files=[]
 for name,body in f.items():q=p/name;q.write_text('package '+P.rstrip('/').replace('/','.')+';\n'+body);files.append(q)
 q=p/'CIOServer.java';q.write_bytes((R/'src'/P/q.name).read_bytes());files.append(q);build=root/'build';build.mkdir()
 with (R/'logs/shutdown-test.log').open('w') as out:
  subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-source','21','-target','21','-d',str(build),*[str(x) for x in files]],check=True,stdout=out,stderr=subprocess.STDOUT)
  subprocess.run(['java','-cp',str(build),P.replace('/','.')+'ShutdownTest'],check=True,stdout=out,stderr=subprocess.STDOUT)
print('Shutdown regression tests passed.')
