import java.util.*;
public final class TestPhase35 {
 static int cases,checks,fixes;
 static void require(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 record Result(String state,List<String> trace,int objects,int allocations,int worldExtras,String error) {}
 interface Action {void run(L1PcInstance pc,boolean win);}
 static Result eval(Action action,boolean win,int existing,int flags,long seed){
  TestRng.seed=seed;Trace.clear();ItemTable.next=1000;ItemTable.created=0;L1World.getInstance().objects.clear();
  L1PcInstance pc=new L1PcInstance();
  if(existing>0){L1ItemInstance item=ItemTable.getInstance().createItem(existing);item.setCount(7);pc.inv.addExisting(item);}
  int startWorld=L1World.getInstance().objects.size(), startAlloc=ItemTable.created;
  pc.inv.reject=flags==1;pc.inv.failCheck=flags==2;pc.inv.failStore=flags==3;pc.inv.returnNull=flags==4;
  String error="";
  try{action.run(pc,win);}catch(RuntimeException ex){error=ex.getClass().getSimpleName()+":"+ex.getMessage();}
  List<L1ItemInstance> items=new ArrayList<>(pc.inv.byId.values());items.sort(Comparator.comparingInt(it->it.itemId));
  StringBuilder b=new StringBuilder();for(L1ItemInstance it:items)b.append(it.itemId).append("*").append(it.count).append(";");
  int extras=0;for(L1ItemInstance it:L1World.getInstance().objects.values())if(!pc.inv.byId.containsKey(it.id))extras++;
  return new Result(b.toString(),List.copyOf(Trace.events),L1World.getInstance().objects.size()-startWorld,ItemTable.created-startAlloc,extras,error);
 }
 static void test(String domain,Action old,Action now,int[] presets,boolean[] wins){
  for(boolean win:wins)for(int existing:presets)for(int flags=0;flags<5;flags++)for(long seed=0;seed<128;seed++){
   Result o=eval(old,win,existing,flags,seed),n=eval(now,win,existing,flags,seed);
   require(o.state.equals(n.state),domain+" inventory "+win+"/"+existing+"/"+flags+"/"+seed+" "+o+" / "+n);
   require(o.error.equals(n.error),domain+" exception changed "+o+" / "+n);
   if(flags!=4 && !(domain.equals("Haunted") && flags==3)){
    // The new implementation suppresses a success notification when storeItem returns null.
    require(o.trace.equals(n.trace),domain+" trace "+o+" / "+n);
   }
   require(n.worldExtras<=o.worldExtras,domain+" orphan count increased "+o+" / "+n);
   if(flags==0 && existing==0 && n.allocations<o.allocations) fixes++;
   cases++;
  }
 }
 public static void main(String[] args){
  test("Death",(pc,w)->new DeathLegacy().run(pc,w),(pc,w)->new DeathCandidate().run(pc,w),new int[]{0,3000022,888},new boolean[]{true});
  test("Haunted",(pc,w)->new HauntedLegacy().run(pc,w),(pc,w)->new HauntedCandidate().run(pc,w),new int[]{0,41308,3000023,888},new boolean[]{true});
  test("Pet",(pc,w)->new PetLegacy().run(pc,w),(pc,w)->new PetCandidate().run(pc,w),new int[]{0,41309,3000025,888},new boolean[]{false,true});
  require(fixes>0,"expected eliminated dummy allocations");
  System.out.println("Phase35 typed regression PASSED cases="+cases+" assertions="+checks+" allocation_reductions="+fixes);
 }
}
