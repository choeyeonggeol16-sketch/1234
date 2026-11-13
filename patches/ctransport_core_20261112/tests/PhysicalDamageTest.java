import java.lang.reflect.*;
import java.util.*;
import l1j.server.MJPassiveSkill.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.character.Attack.Helper.*;
import l1j.server.server.model.character.action.CAttackAction.AttackTargetData;
import l1j.server.server.model.character.skill.L1SkillId;
public class PhysicalDamageTest {
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 static class Pc extends MagicStackRuntimeTest.Pc {
  int armor,level=80;double immune;boolean crash,fury;L1ItemInstance held;
  @Override public int getDamageReductionByArmor(){return armor;}
  @Override public int getDamageReductionIgnore(){return 0;}
  @Override public double getImmuneReduction(){return immune;}
  @Override public int getLevel(){return level;}
  @Override public int getBaseMeleeDmgByLevelClass(){return 0;}
  @Override public int getBaseBowDmgByLevelClass(){return 0;}
  @Override public L1ItemInstance getWeapon(){return held;}
  @Override public MJPassiveInfo getPassive(int id){
   if((crash&&id==MJPassiveID.CRASH.toInt())||(fury&&id==MJPassiveID.FURY.toInt()))return MJPassiveInfo.newInstance();
   return null;
  }
 }
 static int core(int min,int max,int bonus,int enchant,int extra,int loss,int attr,boolean armed,boolean dual,boolean db){
  return L1DamageCalcHelper.calcDamage(min,max,bonus,enchant,extra,0,0,0,loss,attr,
   dual?L1DamageCalcHelper.WeaponCritMode.DUAL_DOUBLE:L1DamageCalcHelper.WeaponCritMode.NONE,db,false,true,armed);
 }
 static void set(Object o,String field,Object v)throws Exception {Field f=AttackTargetData.class.getDeclaredField(field);f.setAccessible(true);f.set(o,v);}
 static void rates100()throws Exception {
  var type=AttackSkillChanceConfig.Settings.class;var fields=type.getRecordComponents();Class<?>[] types=new Class<?>[fields.length];Object[] values=new Object[fields.length];
  var current=AttackSkillChanceConfig.get();
  for(int i=0;i<fields.length;i++){types[i]=fields[i].getType();values[i]=fields[i].getAccessor().invoke(current);if(Set.of("burningChance","crashChance","furyChance").contains(fields[i].getName()))values[i]=100;}
  var next=type.getDeclaredConstructor(types).newInstance(values);Field f=AttackSkillChanceConfig.class.getDeclaredField("current");f.setAccessible(true);f.set(null,next);
 }
 static int full(Pc attacker,Pc target)throws Exception {
  var ctor=AttackTargetData.class.getDeclaredConstructor();ctor.setAccessible(true);var hit=ctor.newInstance();
  set(hit,"_pc",attacker);set(hit,"_targetPc",target);set(hit,"_target",target);set(hit,"_weaponSmall",100);
  set(hit,"_weaponType",4);set(hit,"_weaponType2",1);set(hit,"weapon",attacker.held);
  return hit.calcPcPcDamage();
 }
 public static void main(String[] args)throws Exception {
  java.util.logging.Logger.getLogger("l1j.server.server.model.character.action.CAttackAction").setLevel(java.util.logging.Level.OFF);
  java.util.logging.Logger.getLogger(AttackSkillChanceConfig.class.getName()).setLevel(java.util.logging.Level.OFF);
  check(core(1,1,0,0,0,5,0,true,false,false)==1,"damaged weapon minimum");
  check(core(1,1,0,0,7,5,2,true,false,false)==10,"floor applied to whole attack");
  check(core(0,0,0,0,0,5,0,false,false,false)==0,"unarmed forced minimum");
  check(core(10,10,2,3,7,0,5,true,true,true)==77,"dual/Double Break order changed");
  check(L1DamageCalcHelper.applyFinalPhysicalMultipliers(90,true,false)==135,"burning DR order");
  check(L1DamageCalcHelper.applyFinalPhysicalMultipliers(140,false,true)==280,"Fury whole hit");
  check(L1DamageCalcHelper.applyFinalPhysicalMultipliers(130,false,true)==260,"Fury fixed DR order");
  check(L1DamageCalcHelper.applyFinalPhysicalMultipliers(91,true,true)==272,"per-stage floor");
  check(L1DamageCalcHelper.applyFinalPhysicalMultipliers(-10,true,true)==0,"blocked damage resurrected");
  check(L1DamageCalcHelper.applyFinalPhysicalMultipliers(Integer.MAX_VALUE,true,true)==Integer.MAX_VALUE,"multiplier overflow");
  System.out.println("PASS armed-core minimum 1, unarmed 0, additive separation, dual/Double Break order, final multiplier arithmetic");
  for(int a=-1;a<=101;a++)for(int w=-1;w<=101;w++){
   int pc=Math.max(0,Math.min(100,a)),pw=Math.max(0,Math.min(100,w));
   check(L1SkillProcHelper.criticalChanceBasisPoints(a,w)==pc*100+pw*100-pc*pw,"critical union");
  }
  check(L1SkillProcHelper.criticalChanceBasisPoints(5,10)==1450,"14.5% critical");
  check(L1SkillProcHelper.criticalChanceBasisPoints(10,25)==3250,"32.5% critical");
  check(L1SkillProcHelper.criticalChanceBasisPoints(20,30)==4400,"44% critical");
  Pc p=new Pc();check(!L1SkillProcHelper.isSoulOfFlameMax(p,false),"SOF without buff");
  p.effects.add(L1SkillId.SOUL_OF_FLAME);
  check(!L1SkillProcHelper.isSoulOfFlameMax(p,false),"SOF unarmed maximum");p.held=new L1ItemInstance();
  check(L1SkillProcHelper.isSoulOfFlameMax(p,false)&&!L1SkillProcHelper.isSoulOfFlameMax(p,true),"SOF melee/ranged reversal");
  check(!L1SkillProcHelper.isSoulOfFlameMax(null,false),"null SOF");
  System.out.println("PASS 10609 crow probability combinations, fractional percentages, SOF buff and melee-only scope");
  Pc victim=new Pc();victim.armor=10;
  check(DamageReductionHelper.applyAllDamageReductions(victim,p,false,false,100,true,false)==135,"actual fixed armor DR insertion");
  victim.immune=.5;
  check(DamageReductionHelper.applyAllDamageReductions(victim,p,false,false,100,true,false)==67,"percent defense moved before multiplier");
  check(DamageReductionHelper.applyAllDamageReductions(victim,p,false,true,100,true,true)==45,"magic acquired physical multiplier");
  check(DamageReductionHelper.applyAllDamageReductions(victim,p,false,false,100)==45,"legacy no-proc path changed");
  victim.armor=100;check(DamageReductionHelper.applyAllDamageReductions(victim,p,false,false,100,true,true)==0,"blocked DR multiplied to positive");
  System.out.println("PASS actual fixed-DR -> multiplier -> immune sequence, magic/legacy exclusion and full blocks");
  rates100();Pc attacker=new Pc();attacker.held=new L1ItemInstance();attacker.effects.add(L1SkillId.SOUL_OF_FLAME);
  victim.armor=10;victim.immune=0;
  check(full(attacker,victim)==90,"ordinary actual attack baseline");
  attacker.effects.add(L1SkillId.BURNING_SPIRIT);check(full(attacker,victim)==135,"actual PC attack burning order");
  attacker.effects.remove(L1SkillId.BURNING_SPIRIT);attacker.crash=true;
  check(full(attacker,victim)==130,"actual Crash bonus changed");
  attacker.fury=true;check(full(attacker,victim)==260,"actual Fury still adds only Crash bonuses");
  attacker.effects.add(L1SkillId.BURNING_SPIRIT);check(full(attacker,victim)==390,"actual combined final multipliers");
  System.out.println("PASS actual AttackTargetData pipeline: baseline 90, Burning 135, Crash 130, Fury 260, combined 390");
  System.out.println("Production classes compiled/executed; test PCs control effects, procs and world side effects; no live Windows/client/DB.");
 }
}
