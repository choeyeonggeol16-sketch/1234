#!/usr/bin/env python3
"""Java 21 production-method extraction; deterministic in-memory receipt double.
Not actual CacheD, database, or game server. Compare drop tables to 231 snapshot."""
from pathlib import Path
import tempfile, subprocess, re
P=Path(__file__).resolve().parent
S=(P.parents[1]/'src/l1j/server/server/Controller/FishingTimeController.java').read_text()
def method(sig):
    a=S.index(sig); b=S.index('{',a); n=1;i=b+1
    while n:
        n+=(S[i]=='{')-(S[i]=='}');i+=1
    return S[a:i]
def run(code):
    with tempfile.TemporaryDirectory() as t:
        p=Path(t)/'Test.java';p.write_text(code)
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',t,str(p)],check=True)
        subprocess.run(['java','-cp',t,'Test'],check=True)
constants=S[S.index('    private static final byte EXP_NONE'):S.index('    private static final class Holder')]
# Fixed baseline table definitions retained from 231 (not derived from modified code).
old=(P/'baseline_drop_tables.txt').read_text()
actual='\n'.join(re.findall(r'private static final int\[\] DROP_\w+ = \{.*?\};',S,re.S))
assert actual==old.rstrip('\n'), 'Fishing probability table changed'
run('''import java.util.concurrent.ThreadLocalRandom;
class Config {static boolean FISH_COMS;static int Fishchance1,Fishchance;}
public class Test {
'''+constants+method('private static int selectDrop(')+method('private static int selectSpecialDrop(')+method('private static int[] fishingRewardTable(')+method('private static int rollFishingDrop(')+'''
static void ck(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args){int n=0;for(int[] table:new int[][]{DROP_ANCIENT_SILVER,DROP_ANCIENT_GOLD,DROP_GROW,DROP_HIGH_GROW,DROP_FROG_OR_REEL_HIGH,DROP_REEL_SILVER,DROP_REEL_GOLD,DROP_NORMAL})for(int chance=1;chance<=10000;chance++){
int expected=0;for(int i=0;i<table.length;i+=3){if(chance<table[i]){expected=(table[i+1]<<4)|table[i+2];break;}}ck(selectDrop(table,chance)==expected);n++;}
int[][] expectedTables={DROP_GROW,DROP_FROG_OR_REEL_HIGH,DROP_REEL_SILVER,DROP_REEL_GOLD,DROP_FROG_OR_REEL_HIGH,DROP_ANCIENT_SILVER,DROP_ANCIENT_GOLD,DROP_NORMAL};for(int i=0;i<8;i++){ck(fishingRewardTable(i,EXP_GROWN_UP)==expectedTables[i]);n++;}ck(fishingRewardTable(0,EXP_HIGH_GROWN_UP)==DROP_HIGH_GROW);n++;
for(int kind=0;kind<2;kind++)for(int chance=1;chance<=1000;chance++)for(int limit:new int[]{0,1,2,500,1000,1001,2000}){ck(selectSpecialDrop(kind==0,chance,limit)==(chance<limit?(((kind==0?667799:779988)<<4)|DROP_SPECIAL):0));n++;}
Config.FISH_COMS=true;for(int i=0;i<1000;i++){Config.Fishchance1=1;Config.Fishchance=1;ck(rollFishingDrop(0,EXP_GROWN_UP)==0);ck(rollFishingDrop(0,EXP_HIGH_GROWN_UP)==0);Config.Fishchance1=1001;Config.Fishchance=1001;ck(rollFishingDrop(0,EXP_GROWN_UP)==((667799<<4)|DROP_SPECIAL));ck(rollFishingDrop(0,EXP_HIGH_GROWN_UP)==((779988<<4)|DROP_SPECIAL));n+=4;}
System.out.println("DROP_PASS "+n+" deterministic threshold/packing outcomes");}
}''')
methods='\n'.join(method(x) for x in ['private void prepareRodCycle(','private void finishRodCycle(','private static boolean sameCycle(','private static boolean isNoBaitRod('])
run('''import java.util.*;import java.util.logging.*;
class L1ItemInstance{int id,charge=2,count=1;boolean equipped;L1ItemInstance(int id){this.id=id;}int getChargeCount(){return charge;}int getItemId(){return id;}int getCount(){return count;}boolean isEquipped(){return equipped;} L1ItemInstance getItem(){return this;}String getName(){return "fish";}}
class Inventory{List<L1ItemInstance> items=new ArrayList<>();int slots;int getSize(){return slots;}List<L1ItemInstance> getItems(){return items;}}
class L1PcInstance{L1ItemInstance _fishingRod;Inventory inv=new Inventory();boolean fishing=true;long time=123;int grant,fish,stops,notices,exp,delay,finish;List<Integer> msgs=new ArrayList<>();boolean failPrepare,failGrant,failDebit;L1InventoryTransaction pending;int getId(){return 1;}boolean isFishing(){return fishing;}long getFishingTime(){return time;}Inventory getInventory(){return inv;}int getLevel(){return 1;}int getExp(){return exp;}}
class L1InventoryTransaction implements AutoCloseable{L1PcInstance p;int charge=-1,grant;L1ItemInstance bait;boolean consumed;Runnable fail,commit;boolean submitted;L1InventoryTransaction(L1PcInstance p){this.p=p;}
 boolean debit(L1ItemInstance r,int n){if(p.failDebit)return false;if(r.id==41295)bait=r;else consumed=true;return true;}L1ItemInstance grant(int id,int n){if(p.failGrant)return null;grant=id;return new L1ItemInstance(id);}void changeChargeCount(L1ItemInstance r,int v){charge=v;}void onFailure(Runnable r){fail=r;}public void close(){if(!submitted)p.pending=null;}void finish(boolean ok){p.pending=null;if(!ok){fail.run();return;}if(bait!=null)bait.count--;if(charge>=0)p._fishingRod.charge=charge;p.grant=grant;commit.run();}}
class User{static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance p,int k){if(p.failPrepare)throw new IllegalStateException();return p.pending=new L1InventoryTransaction(p);}}
class CDB{static void RequestUseItem(L1PcInstance p,L1InventoryTransaction tx,Runnable r){tx.submitted=true;tx.commit=r;}}
class CacheD{static int accountOrderKey(String s,int id){return 7;}}
class SC_SystemMessage{static void send(L1PcInstance p,String s){p.notices++;}}
class SC_ServerMessage{static void send(L1PcInstance p,int id){p.msgs.add(id);}}
class Config{static int LIMITLEVEL=100;}
class ExpTable{static int getExpByLevel(int i){return Integer.MAX_VALUE;}}
class CalcExp{static void AddExp(L1PcInstance p,int exp,int unused){p.exp+=exp;}}
public class Test{static Logger _log=Logger.getAnonymousLogger();static final int MODE_NORMAL=7,DROP_SPECIAL=10,DROP_FULL=-1,FISHING_MAX_SLOTS=164;static int roll;
'''+methods+'''
static int rollFishingDrop(int mode,byte e){return roll;}
void endFishing(L1PcInstance p){p.stops++;p.fishing=false;p.time=0;}
static void setNextFishing(L1PcInstance p,long now,int seconds){p.delay++;p.time=now+seconds*1000;}
void notifyFishingReward(L1PcInstance p,int drop,String name,byte e){p.finish++;p.fish=drop>>>4;if((drop&15)==DROP_SPECIAL)ck("fish".equals(name));}
static void ck(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args){_log.setLevel(Level.OFF);Test t=new Test();int cases=0;
int[] rods={41293,41294,41305,41306,9991,87058,87059,600229,4100293,4200293};int[] modes={7,1,2,3,4,5,6,0,0,0};
for(int ri=0;ri<rods.length;ri++)for(int mask=0;mask<1024;mask++){
 int rod=rods[ri],mode=modes[ri];L1PcInstance p=new L1PcInstance();p._fishingRod=new L1ItemInstance(rod);int before=(mask&1)!=0?0:2;p._fishingRod.charge=before;
 L1ItemInstance bait=new L1ItemInstance(41295);bait.count=(mask&2)!=0?0:((mask&256)!=0?1:2);int baitBefore=bait.count;p.inv.items.add(bait);p.inv.items.add(p._fishingRod);p.inv.slots=(mask&128)!=0?165:164;
 p.failPrepare=(mask&4)!=0;p.failDebit=(mask&8)!=0;p.failGrant=(mask&16)!=0;boolean sqlFail=(mask&32)!=0,stale=(mask&64)!=0,hit=(mask&512)!=0;roll=hit?((667799<<4)|DROP_SPECIAL):0;
 boolean needsBait=!isNoBaitRod(rod),exchange=mode!=7&&before==0,unlimited=rod==4200293&&!exchange;
 boolean full=hit&&!exchange&&p.inv.slots-(needsBait&&baitBefore==1?1:0)>164;
 boolean reward=hit&&!exchange&&!full;
 boolean fast=unlimited&&(!hit||p.inv.slots>164);
 boolean rejected=!fast&&(p.failPrepare||(needsBait&&(bait.count==0||p.failDebit))||(exchange&&p.failDebit)||((exchange||reward)&&p.failGrant));
 t.prepareRodCycle(p,p._fishingRod,41293,80,mode,19,(byte)0);
 if(fast){ck(p.pending==null&&p.stops==0&&p.delay==1&&p.exp==19&&p.grant==0&&p.finish==0&&p.msgs.equals(List.of(hit?263:1136)));cases++;continue;}
 ck(p._fishingRod.charge==before&&p.grant==0&&p.finish==0&&bait.count==baitBefore&&p.exp==0);ck((p.pending==null)==rejected);
 if(rejected){ck(p.stops==1);cases++;continue;}
 if(stale){p.fishing=false;p.time=0;}p.pending.finish(!sqlFail);
 ck(bait.count==baitBefore-(!sqlFail&&needsBait?1:0));ck(p.grant==(!sqlFail?(exchange?41293:reward?667799:0):0));
 ck(p.finish==(!sqlFail&&reward?1:0));ck(p.fish==(!sqlFail&&reward?667799:0));ck(p.exp==(!sqlFail&&!exchange&&mode==0?19:0));
 ck(p._fishingRod.charge==(!sqlFail&&!exchange&&!unlimited&&mode!=7?before-1:before));ck(p.stops==(!stale&&(sqlFail||exchange)?1:0));
 ck(p.delay==(!stale&&!sqlFail&&!exchange?1:0));if(stale)ck(p.time==0&&!p.fishing);
 if(!sqlFail&&!exchange){List<Integer> expected=new ArrayList<>();if(full)expected.add(263);else if(!hit)expected.add(1136);if(mode==7)expected.add(1147);ck(p.msgs.equals(expected));}else ck(p.msgs.isEmpty());cases++;
 }System.out.println("ATOMIC_CATCH_PASS "+cases+" staged debit/grant, capacity, stale/rollback cases");}
}''')
assert 'storeItem(' not in method('private void notifyFishingReward(')
assert 'consumeItem(' not in method('private long processPlayer(')
# Actual post-commit notice method under typed doubles; preserve success message/effect and exp dispatch.
run('''import java.util.*;
class L1PcInstance {int getId(){return 1;}List<String> log=new ArrayList<>();}
class SC_SystemMessage{static void send(L1PcInstance p,String s){p.log.add(s);}}
class SC_ServerMessage{static void send(L1PcInstance p,int id,String s){p.log.add(id+":"+s);}}
class SC_SkillSound{static void send(L1PcInstance p,int id,int effect){p.log.add("sound"+effect);}static void broadcast(L1PcInstance p,int id,int effect){p.log.add("broadcast"+effect);}}
class Config{static boolean 낚시이펙트;}
enum EFishingType{GROWN_UP,HIGH_GROWN_UP,ACIENT_SILVER,ACIENT_GOLD}
public class Test{
'''+constants+method('private void notifyFishingReward(')+'''
static L1PcInstance pc;void distribute_message(int owner,int item){pc.log.add("announce"+item);}
void calculate_exp1(L1PcInstance p,EFishingType t){p.log.add("exp1:"+t);}
void calculate_exp(L1PcInstance p,EFishingType t){p.log.add("exp:"+t);}
static void ck(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] a){Test t=new Test();int n=0;for(int msg=0;msg<=10;msg++)for(byte exp=0;exp<=4;exp++)for(int sound=0;sound<2;sound++){pc=new L1PcInstance();Config.낚시이펙트=sound!=0;int item=msg==10?667799:41296;t.notifyFishingReward(pc,(item<<4)|msg,"fish",exp);List<String> expected=new ArrayList<>();expected.add(msg==10?"낚시: fish를 획득하였습니다.":"1185:"+DROP_MESSAGES[msg]);if(sound!=0){expected.add("sound763");expected.add("broadcast763");}expected.add("announce"+item);switch(exp){case 1->expected.add("exp1:GROWN_UP");case 2->expected.add("exp1:HIGH_GROWN_UP");case 3->expected.add("exp:ACIENT_SILVER");case 4->expected.add("exp:ACIENT_GOLD");}ck(pc.log.equals(expected));n++;}System.out.println("NOTICE_PASS "+n+" message/effect/experience outcomes");}
}''')
