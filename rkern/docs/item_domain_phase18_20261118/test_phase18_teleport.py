#!/usr/bin/env python3
"""Java21 typed source-level and deterministic behavior tests for Telbookitem."""
from pathlib import Path
import subprocess, shutil, hashlib,tempfile
D=Path(__file__).resolve().parent
BASE=D/'baseline/Telbookitem.java'
NEW=D.parents[1]/'src/l1j/server/server/model/item/function/Telbookitem.java'
ROOT=Path(tempfile.mkdtemp(prefix='rkern_phase18_tele_'))
def put(root,path,txt):
 f=root/'src'/path;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(txt,'utf8')
def skeleton(root):
 files={
 'l1j/server/Config.java':'''package l1j.server; public final class Config {public static boolean PRISONTEL=false,PRISONTELS=false;}''',
 'l1j/server/server/model/character/skill/L1SkillId.java':'''package l1j.server.server.model.character.skill;public final class L1SkillId {public static final int 이터디스=1,쉐도우제약=2,버닝샷=3,MASS_TELEPORT=4,TELEPORT=5;}''',
 'l1j/server/server/model/network/core/sc/packet/SC_Paralysis.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;public final class SC_Paralysis {public static final int TYPE_TELEPORT_UNLOCK=7; public static void send(L1PcInstance p,int t,boolean b){p.log.append("PAR:").append(t).append(';');}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;public final class SC_SystemMessage {public static void send(L1PcInstance p,String s){p.log.append("MSG:").append(s).append(';');}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;public final class SC_ServerMessage {public static void send(L1PcInstance p,int i){p.log.append("SRV:").append(i).append(';');}}''',
 'l1j/server/server/model/network/core/sc/packet/SC_DoActionGFX.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;public final class SC_DoActionGFX {public static void send(Object o,int i,int act){} public static void broadcast(L1PcInstance p,int a){p.log.append("GFX;");}}''',
 'l1j/server/server/templates/L1BookMark.java':'''package l1j.server.server.templates;public final class L1BookMark {public int getLocX(){return 22;} public int getLocY(){return 30;} public int getMapId(){return 4;}}''',
 'l1j/server/server/model/Instance/L1ItemInstance.java':'''package l1j.server.server.model.Instance;public final class L1ItemInstance {}''',
 'l1j/server/server/model/L1Location.java':'''package l1j.server.server.model;public final class L1Location {public int getX(){return 12;}public int getY(){return 13;}public int getMapId(){return 4;} public L1Location randomLocation(int range,boolean walkable){return this;}public int getTileLineDistance(L1Location other){return 1;}}''',
 'l1j/server/server/model/map/L1Map.java':'''package l1j.server.server.model.map;public final class L1Map {public boolean isEscapable(){return true;}public boolean isTeleportable(){return true;}public boolean isRuler(){return false;} public boolean isInMap(int x,int y){return x>=0&&y>=0;}public boolean isPassable(int x,int y){return true;}}''',
 'l1j/server/server/model/map/L1WorldMap.java':'''package l1j.server.server.model.map;public final class L1WorldMap {private static final L1WorldMap I=new L1WorldMap();public static L1WorldMap getInstance(){return I;}public L1Map getMap(int id){return new L1Map();}}''',
 'l1j/server/server/model/L1World.java':'''package l1j.server.server.model;import java.util.*;import l1j.server.server.model.Instance.L1PcInstance;public final class L1World {private static final L1World I=new L1World();public static L1World getInstance(){return I;}public Collection<L1PcInstance> getVisiblePlayer(L1PcInstance pc){return Collections.emptyList();}public Collection<L1PcInstance> getVisiblePlayer(L1PcInstance pc,int radius){return Collections.emptyList();}}''',
 'l1j/server/server/model/gametime/L1GameTimeClock.java':'''package l1j.server.server.model.gametime;public final class L1GameTimeClock {private static final L1GameTimeClock I=new L1GameTimeClock();public static L1GameTimeClock getInstance(){return I;}public GameTime getGameTime(){return new GameTime();}public static final class GameTime{public long getSeconds(){return 0L;}}}''',
 'l1j/server/server/model/item/function/TeleportUseService.java':'''package l1j.server.server.model.item.function;import l1j.server.server.model.Instance.L1PcInstance;final class TeleportUseService {static boolean canBypassMapRestrictions(L1PcInstance p){return false;}}''',
 'l1j/server/server/model/Instance/L1PcInstance.java':'''package l1j.server.server.model.Instance;
import java.util.*;import l1j.server.server.model.L1Location;import l1j.server.server.templates.L1BookMark;import l1j.server.server.model.map.L1Map;
public final class L1PcInstance {
 public final StringBuilder log=new StringBuilder(); public final int mapId;public final boolean towerItem;
 public final Inventory inventory=new Inventory(this);
 public L1PcInstance(int mapId,boolean towerItem){this.mapId=mapId;this.towerItem=towerItem;}
 public boolean hasSkillEffect(int id){return false;}
 public L1BookMark[] getBookMarkArray(){return new L1BookMark[0];}
 public L1Map getMap(){return new L1Map();}
 public short getMapId(){return (short)mapId;}
 public Inventory getInventory(){return inventory;}
 public int getClanid(){return 0;}
 public int getId(){return 17;}
 public L1Location getLocation(){return new L1Location();}
 public boolean isPrivateShop(){return false;}
 public int getHeading(){return 2;}
 public boolean getAutoHunt(){return false;}
 public Object getNetConnection(){return null;}
 public void start_teleport(int x,int y,int m,int h,int a,boolean b,boolean c){log.append("TP:").append(x).append(':').append(y).append(':').append(m).append(':').append(c).append(';');}
 public void cancelAbsoluteBarrier(){log.append("BARRIER;");}
 public static final class Inventory {
  private final L1PcInstance pc;
  Inventory(L1PcInstance pc){this.pc=pc;}
  public L1ItemInstance findItemId(int id){return (pc.towerItem&&id>=830022&&id<=830031)||id==560028&&pc.towerItem||id==4100135&&pc.towerItem?new L1ItemInstance():null;}
  public boolean checkItem(int id){return false;}
  public void removeItem(L1ItemInstance item,int count){pc.log.append("REMOVE:").append(count).append(';');}
 }
}
''',
 'l1j/server/server/model/item/function/TelbookTest.java':'''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.Instance.L1ItemInstance;
public final class TelbookTest {
 public static void main(String[] args){
  int n=0;
  for(int map=95;map<115;map++) for(int item=0;item<2;item++){
   L1PcInstance pc=new L1PcInstance(map,item!=0);
   Telbookitem.clickItem(pc,40099,0,0,(short)0,new L1ItemInstance());
   System.out.println(map+":"+item+":"+pc.log); n++;
  }
  for(int map=12848;map<12865;map++)for(int item=0;item<2;item++){
   L1PcInstance pc=new L1PcInstance(map,item!=0);
   Telbookitem.clickItem(pc,40099,0,0,(short)0,new L1ItemInstance());
   System.out.println(map+":"+item+":"+pc.log); n++;
  }
  for(int id:new int[]{0,5}){
   L1PcInstance pc=new L1PcInstance(4,true);
   Telbookitem.toActive(pc,40099,new L1ItemInstance(),id);
   System.out.println("SKILL:"+id+":"+pc.log);n++;
  }
  System.err.println("CASES="+n);
 }
}'''
}
 for rel,txt in files.items(): put(root,rel,txt)
def run(root,file):
 skeleton(root)
 put(root,'l1j/server/server/model/item/function/Telbookitem.java',file.read_text('utf8'))
 classes=root/'classes';classes.mkdir()
 files=list((root/'src').rglob('*.java'))
 p=subprocess.run(['javac','-encoding','UTF-8','--release','21','-d',str(classes),*map(str,files)],capture_output=True,text=True)
 if p.returncode: print(p.stderr);raise RuntimeError('javac failed')
 p=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.TelbookTest'],capture_output=True)
 assert p.returncode==0,p.stderr
 print(root.name,p.stderr.decode().strip())
 return p.stdout
old=run(ROOT/'teleport_old',BASE)
new=run(ROOT/'teleport_new',NEW)
assert old==new,'Unexpected deterministic teleport branch difference'
print('PASS TELBOOK TYPED JAVA21 EQUALITY 76 SCENARIOS SHA256',hashlib.sha256(new).hexdigest())
assert [830022+i for i in range(10)]==list(range(830022,830032))

# Distribution is intentionally independent of the old Random implementation;
# only the original [1,3] offset domain is guaranteed.
checks = ROOT/'teleport_new'/'src/l1j/server/server/model/item/function/TelbookRandomBounds.java'
checks.write_text(r'''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
public final class TelbookRandomBounds {
 public static void main(String[] args) {
  int[] counts=new int[9];
  for(int i=0;i<9000;i++) {
   L1PcInstance pc=new L1PcInstance(4,true);
   Telbookitem.toActive(pc,40099,null,L1SkillId.MASS_TELEPORT);
   String t=pc.log.toString();int a=t.indexOf("TP:");
   if(a<0)throw new AssertionError(t);
   String[] parts=t.substring(a+3).split(":");
   int x=Integer.parseInt(parts[0]),y=Integer.parseInt(parts[1]);
   if(x<13||x>15||y<14||y>16)throw new AssertionError(t);
   counts[(x-13)*3+(y-14)]++;
  }
  for(int n:counts) if(n==0)throw new AssertionError("missing tile");
  System.out.println("PASS 9000 mass-teleport roll bounds");
 }
}
''')
classes=ROOT/'teleport_new'/'classes'
subprocess.run(['javac','-encoding','UTF-8','--release','21','-cp',str(classes),'-d',str(classes),str(checks)],check=True)
p=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.TelbookRandomBounds'],check=True,capture_output=True,text=True)
print(p.stdout.strip())
