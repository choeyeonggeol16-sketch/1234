import java.util.*;
import java.lang.reflect.*;
public final class TestPhase60_62 {
 static final Method EXCHANGE,UPGRADE;static final Random RNG;
 static long cases,checks,successful,failed;
 static{try{
 EXCHANGE=QuestLogic.class.getDeclaredMethod("dragonEyeExchange",L1PcInstance.class,L1NpcInstance.class,int.class,int.class,int.class,int.class,String.class,boolean.class);
 UPGRADE=QuestLogic.class.getDeclaredMethod("dragonEyeUpgrade",L1PcInstance.class,L1NpcInstance.class,int.class,int.class,int.class,int.class);
 EXCHANGE.setAccessible(true);UPGRADE.setAccessible(true);
 var field=QuestLogic.class.getDeclaredField("_random");field.setAccessible(true);RNG=(Random)field.get(null);
 }catch(Exception e){throw new RuntimeException(e);}}
 static void ok(boolean yes,String reason){checks++;if(!yes)throw new AssertionError("case="+cases+" "+reason);}
 static long count(L1PcInstance p,String prefix){return p.events.stream().filter(x->x.startsWith(prefix)).count();}
 static L1PcInstance pc(int token,int count,int adena,int reward,boolean preowned,int mode){
  L1PcInstance p=new L1PcInstance();p.inv.put(token,count);p.inv.put(40308,adena);
  if(preowned)p.inv.put(reward,1);
  p.failSql=mode==1;p.failCommit=mode==2;p.failCapacity=mode==3;
  return p;
 }
 static void exchange(int variant,int mode,int count,int money,boolean owned)throws Exception {
  cases++;
  int[] tokens={410162,410163,410164,410165,410030,410028,410031,410029};
  int[] rewards={410034,410032,410035,410033,410034,410032,410035,410033};
  int token=tokens[variant],reward=rewards[variant];boolean log=variant>=4;
  L1PcInstance p=pc(token,count,money,reward,owned,mode);
  Map<Integer,Integer> prior=new HashMap<>(p.inv);
  // Actual A-D production dispatcher has a pre-owned output guard.
  if(!log||!owned)EXCHANGE.invoke(null,p,new L1NpcInstance(),token,log?1:32,100000,reward,log?"sherme1":"sherme10",log);
  boolean success=mode==0&&count>=(log?1:32)&&money>=100000&&(!log||!owned);
  if(success){successful++;
   ok(p.inv.get(token)==count-(log?1:32),"token debit");ok(p.inv.get(40308)==money-100000,"money debit");
   ok(p.inv.getOrDefault(reward,0)==(owned?2:1),"reward count");
   if(log){ok(count(p,"missing:")==1,"403 log message");ok(count(p,"html:")==1,"empty success page");}
   else{ok(p.events.isEmpty(),"H-K success has no packet");}
  }else{failed++;
   ok(p.inv.equals(prior),"rollback both costs/reward");
   if(log&&owned){ok(p.events.isEmpty(),"duplicate output rejected by caller");}
   else {ok(count(p,"html:")==1,"single rejection page");ok(p.events.get(p.events.size()-1).equals("html:"+(log?"sherme1":"sherme10")),"correct failure page");}
  }
 }
 static void upgrade(int variant,int mode,int firstCount,int secondCount,int money,boolean owned)throws Exception{
  cases++;
  int[] first={410034,410036,410037},second={410032,410033,410035},out={410036,410037,410038},percent={40,30,20};
  L1PcInstance p=pc(first[variant],firstCount,money,out[variant],owned,mode);
  p.inv.put(second[variant],secondCount);
  Map<Integer,Integer> before=new HashMap<>(p.inv);
  long seed=cases*73+987611;RNG.setSeed(seed);int rolled=new Random(seed).nextInt(100)+1;
  UPGRADE.invoke(null,p,new L1NpcInstance(),first[variant],second[variant],out[variant],percent[variant]);
  boolean available=firstCount>0&&secondCount>0&&money>=200000&&!owned;
  boolean committed=available&&mode==0;
  boolean gotReward=committed&&rolled<=percent[variant];
  if(committed){successful++;
   ok(p.inv.get(first[variant])==firstCount-1,"first input charged");
   ok(p.inv.get(second[variant])==secondCount-1,"second input charged");
   ok(p.inv.get(40308)==money-200000,"adena charged");
   ok(p.inv.getOrDefault(out[variant],0)==(gotReward?1:0),"chance result");
   ok(count(p,"html:")==1,"committed outcome page once");
   ok(p.events.get(p.events.size()-1).equals("html:"+(gotReward?"":"sherme5")),"success vs roll-failure page");
   ok(count(p,"missing:")==(gotReward?1:0),"403 effect log only successful");
  }else{failed++;
   ok(p.inv.equals(before),"upgrade rollback all costs");
   ok(count(p,"html:")==1,"upgrade failed once");
   ok(p.events.get(p.events.size()-1).equals("html:"+(owned?"sherme0":"sherme1")),"upgrade failure page");
   ok(count(p,"missing:")==0,"no 403 on failure");
  }
 }
 public static void main(String[] args)throws Exception{
  for(int round=0;round<200;round++){
   for(int i=0;i<8;i++)for(int mode=0;mode<4;mode++)for(int amount:new int[]{0,1,31,32,33})
     for(int adena:new int[]{0,99999,100000})for(int owned=0;owned<2;owned++)exchange(i,mode,amount,adena,owned!=0);
   for(int i=0;i<3;i++)for(int mode=0;mode<4;mode++)for(int m1=0;m1<2;m1++)for(int m2=0;m2<2;m2++)
     for(int money:new int[]{0,200000,200001})for(int owned=0;owned<2;owned++)upgrade(i,mode,m1,m2,money,owned!=0);
  }
  ok(successful>1000&&failed>10000,"coverage");
  System.out.printf("PHASE60-62 production typed Java21 PASS cases=%d checks=%d committed=%d rejected=%d%n",cases,checks,successful,failed);
 }
}
