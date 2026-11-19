"""Phase 116: compile the unmodified production identification handler and
an extracted production transaction.identify method against deterministic Java
21 receipt doubles. This is not a live-DB integration test.
"""
from pathlib import Path
import re, subprocess, shutil, textwrap, tempfile
import os
root=Path(os.environ.get('SOURCE_ROOT', '/mnt/data/work116'))
java=root/'src/l1j/server/server/model/item/function/IdentificationAndScrollItems.java'
txsrc=(root/'src/l1j/server/server/model/L1InventoryTransaction.java').read_text()
identify=re.search(r'    public void identify\(L1ItemInstance item\) \{.*?\n    \}',txsrc,re.S)
assert identify and 'setIdentified(true)' in identify.group(0)
d=root/'test116_java'
if d.exists(): shutil.rmtree(d)
d.mkdir()
def put(rel,code):
 f=d/rel;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(textwrap.dedent(code).strip()+'\n')
put('l1j/server/server/model/item/function/IdentificationAndScrollItems.java',java.read_text())
put('l1j/server/server/model/Instance/L1ItemInstance.java','''
 package l1j.server.server.model.Instance;
 public class L1ItemInstance {
  public final int id; public int count;public boolean identified,equipped;public int dirty;
  public final Template template;
  public L1ItemInstance(int id,int count,boolean identified,boolean equipped,Template t){this.id=id;this.count=count;this.identified=identified;this.equipped=equipped;template=t;}
  public int getId(){return id;}public int getCount(){return count;}
  public void setIdentified(boolean v){identified=v;}public boolean isIdentified(){return identified;}
  public int getRecordingColumns(){return dirty;}public Template getItem(){return template;}
  public static class Template {
   final int type,mpr,hpr,enchant;
   public Template(int type,int mpr,int hpr,int enchant){this.type=type;this.mpr=mpr;this.hpr=hpr;this.enchant=enchant;}
   public int getType2(){return type;} public int get_addmpr(){return mpr;}
   public int get_addhpr(){return hpr;}public int get_safeenchant(){return enchant;}
  }
 }
''')
put('l1j/server/server/model/L1PcInventory.java','''
 package l1j.server.server.model;
 import java.util.*;
 import l1j.server.server.model.Instance.L1ItemInstance;
 public class L1PcInventory {
  public static final int COL_SAVE_ALL=65535;
  public Map<Integer,L1ItemInstance> items=new HashMap<>(); public int saves;
  public boolean failSave;
  public L1ItemInstance getItem(int id){return items.get(id);}
  public void saveItem(L1ItemInstance item,int fields){saves++;if(failSave)throw new IllegalStateException("failed save");}
 }
''')
put('l1j/server/server/model/Instance/L1PcInstance.java','''
 package l1j.server.server.model.Instance;
 import l1j.server.server.model.L1PcInventory;
 public class L1PcInstance {
  final L1PcInventory inv=new L1PcInventory();
  public L1PcInventory getInventory(){return inv;}
  public int getId(){return 123;}
 }
''')
put('l1j/server/server/model/L1InventoryTransaction.java','''
 package l1j.server.server.model;
 import java.util.*;
 import l1j.server.server.model.Instance.*;
 public final class L1InventoryTransaction implements AutoCloseable {
  public static L1InventoryTransaction pending;public static int opened,submitted,committed,failed;
  public static boolean forceDebitFailure;
  public static final class Entry {public final L1ItemInstance original;public final L1ItemInstance image;
   Entry(L1ItemInstance old){original=old;image=new L1ItemInstance(old.id,old.count,old.identified,old.equipped,old.getItem());}
  }
  final L1PcInstance pc,primary;final Map<Integer,Entry> entries=new HashMap<>();
  public Runnable success,failureAction; public boolean closed,wasSubmitted;
  public L1InventoryTransaction(L1PcInstance p){pc=p;primary=p;opened++;pending=this;}
  private Entry entry(L1PcInstance owner,L1ItemInstance item){
   if(owner.getInventory().getItem(item.id)!=item)throw new IllegalStateException("identity");
   return entries.computeIfAbsent(item.id,k->new Entry(item));
  }
  public boolean debit(L1ItemInstance item,int count){
   if(forceDebitFailure)return false;
   Entry e=entry(pc,item);if(e.image.count<count)return false;e.image.count-=count;return true;
  }
  REPLACE_IDENTIFY
  public void onFailure(Runnable fail){failureAction=fail;}
  public void prepare(Runnable yes){success=yes;wasSubmitted=true;submitted++;}
  public void finish(boolean successDb){
   if(!successDb){failed++;if(failureAction!=null)failureAction.run();return;}
   for(Entry e:entries.values()){
    if(pc.getInventory().getItem(e.original.id)!=e.original){failed++;if(failureAction!=null)failureAction.run();return;}
   }
   for(Entry e:entries.values()){
    e.original.count=e.image.count;e.original.identified=e.image.identified;
    if(e.original.count==0)pc.getInventory().items.remove(e.original.id);
   }
   committed++;if(success!=null)success.run();
  }
  public void close(){closed=true;}
 }
'''.replace('REPLACE_IDENTIFY',identify.group(0)))
put('l1j/server/server/storage/CacheD.java','''
 package l1j.server.server.storage;
 public class CacheD {public static int accountOrderKey(String prefix,int id){return id+10;}}
''')
put('l1j/server/server/model/network/core/cs/packet/User.java','''
 package l1j.server.server.model.network.core.cs.packet;
 import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
 public class User { public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance p,int key){return new L1InventoryTransaction(p);} }
''')
put('l1j/server/server/model/network/core/cs/packet/CDB.java','''
 package l1j.server.server.model.network.core.cs.packet;
 import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
 public class CDB { public static void RequestUseItem(L1PcInstance p,L1InventoryTransaction tx,Runnable action){tx.prepare(action);} }
''')
put('l1j/server/server/model/network/core/sc/packet/SC_IdentifyDesc.java','''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.*;
 public class SC_IdentifyDesc {
  public static int sent;public static boolean fail;
  public static void send(L1PcInstance p,L1ItemInstance item){sent++;if(!item.identified)throw new AssertionError("identified result not applied");if(fail)throw new IllegalStateException("packet");}
 }
''')
put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.*;
 public class SC_SystemMessage {
  public static int sent;public static boolean fail;public static String last="";
  public static void send(L1PcInstance p,String s){sent++;last=s;if(fail)throw new IllegalStateException("packet");}
 }
''')
put('l1j/server/server/model/item/function/ItemActionResult.java','''
 package l1j.server.server.model.item.function;
 enum ItemActionResult { COMPLETE, STOP }
''')
put('l1j/server/server/model/item/function/ItemActionContext.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 class ItemActionContext {
  L1PcInstance pc; L1ItemInstance item,target;
  ItemActionContext(L1PcInstance p,L1ItemInstance i,L1ItemInstance t){pc=p;item=i;target=t;}
  L1PcInstance player(){return pc;}L1ItemInstance item(){return item;}
  L1ItemInstance targetItem(){return target;}int blankScrollSkillId(){return 17;}
 }
''')
put('l1j/server/server/model/item/function/BlankMagicScrolls.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 class BlankMagicScrolls {static void write(L1PcInstance pc,L1ItemInstance item,int id){}}
''')
put('l1j/server/server/model/item/function/Phase116Test.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.network.core.sc.packet.*;
 import java.util.logging.*;
 public class Phase116Test {
  static int checks=0;
  static void check(boolean v,String msg){checks++;if(!v)throw new AssertionError(msg);}
  static final L1ItemInstance.Template TEMPLATE=new L1ItemInstance.Template(2,3,5,6);
  static class Scenario {
   final L1PcInstance pc=new L1PcInstance();
   final L1ItemInstance scroll=new L1ItemInstance(1,1,true,false,TEMPLATE);
   final L1ItemInstance target;
   Scenario(boolean identified,boolean equipped){
    target=new L1ItemInstance(2,1,identified,equipped,TEMPLATE);
    pc.getInventory().items.put(1,scroll);pc.getInventory().items.put(2,target);
   }
   void use(){check(IdentificationAndScrollItems.handleIdentifyItem(new ItemActionContext(pc,scroll,target))==ItemActionResult.COMPLETE,"return");}
  }
  public static void main(String[] args){
   Logger.getLogger(IdentificationAndScrollItems.class.getName()).setLevel(Level.OFF);
   for(boolean equipped:new boolean[]{false,true})for(boolean identified:new boolean[]{false,true})
   for(boolean failDetail:new boolean[]{false,true})for(boolean failText:new boolean[]{false,true}){
    L1InventoryTransaction.pending=null;
    SC_IdentifyDesc.sent=SC_SystemMessage.sent=0;
    SC_IdentifyDesc.fail=failDetail;SC_SystemMessage.fail=failText;
    Scenario s=new Scenario(identified,equipped);s.use();
    check(!s.target.identified || identified,"changed before commit");
    check(s.scroll.count==1,"scroll consumed before commit");
    check(s.target.equipped==equipped,"equipped state before");
    check(L1InventoryTransaction.pending.wasSubmitted,"receipt not submitted");
    L1InventoryTransaction.pending.finish(true);
    check(s.scroll.count==0,"scroll not consumed after commit");
    check(s.target.identified,"not identified");
    check(s.target.equipped==equipped,"unequipped");
    check(SC_IdentifyDesc.sent==1,"identify packet count");
    check(SC_SystemMessage.sent==1,"description packet count");
   }
   SC_IdentifyDesc.fail=SC_SystemMessage.fail=false;
   Scenario rollback=new Scenario(false,true);rollback.use();L1InventoryTransaction.pending.finish(false);
   check(rollback.scroll.count==1,"rollback scroll");check(!rollback.target.identified,"rollback target");
   Scenario stale=new Scenario(false,false);stale.use();
   stale.pc.getInventory().items.put(2,new L1ItemInstance(2,1,false,false,TEMPLATE));
   L1InventoryTransaction.pending.finish(true);
   check(stale.scroll.count==1,"stale receipt consumed scroll");
   check(!stale.target.identified,"stale receipt identifies old target");
   Scenario missing=new Scenario(false,false);
   missing.pc.getInventory().items.remove(2); L1InventoryTransaction.pending=null;
   IdentificationAndScrollItems.handleIdentifyItem(new ItemActionContext(missing.pc,missing.scroll,missing.target));
   check(L1InventoryTransaction.pending==null,"foreign target submitted");
   Scenario nullTarget=new Scenario(false,false);L1InventoryTransaction.pending=null;
   IdentificationAndScrollItems.handleIdentifyItem(new ItemActionContext(nullTarget.pc,nullTarget.scroll,null));
   check(L1InventoryTransaction.pending==null,"null target submitted");
   Scenario dirty=new Scenario(false,false);dirty.scroll.dirty=1;dirty.target.dirty=1;dirty.use();
   check(dirty.pc.getInventory().saves==2,"dirty save count");
   Scenario failsave=new Scenario(false,false);failsave.pc.getInventory().failSave=true;failsave.target.dirty=1;
   L1InventoryTransaction.pending=null;failsave.use();
   check(L1InventoryTransaction.pending==null,"failed save reserved receipt");
   Scenario failDebit=new Scenario(false,false);L1InventoryTransaction.pending=null;
   L1InventoryTransaction.forceDebitFailure=true;failDebit.use();
   check(!L1InventoryTransaction.pending.wasSubmitted,"failed debit submitted receipt");
   L1InventoryTransaction.forceDebitFailure=false;
   System.out.println("Phase116 typed production handler + extracted production identify transaction PASS checks="+checks);
  }
 }
''')

classes=d/'classes';classes.mkdir()
files=[str(f) for f in d.rglob('*.java')]
r=subprocess.run(['javac','-encoding','UTF-8','--release','21','-d',str(classes),*files],text=True,capture_output=True)
if r.returncode:
 print(r.stdout,r.stderr);raise SystemExit(r.returncode)
r=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.Phase116Test'],text=True,capture_output=True)
print(r.stdout)
if r.returncode: print(r.stderr);raise SystemExit(r.returncode)
(root/'test116_results.log').write_text(r.stdout)
