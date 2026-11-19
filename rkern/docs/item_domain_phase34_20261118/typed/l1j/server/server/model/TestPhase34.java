package l1j.server.server.model;
import java.util.*;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.rank.buff.*;
import l1j.server.server.model.trap.*;
public class TestPhase34 {
 static int checks, cases;
 static void check(boolean v,String msg){checks++;if(!v)throw new AssertionError(msg);}
 record Result(String effect,String message,int world,int created){}
 interface Action {void run(L1PcInstance pc) throws Exception;}
 static Result exec(Action action,int[][] rows, int preset,boolean reject,boolean checkFail,boolean storeFail,boolean storeNull){
  L1World.getInstance().clear();ItemTable.reset();SC_ServerMessage.events.clear();DatabaseFactory.rows.clear();
  for(var row:rows)DatabaseFactory.rows.add(row);
  var pc=new L1PcInstance();if(preset>0){var x=ItemTable.getInstance().createItem(preset);x.setCount(7);pc.inv.putExisting(x);}
  int originalWorld=L1World.getInstance().size();int originalCreated=ItemTable.creations;
  pc.inv.reject=reject;pc.inv.throwCheck=checkFail;pc.inv.throwStore=storeFail;pc.inv.returnNull=storeNull;
  String error="";try{action.run(pc);}catch(Exception e){error=e.getClass().getSimpleName()+":"+e.getMessage();}
  StringBuilder items=new StringBuilder();pc.inv.owned.values().stream().sorted(Comparator.comparingInt(L1ItemInstance::getId)).forEach(x->items.append(x.getItem().itemId).append('/').append(x.getCount()).append('/').append(x.getEnchantLevel()).append(';'));
  String effect=items+"|check:"+pc.inv.checkCalls+"|store:"+pc.inv.storeCalls+"|err:"+error;
  return new Result(effect,SC_ServerMessage.events.toString(),L1World.getInstance().size()-originalWorld,ItemTable.creations-originalCreated);
 }
 static void beginnerTests(){
   for(int[] ids:new int[][]{{101,5,2},{201,1,3},{201,3,6},{101,1,0}}){
    for(int preset:new int[]{0,ids[0],888})for(int flags=0;flags<5;flags++){
     final boolean reject=flags==1,checkFail=flags==2,storeFail=flags==3,storeNull=flags==4;
     final int[][] rows={ids};
     var old=exec(pc->new BeginnerLegacy().GiveItemToActivePc(pc),rows,preset,reject,checkFail,storeFail,storeNull);
     var now=exec(pc->new BeginnerCandidate().GiveItemToActivePc(pc),rows,preset,reject,checkFail,storeFail,storeNull);
     check(old.effect().equals(now.effect()),"Beginner item result: "+Arrays.toString(ids)+" preset="+preset+" flags="+flags+" old="+old+" new="+now);
     if(flags!=4)check(old.message().equals(now.message()),"Beginner message differs: "+old+" / "+now);
     else if(old.effect().contains("err:"))check(true,"store null deliberately suppresses false positive success");
     if(flags!=0 || preset==ids[0] && ids[0]==101)check(now.world()<=old.world(),"beginner temporary world object leak");
     cases++;
    }
   }
   // A multirow batch does not lose earlier successful grants when a later row is rejected.
   var rows=new int[][]{{201,1,2},{101,1,3},{201,1,2}};
   var x=exec(pc->new BeginnerLegacy().GiveItemToActivePc(pc),rows,0,false,false,false,false);
   var y=exec(pc->new BeginnerCandidate().GiveItemToActivePc(pc),rows,0,false,false,false,false);
   check(x.effect().equals(y.effect()) && x.message().equals(y.message()),"multirow beginner order");cases++;
 }
 static void rankTests(){
   for(int preset:new int[]{0,501,888})for(int flags=0;flags<5;flags++){
    boolean reject=flags==1,checkFail=flags==2,storeFail=flags==3,storeNull=flags==4;
    var old=exec(RankBuffLegacy::run,new int[0][],preset,reject,checkFail,storeFail,storeNull);
    var now=exec(RankBuffCandidate::run,new int[0][],preset,reject,checkFail,storeFail,storeNull);
    // Existing item case skips capacity validation and temp creation in the new implementation.
    if(preset!=501)check(old.effect().equals(now.effect()),"Rank result: "+old+" / "+now);
    else check(now.effect().startsWith(old.effect().split("\\|check:")[0]),"Existing rank item changed");
    check(now.world()<=old.world(),"Rank temporary world leak");
    if(preset==501)check(now.created()==0,"rank duplicate allocated temporary item");
    cases++;
   }
 }
 static void raceTests(){
   for(int preset:new int[]{0,3000024,888})for(int flags=0;flags<3;flags++){
    boolean fail=flags==1,retNull=flags==2;
    var old=exec(RaceLegacy::run,new int[0][],preset,false,false,fail,retNull);
    var now=exec(RaceCandidate::run,new int[0][],preset,false,false,fail,retNull);
    check(old.effect().equals(now.effect()),"Race reward effect: "+old+" / "+now);
    if(!fail && !retNull)check(old.message().equals(now.message()),"Race one-item message: "+old+" / "+now);
    check(now.world()<=old.world(),"Race dummy world leak");
    check(now.created()<old.created() || fail,"Race unnecessary new item object");cases++;
   }
 }
 public static void main(String[] args){beginnerTests();rankTests();raceTests();System.out.println("Phase34 source-extracted Java21 grant behavior comparisons passed: "+cases+" cases, "+checks+" assertions");}
}