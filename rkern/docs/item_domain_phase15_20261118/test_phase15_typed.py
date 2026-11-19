#!/usr/bin/env python3
"""Java 21 typed differential tests against exact Phase14 production method bodies."""
from pathlib import Path
import subprocess,tempfile
R=Path(__file__).resolve().parents[2]; F=R/'src/l1j/server/server/model/item/function'; D=R/'docs/item_domain_phase15_20261118'
P='l1j/server/server/model/item/function/'; I='l1j/server/server/model/Instance/'; Q='l1j/server/server/model/network/core/sc/packet/'

def test_suite(name, classes, stubs, driver):
 with tempfile.TemporaryDirectory(prefix='rkern_p15_'+name+'_') as td:
  root=Path(td)
  content={**classes,**stubs,P+'Test15.java':driver}
  for x,data in content.items():
   p=root/x;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(data,'utf-8')
  cmd=['javac','--release','21','-encoding','UTF-8','-d',str(root)]+[str(root/x) for x in content]
  c=subprocess.run(cmd,capture_output=True,text=True)
  if c.returncode: print('COMPILE OUTPUT',c.stderr[:5000]);raise AssertionError(name+' compile')
  c=subprocess.run(['java','-cp',str(root),'l1j.server.server.model.item.function.Test15'],capture_output=True,text=True)
  if c.returncode: print('RUNTIME OUTPUT',c.stdout,c.stderr[:5000]);raise AssertionError(name+' runtime')
  print(c.stdout.strip())

# 6 levels × 256 independent class flags × inventory availability
classes={P+n+'.java':(F/(n+'.java')).read_text() for n in ['LevelMilestonePackages']+['Level'+str(l)+'AchievementReward' for l in (82,84,86,88,89,90)]}
original=(D/'LevelMilestonePackages.java.baseline').read_text().replace('LevelMilestonePackages','LevelMilestonePackagesBaseline')
classes[P+'LevelMilestonePackagesBaseline.java']=original
stubs={
 'l1j/server/server/model/L1InventoryTransaction.java':'package l1j.server.server.model; public class L1InventoryTransaction {}',
 I+'L1PcInstance.java':'''package l1j.server.server.model.Instance;
 public class L1PcInstance {
 public int mask,item; public final Inventory inv=new Inventory();
 public L1PcInstance(int flags,int item){mask=flags;this.item=item;}
 public Inventory getInventory(){return inv;}
 public boolean isWarrior(){return (mask&1)!=0;}public boolean isKnight(){return(mask&2)!=0;}
 public boolean isDragonknight(){return(mask&4)!=0;}public boolean isCrown(){return(mask&8)!=0;}
 public boolean isWizard(){return(mask&16)!=0;}public boolean isBlackwizard(){return(mask&32)!=0;}
 public boolean isElf(){return(mask&64)!=0;}public boolean isDarkelf(){return(mask&128)!=0;}
 public final class Inventory{public boolean checkItem(int id,int count){return item==id&&count==1;}}
 }''',
 P+'PackageRewardStaging.java':'''package l1j.server.server.model.item.function;
 import java.util.*;import l1j.server.server.model.L1InventoryTransaction;
 public class PackageRewardStaging{
 public static void stageReward(L1InventoryTransaction tx,ArrayList<String> m,int id,int count,int enchant,int bless,int attr,boolean identified){m.add(id+"/"+count+"/"+enchant+"/"+bless+"/"+attr+"/"+identified);}
 public static void stageSealedReward(L1InventoryTransaction tx,ArrayList<String> m,int id,int count,int enchant,int bless,int attr,boolean identified){stageReward(tx,m,id,count,enchant,bless,attr,identified);}
 }'''
}
driver='''package l1j.server.server.model.item.function;
import java.util.*;import l1j.server.server.model.L1InventoryTransaction;import l1j.server.server.model.Instance.L1PcInstance;
public class Test15 {
 public static void main(String[]args){int[] ids={714,715,716,717,721,722,30001,0};int tests=0;
 for(int id:ids)for(int mask=0;mask<256;mask++)for(int owns=0;owns<2;owns++){
  var pc=new L1PcInstance(mask,owns==1?id:9999);
  var a=new ArrayList<String>();var b=new ArrayList<String>();
  LevelMilestonePackagesBaseline.stage(pc,id,new L1InventoryTransaction(),a);
  LevelMilestonePackages.stage(pc,id,new L1InventoryTransaction(),b);
  if(!a.equals(b))throw new AssertionError("id="+id+" mask="+mask+" owns="+owns+" old="+a+" new="+b);
  tests++;
 }
 System.out.println("PASS Phase15 levels typed Java21: "+tests+" exact reward ID/amount/order traces");
 }
}'''
test_suite('milestones',classes,stubs,driver)

# 8 spellbook route classes, baseline original ClassSpellbookItems -- compare effects & return value.
sp={
 'wizardBooks':'WizardSpellbookConsumable','royalBooks':'RoyalSpellbookConsumable','elfBooks':'ElfClassSpellbookRoute',
 'darkElfBooks':'DarkElfSpellbookConsumable','knightBooks':'KnightSpellbookConsumable',
 'dragonKnightBooks':'DragonKnightSpellbookConsumable','blackWizardBooks':'IllusionistSpellbookConsumable',
 'warriorBooks':'WarriorSpellbookConsumable'}
classes={P+n+'.java':(F/(n+'.java')).read_text() for n in sp.values()}
classes[P+'ClassSpellbookItemsBaseline.java']=(D/'ClassSpellbookItems.java.baseline').read_text().replace('ClassSpellbookItems','ClassSpellbookItemsBaseline')
stubs={
 I+'L1PcInstance.java':'''package l1j.server.server.model.Instance;
 import java.util.*;public class L1PcInstance {public final List<String> log=new ArrayList<>();public int flags,level;
 public L1PcInstance(int f,int lvl){flags=f;level=lvl;}public int getLevel(){return level;}public boolean isGm(){return(flags&1)!=0;}
 public boolean isCrown(){return(flags&2)!=0;}public boolean isKnight(){return(flags&4)!=0;}public boolean isElf(){return(flags&8)!=0;}
 public boolean isWizard(){return(flags&16)!=0;}public boolean isDarkelf(){return(flags&32)!=0;}public boolean isDragonknight(){return(flags&64)!=0;}
 public boolean isBlackwizard(){return(flags&128)!=0;}public boolean isWarrior(){return(flags&256)!=0;}
 }''',
 I+'L1ItemInstance.java':'package l1j.server.server.model.Instance; public class L1ItemInstance {}',
 'l1j/server/server/model/network/core/session/CIOClient.java':'package l1j.server.server.model.network.core.session;public class CIOClient {}',
 P+'ItemActionResult.java':'package l1j.server.server.model.item.function;public enum ItemActionResult{COMPLETE,STOP,CONTINUE}',
 P+'ItemActionContext.java':'''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.session.CIOClient;
 public class ItemActionContext {
 final L1PcInstance p;final int id; final CIOClient c=new CIOClient(); final L1ItemInstance i=new L1ItemInstance();
 public ItemActionContext(L1PcInstance pc,int itemId){p=pc;id=itemId;}
 public L1PcInstance player(){return p;}public CIOClient client(){return c;}public L1ItemInstance item(){return i;}
 public int itemId(){return id;}public int itemObjectId(){return 1;}public int skillId(){return 1;}
 public int spellTargetId(){return 1;}public int spellX(){return 1;}public int spellY(){return 1;}
 }''',
 Q+'SC_ServerMessage.java':'''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;
 public class SC_ServerMessage {public static void send(L1PcInstance p,Object... ids){p.log.add("SC_ServerMessage"+java.util.Arrays.toString(ids));}}''',
 P+'SpellBookItems.java':'''package l1j.server.server.model.item.function;import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.session.CIOClient;
 public class SpellBookItems {
 public static void useSpellBook(L1PcInstance p,L1ItemInstance i,int id){p.log.add("wizard"+id);}
 public static void useElfSpellBook(L1PcInstance p,L1ItemInstance i,int id){p.log.add("elf"+id);}
 public static void SpellBook1(L1PcInstance p,L1ItemInstance i,CIOClient c){p.log.add("DarkElf");}
 public static void SpellBook3(L1PcInstance p,L1ItemInstance i,CIOClient c){p.log.add("Knight");}
 public static void SpellBook4(L1PcInstance p,L1ItemInstance i,CIOClient c){p.log.add("Crown");}
 public static void SpellBook5(L1PcInstance p,L1ItemInstance i,CIOClient c){p.log.add("DragonKnight");}
 public static void SpellBook6(L1PcInstance p,L1ItemInstance i,CIOClient c){p.log.add("BlackWizard");}
 }''',
 P+'WarriorSkillBookItems.java':'''package l1j.server.server.model.item.function;import l1j.server.server.model.Instance.*;
 public class WarriorSkillBookItems{public static void 전사스킬(L1PcInstance p,L1ItemInstance i,boolean fail){p.log.add("Warrior:"+fail);}}''',
}
# Additional existing unused imports must resolve, including original static imports.
for pth,cname in [('l1j/server/server/model/character/skill/L1SkillId.java','L1SkillId'),('l1j/server/server/model/character/skill/L1SkillUse.java','L1SkillUse'),('l1j/server/server/model/item/cooking/L1Cooking.java','L1Cooking'),('l1j/server/server/templates/L1EtcItem.java','L1EtcItem')]:stubs[pth]='package '+'.'.join(pth.split('/')[:-1])+';public class '+cname+' {}'
for cname in ['SC_Paralysis','SC_SkillSound']:stubs[Q+cname+'.java']='package l1j.server.server.model.network.core.sc.packet;public class '+cname+' {}'
stubs[P+'CreatureItemActions.java']='package l1j.server.server.model.item.function;public class CreatureItemActions{public static void cancelAbsoluteBarrier(Object p){}}'
stubs[P+'ItemReceiptOperations.java']='package l1j.server.server.model.item.function;public class ItemReceiptOperations{public static void UseDurableConsume(Object a,Object b,Object c){}}'
stubs[P+'FurniturePlacementItems.java']='package l1j.server.server.model.item.function;public class FurniturePlacementItems{public static void useFurnitureItem(Object a){}}'
methods='''
static ItemActionResult oldUse(int kind,ItemActionContext use){switch(kind){%s default:return ItemActionResult.STOP;}}
static ItemActionResult newUse(int kind,ItemActionContext use){switch(kind){%s default:return ItemActionResult.STOP;}}
'''%(''.join(f'case {i}:return ClassSpellbookItemsBaseline.{m}(use);' for i,m in enumerate(sp)),''.join(f'case {i}:return {cl}.{m}(use);' for i,(m,cl) in enumerate(sp.items())))
driver='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
public class Test15{
''' + methods + '''
public static void main(String[]args){
 int[] ids={40226,40227,40230,40232,5560,3000090,3000089,40265,40268,40270,40274,40277,5559,
 4100103,4100104,4100105,40164,40165,40166,4100101,41148,3000092,210020,210023,210031,210034,4100106,
 210000,210008,210009,210014,210018,5617,5618,3000096,210121,210123,210125,210129,210130,210131,
 3000094,40224,40244,4100295,3000091,999999,0};
 int tested=0;
 for(int kind=0;kind<8;kind++)for(int id:ids)for(int level:new int[]{0,1,5,45,85})for(int flag=0;flag<512;flag+=17){
  var a=new L1PcInstance(flag,level);var b=new L1PcInstance(flag,level);
  var old=oldUse(kind,new ItemActionContext(a,id));var now=newUse(kind,new ItemActionContext(b,id));
  if(old!=now||!a.log.equals(b.log))throw new AssertionError("kind="+kind+" id="+id+" level="+level+" flags="+flag+" old="+a.log+" new="+b.log);
  tested++;
 }
 System.out.println("PASS Phase15 spellbooks typed Java21: "+tested+" identical effects/messages/returns");
}
}'''
test_suite('spellbooks',classes,stubs,driver)

# 35 movement consumable IDs: execute original vs grouped classes with inventory effects and messages.
classes={P+n+'.java':(F/(n+'.java')).read_text() for n in ['MovementPotionConsumables','GreenMovementPotionUse','BraveryMovementPotionUse','FruitMovementPotionUse']}
classes[P+'MovementPotionConsumablesBaseline.java']=(D/'MovementPotionConsumables.java.baseline').read_text().replace('MovementPotionConsumables','MovementPotionConsumablesBaseline')
stubs={
 I+'L1ItemInstance.java':'package l1j.server.server.model.Instance; public class L1ItemInstance {}',
 I+'L1PcInstance.java':'''package l1j.server.server.model.Instance;
 import java.util.*; public class L1PcInstance{
 public final List<String> log = new ArrayList<>(); public int mask;
 public L1PcInstance(int m){mask=m;}
 public Inventory getInventory(){return new Inventory();}
 public class Inventory{public void removeItem(L1ItemInstance i,int n){log.add("remove="+n);}}
 public boolean isKnight(){return(mask&1)!=0;}public boolean isWarrior(){return(mask&2)!=0;}
 public boolean isCrown(){return(mask&4)!=0;}public boolean isElf(){return(mask&8)!=0;}
 public boolean isBlackwizard(){return(mask&16)!=0;}
 }''',
 Q+'SC_ServerMessage.java':'''package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public class SC_ServerMessage{public static void send(L1PcInstance p,Object... data){p.log.add("server"+java.util.Arrays.toString(data));}}''',
 P+'PotionEffects.java':'''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.L1PcInstance;
 public class PotionEffects{
 public static void useGreenPotion(L1PcInstance p,int id){p.log.add("green="+id);}
 public static void useBravePotion(L1PcInstance p,int id){p.log.add("brave="+id);}
 public static void useFruit(L1PcInstance p,int id){p.log.add("fruit="+id);}
 }''',
}
movementIds=[40030,40013,7006,40018,40039,40040,41338,41261,41262,41268,41269,41271,41272,41342,30067,140013,140018,30158,875640293,40014,140014,41415,30073,40068,140068,210110,30076,712,40031,30075,713,210036,30077,40733,1000000,0,40015]
driver='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;import java.util.*;
public class Test15 {
public static void main(String[]args){int[] ids={'''+','.join(map(str,movementIds))+'''};int cases=0;
for(int id:ids)for(int mask=0;mask<64;mask++)for(int rep=0;rep<9;rep++){
 var p=new L1PcInstance(mask);var p2=new L1PcInstance(mask);
 boolean old=MovementPotionConsumablesBaseline.use(p,new L1ItemInstance(),id);
 boolean now=MovementPotionConsumables.use(p2,new L1ItemInstance(),id);
 if(old!=now||!p.log.equals(p2.log))throw new AssertionError("id="+id+" mask="+mask+" old="+p.log+" new="+p2.log);
 cases++;
}
System.out.println("PASS Phase15 movement consumption typed Java21: "+cases+" exact effect/message/inventory traces");
}}
'''
test_suite('movement',classes,stubs,driver)

# Durable economic item exchanges: real production bodies vs original on transaction/order side effects.
classes={P+n+'.java':(F/(n+'.java')).read_text() for n in ['ClassPackageRewardReceipt','LegacyEquipmentExchangeReceipt','AdenaCurrencyConversionReceipt']}
classes[P+'EconomyRewardItemsBaseline.java']=(D/'EconomyRewardItems.java.baseline').read_text().replace('EconomyRewardItems','EconomyRewardItemsBaseline')
stubs={
 I+'L1ItemInstance.java':'''package l1j.server.server.model.Instance;
public class L1ItemInstance{public final int itemId,id;public int count,bless;
public L1ItemInstance(int id,int itemId,int count,int bless){this.id=id;this.itemId=itemId;this.count=count;this.bless=bless;}
public int getItemId(){return itemId;}public int getId(){return id;}public int getCount(){return count;}
public int getBless(){return bless;}public String getLogName(){return "item:"+itemId;}}''',
 I+'L1PcInstance.java':'''package l1j.server.server.model.Instance;
import java.util.*;public class L1PcInstance{
public final List<String> log=new ArrayList<>();public int type,dex=17,grantFailAt,callNo,refuseDebit;
public final Inventory inventory=new Inventory();public final Ability ability=new Ability();
public int getId(){return 55;}public int getType(){return type;}public Ability getAbility(){return ability;}
public void sendPackets(String s){log.add("sendPackets="+s);}public Inventory getInventory(){return inventory;}
public class Ability{public int getBaseDex(){return dex;}}
public class Inventory{
public L1ItemInstance main,adena;
public L1ItemInstance getItem(int id){if(main!=null&&main.getId()==id)return main;if(adena!=null&&adena.getId()==id)return adena;return null;}
public L1ItemInstance findItemId(int id){return adena!=null&&adena.getItemId()==id?adena:null;}
}
}''',
 'l1j/server/server/model/L1InventoryTransaction.java':'''package l1j.server.server.model;
import l1j.server.server.model.Instance.*;
public class L1InventoryTransaction implements AutoCloseable{
 final L1PcInstance pc; public L1InventoryTransaction(L1PcInstance p){pc=p;}
 public boolean debit(L1ItemInstance it,int c){pc.log.add("debit="+it.getItemId()+"/"+c);return pc.refuseDebit==0;}
 public L1ItemInstance grant(int id,int c){pc.log.add("grant="+id+"/"+c);pc.callNo++;return pc.callNo==pc.grantFailAt?null:new L1ItemInstance(500+pc.callNo,id,c,1);}
 public void onFailure(Runnable r){pc.log.add("onFailure");}
 @Override public void close(){pc.log.add("close");}
}''',
 'l1j/server/server/model/item/L1ItemId.java':'package l1j.server.server.model.item;public class L1ItemId{public static final int ADENA=40308;}',
 'l1j/server/server/storage/CacheD.java':'''package l1j.server.server.storage;public class CacheD{
 public static String accountOrderKey(String s,int id){return s+id;}
}''',
 'l1j/server/server/model/network/core/cs/packet/User.java':'''package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.Instance.*;import l1j.server.server.model.L1InventoryTransaction;
public class User{public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance p,String s){p.log.add("begin="+s);return new L1InventoryTransaction(p);}}''',
 'l1j/server/server/model/network/core/cs/packet/CDB.java':'''package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.Instance.*;import l1j.server.server.model.L1InventoryTransaction;
public class CDB{public static void RequestUseItem(L1PcInstance p,L1InventoryTransaction tx,Runnable after){p.log.add("RequestUseItem");if(after!=null)after.run();}}''',
}
for pclass in ('SC_SystemMessage','SC_PacketBox','SC_SkillSound','SC_ServerMessage'):
 stubs[Q+pclass+'.java']=f'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class {pclass} {{public static final int GREEN_MESSAGE=12;
 public static void send(L1PcInstance p,Object... args) {{p.log.add("{pclass}="+java.util.Arrays.toString(args));}}}}
'''
driver='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class Test15 {
static L1PcInstance create(int type,int dex,int id,int owns,int adenaCount,int bless,int refuse,int fail){
 var p=new L1PcInstance();p.type=type;p.dex=dex;p.refuseDebit=refuse;p.grantFailAt=fail;
 p.inventory.main=new L1ItemInstance(123,id,1,1);p.inventory.adena=new L1ItemInstance(999,40308,adenaCount,bless);
 if(owns==0)p.inventory.main=null;
 return p;
}
static void call(int kind,boolean old,L1PcInstance p,int id){
 L1ItemInstance main=new L1ItemInstance(123,id,1,1);
 if(kind==0){if(old)EconomyRewardItemsBaseline.useClassPackage(p,main);else ClassPackageRewardReceipt.useClassPackage(p,main);}
 else if(kind==1){if(old)EconomyRewardItemsBaseline.exchangeLegacyItem(p,main);else LegacyEquipmentExchangeReceipt.exchangeLegacyItem(p,main);}
 else {if(old)EconomyRewardItemsBaseline.convertAdena(p,main);else AdenaCurrencyConversionReceipt.convertAdena(p,main);}
}
public static void main(String[]args){
 int[][] ids={{77799,30071,999,0},{40280,40288,40289,830032,830041,830042,3000063,830031,0},{400253,875640445,400254,0}};
 int total=0;
 for(int kind=0;kind<3;kind++)for(int id:ids[kind])for(int type=0;type<9;type++)for(int dex:new int[]{17,18})
 for(int owns=0;owns<=1;owns++)for(int adena:new int[]{0,9999999,10000000,100000000})for(int bless:new int[]{0,128})for(int refuse=0;refuse<=1;refuse++)for(int fail:new int[]{0,1,4}){
  var a=create(type,dex,id,owns,adena,bless,refuse,fail);var b=create(type,dex,id,owns,adena,bless,refuse,fail);
  call(kind,true,a,id);call(kind,false,b,id);
  if(!a.log.equals(b.log))throw new AssertionError("kind="+kind+" id="+id+" type="+type+" old="+a.log+" new="+b.log);
  total++;
 }
 System.out.println("PASS Phase15 economic receipts typed Java21: "+total+" exact debit/grant/commit/failure traces");
}
}
'''
test_suite('economy',classes,stubs,driver)
