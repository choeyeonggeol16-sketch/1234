import java.lang.reflect.*;
import java.util.*;
public final class TestPhase43 {
  static final Method ACTION; static int count;
  static {try{ACTION=QuestLogic.class.getDeclaredMethod("handleBlessingBuyback",L1PcInstance.class,L1NpcInstance.class,String.class);ACTION.setAccessible(true);}catch(Exception e){throw new RuntimeException(e);}}
  static void check(boolean b,String reason){count++;if(!b)throw new AssertionError(count+": "+reason);}
  static final String[][] MESSAGES={
    {"미소피아(성장의축복) 300개 매입 완료 / 수표 1장 지급","미소피아(방어의축복) 300개 매입 완료 / 수표 1장 지급","미소피아(공격의축복) 300개 매입 완료 / 수표 1장 지급"},
    {"메티스의 축복 주문서 300개 매입 완료 / 수표 1장 지급","메티스의 정성스런 스프 300개 매입 완료 / 수표 1장 지급","메티스의 정성스런 요리 300개 매입 완료 / 수표 1장 지급"},
    {"코마의 축복 코인 300개 매입 완료 / 수표 1장 지급"}
  };
  static final int[][] IDS={{510012,510013,510014},{3000128,3000129,3000130},{30104}};
  static void run(int group,int mask,int mode,int amount)throws Exception{
    L1PcInstance pc=new L1PcInstance();
    for(int i=0;i<IDS[group].length;i++)pc.inv.put(IDS[group][i],(mask&(1<<i))!=0 ? amount : amount-300);
    if(mode==1)pc.failSql=true;
    if(mode==2)pc.failCommit=true;
    if(mode==3)pc.failCapacity=true;
    Map<Integer,Integer> before=new HashMap<>(pc.inv);
    check((Boolean)ACTION.invoke(null,pc,new L1NpcInstance(),Integer.toString(group+1)),"action routed");
    int num=0;for(int i=0;i<IDS[group].length;i++)if(before.get(IDS[group][i])>=300)num++;
    boolean success=mode==0 && num>0;
    if(success){
      check(pc.inv.getOrDefault(400254,0)==num,"exact voucher quantity");
      for(int i=0;i<IDS[group].length;i++)check(pc.inv.get(IDS[group][i])==before.get(IDS[group][i])-(before.get(IDS[group][i])>=300?300:0),"debit exact 300");
      List<String> real=new ArrayList<>();for(String event:pc.events)if(event.startsWith("packet:"))real.add(event.substring(7));
      List<String> expected=new ArrayList<>();for(int i=0;i<IDS[group].length;i++)if(before.get(IDS[group][i])>=300)expected.add(MESSAGES[group][i]);
      check(real.equals(expected),"original buyback message order");
    }else{
      check(pc.inv.equals(before),"no partial transfer on fail");
      check(pc.inv.getOrDefault(400254,0)==0,"no phantom voucher");
      check(pc.events.stream().noneMatch(x->x.contains("300개 매입 완료")),"no false success message");
    }
  }
  public static void main(String[]args)throws Exception{
    for(int repeat=0;repeat<80;repeat++)for(int group=0;group<3;group++)for(int mask=0;mask<(1<<IDS[group].length);mask++)for(int mode=0;mode<4;mode++)for(int amount:new int[]{299,300,301,1000})run(group,mask,mode,amount);
    L1PcInstance pc=new L1PcInstance();check(!(Boolean)ACTION.invoke(null,pc,new L1NpcInstance(),"other"),"unknown action not consumed");
    System.out.println("Phase43 actual Java21 typed buyback PASS scenarios="+(80*17*4*4+1)+" assertions="+count);
  }
}
