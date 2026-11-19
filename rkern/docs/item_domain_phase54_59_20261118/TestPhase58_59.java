import java.lang.reflect.*;
import java.util.*;
public final class TestPhase58_59 {
 static final Method FIRST,HORO;
 static final Random RNG;
 static long cases,checks;
 static{try{
  FIRST=QuestLogic.class.getDeclaredMethod("questGrantOnce",L1PcInstance.class,L1NpcInstance.class,int.class,int.class,int.class,int.class,int.class,String.class);
  HORO=QuestLogic.class.getDeclaredMethod("keplishaHoroscopeReceipt",L1PcInstance.class,L1NpcInstance.class);
  FIRST.setAccessible(true);HORO.setAccessible(true);
  var f=QuestLogic.class.getDeclaredField("_random");f.setAccessible(true);RNG=(Random)f.get(null);
 }catch(Exception e){throw new RuntimeException(e);}}
 static void ok(boolean yes,String what){checks++;if(!yes)throw new AssertionError("case="+cases+" "+what);}
 static int count(L1PcInstance p,String prefix){int n=0;for(var s:p.events)if(s.startsWith(prefix))n++;return n;}
 static void first(int mode,int step,boolean ticket)throws Exception{
  cases++;
  L1PcInstance p=new L1PcInstance();p.db.put(35,mode==4?99:step);p.cache.put(35,step);if(ticket)p.inv.put(41312,1);
  p.failSql=mode==1;p.failCommit=mode==2;p.failCapacity=mode==3;
  Map<Integer,Integer> oldI=new HashMap<>(p.inv),oldD=new HashMap<>(p.db);
  // Route only enters the receipt when ticket missing and in-memory step 0.
  if(!ticket && step==0)FIRST.invoke(null,p,new L1NpcInstance(),35,0,255,41312,1,"keplisha7");
  boolean success=!ticket&&step==0&&mode==0;
  if(success){
   ok(p.inv.get(41312)==1,"one clue");ok(p.db.get(35)==255&&p.cache.get(35)==255,"first persisted");
   ok(count(p,"reward:")==1,"first reward packet once");
  }else{ok(p.inv.equals(oldI),"first no duplicate");ok(p.db.equals(oldD),"first no premature stage");ok(count(p,"reward:")==0,"first no packet");}
  if(success){
   p.inv.remove(41312);Map<Integer,Integer> after=new HashMap<>(p.inv);
   // The production routing guard blocks subsequent first gifts after the clue was used.
   ok(p.cache.get(35)!=0,"repeat acquisition blocked by durable step");ok(p.inv.equals(after),"no resend");
  }
 }
 static void horoscope(int mode, int adena, int proof, boolean owned)throws Exception{
  cases++;
  L1PcInstance p=new L1PcInstance();p.inv.put(40308,adena);p.inv.put(41313,proof);if(owned)p.inv.put(41314,1);
  p.failSql=mode==1;p.failCommit=mode==2;p.failCapacity=mode==3;
  Map<Integer,Integer> before=new HashMap<>(p.inv);
  long seed=cases*47+1000007;RNG.setSeed(seed);Random ref=new Random(seed);
  String expected=(switch(ref.nextInt(3)){case 0->"horosa";case 1->"horosb";default->"horosc";})+(ref.nextInt(100)+1);
  HORO.invoke(null,p,new L1NpcInstance());
  boolean success=(mode==0||mode==4)&&adena>=1000&&proof>=1&&!owned;
  if(success){
   ok(p.inv.get(40308)==adena-1000,"adena cost");ok(p.inv.get(41313)==proof-1,"paper cost");
   ok(p.inv.getOrDefault(41314,0)==1,"ticket grant");
   ok(p.events.get(p.events.size()-1).equals("html:"+expected),"horoscope RNG and page match old successful code");
   ok(count(p,"reward:")==1,"ticket reward packet once");
  }else{
   ok(p.inv.equals(before),"rollback both materials");ok(count(p,"reward:")==0,"no success packet");
   if(!owned)ok(p.events.get(p.events.size()-1).equals("html:keplisha8"),"failure page");
   else ok(p.events.isEmpty(),"already owned no duplicate");
  }
 }
 public static void main(String[] args)throws Exception{
  for(int r=0;r<300;r++){
   for(int mode=0;mode<5;mode++)for(int step=0;step<=1;step++)for(int owned=0;owned<2;owned++)first(mode,step,owned==1);
   for(int mode=0;mode<5;mode++)for(int adena:new int[]{0,999,1000,100000})for(int proof=0;proof<3;proof++)for(int owned=0;owned<2;owned++)horoscope(mode,adena,proof,owned==1);
  }
  System.out.printf("PHASE58-59 production typed Java21 PASS cases=%d checks=%d%n",cases,checks);
 }
}
