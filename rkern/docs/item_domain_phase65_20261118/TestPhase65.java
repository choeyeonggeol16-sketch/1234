import java.util.*;
import java.lang.reflect.*;
public final class TestPhase65{
 static final Method RUN;static long checks,cases;
 static final int[] X={32744,32745,32744,32744,32738,32737,32738,32738,32738};
 static final int[] Y={32862,32862,32863,32863,32798,32797,32798,32798,32798};
 static{try{RUN=QuestLogic.class.getDeclaredMethod("npcPaidFloorTeleport",L1PcInstance.class,L1NpcInstance.class,int.class,int.class,int.class,int.class);RUN.setAccessible(true);}catch(Exception e){throw new RuntimeException(e);}}
 static void ok(boolean b,String text){checks++;if(!b)throw new AssertionError("case="+cases+" "+text);}
 static void caseOne(int floor,int mode,int adena,int tokens)throws Exception{
  cases++;L1PcInstance p=new L1PcInstance();int token=40104+floor,map=116+10*floor;
  p.inv.put(40308,adena);p.inv.put(token,tokens);
  p.failSql=mode==1;p.failCommit=mode==2;p.failCapacity=mode==3;
  Map<Integer,Integer> before=new HashMap<>(p.inv);
  RUN.invoke(null,p,new L1NpcInstance(),token,X[floor],Y[floor],map);
  boolean success=mode==0&&adena>=300&&tokens>=2;
  if(success){
   ok(p.inv.get(40308)==adena-300,"adena cost");ok(p.inv.get(token)==tokens-2,"token cost");
   ok(p.events.size()==2,"teleport/page once");
   ok(p.events.get(0).equals("teleport:"+X[floor]+":"+Y[floor]+":"+map),"exact legacy coordinates");
   ok(p.events.get(1).equals("html:"),"success page post-commit");
  }else{
   ok(p.inv.equals(before),"failed transaction fully rolled back");
   ok(p.events.size()==1&&p.events.get(0).equals("html:maetnob2"),"no teleport before commit");
  }
 }
 public static void main(String[]args)throws Exception{
  for(int round=0;round<150;round++)for(int floor=0;floor<9;floor++)for(int mode=0;mode<4;mode++)
   for(int money:new int[]{0,299,300,301})for(int token:new int[]{0,1,2,3})caseOne(floor,mode,money,token);
  System.out.printf("PHASE65 production Java21 typed PASS cases=%d checks=%d%n",cases,checks);
 }
}
