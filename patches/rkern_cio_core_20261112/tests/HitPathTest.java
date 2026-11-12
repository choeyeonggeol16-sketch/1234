import java.lang.reflect.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.character.Attack.Helper.*;
import l1j.server.server.model.character.action.CAttackAction.AttackTargetData;
import l1j.server.server.utils.CalcStat;

public class HitPathTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static class Inventory extends L1PcInventory {
  int weight;Inventory(L1PcInstance p){super(p);}
  @Override public int getWeight100(){return weight;}
 }
 static class Pc extends PhysicalDamageTest.Pc {
  Inventory inv=new Inventory(this);int melee=100,bow=90,hitUp=4,hitRate=5,bowUp=6,bowRate=7;
  @Override public L1PcInventory getInventory(){return inv;}
  @Override public int getBaseMeleeHitByLevelClass(){return melee;}
  @Override public int getBaseBowHitByLevelClass(){return bow;}
  @Override public int getHitup(){return hitUp;}
  @Override public int getHitRate(){return hitRate;}
  @Override public int getBowHitup(){return bowUp;}
  @Override public int getBowHitRate(){return bowRate;}
 }
 static AttackTargetData context(Pc p,UnarmedElementTest.Npc n,Pc target,boolean pcAttacker,boolean pcTarget,int weaponType)throws Exception {
  var c=AttackTargetData.class.getDeclaredConstructor();c.setAccessible(true);var attack=c.newInstance();
  UnarmedElementTest.set(attack,"_pc",pcAttacker?p:null);UnarmedElementTest.set(attack,"_npc",pcAttacker?null:n);
  UnarmedElementTest.set(attack,"_target",pcTarget?target:n);
  UnarmedElementTest.set(attack,"_targetPc",pcTarget?target:null);UnarmedElementTest.set(attack,"_targetNpc",pcTarget?null:n);
  UnarmedElementTest.set(attack,"_weaponType",weaponType);UnarmedElementTest.set(attack,"_weaponAddHit",8);UnarmedElementTest.set(attack,"_weaponEnchant",9);
  return attack;
 }
 static void fourPaths()throws Exception {
  Pc p=new Pc(),target=new Pc();p.getAbility().setBaseStr(20);p.getAbility().setBaseDex(25);
  var n=UnarmedElementTest.npc();n.getAbility().setBaseStr(20);n.getAbility().setBaseDex(25);
  Field npcAc=L1Character.class.getDeclaredField("ac");npcAc.setAccessible(true);npcAc.set(n,new AC(null));
  target.getAC().setAc(-140);n.getAC().setAc(-140);
  String[] names={"calcPcPcHit","calcPcNpcHit","calcNpcPcHit","calcNpcNpcHit"};
  for(int path=0;path<4;path++)for(boolean ranged:new boolean[]{false,true})for(boolean result:new boolean[]{false,true}){
   boolean pcAttacker=path<2,pcTarget=(path%2==0);n.template.set_ranged(ranged?10:1);
   AttackTargetData a=context(p,n,target,pcAttacker,pcTarget,ranged?20:4);
   Method m=AttackTargetData.class.getDeclaredMethod(names[path]);m.setAccessible(true);HitEvasionModel.reset(result);
   check((Boolean)m.invoke(a)==result,"roll result not propagated "+names[path]);
   check(HitEvasionModel.calculations==1&&HitEvasionModel.rolls==1&&HitEvasionModel.defenseCalls==1,"duplicate/missing normal hit boundary "+names[path]);
   check(HitEvasionModel.ac==140&&HitEvasionModel.ranged==ranged,"wrong AC/range "+names[path]);
   double expected=pcAttacker?L1AttackHelper.calcPcHitH(p,ranged,8,9,0):L1AttackHelper.calcNpcHitH(n,ranged);
   check(HitEvasionModel.h==expected,"wrong H "+names[path]);
   check(HitEvasionModel.bonus==0,"unbuffed Maan nonzero");
   if(!pcTarget)check(HitEvasionModel.dg==0&&HitEvasionModel.er==0,"NPC acquired PC evasion");
  }
  target.getAC().setAc(7);n.getAC().setAc(7);
  for(int path=0;path<4;path++){
   Method m=AttackTargetData.class.getDeclaredMethod(names[path]);m.setAccessible(true);HitEvasionModel.reset(true);
   m.invoke(context(p,n,target,path<2,path%2==0,4));check(HitEvasionModel.ac==0,"positive AC treated as negative "+names[path]);
  }
  System.out.println("PASS real AttackTargetData four ordinary branches x melee/ranged x HIT/MISS: one model evaluation/roll; signed positive AC across four paths");
 }
 static void hitAggregation(){
  Pc p=new Pc();p.getAbility().setBaseStr(20);p.getAbility().setBaseDex(25);
  for(boolean ranged:new boolean[]{false,true})for(int weight:new int[]{0,33,34,49,50,66,67,100})for(int loss:new int[]{-1,0,3}){
   p.inv.weight=weight;int stat=ranged?CalcStat.원거리명중(25):CalcStat.근거리명중(20);
   int base=ranged?90:100,misc=ranged?13:9;
   int penalty=weight>=67?5:weight>=50?3:weight>=34?1:0;
   int expected=base+stat+misc+8+(9>>1)-penalty-Math.max(0,loss);
   check(L1AttackHelper.calcPcHitH(p,ranged,8,9,loss)==expected,"hit aggregation changed");
  }
  p.hitUp=Integer.MAX_VALUE;p.hitRate=Integer.MAX_VALUE;
  check(L1AttackHelper.calcPcHitH(p,false,Integer.MAX_VALUE,Integer.MAX_VALUE,0)==300,"positive H overflow became zero");
  p.hitUp=Integer.MIN_VALUE;p.hitRate=Integer.MIN_VALUE;
  check(L1AttackHelper.calcPcHitH(p,false,Integer.MIN_VALUE,Integer.MIN_VALUE,0)==0,"negative H overflow became positive");
  check(L1AttackHelper.calcPcHitH(null,false,0,0,0)==0,"null PC H");
  System.out.println("PASS actual melee/ranged H aggregation: 48 weight/durability combinations, STR/DEX and weapon bonus once, 0..300 long saturation");
 }
 public static void main(String[] args)throws Exception{fourPaths();hitAggregation();}
}
