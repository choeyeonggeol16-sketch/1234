#!/usr/bin/env python3
"""Phase14 source and Java21 typed differential regressions on production class bodies."""
from pathlib import Path
from zipfile import ZipFile
import hashlib,json,re,subprocess,tempfile,itertools,random
ROOT=Path(__file__).resolve().parents[2]
F=ROOT/'src/l1j/server/server/model/item/function'
D=Path(__file__).resolve().parent
M=json.loads((D/'MANIFEST.json').read_text())
sha=lambda v:hashlib.sha256(v.encode()).hexdigest()

def method_body(t,name):
    m=re.search(r'(?m)^[ \t]*(?:(?:public|private|protected)\s+)?static\s+[\w<>\[\]]+\s+'+re.escape(name)+r'\s*\(',t)
    assert m,name
    i=m.end()-1;dep=0
    while i<len(t):
        if t[i]=='(':dep+=1
        if t[i]==')':
            dep-=1
            if not dep:break
        i+=1
    i=t.index('{',i);start=i+1;dep=1;state='N';i=start
    while i<len(t):
        c=t[i];n=t[i+1] if i+1<len(t) else ''
        if state=='N':
            if c=='"':state='S'
            elif c=="'":state='C'
            elif c=='/' and n=='/':state='L';i+=1
            elif c=='/' and n=='*':state='B';i+=1
            elif c=='{':dep+=1
            elif c=='}':
                dep-=1
                if dep==0:return t[start:i]
        elif state in ('S','C'):
            if c=='\\':i+=1
            elif c==('"' if state=='S' else "'"):state='N'
        elif state=='L':
            if c=='\n':state='N'
        elif state=='B':
            if c=='*' and n=='/':state='N';i+=1
        i+=1
    raise AssertionError(name)

originals={n:(D/(n+'.java.baseline')).read_text() for n in ('MovementPotionEffects','FishitemService','MaanRaidTransactions')}
for row in M['moved']:
    old=method_body(originals[row['owner']],row['name'])
    got=method_body((F/(row['new_owner']+'.java')).read_text(),row['name'])
    if row['new_owner']=='ClanRaidTransactions':
        got=got.replace('ClanRaidBossSelection.configuredBossIds(', 'configuredBossIds(').replace('ClanRaidNpcSpawn.spawn(', 'spawn(')
    assert old==got,(row['owner'],row['new_owner'],row['name'])
    assert sha(old)==row['body_sha256'] and len(old)==row['length']
print('PASS Phase14: 23 production method bodies identical (raid calls normalized only)')
oldrouter=(D/'SpecialConsumableRouter.java.baseline').read_text()
newrouter=(F/'SpecialConsumableRouter.java').read_text()
assert newrouter.replace('MaanSkillConsumables.','MaanRaidTransactions.').replace('ClanRaidTransactions.','MaanRaidTransactions.')==oldrouter
assert 'MaanRaidTransactions' not in '\n'.join(x.read_text(errors='ignore') for x in F.glob('*.java'))
assert 'MovementPotionEffects.use' in (F/'PotionEffects.java').read_text()
assert 'FishitemService.clickItem' in (F/'FishingUtilityItems.java').read_text()
rod_old=originals['FishitemService'];rod_new=(F/'FishingRodProfile.java').read_text()
for token in ('RodSpec(41293','RodSpec(87058','RodSpec(87059','RodSpec(600229','RodSpec(4100293','RodSpec(4200293'):
    assert rod_old.count(token)==rod_new.count(token)==1
assert re.search(r'private static final RodSpec\[\] RODS = \{.*?\};',rod_old,re.S).group()==re.search(r'private static final RodSpec\[\] RODS = \{.*?\};',rod_new,re.S).group()
assert re.search(r'private static final class RodSpec\s*\{.*?\n    \}',rod_old,re.S).group()==re.search(r'private static final class RodSpec\s*\{.*?\n    \}',rod_new,re.S).group()
print('PASS Phase14: exact fishing rod registry, immutable duration types and array order; original Maan router order preserved')

def typed_suite(name,classes,stubs,driver):
    with tempfile.TemporaryDirectory(prefix='rkern_phase14_'+name+'_') as td:
        t=Path(td);allfiles={**classes,**stubs,'Test14.java':driver}
        for p,c in allfiles.items():
            q=t/p;q.parent.mkdir(parents=True,exist_ok=True);q.write_text(c,'utf-8')
        cmd=['javac','--release','21','-encoding','UTF-8','-d',str(t)]+[str(t/p) for p in allfiles]
        run=subprocess.run(cmd,capture_output=True,text=True)
        assert run.returncode==0, name+' typed Java21 compile failed: '+run.stderr[:10000]
        run=subprocess.run(['java','-cp',str(t),('l1j.server.server.model.item.function.Test14' if driver.startswith('package l1j.server.server.model.item.function;') else 'Test14')],capture_output=True,text=True)
        assert run.returncode==0,name+' runtime failed: '+run.stdout+'\n'+run.stderr[:10000]
        print(run.stdout.strip())

P='l1j/server/server/model/item/function/'
I='l1j/server/server/model/Instance/'
N='l1j/server/server/model/network/core/sc/packet/'
# ------------ movement potion original vs exact new production classes ------------
move_classes={P+x+'.java':(F/(x+'.java')).read_text() for x in ('MovementPotionEffects','HastePotionEffects','BraveryPotionEffects','FruitPotionEffects')}
move_classes[P+'MovementPotionEffectsBaseline.java']=originals['MovementPotionEffects'].replace('MovementPotionEffects','MovementPotionEffectsBaseline')
# Legacy file contained dozens of unrelated wildcard imports. Trim imports in the
# temporary test copy only; retain its entire original production method bodies.
used_imports = list(dict.fromkeys(i for k,c in move_classes.items() if not k.endswith("MovementPotionEffectsBaseline.java") for i in re.findall(r'(?m)^import [^;]+;',c)))
base_move = move_classes[P+'MovementPotionEffectsBaseline.java']
move_classes[P+'MovementPotionEffectsBaseline.java'] = ('package l1j.server.server.model.item.function;\n'+'\n'.join(used_imports)+'\n'+base_move[base_move.index('/** MovementPotionEffectsBaseline'):])
staticnames=re.findall(r'(?m)^import static l1j\.server\.server\.model\.character\.skill\.L1SkillId\.(\w+);',originals['MovementPotionEffects'])
move_skill='package l1j.server.server.model.character.skill; public class L1SkillId {\n'+'\n'.join(' public static final int '+nm+' = '+str(i+200)+';' for i,nm in enumerate(staticnames))+'\n}'
move_stubs={
 'l1j/server/server/model/character/skill/L1SkillId.java':move_skill,
 'l1j/server/server/model/item/L1ItemId.java':'''package l1j.server.server.model.item;
 public final class L1ItemId { public static final int POTION_OF_HASTE_SELF=4001,B_POTION_OF_HASTE_SELF=4002,POTION_OF_EMOTION_BRAVERY=4003,B_POTION_OF_EMOTION_BRAVERY=4004; }''',
 I+'L1PcInstance.java':'''package l1j.server.server.model.Instance;
 import java.util.*; import l1j.server.server.model.character.skill.L1SkillId;
 public class L1PcInstance {
 public final java.util.ArrayList<String> log=new java.util.ArrayList<>();
 public final java.util.HashMap<Integer,Integer> status = new java.util.HashMap<>();
 public int hasteEquipped,moveSpeed,braveSpeed;public boolean drink=true;
 public boolean hasSkillEffect(int k){return status.containsKey(k);}
 public int getId(){return 234;}
 public int getHasteItemEquipped(){return hasteEquipped;}
 public void setDrink(boolean b){drink=b;log.add("drink="+b);}
 public void killSkillEffectTimer(int k){log.add("kill="+k);status.remove(k);}
 public int getSkillEffectTimeSec(int k){return status.getOrDefault(k,0);}
 public void setMoveSpeed(int v){moveSpeed=v;log.add("move="+v);}
 public void setSkillEffect(int k,int ms){status.put(k,ms / 1000);log.add("effect="+k+"/"+ms);}
 public void setBraveSpeed(int v){braveSpeed=v;log.add("brave="+v);}
 public int getBraveSpeed(){return braveSpeed;}
 public void remove_elf_second_brave(){log.add("elfclear");}
 }''',
 P+'CreatureItemActions.java':'''package l1j.server.server.model.item.function;import l1j.server.server.model.Instance.L1PcInstance; public class CreatureItemActions {public static void cancelAbsoluteBarrier(L1PcInstance p){p.log.add("barrier");}}'''
}
for packet in ('SC_SkillSound','SC_ServerMessage','SC_SkillHaste','SC_SkillBrave'):
    move_stubs[N+packet+'.java']=f'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class {packet} {{public static void send(L1PcInstance p,Object... a){{p.log.add("{packet}.send"+java.util.Arrays.toString(a));}}
public static void broadcast(L1PcInstance p,Object... a){{p.log.add("{packet}.broadcast"+java.util.Arrays.toString(a));}} }}'''
move_test='''import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.function.*;
public class Test14 {
  static void run(int kind,boolean old,L1PcInstance p,int id){
    if(old){
      if(kind==0)MovementPotionEffectsBaseline.useGreenPotion(p,id);
      else if(kind==1)MovementPotionEffectsBaseline.useBravePotion(p,id);
      else MovementPotionEffectsBaseline.useFruit(p,id);
    }else{
      if(kind==0)MovementPotionEffects.useGreenPotion(p,id);
      else if(kind==1)MovementPotionEffects.useBravePotion(p,id);
      else MovementPotionEffects.useFruit(p,id);
    }
  }
  static void check(int kind,int id,int mask,int seed){
   L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();
   int[] keys={71,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,220,221,222,223,224,225,226,227,228,229,230,231,232,233,234,235};
   for(int i=0;i<keys.length;i++) if((mask&(1<<(i%24)))!=0){ a.status.put(keys[i],seed); b.status.put(keys[i],seed); }
   a.hasteEquipped=b.hasteEquipped=(seed==7200?1:0);
   run(kind,true,a,id);run(kind,false,b,id);
   if(!a.log.equals(b.log)||!a.status.equals(b.status)||a.moveSpeed!=b.moveSpeed||a.braveSpeed!=b.braveSpeed||a.drink!=b.drink)throw new AssertionError("potion "+kind+" id="+id+" mask="+mask+" seed="+seed+" old="+a.log+" new="+b.log);
  }
  public static void main(String[] argv){
   int[] ids={0,4001,4002,4003,4004,7006,40018,41342,140018,41338,30158,40039,40040,30067,40030,41261,41262,41268,41269,41271,41272,41273,30073,41415,712,40068,30076,140068,210110,40031,30075,40733,210036,30077,713,50000};
   java.util.Random r=new java.util.Random(0x14);
   int cases=0;
   for(int kind=0;kind<3;kind++)for(int id:ids)for(int i=0;i<160;i++){
      int mask=i<32 ? (1<<i) : r.nextInt(); int seed=(i%5==0)?7200:((i%5==1)?2:300);
      check(kind,id,mask,seed);cases++;
   }
   System.out.println("PASS movement potion Java21 production vs original: "+cases+" scenarios, exact effects/packets/skill mutation");
  }
}'''
# Baseline has package-private class; direct external test cannot call it. Move test into production package.
move_test=move_test.replace('public class Test14','public class Test14')
# change baseline and new MovementPotionEffects to public only in test build, since actual class remains package private
for k in (P+'MovementPotionEffects.java',P+'MovementPotionEffectsBaseline.java'):
    move_classes[k]=move_classes[k].replace('final class MovementPotionEffects','public final class MovementPotionEffects')
typed_suite('movement',move_classes,move_stubs,move_test)

# ------------ fishing: compile real production and compare state and ordered side effects ------------
fish_classes={P+x+'.java':(F/(x+'.java')).read_text() for x in ('FishitemService','FishingIslandRodUse','FishingPondRodUse','FishingUseRules','FishingRodSession','FishingRodProfile')}
fish_classes[P+'FishitemServiceBaseline.java']=originals['FishitemService'].replace('FishitemService','FishitemServiceBaseline')
fish_stubs={
 'l1j/server/Config.java':'package l1j.server; public class Config { public static int FISH_TIME1=130,FISH_TIME2=240; }',
 'l1j/server/server/ActionCodes.java':'package l1j.server.server; public class ActionCodes { public static final int ACTION_Fishing=15; }',
 'l1j/server/server/Controller/FishingTimeController.java':'''package l1j.server.server.Controller; import l1j.server.server.model.Instance.L1PcInstance;
public class FishingTimeController {public static FishingTimeController getInstance(){return new FishingTimeController();} public void addMember(L1PcInstance p){p.log.add("controller");}}''',
 'l1j/server/server/model/character/skill/L1SkillId.java':'package l1j.server.server.model.character.skill; public class L1SkillId {public static final int SHAPE_CHANGE=1,INVISIBILITY=2,Fishing_etc=3;}',
 I+'L1ItemInstance.java':'''package l1j.server.server.model.Instance; public class L1ItemInstance {public int id,charge=1; public L1ItemInstance(int i,int c){id=i;charge=c;} public int getItemId(){return id;}public int getChargeCount(){return charge;}}''',
 I+'L1PcInstance.java':'''package l1j.server.server.model.Instance; import java.util.*;
public class L1PcInstance {
  public final List<String> log=new ArrayList<>();public final Inventory inventory=new Inventory();
  public boolean fishing,ready,dead,teleport,shape,invis;public int mapId=4,x=33602,y=33245,_fishingX,_fishingY; public L1ItemInstance _fishingRod;
  public long fishingTime;
  public boolean isFishing(){return fishing;}public boolean isDead(){return dead;}public boolean get_teleport(){return teleport;}
  public int getMapId(){return mapId;}public int getX(){return x;}public int getY(){return y;} public int getId(){return 900;}
  public boolean hasSkillEffect(int k){return k==1?shape:invis;}
  public Inventory getInventory(){return inventory;}
  public void setFishing(boolean v){fishing=v;log.add("fishing="+v);}
  public void setFishingReady(boolean v){ready=v;log.add("ready="+v);}
  public void setFishingTime(long t){fishingTime=t;log.add("time");}
  public class Inventory {public int weight,size,bait=2;public L1ItemInstance rod;
    public int getWeight100(){return weight;}public int getSize(){return size;}
    public L1ItemInstance getItem(int oid){return oid==10?rod:null;}
    public int countItems(int id){return bait;}
  }
}''',
 'l1j/server/server/model/map/L1WorldMap.java':'''package l1j.server.server.model.map;
public class L1WorldMap {public static int tile=28; public static L1WorldMap getInstance(){return new L1WorldMap();}
  public L1WorldMap getMap(short id){return this;}public int getOriginalTile(int x,int y){return tile;} }''',
 'l1j/server/server/packets/Protocol/MainServer/Proto/SC_FISHING_NOT.java':'''package l1j.server.server.packets.Protocol.MainServer.Proto;
import l1j.server.server.model.Instance.L1PcInstance;public class SC_FISHING_NOT {public static void sendCreate(L1PcInstance p,int s){p.log.add("FISHING_NOT="+s);}}''',
}
for packet in ('SC_Fishing','SC_ServerMessage','SC_SystemMessage','SC_InventoryIcon'):
    fish_stubs[N+packet+'.java']=f'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class {packet} {{
 public static void send(L1PcInstance p,Object... args){{p.log.add("{packet}.send"+java.util.Arrays.toString(args));}}
 public static void broadcast(L1PcInstance p,Object... args){{p.log.add("{packet}.broadcast"+java.util.Arrays.toString(args));}}
 public static void iconNewUnLimit(L1PcInstance p,Object... args){{p.log.add("{packet}.icon"+java.util.Arrays.toString(args));}}
}}'''
fish_test='''import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.function.*;
import l1j.server.server.model.map.L1WorldMap;
public class Test14 {
 static void run(int kind, boolean old, L1PcInstance pc,int itemId,int tx,int ty,L1ItemInstance item,int objectId){
   if(old){if(kind==0)FishitemServiceBaseline.clickItem(pc,itemId,tx,ty,item,objectId);else FishitemServiceBaseline.clickItem1(pc,itemId,tx,ty,item,objectId);}
   else{if(kind==0)FishitemService.clickItem(pc,itemId,tx,ty,item,objectId);else FishitemService.clickItem1(pc,itemId,tx,ty,item,objectId);}
 }
 static void check(int kind,int rodId,int argid,int mask,int cx,int cy){
  L1PcInstance a=new L1PcInstance(),b=new L1PcInstance();
  a.mapId=b.mapId=(mask&1)!=0 ? (kind==0?5490:4) : (kind==0?4:5490);
  a.x=b.x=(kind==0?33602:32749);a.y=b.y=(kind==0?33245:32821);
  a.fishing=b.fishing=(mask&2)!=0;a.dead=b.dead=(mask&4)!=0;a.teleport=b.teleport=(mask&8)!=0;
  a.shape=b.shape=(mask&16)!=0;a.invis=b.invis=(mask&32)!=0;
  a.inventory.weight=b.inventory.weight=(mask&64)!=0?83:81;
  a.inventory.size=b.inventory.size=(mask&128)!=0?165:164;
  a.inventory.bait=b.inventory.bait=(mask&256)!=0?0:3;
  a.inventory.rod=new L1ItemInstance(rodId,(mask&512)!=0?0:2);
  b.inventory.rod=new L1ItemInstance(rodId,(mask&512)!=0?0:2);
  L1ItemInstance it1=new L1ItemInstance(argid,(mask&1024)!=0?0:2),it2=new L1ItemInstance(argid,(mask&1024)!=0?0:2);
  int oid=(mask&2048)!=0?11:10;
  L1WorldMap.tile=(mask&4096)!=0?1:28;
  run(kind,true,a,argid,cx,cy,it1,oid);run(kind,false,b,argid,cx,cy,it2,oid);
  if(!a.log.equals(b.log)||a.fishing!=b.fishing||a.ready!=b.ready||a._fishingX!=b._fishingX||a._fishingY!=b._fishingY||((a._fishingRod!=null)!=(b._fishingRod!=null))||a.fishingTime>0 != (b.fishingTime>0))throw new AssertionError("fishing "+kind+" rod="+rodId+" arg="+argid+" mask="+mask+" old="+a.log+" new="+b.log);
 }
 public static void main(String[] args){
 int[] ids={0,41293,41294,41305,41306,600229,9991,87058,87059,4100293,4200293,999999};
 int cases=0;java.util.Random r=new java.util.Random(4432);
 for(int kind=0;kind<2;kind++)for(int id:ids)for(int j=0;j<600;j++){
  int mask=j<13? (1<<j):r.nextInt(8192);
  int x=(kind==0?33602:32749)+(j%5==0?18:j%7==0?5:1);
  int y=(kind==0?33245:32821)+(j%11==0?18:0);
  check(kind,id,(j%11==0?0:id),mask,x,y);cases++;
 }
 System.out.println("PASS fishing Java21 production vs original: "+cases+" scenarios, identical order/state/rod table");
 }
}'''
typed_suite('fishing',fish_classes,fish_stubs,fish_test)

# ------------ Maan: production skill/raid/spawn/CSV classes against original production code ------------
maan_classes={P+x+'.java':(F/(x+'.java')).read_text() for x in ('MaanSkillConsumables','ClanRaidBossSelection','ClanRaidNpcSpawn','ClanRaidTransactions')}
maan_classes[P+'MaanRaidTransactionsBaseline.java']=originals['MaanRaidTransactions'].replace('MaanRaidTransactions','MaanRaidTransactionsBaseline')
maan_stubs={
 'l1j/server/Config.java':'''package l1j.server;public class Config {public static String clanraidmobboss="9000";}''',
 'l1j/server/server/IdFactory.java':'''package l1j.server.server;public class IdFactory {public static int next=100;public static IdFactory getInstance(){return new IdFactory();}public int nextId(){return next++;}}''',
 'l1j/server/server/model/L1InventoryTransaction.java':'''package l1j.server.server.model;
 import l1j.server.server.model.Instance.*;
 public class L1InventoryTransaction implements AutoCloseable {
  public static boolean busy=false,debitSuccess=true;
  public static L1InventoryTransaction begin(L1PcInstance pc,int id){if(busy)throw new IllegalStateException("busy");pc.log.add("begin="+id);return new L1InventoryTransaction(pc);}
  L1PcInstance pc;public L1InventoryTransaction(L1PcInstance p){pc=p;}
  public boolean debit(L1ItemInstance it,int n){pc.log.add("debit="+n);return debitSuccess;}
  public void onFailure(Runnable f){pc.log.add("onFailure");}
  public void change(L1ItemInstance item,java.util.function.Consumer<L1ItemInstance> fn){pc.log.add("change");fn.accept(item);}
  public void commitAfterInventoryRelease(Runnable success){pc.log.add("commit");success.run();}
  public void close(){pc.log.add("close");}
 }''',
 'l1j/server/server/model/L1ItemDelay.java':'''package l1j.server.server.model;import l1j.server.server.model.Instance.*;
 public class L1ItemDelay {public static void onItemUse(L1PcInstance p,L1ItemInstance i){p.log.add("delay");}}''',
 'l1j/server/server/datatables/NpcTable.java':'''package l1j.server.server.datatables;import l1j.server.server.model.Instance.*;
 public class NpcTable { public static int missing=0;public static NpcTable getInstance(){return new NpcTable();}
 public Object getTemplate(int id){return id==missing?null:new Object();}
 public L1NpcInstance newNpcInstance(int id){return id==5147||id==5148||id==5149||id==5150||id==5151 ? new L1DoorInstance(id):new L1NpcInstance(id);}
 }''',
 'l1j/server/server/model/L1World.java':'''package l1j.server.server.model;import l1j.server.server.model.Instance.*;
 public class L1World { public static final java.util.ArrayList<String> events=new java.util.ArrayList<>();
 public static L1World getInstance(){return new L1World();}
 public void storeAndEnterWorld(L1NpcInstance npc){events.add("world="+npc.getNpcId()+"/"+npc.getX()+"/"+npc.getY());}}
 ''',
 I+'L1ItemInstance.java':'''package l1j.server.server.model.Instance;import java.sql.Timestamp;public class L1ItemInstance {
 public int id=101,count=1;public Timestamp used;
 public int getId(){return id;}public int getCount(){return count;}
 public void setLastUsed(Timestamp t){used=t;}public Timestamp getLastUsed(){return used;}
 }''',
 I+'L1PcInstance.java':'''package l1j.server.server.model.Instance;import java.util.*;
 public class L1PcInstance {public final List<String> log=new ArrayList<>();
 public int mapId=53,x=32710,y=32810,heading=4;public int getMapId(){return mapId;}
 public int getX(){return x;} public int getY(){return y;}public int getId(){return 42;}public int getHeading(){return heading;}
 public void sendPackets(String s){log.add("send="+s);}
 public void start_teleport(int x,int y,int m,int h,int e,boolean b,boolean a){log.add("teleport="+x+"/"+y+"/"+m+"/"+h+"/"+e+"/"+b+"/"+a);}
 }''',
 I+'L1NpcInstance.java':'''package l1j.server.server.model.Instance;import l1j.server.server.model.L1World;
 public class L1NpcInstance {
  public static final int CHAT_TIMING_APPEARANCE=1;
  protected int id,npcId,map,x,y,heading,homeX,homeY;
  public L1NpcInstance(int type){npcId=type;}
  public void setId(int v){id=v;}public void setMap(short v){map=v;}
  public L1Location getLocation(){return new L1Location(this);}
  public int getX(){return x;}public int getY(){return y;}
  public void setX(int v){x=v;}public void setY(int v){y=v;}
  public L1Map getMap(){return new L1Map();}
  public int getNpcId(){return npcId;}
  public void setHomeX(int a){homeX=a;}public void setHomeY(int a){homeY=a;}public void setHeading(int a){heading=a;}
  public Light getLight(){return new Light();}public void startChat(int k){L1World.events.add("chat="+npcId);}
  public class Light {public void turnOnOffLight(){L1World.events.add("light="+npcId);}}
  public static class L1Map {public boolean isInMap(L1Location v){return true;}public boolean isPassable(L1Location v){return true;}}
  public static class L1Location {L1NpcInstance npc;L1Location(L1NpcInstance n){npc=n;}
    public void set(int x,int y,short map){npc.x=x;npc.y=y;npc.map=map;}
    public void forward(int h){npc.x+=1;}
  }
 }''',
 I+'L1DoorInstance.java':'''package l1j.server.server.model.Instance;import l1j.server.server.model.L1World;
 public class L1DoorInstance extends L1NpcInstance {public L1DoorInstance(int id){super(id);}
 public void setLeftEdgeLocation(int v){L1World.events.add("left="+v);}
 public void setRightEdgeLocation(int v){L1World.events.add("right="+v);}
 public void setDirection(int v){L1World.events.add("dir="+v);}
 }''',
 'l1j/server/server/model/character/skill/L1SkillId.java':'''package l1j.server.server.model.character.skill;public class L1SkillId{public static int LIFE_MAAN=99;}''',
 'l1j/server/server/model/character/skill/L1SkillUse.java':'''package l1j.server.server.model.character.skill;import l1j.server.server.model.Instance.L1PcInstance;
 public class L1SkillUse{public static final int TYPE_GMBUFF=2;
 public static void cast(L1PcInstance p,int a,int b,int c,int d,Object t,int e,int f){p.log.add("cast="+a+"/"+b+"/"+c+"/"+d+"/"+e+"/"+f);}}
 ''',
 N+'SC_SystemMessage.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;
 public class SC_SystemMessage{public static void send(L1PcInstance pc,String s){pc.log.add("system="+s);}}''',
 'l1j/server/server/utils/L1SpawnUtil.java':'''package l1j.server.server.utils;import l1j.server.server.model.L1World;
 public class L1SpawnUtil {public static void spawn2(int x,int y,short m,int npcId,int f,int life,int g){L1World.events.add("portal="+x+"/"+y+"/"+m+"/"+npcId+"/"+f+"/"+life+"/"+g);}}''',
 P+'SpecialConsumableRouter.java':'''package l1j.server.server.model.item.function;
 import java.sql.Timestamp;
 public class SpecialConsumableRouter {
  public enum Result {RETURN,HANDLED}
  public static Timestamp nowTimestamp(){return new Timestamp(123456789L);}
 }''',
}
maan_test='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.*;
import l1j.server.server.datatables.NpcTable;
import l1j.server.Config;
import java.util.*;
public class Test14 {
 static int tests=0;
 static void eq(Object a,Object b,String why){if(!java.util.Objects.equals(a,b))throw new AssertionError(why+": "+a+" != "+b);}
 static void compare(L1PcInstance a,L1PcInstance b,L1ItemInstance ia,L1ItemInstance ib,String why){
   eq(a.log,b.log,why+" trace");eq(ia.getLastUsed(),ib.getLastUsed(),why+" timestamp");tests++;
 }
 static void clear(){L1World.events.clear();l1j.server.server.IdFactory.next=100;}
 public static void main(String[] args){
  java.util.Random r=new java.util.Random(14);
  String[] base={null,"","  ","0","1","1,2,3","1,abc,3","-2,0,5","2,,8","999999"," 5 , 5 , 7 ","2, 0,-9,z, 3, ,1"};
  int csvCount=0;
  for(int i=0;i<1500;i++){
   String csv=i<base.length?base[i]:String.join(",",java.util.Arrays.asList(Integer.toString(r.nextInt(40)-15),"x",Integer.toString(r.nextInt(100))," "));
   int[] a=MaanRaidTransactionsBaseline.parseBossIds(csv),b=ClanRaidBossSelection.parseBossIds(csv);
   if(!Arrays.equals(a,b))throw new AssertionError("parse="+csv);
   a=MaanRaidTransactionsBaseline.configuredBossIds(csv);b=ClanRaidBossSelection.configuredBossIds(csv);
   if(!Arrays.equals(a,b))throw new AssertionError("config="+csv);
   // Cached arrays are reused without allocations when config text is unchanged.
   if(a!=MaanRaidTransactionsBaseline.configuredBossIds(csv)||b!=ClanRaidBossSelection.configuredBossIds(csv))throw new AssertionError("cache="+csv);
   csvCount++;
  }
  for(int skillId:new int[]{1,99,1201}){
   L1PcInstance p=new L1PcInstance(),q=new L1PcInstance();L1ItemInstance a=new L1ItemInstance(),b=new L1ItemInstance();
   MaanRaidTransactionsBaseline.castAndTouch(p,a,skillId);MaanSkillConsumables.castAndTouch(q,b,skillId);
   compare(p,q,a,b,"castAndTouch");
  }
  for(boolean busy:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true}){
   L1InventoryTransaction.busy=busy;L1InventoryTransaction.debitSuccess=debit;
   L1PcInstance p=new L1PcInstance(),q=new L1PcInstance();L1ItemInstance a=new L1ItemInstance(),b=new L1ItemInstance();
   eq(MaanRaidTransactionsBaseline.useLifeMaanOnce(p,a),MaanSkillConsumables.useLifeMaanOnce(q,b),"life result");
   compare(p,q,a,b,"life/receipt");
  }
  L1InventoryTransaction.busy=false;L1InventoryTransaction.debitSuccess=true;
  // deterministic failure path: impossible map regardless of random roll.
  for(int i=0;i<250;i++){
   L1PcInstance p=new L1PcInstance(),q=new L1PcInstance();p.mapId=q.mapId=-1;
   L1ItemInstance a=new L1ItemInstance(),b=new L1ItemInstance();
   eq(MaanRaidTransactionsBaseline.useRaidItem(p,a,53,1,2,(short)4,999,100,200,(short)4),
       ClanRaidTransactions.useRaidItem(q,b,53,1,2,(short)4,999,100,200,(short)4),"raid failed-map return");
   compare(p,q,a,b,"raid failed-map");
  }
  // Successful raid routes: one configured boss means random selection yields same template.
  Config.clanraidmobboss="9000";
  for(int count:new int[]{1,2})for(boolean busy:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true}){
   L1InventoryTransaction.busy=busy;L1InventoryTransaction.debitSuccess=debit;
   L1PcInstance p=new L1PcInstance(),q=new L1PcInstance();L1ItemInstance a=new L1ItemInstance(),b=new L1ItemInstance();a.count=b.count=count;
   // retry until RNG passes for both; compare successful attempt outputs only.
   boolean oldOk=false,newOk=false;
   for(int j=0;j<3000;j++){
     p.log.clear();a.used=null;clear();
     MaanRaidTransactionsBaseline.useRaidItem(p,a,53,1,2,(short)4,999,100,200,(short)4);
     if(!p.log.isEmpty()&&!p.log.get(0).startsWith("send=")){oldOk=true;break;}
   }
   if(!oldOk)throw new AssertionError("raid old never started");
   java.util.ArrayList<String> oldWorld=new java.util.ArrayList<>(L1World.events);
   for(int j=0;j<3000;j++){
     q.log.clear();b.used=null;clear();
     ClanRaidTransactions.useRaidItem(q,b,53,1,2,(short)4,999,100,200,(short)4);
     if(!q.log.isEmpty()&&!q.log.get(0).startsWith("send=")){newOk=true;break;}
   }
   if(!newOk)throw new AssertionError("raid new never started");
   compare(p,q,a,b,"raid success");eq(oldWorld,L1World.events,"raid event order");
  }
  L1InventoryTransaction.busy=false;L1InventoryTransaction.debitSuccess=true;
  for(int type:new int[]{500,5147,5148,5149,5150,5151}){
   clear();MaanRaidTransactionsBaseline.spawn(33000,33100,(short)4,1,type,0,1);
   java.util.ArrayList<String> events=new java.util.ArrayList<>(L1World.events);
   clear();ClanRaidNpcSpawn.spawn(33000,33100,(short)4,1,type,0,1);
   eq(events,L1World.events,"spawn="+type);tests++;
  }
  System.out.println("PASS Maan/raid Java21 production vs original: "+csvCount+" config/CSV + "+tests+" effect/receipt/spawn cases; unchanged commit ordering");
 }
}'''
typed_suite('maan',maan_classes,maan_stubs,maan_test)
