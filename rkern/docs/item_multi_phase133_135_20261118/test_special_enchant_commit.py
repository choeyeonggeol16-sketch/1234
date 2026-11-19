#!/usr/bin/env python3
"""Java 21 typed compile + deferred-commit failure injection for the actual
production bodies of nine special scroll actions and two seal actions.
Only external collaborators are stubs; no MySQL/game world is available here.
"""
import os
import re
import subprocess
import tempfile
import textwrap
from pathlib import Path

ROOT = Path(os.environ.get('SOURCE_ROOT', Path(__file__).resolve().parents[2]))
PROD = ROOT / 'src/l1j/server/server/model/item/function'
PKG = 'l1j/server/server/model/item/function/'

def put(base, path, code):
    p = base / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(textwrap.dedent(code).strip() + '\n', 'utf-8')

def body(file, title):
    src = (PROD / file).read_text('utf-8')
    return src[src.index('final class ' + title):]

base_code = '''
package l1j.server.server.model.item.function;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.*;
import l1j.server.server.model.network.core.cs.packet.*;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.*;
import static l1j.server.server.model.item.function.ItemReceiptOperations.*;
'''
with tempfile.TemporaryDirectory(prefix='rkern133_enchant_') as temp:
    base = Path(temp) / 'src'; classes = Path(temp) / 'classes';classes.mkdir()
    for file in ['WeaponBlessingScrollEffects','AccessoryBlessingScrollEffects',
                 'ArmorBlessingScrollEffects','ItemOptionScrollEffects','WeaponEvolutionScrollEffects']:
        put(base, PKG+file+'.java', base_code+body(file+'.java',file))
    # SealRemovalScrolls has a short, real import set and is compiled unchanged.
    put(base, PKG+'SealRemovalScrolls.java', (PROD/'SealRemovalScrolls.java').read_text('utf-8'))
    put(base, PKG+'SpecialEnchantmentItems.java', (PROD/'SpecialEnchantmentItems.java').read_text('utf-8'))
    sp=(PROD/'SpecialEnchantScrolls.java').read_text('utf-8')
    funcs=[]
    for signature in ['public static boolean isOwnedScrollTarget(',
                      'public static boolean isAccessoryEnchantTarget(',
                      'public static int qualityBonus(']:
        start=sp.index(signature);cursor=sp.index('{',start);depth=1;end=cursor+1
        while depth:
            if sp[end]=='{':depth+=1
            elif sp[end]=='}':depth-=1
            end+=1
        funcs.append(sp[start:end])
    funcs='\n'.join(funcs)
    put(base, PKG+'SpecialEnchantScrolls.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class SpecialEnchantScrolls {
'''+funcs+'''
public static void 무기축복(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){WeaponBlessingScrollEffects.무기축복(p,a,b);}
public static void 무기축복100(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){WeaponBlessingScrollEffects.무기축복100(p,a,b);}
public static void 악세인챈(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){AccessoryBlessingScrollEffects.악세인챈(p,a,b);}
public static void 악세인챈100(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){AccessoryBlessingScrollEffects.악세인챈100(p,a,b);}
public static void 방어구축복(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){ArmorBlessingScrollEffects.방어구축복(p,a,b);}
public static void 방어구축복100(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){ArmorBlessingScrollEffects.방어구축복100(p,a,b);}
public static void 옵션부여(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){ItemOptionScrollEffects.옵션부여(p,a,b);}
public static void 품질부여주문서(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){ItemOptionScrollEffects.품질부여주문서(p,a,b);}
public static void 집행진화주문서(L1PcInstance p,L1ItemInstance a,L1ItemInstance b){WeaponEvolutionScrollEffects.집행진화주문서(p,a,b);}
}
''')
    put(base,'l1j/server/Config.java','''
package l1j.server;
public class Config {
 public static int qqq=1;
 public static int 축복1단=100, 축복2단=100, 축복3단=100, 축복4단=100, 축복5단=100;
 public static int 악세축복1단=100, 악세축복3단=100;
 public static int 방어구축복1단=100, 방어구축복2단=100, 방어구축복3단=100, 방어구축복5단=100, 방어구축복6단=100;
 public static int 랜덤부여주문서=100;
}
''')
    put(base,'l1j/server/server/storage/CacheD.java','''
package l1j.server.server.storage;
public class CacheD {public static String accountOrderKey(String label,int id){return label+id;}}
''')
    put(base,'l1j/server/server/model/Instance/L1ItemInstance.java','''
package l1j.server.server.model.Instance;
public class L1ItemInstance {
 public static class Template {
  public int type2,type,material;
  public boolean tradable=true,cantDelete=false;
  public int safeEnchant=0;
  public int getType2(){return type2;}public int getType(){return type;}
  public int getMaterial(){return material;}public boolean isTradable(){return tradable;}
  public boolean isCantDelete(){return cantDelete;}public int get_safeenchant(){return safeEnchant;}
 }
 public final int id,itemId;public Template tpl=new Template();
 public int count=1,bless=0,level=0,blessLevel=0,quality=0,attr=0;
 public boolean equipped=false,identified=true;
 public L1ItemInstance(int id,int itemId){this.id=id;this.itemId=itemId;}
 public int getId(){return id;}public int getItemId(){return itemId;}
 public Template getItem(){return tpl;}public int getCount(){return count;}
 public int getBless(){return bless;}public void setBless(int x){bless=x;}
 public int getEnchantLevel(){return level;}
 public int get_bless_level(){return blessLevel;}public void set_bless_level(int x){blessLevel=x;}
 public int get_bless_level2(){return quality;}public void set_bless_level2(int x){quality=x;}
 public void setAttrEnchantLevel(int x){attr=x;}
 public boolean isEquipped(){return equipped;}public String getLogName(){return "test-item";}
 public boolean isIdentified(){return identified;}
 public L1ItemInstance copy(){L1ItemInstance x=new L1ItemInstance(id,itemId);
 x.tpl=tpl;x.count=count;x.bless=bless;x.level=level;x.blessLevel=blessLevel;
 x.quality=quality;x.attr=attr;x.equipped=equipped;x.identified=identified;return x;}
 public void copyFrom(L1ItemInstance x){count=x.count;bless=x.bless;level=x.level;
 blessLevel=x.blessLevel;quality=x.quality;attr=x.attr;equipped=x.equipped;identified=x.identified;}
}
''')
    put(base,'l1j/server/server/model/Instance/L1PcInstance.java','''
package l1j.server.server.model.Instance;
import java.util.*;
public class L1PcInstance {
 public final Inventory inventory=new Inventory();public int notifications=0;public boolean failMessage=false;
 public static class Inventory {
  public final Map<Integer,L1ItemInstance> items=new HashMap<>();
  public L1ItemInstance getItem(int id){return items.get(id);}
 }
 public Inventory getInventory(){return inventory;}public int getId(){return 10001;}
 public void sendPackets(String message){notifications++;if(failMessage)throw new IllegalStateException("message IO failed");}
}
''')
    put(base,'l1j/server/server/model/L1InventoryTransaction.java','''
package l1j.server.server.model;
import l1j.server.server.model.Instance.*;
import java.util.*;import java.util.function.*;
public class L1InventoryTransaction implements AutoCloseable {
 public static L1InventoryTransaction last;public final L1PcInstance pc;
 public Runnable completed;public Runnable failed;public int debits;
 private final ArrayList<L1ItemInstance> spent =new ArrayList<>();
 private final Map<L1ItemInstance,L1ItemInstance> edited=new HashMap<>();
 public L1InventoryTransaction(L1PcInstance pc){this.pc=pc;last=this;}
 public static L1InventoryTransaction begin(L1PcInstance pc,int id){return new L1InventoryTransaction(pc);}
 public boolean debit(L1ItemInstance item,int quantity){
  if(item==null || item.count<quantity || pc.inventory.items.get(item.id)!=item)return false;
  debits+=quantity;spent.add(item);return true;
 }
 public void change(L1ItemInstance target,Consumer<L1ItemInstance> mut){
  L1ItemInstance copy=edited.computeIfAbsent(target,L1ItemInstance::copy);mut.accept(copy);
 }
 public void changeBless(L1ItemInstance target,int n){change(target,x->x.setBless(n));}
 public void requireTables(String... names){}public void onFailure(Runnable r){failed=r;}
 public void commit(Runnable run){completed=run;}
 public void commitAfterInventoryRelease(Runnable run){completed=run;}
 public void succeed(){for(var e:edited.entrySet())e.getKey().copyFrom(e.getValue());
  for(L1ItemInstance i:spent) {i.count--;if(i.count==0)pc.inventory.items.remove(i.id);}
  if(completed!=null)completed.run();}
 public void rollback(){if(failed!=null)failed.run();}
 public void close(){}
}
''')
    put(base,'l1j/server/server/model/network/core/cs/packet/User.java','''
package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.*;
public class User {public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance pc,String key){return new L1InventoryTransaction(pc);}}
''')
    put(base,'l1j/server/server/model/network/core/cs/packet/CDB.java','''
package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.*;
public class CDB {public static void RequestUseItem(L1PcInstance pc,L1InventoryTransaction tx,Runnable cb){tx.commit(cb);}}
''')
    put(base,'l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_ServerMessage {public static void send(L1PcInstance pc,int id){pc.notifications++;}}
''')
    put(base,'l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_SystemMessage {public static void send(L1PcInstance pc,String s){pc.notifications++;}}
''')
    put(base,'l1j/server/server/model/network/core/sc/packet/SC_SkillSound.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_SkillSound {public static int calls=0,failed=0;
 public static void send(L1PcInstance pc,int id,int effect){calls++;if(failed>0){failed--;throw new IllegalStateException("sound IO failed");}}}
''')
    put(base,'l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java','''
package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_PacketBox {public static int ITEM_STATUS=1,calls,fail;
 public static void send(L1PcInstance pc,int tag,L1ItemInstance item,int value){calls++;if(fail>0){fail--;throw new IllegalStateException("status IO failed");}}}
''')
    put(base,PKG+'CreatureItemActions.java','''
package l1j.server.server.model.item.function;
import java.util.logging.*;
public class CreatureItemActions {public static final Logger ITEM_USE2_LOG=Logger.getLogger("test item actions");}
''')
    put(base,PKG+'ItemActionCompletion.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class ItemActionCompletion {public static int calls;
 public static void finishCommittedConsumable(L1PcInstance pc,L1ItemInstance item){
 calls++;if(item.count>0&&!pc.inventory.items.containsKey(item.id))throw new AssertionError("wrong item");}}
''')
    put(base,PKG+'ItemReceiptOperations.java','''
package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.L1InventoryTransaction;
public class ItemReceiptOperations {
 public static void UseDurableTwoInput(L1PcInstance pc,L1ItemInstance scroll,L1ItemInstance target,int id,Runnable after){
  L1InventoryTransaction tx=new L1InventoryTransaction(pc);
  if(!tx.debit(scroll,1)||!tx.debit(target,1))return;
  tx.commit(after);
 }
}
''')
    put(base,PKG+'TestSpecialCommit.java','''
package l1j.server.server.model.item.function;
import java.util.logging.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.*;
public class TestSpecialCommit {
 static int checks;
 static void ok(boolean value,String msg){checks++;if(!value)throw new AssertionError(msg);}
 static L1PcInstance pc;static L1ItemInstance scroll,target;
 static void reset(int itemId,int type2,int type){
  pc=new L1PcInstance();scroll=new L1ItemInstance(501,itemId);target=new L1ItemInstance(502,5012);
  target.tpl.type2=type2;target.tpl.type=type;target.level=10;target.blessLevel=0;
  pc.inventory.items.put(scroll.id,scroll);pc.inventory.items.put(target.id,target);
  L1InventoryTransaction.last=null;ItemActionCompletion.calls=0;SC_SkillSound.calls=0;
  SC_SkillSound.failed=0;SC_PacketBox.calls=0;SC_PacketBox.fail=0;
 }
 static void run(int id,int type2,int type,boolean failNotice,boolean expectSound){
  reset(id,type2,type);pc.failMessage=failNotice;
  SpecialEnchantmentItems.use(pc,scroll,target,id);
  L1InventoryTransaction tx=L1InventoryTransaction.last;
  ok(tx!=null,"receipt created for id="+id);
  ok(tx.completed!=null,"callback deferred for id="+id);
  ok(ItemActionCompletion.calls==0,"completion never before commit for id="+id);
  ok(scroll.count==1,"scroll not consumed before commit id="+id);
  int beforeSound=SC_SkillSound.calls;
  tx.succeed();
  ok(scroll.count==0,"scroll consumed only by commit id="+id);
  ok(ItemActionCompletion.calls==1,"one use completion id="+id);
  if(expectSound)ok(SC_SkillSound.calls>beforeSound,"sound independently attempted id="+id);
 }
 static void rollback(int id,int type2,int type){
  reset(id,type2,type);SpecialEnchantmentItems.use(pc,scroll,target,id);
  L1InventoryTransaction tx=L1InventoryTransaction.last;
  ok(tx!=null,"receipt staged for rollback id="+id);
  tx.rollback();ok(scroll.count==1,"rolled-back scroll retained id="+id);
  ok(ItemActionCompletion.calls==0,"rolled-back completion omitted id="+id);
 }
 public static void main(String[] args){
  CreatureItemActions.ITEM_USE2_LOG.setLevel(Level.OFF);
  Logger.getLogger(SealRemovalScrolls.class.getName()).setLevel(Level.OFF);
  int[] ids={7010,7011,8010,8011,5922910,5922911,622910,633910};
  for(int id:ids){
    int type2=(id==7010||id==7011)?1:2;
    int type=id==8010||id==8011?9:0;
    run(id,type2,type,false,true);
    run(id,type2,type,true,true);
    rollback(id,type2,type);
  }
  reset(885544,1,0);target.level=10;target=new L1ItemInstance(502,61);
  target.tpl.type2=1;target.level=10;pc.inventory.items.put(target.id,target);
  SpecialEnchantmentItems.use(pc,scroll,target,885544);
  L1InventoryTransaction tx=L1InventoryTransaction.last;
  ok(tx!=null,"evolution successful staged");
  ok(ItemActionCompletion.calls==0,"evolution did not run tail early");
  tx.succeed();ok(ItemActionCompletion.calls==1,"evolution success committed tail");
  reset(885544,1,0);target.level=10;target=new L1ItemInstance(502,999);
  target.tpl.type2=1;target.level=10;pc.inventory.items.put(target.id,target);
  pc.failMessage=true;
  SpecialEnchantmentItems.use(pc,scroll,target,885544);
  tx=L1InventoryTransaction.last;ok(tx!=null,"unsupported evolution debits scroll");
  tx.succeed();ok(ItemActionCompletion.calls==1,"unsupported evolution committed tail");
  for(int sealId:new int[]{50020,50021}){
   reset(sealId,1,0);target.bless=sealId==50020?0:128;
   SealRemovalScrolls.use(pc,scroll,target,sealId);
   tx=L1InventoryTransaction.last;ok(tx!=null,"seal receipt staged");
   ok(ItemActionCompletion.calls==0,"seal tail deferred");
   int old=target.bless;SC_PacketBox.fail=1;
   tx.succeed();ok(ItemActionCompletion.calls==1,"seal committed tail");
   ok(target.bless==(sealId==50020?old+128:old-128),"seal bless committed");
   ok(SC_PacketBox.calls==1,"seal status attempted despite packet failure");
  }
  reset(50020,1,0);pc.inventory.items.remove(target.id);
  SealRemovalScrolls.use(pc,scroll,target,50020);
  ok(L1InventoryTransaction.last==null,"seal rejects foreign target");
  reset(7010,1,0);pc.inventory.items.remove(target.id);
  SpecialEnchantmentItems.use(pc,scroll,target,7010);
  ok(L1InventoryTransaction.last==null,"special enchant rejects foreign target");
  System.out.println("SPECIAL_ENCHANT_JAVA21_ASSERTIONS="+checks);
 }
}
''')
    files=list(base.rglob('*.java'))
    cmd=['javac','--release','21','-encoding','UTF-8','-d',str(classes),*map(str,files)]
    p=subprocess.run(cmd,text=True,capture_output=True)
    if p.returncode:
        print(p.stdout,p.stderr[-12000:]);raise SystemExit('Java21 type compile failed')
    p=subprocess.run(['java','-cp',str(classes),
                      'l1j.server.server.model.item.function.TestSpecialCommit'],
                     text=True,capture_output=True)
    print(p.stdout, end='')
    if p.returncode:
        print(p.stderr[-12000:]);raise SystemExit('Java21 commit test failed')
# Verify the precise dispatch return gate is still STOP, and both remain non-legacy.
s=(PROD/'ItemActionDispatcher.java').read_text('utf-8')
assert re.search(r'case 50020, 50021 -> \{.*?yield ItemActionResult.STOP;',s)
assert re.search(r'case 7010, 7011, 8010, 8011, 5922910, 5922911, 622910, 633910, 885544 -> \{.*?yield ItemActionResult.STOP;',s)
print('SPECIAL_SCROLL_DISPATCH_GATES=2')
