#!/usr/bin/env python3
"""Compile exact original and Phase17 production methods against a deterministic game model."""
from pathlib import Path
import subprocess,shutil,re,tempfile
WORKROOT=Path(__file__).resolve().parents[2]
BASELINES=WORKROOT/'docs/item_domain_phase17_20261118'
ROOT=WORKROOT/'src/l1j/server/server/model/item/function'
TEST=Path(tempfile.mkdtemp(prefix='rkern_phase17_java21_'))
if TEST.exists():shutil.rmtree(TEST)
PKG='l1j.server.server.model.item.function'
P=TEST/'src'/Path(*PKG.split('.'));P.mkdir(parents=True)
log=[]
def put(package,name,txt):
 dst=TEST/'src'/Path(*package.split('.'))/(name+'.java');dst.parent.mkdir(parents=True,exist_ok=True)
 dst.write_text('package '+package+';\n'+txt+'\n','utf8')
def prod(name): (P/(name+'.java')).write_bytes((ROOT/(name+'.java')).read_bytes())
for name in ['ResolventTransactions','DurableSkillItems']:
 (P/(name+'.java')).write_bytes((BASELINES/(name+'.baseline.java')).read_bytes())
for name in ['OrdinaryResolventItems','DragonResolventItems','ResolventPairRules','ResolventSettlement','DurableSkillReagentTransaction','DurablePassiveLearningTransaction','PotentialScrollSelection','PotentialScrollTransaction','PotentialSkillEffects']:
 prod(name)

put(PKG,'Trace','''
import java.util.*;
public class Trace {
 public static final StringBuilder b = new StringBuilder();
 public static void add(String s){ b.append(s).append(';'); }
 public static void reset(){ b.setLength(0); }
 public static String take(){ return b.toString(); }
}
''')
put('l1j.server.server.templates','L1EtcItem','''
public class L1EtcItem {
 public int get_delayEffect(){return 100;}
 public int getType2(){return 0;}
}
''')
put('l1j.server.server.model.Instance','L1ItemInstance','''
import l1j.server.server.templates.L1EtcItem;
public class L1ItemInstance {
 public int itemId, id=100, count=2, enchant, bless;
 public boolean equipped;
 public L1EtcItem item=new L1EtcItem();
 public L1ItemInstance(int itemId){this.itemId=itemId;}
 public int getItemId(){return itemId;}
 public int getId(){return id;}
 public int getCount(){return count;}
 public L1EtcItem getItem(){return item;}
 public int getEnchantLevel(){return enchant;}
 public boolean isEquipped(){return equipped;}
 public int getBless(){return bless;}
 public String getName(){return "T"+itemId;}
 public String getLogName(){return "L"+itemId;}
}
''')
# Dynamic skill identifiers and activation methods from actual original production source.
orig=(P/'DurableSkillItems.java').read_text('utf8')
skillnames=list(dict.fromkeys(re.findall(r'L1SkillId\.([\w가-힣]+)',orig)))
put('l1j.server.server.model.character.skill','L1SkillId','public class L1SkillId {\n'+''.join(f' public static final int {n}={i+500};\n' for i,n in enumerate(skillnames))+'}\n')
methods=list(dict.fromkeys(re.findall(r'pc\.([\w가-힣]+)\(true\)',orig)))
put('l1j.server.server.model.Instance','L1PcInstance','''
import l1j.server.server.model.item.function.Trace;
public class L1PcInstance {
 public int id=42;
 public int getId(){return id;}
 public Inventory inventory = new Inventory();
 public Inventory getInventory(){return inventory;}
 public boolean hasSkillEffect(int n){return false;}
 public void setSkillEffect(int skill,int dur){Trace.add("setSkill:"+skill+":"+dur);}
 public static class Inventory {
  public l1j.server.server.model.Instance.L1ItemInstance known;
  public l1j.server.server.model.Instance.L1ItemInstance getItem(int id){return known;}
 }
'''+''.join(f' public void {n}(boolean v){{Trace.add("effect:{n}:"+v);}}\n' for n in methods)+'}\n')
put('l1j.server.server.datatables','ResolventTable','''
public class ResolventTable {
 static final ResolventTable I = new ResolventTable();
 public static int value=3;
 public static ResolventTable getInstance(){return I;}
 public int getCrystalCount(int id){return value;}
}
''')
put('l1j.server.server.model','L1ItemDelay','''
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.function.Trace;
public class L1ItemDelay {public static void onItemUse(L1PcInstance pc,L1ItemInstance item){Trace.add("delay:"+item.getItemId());}}
''')
put('l1j.server.server.model','L1InventoryTransaction','''
import java.sql.*;
import java.util.function.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.function.Trace;
public class L1InventoryTransaction implements AutoCloseable {
 public static L1InventoryTransaction begin(L1PcInstance p,int id){Trace.add("begin:"+id);return new L1InventoryTransaction();}
 public boolean debit(L1ItemInstance item,int count){Trace.add("debitItem:"+item.getItemId()+":"+count);return true;}
 public boolean debit(int item,int count){Trace.add("debitID:"+item+":"+count);return true;}
 public L1ItemInstance grant(int item,int count){Trace.add("grant:"+item+":"+count);return new L1ItemInstance(item);}
 public void onFailure(Runnable cb){Trace.add("failureRegistered");}
 public void commitAfterInventoryRelease(Runnable cb){Trace.add("commit");cb.run();}
 public void change(L1ItemInstance item,Consumer<Image> cb){Trace.add("change:"+item.getItemId());cb.accept(new Image());}
 public static final class Image {public void setLastUsed(Timestamp date){Trace.add("lastUsed:"+date.getTime());}}
 public void requireTables(String... ts){Trace.add("tables:"+String.join(",",ts));}
 @FunctionalInterface public interface SqlAction {void run(Connection c) throws SQLException;}
 public void sql(SqlAction job){Trace.add("sqlRegistered");}
 public void close(){Trace.add("close");}
}
''')
put('l1j.server.server.storage','CacheD','public class CacheD { public static String accountOrderKey(String s,int id){return s+id;} }')
put('l1j.server.server.model.network.core.cs.packet','User','''
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.function.Trace;
public class User {public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance pc,String key){Trace.add("pending:"+key);return new L1InventoryTransaction();}}
''')
put('l1j.server.server.model.network.core.cs.packet','CDB','''
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.function.Trace;
public class CDB {public static void RequestUseItem(L1PcInstance pc,L1InventoryTransaction tx,Runnable done){Trace.add("requestCommit");done.run();}}
''')
put('l1j.server.server.model.network.core.sc.packet','SC_SystemMessage','''
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.function.Trace;
public class SC_SystemMessage {public static void send(L1PcInstance pc,String s){Trace.add("sys:"+s);}}
''')
put('l1j.server.server.model.network.core.sc.packet','SC_ServerMessage','''
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.function.Trace;
import java.util.*;
public class SC_ServerMessage {public static void send(L1PcInstance pc,int id,Object... p){Trace.add("server:"+id+":"+Arrays.toString(p));}}
''')
put('l1j.server.server.model.network.core.sc.packet','SC_SkillSound','''
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.function.Trace;
public class SC_SkillSound {public static void send(L1PcInstance pc,int id,int effect){Trace.add("snd:"+id+":"+effect);}public static void broadcast(L1PcInstance pc,int id,int effect){Trace.add("bcast:"+id+":"+effect);}}
''')
put('l1j.server.server.model.network.core.sc.packet','SC_OwnCharAttrDef','''
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.function.Trace;
public class SC_OwnCharAttrDef {public static void send(L1PcInstance pc){Trace.add("attr");}}
''')
put(PKG,'DollEffectItems','''
import l1j.server.server.model.Instance.*;
public class DollEffectItems {public static void RemoveDollPotential(L1PcInstance pc){Trace.add("clearPotential");}}
''')
put('l1j.server','Config','''
public class Config {
 public static int 부여주문서3단=100,부여주문서2단=400,부여주문서1단=800;
}
''')
put('l1j.server.server.utils','CommonUtil','''
public class CommonUtil {
 public static int[] q={0,0};public static int ix;
 public static void set(int a,int b){q[0]=a;q[1]=b;ix=0;}
 public static int random(int n){return q[(ix++)%2]%n;}
}
''')
put(PKG,'SpecialConsumableRouter','''
import java.sql.Timestamp;
public class SpecialConsumableRouter {public static Timestamp nowTimestamp(){return new Timestamp(1234567L);}}
''')
put(PKG,'RunTests',r'''
import l1j.server.server.model.Instance.*;
import l1j.server.server.datatables.*;
import l1j.server.server.utils.*;
public class RunTests {
 static int checked=0;
 static void eq(String name,String a,String b){checked++;if(!a.equals(b))throw new AssertionError(name+"\nORIG="+a+"\nNEW ="+b);}
 static L1ItemInstance target(int id,int count){L1ItemInstance v=new L1ItemInstance(id);v.count=count;return v;}
 static String runRoll(boolean orig,int roll,int base,int count,int itemId){
   Trace.reset();L1PcInstance pc=new L1PcInstance();L1ItemInstance a=target(itemId,3),b=target(41245,count);
   if(orig)ResolventTransactions.useResolventRoll(pc,a,b,base,roll);
   else OrdinaryResolventItems.useResolventRoll(pc,a,b,base,roll);
   return Trace.take();
 }
 static String runValid(boolean orig,int mode){
   Trace.reset();L1PcInstance pc=mode==0?null:new L1PcInstance();
   L1ItemInstance a=mode==1?null:target(41246,1),b=mode==2?null:target(41245,1);
   if(mode==3)b=a;
   if(mode==4)b=target(9,1);
   boolean ok=orig?ResolventTransactions.validResolventPair(pc,a,b,41245):ResolventPairRules.validResolventPair(pc,a,b,41245);
   return ok+"/"+Trace.take();
 }
 static String runDragon(boolean orig,int id,int crystal,int count){
   Trace.reset();ResolventTable.value=crystal;L1PcInstance pc=new L1PcInstance();
   L1ItemInstance a=target(id,1),b=target(442455,count);
   if(orig)ResolventTransactions.useDragonResolvent(pc,a,b);
   else DragonResolventItems.useDragonResolvent(pc,a,b);
   return Trace.take();
 }
 static String runPotion(boolean orig,int roll,int idx,boolean valid){
   Trace.reset();CommonUtil.set(roll,idx);L1PcInstance pc=new L1PcInstance();L1ItemInstance item=target(880800,1);
   pc.inventory.known=valid?item:null;
   if(orig) DurableSkillItems.usePotentialScroll(pc,item);
   else PotentialScrollSelection.usePotentialScroll(pc,item);
   return Trace.take();
 }
 static String runPassive(boolean orig,int skill,int replaced,boolean valid){
   Trace.reset();L1PcInstance pc=new L1PcInstance();L1ItemInstance item=target(999,1);
   pc.inventory.known=valid?item:null;
   if(orig) DurableSkillItems.useDurablePassive(pc,item,skill,replaced,()->Trace.add("after"));
   else DurablePassiveLearningTransaction.useDurablePassive(pc,item,skill,replaced,()->Trace.add("after"));
   return Trace.take();
 }
 static String runReagent(boolean orig,int material,int count){
   Trace.reset();L1PcInstance pc=new L1PcInstance();
   if(orig) DurableSkillItems.useDurableSkillReagent(pc,material,count,()->Trace.add("after"));
   else DurableSkillReagentTransaction.useDurableSkillReagent(pc,material,count,()->Trace.add("after"));
   return Trace.take();
 }
 public static void main(String[] args){
   int[] base={-1,0,1,2,3,17,100,32767,2147483647};
   for(int r=0;r<=102;r++)for(int b:base)for(int n:new int[]{1,2,7})for(int id:new int[]{1,344})
      eq("roll:"+r+":"+b+":"+n+":"+id,runRoll(true,r,b,n,id),runRoll(false,r,b,n,id));
   for(int k=0;k<=5;k++)eq("valid:"+k,runValid(true,k),runValid(false,k));
   for(int id:new int[]{4000137,4000138,4000140,4000144,4000145})for(int b:base)for(int n:new int[]{1,2})
      eq("dragon",runDragon(true,id,b,n),runDragon(false,id,b,n));
   for(int r=0;r<1000;r+=7)for(int i=0;i<33;i++)for(boolean valid:new boolean[]{true,false})
      eq("potential:"+r+":"+i,runPotion(true,r,i,valid),runPotion(false,r,i,valid));
   for(int skill:new int[]{0,11,1024})for(int replace:new int[]{0,3,8})for(boolean valid:new boolean[]{true,false})
      eq("passive",runPassive(true,skill,replace,valid),runPassive(false,skill,replace,valid));
   for(int id:new int[]{1,41246,40319})for(int count:new int[]{0,1,2,5})
      eq("material",runReagent(true,id,count),runReagent(false,id,count));
   System.out.println("Phase17 typed Java21, original vs extracted: " + checked + " exact traces PASS");
 }
}
''')
sources=list((TEST/'src').rglob('*.java'))
classes=TEST/'classes';classes.mkdir()
cmd=['javac','--enable-preview','--release','21','-encoding','UTF-8','-d',str(classes)]+[str(x) for x in sources]
r=subprocess.run(cmd,capture_output=True,text=True,env={'LANG':'C.UTF-8','LC_ALL':'C.UTF-8','PATH':'/usr/local/bin:/usr/bin:/bin'})
if r.returncode:
 print('JAVAC FAILED\n',r.stdout,r.stderr[:8000]);raise SystemExit(1)
r=subprocess.run(['java','-cp',str(classes),PKG+'.RunTests'],capture_output=True,text=True)
if r.returncode:
 print('RUNTIME FAILED\n',r.stdout,r.stderr[:8000]);raise SystemExit(1)
print(r.stdout.strip())
shutil.rmtree(TEST)
