import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.character.scheduler.CRegenController;
import l1j.server.server.model.network.core.*;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.utils.CalcStat;
public class HpMpTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static class Client extends CIOClient {Client(){super(null,false);}@Override public boolean isConnected(){return false;}}
 static class Inventory extends L1PcInventory {int weight;Inventory(L1PcInstance p){super(p);}@Override public int getWeight100(){return weight;}}
 static class Pc extends MagicStackRuntimeTest.Pc {
  Inventory inventory;CIOClient client;
  Pc(){inventory=new Inventory(this);set_food(100);setName("hpmp-test");}
  @Override public L1PcInventory getInventory(){return inventory!=null?inventory:super.getInventory();}
  @Override public CIOClient getNetConnection(){return client;}
  @Override public boolean is_top_ranker(){return false;}
 }
 static Pc pc(int con,int wis,int level){Pc p=new Pc();p.getAbility().setBaseCon(con);p.getAbility().setBaseWis(wis);p.setLevel(level);p.addBaseMaxHp((short)1000);p.addBaseMaxMp(1000);p.setCurrentHp(100);p.setCurrentMp(100);return p;}
 static Object unsafe(Class<?> type)throws Exception{Class<?> c=Class.forName("sun.misc.Unsafe");Field f=c.getDeclaredField("theUnsafe");f.setAccessible(true);return c.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);}
 static void move(Pc p,int level)throws Exception{
  int gap=level-p.getLevel();p.setExp(ExpTable.getExpByLevel(level));
  Method m=L1PcInstance.class.getDeclaredMethod("updateLevelAndGrowth",int.class);m.setAccessible(true);m.invoke(p,gap);check(p.getLevel()==level,"real level transition");
 }
 static void bonuses()throws Exception{
  for(int n:new int[]{24,25,34,35,44,45})for(int temporary:new int[]{0,1}){
   Pc p=pc(n,n,80);p.getAbility().addAddedCon(temporary);p.getAbility().addAddedWis(temporary);
   int bonus=n<25?0:n<35?50:n<45?150:300;
   check(p.getMaxHp()==1000+bonus&&p.getMaxMp()==1000+bonus,"temporary stat opened threshold "+n);
   p.addMaxHp(80);p.addMaxMp(40);check(p.getMaxHp()==1080+bonus&&p.getMaxMp()==1040+bonus,"flat equipment HP/MP lost");
   p.setCurrentHp(p.getMaxHp());p.setCurrentMp(p.getMaxMp());p.setMaxHp(1080);p.setMaxMp(1040);
   check(p.getCurrentHp()==1080+bonus&&p.getCurrentMp()==1040+bonus,"cap discarded pure bonus");
  }
  Pc p=pc(24,24,80);p.getAbility().addCon(1);p.getAbility().addWis(1);
  check(p.getAbility().getBaseCon()==24&&p.getAbility().getCon()==25&&p.getMaxHp()==1050&&p.getMaxMp()==1050,"permanent allocation omitted");
  p=pc(24,20,80);p.getAbility().addAddedWis(25);p.getAbility().addAddedCon(21);
  var view=l1j.server.server.packets.Protocol.Packet.SC_CharStat.create(p,2,l1j.server.server.packets.Protocol.Packet.SC_CharStat.Stat_Wis);
  Field lo=view.getClass().getDeclaredField("_v4"),hi=view.getClass().getDeclaredField("_v5");lo.setAccessible(true);hi.setAccessible(true);
  check(lo.getInt(view)==CalcStat.MinincreaseMp(p.getType(),20)&&hi.getInt(view)==CalcStat.MaxincreaseMp(p.getType(),20),"actual UI used temporary WIS growth");
  var reset=l1j.server.server.model.network.core.cs.packet.CS_ReturnStatus.class;
  Method initHp=reset.getDeclaredMethod("getInitHp",L1PcInstance.class),initMp=reset.getDeclaredMethod("getInitMp",L1PcInstance.class,int.class);
  initHp.setAccessible(true);initMp.setAccessible(true);
  for(int t=0;t<8;t++)for(int wis:new int[]{11,12,15,16,20}){
   p.setType(t);check(((Short)initHp.invoke(null,p))==CalcStat.initialHp(t)&&((Short)initMp.invoke(null,p,wis))==CalcStat.initialMp(t,wis),"reset and creation baselines differ");
  }
  System.out.println("PASS shared creation/stat-reset HP/MP baselines across eight classes and WIS boundaries");
  System.out.println("PASS actual maximum getters: 24/25,34/35,44/45; gear/buff exclusion; permanent allocation; unrelated HP/MP effects and caps");
 }
 static void oracle(Path csv)throws Exception {
  int count=0;double maxError=0;
  for(String line:Files.readAllLines(csv).subList(1,Files.readAllLines(csv).size())){
   String[] f=line.split(",");boolean hp=f[0].equals("HP");int type=Integer.parseInt(f[1]),stat=Integer.parseInt(f[2]),level=Integer.parseInt(f[3]),cap=Integer.parseInt(f[4]);double total=Double.parseDouble(f[5]);
   for(int l=2;l<=level;l++){
    int pure=Math.min(stat,cap+Math.max(0,l-50));
    total+=hp?CalcStat.PureHp(type,pure)+.5:(CalcStat.MinincreaseMp(type,pure)+CalcStat.MaxincreaseMp(type,pure))/2.0;
   }
   total+=CalcStat.pureHpMpBonus(Math.min(stat,cap+Math.max(0,level-50)));
   double error=Math.abs(total-Double.parseDouble(f[6]));maxError=Math.max(maxError,error);
   check(error<.00001,"sheet disagreement "+line+" actual="+total);count++;
  }
  for(int type=0;type<8;type++)for(int wis=1;wis<=45;wis++){
   int min=CalcStat.MinincreaseMp(type,wis),max=CalcStat.MaxincreaseMp(type,wis);
   for(int j=0;j<1000;j++){int v=CalcStat.increaseMp(type,(byte)wis);check(v>=min&&v<=max,"MP roll out of displayed range");}
  }
  check(CalcStat.MinincreaseMp(1,45)==6&&CalcStat.MaxincreaseMp(1,45)==10,"knight45");
  check(CalcStat.MinincreaseMp(3,45)==20&&CalcStat.MaxincreaseMp(3,45)==32,"wizard45");
  check(CalcStat.MinincreaseMp(2,18)==5&&CalcStat.MaxincreaseMp(2,18)==10,"elf18");
  int[] min12={12,17,10,7,11,13,9,17};
  for(int t=0;t<8;t++)for(int con:new int[]{11,12,13,16,20,25,35,45})for(int j=0;j<64;j++){
   int v=CalcStat.increaseHp(t,(byte)con);check(v>=CalcStat.PureHp(t,con)&&v<=CalcStat.PureHp(t,con)+1,"HP roll/display mismatch");
   check(CalcStat.PureHp(t,12)==min12[t],"CON12 class baseline");
  }
  System.out.println("PASS "+count+" public-sheet cached means (8 classes, HP/MP, levels50/51..88, gradual stat allocation), max error="+maxError);
  System.out.println("PASS all 360 MP ranges / 360000 actual rolls; HP growth, display, class/CON boundaries");
 }
 static void regen()throws Exception{
  Method hp=CRegenController.class.getDeclaredMethod("regenHp",L1PcInstance.class),mp=CRegenController.class.getDeclaredMethod("regenMp",L1PcInstance.class);hp.setAccessible(true);mp.setAccessible(true);
  Pc p=pc(20,20,80);p.client=(CIOClient)unsafe(Client.class);mp.invoke(null,p);check(p.getCurrentMp()==106,"WIS20 lvl80 duplicated MPR");
  for(int con:new int[]{44,45})for(int wis:new int[]{44,45}){
   p=pc(con,wis,80);p.inventory.weight=50;p.client=(CIOClient)unsafe(Client.class);
   hp.invoke(null,p);mp.invoke(null,p);
   check(con==45?p.getCurrentHp()>100:p.getCurrentHp()==100,"HP overweight independent gate");
   check(p.getCurrentMp()==(wis==45?108:100),"MP overweight independent gate");
  }
  p=pc(44,44,80);p.getAbility().addAddedCon(1);p.getAbility().addAddedWis(1);p.inventory.weight=50;p.client=(CIOClient)unsafe(Client.class);
  hp.invoke(null,p);mp.invoke(null,p);check(p.getCurrentHp()==100&&p.getCurrentMp()==100,"temporary45 enabled overweight regen");
  p=pc(45,45,80);p.inventory.weight=50;p.set_food(39);p.client=(CIOClient)unsafe(Client.class);hp.invoke(null,p);mp.invoke(null,p);check(p.getCurrentHp()==100&&p.getCurrentMp()==100,"hunger bypass");
  p.set_food(100);p.effects.add(L1SkillId.ABSOLUTE_BARRIER);hp.invoke(null,p);mp.invoke(null,p);check(p.getCurrentHp()==100&&p.getCurrentMp()==100,"barrier bypass");
  check(CalcStat.naturalRegenAfterWeight(11,true,45)==5&&CalcStat.naturalRegenAfterWeight(12,true,45)==6,"odd half policy");
  p.effects.clear();p.client=null;hp.invoke(null,p);mp.invoke(null,p);check(p.getCurrentHp()==100&&p.getCurrentMp()==100,"logout regen");
  System.out.println("PASS actual CRegenController MP6 at WIS20/lvl80; independent pure45 overweight gates; temporary stats/hunger/barrier/logout; odd half policy");
 }
 static void growth()throws Exception{
  Pc p=pc(20,20,1);move(p,2);int hp2=p.getBaseMaxHp(),mp2=p.getBaseMaxMp();String encoded=p.encodeHpMpGrowth();
  for(int i=0;i<100;i++){move(p,1);check(p.getBaseMaxHp()==1000&&p.getBaseMaxMp()==1000,"down rerolled");move(p,2);check(p.getBaseMaxHp()==hp2&&p.getBaseMaxMp()==mp2,"up rerolled");}
  Pc reloaded=pc(20,20,2);reloaded.addBaseMaxHp((short)(hp2-1000));reloaded.addBaseMaxMp(mp2-1000);reloaded.restoreHpMpGrowth(encoded);move(reloaded,1);check(reloaded.getBaseMaxHp()==1000&&reloaded.getBaseMaxMp()==1000,"restart growth lost");
  p=pc(20,20,80);move(p,79);check(p.getBaseMaxHp()==1000&&p.getBaseMaxMp()==1000,"legacy history guessed");move(p,80);check(p.getBaseMaxHp()==1000&&p.getBaseMaxMp()==1000,"legacy growth duplicated");move(p,81);int hp81=p.getBaseMaxHp(),mp81=p.getBaseMaxMp();move(p,79);move(p,81);check(p.getBaseMaxHp()==hp81&&p.getBaseMaxMp()==mp81,"legacy floor roundtrip");
  p=pc(45,45,1);p.addBaseMaxHp((short)(32760-1000));p.addBaseMaxMp(32760-1000);move(p,2);check(p.getBaseMaxHp()==32767,"short overflow at cap");move(p,1);check(p.getBaseMaxHp()==32760&&p.getBaseMaxMp()==32760,"actual clipped deltas lost");
  HpMpGrowthHistory stage=new HpMpGrowthHistory(1);stage.record(2,12,3);p=pc(20,20,2);p.addBaseMaxHp((short)12);p.addBaseMaxMp(3);p.completeStatResetGrowth(stage);move(p,1);check(p.getBaseMaxHp()==1000&&p.getBaseMaxMp()==1000,"stat-reset stage not installed");
  for(String bad:new String[]{"2:1","1:0","1:1;3,1,1","1:1;2,-1,1","1:1;2,1,1;2,1,1"}){
   try{HpMpGrowthHistory.decode(bad);throw new AssertionError("bad history accepted");}catch(IllegalArgumentException expected){}
  }
  System.out.println("PASS actual level/growth transition: 100 roundtrips, restart, legacy floor/no double reward, cap32767, reset stage, malformed history rejection");
 }
 public static void main(String[] args)throws Exception{
  java.util.logging.Logger.getLogger("l1j.server.server.model.character.action.CAttackAction").setLevel(java.util.logging.Level.OFF);
  bonuses();oracle(Path.of(args[0]));regen();growth();
  System.out.println("Actual production classes tested; disconnected test clients/PC world effects, public research oracle; no Windows game or production DB.");
 }
}
