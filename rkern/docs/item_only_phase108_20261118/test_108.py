"""Phase108: compile actual synthesis Java against Phase102 typed receipt stubs.

The transport/DB are stubs; this test proves the post-commit UI-only drift
policy independently of the durable account-count/session mismatch guard.
"""
from pathlib import Path
import importlib.util
root = Path(__file__).resolve().parents[2]
source = root / 'docs/item_only_phase102_20261118/test_102.py'
spec = importlib.util.spec_from_file_location('phase102_fixture', source)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
transaction_path='l1j/server/server/model/L1InventoryTransaction.java'
transaction=fixture.STUBS[transaction_path]
assert 'public static boolean FAIL_DEBIT,FAIL_GRANT,FAIL_SQL,DB_MISMATCH,DEFER;' in transaction
transaction=transaction.replace('public static boolean FAIL_DEBIT,FAIL_GRANT,FAIL_SQL,DB_MISMATCH,DEFER;',
                                'public static boolean FAIL_DEBIT,FAIL_GRANT,FAIL_SQL,DB_MISMATCH,DEFER; public static Runnable BEFORE_CALLBACK;')
assert 'COMMITS++;callback.run();' in transaction
transaction=transaction.replace('COMMITS++;callback.run();',
                                'COMMITS++;if(BEFORE_CALLBACK!=null)BEFORE_CALLBACK.run();callback.run();')
fixture.STUBS[transaction_path]=transaction
fixture.STUB_TEST=fixture.STUB_TEST.replace('L1InventoryTransaction.PENDING=null;}',
  'L1InventoryTransaction.PENDING=null;L1InventoryTransaction.BEFORE_CALLBACK=null;}')
needle='''  System.out.println("PASS: actual production Java 21 type compilation and "+total+" checks");'''
assert fixture.STUB_TEST.count(needle)==1
extra='''  // Phase108: an in-flight receipt must retire its confirmed economy even
  // if another session changed only the transient selection UI.
  for(int k=0;k<3;k++)for(int slot=0;slot<4;slot++){
    reset();
    Account changed=new Account(22,k);
    L1PcInstance owner=new L1PcInstance(changed,k,22);
    int before=cardBefore(changed,k);
    int commits=L1InventoryTransaction.COMMITS;
    L1InventoryTransaction.DEFER=true;
    check(invoke(k,owner),"accept pending synthesis "+k+":"+slot);
    check(cardBefore(changed,k)==before,"deferred card unchanged");
    switch(slot){
       case 0 -> changed.setInfo_card_a(999);
       case 1 -> changed.setInfo_card_b(999);
       case 2 -> changed.setInfo_card_c(999);
       default -> changed.setInfo_card_d(999);
    }
    L1InventoryTransaction.flush();
    check(L1InventoryTransaction.COMMITS==commits+1,"exactly one commit");
    check(cardBefore(changed,k)==before-1,"confirmed count applied once");
    int actual=switch(slot){case 0 -> changed.getInfo_card_a();case 1 -> changed.getInfo_card_b();
       case 2 -> changed.getInfo_card_c();default -> changed.getInfo_card_d();};
    check(actual==999,"newer UI-only selection preserved");
    check(changed.events.toString().contains("store:"),"reward committed");
  }
  // Drift in the actual economy is not a harmless UI change. It must still
  // reject the memory application, preserving the quarantine/error contract.
  for(int k=0;k<3;k++){
    reset();
    final int kind=k;
    Account changed=new Account(22,k);L1PcInstance owner=new L1PcInstance(changed,k,22);
    int before=cardBefore(changed,k);
    L1InventoryTransaction.DEFER=true;
    check(invoke(k,owner),"accept post-SQL race fixture");
    L1InventoryTransaction.BEFORE_CALLBACK=()->{
        if(kind==0)changed.setInfo_ds_poly(before+7);
        else if(kind==1)changed.setInfo_heaven_poly(before+7);
        else changed.setInfo_class_poly(before+7);
    };
    boolean rejected=false;
    try{L1InventoryTransaction.flush();}
    catch(IllegalStateException conflict){rejected=conflict.getMessage().contains("memory preimage changed");}
    check(rejected,"economy conflict is never treated as UI-only");
    check(cardBefore(changed,k)==before+7,"no overwrite of unexpected count");
  }
  // Multiple newer slots stay intact as a group.
  for(int k=0;k<3;k++){
    reset();Account changed=new Account(22,k);L1PcInstance owner=new L1PcInstance(changed,k,22);
    int before=cardBefore(changed,k);L1InventoryTransaction.DEFER=true;
    check(invoke(k,owner),"accepted full change");
    changed.setInfo_card_a(991);changed.setInfo_card_b(992);
    changed.setInfo_card_c(993);changed.setInfo_card_d(994);
    L1InventoryTransaction.flush();
    check(changed.getInfo_card_a()==991 && changed.getInfo_card_b()==992 &&
       changed.getInfo_card_c()==993 && changed.getInfo_card_d()==994,"preserve entire newer selection");
    check(cardBefore(changed,k)==before-1,"confirmed full change count");
  }
'''
fixture.STUB_TEST=fixture.STUB_TEST.replace(needle,extra+needle)
fixture.run_one('new')
card=(root/'src/l1j/server/server/model/item/Card/CardAlchemyReceipt.java').read_text()
assert 'Card synthesis memory preimage changed' in card
assert 'Card synthesis account session changed' in card
assert 'Card synthesis selection preimage changed' not in card
assert 'if (account.getInfo_card_a() == cardA' in card
assert 'info_card_a=0,info_card_b=0,info_card_c=0,info_card_d=0' in card
print('PASS 108: 12 one-slot races, 3 four-slot races, 3 account-count mismatch guards; original receipt tests')
