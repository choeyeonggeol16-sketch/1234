#!/usr/bin/env python3
"""Run real 810003 & 4100148 branches compiled in full production WeaponEnchantScrollResolution.
Reuses typed test fixture from phase159; receipt is a controlled commit/rollback stub."""
from pathlib import Path
import tempfile,subprocess,sys
project=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[2]
here=project
legacy=(project/'docs/item_multi_phase159_163_20261118/check_weapon.py').read_text()
start=legacy.index(" add(base+'TestHarness.java'")
end=legacy.index(" files=list(root.rglob('*.java'))+[prod];out=root/'out';out.mkdir()",start)
receipt='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.session.*;
public class ItemReceiptOperations {
 static boolean commit=true; static int sent,atomic,consume;
 static void UseAtomicEnchantSuccess(L1PcInstance p,L1ItemInstance s,L1ItemInstance t,CIOClient c,Runnable onCommit){
  sent++;atomic++;if(commit){s.count--;t.lv++;ItemActionCompletion.finishCommittedConsumable(p,s);if(onCommit!=null)onCommit.run();}
 }
 static void UseDurableConsume(L1PcInstance p,L1ItemInstance s,Runnable onCommit){
  sent++;consume++;if(commit){s.count--;if(onCommit!=null)onCommit.run();}
 }
}'''
addition='''\n add(base+'SpecialEnchantScrolls.java','''+repr('''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class SpecialEnchantScrolls {
 static boolean isOwnedScrollTarget(L1PcInstance p,L1ItemInstance s,L1ItemInstance t){
 return p!=null&&s!=null&&t!=null&&s!=t&&s.count>0&&t.count>0;
 }}''')+''')
 add(base+'ItemReceiptOperations.java','''+repr(receipt)+''')
 add(base+'ItemActionCompletion.java','''+repr('''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class ItemActionCompletion {static int done;static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance s){
 if(s.count!=0)throw new AssertionError("precommit completion");done++;}}''')+''')
'''
test='''package l1j.server.server.model.item.function;
import l1j.server.Config;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.*;
class TestHarness {
 static int checks;static void ok(boolean v,String what){checks++;if(!v)throw new AssertionError(checks+" "+what);}
 static void run(int id,int enchant,int material,boolean commit,boolean packetFault,boolean masterWin){
  L1PcInstance pc=new L1PcInstance();L1ItemInstance scroll=new L1ItemInstance(),target=new L1ItemInstance();
  target.data.id=315;target.lv=enchant;target.data.material=material;
  Config.Master_Enchant=masterWin?100:0;
  SC_SystemMessage.fail=packetFault;SC_ServerMessage.fail=packetFault;
  ItemReceiptOperations.sent=ItemReceiptOperations.atomic=ItemReceiptOperations.consume=0;
  ItemReceiptOperations.commit=commit;ItemActionCompletion.done=0;
  int a=target.lv;
  ItemActionResult r=null;boolean packetException=false;
  try {r=WeaponEnchantScrollResolution.apply(new ItemActionContext(pc,scroll,target,id));}
  catch(RuntimeException ex){packetException=true;}
  boolean admitted=material!=9 && material!=18 && (id==810003?enchant==9:enchant>=8);
  ok(admitted?!packetException&&r==ItemActionResult.STOP:packetFault?packetException:r==ItemActionResult.COMPLETE,"return flow");
  ok(ItemReceiptOperations.sent==(admitted?1:0),"receipt submitted iff valid");
  ok(pc.inv.removals==0,"no direct inventory debit");
  ok(scroll.count==(admitted&&commit?0:1),"durable scroll debit");
  ok(target.lv==a+(admitted&&commit&&(id==4100148||masterWin)?1:0),"durable level");
  ok(ItemActionCompletion.done==(admitted&&commit?1:0),"delay after commit");
  ok(ItemReceiptOperations.atomic==(admitted&&(id==4100148||masterWin)?1:0),"atomic success chosen");
  ok(ItemReceiptOperations.consume==(admitted&&id==810003&&!masterWin?1:0),"non-success receipt chosen");
 }
 public static void main(String[]args){
  for(int id:new int[]{810003,4100148})for(int material:new int[]{0,9,18})
  for(int enchant:new int[]{7,8,9,10})for(boolean commit:new boolean[]{true,false})
  for(boolean packetFault:new boolean[]{true,false})for(boolean win:new boolean[]{true,false})
  run(id,enchant,material,commit,packetFault,win);
  System.out.println("WEAPON_MASTER_TYPED_CASES="+checks+" PASS");
 }
}'''
addition+=" add(base+'TestHarness.java',"+repr(test)+")\n"
script=legacy[:start]+addition+legacy[end:]
with tempfile.NamedTemporaryFile('w',suffix='.py',encoding='utf8',delete=False) as f:
 f.write(script);path=Path(f.name)
try:
 run=subprocess.run([sys.executable,str(path),str(here/'src')],text=True,capture_output=True,timeout=90)
 if run.returncode:raise SystemExit(run.stdout+'\n'+run.stderr)
 print(run.stdout.strip())
finally:path.unlink()
