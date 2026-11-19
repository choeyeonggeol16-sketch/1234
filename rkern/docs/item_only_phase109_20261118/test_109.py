"""Phase 109: run real Java 21 item class with adversarial gate-acquisition hooks.
No live MySQL/CacheD; hooks exist only inside test doubles.
"""
from pathlib import Path
import importlib.util, subprocess, shutil
ROOT=Path(__file__).resolve().parents[2]
DOC=Path(__file__).resolve().parent

def run(*argv,timeout=150):
    p=subprocess.run(argv,capture_output=True,text=True,timeout=timeout,cwd=ROOT)
    if p.returncode:
        print('FAILED',argv, p.stdout[-5000:],p.stderr[-5000:]);raise SystemExit(p.returncode)
    print(p.stdout.strip())

# ------- Synthesis 101 / 102 / 107 / 108 fixtures with injection at the gate -----
path=ROOT/'docs/item_only_phase102_20261118/test_102.py'
spec=importlib.util.spec_from_file_location('synthesis109_fixture', path)
fx=importlib.util.module_from_spec(spec);spec.loader.exec_module(fx)
user='l1j/server/server/model/network/core/cs/packet/User.java'
assert 'public class User {public static L1InventoryTransaction CheckAndSetPendingUseETCItem' in fx.STUBS[user]
fx.STUBS[user]=fx.STUBS[user].replace('public class User {','public class User {public static Runnable BEFORE_BEGIN;')
fx.STUBS[user]=fx.STUBS[user].replace('return L1InventoryTransaction.begin(pc,key);','if(BEFORE_BEGIN!=null)BEFORE_BEGIN.run();return L1InventoryTransaction.begin(pc,key);')
needle='''  System.out.println("PASS: actual production Java 21 type compilation and "+total+" checks");'''
assert fx.STUB_TEST.count(needle)==1
insert='''
  // A changed selection between taking the preimage and getting the gate
  // must NEVER stage SQL or debit the player, across all three tiers.
  for(int tier=0;tier<3;tier++)for(int slot=0;slot<4;slot++){
    reset();
    Account a=new Account(22,tier);L1PcInstance pc=new L1PcInstance(a,tier,22);
    int original=cardBefore(a,tier),stages=L1InventoryTransaction.SQL_STAGES,commits=L1InventoryTransaction.COMMITS;
    final int which=slot;
    l1j.server.server.model.network.core.cs.packet.User.BEFORE_BEGIN=()->{
      switch(which){case 0 -> a.setInfo_card_a(999);case 1 -> a.setInfo_card_b(999);
       case 2 -> a.setInfo_card_c(999);default -> a.setInfo_card_d(999);}
    };
    check(!invoke(tier,pc),"stale UI refused tier="+tier+" slot="+slot);
    check(L1InventoryTransaction.SQL_STAGES==stages,"no SQL staged on stale UI");
    check(L1InventoryTransaction.COMMITS==commits,"no durable receipt on stale UI");
    check(cardBefore(a,tier)==original,"no card debit on stale UI");
    check(!a.events.toString().contains("store:"),"no reward on stale UI");
    l1j.server.server.model.network.core.cs.packet.User.BEFORE_BEGIN=null;
  }
  // In addition to UI drift, changing the actual card amount at the gate
  // also must reject before the SQL stage. Do not overwrite the new balance.
  for(int tier=0;tier<3;tier++){
    reset();Account a=new Account(22,tier);L1PcInstance pc=new L1PcInstance(a,tier,22);
    int initial=cardBefore(a,tier),stages=L1InventoryTransaction.SQL_STAGES;
    l1j.server.server.model.network.core.cs.packet.User.BEFORE_BEGIN=()->{
      if(tier==0)a.setInfo_ds_poly(initial+5);
      else if(tier==1)a.setInfo_heaven_poly(initial+5);
      else a.setInfo_class_poly(initial+5);
    };
    check(!invoke(tier,pc),"stale economy rejected");
    check(L1InventoryTransaction.SQL_STAGES==stages,"no SQL on changed balance");
    check(cardBefore(a,tier)==initial+5,"changed balance not overwritten");
    check(!a.events.toString().contains("consume:"),"no adena debit after drift");
    l1j.server.server.model.network.core.cs.packet.User.BEFORE_BEGIN=null;
  }
'''
# Java lambda captures tier loop var must be effectively final.
insert=insert.replace('    int initial=cardBefore(a,tier),stages=L1InventoryTransaction.SQL_STAGES;',
                       '    final int kind=tier;int initial=cardBefore(a,tier),stages=L1InventoryTransaction.SQL_STAGES;')
insert=insert.replace('if(tier==0)a.setInfo_ds_poly(initial+5);','if(kind==0)a.setInfo_ds_poly(initial+5);').replace('else if(tier==1)a.setInfo_heaven_poly(initial+5);','else if(kind==1)a.setInfo_heaven_poly(initial+5);')
fx.STUB_TEST=fx.STUB_TEST.replace(needle,insert+needle)
fx.run_one('new')
# ------- Registration: load phase103 fixture and inject gate hook. ----------------
run('python3','docs/item_only_phase103_20261118/test_103.py')
reg=ROOT/'docs/item_only_phase103_20261118/fixture_work/src'
f=reg/user
s=f.read_text();assert 'public class User {public static L1InventoryTransaction CheckAndSetPendingUseETCItem' in s
s=s.replace('public class User {','public class User {public static Runnable BEFORE_BEGIN;').replace('return new L1InventoryTransaction(pc);','if(BEFORE_BEGIN!=null)BEFORE_BEGIN.run();return new L1InventoryTransaction(pc);')
f.write_text(s)
source=reg/'l1j/server/server/model/item/Card/CardRegistration109Test.java'
source.write_text('''package l1j.server.server.model.item.Card;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.cs.packet.User;
public class CardRegistration109Test {
 static int assertions;
 static void ok(boolean x,String why){assertions++;if(!x)throw new AssertionError(why);}
 static void empty(L1InventoryTransaction t,String why){ok(t!=null && !t.pending && t.action==null && t.after==null,why);}
 public static void main(String[] args)throws Exception {
   for(String[] rec:CardRegistration103Test.CARDS){
     int type=Integer.parseInt(rec[0]), id=Integer.parseInt(rec[1]);String field=rec[2];
     // A newer selection must survive. A stale token must not be debited.
     L1PcInstance pc=new L1PcInstance();L1ItemInstance original=new L1ItemInstance(id);
     L1ItemInstance newer=new L1ItemInstance(id);pc.selected=original;
     final L1PcInstance owner=pc;
     User.BEFORE_BEGIN=()->owner.selected=newer;
     CardRegistration103Test.go(type,pc);
     empty(L1InventoryTransaction.last,"changed collection token rejected");
     ok(pc.selected==newer && original.count==1 && newer.count==1,"new selection preserved");
     ok(pc.messages.size()==1 && pc.messages.get(0).contains("변경"),"stale selection warning");
     User.BEFORE_BEGIN=null;
     // The captured count is no longer valid; fail before staging any SQL.
     pc=new L1PcInstance();pc.selected=new L1ItemInstance(id);
     final Account acc=pc.acc;
     User.BEFORE_BEGIN=()->{try{CardRegistration103Test.set(acc,field,5);}catch(Exception e){throw new RuntimeException(e);}};
     CardRegistration103Test.go(type,pc);
     empty(L1InventoryTransaction.last,"changed collection balance rejected");
     ok(CardRegistration103Test.get(acc,field)==5,"no hidden count modification");
     User.BEFORE_BEGIN=null;
     // First-registration score drift must also be checked before the receipt.
     pc=new L1PcInstance();pc.selected=new L1ItemInstance(id);
     final Account a=pc.acc;
     final String score=CardRegistration103Test.scoreField(type);
     User.BEFORE_BEGIN=()->{try{CardRegistration103Test.set(a,score,7);}catch(Exception e){throw new RuntimeException(e);}};
     CardRegistration103Test.go(type,pc);
     empty(L1InventoryTransaction.last,"changed score rejected");
     ok(CardRegistration103Test.get(a,score)==7,"score not overwritten");
     User.BEFORE_BEGIN=null;
   }
   for(String[] rec:CardRegistration103Test.TREES){
     int id=Integer.parseInt(rec[0]);String field=rec[2];
     L1PcInstance pc=new L1PcInstance();L1ItemInstance original=new L1ItemInstance(id);
     L1ItemInstance newer=new L1ItemInstance(id);pc.selected=original;
     final L1PcInstance owner=pc;
     User.BEFORE_BEGIN=()->owner.selected=newer;
     L1CardAdd.SkillTree(pc);
     empty(L1InventoryTransaction.last,"changed tree token rejected");
     ok(pc.selected==newer && original.count==1 && newer.count==1,"new tree selection preserved");
     User.BEFORE_BEGIN=null;
     pc=new L1PcInstance();pc.selected=new L1ItemInstance(id);
     final Account a=pc.acc;
     User.BEFORE_BEGIN=()->{try{CardRegistration103Test.set(a,field,1);}catch(Exception e){throw new RuntimeException(e);}};
     L1CardAdd.SkillTree(pc);
     empty(L1InventoryTransaction.last,"changed tree balance rejected");
     ok(CardRegistration103Test.get(a,field)==1,"tree count preserved");
     User.BEFORE_BEGIN=null;
   }
   System.out.println("PASS 109 card collection / tree gate race: "+assertions+" assertions");
 }
}
''', encoding='utf8')
files=sorted(str(p) for p in reg.rglob('*.java'))
out=DOC/'java_109_classes';out.mkdir(parents=True,exist_ok=True)
run('javac','-encoding','UTF-8','--release','21','-d',str(out),*files)
run('java','-cp',str(out),'l1j.server.server.model.item.Card.CardRegistration103Test')
run('java','-cp',str(out),'l1j.server.server.model.item.Card.CardRegistration109Test')
# Phase103's own regression compiler intentionally includes every .java in its
# fixture_work directory. Remove only our temporary hook class after testing,
# so original Phase103/104 scripts can be rerun in any order.
source.unlink()
f.write_text(s.replace('public static Runnable BEFORE_BEGIN;', '').replace('if(BEFORE_BEGIN!=null)BEFORE_BEGIN.run();',''))
print('PASS 109: both production classes compile and gate-change tests complete')
