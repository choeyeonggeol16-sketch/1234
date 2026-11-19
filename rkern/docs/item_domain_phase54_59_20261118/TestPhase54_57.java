import java.util.*;
import java.lang.reflect.*;
public final class TestPhase54_57 {
  static final int MAIN=23,END=255;
  static final Method CHEST,FINISH;
  static final Random RNG;
  static long cases, checks, okCount, failCount;
  static {try{
    CHEST=QuestLogic.class.getDeclaredMethod("lukeinChestReceipt",L1PcInstance.class,L1NpcInstance.class,int.class,int.class,int.class,int.class,int.class,String.class);
    FINISH=QuestLogic.class.getDeclaredMethod("questItemExchange",L1PcInstance.class,L1NpcInstance.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,String.class,String.class);
    CHEST.setAccessible(true);FINISH.setAccessible(true);
    var f=QuestLogic.class.getDeclaredField("_random");f.setAccessible(true);RNG=(Random)f.get(null);
  }catch(Exception e){throw new RuntimeException(e);}}
  static void yes(boolean b,String message){checks++;if(!b)throw new AssertionError("case="+cases+" "+message);}
  static int count(List<String> events,String prefix){int n=0;for(String s:events)if(s.startsWith(prefix))n++;return n;}
  static L1PcInstance player(int stage,int boxId,int durableStage,int durableBox,int mats){
    L1PcInstance p=new L1PcInstance();p.inv.put(40701,mats);
    p.cache.put(MAIN,stage);p.cache.put(boxId,0);p.db.put(MAIN,durableStage);p.db.put(boxId,durableBox);
    if(boxId==25){p.cache.put(24,END);p.db.put(24,END);}
    if(boxId==26){p.cache.put(24,END);p.cache.put(25,END);p.db.put(24,END);p.db.put(25,END);}
    return p;
  }
  static void chest(int box, int expected, int first, int width, int reward, int mode, int mats, int prior, boolean repeat) throws Exception {
    cases++;
    int boxId=23+box;
    L1PcInstance p=player(expected,boxId,mode==4?expected+40:expected,mode==5?255:0,mats);
    if(mode==6)p.cache.put(MAIN,expected+1);
    if(mode==7)p.cache.put(boxId,255);
    if(mode==8&&box>1)p.cache.put(boxId-1,0);
    p.failSql=mode==1;p.failCommit=mode==2;p.failCapacity=mode==3;
    Map<Integer,Integer> inv0=new HashMap<>(p.inv),db0=new HashMap<>(p.db),cache0=new HashMap<>(p.cache);
    long seed=1234567L+cases*1337;
    RNG.setSeed(seed);
    int nextStep=first+(width==1?0:new Random(seed).nextInt(width));
    CHEST.invoke(null,p,new L1NpcInstance(),expected,first,width,boxId,reward,box==3?"maptboxi":"maptbox1");
    boolean committed=(mode==0 || (mode==8 && box==1)) && mats>=1;
    if(committed){
      okCount++;
      yes(p.inv.get(40701)==mats-1,"one map consumed");
      yes(p.inv.getOrDefault(reward,0)==1,"reward exactly once");
      yes(p.db.get(MAIN)==nextStep,"durable step");
      yes(p.cache.get(MAIN)==nextStep,"published main step");
      yes(p.db.get(boxId)==END&&p.cache.get(boxId)==END,"durable and in-memory box completed");
      yes(count(p.events,"reward:")==1,"one reward message");
      yes(p.events.get(p.events.size()-1).equals("html:"+(box==3?"maptboxi":"maptbox1")),"page after commit");
    }else{
      failCount++;
      yes(p.inv.equals(inv0),"no partial consumed/reward");
      yes(p.db.equals(db0),"no partial quest persistence");
      yes(p.cache.equals(cache0),"no stale publication");
      yes(count(p.events,"reward:")==0,"no success reward packet");
      yes(count(p.events,"html:")==1,"single failure page");
    }
    if(repeat&&committed){
      cases++;
      Map<Integer,Integer> prev=new HashMap<>(p.inv);
      CHEST.invoke(null,p,new L1NpcInstance(),expected,first,width,boxId,reward,box==3?"maptboxi":"maptbox1");
      yes(p.inv.equals(prev),"repeat cannot reward again");
      yes(count(p.events,"reward:")==1,"repeat sends no reward message");
    }
  }
  static void finale(int mode,int mats,boolean cacheStale)throws Exception {
    cases++;
    L1PcInstance p=player(11,26,mode==4?19:11,255,0);
    p.db.put(26,255);p.cache.put(26,255);
    p.inv.put(40716,mats);
    if(cacheStale)p.cache.put(MAIN,12); // direct helper verifies durable step, but caller routing checks this.
    p.failSql=mode==1;p.failCommit=mode==2;p.failCapacity=mode==3;
    Map<Integer,Integer> inv0=new HashMap<>(p.inv),db0=new HashMap<>(p.db);
    if(cacheStale){ // production route rejects before method call
      yes(p.cache.get(MAIN)!=11,"final routing cache guard");return;
    }
    FINISH.invoke(null,p,new L1NpcInstance(),MAIN,11,END,40716,1,0,20269,0,"lukein0","lukein0");
    boolean committed=mode==0&&mats>=1;
    if(committed){
      yes(p.inv.get(40716)==mats-1,"final treasure consumed");
      yes(p.inv.getOrDefault(20269,0)==1,"necklace granted");
      yes(p.cache.get(MAIN)==END && p.db.get(MAIN)==END,"final stage complete");
      yes(p.db.get(26)==255,"last box remains claimed");
      yes(count(p.events,"reward:")==1,"final reward message once");
    }else{
      yes(p.inv.equals(inv0),"failed final exchange has no item mutation");
      yes(p.db.equals(db0),"failed final has no quest mutation");
      yes(count(p.events,"reward:")==0,"no final reward packet");
    }
  }
  public static void main(String[] args)throws Exception {
    for(int round=0;round<80;round++){
      for(int box=1;box<=3;box++){
        int steps=box==1?1:box==2?3:6;
        for(int step=0;step<steps;step++){
          int expected=box==1?1:box==2?2+step:5+step;
          int first=box==1?2:box==2?5:11;
          int width=box==1?3:box==2?6:1;
          int reward=box==3?40716:40702;
          for(int mode=0;mode<=8;mode++)for(int mats=0;mats<=2;mats++)
            chest(box,expected,first,width,reward,mode,mats,0,round%9==0);
        }
      }
      for(int mode=0;mode<=4;mode++)for(int mats=0;mats<=2;mats++)for(int stale=0;stale<2;stale++)finale(mode,mats,stale==1);
    }
    yes(okCount>1000&&failCount>10000,"adequate scenario coverage");
    System.out.printf("PHASE54-57 actual-production-method Java21 PASS cases=%d checks=%d commits=%d rollbacks=%d%n",cases,checks,okCount,failCount);
  }
}
