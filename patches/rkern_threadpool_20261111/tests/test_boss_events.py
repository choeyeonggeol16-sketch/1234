from pathlib import Path
import argparse, subprocess, tempfile, zipfile
R=Path(__file__).resolve().parents[1]
a=argparse.ArgumentParser();a.add_argument('--src',type=Path);args=a.parse_args()
fixtures={
'l1j/server/Config.java':'public class Config {public static boolean STANDBY_SERVER=false;}',
'l1j/server/server/ActionCodes.java':'public class ActionCodes {public static final int ACTION_Appear=1,ACTION_Hide=2,ACTION_On=3,ACTION_SwordWalk=4,ACTION_Walk=5;}',
'l1j/server/server/IdFactory.java':'public class IdFactory {public static IdFactory getInstance(){return new IdFactory();}public int nextId(){return 1;}}',
'l1j/server/server/model/L1Object.java':'public class L1Object {}',
'l1j/server/server/model/Instance/L1PcInstance.java':'''public class L1PcInstance {public int getMapId(){return 1;}public boolean isPrivateShop(){return false;}public boolean is_world(){return true;}public int getX(){return 0;}public int getY(){return 0;}public int getHeading(){return 0;}public Object getNetConnection(){return null;}public void start_teleport(int x,int y,int map,int heading,int effect,boolean a,boolean b){}}''',
'l1j/server/server/model/Instance/L1NpcInstance.java':'''public class L1NpcInstance extends l1j.server.server.model.L1Object {
 public static final int CHAT_TIMING_APPEARANCE=0;
 public void setId(int n){}public int getId(){return 1;}public void setMap(short map){}public void setX(int x){}public void setY(int y){}public int getX(){return 0;}public int getY(){return 0;}public void setHomeX(int x){}public void setHomeY(int y){}public void setHeading(int h){}public void setPassObject(boolean p){}public void onPerceive(L1PcInstance p){}public void startChat(int n){}public void deleteMe(){}public int getNpcId(){return 1;}public boolean isDead(){return false;}
 public Template getNpcTemplate(){return new Template();}public static class Template {public int get_npcId(){return 1;}}
 public Map getMap(){return new Map();}public static class Map {public boolean isInMap(Location p){return true;}public boolean isPassable(Location p){return true;}}
 public Location getLocation(){return new Location();}public static class Location {public void forward(int h){}}
 public Light getLight(){return new Light();}public static class Light {public void turnOnOffLight(){}}
}''',
'l1j/server/server/model/Instance/L1DollInstance.java':'public class L1DollInstance extends L1NpcInstance {}',
'l1j/server/server/model/Instance/L1MonsterInstance.java':'public class L1MonsterInstance extends L1NpcInstance {}',
'l1j/server/server/datatables/NpcTable.java':'''public class NpcTable {public static NpcTable getInstance(){return new NpcTable();}public l1j.server.server.model.Instance.L1NpcInstance newNpcInstance(int n){return new l1j.server.server.model.Instance.L1NpcInstance();}}''',
'l1j/server/server/model/L1World.java':'''public class L1World {
 public static final java.util.concurrent.atomic.AtomicInteger spawned=new java.util.concurrent.atomic.AtomicInteger();
 public static L1World getInstance(){return new L1World();}public java.util.List<l1j.server.server.model.Instance.L1PcInstance> getAllPlayers(){return java.util.List.of();}public java.util.List<l1j.server.server.model.Instance.L1PcInstance> getVisiblePlayer(Object n){return java.util.List.of();}public java.util.Map<Integer,L1Object> getVisibleObjects(int n){return java.util.Map.of();}public L1Object findNpc(int n){return null;}public void storeAndEnterWorld(Object n){spawned.incrementAndGet();}
}''',
'l1j/server/server/model/network/core/sc/packet/SC_DoActionGFX.java':'public class SC_DoActionGFX {public static void send(Object p,int id,int action){}}',
'l1j/server/server/model/network/core/sc/packet/SC_DisplayEffect.java':'public class SC_DisplayEffect {public static void send(Object p,int effect){}}',
'l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java':'public class SC_PacketBox {public static final int GREEN_MESSAGE=1;public static void send(Object p,int type,String text){}public static void broadcastGreen(String text){}}',
'l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java':'public class SC_SystemMessage {public static void broadcast(String text){}}',
'BossEventRegression.java':'''import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import l1j.server.server.ThreadPool;
public class BossEventRegression {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static Object field(Object o,String s)throws Exception {Field f=o.getClass().getDeclaredField(s);f.setAccessible(true);synchronized(o){return f.get(o);}}
 static void call(Object o,String method)throws Exception{o.getClass().getMethod(method).invoke(o);}
 static void stop(Object o)throws Exception{o.getClass().getMethod("setReady",boolean.class).invoke(o,false);}
 public static void main(String[] args)throws Exception {
  var pool=ThreadPool.getInstance();var events=new ArrayList<Object>();
  try {
   for(int i=1;i<=11;i++) {
    var c=Class.forName("l1j.server.TowerOfDominance.BossController.DominanceFloorLv"+i);
    var event=c.getConstructor(int.class,int.class,int.class,int.class,boolean.class,String.class,boolean.class,int.class).newInstance(90000+i,1,1,i,false,"test",false,0);
    events.add(event);call(event,"Start");long old=(Long)field(event,"eventGeneration");stop(event);call(event,"Start");
    var run=c.getDeclaredMethod("runEvent",long.class);run.setAccessible(true);run.invoke(event,old);
    check(!(Boolean)field(event,"eventRunning"),"stale cancelled generation began execution");
    var future=field(event,"eventTask");call(event,"Start");check(future==field(event,"eventTask"),"duplicate event admitted");
   }
   check(l1j.server.server.model.L1World.spawned.get()==0,"event started before five second delay");
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(9);
   for(;;) {
    boolean all=true;for(var e:events)if(!(Boolean)field(e,"eventRunning"))all=false;
    if(all)break;check(System.nanoTime()<deadline,"event launch timeout");Thread.sleep(5);
   }
   var tick=new CountDownLatch(1);pool.scheduleRequired(tick::countDown,0);check(tick.await(1,TimeUnit.SECONDS),"eleven sleeping bosses blocked scheduler");
   for(var e:events)check((Integer)field(e,"stage")==1,"seven second scripted stage delay changed");
   for(var e:events)stop(e);
   check(pool.shutdown(3,TimeUnit.SECONDS),"boss interruption/cleanup did not complete");
   for(var e:events)check(!(Boolean)field(e,"eventRunning")&&!(Boolean)field(e,"Running"),"boss remains running");
   System.out.println("PASS all eleven actual boss controllers compile and launch through actual ThreadPool");
   System.out.println("PASS five-second admission delay, duplicate suppression and stale generation rejection");
   System.out.println("PASS eleven simultaneous sleeping events leave the single scheduler responsive");
   System.out.println("PASS scripted stage wait preserved; stop interrupts all events and joins their cleanup");
  }finally{for(var e:events)stop(e);check(pool.shutdown(3,TimeUnit.SECONDS),"fixture shutdown");}
  System.out.println("World/NPC/packet/config dependencies are fixtures; no real game/DB integration.");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='aden-boss-regression-') as tmp:
 p=Path(tmp);src=args.src
 if src is None:
  with zipfile.ZipFile(R.parents[1]/'downloads/RKern_Aden_IOCP_THREADPOOL_FIXED_FULL_SRC_20261111.zip') as z:z.extractall(p/'source')
  src=p/'source/src'
 files=[]
 for rel,body in fixtures.items():
  q=p/rel;q.parent.mkdir(parents=True,exist_ok=True)
  package=str(Path(rel).parent).replace('/','.')
  q.write_text(('package '+package+';\n' if package!='.' else '')+body);files.append(q)
 files+=list((src/'l1j/server/TowerOfDominance/BossController').glob('DominanceFloorLv*.java'))
 files += [src/f for f in ['l1j/server/server/ThreadPool.java','l1j/server/server/utils/ThreadConfig.java','l1j/server/server/utils/ConfigReader.java','l1j/server/server/utils/TimeUtil.java','l1j/server/server/model/network/utill/PacketThreadFactory.java']]
 (p/'build').mkdir();(p/'config').mkdir();(p/'config/Threads.ini').write_text('ScheduledThreadPoolSize=1\nInstantThreadPoolSize=1\n')
 with (R/'logs/boss-test.log').open('w') as out:
  subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-source','21','-target','21','-encoding','UTF-8','-d',str(p/'build'),*[str(f) for f in files]],stdout=out,stderr=subprocess.STDOUT,check=True,timeout=30)
  subprocess.run(['java','-cp',str(p/'build'),'BossEventRegression'],cwd=p,stdout=out,stderr=subprocess.STDOUT,check=True,timeout=20)
print((R/'logs/boss-test.log').read_text())
