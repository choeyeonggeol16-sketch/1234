#!/usr/bin/env python3
"""Compile extracted production methods against typed, deterministic doubles. No real DB/world."""
from pathlib import Path
import tempfile, subprocess, re
P=Path(__file__).resolve().parent
R=P.parents[1]/'src/l1j/server/server'; D=R/'model/item/function'
def method(path, sig):
    s=path.read_text(); start=s.index(sig); i=s.index('{',start); n=1; j=i+1
    while n:
        n += (s[j]=='{')-(s[j]=='}');j+=1
    return s[start:j]
def body(path, name):
    s=path.read_text();return s[s.index('final class '+name):]
def run(name,code):
    with tempfile.TemporaryDirectory() as tmp:
        p=Path(tmp)/'Test.java';p.write_text(code)
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',tmp,str(p)],check=True)
        subprocess.run(['java','-cp',tmp,'Test'],check=True)
fishing=body(D/'FishingRodSession.java','FishingRodSession')
fishing=fishing.replace('requiresCharge(rodId)','FishingUseRules.requiresCharge(rodId)').replace('safeCharge(rod)','FishingUseRules.safeCharge(rod)').replace('applyRodUiOnly(pc, rodId)','FishingRodProfile.applyRodUiOnly(pc, rodId)')
controller=R/'Controller/FishingTimeController.java'
code='''
class L1ItemInstance { int id=5,itemId=41294,count=1,charges=2; int getId(){return id;}int getItemId(){return itemId;}int getCount(){return count;}int getChargeCount(){return charges;} }
class Inventory { L1ItemInstance current;int bait=1,spent,remove,grants;boolean removeOK=true;L1ItemInstance getItem(int id){return current!=null&&current.id==id?current:null;}int countItems(int id){return bait;}boolean consumeItem(int id,int n){if(bait<n)return false;bait-=n;spent+=n;return true;}int removeItem(L1ItemInstance i,int n){if(!removeOK)return 0;current=null;remove++;return n;}L1ItemInstance storeItem(int id,int n){grants++;return new L1ItemInstance();} }
class L1PcInstance { L1ItemInstance _fishingRod;int _fishingX,_fishingY,map=4,hp=100,trade,delay,notices,stops;long time;boolean fishing,ready,dead,busy,resolving;Inventory inv=new Inventory();int getId(){return 1;}Inventory getInventory(){return inv;}boolean isFishing(){return fishing;}'''+method(R/'model/Instance/L1PcInstance.java','public void setFishing(boolean flag)').replace('_isFishingReady','ready').replace('_isFishing','fishing').replace('_fishingTime','time')+'''void setFishingReady(boolean b){ready=b;}void setFishingTime(long t){time=t;}long getFishingTime(){return time;}int getMapId(){return map;}int getCurrentHp(){return hp;}boolean isDead(){return dead;}boolean isItemTransferBusy(){return busy;}boolean isTradeResolving(){return resolving;}int getTradeID(){return trade;}
'''+method(R/'model/Instance/L1PcInstance.java','public void offFishing()')+'''
}
class FishingUseRules {'''+method(D/'FishingUseRules.java','static boolean requiresCharge(')+method(D/'FishingUseRules.java','static int safeCharge(')+'''}
class FishingRodProfile {static boolean fail;static void applyRodUiOnly(L1PcInstance p,int id){p.time=100;if(fail)throw new IllegalStateException("UI");}}
class SC_ServerMessage{static void send(L1PcInstance p,int id){p.notices++;}}
class SC_Fishing{static int fail;static void send(L1PcInstance p,int id,int a,int x,int y){if(fail==1)throw new IllegalStateException("send");}static void broadcast(L1PcInstance p,int id,int a,int x,int y){if(fail==2)throw new IllegalStateException("broadcast");}}
class SC_CharVisualUpdate{static int pack(L1PcInstance p){return 1;}static void sendPacked(L1PcInstance p,int k){if(SC_Fishing.fail==3)throw new IllegalStateException("off packet");}static void broadcastPacked(L1PcInstance p,int k){}}
class ActionCodes{static final int ACTION_Fishing=1;}
class CIOThreadCallback{static CIOThreadCallback I=new CIOThreadCallback();static boolean accepted;static CIOThreadCallback getInstance(){return I;}Object startFishing(L1PcInstance p){return accepted?this:null;}}
class Config{static int FISH_TIME1=60,FISH_TIME2=60,FISH_EXP1=1,FISH_EXP2=2;}
class FishingTimeController{static final long TICK_MS=1000,FISHING_GRACE_MS=1;static final int MODE_NORMAL=7;static final byte EXP_NONE=0,EXP_ANCIENT_SILVER=1,EXP_ANCIENT_GOLD=2,EXP_GROWN_UP=3,EXP_HIGH_GROWN_UP=4;static FishingTimeController I=new FishingTimeController();static FishingTimeController getInstance(){return I;}
void removeMember(L1PcInstance pc){pc.stops++;}void endFishing(L1PcInstance pc){pc._fishingRod=null;pc.setFishing(false);pc.setFishingReady(false);pc.setFishingTime(0);removeMember(pc);}
static long nextDelayMs(L1PcInstance p,long t){return p.fishing?1000:-1;}void processChargeRod(L1PcInstance p,L1ItemInstance r,int rId,int t,byte exp,int mode){if(!isNoBaitRod(r.itemId)&&!p.inv.consumeItem(41295,1)){endFishing(p);return;}p.delay++;}void processGrowthRod(L1PcInstance p,L1ItemInstance r,int t,int exp,byte e){p.delay++;}static void setNextFishing(L1PcInstance p,long t,int n){p.time=t+n;}void prepareRodCycle(L1PcInstance p,L1ItemInstance r,int replacement,int seconds,int mode,int exp,byte type){if(!p.inv.consumeItem(41295,1)){endFishing(p);return;}p.delay++;}
'''+method(controller,'public boolean addMember(')+method(controller,'private long processPlayer(')+method(controller,'private static boolean isNoBaitRod(')+'''
long tick(L1PcInstance p){return processPlayer(p,200);}
}
'''+fishing+'''
public class Test {
static void ck(boolean v){if(!v)throw new AssertionError();}
static L1PcInstance pc(int itemId){L1PcInstance p=new L1PcInstance();p.inv.current=new L1ItemInstance();p.inv.current.itemId=itemId;return p;}
public static void main(String[] a){int n=0;
 int[] rods={41293,41294,41305,41306,9991,600229,87058,87059,4100293,4200293};
 for(int rod:rods) for(int f=0;f<32;f++){L1PcInstance p=pc(rod);if((f&1)!=0)p.inv.current.charges=0;if((f&2)!=0)p.inv.bait=0;CIOThreadCallback.accepted=(f&4)==0;SC_Fishing.fail=(f&8)!=0?1:0;FishingRodProfile.fail=(f&16)!=0;
 boolean allowed=(!FishingUseRules.requiresCharge(rod)||(f&1)==0)&&(FishingRodSession.isBaitlessRodFallback(rod)||(f&2)==0);boolean expected=allowed&&(f&28)==0;
 try{FishingRodSession.doStartFishing(p,10,20,5);}catch(IllegalStateException ex){ck(allowed&&((f&24)!=0));}
 ck(p.isFishing()==expected&&p.ready==expected&&(p._fishingRod!=null)==expected);ck(expected||p.time==0);ck(p.inv.spent==0);
 if(expected){SC_Fishing.fail=3;p.offFishing();ck(p._fishingRod==null&&!p.fishing&&!p.ready&&p.time==0&&p.stops==1);}n++;
 }
 for(int f=0;f<128;f++){L1PcInstance p=pc(41294);p.fishing=true;p.ready=true;p._fishingRod=p.inv.current;if((f&1)!=0)p.inv.current=new L1ItemInstance();p.map=(f&2)!=0?9:4;p.dead=(f&4)!=0;p.busy=(f&8)!=0;p.resolving=(f&16)!=0;p.trade=(f&32)!=0?1:0;if((f&64)!=0)p._fishingRod.count=0;
 long ret=FishingTimeController.I.tick(p);boolean stop=(f&71)!=0;boolean paused=!stop&&(f&56)!=0;ck(stop?ret==-1L:ret>0);ck(p.fishing!=stop);ck(p.inv.spent==(!stop&&!paused?1:0));ck(p.delay==(!stop&&!paused?1:0));n++;}
 L1PcInstance direct=pc(41294);direct.fishing=true;direct.ready=true;direct.time=1;direct._fishingRod=direct.inv.current;direct.setFishing(false);ck(!direct.fishing&&!direct.ready&&direct.time==0&&direct._fishingRod==null);n++;System.out.println("FISHING_PASS "+n+" matrix outcomes");}
}
'''
run('fishing',code)
code='''import java.sql.Timestamp;
class L1Item {int type=0;int getType2(){return type;}}
class L1EtcItem extends L1Item {int effect=10;int get_delayEffect(){return effect;}}
class L1ItemInstance {int id=1,charge=1;Timestamp stamp;L1EtcItem def=new L1EtcItem();int getId(){return id;}L1Item getItem(){return def;}int getChargeCount(){return charge;}void setChargeCount(int n){charge=n;}Timestamp getLastUsed(){return stamp;}void setLastUsed(Timestamp t){stamp=t;}}
class CUserInventory {static final int COL_DELAY_EFFECT=1,COL_CHARGE_COUNT=2;L1ItemInstance current;int dirty,notices,removed;boolean failSave,failRemove,zeroRemove;L1ItemInstance getItem(int id){return current!=null&&id==current.id?current:null;}void saveItem(L1ItemInstance i,int c){if(failSave)throw new IllegalStateException("save");dirty|=c;}void updateItem(L1ItemInstance i,int c){notices++;}int removeItem(L1ItemInstance i,int n){if(failRemove)throw new IllegalStateException("delete");if(zeroRemove)return 0;current=null;removed+=n;return n;}}
class L1PcInstance {CUserInventory inv=new CUserInventory();int delay;CUserInventory getInventory(){return inv;}}
class ItemActionContext {L1PcInstance p;L1ItemInstance i;int itemId;boolean delay;ItemActionContext(L1PcInstance p,L1ItemInstance i,int id,boolean d){this.p=p;this.i=i;itemId=id;delay=d;}L1PcInstance player(){return p;}L1ItemInstance item(){return i;}int itemId(){return itemId;}boolean delayEffect(){return delay;}}
class L1ItemDelay {static void onItemUse(L1PcInstance p,L1ItemInstance i){p.delay++;}}
'''+body(D/'ItemActionCompletion.java','ItemActionCompletion').replace('l1j.server.server.templates.L1EtcItem','L1EtcItem')+'''
public class Test {static void ck(boolean b){if(!b)throw new AssertionError();}public static void main(String[] a){int n=0;
for(int f=0;f<128;f++){L1PcInstance p=new L1PcInstance();L1ItemInstance i=new L1ItemInstance();i.charge=(f&1)==0?1:2;i.stamp=new Timestamp(100);int old=i.charge;Timestamp last=i.stamp;if((f&2)==0)p.inv.current=i;else p.inv.current=new L1ItemInstance();p.inv.failSave=(f&4)!=0;p.inv.failRemove=(f&8)!=0;p.inv.zeroRemove=(f&16)!=0;boolean charged=(f&32)==0,delay=(f&64)==0;boolean live=(f&2)==0;boolean removing=live&&delay&&charged&&old<=1;boolean save=live&&delay&&!removing; boolean fail=removing&&p.inv.failRemove||save&&p.inv.failSave;boolean early=removing&&!p.inv.failRemove&&p.inv.zeroRemove; boolean threw=false;try{ItemActionCompletion.finish(new ItemActionContext(p,i,charged?410008:123,delay));}catch(IllegalStateException e){threw=true;}ck(threw==fail);ck(p.delay==(!fail&&!early?1:0));ck(p.inv.removed==(removing&&!fail&&!early?1:0));if(fail||early||!live||!delay){ck(i.charge==old&&i.stamp==last&&p.inv.dirty==0);}else if(removing){ck(i.charge==0&&p.inv.dirty==0);}else{ck(p.inv.dirty==(charged?3:1));ck(i.charge==(charged?old-1:old)&&i.stamp!=last);}n++;}
for(int f=0;f<8;f++){L1PcInstance p=new L1PcInstance();L1ItemInstance i=new L1ItemInstance();if((f&1)==0)p.inv.current=i;if((f&2)!=0)i.def.effect=0;p.inv.failSave=(f&4)!=0;boolean save=(f&3)==0;boolean threw=false;try{ItemActionCompletion.finishCommittedConsumable(p,i);}catch(IllegalStateException ex){threw=true;}ck(threw==(save&&p.inv.failSave));ck(p.inv.dirty==(save&&!threw?1:0));ck(p.delay==(threw?0:1));n++;}
System.out.println("COMPLETION_PASS "+n+" matrix outcomes");}}
'''
run('completion',code)
# Match the original world query's map/id/point/life predicates without creating a result list.
code='''import java.util.*;
class Point{int x,y;Point(int x,int y){this.x=x;this.y=y;}boolean isSamePoint(Point p){return x==p.x&&y==p.y;}}
class L1Object{int id,map;Point point=new Point(1,1);int getId(){return id;}int getMapId(){return map;}Point getLocation(){return point;}}
class L1PcInstance extends L1Object{boolean dead;boolean isDead(){return dead;}}
class L1World{static L1World I=new L1World();List<L1PcInstance> players=new ArrayList<>();static L1World getInstance(){return I;}Collection<L1PcInstance> getAllPlayers(){return players;}}
public class Test{'''+method(D/'ResurrectionConsumables.java','private static boolean hasLivingOccupant(')+'''
static void ck(boolean b){if(!b)throw new AssertionError();}public static void main(String[] args){int n=0;for(int f=0;f<64;f++){L1World.I.players.clear();L1Object target=new L1Object();target.id=1;target.map=4;L1PcInstance other=new L1PcInstance();other.id=(f&1)!=0?1:2;other.map=(f&2)!=0?5:4;other.point=new Point((f&4)!=0?2:1,(f&8)!=0?2:1);other.dead=(f&16)!=0;L1World.I.players.add(null);L1World.I.players.add(other);if((f&32)!=0){L1PcInstance blocker=new L1PcInstance();blocker.id=3;blocker.map=4;L1World.I.players.add(blocker);}ck(hasLivingOccupant(target)==((f&32)!=0||(f&31)==0));n++;}System.out.println("RESURRECTION_PASS "+n+" occupancy conditions");}}
'''
run('resurrection',code)
# Use actual load() method with fake JAXB, injected failed init, and removed definitions.
box=R/'model/item/L1TreasureBox.java'
code='''import java.util.*;import java.io.*;import java.util.logging.*;
class JAXBContext{static JAXBContext newInstance(Class<?> c){return new JAXBContext();}Unmarshaller createUnmarshaller(){return new Unmarshaller();}}
class Unmarshaller{static L1TreasureBox.TreasureBoxList list;Object unmarshal(File f){return list;}}
class L1TreasureBox{static Logger _log=Logger.getAnonymousLogger();static String PATH="fake";static class TreasureBoxList extends ArrayList<L1TreasureBox>{}
int id;boolean fail;L1TreasureBox(int id,boolean f){this.id=id;fail=f;}int getBoxId(){return id;}void init(){if(fail)throw new IllegalStateException("init");}
'''+re.search(r'private static volatile HashMap<Integer, L1TreasureBox> _dataMap = .*?;',box.read_text()).group(0)+method(box,'public static L1TreasureBox get(')+method(box,'public static boolean load()')+'''}
public class Test{static void ck(boolean b){if(!b)throw new AssertionError();}static void set(L1TreasureBox... b){Unmarshaller.list=new L1TreasureBox.TreasureBoxList();Unmarshaller.list.addAll(Arrays.asList(b));}public static void main(String[] args){L1TreasureBox a=new L1TreasureBox(1,false),b=new L1TreasureBox(2,false),c=new L1TreasureBox(3,false);set(a,b);ck(L1TreasureBox.load());ck(L1TreasureBox.get(2)==b);set(c,new L1TreasureBox(4,true));ck(!L1TreasureBox.load());ck(L1TreasureBox.get(1)==a&&L1TreasureBox.get(2)==b&&L1TreasureBox.get(3)==null);set(c);ck(L1TreasureBox.load());ck(L1TreasureBox.get(1)==null&&L1TreasureBox.get(2)==null&&L1TreasureBox.get(3)==c);set();ck(L1TreasureBox.load()&&L1TreasureBox.get(3)==null);System.out.println("BOX_RELOAD_PASS 7 checks");}}
'''
run('reload',code)
assert 'case 55558 -> { 정상의가호2(pc, item); yield ItemActionResult.STOP; }' in (D/'ItemActionDispatcher.java').read_text()
# The old typed fixture lacks the newly called completion helper. Extend only its
# fixture, and leave the previous test archive untouched.
prev=P.parent/'item_only_phase115_20261118/test_115.py'
s=prev.read_text().replace("root = Path(os.environ.get('SOURCE_ROOT', '/mnt/data/work115'))",f'root = Path({str(R.parents[3])!r})')
# R == <root>/src/l1j/server/server
s=s.replace("for name in ('QuestProgressItems.java', 'TopRankerBlessingItems.java'):","""put('l1j/server/server/model/item/function/ItemActionCompletion.java', '''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
final class ItemActionCompletion {static int calls;static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance i){calls++;}}
''')
for name in ('QuestProgressItems.java', 'TopRankerBlessingItems.java'):""")
s=s.replace('ItemReceiptOperations.pending=null;\n      TopRankerBlessingItems.정상의가호2(p,p.inventory.current);','ItemReceiptOperations.pending=null;\n      int oldCalls=ItemActionCompletion.calls;\n      TopRankerBlessingItems.정상의가호2(p,p.inventory.current);',1)
s=s.replace('ok(p.skillMs==0&&p.lastBless==null,"blessing not applied before commit");','ok(p.skillMs==0&&p.lastBless==null&&ItemActionCompletion.calls==oldCalls,"blessing not applied before commit");')
s=s.replace('ok(p.saveCalls==1,"top ranker save attempted");','ok(p.saveCalls==1&&ItemActionCompletion.calls==oldCalls+1,"top ranker save and delay once after commit");')
s=s.replace('ItemReceiptOperations.finish(false);\n    ok(p.skillMs==0&&p.lastBless==null,"rollback no effect or cooldown");','int oldCalls=ItemActionCompletion.calls;\n    ItemReceiptOperations.finish(false);\n    ok(p.skillMs==0&&p.lastBless==null&&ItemActionCompletion.calls==oldCalls,"rollback no effect or cooldown");')
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp)/'phase115_extended.py';p.write_text(s)
    subprocess.run(['python',str(p)],check=True)
