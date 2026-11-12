import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import gnu.trove.map.TIntObjectMap;
import l1j.server.DatabaseFactory;
import l1j.server.MJPassiveSkill.*;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.character.action.CAttackAction.AttackTargetData;
import l1j.server.server.model.character.Attack.Helper.*;
import l1j.server.server.model.character.skill.*;
import l1j.server.server.model.character.skill.core.RecastPolicy;
import l1j.server.server.templates.*;
import l1j.server.server.utils.MJCommons;

public class UnarmedElementTest {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 static void set(Object o,String name,Object value)throws Exception{field(AttackTargetData.class,name).set(o,value);}
 static Method method(Class<?> c,String n,Class<?>... args)throws Exception{Method m=c.getDeclaredMethod(n,args);m.setAccessible(true);return m;}
 static class Pc extends PhysicalDamageTest.Pc {
  int sprite;boolean passive;
  @Override public int getCurrentSpriteId(){return sprite;}
  @Override public boolean isPassive(int id){return passive&&id==MJPassiveID.RESIST_ELEMENT.toInt();}
 }
 static class Npc extends L1MonsterInstance {
  L1Npc template;Resistance res;Set<Integer> effects;int calls,lastDamage;
  Npc(){super(null);}
  @Override public L1Npc getNpcTemplate(){return template;}
  @Override public Resistance getResistance(){return res;}
  @Override public int getLevel(){return 80;}
  @Override public boolean hasSkillEffect(int id){return effects!=null&&effects.contains(id);}
  @Override public void removeSkillEffect(int id){if(effects.remove(id))SkillStopHandlers.dispatch(this,id);}
  @Override public void receiveDamage(L1Character attacker,int damage){calls++;lastDamage=damage;}
 }
 static Npc npc()throws Exception{
  Npc n=(Npc)HpMpTest.unsafe(Npc.class);n.template=new L1Npc();n.template.set_size("small");n.res=new Resistance(n);n.effects=new HashSet<>();field(L1Object.class,"_loc").set(n,new L1Location());
  Constructor<Ability> c=Ability.class.getDeclaredConstructor(L1Character.class);c.setAccessible(true);field(L1Character.class,"ability").set(n,c.newInstance(n));return n;
 }
 static Pc pc(){Pc p=new Pc();p.setName("element-test");p.setMaxHp(30000);p.setCurrentHp(30000);return p;}
 static AttackTargetData attack(Pc attacker,L1Character target)throws Exception {
  Constructor<AttackTargetData> c=AttackTargetData.class.getDeclaredConstructor();c.setAccessible(true);AttackTargetData hit=c.newInstance();
  set(hit,"_pc",attacker);set(hit,"_target",target);set(hit,"_calcType",target instanceof L1PcInstance?1:2);
  set(hit,target instanceof L1PcInstance?"_targetPc":"_targetNpc",target);
  set(hit,"weapon",attacker.held);set(hit,"_isHit",true);set(hit,"_weaponSmall",attacker.held==null?0:100);set(hit,"_weaponLarge",attacker.held==null?0:100);
  if(attacker.held!=null){set(hit,"_weaponType",4);set(hit,"_weaponType2",1);}
  return hit;
 }
 static void polyFixture()throws Exception {
  Class.forName("org.h2.Driver");
  try(Connection c=DriverManager.getConnection("jdbc:h2:mem:poly;DB_CLOSE_DELAY=-1")){
   c.createStatement().execute("CREATE TABLE IF NOT EXISTS polymorphs(id INT)");
   Field pool=field(DatabaseFactory.class,"DATABASE_POOL");Object before=pool.get(null);pool.set(null,new HpMpPersistenceTest.Source(c));
   try{
    PolyTable table=PolyTable.getInstance();
    @SuppressWarnings("unchecked") TIntObjectMap<L1PolyMorph> index=(TIntObjectMap<L1PolyMorph>)field(PolyTable.class,"_polyIdIndex").get(table);
    index.put(111,new L1PolyMorph(1,"bow-only",111,1,256,0,true,0,0));
    index.put(112,new L1PolyMorph(2,"bow-and-sword",112,1,258,0,true,0,0));
   }finally{pool.set(null,before);}
  }
 }
 static void bareHands()throws Exception {
  Pc p=pc(),victim=pc();Npc n=npc();n.template.set_damagereduction(5000);
  p.addDmgup(300);p.getAbility().setBaseStr(127);p.getResistance().addPVPweaponTotalDamage(100);
  p.effects.addAll(Set.of(L1SkillId.BERSERKERS,L1SkillId.BURNING_SPIRIT,L1SkillId.SOUL_OF_FLAME,L1SkillId.IllUSION_AVATAR));
  victim.armor=5000;victim.getResistance().addcalcPcDefense(100);boolean one=false,two=false;
  for(L1Character target:new L1Character[]{victim,n}){
   AttackTargetData hit=attack(p,target);set(hit,"_statusDamage",500);set(hit,"_weaponAddDmg",500);
   for(int i=0;i<500;i++){
    int d=hit.calcDamage();check(d==1||d==2,"bare-hand damage entered stat/gear/buff/DR path: "+d);one|=d==1;two|=d==2;
    check(!field(AttackTargetData.class,"_isCritical").getBoolean(hit),"bare-hand critical proc");
   }
  }
  check(one&&two,"bare-hand dice lost a value");
  Method hitRoll=method(AttackTargetData.class,"calcPcPcHit");AttackTargetData hit=attack(p,victim);victim.getAC().setAc(-150);
  int hits=0;for(int i=0;i<1000;i++)if((Boolean)hitRoll.invoke(hit))hits++;
  check(hits>0&&hits<1000,"bare-hand hit became guaranteed or impossible");
  System.out.println("PASS U01-U06/U09: actual PC/PC and PC/NPC bare-hand damage 1/2 despite STR127, large bonuses/buffs/DR; existing hit roll still misses");
 }
 static void delivery()throws Exception {
  Pc p=pc(),v=pc();p.sprite=15986;v.effects.add(L1SkillId.LUCIFER); // Ordinary hits get sprite +2 and Lucifer reduction.
  for(int i=0;i<100;i++){
   AttackTargetData hit=attack(p,v);int d=hit.calcDamage(),before=v.getCurrentHp();hit.commit();
   check(before-v.getCurrentHp()==d,"victim post-processing changed bare-hand damage");
  }
  AttackTargetData miss=attack(p,v);set(miss,"_isHit",false);int before=v.getCurrentHp();miss.commit();check(v.getCurrentHp()==before,"miss committed damage");
  Npc n=npc();AttackTargetData npcHit=attack(p,n);int d=npcHit.calcDamage();npcHit.commit();check(n.calls==1&&n.lastDamage==d,"NPC delivery rerolled/reduced damage");
  before=v.getCurrentHp();int ordinary=92-v.getBaseAbility().getPVP대미지리덕션();v.receiveDamage(p,100,true);
  check(before-v.getCurrentHp()==ordinary,"an unarmed caster's ordinary spell acquired fist immunity to DR");
  v.effects.add(L1SkillId.ABSOLUTE_BARRIER);before=v.getCurrentHp();v.receiveUnarmedPhysicalDamage(p,2);
  check(v.getCurrentHp()==before,"queued bare-hand hit bypassed absolute barrier");
  v.effects.remove(L1SkillId.ABSOLUTE_BARRIER);
  System.out.println("PASS real attack -> commit -> victim controller -> atomic HP: fist provenance survives final PVP modifiers; misses and NPC delivery preserved");
 }
 static class BlockingPc extends Pc {
  final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);final AtomicBoolean first=new AtomicBoolean();final AtomicInteger delivered=new AtomicInteger();
  @Override public void GotUnarmedPhysicalDamage(L1Character a,int d){
   if(first.compareAndSet(false,true)){entered.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}}
   delivered.incrementAndGet();super.GotUnarmedPhysicalDamage(a,d);
  }
 }
 static void overflow()throws Exception {
  BlockingPc v=new BlockingPc();v.setMaxHp(30000);v.setCurrentHp(30000);Pc a=pc();a.sprite=15986;
  Thread owner=new Thread(()->v.receiveUnarmedPhysicalDamage(a,2));owner.start();check(v.entered.await(2,TimeUnit.SECONDS),"victim not blocked");
  try{for(int i=0;i<80;i++)v.receiveUnarmedPhysicalDamage(a,2);}
  finally{v.release.countDown();}
  owner.join(3000);check(!owner.isAlive()&&v.delivered.get()==81&&v.getCurrentHp()==30000-162,"fist provenance/HP lost in saturated ring overflow");
  System.out.println("PASS existing controller ring/overflow preserves 81 bare-hand events and exact HP without a new queue");
 }
 static void bowMorph()throws Exception {
  check(!L1PolyMorph.canAttackWithoutWeapon(111)&&L1PolyMorph.canAttackWithoutWeapon(112)&&L1PolyMorph.canAttackWithoutWeapon(999),"bow-only mask policy");
  Pc a=pc(),v=pc();a.sprite=111;a.setHeading(5);AttackTargetData hit=attack(a,v);
  check(!hit.calcHit()&&hit.calcDamage()==0,"bow-only unarmed attack admitted");hit.action();check(a.getHeading()==5,"bow-only rejection still entered swing animation");
  Object action=method(a.getCreatureController().getClass(),"attackAction").invoke(a.getCreatureController());
  boolean allowed=(Boolean)method(CAttackAction.class,"validateAttackPreconditions",L1PcInstance.class,int.class,boolean.class).invoke(action,a,1,false);
  check(!allowed,"packet/auto preconditions admitted bow-only fist attack");
  System.out.println("PASS U08: bow-only polymorph rejects attack before hit/empty swing, actionPc emits no animation; mixed/unknown masks retained");
 }
 static void add(Resistance r,int attr,int value){switch(attr){case 1:r.addEarth(value);break;case 2:r.addFire(value);break;case 4:r.addWater(value);break;case 8:r.addWind(value);break;}}
 static class CountingResistance extends Resistance {
  int reads;CountingResistance(L1Character c){super(c);}
  @Override public int getFire(){reads++;return super.getFire();}
  @Override public int getWater(){reads++;return super.getWater();}
  @Override public int getEarth(){reads++;return super.getEarth();}
  @Override public int getWind(){reads++;return super.getWind();}
 }
 static void elementVectors()throws Exception {
  int vectors=0;
  for(int band=0;band<4;band++)for(int level=1;level<=5;level++)for(int er:new int[]{-128,-100,-20,0,19,20,21,40,60,79,80,81,90,99,100,101,110,130}){
   Pc v=pc();int channel=new int[]{2,4,8,1}[band];add(v.getResistance(),channel,er);
   int actualEr=switch(channel){case 1->v.getResistance().getEarth();case 2->v.getResistance().getFire();case 4->v.getResistance().getWater();default->v.getResistance().getWind();};
   int base=2*level-1,expected=(int)((long)base*(100L-Math.min(100,actualEr))/100);
   int value=(int)L1WeaponAttrEnchantHelper.calcAttrEnchantDamage(band*5+level,0,v,null);
   check(value==expected,"element percent/channel/floor mismatch");vectors++;
   Npc n=npc();add(n.res,channel,er);check((int)L1WeaponAttrEnchantHelper.calcAttrEnchantDamage(band*5+level,0,null,n)==expected,"NPC existing no-weakness mapping differs");
  }
  check(SpellDamageFormula.coefficientA100(45,0,0,-128)>SpellDamageFormula.coefficientA100(45,0,0,-100),"negative spell weakness clamped at -100");
  check(SpellDamageFormula.coefficientA100(45,0,0,100)==SpellDamageFormula.coefficientA100(45,0,0,130),"spell ER100 cap");
  check(SpellDamageFormula.calculate(10,SpellDamageFormula.coefficientA100(45,0,0,100),100,0,0,0,false)>0,"ER100 zeroed INT-amplified spell");
  System.out.println("PASS E01-E06/E09/E11: "+vectors+" weapon element/channel vectors and matching NPC mapping; signed spell weakness and ER100 nonzero INT damage");
 }
 static void singleApplication()throws Exception {
  for(int level:new int[]{5,10,15,20}){
   Pc a=pc(),v=pc();v.level=60;a.held=new L1ItemInstance();a.effects.add(L1SkillId.SOUL_OF_FLAME);
   CountingResistance res=new CountingResistance(v);res.addAllNaturalResistance(40);field(L1Character.class,"resistance").set(v,res);
   AttackTargetData hit=attack(a,v);set(hit,"_weaponAttrLevel",level);
   int d=hit.calcDamage();check(d==105&&res.reads==1,"weapon bonus not 5 or elemental reduction applied twice: "+d+" reads="+res.reads);
   int hp=v.getCurrentHp();hit.commit();check(v.getCurrentHp()==hp-105&&res.reads==1,"receiveDamage applied elemental resistance again");
  }
  Pc v=pc();v.passive=true;v.getResistance().addAllNaturalResistance(20);v.getResistance().addFire(10);
  check(v.getResistance().getFire()==35&&v.getResistance().getWater()==25,"equipment/buff/passive same effect counted twice");
  v.getResistance().addFire(-10);v.getResistance().addAllNaturalResistance(-20);check(v.getResistance().getFire()==5,"effect removal duplicated passive");
  System.out.println("PASS E07/E13-E16: physical base100 + resisted bonus5 stays105 through final HP, exactly one element read; four independent channels and additive effect roundtrip");
 }
 static class FallDown extends L1SkillUse {
  Pc caster;@Override public L1Character hdUser(){return caster;}@Override public L1PcInstance hdPlayer(){return caster;}
  @Override public int hdSkillId(){return L1SkillId.ELEMENTAL_FALL_DOWN;}@Override public int hdGetBuffIconDuration(){return 10;}
 }
 static void castFall(Method handler,FallDown su,Npc target)throws Exception {
  handler.invoke(null,su,target,null,0,null);
  target.effects.add(L1SkillId.ELEMENTAL_FALL_DOWN); // Processor duration installation after each handler.
 }
 static void npcRecast()throws Exception {
  FallDown su=new FallDown();su.caster=pc();su.caster.setElfAttr(2);Npc target=npc();
  Method handler=method(SkillHandlers.class,"onElementalFallDown",L1SkillUse.class,L1Character.class,L1Magic.class,int.class,Iterator.class);
  castFall(handler,su,target);
  check(!RecastPolicy.INSTANCE.refreshAndSkipFurther(su,target),"recast test not in real exception path");
  castFall(handler,su,target);check(target.res.getFire()==-50,"NPC same debuff applied twice: "+target.res.getFire());
  su.caster.setElfAttr(4);castFall(handler,su,target);
  check(target.res.getFire()==0&&target.res.getWater()==-50,"recast did not undo previous element channel");
  target.removeSkillEffect(L1SkillId.ELEMENTAL_FALL_DOWN);check(target.res.getWater()==0,"debuff expiration left residual resistance");
  System.out.println("PASS E14 actual NPC handler/recast exception/stop handler: repeated -50 remains -50; channel switch restores old channel; expiry restores baseline");
 }
 static void legacyBounds()throws Exception {
  Pc caster=pc(),v=pc();L1Magic magic=new L1Magic(caster,v,true);Method fallback=method(L1Magic.class,"calcAttrResistance",int.class);
  v.getResistance().addFire(40);check((Double)fallback.invoke(magic,L1Skills.ATTR_FIRE)==.18,"legacy spell integer division lost fraction");
  v.getResistance().addFire(60);double at100=(Double)fallback.invoke(magic,L1Skills.ATTR_FIRE);v.getResistance().addFire(30);
  check((Double)fallback.invoke(magic,L1Skills.ATTR_FIRE)==at100,"legacy spell ER100 cap");
  double before=MJCommons.MCS_DEFENCE_ATTR_WEIGHT;try{MJCommons.MCS_DEFENCE_ATTR_WEIGHT=.01;
   check(MJCommons.getAttrDamage(9,40)==5&&MJCommons.getAttrDamage(9,-20)==10,"configured proc percentages");
   check(MJCommons.getAttrDamage(9,100)==0&&MJCommons.getAttrDamage(9,130)==0,"proc resistance past100 produced negative damage");
  }finally{MJCommons.MCS_DEFENCE_ATTR_WEIGHT=before;}
  v.getResistance().addFire(-127);v.getResistance().addFire(100);
  check(L1WeaponSkill.calcDamageReduction(v,100,L1Skills.ATTR_FIRE)==0,"legacy weapon proc ER100");
  v.getResistance().addFire(30);check(L1WeaponSkill.calcDamageReduction(v,100,L1Skills.ATTR_FIRE)==0,"legacy weapon proc ER>100 became negative");
  System.out.println("PASS distinct legacy spell 45% curve retains fractions/caps; configured item proc cap/weakness; no physical-percent formula substituted into INT spells");
 }
 static void npcPhysicalPaths()throws Exception {
  Npc a=npc(),n=npc();Pc p=pc();
  a.getAbility().setBaseStr(20);n.template.set_damagereduction(0);
  for(L1Character target:new L1Character[]{p,n}){
   CountingResistance r=new CountingResistance(target);r.addAllNaturalResistance(40);
   if(target==n)n.res=r;else field(L1Character.class,"resistance").set(target,r);
   Constructor<AttackTargetData> c=AttackTargetData.class.getDeclaredConstructor();c.setAccessible(true);AttackTargetData hit=c.newInstance();
   set(hit,"_npc",a);set(hit,"_target",target);set(hit,target==n?"_targetNpc":"_targetPc",target);set(hit,"_calcType",target==n?4:3);set(hit,"_isHit",true);
   check(hit.calcDamage()>0&&r.reads==0,"NPC physical base acquired player enchant resistance");
  }
  System.out.println("PASS E10/E17: actual NPC/PC and NPC/NPC ordinary physical paths do not apply player weapon enchant resistance");
 }
 public static void main(String[] args)throws Exception {
  java.util.logging.Logger.getLogger("l1j.server.server.model.character.action.CAttackAction").setLevel(java.util.logging.Level.OFF);
  polyFixture();
  switch(args.length==0?"all":args[0]){
   case "bare":bareHands();break;case "recast":npcRecast();break;case "legacy":legacyBounds();break;
   default:bareHands();delivery();overflow();bowMorph();elementVectors();singleApplication();npcRecast();legacyBounds();npcPhysicalPaths();
  }
  System.out.println("Production combat classes executed with disconnected PCs, local H2 polymorph table and NPC world/HP fixture; not Windows/client/production DB. U07 special NPC ID unverified.");
 }
}
