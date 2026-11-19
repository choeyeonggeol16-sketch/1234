"""Java 21 type-compilation of three unchanged-signature production handlers, and
fault-injection regression for item food, karma, holy waters. No live DB tested."""
from pathlib import Path
import subprocess, tempfile, shutil, textwrap, re, os

source_root=Path(os.environ['SOURCE_ROOT']) if os.environ.get('SOURCE_ROOT') else next(
 p for p in Path(__file__).resolve().parents
 if (p/'src/l1j/server/server/model/item/function/ItemActionDispatcher.java').is_file())
root=source_root/'src/l1j/server/server/model/item/function'
tmp=Path(tempfile.mkdtemp(prefix='rkern114_'))
src=tmp/'src'; cls=tmp/'classes';cls.mkdir(parents=True)
def put(p,s):
 f=src/p; f.parent.mkdir(parents=True,exist_ok=True); f.write_text(textwrap.dedent(s).strip()+'\n', encoding='utf-8')
for n in ('FoodAndPotionConsumables','BlessingConsumables','EventUtilityConsumables'):
 f=src/'l1j/server/server/model/item/function'/f'{n}.java';f.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(root/(n+'.java'),f)
put('l1j/server/Config.java','''package l1j.server; public final class Config { public static double RATE_KARMA=1.0; }''')
put('l1j/server/server/templates/L1Item.java','''
 package l1j.server.server.templates;
 public final class L1Item {public final int volume;public final String nameId;
 public L1Item(int v,String n){volume=v;nameId=n;}
 public int getFoodVolume(){return volume;} public String getNameId(){return nameId;}}
''')
put('l1j/server/server/model/Instance/L1ItemInstance.java','''
 package l1j.server.server.model.Instance;
 import l1j.server.server.templates.L1Item;
 public final class L1ItemInstance {
 public final int id;private final L1Item base;
 public L1ItemInstance(int id,int volume,String name){this.id=id;base=new L1Item(volume,name);}
 public L1Item getItem(){return base;}
 }
''')
put('l1j/server/server/model/Instance/L1PcInstance.java','''
 package l1j.server.server.model.Instance;
 import java.util.*;
 import l1j.server.server.model.L1Clan;
 public class L1PcInstance {
 public int hunger=0, karma=0, skillSet=-1, skillRemove=-1, floatingShows=0;
 public boolean blind=false, failFood=false, failSkill=false, failKarma=false, failVisibility=false;
 public int zone=0; public final Map<Integer,Integer> effects=new HashMap<>();
 public final Inventory inv=new Inventory();
 public static final class Inventory { public boolean consumeItem(int id,int qty){return true;} }
 public Inventory getInventory(){return inv;}
 public int getId(){return 14;} public String getName(){return "tester";}
 public int get_food(){return hunger;}
 public void set_food(int i){if(failFood)throw new IllegalStateException("hunger state failed");hunger=i;}
 public void add_food(int i){if(failFood)throw new IllegalStateException("food increment failed"); hunger=Math.min(225,Math.max(1,hunger+i));}
 public boolean isBlind(){return blind;}
 public void showFloatingEyeToInvis(){if(failVisibility)throw new IllegalStateException("visibility failed");floatingShows++;}
 public boolean hasSkillEffect(int id){return effects.containsKey(id);}
 public void setSkillEffect(int id,int ms){if(failSkill)throw new IllegalStateException("skill update failed"); effects.put(id,ms);skillSet=id;}
 public void removeSkillEffect(int id){if(failSkill)throw new IllegalStateException("skill removal failed");effects.remove(id);skillRemove=id;}
 public int getKarma(){return karma;} public void addKarma(int change){if(failKarma)throw new IllegalStateException("karma update failed");karma+=change;}
 public int getMapId(){return 1010;} public int getZoneType(){return zone;}
 public L1Clan getClan(){return null;}
 public void sendPackets(String msg){}
 }
''')
put('l1j/server/server/model/character/skill/L1SkillId.java','''
 package l1j.server.server.model.character.skill;
 public final class L1SkillId {
 public static final int STATUS_FLOATING_EYE=100,STATUS_HOLY_MITHRIL_POWDER=101,
 STATUS_HOLY_WATER=102,STATUS_HOLY_WATER_OF_EVA=103;
 }
''')
for name,package in [('L1SkillUse','l1j/server/server/model/character/skill'),('CS_Attr','l1j/server/server/model/network/core/cs/packet'),('ClanTable','l1j/server/server/datatables'),('SC_AuctionSystemBoard','l1j/server/server/model/network/core/sc/packet'),('SC_Board','l1j/server/server/model/network/core/sc/packet'),('SC_MessageYN','l1j/server/server/model/network/core/sc/packet')]:
 put(f'{package}/{name}.java',f'package {package.replace("/", ".")}; public class {name} {{}}')
put('l1j/server/server/model/L1Clan.java','''package l1j.server.server.model;public class L1Clan{public void addBlessCount(int n){}}''')
put('l1j/server/server/model/Instance/L1NpcInstance.java','''package l1j.server.server.model.Instance;public class L1NpcInstance{}''')
put('l1j/server/server/model/L1World.java','''
 package l1j.server.server.model;
 import l1j.server.server.model.Instance.L1NpcInstance;
 public final class L1World{public static L1World getInstance(){return new L1World();}public L1NpcInstance findNpcByNpcId(int id){return null;}}
''')
put('l1j/server/server/model/poison/L1DamagePoison.java','''
 package l1j.server.server.model.poison;
 import l1j.server.server.model.Instance.L1PcInstance;
 public class L1DamagePoison {public static void doInfection(L1PcInstance a,L1PcInstance b,int p,int i,boolean v,int d){}}
''')
put('l1j/server/server/model/item/function/ItemActionResult.java','''package l1j.server.server.model.item.function;public enum ItemActionResult{STOP,COMPLETE}''')
put('l1j/server/server/model/item/function/ItemActionContext.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class ItemActionContext {private final L1PcInstance pc;private final L1ItemInstance item;private final int id;
 public ItemActionContext(L1PcInstance p,L1ItemInstance i,int n){pc=p;item=i;id=n;}
 public L1PcInstance player(){return pc;}public L1ItemInstance item(){return item;}public int itemId(){return id;}}
''')
put('l1j/server/server/model/item/function/ItemReceiptOperations.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class ItemReceiptOperations {
 public static Runnable callback;public static int submitted, committed, rejected;
 public static void UseDurableConsume(L1PcInstance p,L1ItemInstance i,Runnable done){
 if(callback!=null)throw new AssertionError("overlapping receipt");submitted++;callback=done;}
 public static void finish(boolean ok){Runnable run=callback;callback=null;if(run==null)throw new AssertionError("missing callback");
 if(ok){committed++;run.run();}else{rejected++;}}
 public static void reset(){callback=null;submitted=committed=rejected=0;}
 }
''')
put('l1j/server/server/model/item/function/PcRoomPassTransactions.java','''package l1j.server.server.model.item.function;public class PcRoomPassTransactions{public static final int 피씨방코인=9;}''')
put('l1j/server/server/model/item/function/CombatAbilityItems.java','''package l1j.server.server.model.item.function;public class CombatAbilityItems{public static final int 블레이즈스턴=8;}''')
put('l1j/server/server/model/item/function/PotionEffects.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class PotionEffects {public static void useCashScroll(L1PcInstance p,int id){} public static boolean usePotionCase(L1PcInstance p,L1ItemInstance i,int id){return false;}}
''')
put('l1j/server/server/model/item/function/SpecialConsumableRouter.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public class SpecialConsumableRouter {public enum Result{RETURN,COMPLETE}public static Result use(L1PcInstance p,L1ItemInstance i){return Result.COMPLETE;}}
''')
put('l1j/server/server/model/item/function/AinBlessingGemConsumables.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public final class AinBlessingGemConsumables {public static boolean useGemCase(L1PcInstance p,L1ItemInstance i,int id){return false;}}
''')
base='l1j/server/server/model/network/core/sc/packet/'
put(base+'Fault.java','''
 package l1j.server.server.model.network.core.sc.packet;
 public final class Fault {
 public static int failed=0;public static final int[] calls=new int[12];
 public static void reset(){java.util.Arrays.fill(calls,0);failed=0;}
 public static void call(int bit){calls[bit]++;if((failed&(1<<bit))!=0)throw new IllegalStateException("packet failed bit="+bit);}
 }
''')
packet_defs={
'SC_CurseBlind':'public static void send(L1PcInstance p,int v){if(v!=2)throw new AssertionError("blind code changed");Fault.call(0);}',
'SC_PacketBox':'public static final int FOOD=1,GREEN_MESSAGE=2;public static void send(L1PcInstance p,int kind,int value){if(kind!=FOOD)throw new AssertionError("food kind changed");Fault.call(1);} public static void send(L1PcInstance p,int kind,String msg){Fault.call(9);}',
'SC_ServerMessage':'public static void send(L1PcInstance p,int code,String name){if(code!=76)throw new AssertionError("food msg changed");Fault.call(2);} public static void send(L1PcInstance p,int code){if(code==1140||code==1141)Fault.call(6); else Fault.call(9);}',
'SC_Karma':'public static void send(L1PcInstance p){Fault.call(3);}',
'SC_SystemMessage':'public static void send(L1PcInstance p,String name){Fault.call(4);}',
'SC_SkillSound':'public static void send(L1PcInstance p,int object,int effect){if(effect!=190 && effect!=21178 && effect!=21129 && (effect<3204||effect>3209))throw new AssertionError("sound changed");Fault.call(5);} public static void broadcast(L1PcInstance p,int object,int effect){Fault.call(7);}',
'SC_ChatPacket':'public static void sendDirect(L1PcInstance p,L1PcInstance s,String msg){Fault.call(9);}',
'SC_NPCTalkReturn':'public static void send(L1PcInstance p,int id,String name){Fault.call(9);}',
'SC_Boarditem':'public static void sendListAsync(L1PcInstance p,l1j.server.server.model.Instance.L1NpcInstance n,int page){Fault.call(9);}',
}
for name,body in packet_defs.items():
 put(base+name+'.java',f'''package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public final class {name} {{ {body} }}''')
# generate all ids for food from production's dispatcher
text=(source_root/'src/l1j/server/server/model/item/function/ItemActionDispatcher.java').read_text()
m=re.search(r'case 40056,.*?-> FoodAndPotionConsumables.handleFood\(use\)',text,re.S); assert m
ids=[int(a) for a in re.findall(r'\d+',m.group().split('->')[0])]
assert len(ids)==36 and len(set(ids))==36,ids
put('l1j/server/server/model/item/function/Check114.java',r'''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.character.skill.L1SkillId;
 import l1j.server.server.model.network.core.sc.packet.Fault;
 import java.util.logging.*;
 public final class Check114 {
 static int asserts=0;
 static void check(boolean b,String s){asserts++;if(!b)throw new AssertionError(s);}
 static L1ItemInstance item(int id){return new L1ItemInstance(id,0,"test-name");}
 static void reset(){ItemReceiptOperations.reset();Fault.reset();}
 static void food(int id,int mask,boolean commit,int fullness,boolean blind){
   reset();Fault.failed=mask;L1PcInstance p=new L1PcInstance();p.hunger=fullness;p.blind=blind;
   ItemActionResult result=FoodAndPotionConsumables.handleFood(new ItemActionContext(p,item(id),id));
   check(result==ItemActionResult.COMPLETE,"food result");check(ItemReceiptOperations.submitted==1,"food must use durable receipt");
   check(p.hunger==fullness,"early food change before commit");check(p.effects.isEmpty(),"early food skill");
   ItemReceiptOperations.finish(commit);
   int expected=fullness>=225?fullness:Math.min(225,fullness+10+(id==210039||id==30085?90:0)+5);
   check(p.hunger==(commit?expected:fullness),"committed food amount");
   check(p.effects.containsKey(L1SkillId.STATUS_FLOATING_EYE)==(commit&&id==40057),"floating eye skill");
   check(p.floatingShows==(commit&&id==40057&&blind?1:0),"floating detection");
   check(Fault.calls[0]==(commit&&id==40057&&blind?1:0),"blind notice");
   check(Fault.calls[1]==(commit&&fullness<225?1:0),"hunger notice");
   check(Fault.calls[2]==(commit&&fullness<225?1:0),"food item notice independence");
   check(ItemReceiptOperations.committed==(commit?1:0),"receipt commit");
 }
 static void karma(int mask,boolean commit,int before){
  reset();Fault.failed=mask;L1PcInstance p=new L1PcInstance();p.karma=before;
  FoodAndPotionConsumables.handleKarmaPotion(new ItemActionContext(p,item(500218),500218));
  if(before < -10000000){check(ItemReceiptOperations.submitted==0,"karma should not consume below threshold");return;}
  check(ItemReceiptOperations.submitted==1,"karma receipt");check(p.karma==before,"karma early update");
  ItemReceiptOperations.finish(commit);
  check(p.karma==(commit?before-15000:before),"karma amount");
  check(Fault.calls[3]==(commit?1:0),"karma packet independent");
  check(Fault.calls[4]==(commit?1:0),"karma system message independent");
 }
 static void holy(int mask,boolean commit,boolean forbidden){
  reset();Fault.failed=mask;L1PcInstance p=new L1PcInstance();
  p.effects.put(L1SkillId.STATUS_HOLY_MITHRIL_POWDER,1);
  if(!forbidden)p.effects.clear();
  BlessingConsumables.handleHolyWaterOfEva(new ItemActionContext(p,item(41315),41315));
  check(ItemReceiptOperations.submitted==1,"holy water receipt");
  check(!p.effects.containsKey(L1SkillId.STATUS_HOLY_WATER),"early buff");
  ItemReceiptOperations.finish(commit);
  check(p.effects.containsKey(L1SkillId.STATUS_HOLY_WATER)==commit,"holy effect");
  check(p.effects.containsKey(L1SkillId.STATUS_HOLY_MITHRIL_POWDER)==(!commit&&forbidden),"holy removal only on commit");
  check(Fault.calls[5]==(commit?1:0),"holy self sound");
  check(Fault.calls[7]==(commit?1:0),"holy broadcast sound");
  check(Fault.calls[6]==(commit?1:0),"holy message");
 }
 static void eva(int mask,boolean commit){
  reset();Fault.failed=mask;L1PcInstance p=new L1PcInstance();
  EventUtilityConsumables.use(p,item(41354),41354,null);
  check(ItemReceiptOperations.submitted==1,"eva receipt");check(p.effects.isEmpty(),"early eva effect");
  ItemReceiptOperations.finish(commit);
  check(p.effects.containsKey(L1SkillId.STATUS_HOLY_WATER_OF_EVA)==commit,"eva buff");
  check(Fault.calls[5]==(commit?1:0),"eva self sound");
  check(Fault.calls[7]==(commit?1:0),"eva broadcast");
  check(Fault.calls[6]==(commit?1:0),"eva message");
 }
 static void hardFailures(){
   reset();L1PcInstance pc=new L1PcInstance();pc.failFood=true;
   FoodAndPotionConsumables.handleFood(new ItemActionContext(pc,item(40056),40056));
   boolean threw=false;try{ItemReceiptOperations.finish(true);}catch(IllegalStateException e){threw=true;}
   check(threw,"food gameplay failure must propagate");check(Fault.calls[1]==0,"food UI before gameplay error");
   reset();pc=new L1PcInstance();pc.blind=true;pc.failVisibility=true;
   FoodAndPotionConsumables.handleFood(new ItemActionContext(pc,item(40057),40057));
   ItemReceiptOperations.finish(true);
   check(pc.hunger==15 && pc.effects.containsKey(L1SkillId.STATUS_FLOATING_EYE),"AOI presentation failure must not skip food/buff");
   check(Fault.calls[0]==1 && Fault.calls[1]==1 && Fault.calls[2]==1,"AOI failure must not skip other notices");
   reset();pc=new L1PcInstance();pc.failKarma=true;
   FoodAndPotionConsumables.handleKarmaPotion(new ItemActionContext(pc,item(500218),500218));
   threw=false;try{ItemReceiptOperations.finish(true);}catch(IllegalStateException e){threw=true;}
   check(threw,"karma failure must propagate");check(Fault.calls[3]==0,"karma UI before gameplay error");
   reset();pc=new L1PcInstance();pc.failSkill=true;
   BlessingConsumables.handleHolyWaterOfEva(new ItemActionContext(pc,item(41315),41315));
   threw=false;try{ItemReceiptOperations.finish(true);}catch(IllegalStateException e){threw=true;}
   check(threw,"holy gameplay failure must propagate");check(Fault.calls[5]==0,"holy sound before gameplay error");
   reset();pc=new L1PcInstance();pc.failSkill=true;
   EventUtilityConsumables.use(pc,item(41354),41354,null);
   threw=false;try{ItemReceiptOperations.finish(true);}catch(IllegalStateException e){threw=true;}
   check(threw,"eva gameplay failure must propagate");check(Fault.calls[5]==0,"eva sound before gameplay error");
 }
 public static void main(String[] args){
  for(String c:new String[]{"FoodAndPotionConsumables","BlessingConsumables","EventUtilityConsumables"})
   Logger.getLogger("l1j.server.server.model.item.function."+c).setLevel(Level.OFF);
  int[] ids=IDS_GO_HERE;
  for(int id:ids)for(int mask=0;mask<256;mask++){
   for(boolean commit:new boolean[]{true,false}){
    food(id,mask,commit,0,false);
    if(id==40057)food(id,mask,commit,0,true);
   }
  }
  for(int mask=0;mask<256;mask++){
   for(boolean commit:new boolean[]{true,false}){
    food(210039,mask,commit,225,false);
    food(40056,mask,commit,220,false);
    karma(mask,commit,0);
    holy(mask,commit,false);
    holy(mask,commit,true);
    eva(mask,commit);
   }
  }
  reset();FoodAndPotionConsumables.handleKarmaPotion(new ItemActionContext(new L1PcInstance(){ {karma=-10000001;} },item(500218),500218));
  check(ItemReceiptOperations.submitted==0,"karma precondition");
  hardFailures();
  System.out.println("PHASE114 JAVA 21 PASS: "+asserts+" assertions; all 36 food IDs; 8-bit packet-failure matrix, commit/rollback, holy waters, karma, hard-state errors");
 }
 }
'''.replace('IDS_GO_HERE','new int[]{'+','.join(map(str,ids))+'}'))
files=[str(x) for x in src.rglob('*.java')]
cmd=['javac','-encoding','UTF-8','--release','21','-d',str(cls)]+files
p=subprocess.run(cmd,text=True,capture_output=True)
if p.returncode:
 print(p.stderr[-12000:]);raise SystemExit(p.returncode)
p=subprocess.run(['java','-cp',str(cls),'l1j.server.server.model.item.function.Check114'],text=True,capture_output=True)
print(p.stdout,end='')
if p.returncode:
 print(p.stderr[-12000:]);raise SystemExit(p.returncode)
print('Actual 3 production Java classes type compiled with typed stubs. Folder:',tmp)
