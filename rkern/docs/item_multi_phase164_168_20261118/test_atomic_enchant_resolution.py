#!/usr/bin/env python3
"""Java 21 typed tests against the actual production weapon/armor resolution files.
The receipt and world classes are typed mocks, NOT a MySQL integration test.
"""
from pathlib import Path
import tempfile, subprocess, sys
root=Path(sys.argv[1]).resolve()
legacy=root/'docs/item_multi_phase159_163_20261118'
base='l1j/server/server/model/item/function/'
receipt='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.session.CIOClient;
public class ItemReceiptOperations {
  public static int submitted,successes,failures,destructions;
  public static boolean commit=true;
  public static void UseAtomicEnchantSuccess(L1PcInstance p,L1ItemInstance scroll,L1ItemInstance target,CIOClient client,Runnable effects){
    submitted++; if(!commit) return;
    if(scroll.count<=0 || target.count<=0)throw new AssertionError("missing receipt input");
    scroll.count--; target.lv++; successes++; ItemActionCompletion.finishCommittedConsumable(p,scroll); effects.run();
  }
  public static void UseDurableConsume(L1PcInstance p,L1ItemInstance scroll,Runnable effects){
    submitted++; if(!commit)return;
    if(scroll.count<=0)throw new AssertionError("missing scroll");
    scroll.count--; failures++;effects.run();
  }
  public static void UseDurableConsumeTwoInput(L1PcInstance p,L1ItemInstance scroll,L1ItemInstance target,Runnable effects){
    submitted++; if(!commit)return;
    if(scroll.count<=0 || target.count<=0)throw new AssertionError("missing ruin target");
    scroll.count--;target.count--;destructions++;effects.run();
  }
}'''
completion='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class ItemActionCompletion {
  public static int finishes;
  public static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance scroll) {
    finishes++;if(scroll.count!=0)throw new AssertionError("delay started before scroll COMMIT");
  }
}'''
# Capture old script's compiled stubs and replace its old TestHarness just before javac.
# The legacy test still covers its previous cases independently on the baseline.
for path,typ in ((legacy/'check_weapon.py','weapon'),(legacy/'check_armor_extended.py','armor')):
    original=path.read_text()
    marker=" files=list(root.rglob('*.java'))+[prod];out=root/'out';out.mkdir()" if typ=='weapon' else " classes=r/'classes';classes.mkdir(); files=list(r.rglob('*.java'))+[prod]"
    assert marker in original
    test='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.model.network.core.sc.packet.*;
public class TestHarness {
 static int checks;
 static void ok(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg+" check "+checks);}
 public static void main(String[] args) {
   for (int id : new int[]{98077,68077,3000380,4000518,3000518,3000517}) {
     for (boolean win : new boolean[]{false,true}) for(boolean commit:new boolean[]{false,true})
        for(boolean packetFailure:new boolean[]{false,true}) {
       L1PcInstance p=new L1PcInstance();L1ItemInstance scroll=new L1ItemInstance(),target=new L1ItemInstance();
       target.lv=1;target.data.id=id==98077?123111:900116;
       ItemReceiptOperations.commit=commit;
       MJRnd.win=win;
       l1j.server.Config.accessory[1]=win?100:0;
       SC_SystemMessage.fail=packetFailure;
       ItemReceiptOperations.submitted=ItemReceiptOperations.successes=ItemReceiptOperations.failures=ItemReceiptOperations.destructions=0;
       ItemActionCompletion.finishes=0;
       ItemActionResult outcome=ArmorEnchantScrollResolution.apply(new ItemActionContext(p,scroll,target,id));
       ok(outcome==ItemActionResult.STOP,"return STOP id="+id);
       ok(ItemReceiptOperations.submitted==1,"one submission id="+id);
       ok(scroll.count==(commit?0:1),"consumed iff committed id="+id);
       ok(target.lv==(commit&&win?2:1),"enchanted iff win+COMMIT id="+id);
       ok(target.count==(commit&&!win&&id==3000517?0:1),"destruction is atomic id="+id);
       ok(ItemActionCompletion.finishes==(commit?1:0),"delay iff committed id="+id);
       ok(p.inventory.removals==0,"no legacy nontransaction debit id="+id);
     }
   }
   System.out.println("ARMOR_ATOMIC_TYPED_CASES="+checks+" PASS");
 }
}'''
    if typ=='weapon':
        test='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.*;
public class TestHarness {
 static int checks;
 static void ok(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg+" check "+checks);}
 public static void main(String[]args){
   for(boolean win:new boolean[]{false,true})for(boolean commit:new boolean[]{false,true})
     for(boolean packetFailure:new boolean[]{false,true})for(int i=0;i<6;i++){
       L1PcInstance p=new L1PcInstance();L1ItemInstance scroll=new L1ItemInstance(),target=new L1ItemInstance();
       target.lv=1;target.data.id=315;
       ItemReceiptOperations.commit=commit;
       ItemReceiptOperations.submitted=0;ItemActionCompletion.finishes=0;
       SC_SystemMessage.fail=packetFailure;
       // Config.rareweapon[1] can force either outcome; rnd in 1..100.
       l1j.server.Config.rareweapon[1]=win?100:0;
       ItemActionResult outcome=WeaponEnchantScrollResolution.apply(new ItemActionContext(p,scroll,target,68076));
       ok(outcome==ItemActionResult.STOP,"deferred path");
       ok(ItemReceiptOperations.submitted==1,"one receipt");
       ok(scroll.count==(commit?0:1),"scroll counted on commit only");
       ok(target.lv==(commit&&win?2:1),"target enchant atomic");
       ok(ItemActionCompletion.finishes==(commit?1:0),"delay on commit only");
       ok(p.inv.removals==0,"legacy remove bypassed");
     }
   System.out.println("WEAPON_ATOMIC_TYPED_CASES="+checks+" PASS");
 }
}'''
    stubcalls="\n add(base+'ItemReceiptOperations.java',"+repr(receipt)+")\n add(base+'ItemActionCompletion.java',"+repr(completion)+")\n add(base+'TestHarness.java',"+repr(test)+")\n"
    modified=original.replace(marker,stubcalls+marker,1)
    # legacy python module argument differs paths src expected in script (yes)
    with tempfile.NamedTemporaryFile('w',suffix='.py',delete=False,encoding='utf8') as f:
        f.write(modified)
        script=Path(f.name)
    try:
        result=subprocess.run([sys.executable,str(script),str(root/'src')],capture_output=True,text=True,timeout=55)
        if result.returncode:raise RuntimeError(f'{typ} compile/run FAILED:\n{result.stdout}\n{result.stderr}')
        print(result.stdout.strip())
    finally:
        script.unlink()
