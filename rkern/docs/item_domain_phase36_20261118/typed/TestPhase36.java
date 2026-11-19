import java.util.*;
public final class TestPhase36 {
 interface Action {void run(int item,int stack,int times);}
 static int cases,checks;
 static void eq(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
 record Result(String inventory,int checks,int stores,int world,int orphans,String error){}
 static Result evaluate(Action runner,int id,int stack,int times,boolean existing,int mode){
  L1World world=L1World.getInstance();world.reset();ItemTable.next=1000;L1GroundInventory gr=world.ground;
  if(existing){L1ItemInstance it=ItemTable.getInstance().createItem(id);it.count=3;gr.owned.put(it.id,it);}
  gr.reject=mode==1;gr.failCheck=mode==2;gr.failStore=mode==3;gr.returnNull=mode==4;
  String error="";try{runner.run(id,stack,times);}catch(RuntimeException ex){error=ex.getClass().getSimpleName()+":"+ex.getMessage();}
  StringBuilder inv=new StringBuilder();for(L1ItemInstance it:gr.owned.values())inv.append(it.itemId).append(":").append(it.count).append(";");
  int orphans=0;for(L1ItemInstance it:world.world.values())if(!gr.owned.containsKey(it.id))orphans++;
  return new Result(inv.toString(),gr.checkCalls,gr.storeCalls,world.world.size(),orphans,error);
 }
 static void test(String name,Action old,Action newer){
  for(int item:new int[]{100,101})for(int stack:new int[]{1,2,4})for(int times:new int[]{1,2,5})for(boolean existing:new boolean[]{false,true})for(int mode=0;mode<5;mode++){
   Result before=evaluate(old,item,stack,times,existing,mode);
   Result after=evaluate(newer,item,stack,times,existing,mode);
   eq(before.inventory.equals(after.inventory),name+" item state "+before+" / "+after);
   eq(before.checks==after.checks && before.stores==after.stores,name+" call counts "+before+" / "+after);
   eq(before.error.equals(after.error),name+" errors "+before+" / "+after);
   eq(after.orphans<=before.orphans,name+" orphan regression "+before+" / "+after);
   if(mode!=0 || (item==100 && existing))eq(after.orphans==0,name+" unowned world object remains "+after);
   cases++;
  }
 }
 public static void main(String[] args){
  test("UB",(id,s,c)->new L1UltimateBattleLegacy().run(id,s,c),(id,s,c)->new L1UltimateBattleCandidate().run(id,s,c));
  test("NewUB",(id,s,c)->new L1NewUltimateBattleLegacy().run(id,s,c),(id,s,c)->new L1NewUltimateBattleCandidate().run(id,s,c));
  System.out.println("Phase36 ground reward regression PASS cases="+cases+" assertions="+checks);
 }
}
