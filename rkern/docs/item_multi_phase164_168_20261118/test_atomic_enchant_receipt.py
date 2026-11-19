#!/usr/bin/env python3
"""Compile/exercise actual production atomic enchant receipt methods with typed Java21 stubs.
Covers admission, preimage, rollback, commit, callback order, stale selection.
"""
from pathlib import Path
import tempfile, subprocess,sys
root=Path(sys.argv[1]).resolve()
item_root=root/'src/l1j/server/server/model'
def method(text, token):
    start=text.index(token)
    brace=text.index('{',start)
    depth=0
    for i in range(brace,len(text)):
        if text[i]=='{': depth+=1
        if text[i]=='}':
            depth-=1
            if depth==0:return text[start:i+1]
    raise Exception('Unclosed method')
a=method((item_root/'item/function/ItemReceiptOperations.java').read_text(), '    public static void UseAtomicEnchantSuccess(')
b=method((item_root/'L1InventoryTransaction.java').read_text(), '    public void changeEnchantLevel(')
with tempfile.TemporaryDirectory() as tmp:
 r=Path(tmp)
 def add(path,contents):
  p=r/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(contents)
 add('l1j/server/server/model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance;
 public class L1ItemInstance {
 public int id,lv,count=1,dirty; public Item item=new Item(); public boolean equipped;
 public L1ItemInstance(int id,int lv){this.id=id;this.lv=lv;}
 public int getId(){return id;}public int getEnchantLevel(){return lv;}public int getCount(){return count;}
 public int getRecordingColumns(){return dirty;}public Item getItem(){return item;}public void setEnchantLevel(int lv){this.lv=lv;}
 public static class Item{public int type2=2;public int getType2(){return type2;}}
 }''')
 add('l1j/server/server/model/L1PcInventory.java','''package l1j.server.server.model;
 import l1j.server.server.model.Instance.*;
 public class L1PcInventory {public static final int COL_SAVE_ALL=255;
 public L1ItemInstance scroll,target; public int saves;public boolean failSave;
 public L1ItemInstance getItem(int id){return id==1?scroll:id==2?target:null;}
 public void saveItem(L1ItemInstance item,int flags){saves++;if(failSave)throw new IllegalStateException("save unavailable");item.dirty=0;}
 }''')
 add('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
 import l1j.server.server.model.L1PcInventory;
 public class L1PcInstance {public L1PcInventory inv=new L1PcInventory();public int getId(){return 42;}
 public L1PcInventory getInventory(){return inv;}}
 ''')
 add('l1j/server/server/model/network/core/session/CIOClient.java','''package l1j.server.server.model.network.core.session;public class CIOClient {}''')
 add('l1j/server/server/storage/CacheD.java','''package l1j.server.server.storage;public class CacheD {public static int accountOrderKey(String s,int id){return id;}}''')
 add('l1j/server/server/model/network/core/cs/packet/User.java','''package l1j.server.server.model.network.core.cs.packet;
 import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
 public class User {public static int admissions; public static boolean forceStale;
 public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance pc,int key){ admissions++;
 if(forceStale) pc.inv.target.lv++;
 return new L1InventoryTransaction(pc);}}
 ''')
 add('l1j/server/server/model/network/core/cs/packet/CDB.java','''package l1j.server.server.model.network.core.cs.packet;
 import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;
 public class CDB {public static boolean commit=true;public static int submissions,rollbacks;
 public static void RequestUseItem(L1PcInstance pc,L1InventoryTransaction tx,Runnable onCommit){submissions++;
 if(commit){tx.applyCommitted();onCommit.run();}else{rollbacks++;tx.fail();}}
 }''')
 add('l1j/server/server/model/L1InventoryTransaction.java','''package l1j.server.server.model;
 import l1j.server.server.model.Instance.*;
 public class L1InventoryTransaction implements AutoCloseable {
 final L1PcInstance primary; final Entry[] entries=new Entry[2]; public Runnable onFailure; public boolean debit;
 public L1InventoryTransaction(L1PcInstance p){primary=p;}
 static class Entry {L1ItemInstance image;Entry(L1ItemInstance source){
 image=new L1ItemInstance(source.id,source.lv); image.count=source.count;image.item.type2=source.item.type2;}}
 Entry entry(L1PcInstance p,L1ItemInstance i){if(primary.inv.getItem(i.id)!=i)throw new IllegalStateException("stale");
 int slot=i.id-1;if(entries[slot]==null)entries[slot]=new Entry(i);return entries[slot];}
 public boolean debit(L1ItemInstance i,int n){if(i.count<n)return false;Entry e=entry(primary,i);e.image.count-=n;debit=true;return true;}
 '''+b+'''
 public void onFailure(Runnable cb){onFailure=cb;}
 public void applyCommitted(){if(entries[0]!=null){primary.inv.scroll.count=entries[0].image.count;}
 if(entries[1]!=null){primary.inv.target.lv=entries[1].image.lv;}}
 public void fail(){if(onFailure!=null)onFailure.run();}
 public void close(){}
 }''')
 add('l1j/server/server/model/item/function/SpecialEnchantScrolls.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public class SpecialEnchantScrolls {public static boolean isOwnedScrollTarget(L1PcInstance p,L1ItemInstance s,L1ItemInstance t){
 return p!=null&&s!=null&&t!=null&&s!=t&&s.count>0&&t.count>0&&p.inv.getItem(s.id)==s&&p.inv.getItem(t.id)==t;}}
 ''')
 add('l1j/server/server/model/item/function/ItemTransactionActions.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*; public class ItemTransactionActions {
 public static int failureNotices;public static void failureNotice(L1PcInstance p,String s){failureNotices++;}}
 ''')
 add('l1j/server/server/model/item/function/ItemActionCompletion.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;public class ItemActionCompletion {
 public static int finishes;public static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance scroll){
 if(scroll.count!=0)throw new AssertionError("finish before durable debit");finishes++;}}
 ''')
 add('l1j/server/server/model/item/function/EnchantmentOutcomeEffects.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.session.*;
 public class EnchantmentOutcomeEffects {public static int effects;public static void SuccessEnchantCommitted(L1PcInstance p,L1ItemInstance target,CIOClient c,int delta){
 if(target.lv!=2)throw new AssertionError("effect before commit");effects++;}}
 ''')
 add('l1j/server/server/model/item/function/ItemReceiptOperations.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.L1PcInventory;
 import l1j.server.server.model.network.core.session.CIOClient;
 import l1j.server.server.model.network.core.cs.packet.User;
 import l1j.server.server.model.network.core.cs.packet.CDB;
 import l1j.server.server.storage.CacheD;
 public final class ItemReceiptOperations {public static java.util.logging.Logger ITEM_USE2_LOG=java.util.logging.Logger.getLogger("receipt-test");
 '''+a+'''
 }''')
 add('l1j/server/server/model/item/function/TestHarness.java','''package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.network.core.cs.packet.*;
 public class TestHarness {
 static int n;static void ok(boolean v,String s){n++;if(!v)throw new AssertionError(s+" #"+n);}
 static L1PcInstance pc(){L1PcInstance p=new L1PcInstance();p.inv.scroll=new L1ItemInstance(1,0);p.inv.target=new L1ItemInstance(2,1);return p;}
 public static void main(String[]args){
   for(boolean commit:new boolean[]{false,true})for(boolean dirty:new boolean[]{false,true}){
     L1PcInstance p=pc();p.inv.scroll.dirty=p.inv.target.dirty=dirty?3:0;
     CDB.commit=commit;int starts=EnchantmentOutcomeEffects.effects;
     ItemReceiptOperations.UseAtomicEnchantSuccess(p,p.inv.scroll,p.inv.target,null,()->{});
     ok(p.inv.scroll.count==(commit?0:1),"debit iff commit");
     ok(p.inv.target.lv==(commit?2:1),"level iff commit");
     ok(EnchantmentOutcomeEffects.effects==starts+(commit?1:0),"effects iff commit");
     ok(p.inv.saves==(dirty?2:0),"two dirty preflushes");
   }
   L1PcInstance stale=pc();stale.inv.scroll=new L1ItemInstance(1,0);
   int before=CDB.submissions;ItemReceiptOperations.UseAtomicEnchantSuccess(stale,new L1ItemInstance(1,0),stale.inv.target,null,()->{});
   ok(CDB.submissions==before,"stale scroll must reject");
   L1PcInstance mismatch=pc();User.forceStale=true;
   before=CDB.submissions;ItemReceiptOperations.UseAtomicEnchantSuccess(mismatch,mismatch.inv.scroll,mismatch.inv.target,null,()->{});
   ok(CDB.submissions==before,"stale level in admission must reject");
   ok(mismatch.inv.scroll.count==1,"no item deletion on invalid state"); User.forceStale=false;
   L1PcInstance unavailable=pc();unavailable.inv.failSave=true;unavailable.inv.target.dirty=2;
   before=User.admissions;ItemReceiptOperations.UseAtomicEnchantSuccess(unavailable,unavailable.inv.scroll,unavailable.inv.target,null,()->{});
   ok(User.admissions==before,"no admission when preflush refused");
   ok(unavailable.inv.scroll.count==1&&unavailable.inv.target.lv==1,"no mutation on preflush failure");
   System.out.println("ENCHANT_RECEIPT_TYPED_CASES="+n+" PASS");
 }
 }''')
 c=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(r/'classes'),*map(str,r.rglob('*.java'))],capture_output=True,text=True)
 if c.returncode:raise AssertionError(c.stderr)
 c=subprocess.run(['java','-cp',str(r/'classes'),'l1j.server.server.model.item.function.TestHarness'],capture_output=True,text=True)
 if c.returncode:raise AssertionError(c.stdout+c.stderr)
 print(c.stdout.strip())
