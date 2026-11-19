import java.lang.reflect.*;
import java.util.*;
public final class TestPhase49_53 {
  static final Method EXCHANGE, QUEST, LEVEL15;
  static long checks=0, scenarios=0;
  static {try {
    EXCHANGE=QuestLogic.class.getDeclaredMethod("npcItemExchange",L1PcInstance.class,L1NpcInstance.class,int.class,int.class,String.class,String.class);
    QUEST=QuestLogic.class.getDeclaredMethod("npcQuestDebit",L1PcInstance.class,L1NpcInstance.class,int.class,int.class,int.class,int.class,int.class,String.class,String.class);
    LEVEL15=QuestLogic.class.getDeclaredMethod("level15ItemReceipt",L1PcInstance.class,L1NpcInstance.class,boolean.class,boolean.class);
    for(Method m:List.of(EXCHANGE,QUEST,LEVEL15))m.setAccessible(true);
  }catch(Exception e){throw new RuntimeException(e);}}
  static void ok(boolean success,String message){checks++;if(!success)throw new AssertionError("scenario="+scenarios+" "+message);}
  static void inject(L1PcInstance p,int mode) {p.failSql=mode==1;p.failCommit=mode==2;p.failCapacity=mode==3;}
  static void page(L1PcInstance p,String name) {ok(!p.events.isEmpty()&&p.events.get(p.events.size()-1).equals("html:"+name),"HTML "+name+" events="+p.events);ok(p.events.stream().filter(e->e.startsWith("html:")).count()==1,"one HTML");}
  static void exchange(int i,int mode,int count)throws Exception {
    scenarios++;
    int[] inputs={41093,41094,41095,41096},outputs={41097,41918,41919,41920};
    String[] pages={"my5","my6","my7","my8"};
    L1PcInstance pc=new L1PcInstance();pc.inv.put(inputs[i],count);inject(pc,mode);
    EXCHANGE.invoke(null,pc,new L1NpcInstance(),inputs[i],outputs[i],pages[i],"my4");
    boolean success=mode==0&&count>0;
    ok(pc.inv.get(inputs[i])==count-(success?1:0),"material");
    ok(pc.inv.getOrDefault(outputs[i],0)==(success?1:0),"reward");
    page(pc,success?pages[i]:"my4");
  }
  static void quest(int i,int mode,int count,boolean staleCache)throws Exception {
    scenarios++;
    int[] questId={31,32,32},before={1,2,3},after={2,3,4},materials={40647,40644,40630},needed={3,1,1};
    String[] yes={"cadmus6","kamyla10","kamyla13"},no={"cadmus5","kamyla10","kamyla13"};
    L1PcInstance pc=new L1PcInstance();int q=questId[i],step=before[i];pc.db.put(q,mode==4?step+10:step);pc.cache.put(q,staleCache?step+1:step);pc.inv.put(materials[i],count);inject(pc,mode);
    var inv=new HashMap<>(pc.inv);var db=new HashMap<>(pc.db);
    QUEST.invoke(null,pc,new L1NpcInstance(),q,step,after[i],materials[i],needed[i],yes[i],no[i]);
    boolean success=mode==0&&!staleCache&&count>=needed[i];
    if(success){ok(pc.inv.get(materials[i])==count-needed[i],"debit");ok(pc.db.get(q)==after[i],"durable quest");ok(pc.cache.get(q)==after[i],"quest published");}
    else {ok(pc.inv.equals(inv),"rollback material");ok(pc.db.equals(db),"rollback quest");ok(pc.cache.get(q)==(staleCache?step+1:step),"rollback cache");}
    page(pc,success?yes[i]:no[i]);
  }
  static int[][] materials(boolean elf,boolean start){if(start)return new int[0][0];return elf?new int[][]{{210091,10},{40510,1},{40511,1},{40512,1},{41080,1}}:new int[][]{{210088,1},{210089,1},{210090,1}};}
  static int[][] output(boolean elf,boolean start){return elf?(start?new int[][]{{210092,5},{210093,1}}:new int[][]{{505,1},{210004,1}}):(start?new int[][]{{210087,1}}:new int[][]{{502,1},{210020,1}});}
  static void level15(boolean elf,boolean start,int mode,int missing,boolean staleCache)throws Exception {
    scenarios++;
    L1PcInstance pc=new L1PcInstance();int step=start?0:1;
    pc.db.put(1,mode==4?10:step);pc.cache.put(1,staleCache?99:step);
    int[][] material=materials(elf,start),reward=output(elf,start);
    for(var m:material)pc.inv.put(m[0],m[1]);
    if(missing>=0&&missing<material.length)pc.inv.put(material[missing][0],material[missing][1]-1);
    inject(pc,mode);var inv=new HashMap<>(pc.inv);var db=new HashMap<>(pc.db);
    LEVEL15.invoke(null,pc,new L1NpcInstance(),elf,start);
    boolean success=mode==0&&!staleCache&&missing==-1;
    if(success){
      ok(pc.db.get(1)==(start?1:255),"durable progression");ok(pc.cache.get(1)==(start?1:255),"in-memory progression");
      for(var m:material)ok(pc.inv.get(m[0])==inv.get(m[0])-m[1],"all costs");
      for(var r:reward)ok(pc.inv.getOrDefault(r[0],0)==r[1],"all rewards");
    }else{ok(pc.inv.equals(inv),"no partial reward/cost");ok(pc.db.equals(db),"no early quest persistence");ok(pc.cache.get(1)==(staleCache?99:step),"no early step");}
    String good=elf?(start?"silrein4":"silrein7"):(start?"prokel3":"prokel5");
    String bad=elf?(start?"silrein4":"silrein8"):(start?"prokel3":"prokel6");
    page(pc,success?good:bad);
  }
  public static void main(String[] args)throws Exception {
    for(int round=0;round<80;round++){
      for(int i=0;i<4;i++)for(int mode=0;mode<4;mode++)for(int count=0;count<4;count++)exchange(i,mode,count);
      for(int i=0;i<3;i++)for(int mode=0;mode<5;mode++)for(int count=0;count<5;count++)for(int stale=0;stale<2;stale++)quest(i,mode,count,stale==1);
      for(int elf=0;elf<2;elf++)for(int start=0;start<2;start++)for(int mode=0;mode<5;mode++)for(int missing=-1;missing<materials(elf==1,start==1).length;missing++)for(int stale=0;stale<2;stale++)level15(elf==1,start==1,mode,missing,stale==1);
    }
    System.out.println("PHASE49-53 extracted-production Java21 typed PASS scenarios="+scenarios+" assertions="+checks);
  }
}
