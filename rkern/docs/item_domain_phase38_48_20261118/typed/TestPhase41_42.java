import java.lang.reflect.*;
import java.util.*;
public final class TestPhase41_42 {
  static int checks; static final Method ACT;
  static{try{ACT=QuestLogic.class.getDeclaredMethod("handleCrystalKeeper",L1PcInstance.class,L1NpcInstance.class,String.class);ACT.setAccessible(true);}catch(Exception e){throw new RuntimeException(e);}}
  static void check(boolean good,String detail){checks++;if(!good)throw new AssertionError("test "+checks+": "+detail);}
  static void run(int action,int mode,Random rand)throws Exception{
    L1PcInstance p=new L1PcInstance(); String s=action==0?"a":action==1?"b":"c";
    p.db.put(33,action==0?0:action==1?1:2);p.cache.putAll(p.db);
    if(action!=0){p.inv.put(246,1);p.inv.put(247,1);p.inv.put(248,1);p.inv.put(249,1);p.inv.put(40620,1);}
    if(action==2)p.inv.put(40660,4+rand.nextInt(6));
    if(mode>=1&&mode<=5){
      if(action==0){if(mode==1)p.failCapacity=true;else if(mode==2)p.failCommit=true;else if(mode==3)p.failSql=true;else if(mode==4)p.db.put(33,2);else p.cache.put(33,1);}
      else {if(mode==1)p.inv.remove(246+rand.nextInt(4));else if(mode==2)p.failCapacity=true;else if(mode==3)p.failCommit=true;else if(mode==4)p.failSql=true;else p.db.put(33,99);}
    }
    if(mode==6&&action!=0)p.equipped.add(249);
    if(mode==7&&action==1)p.inv.put(40660,1);
    if(mode==8&&action==2)p.inv.remove(40660);
    Map<Integer,Integer> before=new HashMap<>(p.inv), dbBefore=new HashMap<>(p.db);
    int cacheBefore=p.cache.get(33);
    check((Boolean)ACT.invoke(null,p,new L1NpcInstance(),s),"action matched");
    boolean succeeded=mode==0||(mode==6&&action==0)||(mode==7&&action!=1)||(mode==8);
    if(action==0&&mode==5)succeeded=false;
    if(succeeded){
      check(p.db.get(33)==(action==0?1:action==1?2:0),"DB step persisted");
      check(p.cache.get(33)==p.db.get(33),"cache step matches durable");
      if(action==0){
        for(int id:new int[]{246,247,248,249})check(p.inv.getOrDefault(id,0)==before.getOrDefault(id,0)+1,"grant component " + id);
        check(p.inv.getOrDefault(40660,0)==before.getOrDefault(40660,0)+5,"grant stones quantity 5");
        check(p.events.stream().filter(x->x.startsWith("reward:")).count()==5,"five messages in order");
        check(p.events.indexOf("reward:i246")<p.events.indexOf("reward:i40660"),"grant message order");
      } else {
        for(int id:new int[]{246,247,248,249,40620})check(p.inv.getOrDefault(id,0)==before.getOrDefault(id,0)-1,"debit component " + id);
        if(action==2)check(p.inv.getOrDefault(40660,0)==0,"all stones debited on return");
        if(action==1)check(p.inv.getOrDefault(40660,0)==0,"no leftover stones on entry");
        String teleport=action==1?"teleport:32801:32895:483":"teleport:32736:32800:483";
        check(p.events.contains(teleport),"teleport only on committed items");
      }
      check(p.events.get(p.events.size()-1).equals("html:"),"original blank HTML");
    }else{
      check(p.inv.equals(before),"no partial item grants/debits on failure");
      check(p.db.equals(dbBefore)&&p.cache.get(33)==cacheBefore,"no quest advancement on failure");
      check(p.events.stream().noneMatch(x->x.startsWith("teleport:")||x.startsWith("reward:")),"no effects before commit");
    }
  }
  public static void main(String[] args)throws Exception{
    Random rng=new Random(410042);
    for(int round=0;round<500;round++)for(int action=0;action<3;action++)for(int mode=0;mode<9;mode++)run(action,mode,rng);
    L1PcInstance p=new L1PcInstance();check(!(Boolean)ACT.invoke(null,p,new L1NpcInstance(),"unknown"),"unknown handler not consumed");
    System.out.println("Phase41-42 extracted-production Java21 typed PASS scenarios="+(500*3*9+1)+" assertions="+checks);
  }
}
