"""Typed Java 21 execution of actual CardAlchemyReceipt and three callers.
Adapts the Phase102 fixture to enforce Phase107's all-slot clearing and
selection-preimage guard, without replacing production methods with mocks.
"""
from pathlib import Path
import importlib.util
root = Path(__file__).resolve().parents[2]
original = root/'docs/item_only_phase102_20261118/test_102.py'
spec = importlib.util.spec_from_file_location('phase102_fixture', original)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
needle='''  System.out.println("PASS: actual production Java 21 type compilation and "+total+" checks");'''
extra='''  // 107: a two-card synthesis MUST retire stale C/D persisted selections.
  reset();
  Account stale=new Account(22,0);
  stale.setInfo_card_c(204);stale.setInfo_card_d(211);
  L1PcInstance stalePc=new L1PcInstance(stale,0,22);
  check(invoke(0,stalePc),"two-card stale-slot submission");
  check(L1InventoryTransaction.LAST_SQL.contains("info_card_c=0,info_card_d=0"),"all four SQL slots cleared");
  check(stale.getInfo_card_c()==0 && stale.getInfo_card_d()==0,"all four memory slots cleared");
  // 107: post-COMMIT memory guard must not erase a changed selection.
  for(int k=0;k<3;k++)for(int slot=0;slot<4;slot++){
    reset();
    Account changed=new Account(22,k);L1PcInstance owner=new L1PcInstance(changed,k,22);
    L1InventoryTransaction.DEFER=true;
    check(invoke(k,owner),"deferred accepted");
    int previous=cardBefore(changed,k);
    switch(slot){
      case 0 -> changed.setInfo_card_a(999);
      case 1 -> changed.setInfo_card_b(999);
      case 2 -> changed.setInfo_card_c(999);
      default -> changed.setInfo_card_d(999);
    }
    boolean rejected=false;
    try{L1InventoryTransaction.flush();}
    catch(IllegalStateException expected){rejected=expected.getMessage().contains("selection preimage");}
    check(rejected,"selection preimage detected "+k+":"+slot);
    check(cardBefore(changed,k)==previous,"no card-memory debit after selection race");
    int mutated=switch(slot){case 0->changed.getInfo_card_a();case 1->changed.getInfo_card_b();
      case 2->changed.getInfo_card_c();default->changed.getInfo_card_d();};
    check(mutated==999,"later selection never silently erased");
  }
'''
assert mod.STUB_TEST.count(needle)==1
mod.STUB_TEST=mod.STUB_TEST.replace(needle,extra+needle)
mod.run_one('new')
card = (root/'src/l1j/server/server/model/item/Card/CardAlchemyReceipt.java').read_text()
assert card.count('account.setInfo_card_d(0);') == 1
assert 'if (selectedCount == 4) account.setInfo_card_d(0);' not in card
assert 'info_card_a=0,info_card_b=0,info_card_c=0,info_card_d=0' in card
menu = (root/'src/l1j/server/server/NeigeCard3.java').read_text()
assert 'System.out.println("" + s)' not in menu
print('PASS 107: stale C/D cleanup, all 12 selection-race branches, no debug print')
