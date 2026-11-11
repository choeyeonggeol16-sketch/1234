import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.character.action.CCreatureController;
import l1j.server.server.model.character.skill.L1SkillId;

/** Real compiled PC/controller/HP code. Disconnected PCs, no world or DB boot. */
public class MagicStackRuntimeTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static Field field(Class<?> c,String name)throws Exception {Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
 static Object value(Object o,String name)throws Exception{return field(L1PcInstance.class,name).get(o);}
 static final Method STACK;
 static {try{STACK=L1PcInstance.class.getDeclaredMethod("applyMagicStackToDamage",int.class,long.class);STACK.setAccessible(true);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static int stack(L1PcInstance p,int d,long ms)throws Exception{return (Integer)STACK.invoke(p,d,TimeUnit.MILLISECONDS.toNanos(ms));}
 public static class Pc extends L1PcInstance {
  final Set<Integer> effects=ConcurrentHashMap.newKeySet();
  volatile boolean teleport;int deaths;
  @Override public int getZoneType(){return 1;}
  @Override public boolean knownsObject(L1Object o){return true;}
  @Override public void delInvis(){}
  @Override public boolean hasSkillEffect(int id){return effects!=null&&effects.contains(id);}
  @Override public boolean get_teleport(){return teleport;}
  @Override public void death(L1Character attacker,boolean penalty){deaths++;setDead(true);}
 }
 public static class BlockingPc extends Pc {
  final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  final AtomicBoolean once=new AtomicBoolean();final AtomicInteger delivered=new AtomicInteger();
  @Override public void GotDamage(L1Character a,int d,boolean magic){
   if(once.compareAndSet(false,true)){entered.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}}
   delivered.incrementAndGet();super.GotDamage(a,d,magic);
  }
 }
 static <T extends Pc>T hp(T p){p.setMaxHp(30000);p.setCurrentHp(30000);return p;}
 static L1NpcInstance npc()throws Exception {
  Class<?> c=Class.forName("sun.misc.Unsafe");Field f=c.getDeclaredField("theUnsafe");f.setAccessible(true);
  return (L1NpcInstance)c.getMethod("allocateInstance",Class.class).invoke(f.get(null),L1NpcInstance.class);
 }
 static void deterministic()throws Exception {
  Pc p=hp(new Pc());int[] expected={300,200,133,88,59};
  for(int i=0;i<expected.length;i++)check(stack(p,300,i*100)==expected[i],"fixed window hit "+i);
  check(stack(p,300,1999)==39,"1999ms boundary");check(stack(p,300,2000)==300,"2000ms reset");
  p=hp(new Pc());check(stack(p,300,0)==300&&stack(p,300,1800)==200&&stack(p,300,2100)==300,"window slid on every hit");
  p=hp(new Pc());stack(p,300,0);int count=(Integer)value(p,"magicStackCount");
  check(stack(p,0,1000)==0&&(Integer)value(p,"magicStackCount")==count,"zero changes window");
  p.setDead(true);stack(p,300,1200);check((Integer)value(p,"magicStackCount")==count,"dead hit changes window");
  p.setDead(false);p.nextSessionStamp();check(stack(p,300,1500)==300,"relogin retained old stack");
  stack(p,300,1600);field(L1PcInstance.class,"_deathTaskGeneration").setInt(p,1);
  check(stack(p,300,1700)==300,"new life retained old stack");
  field(L1PcInstance.class,"magicStackCount").setInt(p,Integer.MAX_VALUE);
  check(stack(p,300,1800)==0&&(Integer)value(p,"magicStackCount")==Integer.MAX_VALUE,"counter overflow");
  System.out.println("PASS actual PC state: hits 1..5, 1999/2000ms, fixed window, zero/dead hits, relog/new life, overflow");
 }
 static void delivery()throws Exception {
  Pc p=hp(new Pc()),attacker=hp(new Pc());var monster=npc();
  p.receiveDamage(attacker,300,true);check(p.getCurrentHp()==29700,"PC first spell HP");
  p.receiveDamage(attacker,300);check(p.getCurrentHp()==29400,"weapon/physical damage changed");
  check((Integer)value(p,"magicStackCount")==0,"two-arg weapon advanced stack");
  p.receiveDamage(monster,300,true);check(p.getCurrentHp()==29200,"NPC spell did not share victim stack");
  p.receiveDamage(attacker,100,false);check(p.getCurrentHp()==29100,"explicit false damage changed");
  check((Integer)value(p,"magicStackCount")==1,"false event advanced stack");
  p.receiveDamage(attacker,0,true);p.receiveDamage(attacker,-100,true);
  check(p.getCurrentHp()==29100&&(Integer)value(p,"magicStackCount")==1,"invalid spell HP/count");
  p.effects.add(L1SkillId.ABSOLUTE_BARRIER);p.receiveDamage(monster,300,true);
  check(p.getCurrentHp()==29100&&(Integer)value(p,"magicStackCount")==1,"barrier advanced stack");
  p.effects.clear();p.teleport=true;p.receiveDamage(monster,300,true);
  check((Integer)value(p,"magicStackCount")==1,"teleport advanced stack");
  p.teleport=false;
  field(L1Character.class,"_currentHp").setInt(p,100);
  p.receiveDamage(monster,300,true); // Third spell: floor(300*4/9)=133, then HP cap 100.
  check(p.getCurrentHp()==0&&p.deaths==1,"HP capped before attenuation or lethal transition duplicated");
  L1Magic weapon=new L1Magic(attacker,hp(new Pc()));
  check(!field(L1Magic.class,"ordinarySpell").getBoolean(weapon),"default magic source opted weapons into stacking");
  Method eligible=L1Magic.class.getDeclaredMethod("uses2016Formula",int.class);eligible.setAccessible(true);
  check(!(Boolean)eligible.invoke(new L1Magic(monster,npc(),true),L1SkillId.ENERGY_BOLT),"NPC assigned PC formula");
  System.out.println("PASS real receiveDamage -> real controller -> real GotDamage -> atomic HP: PC/NPC spells share state; weapons/false/invalid/blocked hits do not");
  System.out.println("PASS overkill attenuation precedes HP cap; single lethal transition; direct weapon effects and NPC targets stay excluded");
 }
 static void overflow()throws Exception {
  var p=hp(new BlockingPc());var attacker=npc();Thread runner=new Thread(()->p.receiveDamage(attacker,300,true),"victim-owner");runner.start();
  check(p.entered.await(2,TimeUnit.SECONDS),"owner not blocked");
  try {
   for(int i=0;i<80;i++){p.receiveDamage(attacker,10,false);p.receiveDamage(attacker,300,true);}
   var overflow=(Queue<?>)field(CCreatureController.class,"damageOverflow").get(p.getCreatureController());
   check(!overflow.isEmpty(),"overflow path not exercised");
  }finally{p.release.countDown();}
  runner.join(3000);check(!runner.isAlive(),"owner stuck after overflow");
  int sum=800;for(int i=0;i<=80;i++)sum+=(int)Math.floor(300*Math.pow(2.0/3.0,i));
  check(p.getCurrentHp()==30000-sum,"overflow damage lost, reordered or misclassified");
  check(p.delivered.get()==161&&(Integer)value(p,"magicStackCount")==80,"overflow stack count/delivery count");
  System.out.println("PASS existing controller FIFO and overflow: 161 mixed events, no lost damage or stacking flag");
 }
 static void parallel()throws Exception {
  Pc p=hp(new Pc());var attacker=npc();var begin=new CountDownLatch(1);var threads=new ArrayList<Thread>();
  for(int i=0;i<8;i++){
   var t=new Thread(()->{try{begin.await();}catch(InterruptedException e){throw new AssertionError(e);}for(int j=0;j<50;j++)p.receiveDamage(attacker,300,true);});threads.add(t);t.start();
  }
  begin.countDown();for(var t:threads){t.join(3000);check(!t.isAlive(),"parallel owner deadlock");}
  int sum=0;for(int i=0;i<400;i++)sum+=(int)Math.floor(300*Math.pow(2.0/3.0,i));
  check(p.getCurrentHp()==30000-sum&&(Integer)value(p,"magicStackCount")==399,"parallel count/HP race");
  System.out.println("PASS eight concurrent attackers: 400 spell events counted/applied exactly once on victim owner");
 }
 static void stale()throws Exception {
  var p=hp(new BlockingPc());var attacker=npc();Thread runner=new Thread(()->p.receiveDamage(attacker,300,true));runner.start();
  check(p.entered.await(2,TimeUnit.SECONDS),"stale fixture not active");
  p.receiveDamage(attacker,300,true);p.nextSessionStamp();p.release.countDown();runner.join(3000);
  check(p.delivered.get()==1&&(Integer)value(p,"magicStackCount")==0,"queued old-session hit mutated current stack");
  System.out.println("PASS stale session events rejected before stack/HP delivery");
 }
 public static void main(String[] args)throws Exception {
  java.util.logging.Logger.getLogger("l1j.server.server.model.character.action.CAttackAction").setLevel(java.util.logging.Level.OFF);
  deterministic();delivery();overflow();parallel();stale();
  System.out.println("Actual production classes executed; test PCs override world visibility/zone/buffs/death side effects, NPC attacker is uninitialized test identity; no live IOCP/DB/game.");
 }
}
