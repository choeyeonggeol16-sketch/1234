from pathlib import Path
import argparse, subprocess, tempfile, zipfile
R=Path(__file__).resolve().parents[1]
a=argparse.ArgumentParser();a.add_argument('--src',type=Path);args=a.parse_args()
with tempfile.TemporaryDirectory(prefix='aden-server-shutdown-') as tmp:
 p=Path(tmp);src=args.src
 if src is None:
  with zipfile.ZipFile(R.parents[1]/'downloads/RKern_Aden_IOCP_THREADPOOL_FIXED_FULL_SRC_20261111.zip') as z:z.extractall(p/'source')
  src=p/'source/src'
 s=(src/'l1j/server/server/GameServer.java').read_text()
 block=s[s.index('    private final Object resourceShutdownLock'):s.index('\tpublic synchronized void abortShutdown()')]
 shutdown=(src/'l1j/server/server/Shutdown.java').read_text();save=shutdown[shutdown.index('\tprivate void saveData()'):shutdown.index('\tpublic void startTelnetShutdown')]
 assert 'GameServer.getInstance().shutdownFromHook();' in save and 'System.exit' not in save
 files=[]
 def write(rel,body):
  q=p/rel;q.parent.mkdir(parents=True,exist_ok=True);q.write_text(body);files.append(q)
 write('l1j/server/server/GameServer.java','''package l1j.server.server;
import l1j.server.Server;
public class GameServer {
 static final GameServer INSTANCE=new GameServer();static int disconnected;
 public static GameServer getInstance(){return INSTANCE;}
 public void disconnectAllCharacters(){disconnected++;}
'''+block+'\n}\n')
 write('l1j/server/Server.java','''package l1j.server;
public class Server {public static int accepts,transport;public static void stopNewConnections(){accepts++;}public static void stopLoginServer(){transport++;}public static void printShutdownStatus(String text){}}
''')
 names='AppCenterService OneTimeController NpcChatTimeController L1CompanionInstance MJRaidLoadManager MJInstanceLoadManager MJDShopStorage MJCTLoadManager MJItemSkillModelLoader MJKDALoadManager NetworkSafeLoadManager CharTamScheduler PcInventoryExpiryScheduler AutoSaveChaQuitCheckScheduler PremiumTimeController BugRaceController RankUserLoader NetworkClientCounter LoggerInstance TileOccupancyNpc TileOccupancy MJEffectModelLoader NpcServer CIOThreadCallback L1GameTimeClock RealTimeClock'.split()
 generic='''package l1j.server.server;
class Service {static Service getInstance(){return new Service();}static void stop(){}static void release(){}static void clearProcess(){}static void do_store_companions(){}void destroy(){}void flushImmediately(){}void shutdown(){}boolean awaitTermination(long n,java.util.concurrent.TimeUnit u){return true;}}
class CacheD {static int closed;static CacheD getInstance(){return new CacheD();}boolean shutdown(long n){if(!ShutdownIntegration.queuedRan.get())throw new AssertionError("storage closed before accepted job ran");closed++;return true;}int pendingAccounts(){return 0;}int pendingCharacters(){return 0;}int pendingItems(){return 0;}Throwable lastFailure(){return null;}}
class DatabaseFactory {static int closed;static DatabaseFactory getInstance(){return new DatabaseFactory();}void shutdown(){if(!ShutdownIntegration.queuedRan.get()||CacheD.closed!=1)throw new AssertionError("database closed before pool/storage drain");closed++;}}
'''
 generic+='\n'.join('class '+n+' extends Service {}' for n in names)
 write('l1j/server/server/Fixtures.java',generic)
 write('l1j/server/server/ShutdownIntegration.java','''package l1j.server.server;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class ShutdownIntegration {
 static final AtomicBoolean queuedRan=new AtomicBoolean();
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception {
  ThreadPool p=ThreadPool.getInstance();GameServer gs=GameServer.getInstance();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  p.execute((Runnable)()->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}});
  check(entered.await(2,TimeUnit.SECONDS),"instant worker did not start");p.execute((Runnable)()->queuedRan.set(true));
  Runtime.getRuntime().addShutdownHook(new Thread(()->{
   try {
    gs.shutdownFromHook();gs.shutdownFromHook();
    check(DatabaseFactory.closed==1&&CacheD.closed==1&&GameServer.disconnected==1,"cleanup executed more than once or skipped");
    check(l1j.server.Server.accepts==1&&l1j.server.Server.transport==1,"transport lifecycle skipped or duplicated");
    System.out.println("PASS actual GameServer shutdown method bodies: "+args[0]+" exit drains accepted work, closes storage afterward, repeated hook is idempotent");
   }catch(Throwable failure){failure.printStackTrace();Runtime.getRuntime().halt(4);}
  },"actual-hook-pipeline"));
  Thread verifier=new Thread(()->{
   try {
    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
    while(!p.isShutdown()) {check(System.nanoTime()<deadline,"pool drain not reached");Thread.sleep(1);}
    check(DatabaseFactory.closed==0&&CacheD.closed==0,"storage closed while accepted work blocked");
   }catch(Throwable failure){failure.printStackTrace();Runtime.getRuntime().halt(5);}
   finally{release.countDown();}
  },"storage-order-verifier");verifier.setDaemon(true);verifier.start();
  if(args[0].equals("direct"))gs.shutdown();
  else p.startDedicatedThread(new Thread(()->{p.detachCurrentDedicatedThreadForJvmExit();System.exit(0);},"gm-countdown"));
 }
}''')
 files += [src/f for f in ['l1j/server/server/ThreadPool.java','l1j/server/server/utils/ThreadConfig.java','l1j/server/server/utils/ConfigReader.java','l1j/server/server/utils/TimeUtil.java','l1j/server/server/model/network/utill/PacketThreadFactory.java']]
 (p/'build').mkdir();(p/'config').mkdir();(p/'config/Threads.ini').write_text('ScheduledThreadPoolSize=1\nInstantThreadPoolSize=1\n')
 with (R/'logs/server-shutdown-test.log').open('w') as out:
  subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-source','21','-target','21','-encoding','UTF-8','-d',str(p/'build'),*[str(f) for f in files]],stdout=out,stderr=subprocess.STDOUT,check=True,timeout=30)
  for mode in ['direct','gm-hook']:
   subprocess.run(['java','-cp',str(p/'build'),'l1j.server.server.ShutdownIntegration',mode],cwd=p,stdout=out,stderr=subprocess.STDOUT,check=True,timeout=12)
  out.write('Actual ThreadPool and exact GameServer shutdown method bodies tested; service/native/storage dependencies are fixtures. Full GameServer dependency linking and Windows runtime not tested.\n')
print((R/'logs/server-shutdown-test.log').read_text())
