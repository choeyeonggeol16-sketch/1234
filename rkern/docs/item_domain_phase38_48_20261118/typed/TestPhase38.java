import java.lang.reflect.*;
import java.util.*;
public final class TestPhase38 {
  static final Method TION, JERON;
  static {try {
    TION=QuestLogic.class.getDeclaredMethod("handleTionQuest",L1PcInstance.class,L1NpcInstance.class,String.class);
    JERON=QuestLogic.class.getDeclaredMethod("handleJeronQuest",L1PcInstance.class,L1NpcInstance.class,String.class);
    TION.setAccessible(true); JERON.setAccessible(true);
  } catch(Exception e){throw new RuntimeException(e);} }
  static final int[][] CASES={
    {71198,65,0,1,41339,5,0,41340,0},
    {71198,66,1,2,41341,1,0,0,0},
    {71198,67,2,3,41343,1,0,21057,0},
    {71198,68,3,4,41344,1,21057,21058,0},
    {71198,69,4,0,41345,1,21058,21059,71199},
    {71199,66,1,255,40308,1000000,41340,41341,0},
    {71199,67,1,255,41342,1,41340,41341,0}
  };
  static final String[] SUCCESS={"tion4","tion5","tion6","tion7","tion8","jeron6","jeron5"};
  static final String[] FAILED={"tion9","tion10","tion12","tion13","tion15","jeron8","jeron9"};
  static int count;
  static void check(boolean condition,String detail){count++;if(!condition)throw new AssertionError("test "+count+": "+detail);}
  static void invoke(int index,L1PcInstance pc) throws Exception {
    Method m=CASES[index][0]==71198 ? TION:JERON;
    char action=(char)CASES[index][1];
    check((Boolean)m.invoke(null,pc,new L1NpcInstance(),String.valueOf(action)),"matched action");
  }
  static L1PcInstance fixture(int[] c){
    L1PcInstance pc=new L1PcInstance();
    pc.db.put(c[0],c[2]);pc.cache.put(c[0],c[2]);
    pc.inv.put(c[4],c[5]);
    if(c[6]!=0)pc.inv.put(c[6],1);
    if(c[8]!=0){pc.db.put(c[8],255);pc.cache.put(c[8],255);}
    return pc;
  }
  static void run(int index,int mode,Random random) throws Exception {
    int[] c=CASES[index];L1PcInstance pc=fixture(c);
    if(mode==1) pc.inv.put(c[4],Math.max(0,c[5]-1));
    if(mode==2 && c[6]!=0)pc.inv.put(c[6],0);
    if(mode==3)pc.failCommit=true;
    if(mode==4)pc.failCapacity=true;
    if(mode==5)pc.failSql=true;
    if(mode==6)pc.db.put(c[0],(c[2]+1)%256);
    if(mode==7)pc.cache.put(c[0],(c[2]+1)%256);
    if(mode==8)pc.inv.put(21059,1);
    if(mode==9)pc.inv.merge(c[4],random.nextInt(6),Integer::sum);
    Map<Integer,Integer> oldInv=new HashMap<>(pc.inv);
    Map<Integer,Integer> oldDB=new HashMap<>(pc.db);
    int cachedBefore=pc.cache.get(c[0]);
    invoke(index,pc);
    boolean validPre=mode!=7 && mode!=8;
    boolean validDebit=oldInv.getOrDefault(c[4],0)>=c[5] && (c[6]==0||oldInv.getOrDefault(c[6],0)>0);
    boolean fails=mode==3 ||mode==4||mode==5||mode==6;
    boolean success=validPre&&validDebit&&!fails;
    if(success){
      check(pc.db.get(c[0])==c[3],"durable quest step");
      check(pc.cache.get(c[0])==c[3],"memory quest step");
      check(pc.inv.get(c[4])==oldInv.get(c[4])-c[5],"material debit");
      if(c[6]!=0)check(pc.inv.get(c[6])==oldInv.get(c[6])-1,"second material debit");
      if(c[7]!=0)check(pc.inv.get(c[7])==oldInv.getOrDefault(c[7],0)+1,"reward grant");
      if(c[8]!=0){check(pc.db.get(c[8])==0,"second durable reset");check(pc.cache.get(c[8])==0,"second memory reset");}
      check(pc.events.get(pc.events.size()-1).equals("html:"+SUCCESS[index]),"success html");
      int expectedRewards=c[7]==0?0:1;long actual=pc.events.stream().filter(x->x.startsWith("reward:")).count();
      check(actual==expectedRewards,"reward message exactly once");
      check(pc.events.stream().noneMatch(x->x.startsWith("failure:")),"no false error");
    }else{
      check(pc.inv.equals(oldInv),"no partial debit/grant");
      check(pc.db.equals(oldDB),"no partial DB quest write");
      check(pc.cache.get(c[0])==cachedBefore,"no partial cache step");
      check(pc.events.stream().noneMatch(x->x.startsWith("reward:")),"no reward message on fail");
      if(validPre)check(pc.events.get(pc.events.size()-1).equals("html:"+FAILED[index]),"failure html");
      else check(pc.events.isEmpty(),"no response for invalid quest step/has 21059");
    }
  }
  public static void main(String[] args)throws Exception{
    Random rand=new Random(38019);
    for(int r=0;r<300;r++)for(int i=0;i<CASES.length;i++)for(int m=0;m<10;m++)run(i,m,rand);
    L1PcInstance noAction=new L1PcInstance();
    check(!(Boolean)TION.invoke(null,noAction,new L1NpcInstance(),"Q"),"unknown action remains unhandled");
    L1PcInstance jeron=new L1PcInstance();jeron.inv.put(41340,1);
    check((Boolean)JERON.invoke(null,jeron,new L1NpcInstance(),"A"),"Jeron A");
    check(jeron.db.get(71199)==1&&jeron.cache.get(71199)==1,"Jeron A quest save");
    check(jeron.events.contains("html:jeron2"),"Jeron A UI");
    System.out.println("Phase38 extracted-production Java21 typed regression PASS assertions="+count+" cases="+(300*7*10+2));
  }
}
