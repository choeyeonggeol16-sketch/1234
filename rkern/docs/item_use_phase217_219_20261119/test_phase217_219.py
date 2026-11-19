from pathlib import Path
import subprocess,tempfile,re
P=Path(__file__).resolve().parent; R=P.parents[1];D=R/'src/l1j/server/server/model/item/function'
# Reuse the prior test's shared API fixtures, not its test execution.
prev=(P.parent/'item_use_phase214_216_20261119/test_phase214_216.py').read_text()
code=prev.split("code='''",1)[1].split("'''+registry",1)[0]
code=code.replace('static int 엘븐스트라이크기본','static int 어드밴스레벨=80,어드밴스대기=10,데몰리션대기=10,데몰리션엠피=20,데페기본확률=100,내성배율=1,데몰리션대미지=2,어벤저배율=5,어벤저확률=100,어벤저대기=10; static double STUNHIT_TO_LEVEL11=1; static int 엘븐스트라이크기본')
code=code.replace('static final int 엘븐스트라이크대기','static final int 어드밴스대기=20,데몰리션대기=21,쉐도우제약=22,어벤저대기=23;static final int 엘븐스트라이크대기')
code=code.replace('int attempts,spent;', 'int attempts,spent,equipped;int getTypeEquipped(int a,int b){return b==6?equipped:0;}void updateItem(L1ItemInstance item,int col){}')
code=code.replace('if(id!=40319)throw new AssertionError();','if(id!=40321&&id!=41246)throw new AssertionError();')
code=code.replace('int type=2,mp=40;', '''int type=2,mp=40,hp=100,clan=1,teleports,deaths;boolean skillDelay;L1Party party=new L1Party();int getCurrentHp(){return hp;}int getMaxHp(){return 100;}void death(L1PcInstance p,boolean b){deaths++;} int getClanid(){return clan;}int getHeading(){return 0;}String getName(){return "target";}L1Party getParty(){return party;}void 어드밴스(int x,int y,int map,int heading,int effect,boolean a,boolean b){teleports++;}boolean isSkillDelay(){return skillDelay;}Resistance getResistance(){return new Resistance();}int getImpactUp(){return 0;}''')
code=code.replace('static final int ACTION_Wand=1;','static final int ACTION_Wand=1,ACTION_SkillBuff=2;')
code=code.replace('static int elven(Random r)', 'static int demolition(Random r){return 3000;}static int elven(Random r)')
code+='''
class Resistance {int getSpecialPierce(eKind k){return 0;}int getSpecialResistance(eKind k){return 0;}}
enum eKind{FEAR,ALL}
class L1Party{}
class L1PcInventory {static final int COL_CHARGE_COUNT=1;}
class L1Location {int getX(){return 1;}int getY(){return 1;}int getMapId(){return 4;}}
class L1Teleport {static L1Teleport getInstance(){return new L1Teleport();}L1Location 소환텔레포트(L1PcInstance p,int d){return new L1Location();}}
class SC_DoActionGFX {static void send(Object o,int a,int b){}static void broadcast(L1PcInstance p,int a){}}
class L1DamagePoison {static void doInfection(L1PcInstance p,L1PcInstance m,int a,int b,boolean c,int d){}}
'''
for name in ('AvengerItemEffect','DemolitionItemEffect','CallAdvanceItemEffect'):
 s=(D/(name+'.java')).read_text();s=s[s.index('final class '+name):].replace('COUNTER_MAGIC','99').replace('STATUS_FREEZE','98');code+=s
code+='''
public class Test {
 static void ck(boolean b){if(!b)throw new AssertionError();}
 static boolean use(int k,L1PcInstance p,L1PcInstance t){L1World.W.target=t;return switch(k){case 0->AvengerItemEffect.어벤저(p,3,p.inv.item);case 1->DemolitionItemEffect.데몰리션(p,3,p.inv.item);default->CallAdvanceItemEffect.콜어드밴스(p,3,p.inv.item);};}
 public static void main(String[] args){int n=0;
 for(int k=0;k<3;k++)for(int f=0;f<512;f++){
 L1PcInstance p=new L1PcInstance(),t=new L1PcInstance();p.id=1;p.type=k==0?4:k==1?7:0;p.inv.item.itemId=k==0?707779:k==1?707781:70721;p.inv.equipped=k==1?1:0;
 t.map=(f&1)!=0?5:4;t.dead=(f&2)!=0;t.teleport=(f&4)!=0;p.inv.consume=(f&8)==0;p.inv.check=(f&16)==0;p.nonAction=(f&32)!=0;p.invis=(f&64)!=0;p.mp=(f&128)!=0?0:40;p.dead=(f&256)!=0;int before=p.mp;
 boolean ok=use(k,p,t);ck(ok==(f==0));ck(p.inv.spent==(ok?(k==0?2:k==1?100:200):0));ck(p.mp==before-(ok?(k==0?25:20):0));ck((p.bind>0)==ok);ck((t.damage+t.deaths+t.bind+t.teleports>0)==ok);n++;
 }
 L1PcInstance p=new L1PcInstance(),t=new L1PcInstance();p.id=1;p.type=4;p.inv.item.itemId=707779;t.effects.add(99);p.inv.consume=false;ck(!use(0,p,t)&&t.effects.contains(99));p.inv.consume=true;ck(use(0,p,t)&&!t.effects.contains(99));
 System.out.println("PASS "+n+" target/debit/admission matrix outcomes; 2 counter-magic debit outcomes");
 }
}
'''
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp);(p/'Test.java').write_text(code);subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',tmp,str(p/'Test.java')],check=True);subprocess.run(['java','-cp',tmp,'Test'],check=True)
s=(D/'ItemActionDispatcher.java').read_text()
for id,c,m in [(707779,'AvengerItemEffect','어벤저'),(707781,'DemolitionItemEffect','데몰리션'),(70721,'CallAdvanceItemEffect','콜어드밴스')]:
 assert re.search(rf'case {id} -> {c}\.{m}\(pc, spellTargetId, item\)\s*\? ItemActionResult.COMPLETE : ItemActionResult.STOP;',s)
