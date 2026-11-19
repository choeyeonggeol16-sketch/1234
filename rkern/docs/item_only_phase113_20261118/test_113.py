"""Phase 113: compile actual ManaConsumables/DessertBuffConsumables with Java 21 stubs.
Exercise commit, rollback, packet failure independence and hard gameplay state errors.
This is a deterministic test for the handler boundary, NOT a real CacheD/SQL or game integration test.
"""
from pathlib import Path
import subprocess, textwrap, shutil, tempfile, os
root=Path(__file__).resolve().parent/'full'
if not (root/'src').is_dir():
 root=Path(__file__).resolve().parents[2]
original=os.environ.get('SOURCE_ROOT')
if original: root=Path(original)
tmp=Path(tempfile.mkdtemp(prefix='rkern_113_'))
src=tmp/'src';classes=tmp/'classes';classes.mkdir(parents=True)
def put(name,s):
 p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(textwrap.dedent(s).strip()+'\n',encoding='utf-8')
for n in ('ManaConsumables.java','DessertBuffConsumables.java'):
 p=src/'l1j/server/server/model/item/function'/n;p.parent.mkdir(parents=True,exist_ok=True)
 shutil.copy2(root/'src/l1j/server/server/model/item/function'/n,p)
put('l1j/server/server/model/Instance/L1ItemInstance.java','''
 package l1j.server.server.model.Instance;
 public final class L1ItemInstance { public final int id; public L1ItemInstance(int id){this.id=id;} }
''')
put('l1j/server/server/model/Instance/L1PcInstance.java','''
 package l1j.server.server.model.Instance;
 import java.util.*;
 public class L1PcInstance {
    public int mp=100,maxMp=5000; public int skillSet, skillRemove;
    public boolean FAIL_SET_MP, FAIL_SET_SKILL; public int hit,dmg,bowhit,bowdmg,magic,maxMpAdd;
    public final Map<Integer,Integer> active = new HashMap<>();
    public final Ability ability = new Ability();
    public int getId(){return 7;} public int getCurrentMp(){return mp;}
    public int getMaxMp(){return maxMp;} public int getTotalER(){return 9;}
    public void setCurrentMp(int v){if(FAIL_SET_MP)throw new IllegalStateException("gameplay MP failure");mp=v;}
    public boolean hasSkillEffect(int v){return active.containsKey(v);}
    public int getSkillEffectTimeSec(int v){return 99;}
    public void removeSkillEffect(int v){skillRemove=v;active.remove(v);}
    public void setSkillEffect(int v,int ms){if(FAIL_SET_SKILL)throw new IllegalStateException("gameplay skill failure");active.put(v,ms);skillSet=v;}
    public void addHitup(int v){hit+=v;} public void addDmgup(int v){dmg+=v;}
    public void addBowHitup(int v){bowhit+=v;} public void addBowDmgup(int v){bowdmg+=v;}
    public void addMaxMp(int v){maxMp+=v;maxMpAdd+=v;} public void addMagicHit(int v){magic+=v;}
    public Ability getAbility(){return ability;}
    public static final class Ability {
     public int str,dex,intel,sp;
     public void addAddedStr(int v){str+=v;}public void addAddedDex(int v){dex+=v;}
     public void addAddedInt(int v){intel+=v;}public void addSp(int v){sp+=v;}
    }
 }
''')
put('l1j/server/server/model/character/skill/L1SkillId.java','''
 package l1j.server.server.model.character.skill;
 public class L1SkillId {
  public static final int WITCH_MANA_POTION=101, 완력빙수=201, 민첩빙수=202, 지식빙수=203;
 }
''')
put('l1j/server/server/model/network/core/session/CIOClient.java','''
 package l1j.server.server.model.network.core.session;
 public class CIOClient {}
''')
put('l1j/server/server/model/item/function/ItemReceiptOperations.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 public class ItemReceiptOperations {
  public static Runnable callback; public static int submitted, committed, rolledBack;
  public static void UseDurableConsume(L1PcInstance pc,L1ItemInstance item,Runnable after){
    if(callback!=null)throw new AssertionError("double admission");submitted++;callback=after;
  }
  public static void finish(boolean commit){Runnable cb=callback;callback=null;if(cb==null)throw new AssertionError("missing receipt");
    if(commit){committed++;cb.run();}else{rolledBack++;}}
  public static void reset(){callback=null;submitted=0;committed=0;rolledBack=0;}
 }
''')
# A single packet fault bit per output channel, count all attempts independently.
packet_defs={
'SC_ServerMessage':('1','public static void send(L1PcInstance p,int code,String msg){if(code!=338 || !"$1084".equals(msg))throw new AssertionError("notice changed");attempt(1);}',),
'SC_SystemMessage':('2','public static void send(L1PcInstance p,String msg){attempt(2);}',),
'SC_SkillSound':('3','public static void send(L1PcInstance p,int id,int sound){if(sound!=190&&sound!=7952&&sound!=7954&&sound!=7956)throw new AssertionError("sound changed");attempt(3);} public static void broadcast(L1PcInstance p,int id,int sound){if(sound!=7952&&sound!=7954&&sound!=7956)throw new AssertionError("broadcast sound changed");attempt(4);}',),
'SC_OwnCharStatus2':('5','public static void send(L1PcInstance p){attempt(5);}',),
'SC_OwnCharAttrDef':('6','public static void send(L1PcInstance p){attempt(6);}',),
'SC_SPMR':('7','public static void send(L1PcInstance p){attempt(7);}',),
'SC_MpUpdate':('8','public static void send(L1PcInstance p,int curr,int max){attempt(8);}',),
'SC_PacketBox':('9','public static final int ER_UpDate=20;public static void send(L1PcInstance p,int type,int value){if(type!=ER_UpDate)throw new AssertionError("ER packet changed");attempt(9);}',),
}
for n,(idx,members) in packet_defs.items():
 put('l1j/server/server/model/network/core/sc/packet/'+n+'.java',f'''
 package l1j.server.server.model.network.core.sc.packet;
 import l1j.server.server.model.Instance.L1PcInstance;
 public final class {n} {{
  public static void attempt(int bit){{Fault.attempt(bit);}}
  {members}
 }}
 ''')
put('l1j/server/server/model/network/core/sc/packet/Fault.java','''
 package l1j.server.server.model.network.core.sc.packet;
 public final class Fault {
  public static int failMask; public static final int[] attempts = new int[10];
  public static void clear(){java.util.Arrays.fill(attempts,0);failMask=0;}
  public static void attempt(int bit){attempts[bit]++;if((failMask & (1<<bit))!=0)throw new IllegalStateException("transport failed bit="+bit);}
 }
''')
put('l1j/server/server/model/item/function/Check113.java','''
 package l1j.server.server.model.item.function;
 import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.character.skill.L1SkillId;
 import l1j.server.server.model.network.core.sc.packet.Fault;
 import java.util.logging.Level;
 public class Check113 {
  static int count=0;
  static void ck(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
  static int expectMin(int id){return switch(id){case 40067,41414 -> 15;case 40066,41413,41412 ->5;case 500220 ->1000;case 410002 ->44;case 40735 ->60;case 40042 ->50;case 41404 ->80;default ->-1;};}
  static int expectMax(int id){return switch(id){case 40067,41414 ->44;case 40066,41413 ->14;case 500220 ->1000;case 410002 ->44;case 40735 ->60;case 40042 ->50;case 41404 ->100;case 41412 ->20;default ->-1;};}
  static boolean hasSound(int id){return id==40067||id==41414||id==40066||id==41413||id==500220;}
  static void mana(int id,int mask,boolean commit){
    ItemReceiptOperations.reset();Fault.clear();Fault.failMask=mask;
    L1PcInstance pc=new L1PcInstance();
    ManaConsumables.use(pc,new L1ItemInstance(id),id,null);
    ck(ItemReceiptOperations.submitted==1,"mana submission "+id);
    ck(pc.mp==100,"early MP mutation "+id);
    ItemReceiptOperations.finish(commit);
    ck(ItemReceiptOperations.committed==(commit?1:0),"commit receipt");
    if(!commit){ck(pc.mp==100,"rollback MP "+id);ck(pc.active.isEmpty(),"rollback skill "+id);return;}
    ck(pc.mp-100>=expectMin(id)&&pc.mp-100<=expectMax(id),"MP gain id "+id+" got "+(pc.mp-100));
    ck(pc.hasSkillEffect(L1SkillId.WITCH_MANA_POTION)==(id==500220),"witch buff "+id);
    ck(Fault.attempts[1]==(hasSound(id)&&id!=500220?0:1) || (hasSound(id)&&id!=500220&&Fault.attempts[1]==0),"notice attempts "+id);
    ck(Fault.attempts[3]==(hasSound(id)?1:0),"sound attempts "+id);
  }
  static void dessert(int id,int mask,boolean commit){
    ItemReceiptOperations.reset();Fault.clear();Fault.failMask=mask;
    L1PcInstance pc=new L1PcInstance();
    pc.active.put(L1SkillId.완력빙수,900000); // previous flavor to be replaced except same kind
    DessertBuffConsumables.use(null,pc,new L1ItemInstance(id),id,null);
    ck(ItemReceiptOperations.submitted==1,"dessert submission");
    ck(pc.skillSet==0,"early skill effect");
    ItemReceiptOperations.finish(commit);
    if(!commit){ck(pc.skillSet==0,"rollback skill");ck(pc.hit==0&&pc.bowhit==0&&pc.maxMpAdd==0,"rollback stats");return;}
    int expected=id==31118?L1SkillId.완력빙수:id==31119?L1SkillId.민첩빙수:L1SkillId.지식빙수;
    ck(pc.skillSet==expected,"dessert skill "+id);
    ck(pc.active.get(expected)==900000,"dessert timer "+id);
    ck(id!=31118 || (pc.hit==5&&pc.dmg==3&&pc.ability.str==1),"strength stats");
    ck(id!=31119 || (pc.bowhit==5&&pc.bowdmg==3&&pc.ability.dex==1),"dexterity stats");
    ck(id!=31120 || (pc.maxMpAdd==50&&pc.ability.sp==1&&pc.magic==3&&pc.ability.intel==1),"intellect stats");
    ck(Fault.attempts[5]==1,"status packet attempted");
    ck(Fault.attempts[3]==1&&Fault.attempts[4]==1,"both sound channels attempted");
    ck(Fault.attempts[6]==(id==31119?1:0),"attr packet attempted");
    ck(Fault.attempts[9]==(id==31119?1:0),"ER packet attempted");
    ck(Fault.attempts[7]==(id==31120?1:0),"SPMR packet attempted");
    ck(Fault.attempts[8]==(id==31120?1:0),"MP status attempted");
  }
  static void hardFailures(){
    // Gameplay state exceptions must NOT be swallowed as optional presentation errors.
    ItemReceiptOperations.reset();Fault.clear();L1PcInstance pc=new L1PcInstance();pc.FAIL_SET_MP=true;
    ManaConsumables.use(pc,new L1ItemInstance(40042),40042,null);
    boolean threw=false;try{ItemReceiptOperations.finish(true);}catch(IllegalStateException expected){threw=true;}
    ck(threw,"MP gameplay failure masked");ck(Fault.attempts[1]==0,"notice sent before gameplay state");
    ItemReceiptOperations.reset();Fault.clear();pc=new L1PcInstance();pc.FAIL_SET_SKILL=true;
    DessertBuffConsumables.use(null,pc,new L1ItemInstance(31118),31118,null);
    threw=false;try{ItemReceiptOperations.finish(true);}catch(IllegalStateException expected){threw=true;}
    ck(threw,"Dessert gameplay failure masked");ck(Fault.attempts[3]==0,"sound sent before gameplay state");
  }
  public static void main(String[]args){
    java.util.logging.Logger.getLogger("l1j.server.server.model.item.function.ManaConsumables").setLevel(Level.OFF);
    java.util.logging.Logger.getLogger("l1j.server.server.model.item.function.DessertBuffConsumables").setLevel(Level.OFF);
    int[] mana={40067,41414,40066,41413,500220,410002,40735,40042,41404,41412};
    for(int id:mana){for(int mask=0;mask<16;mask++){mana(id,mask,true);mana(id,mask,false);}}
    int[] dessert={31118,31119,31120};
    for(int id:dessert){for(int mask=0;mask<1024;mask++){dessert(id,mask,true);dessert(id,mask,false);}}
    // Reuse blocked by its own active buff (without touching inventory).
    ItemReceiptOperations.reset();Fault.clear();L1PcInstance p=new L1PcInstance();
    p.active.put(L1SkillId.WITCH_MANA_POTION,30000);
    ManaConsumables.use(p,new L1ItemInstance(500220),500220,null);
    ck(ItemReceiptOperations.submitted==0,"witch cooldown bypass");ck(p.mp==100,"cooldown changed MP");
    hardFailures();
    System.out.println("PHASE113 JAVA 21 PASS: "+count+" assertions; mana 10 IDs; dessert 3 IDs; commit/rollback; independent packet faults; hard-state exceptions");
  }
 }
''')
# Compile the actual modified production classes and execute validation.
files=[str(x) for x in src.rglob('*.java')]
cmd=['javac','-encoding','UTF-8','-source','21','-target','21','-d',str(classes)]+files
r=subprocess.run(cmd,capture_output=True,text=True)
if r.returncode:
 print('JAVAC FAILED',r.stdout,r.stderr);raise SystemExit(r.returncode)
r=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.Check113'],capture_output=True,text=True)
print(r.stdout,end='')
if r.returncode:
 print('JAVA TEST FAILED:',r.stderr[-5000:]);raise SystemExit(r.returncode)
print('TYPED SOURCES: ManaConsumables.java, DessertBuffConsumables.java; Java 21 actual compilation PASS')
