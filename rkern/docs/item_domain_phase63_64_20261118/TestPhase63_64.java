import java.util.*;
import java.lang.reflect.*;
public final class TestPhase63_64 {
 static final Method METHOD;static long cases,checks;
 static{try{METHOD=QuestLogic.class.getDeclaredMethod("npcSingleMaterialExchange",L1PcInstance.class,L1NpcInstance.class,int.class,int.class,int.class,String.class,String.class,String.class);METHOD.setAccessible(true);}catch(Exception e){throw new RuntimeException(e);}}
 static void check(boolean c,String m){checks++;if(!c)throw new AssertionError("case="+cases+" "+m);}
 static void test(int variant,int mode,int count,boolean owned)throws Exception{
  cases++;
  boolean chef=variant==4;int id=chef?41921:40308;int amount=chef?45:10000000;
  int out=chef?410055:variant==0?14000000:variant==1?1400001:variant==2?1400002:1400003;
  L1PcInstance p=new L1PcInstance();p.inv.put(id,count);if(owned&&!chef)p.inv.put(410039,1);
  p.failSql=mode==1;p.failCommit=mode==2;p.failCapacity=mode==3;
  Map<Integer,Integer> before=new HashMap<>(p.inv);
  // A player holding the DRUGA pocket is rejected by the dispatcher, not passed to the helper.
  if(chef||!owned)METHOD.invoke(null,p,new L1NpcInstance(),id,amount,out,
       chef?null:"veil8",chef?"suschef5":"veil4",chef?"$8538":null);
  boolean success=count>=amount&&mode==0&&(chef||!owned);
  if(success){
   check(p.inv.get(id)==count-amount,"debit cost exactly once");
   check(p.inv.getOrDefault(out,0)==1,"reward exactly once");
   check(p.events.size()==(chef?1:2),"correct number of packets");
   check(p.events.get(0).equals("missing:"+(chef?"$8538":"i"+out)),"old 403 message value");
   if(!chef)check(p.events.get(1).equals("html:veil8"),"success page follows reward");
  }else{
   check(p.inv.equals(before),"no partial mutation on failure");
   if(owned&&!chef)check(p.events.isEmpty(),"preowned pocket guard");
   else check(p.events.size()==1&&p.events.get(0).equals("html:"+(chef?"suschef5":"veil4")),"failure page only");
  }
 }
 public static void main(String[]args)throws Exception{
  for(int round=0;round<50;round++)for(int v=0;v<5;v++)for(int mode=0;mode<4;mode++)
   for(int x:new int[]{0,1,44,45,9999999,10000000,10000001})for(int own=0;own<2;own++)test(v,mode,x,own!=0);
  System.out.printf("PHASE63-64 Java21 production method PASS cases=%d assertions=%d%n",cases,checks);
 }
}
