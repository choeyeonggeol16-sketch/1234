import java.lang.reflect.*;
import java.util.*;
public final class TestPhase46_48 {
  static Method GRANT;static int assertions;
  static{try{GRANT=QuestLogic.class.getDeclaredMethod("questGrantOnce",L1PcInstance.class,L1NpcInstance.class,int.class,int.class,int.class,int.class,int.class,String.class);GRANT.setAccessible(true);}catch(Exception e){throw new RuntimeException(e);}}
  static void assertTrue(boolean good,String why){assertions++;if(!good)throw new AssertionError(assertions+" "+why);}
  static int[][] CASES={{23,0,1,40701},{32,1,2,40644},{29,0,1,40700}};
  static String[] PAGES={"lukein8","francu10","rudian6"};
  static void run(int kind,int mode) throws Exception {
    int[] c=CASES[kind];L1PcInstance pc=new L1PcInstance();pc.db.put(c[0],c[1]);pc.cache.put(c[0],c[1]);
    if(mode==1)pc.failSql=true;
    if(mode==2)pc.failCommit=true;
    if(mode==3)pc.failCapacity=true;
    if(mode==4)pc.db.put(c[0],88);
    if(mode==5)pc.cache.put(c[0],99);
    Map<Integer,Integer> beforeInv=new HashMap<>(pc.inv),beforeDb=new HashMap<>(pc.db);
    int oldCache=pc.cache.get(c[0]);
    GRANT.invoke(null,pc,new L1NpcInstance(),c[0],c[1],c[2],c[3],1,PAGES[kind]);
    if(mode==0){
      assertTrue(pc.inv.get(c[3])==1,"one item issued");
      assertTrue(pc.db.get(c[0])==c[2]&&pc.cache.get(c[0])==c[2],"durable quest stage confirmed");
      assertTrue(pc.events.stream().filter(e->e.equals("reward:i"+c[3])).count()==1,"one reward message");
      assertTrue(pc.events.contains("html:"+PAGES[kind]),"original page");
    }else{
      assertTrue(pc.inv.equals(beforeInv),"no gift on rollback or wrong stage");
      assertTrue(pc.db.equals(beforeDb)&&pc.cache.get(c[0])==oldCache,"no false quest advancement");
      assertTrue(pc.events.stream().noneMatch(e->e.startsWith("reward:")),"no false successful gift");
    }
  }
  public static void main(String[]args)throws Exception{
    for(int r=0;r<1000;r++)for(int kind=0;kind<3;kind++)for(int mode=0;mode<6;mode++)run(kind,mode);
    System.out.println("Phase46-48 production extracted Java21 typed PASS scenarios=18000 assertions="+assertions);
  }
}
