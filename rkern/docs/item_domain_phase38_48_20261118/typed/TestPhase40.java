import java.lang.reflect.*;
import java.util.*;
public final class TestPhase40 {
  private static int assertions;
  static void ok(boolean c,String msg){assertions++;if(!c)throw new AssertionError(assertions+": "+msg);}
  static void trial(Method method,int mode,Random rng)throws Exception{
    L1PcInstance pc=new L1PcInstance();
    pc.db.put(33,3);pc.cache.put(33,3);
    pc.inv.put(40654,1+rng.nextInt(5));
    if(mode==1)pc.inv.put(40654,0);
    if(mode==2)pc.failSql=true;
    if(mode==3)pc.failCommit=true;
    if(mode==4)pc.failCapacity=true;
    if(mode==5)pc.db.put(33,4);
    int old=pc.inv.get(40654);int beforeDb=pc.db.get(33);
    boolean handled=(Boolean)method.invoke(null,pc,new L1NpcInstance(),"a");
    ok(handled,"NPC action recognized");
    if(mode==0){
      ok(pc.inv.get(40654)==old-1,"material consumed once");
      ok(pc.db.get(33)==255&&pc.cache.get(33)==255,"quest committed");
      int t=pc.events.indexOf("teleport:32744:32927:483");
      ok(t>=1,"teleport emitted after quest cache publish");
      ok(pc.events.get(pc.events.size()-1).equals("html:"),"blank original page sent");
    }else{
      ok(pc.inv.get(40654)==old,"no item consumed on rollback");
      ok(pc.db.get(33)==beforeDb&&pc.cache.get(33)==3,"no quest advanced");
      ok(pc.events.stream().noneMatch(x->x.startsWith("teleport:")),"no teleport on failure");
      ok(pc.events.get(pc.events.size()-1).equals("html:"),"blank response on failure");
    }
  }
  public static void main(String[] args)throws Exception{
    Method m=QuestLogic.class.getDeclaredMethod("handleCrystalExit",L1PcInstance.class,L1NpcInstance.class,String.class);
    m.setAccessible(true);Random random=new Random(40);
    for(int r=0;r<700;r++)for(int mode=0;mode<6;mode++)trial(m,mode,random);
    L1PcInstance pc=new L1PcInstance();ok(!(Boolean)m.invoke(null,pc,new L1NpcInstance(),"b"),"unknown action unchanged");
    System.out.println("Phase40 extracted Java21 typed PASS cases=4201 assertions="+assertions);
  }
}
