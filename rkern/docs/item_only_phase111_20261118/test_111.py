"""111: Compile actual production L1CardAdd, L1CardCollection and unique registration
against typed Java 21 test doubles. Inject packet failures after committed receipts.
No production DB/CacheD or integration validation is claimed.
"""
from pathlib import Path
import shutil,subprocess
W=Path(__file__).resolve().parents[2]
fixture=W/'docs/item_only_phase103_20261118/fixture_work/src'
if not fixture.is_dir():
    r=subprocess.run(['python3',str(W/'docs/item_only_phase103_20261118/test_103.py')],
                     capture_output=True,text=True)
    if r.returncode:raise RuntimeError('Phase103 fixture failed: '+r.stdout[-1500:]+r.stderr[-1500:])
out=W/'docs/item_only_phase111_20261118/fixture'
if out.exists():shutil.rmtree(out)
src=out/'src'
shutil.copytree(fixture,src)

def put(path,s):
 p=src/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,encoding='utf8')

put('l1j/server/server/model/item/Card/L1CardAdd.java',(W/'src/l1j/server/server/model/item/Card/L1CardAdd.java').read_text())
put('l1j/server/server/model/item/Card/L1CardCollection.java',(W/'src/l1j/server/server/model/item/Card/L1CardCollection.java').read_text())
put('l1j/server/server/model/item/function/TransformationCollectionCards.java',(W/'src/l1j/server/server/model/item/function/TransformationCollectionCards.java').read_text())
user_stub=(src/'l1j/server/server/model/network/core/cs/packet/User.java').read_text()
assert 'return new L1InventoryTransaction(pc);' in user_stub
user_stub=user_stub.replace('public class User {','public class User {public static Runnable ON_GATE;')
user_stub=user_stub.replace('return new L1InventoryTransaction(pc);',
    'L1InventoryTransaction tx=new L1InventoryTransaction(pc);if(ON_GATE!=null)ON_GATE.run();return tx;')
put('l1j/server/server/model/network/core/cs/packet/User.java',user_stub)
put('l1j/server/server/model/Instance/L1PcInstance.java','''package l1j.server.server.model.Instance;
import l1j.server.server.Account;import java.util.ArrayList;
public class L1PcInstance {
 public Account acc=new Account(); public L1ItemInstance selected;
 public ArrayList<String> messages=new ArrayList<>(); public int bonuses,stats;public boolean failStat;
 private int bcolY,bcolM,bcolL,bcolH;
 public Account getAccount(){return acc;}public int getId(){return 1234;}
 public L1ItemInstance getCollecItem(){return selected;}public void setCollecItem(L1ItemInstance i){selected=i;}
 public int getBcol_y(){return bcolY;}public void setBcol_y(int v){bcolY=v;}
 public int getBcol_m(){return bcolM;}public void setBcol_m(int v){bcolM=v;}
 public int getBcol_l(){return bcolL;}public void setBcol_l(int v){bcolL=v;}
 public int getBcol_h(){return bcolH;}public void setBcol_h(int v){bcolH=v;}
 public boolean isDead(){return false;}
 public void addDmgup(int v){if(failStat)throw new IllegalStateException("injected game stat failure");stats+=v;}
 public void addBowDmgup(int v){stats+=v;} public void addDamageReduction(int v){stats+=v;}
 public void addHitup(int v){stats+=v;} public void addBowHitup(int v){stats+=v;}
 public void addMagicHit(int v){stats+=v;} public void addMaxHp(int v){stats+=v;}
 public void addMaxMp(int v){stats+=v;} public void add_melee_critical_rate(int v){stats+=v;}
 public void add_magic_critical_rate(int v){stats+=v;}public void add_missile_critical_rate(int v){stats+=v;}
 public final Ability ability=new Ability();public final Resistance resist=new Resistance();
 public Ability getAbility(){return ability;}public Resistance getResistance(){return resist;}
 public static class Ability {public int sp;public void addSp(int v){sp+=v;}}
 public static class Resistance{public int mr;public void addMr(int v){mr+=v;}}
}
''')
put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java','''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_SystemMessage {public static boolean FAIL_SEND;
 public static void send(L1PcInstance p,String s){if(FAIL_SEND)throw new IllegalStateException("injected chat failure");p.messages.add(s);}}
''')
put('l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java','''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_PacketBox {public static final int GREEN_MESSAGE=1;
 public static boolean FAIL_SEND;public static void send(L1PcInstance p,int kind,String s){if(FAIL_SEND)throw new IllegalStateException("injected packet box failure");p.messages.add(s);}}
''')
for name in ('SC_SPMR','SC_OwnCharStatus'):
 put(f'l1j/server/server/model/network/core/sc/packet/{name}.java',f'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class {name} {{public static boolean FAIL_SEND;public static void send(L1PcInstance p){{if(FAIL_SEND)throw new IllegalStateException("injected {name} failure");}}}}
''')
put('l1j/server/server/model/network/core/sc/packet/SC_NPCTalkReturn.java','''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;import java.util.List;
public class SC_NPCTalkReturn {public static boolean FAIL_SEND;
 public static void sendList(L1PcInstance p,int i,String name,List<String> arg){if(FAIL_SEND)throw new IllegalStateException("injected talk failure");}
}
''')
put('l1j/server/server/model/network/core/sc/packet/SC_ChatPacket.java','''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_ChatPacket {public static boolean FAIL_SEND;
 public static void sendDirect(L1PcInstance p,L1PcInstance t,String s){if(FAIL_SEND)throw new IllegalStateException("injected unique message failure");p.messages.add(s);}}
''')
# L1SkillTree typed stub simulates a UI renderer that may fail after registration.
put('l1j/server/server/model/item/Card/L1SkillTree.java','''package l1j.server.server.model.item.Card;
import l1j.server.server.model.Instance.L1PcInstance;
public class L1SkillTree {public static boolean FAIL_UI;
 static void render(L1PcInstance p,int n){if(FAIL_UI)throw new IllegalStateException("injected tree UI failure");p.bonuses=n;}
 public static void Info(L1PcInstance p){render(p,11);}public static void Info2(L1PcInstance p){render(p,12);}
 public static void Info3(L1PcInstance p){render(p,13);}public static void Info5(L1PcInstance p){render(p,14);}}
''')
put('l1j/server/server/model/item/Card/CardRegistration111Test.java','''package l1j.server.server.model.item.Card;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.*;
public class CardRegistration111Test {
 static int checks;
 static void ok(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static L1PcInstance pc(int item){L1PcInstance pc=new L1PcInstance();pc.selected=new L1ItemInstance(item);return pc;}
 static void go(int tier,L1PcInstance pc){switch(tier){case 1->L1CardAdd.legend1(pc);case 3->L1CardAdd.hero1(pc);case 5->L1CardAdd.myth(pc);}}
 public static void main(String[] args){
   for(int tier:new int[]{1,3,5}) for(int fail:new int[]{1,2,3,4,5}){
     L1PcInstance p=pc(tier==1?875640306:tier==3?875640315:875640296);
     Account a=p.acc;int beforeScore=tier==1?22:tier==3?35:10;
     if(tier==1)a.setInfo_col_l(beforeScore);else if(tier==3)a.setInfo_col_h(beforeScore);else a.setInfo_col_m(beforeScore);
     if(fail==1)SC_SystemMessage.FAIL_SEND=true;
     if(fail==2)SC_SPMR.FAIL_SEND=true;
     if(fail==3)SC_OwnCharStatus.FAIL_SEND=true;
     if(fail==4)SC_PacketBox.FAIL_SEND=true;
     go(tier,p);L1InventoryTransaction tx=L1InventoryTransaction.last;L1ItemInstance item=tx.item;
     ok(item.count==1,"deferred before commit");
     tx.finish(true);
     SC_SystemMessage.FAIL_SEND=false;SC_SPMR.FAIL_SEND=false;SC_OwnCharStatus.FAIL_SEND=false;SC_PacketBox.FAIL_SEND=false;
     int score=tier==1?a.getInfo_col_l():tier==3?a.getInfo_col_h():a.getInfo_col_m();
     ok(item.count==0,"committed item debit");ok(score==beforeScore+1,"score committed");
     int card=tier==1?a.getInfo_ds_poly():tier==3?a.getInfo_heaven_poly():a.getInfo_class_poly();
     ok(card==1,"card count committed");
     ok(tier==1?p.getBcol_l()==1:tier==3?p.getBcol_h()==1:p.getBcol_m()==1,"bonus flag applied");
     ok(p.stats>0,"all stat changes applied");
     ok(tx.sql.contains("info_col_"),"SQL first unlock");
   }
   for(int fail=0;fail<4;fail++){
     L1PcInstance p=pc(875640331);L1ItemInstance item=p.selected;
     if(fail==0||fail==2)L1SkillTree.FAIL_UI=true;
     if(fail==1||fail==2)SC_SystemMessage.FAIL_SEND=true;
     L1CardAdd.SkillTree(p);L1InventoryTransaction tx=L1InventoryTransaction.last;tx.finish(true);
     L1SkillTree.FAIL_UI=false;SC_SystemMessage.FAIL_SEND=false;
     ok(p.acc.getTree_iceq()==1,"guardian persisted bit");ok(item.count==0,"guardian item consumed once");
     ok(p.messages.size()==(fail==1||fail==2?0:1),"guardian chat independent from UI");
   }
   // An actual stat mutation failure is NOT cosmetic: the callback must still
   // throw, allowing the true economy-state quarantine policy to run.
   L1PcInstance p=pc(875640306);p.acc.setInfo_col_l(22);p.failStat=true;
   L1CardAdd.legend1(p);L1InventoryTransaction tx=L1InventoryTransaction.last;
   boolean threw=false;try{tx.finish(true);}catch(IllegalStateException x){threw=x.getMessage().contains("stat failure");}
   ok(threw,"gameplay stat application exception must propagate");
   System.out.println("PASS 111 card+guardian compiled-production checks="+checks);
 }
}
''')
put('l1j/server/server/model/item/function/UniqueRegistration111Test.java','''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
public class UniqueRegistration111Test {
 static int n;static void check(boolean x,String msg){n++;if(!x)throw new AssertionError(msg);}
 public static void main(String[] args){
   for(int failure=0;failure<3;failure++){
     L1PcInstance p=new L1PcInstance();L1ItemInstance item=new L1ItemInstance(875640295);
     if(failure==0)SC_ChatPacket.FAIL_SEND=true;
     if(failure==1)SC_SPMR.FAIL_SEND=true;
     check(TransformationCollectionCards.handleUnlockCollection(new ItemActionContext(p,item))==ItemActionResult.COMPLETE,"unique submit");
     L1InventoryTransaction tx=L1InventoryTransaction.last;tx.finish(true);
     SC_ChatPacket.FAIL_SEND=false;SC_SPMR.FAIL_SEND=false;
     check(item.count==0,"unique debit");check(p.acc.getInfo_kir_poly()==1 && p.acc.getInfo_col_y()==1,"unique account state");
     check(p.getBcol_y()==1 && p.stats>0,"unique bonus stats");
   }
   // The snapshot read must be revalidated once the owner gate is entered.
   for(int change=0;change<3;change++){
     L1PcInstance p=new L1PcInstance();L1ItemInstance item=new L1ItemInstance(875640295);
     final int kind=change;
     l1j.server.server.model.network.core.cs.packet.User.ON_GATE=()->{
       if(kind==0)p.acc.setInfo_kir_poly(7);
       else if(kind==1)p.acc.setInfo_col_y(4);
       else p.acc=new l1j.server.server.Account();
     };
     check(TransformationCollectionCards.handleUnlockCollection(new ItemActionContext(p,item))==ItemActionResult.COMPLETE,"pre-gate race return");
     l1j.server.server.model.network.core.cs.packet.User.ON_GATE=null;
     check(!L1InventoryTransaction.last.pending,"stale unique input never starts a receipt");
     check(item.count==1,"stale unique input preserves item");
     check(p.getBcol_y()==0,"stale unique input does not apply bonus");
   }
   System.out.println("PASS 111 unique compiled-production checks="+n);
 }
}
''')
classes=out/'classes';classes.mkdir(parents=True)
javafiles=sorted((src).rglob('*.java'))
r=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*[str(p) for p in javafiles]],capture_output=True,text=True)
if r.returncode:
 print(r.stdout,r.stderr);raise SystemExit('JAVAC failed')
print('PASS: actual production L1CardAdd, L1CardCollection, TransformationCollectionCards Java 21 compiled')
for cls in ('l1j.server.server.model.item.Card.CardRegistration111Test','l1j.server.server.model.item.function.UniqueRegistration111Test'):
 r=subprocess.run(['java','-cp',str(classes),cls],capture_output=True,text=True)
 print(r.stdout.strip())
 if r.returncode:print(r.stderr);raise SystemExit('JAVA TEST FAILED '+cls)
 print('Warning logs from injected faults:',r.stderr.count('WARNING:'),'(expected)')
