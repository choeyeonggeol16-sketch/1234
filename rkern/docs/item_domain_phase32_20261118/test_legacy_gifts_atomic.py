#!/usr/bin/env python3
"""Compile production LegacyGiftBoxes against typed Java21 game stubs; run two baseline methods across all rolls."""
from pathlib import Path
import tempfile, subprocess
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'src/l1j/server/server/model/item/function'
BASE=Path(__file__).resolve().parent/'baseline'
new=(SRC/'LegacyGiftBoxes.java').read_text('utf8')
router=(SRC/'ItemActionDispatcher.java').read_text('utf8')
assert 'case 41303 -> LegacyGiftBoxes.open(use, 41303);' in router
assert 'case 41304 -> LegacyGiftBoxes.open(use, 41304);' in router
for removed in ('LegacyGiftPackageUse','LegacyGiftGroupTwoUse'):
    assert not (SRC/(removed+'.java')).exists()
    for p in (ROOT/'src').rglob('*.java'):
        assert removed not in p.read_text('utf8'), p

STUBS = {}
def put(name,code): STUBS[name]=code
put('l1j/server/server/model/Instance/L1ItemInstance.java','''package l1j.server.server.model.Instance;
public final class L1ItemInstance{final int id,template;public L1ItemInstance(int t,int i){template=t;id=i;}public int getId(){return id;}public int getItemId(){return template;}}''')
put('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
import java.util.*;
public final class L1PcInstance {public final List<String> messages=new ArrayList<>();public final Inventory inv;public L1PcInstance(int id){inv=new Inventory(id);}public Inventory getInventory(){return inv;}public int getId(){return 25;}
public final class Inventory{public final L1ItemInstance source;public final List<String> rewards=new ArrayList<>();public int count=1;public Inventory(int id){source=new L1ItemInstance(id,40);}public L1ItemInstance getItem(int id){return count>0&&id==40?source:null;}public void storeItem(int id,int n){rewards.add(id+":"+n);}public void removeItem(L1ItemInstance i,int n){if(i!=source||n!=1||count<=0)throw new AssertionError("bad debit");count--;}}}''')
put('l1j/server/server/model/item/function/ItemActionContext.java','''package l1j.server.server.model.item.function;import l1j.server.server.model.Instance.*;final class ItemActionContext{final L1PcInstance p;final L1ItemInstance i;ItemActionContext(L1PcInstance p,L1ItemInstance i){this.p=p;this.i=i;}L1PcInstance player(){return p;}L1ItemInstance item(){return i;}}''')
put('l1j/server/server/model/item/function/ItemActionResult.java','''package l1j.server.server.model.item.function;enum ItemActionResult{COMPLETE,STOP}''')
put('l1j/server/server/model/item/function/CreatureItemActions.java','''package l1j.server.server.model.item.function;import java.util.Random;final class CreatureItemActions {static final ForcedRandom _random=new ForcedRandom();static final class ForcedRandom extends Random{int value;@Override public int nextInt(int bound){if(value>=bound)throw new AssertionError("invalid RNG range");return value;}}}''')
put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;public final class SC_SystemMessage{public static void send(L1PcInstance p,String text){p.messages.add("SYS:"+text);}}''')
put('l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java','''package l1j.server.server.model.network.core.sc.packet;import l1j.server.server.model.Instance.L1PcInstance;public final class SC_ServerMessage{public static void send(L1PcInstance p,int n){p.messages.add("SERVER:"+n);}}''')
put('l1j/server/server/model/L1InventoryTransaction.java','''package l1j.server.server.model;import java.util.*;import l1j.server.server.model.Instance.*;
public final class L1InventoryTransaction implements AutoCloseable {public static int failGrantAt;public static boolean failSubmit;final L1PcInstance p;public boolean submitted,debited;public int grants;public Runnable done,failed;public final List<String> prepared=new ArrayList<>();public L1InventoryTransaction(L1PcInstance p){this.p=p;}
public boolean debit(L1ItemInstance i,int n){if(i!=p.inv.source||n!=1||p.inv.count!=1)return false;debited=true;return true;}
public Object grant(int id,int n){if(++grants==failGrantAt)return null;prepared.add(id+":"+n);return new Object();}
public void onFailure(Runnable r){failed=r;}public void commitAfterInventoryRelease(Runnable r){if(failSubmit)throw new IllegalStateException("capacity");submitted=true;done=r;}
public void finish(boolean success){if(!submitted)throw new AssertionError("not submitted");if(success){if(!debited)throw new AssertionError("no debit");p.inv.count--;p.inv.rewards.addAll(prepared);if(done!=null)done.run();}else if(failed!=null)failed.run();}
public void close(){}}''')
put('l1j/server/server/model/network/core/cs/packet/User.java','''package l1j.server.server.model.network.core.cs.packet;import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;public final class User{public static L1InventoryTransaction last;public static L1InventoryTransaction CheckAndSetPendingUseETCItem(L1PcInstance p,int key){return last=new L1InventoryTransaction(p);}}''')
put('l1j/server/server/model/network/core/cs/packet/CDB.java','''package l1j.server.server.model.network.core.cs.packet;import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;public final class CDB{public static void RequestUseItem(L1PcInstance p,L1InventoryTransaction tx,Runnable r){tx.commitAfterInventoryRelease(r);}}''')
put('l1j/server/server/storage/CacheD.java','''package l1j.server.server.storage;public final class CacheD{public static int accountOrderKey(String p,int id){return id;}}''')
put('l1j/server/server/model/item/function/LegacyGiftBoxes.java',new)
for old,renamed in [('LegacyGiftPackageUse','OldLegacyGiftPackageUse'),('LegacyGiftGroupTwoUse','OldLegacyGiftGroupTwoUse')]:
    original=(BASE/(old+'.java')).read_text('utf8')
    code='\n'.join(ln for ln in original.splitlines() if not ln.startswith('import ')).replace(old,renamed)
    code=code.replace('package l1j.server.server.model.item.function;','''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.CreatureItemActions._random;''')
    put('l1j/server/server/model/item/function/'+renamed+'.java',code)
put('l1j/server/server/model/item/function/TestLegacyGift32.java','''package l1j.server.server.model.item.function;
import java.util.*;import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.cs.packet.*;
public final class TestLegacyGift32{
 static int cases=0;static void ok(boolean v,String msg){if(!v)throw new AssertionError(msg);}
 static void reset(int roll){CreatureItemActions._random.value=roll-1;L1InventoryTransaction.failGrantAt=0;L1InventoryTransaction.failSubmit=false;User.last=null;}
 static void old(int id,L1PcInstance p){ItemActionContext c=new ItemActionContext(p,p.inv.source);if(id==41303)OldLegacyGiftPackageUse.handleOpenLegacyGift(c);else OldLegacyGiftGroupTwoUse.handleOpenLegacyGiftGroup2(c);}
 static void current(int id,L1PcInstance p){ok(LegacyGiftBoxes.open(new ItemActionContext(p,p.inv.source),id)==ItemActionResult.COMPLETE,"result");}
 public static void main(String[] args){for(int id:new int[]{41303,41304}){int maximum=id==41303?120:170;
 for(int roll=1;roll<=maximum;roll++){
 reset(roll);L1PcInstance original=new L1PcInstance(id);old(id,original);
 List<String> expectedItems=new ArrayList<>(original.inv.rewards);List<String> expectedMessages=new ArrayList<>(original.messages);
 ok(original.inv.count==0,"original debit");reset(roll);L1PcInstance revised=new L1PcInstance(id);current(id,revised);
 ok(revised.inv.count==1&&revised.inv.rewards.isEmpty()&&revised.messages.isEmpty(),"precommit mutation "+id+"/"+roll);
 ok(User.last!=null&&User.last.submitted,"no submitted receipt");User.last.finish(true);
 ok(revised.inv.count==0&&revised.inv.rewards.equals(expectedItems)&&revised.messages.equals(expectedMessages),"outcome "+id+"/"+roll);
 cases++;
 for(int at=1;at<=expectedItems.size();at++){
 reset(roll);L1InventoryTransaction.failGrantAt=at;L1PcInstance bad=new L1PcInstance(id);current(id,bad);
 ok(bad.inv.count==1&&bad.inv.rewards.isEmpty()&&User.last!=null&&!User.last.submitted,"partial grant failure");
 ok(bad.messages.equals(List.of("SERVER:82")),"failure notice");cases++;
 }
 reset(roll);L1PcInstance rollback=new L1PcInstance(id);current(id,rollback);User.last.finish(false);
 ok(rollback.inv.count==1&&rollback.inv.rewards.isEmpty()&&rollback.messages.size()==1&&rollback.messages.get(0).startsWith("SYS:선물상자 보상"),"DB rollback");cases++;
 reset(roll);L1InventoryTransaction.failSubmit=true;L1PcInstance capacity=new L1PcInstance(id);current(id,capacity);
 ok(capacity.inv.count==1&&capacity.inv.rewards.isEmpty()&&capacity.messages.size()==1&&capacity.messages.get(0).startsWith("SYS:선물상자 사용"),"capacity failure");cases++;
 }
 reset(1);L1PcInstance stale=new L1PcInstance(id);LegacyGiftBoxes.open(new ItemActionContext(stale,new L1ItemInstance(id,40)),id);
 ok(User.last==null&&stale.inv.count==1,"stale source admitted");cases++;
 reset(1);L1PcInstance wrong=new L1PcInstance(id);LegacyGiftBoxes.open(new ItemActionContext(wrong,wrong.inv.source),41305);
 ok(User.last==null&&wrong.inv.count==1,"wrong box admitted");cases++;
 }
 System.out.println("PASS Java21 production typed original-vs-atomic: "+cases+" cases");}
}''')
with tempfile.TemporaryDirectory(prefix='rkern_item32_') as temp:
    path=Path(temp)
    for name,source in STUBS.items():
        dest=path/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(source,'utf8')
    compile=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(path/'classes'),*[str(p) for p in path.rglob('*.java')]],capture_output=True,text=True)
    if compile.returncode:raise RuntimeError('typed compile failed:\n'+compile.stderr)
    run=subprocess.run(['java','-cp',str(path/'classes'),'l1j.server.server.model.item.function.TestLegacyGift32'],capture_output=True,text=True)
    if run.returncode:raise RuntimeError('differential failed:\n'+run.stdout+'\n'+run.stderr)
    print(run.stdout.strip())
