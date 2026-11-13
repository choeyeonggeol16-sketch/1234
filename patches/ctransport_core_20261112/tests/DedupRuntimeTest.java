import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.GameServerSetting;
import l1j.server.server.datatables.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.item.Exp.*;
import l1j.server.server.model.character.Attack.Helper.*;
import l1j.server.server.model.character.action.CAttackAction.AttackTargetData;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.cs.packet.CS_EnterWorld;
import l1j.server.server.packets.Protocol.Packet.SC_RestExpInfo;
import l1j.server.server.storage.cached.CAccount;
import l1j.server.MJCompanion.Basic.HuntingGround.MJCompanionHuntingGround;

public class DedupRuntimeTest {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 static class SpyAccount extends Account {
  final AtomicInteger saves=new AtomicInteger();
  @Override public void updateBlessOfAin(){saves.incrementAndGet();}
 }
 static class Pc extends MagicStackRuntimeTest.Pc {
  SpyAccount account;int callbacks,rank,starts;
  @Override public Account getAccount(){return account;}
  @Override public L1Clan getClan(){return null;}
  @Override public int getRankLevel(){return rank;}
  @Override public boolean getSafetyZone(){return true;}
  @Override public void onChangeExp(){callbacks++;}
  @Override public void startEinhasadTimer(){starts++;}
 }
 static Pc pc(int bless){Pc p=new Pc();p.setName("dedup-test");p.setLevel(52);p.setExp(ExpTable.getExpByLevel(52)+100);p.account=new SpyAccount();p.account.setBlessOfAin(bless);p.account.saves.set(0);return p;}
 static void fixtures()throws Exception {
  field(MJCompanionHuntingGround.class,"m_hunting_grounds").set(null,new HashMap<>());
  Object map=HpMpTest.unsafe(IncreaseEinhasadMap.class);field(IncreaseEinhasadMap.class,"m_increaseInfo").set(map,new HashMap<>());
  field(IncreaseEinhasadMap.class,"_instance").set(null,map);
  Config.STANDBY_SERVER=false;Config.RATE_XP=1;Config.ALT_BEGINNER_BONUS=false;Config.LIMITLEVEL=99;
  Arrays.fill((int[])field(ExpTable.class,"_expPenalty").get(null),1);
  GameServerSetting.getInstance().set_maxLevel(95);
 }
 static void hunting()throws Exception {
  for(boolean room:new boolean[]{false,true})for(int item:new int[]{0,L1SkillId.EMERALD_YES,L1SkillId.DRAGON_PUPLE,L1SkillId.DRAGON_TOPAZ})for(int combo:new int[]{0,20}) {
   Pc p=pc(5000000);p.PC방_버프=room;if(item!=0)p.effects.add(item);if(combo>0){p.effects.add(80006);p.setComboCount(combo);}
   int base=1000;double comboMul=1+combo*.1,ain=2+(room?.4:0),dragon=1;
   if(item==L1SkillId.EMERALD_YES)dragon=1.8;
   else if(item==L1SkillId.DRAGON_PUPLE)ain+=.53;
   else if(item==L1SkillId.DRAGON_TOPAZ)ain+=.8;
   int actual=ExpGainCalculator.calcAddExpAndApplySideEffects(p,base);
   int expected=(int)(base*ExpTable.getPenaltyRate(52)*comboMul*ain*dragon);
   int consumed=(int)(base*comboMul*.6);
   check(p.account.saves.get()==1,"one hunting award submitted blessing consumption "+p.account.saves.get()+" times (room="+room+", item="+item+", combo="+combo+")");
   check(p.account.getBlessOfAin()==5000000-consumed,"duplicate blessing debit or lost combo basis");
   check(Math.abs(actual-expected)<=1,"room/dragon bonus duplicated or missing actual="+actual+" expected="+expected);
   int before=p.account.getBlessOfAin();for(int i=0;i<20;i++)SC_RestExpInfo.send(p);
   check(p.account.getBlessOfAin()==before&&p.account.saves.get()==1,"UI replay consumed again");
  }
  Pc p=pc(5000000);Config.LIMITLEVEL=52;
  check(ExpGainCalculator.calcAddExpAndApplySideEffects(p,1000)==0&&p.account.saves.get()==0,"rejected level-cap award consumed blessing");Config.LIMITLEVEL=99;
  p=pc(15000);check(ExpGainCalculator.calcAddExpAndApplySideEffects(p,Integer.MAX_VALUE)>0&&p.account.getBlessOfAin()==0&&p.account.saves.get()==1,"large award depletion boundary");
  for(int low:new int[]{1,500,10000}){
   p=pc(low);p.effects.add(80006);p.setComboCount(20);
   check(ExpGainCalculator.calcAddExpAndApplySideEffects(p,1000)>0,"low-balance combo award rejected");
   check(p.account.getBlessOfAin()==Math.max(0,low-3000)&&p.account.saves.get()==1,"low-balance combo awarded without its single debit");
  }
  Pc one=pc(5000000),two=pc(5000000);ExpGainCalculator.calcAddExpAndApplySideEffects(one,500);ExpGainCalculator.calcAddExpAndApplySideEffects(two,700);
  check(one.account.getBlessOfAin()==4999700&&two.account.getBlessOfAin()==4999580,"per-recipient shared EXP applied to another account");
  System.out.println("PASS 16 room/dragon/combo combinations: one accepted hunting debit, one persistence submission, preserved bonus math; 20 UI replays, rejected award, large depletion and independent recipients");
 }
 static void questCallback()throws Exception {
  Pc p=pc(100000);int old=p.getExp();CalcExp.AddExp(p,10);
  check(p.getExp()==old+10&&p.callbacks==1,"non-hunting EXP publishes level change twice: "+p.callbacks);
  p=pc(100000);p.setLevel(95);p.setExp(ExpTable.getExpByLevel(96)-5);CalcExp.AddExp(p,10);
  check(p.getExp()==ExpTable.getExpByLevel(96)-1&&p.callbacks==1,"capped non-hunting transition missing or duplicated");
  check(p.account.saves.get()==0,"quest EXP unexpectedly consumed hunting blessing");
  System.out.println("PASS actual CalcExp.AddExp ordinary/capped path calls level transition exactly once; non-hunting rewards do not consume blessing");
 }
 static void recovery()throws Exception {
  Pc p=pc(100000);long now=System.currentTimeMillis();p.setLastLoginTime(new Timestamp(now-3600000));p.account.setLastLogOut(new Timestamp(now-905000));
  Method apply=CS_EnterWorld.class.getDeclaredMethod("applyEinhasad",L1PcInstance.class);apply.setAccessible(true);apply.invoke(null,p);
  check(p.account.getBlessOfAin()==100001,"offline recovery counted already-recovered online session: "+p.account.getBlessOfAin());
  SpyAccount a=new SpyAccount();a.setBlessOfAin(10000);a.addBlessOfAin(-2200,null);a.recoverBlessOfAin(10000,50000000);
  check(a.getBlessOfAin()==17800,"recovery discarded partial-percent remainder");
  a.setBlessOfAin(2000001);a.recoverBlessOfAin(1,2000000);check(a.getBlessOfAin()==2000001,"natural cap lowered a charged balance");
  for(int units:new int[]{0,1,9999,10000,10001,1999999,2000000,2000001,7000000}){
   int expected=(int)(((long)units+9999)/10000);check(SC_RestExpInfo.displayedGauge(units)==expected,"display ceil boundary");
  }
  Pc high=pc(5000000);high.setLevel(127);high.getResistance().setcalcAinPer(100);
  check(SC_RestExpInfo.calcDecreaseCharacterEinhasad(high,2200)==0,"high-level/efficiency calculation generated negative debit");
  SpyAccount shared=new SpyAccount();shared.setBlessOfAin(1000000);
  Thread debit=new Thread(()->{for(int i=0;i<500;i++)shared.addBlessOfAin(-1,null);});
  Thread recover=new Thread(()->{for(int i=0;i<500;i++)shared.recoverBlessOfAin(1,50000000);});
  debit.start();recover.start();debit.join(3000);recover.join(3000);
  check(!debit.isAlive()&&!recover.isAlive()&&shared.getBlessOfAin()==1000000,"concurrent recovery/debit lost state");
  System.out.println("PASS actual offline path excludes online time, partial internal units retained, gauge ceil/bounds, level127/efficiency cap and concurrent recovery/debit");
 }
 static AttackTargetData armorAttack(int weaponBase,int extra,int dr,boolean armor,boolean burning)throws Exception {
  PhysicalDamageTest.Pc a=new PhysicalDamageTest.Pc(),v=new PhysicalDamageTest.Pc();
  a.held=new L1ItemInstance();a.effects.add(L1SkillId.SOUL_OF_FLAME);a.getResistance().addPVPweaponTotalDamage(extra);
  a.effects.add(L1SkillId.ARMOR_BRAKE_ATTACKER);if(armor)v.effects.add(L1SkillId.ARMOR_BRAKE);if(burning)a.effects.add(L1SkillId.BURNING_SPIRIT);v.armor=dr;
  Constructor<AttackTargetData> c=AttackTargetData.class.getDeclaredConstructor();c.setAccessible(true);AttackTargetData hit=c.newInstance();
  PhysicalDamageTest.set(hit,"_pc",a);PhysicalDamageTest.set(hit,"_targetPc",v);PhysicalDamageTest.set(hit,"_target",v);
  PhysicalDamageTest.set(hit,"_weaponType",4);PhysicalDamageTest.set(hit,"_weaponType2",1);PhysicalDamageTest.set(hit,"_weaponSmall",weaponBase);PhysicalDamageTest.set(hit,"weapon",a.held);
  PhysicalDamageTest.set(hit,"_calcType",1);PhysicalDamageTest.set(hit,"_isHit",true);
  return hit;
 }
 static int armorHit(int weaponBase,int extra,int dr,boolean armor,boolean burning)throws Exception {
  return armorAttack(weaponBase,extra,dr,armor,burning).calcPcPcDamage();
 }
 static void armor()throws Exception {
  PhysicalDamageTest.rates100();
  check(armorHit(30,0,24,false,false)==6&&armorHit(30,0,24,true,false)==23,"AB base30/DR24");
  check(armorHit(30,0,18,false,false)==12&&armorHit(30,0,18,true,false)==29,"AB base30/DR18");
  check(armorHit(100,77,31,true,true)==372,"full pre-DR Armor Break omitted additions or applied twice: "+armorHit(100,77,31,true,true));
  check(armorHit(100,77,27,true,true)==378,"AB 177/DR27/Burning order");
  check(armorHit(100,77,31,false,true)==219,"AB-off Burning changed");
  PhysicalDamageTest.Pc a=new PhysicalDamageTest.Pc(),v=new PhysicalDamageTest.Pc();v.effects.add(L1SkillId.ARMOR_BRAKE);a.effects.add(L1SkillId.ARMOR_BRAKE_ATTACKER);
  check(L1AttackHelper.applyArmorBreak(a,v,true,177)==177,"ranged/keylink acquired AB");
  check(L1AttackHelper.applyArmorBreak(a,v,false,Integer.MAX_VALUE)==Integer.MAX_VALUE,"AB multiplication overflow");
  check(L1AttackHelper.applyArmorBreak(a,v,false,0)==0&&DamageReductionHelper.applyAllDamageReductions(v,a,false,false,1,true,false)>=0,"zero/blocked boundary");
  a.effects.clear();check(L1AttackHelper.applyArmorBreak(a,v,false,30)==36,"existing non-caster 1.20 policy changed");
  AttackTargetData delivered=armorAttack(100,77,31,true,true);
  PhysicalDamageTest.Pc victim=(PhysicalDamageTest.Pc)field(AttackTargetData.class,"_targetPc").get(delivered);
  victim.level=60;victim.setMaxHp(1000);victim.setCurrentHp(1000);
  int damage=delivered.calcDamage();check(damage==372,"full calcDamage differed from helper chain");delivered.commit();
  check(victim.getCurrentHp()==628,"commit reapplied Armor Break or DR/Burning");
  System.out.println("PASS actual physical chain: 6/23/12/29/372/378; AB/fixed DR/Burning once, final HP1000->628; ranged/keylink and legacy1.20 policies, overflow/zero");
 }
 static void persistRetry()throws Exception {
  Class.forName("org.h2.Driver");try(Connection c=DriverManager.getConnection("jdbc:h2:mem:dedup;MODE=MySQL;DB_CLOSE_DELAY=-1")){
   c.createStatement().execute("CREATE TABLE accounts(login VARCHAR PRIMARY KEY,bless_of_ain INT)");c.createStatement().execute("INSERT INTO accounts VALUES('dedup',5000000)");
   Field pool=field(DatabaseFactory.class,"DATABASE_POOL");Object before=pool.get(null);pool.set(null,new HpMpPersistenceTest.Source(c));
   try{
    CAccount state=new CAccount("dedup");state.blessOfAin=4999400;state.changed(CAccount.BLESS_OF_AIN);CAccount.Snapshot snapshot=new CAccount.Snapshot(state);
    c.setAutoCommit(false);CAccount.persist(snapshot);c.rollback();CAccount.persist(snapshot);c.commit();CAccount.persist(snapshot);c.commit();
    try(Connection reopened=DriverManager.getConnection("jdbc:h2:mem:dedup;MODE=MySQL;DB_CLOSE_DELAY=-1");ResultSet rows=reopened.createStatement().executeQuery("SELECT bless_of_ain FROM accounts WHERE login='dedup'")){
     check(rows.next()&&rows.getInt(1)==4999400,"absolute CacheD snapshot retry consumed twice");
    }
   }finally{pool.set(null,before);}
  }
  System.out.println("PASS actual CAccount.persist SQL rollback/retry/reopened connection is an absolute balance write, not a second consumption; no claim of cross-row XP/blessing crash atomicity");
 }
 public static void main(String[] args)throws Exception {
  java.util.logging.Logger.getLogger("l1j.server.server.model.character.action.CAttackAction").setLevel(java.util.logging.Level.OFF);
  fixtures();switch(args.length==0?"all":args[0]){
   case "xp":hunting();break;case "quest":questCallback();break;case "offline":recovery();break;case "armor":armor();break;
   default:hunting();questCallback();recovery();armor();persistRetry();
  }
  System.out.println("Actual production math/Account/EXP/attack paths with disconnected PCs, empty map fixtures and local H2; no Windows/live DB. 2015 6059 model not installed as 2019 policy.");
 }
}
