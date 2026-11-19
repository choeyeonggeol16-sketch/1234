import java.lang.reflect.*;
import java.util.*;
public final class TestPhase44_45 {
 static int checks;static Method exchange,simizz;
 static{try{
  for(Method m:QuestLogic.class.getDeclaredMethods())if(m.getName().equals("questItemExchange")&&m.getParameterCount()==12)exchange=m;
  simizz=QuestLogic.class.getDeclaredMethod("simizzReceipt",L1PcInstance.class,L1NpcInstance.class);
  if(exchange==null)throw new IllegalStateException("Original production exchange method missing");
  exchange.setAccessible(true);simizz.setAccessible(true);
 }catch(Exception e){throw new RuntimeException(e);}}
 static void yes(boolean b,String m){checks++;if(!b)throw new AssertionError("assertion "+checks+" "+m);}
 static void recipe(int kind,int mode,Random rng)throws Exception{
  L1PcInstance pc=new L1PcInstance();boolean two=kind==1;
  int quest=two?29:28,material=two?40700:40714,other=two?40710:0;
  pc.db.put(quest,3);pc.cache.put(quest,3);pc.inv.put(material,1+rng.nextInt(3));
  if(two)pc.inv.put(other,1+rng.nextInt(3));
  if(mode==1)pc.inv.put(material,0);
  if(mode==2&&two)pc.inv.put(other,0);
  if(mode==3)pc.failSql=true;
  if(mode==4)pc.failCommit=true;
  if(mode==5)pc.failCapacity=true;
  if(mode==6)pc.db.put(quest,4);
  Map<Integer,Integer> initial=new HashMap<>(pc.inv), db=new HashMap<>(pc.db);
  Object[] args={pc,new L1NpcInstance(),quest,3,255,material,1,other,40647,0,two?"rudian8":"doil8",two?"rudian9":"doil7"};
  exchange.invoke(null,args);
  boolean success=mode==0 || mode==2&&!two;
  if(success){
   yes(pc.inv.get(material)==initial.get(material)-1,"first material debit");
   if(two)yes(pc.inv.get(other)==initial.get(other)-1,"second material debit");
   yes(pc.inv.get(40647)==1,"reward");
   yes(pc.db.get(quest)==255&&pc.cache.get(quest)==255,"quest and cache committed");
   yes(pc.events.contains("html:"+(two?"rudian8":"doil8")),"success page");
  }else{
   yes(pc.inv.equals(initial),"no partial consumption");
   yes(pc.db.equals(db)&&pc.cache.get(quest)==3,"no quest success before receipt");
   yes(pc.events.stream().noneMatch(e->e.startsWith("reward:")),"no reward notice on fail");
   yes(pc.events.contains("html:"+(two?"rudian9":"doil7")),"failure page");
  }
 }
 static void simizz(int mode,Random rng)throws Exception{
  L1PcInstance pc=new L1PcInstance();pc.db.put(27,1);pc.cache.put(27,1);
  for(int id:new int[]{40661,40662,40663})pc.inv.put(id,1+rng.nextInt(5));
  if(mode>=1&&mode<=3)pc.inv.put(40660+mode,0);
  if(mode==4)pc.failCommit=true;
  if(mode==5)pc.failSql=true;
  if(mode==6)pc.failCapacity=true;
  if(mode==7)pc.db.put(27,99);
  Map<Integer,Integer> inventory=new HashMap<>(pc.inv),db=new HashMap<>(pc.db);
  simizz.invoke(null,pc,new L1NpcInstance());
  if(mode==0){
   for(int id:new int[]{40661,40662,40663})yes(pc.inv.get(id)==inventory.get(id)-1,"all 3 costs");
   yes(pc.inv.get(20044)==1,"single result 20044");
   yes(pc.db.get(27)==2&&pc.cache.get(27)==2,"quest2 persist and apply");
   yes(pc.events.contains("reward:i20044"),"one reward message");
   yes(pc.events.contains("html:SIMIZZ8"),"SIMIZZ success html");
  } else {
   yes(pc.inv.equals(inventory),"no partial item state");
   yes(pc.db.equals(db)&&pc.cache.get(27)==1,"no partial quest state");
   yes(pc.events.stream().noneMatch(e->e.startsWith("reward:")),"no false success");
   yes(pc.events.contains("html:SIMIZZ9"),"SIMIZZ fail html");
  }
 }
 public static void main(String[]args)throws Exception{
  Random rng=new Random(4455);
  for(int r=0;r<600;r++){for(int k=0;k<2;k++)for(int m=0;m<7;m++)recipe(k,m,rng);for(int m=0;m<8;m++)simizz(m,rng);}
  System.out.println("Phase44-45 extracted-production Java21 typed PASS scenarios="+(600*(14+8))+" assertions="+checks);
 }
}
