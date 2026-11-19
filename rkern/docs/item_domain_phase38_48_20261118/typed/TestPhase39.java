import java.lang.reflect.*;
import java.util.*;
public class TestPhase39 {
  static int checked=0;
  static void ok(boolean yes,String msg){checked++;if(!yes)throw new AssertionError(checked+" "+msg);}
  static final Method method;
  static {try{method=QuestLogic.class.getDeclaredMethod("handleLizardQuest",L1PcInstance.class,L1NpcInstance.class,String.class);method.setAccessible(true);}catch(Exception e){throw new RuntimeException(e);}}
  static void run(String action,int mode)throws Exception{
    L1PcInstance pc=new L1PcInstance();pc.db.put(34,2);pc.cache.put(34,2);
    pc.inv.put(40633,1);pc.inv.put(40634,1);
    if(mode==1)pc.inv.put("B".equals(action)?40633:40634,0);
    if(mode==2)pc.failSql=true;
    if(mode==3)pc.failCapacity=true;
    if(mode==4)pc.failCommit=true;
    if(mode==5)pc.db.put(34,0);
    Map<Integer,Integer> originalInv=new HashMap<>(pc.inv);
    Map<Integer,Integer> originalDb=new HashMap<>(pc.db);
    ok((Boolean)method.invoke(null,pc,new L1NpcInstance(),action),"lizard action handled");
    if("A".equals(action)){
      ok(pc.db.get(34)==1 && pc.cache.get(34)==1,"lizard A persistent");
      ok(pc.events.contains("html:lelder5"),"lizard A page");
      ok(pc.inv.equals(originalInv),"lizard A no item");return;
    }
    int resource="B".equals(action)?40633:40634;
    int next="B".equals(action)?3:255;
    boolean success=mode==0;
    if(success){
      ok(pc.inv.get(resource)==0,"lizard debit material");
      if("C".equals(action)){
        ok(pc.inv.get(20167)==1,"lizard reward once");
        ok(pc.events.contains("reward:i20167"),"lizard reward message");
      }
      ok(pc.db.get(34)==next && pc.cache.get(34)==next,"lizard quest and receipt committed");
    }else{
      ok(pc.inv.equals(originalInv),"lizard failure no partial items");
      ok(pc.db.equals(originalDb),"lizard failure no quest progress");
      if(mode==1&&"C".equals(action))ok(pc.events.contains("missing:i40634"),"lizard missing-material 337 preserved");
      ok(pc.events.stream().noneMatch(x->x.startsWith("reward:")),"lizard no false reward message");
    }
    ok(pc.events.contains("html:"+("B".equals(action)?"lelder10":"lelder13")),"lizard html response");
  }
  public static void main(String[]args)throws Exception{
    for(int i=0;i<500;i++)for(String s:new String[]{"A","B","C"})for(int mode=0;mode<6;mode++)run(s,mode);
    L1PcInstance pc=new L1PcInstance();ok(!(Boolean)method.invoke(null,pc,new L1NpcInstance(),"Q"),"invalid lizard action unhandled");
    System.out.println("Phase39 actual handler typed regression PASS checks="+checked+" scenarios="+(500*3*6+1));
  }
}
