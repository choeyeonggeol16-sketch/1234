import java.util.*;
public class TestPhase37 {
 record R(boolean result,String state,List<String> events,int orphans,String error,int writes){}
 static int cases,checks,fixCases;
 static void eq(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static R run(boolean candidate,boolean sealed,int id,int count,boolean existing,int fail){
  Trace.events.clear();L1World.getInstance().all.clear();ItemTable.next=1000;
  L1PcInstance pc=new L1PcInstance();CUserInventory inv=pc.inventory;
  if(existing){L1ItemInstance it=ItemTable.getInstance().createItem(id);it.count=9;inv.owned.put(it.id,it);}
  inv.reject=fail==1;inv.failCheck=fail==2;inv.failStore=fail==3;inv.storeNull=fail==4;inv.failUpdate=fail==5;inv.failSave=fail==6;
  boolean result=false;String error="";
  try{result=candidate?new PacketCandidate().grant(sealed,pc,id,count,5,128,3,true):new PacketLegacy().grant(sealed,pc,id,count,5,128,3,true);}
  catch(Exception e){error=e.getClass().getSimpleName()+":"+e.getMessage();}
  StringBuilder b=new StringBuilder();for(L1ItemInstance it:inv.owned.values())b.append(it.itemId).append("*").append(it.count).append("/+").append(it.enchant).append("/B").append(it.bless).append(";");
  int orphans=0;for(L1ItemInstance it:L1World.getInstance().all.values())if(!inv.owned.containsKey(it.id))orphans++;
  return new R(result,b.toString(),List.copyOf(Trace.events),orphans,error,inv.updates+inv.saves);
 }
 public static void main(String[] args){
  for(boolean sealed:new boolean[]{false,true})for(int id:new int[]{100,101})for(int count:new int[]{1,2,5})for(boolean existing:new boolean[]{false,true})for(int failure=0;failure<=6;failure++){
   R old=run(false,sealed,id,count,existing,failure),now=run(true,sealed,id,count,existing,failure);
   eq(old.state.equals(now.state)||sealed&&id==101&&existing&&failure!=4,"item state changed "+sealed+"/"+id+"/"+existing+"/"+failure+" "+old+" / "+now);
   eq(old.error.equals(now.error),"exception "+old+" / "+now);
   eq(now.orphans<=old.orphans,"object orphan increased "+old+" / "+now);
   if(failure==0 && (!existing || id==100)){eq(old.result==now.result && old.events.equals(now.events),"successful grant "+old+" / "+now);}
   if(failure==1 || failure==2 || failure==3)eq(old.result==now.result && old.events.equals(now.events),"failed grant semantics "+old+" / "+now);
   if(failure==4){eq(!now.result,"null insert cannot succeed "+now);fixCases++;}
   if(sealed && id==101 && existing && failure==0){eq(now.writes==old.writes,"post merge write count changed");fixCases++;}
   cases++;
  }
  System.out.println("Phase37 NPC reward typed PASS cases="+cases+" assertions="+checks+" corrected_failure_or_merge="+fixCases);
 }
}
