"""Phase 110: compiled production card synthesis, fault-injected post-commit effects.
The account/SQL/inventory implementations are typed doubles; not a MySQL test.
"""
from pathlib import Path
import importlib.util
root = Path(__file__).resolve().parents[2]
source = root / 'docs/item_only_phase102_20261118/test_102.py'
spec = importlib.util.spec_from_file_location('card_phase102_fixture', source)
f = importlib.util.module_from_spec(spec)
spec.loader.exec_module(f)
packet='l1j/server/server/model/network/core/sc/packet/SC_SkillSound.java'
p=f.STUBS[packet]
assert 'public class SC_SkillSound {' in p
assert 'public static void send(L1PcInstance p,int id,int fx){Trace.e("sound:"+fx);}' in p
p=p.replace('public class SC_SkillSound {','public class SC_SkillSound {public static boolean FAIL_SEND;')
p=p.replace('public static void send(L1PcInstance p,int id,int fx){Trace.e("sound:"+fx);}',
            'public static void send(L1PcInstance p,int id,int fx){if(FAIL_SEND)throw new IllegalStateException("injected packet failure");Trace.e("sound:"+fx);}')
f.STUBS[packet]=p
packet='l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java'
p=f.STUBS[packet]
assert 'public class SC_SystemMessage {' in p
p=p.replace('public class SC_SystemMessage {','public class SC_SystemMessage {public static boolean FAIL_SEND;')
p=p.replace('public static void send(L1PcInstance p,String x){Trace.e("sys:"+x);}',
            'public static void send(L1PcInstance p,String x){if(FAIL_SEND)throw new IllegalStateException("injected GM message failure");Trace.e("sys:"+x);}')
f.STUBS[packet]=p
packet='l1j/server/server/utils/MessageUtil.java'
p=f.STUBS[packet]
assert 'public class MessageUtil {' in p
p=p.replace('public class MessageUtil {','public class MessageUtil {public static boolean FAIL_GLOBAL;')
p=p.replace('public static void toAllPlayers(String x){Trace.e("all:"+x);}',
            'public static void toAllPlayers(String x){if(FAIL_GLOBAL)throw new IllegalStateException("injected global notice failure");Trace.e("all:"+x);}')
f.STUBS[packet]=p
packet='l1j/server/server/model/L1InventoryTransaction.java' 
p=f.STUBS[packet]
assert 'public static boolean FAIL_DEBIT,FAIL_GRANT,FAIL_SQL,DB_MISMATCH,DEFER;' in p
p=p.replace('public static boolean FAIL_DEBIT,FAIL_GRANT,FAIL_SQL,DB_MISMATCH,DEFER;',
            'public static boolean FAIL_DEBIT,FAIL_GRANT,FAIL_SQL,DB_MISMATCH,DEFER; public static Runnable BEFORE_CALLBACK;')
assert 'COMMITS++;callback.run();' in p
p=p.replace('COMMITS++;callback.run();','COMMITS++;if(BEFORE_CALLBACK!=null)BEFORE_CALLBACK.run();callback.run();')
f.STUBS[packet]=p
needle='''  System.out.println("PASS: actual production Java 21 type compilation and "+total+" checks");'''
assert f.STUB_TEST.count(needle)==1
extra='''  // Fault injection AFTER durable commitment: a packet/UI failure is NOT an
  // uncertain economy result. No duplicate debit, and no persistent bonus.
  for(int k=0;k<3;k++){
    reset();Account a=new Account(22,k);L1PcInstance p=new L1PcInstance(a,k,22);
    int before=cardBefore(a,k), commits=L1InventoryTransaction.COMMITS;
    l1j.server.server.model.network.core.sc.packet.SC_SkillSound.FAIL_SEND=true;
    check(invoke(k,p),"accepted synthesis even with broken effect");
    l1j.server.server.model.network.core.sc.packet.SC_SkillSound.FAIL_SEND=false;
    check(L1InventoryTransaction.COMMITS==commits+1,"once committed with broken effect");
    check(cardBefore(a,k)==before-1,"card applied with broken effect");
    check(a.getInfo_card_a()==0 && a.getInfo_card_b()==0 && a.getInfo_card_c()==0 && a.getInfo_card_d()==0,"selection retired");
    check(a.events.toString().contains("store:"),"reward already granted");
    check(!bonus(p,k),"committed probability bonus retired before packet");
  }
  // Global announcements can throw after the reward was committed. All three
  // tiers must retain the durable card debit and retire the bonus regardless.
  l1j.server.Config.myth_per=100;
  l1j.server.Config.legend_per=100;
  l1j.server.Config.sole_per=100;
  for(int k=0;k<3;k++){
    reset();Account a=new Account(22,k);L1PcInstance p=new L1PcInstance(a,k,22);
    int before=cardBefore(a,k),commits=L1InventoryTransaction.COMMITS;
    l1j.server.server.utils.MessageUtil.FAIL_GLOBAL=true;
    check(invoke(k,p),"announcement failure ignored after commitment");
    l1j.server.server.utils.MessageUtil.FAIL_GLOBAL=false;
    check(L1InventoryTransaction.COMMITS==commits+1,"announcement exception preserves one commit");
    check(cardBefore(a,k)==before-1 && a.events.toString().contains("store:"),"announcement exception does not undo reward");
    check(!bonus(p,k),"announcement exception does not preserve probability bonus");
  }
  l1j.server.Config.myth_per=50;
  l1j.server.Config.legend_per=50;
  l1j.server.Config.sole_per=50;
  // GM-only debug messages were previously emitted before the flag retired.
  for(int k=0;k<3;k++){
    reset();Account a=new Account(23,k);L1PcInstance p=new L1PcInstance(a,k,23);
    p.전설합성확률증가=true;p.영웅합성확률증가=true;p.신화합성확률증가=true;
    int before=cardBefore(a,k),commits=L1InventoryTransaction.COMMITS;
    l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.FAIL_SEND=true;
    check(invoke(k,p),"GM debug failure cannot unwind committed synthesis");
    l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.FAIL_SEND=false;
    check(cardBefore(a,k)==before-1 && L1InventoryTransaction.COMMITS==commits+1,"GM effect cannot uncommit");
    check(!bonus(p,k),"GM debug failure cannot preserve consumed bonus");
  }
  // Rollback means the probability buff has not been spent.
  for(int k=0;k<3;k++){
    reset();Account a=new Account(22,k);L1PcInstance p=new L1PcInstance(a,k,22);
    int before=cardBefore(a,k);L1InventoryTransaction.FAIL_SQL=true;
    invoke(k,p);
    check(cardBefore(a,k)==before,"failed DB write leaves account count");
    check(bonus(p,k),"failed DB write retains bonus");
  }
  // A committed count conflict is still a HARD failure. Only cosmetic failures
  // are swallowed. Memory preimage protection is never moved into the catch.
  for(int k=0;k<3;k++){
    reset();Account a=new Account(22,k);L1PcInstance p=new L1PcInstance(a,k,22);
    int before=cardBefore(a,k);final int tier=k;
    L1InventoryTransaction.DEFER=true;
    check(invoke(k,p),"accepted pending count drift");
    L1InventoryTransaction.BEFORE_CALLBACK=()->{
      if(tier==0)a.setInfo_ds_poly(before+9);
      else if(tier==1)a.setInfo_heaven_poly(before+9);
      else a.setInfo_class_poly(before+9);
    };
    boolean threw=false;
    try{L1InventoryTransaction.flush();}catch(IllegalStateException x){
       threw=x.getMessage().contains("memory preimage changed");
    }
    check(threw,"economy preimage drift must still propagate");
    check(cardBefore(a,k)==before+9,"no overwrite of concurrently changed card quantity");
  }
'''
# Static method in Test101 body inserted before main.
method=''' static boolean bonus(L1PcInstance p,int kind){return kind==0?p.전설합성확률증가:kind==1?p.영웅합성확률증가:p.신화합성확률증가;}
'''
f.STUB_TEST=f.STUB_TEST.replace(' public static void main(String[] args)throws Exception{', method+' public static void main(String[] args)throws Exception{')
f.STUB_TEST=f.STUB_TEST.replace('L1InventoryTransaction.PENDING=null;}', 'L1InventoryTransaction.PENDING=null;L1InventoryTransaction.BEFORE_CALLBACK=null;}')
f.STUB_TEST=f.STUB_TEST.replace(needle,extra+needle)
f.run_one('new')
for n,flag in [('L1CardAlchemy1.java','전설합성확률증가'),('L1CardAlchemy2.java','영웅합성확률증가'),('L1CardAlchemy3.java','신화합성확률증가')]:
    code=(root/'src/l1j/server/server/model/item/Card'/n).read_text(encoding='utf8')
    assert code.count('pc.'+flag+' = false;')==1
code=(root/'src/l1j/server/server/model/item/Card/CardAlchemyReceipt.java').read_text(encoding='utf8')
assert 'Card synthesis memory preimage changed' in code
assert 'catch (RuntimeException effectFailure)' in code
print('PASS Phase110: typed production + fault injection on committed presentation; economy guard intact')
