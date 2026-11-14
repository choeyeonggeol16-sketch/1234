package l1j.server.server.model.network.core.broadcast;

import l1j.server.server.model.network.core.session.CIOClient;
import java.util.*;import l1j.server.server.model.*;import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.sc.packet.*;
public class BroadcastFollowupTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static L1PcInstance pc(int id){var p=new L1PcInstance();p.id=id;p.client=new CIOClient();return p;}
 static byte[] last(L1PcInstance p){var w=p.client.owner.wires;return w.get(w.size()-1);}
 public static void main(String[] args){
  var world=L1World.INSTANCE;var source=pc(1);var a=pc(2);var b=pc(3);b.client.owner.shared=false;var other=pc(4);other.map=9;var offline=pc(5);offline.client.connected=false;
  world.list.addAll(Arrays.asList(source,a,b,other,offline,null));
  for(int h=0;h<8;h++){
   SC_MoveChar.broadcast(source,-1,65536,h);check(Arrays.equals(last(a),last(b)),"move shared/legacy wire");check(last(a).length==17,"move size");
   SC_ChangeHeading.broadcastFast(source,0x81234567,h);check(Arrays.equals(last(a),last(b)),"heading shared/legacy wire");
   source.heading=h;SC_MoveChar.broadcast(source);check(Arrays.equals(last(a),last(b)),"implicit move offsets");
  }
  SC_RemoveObject.broadcast(source,0x91234567);check(Arrays.equals(last(a),last(b))&&last(a).length==9,"remove wire");
  SC_DoActionGFX.broadcast(source,257);check(Arrays.equals(last(a),last(b))&&last(a).length==8,"action wire");
  SC_SkillSound.broadcastFast(source,123,65537);check(Arrays.equals(last(a),last(b))&&last(a).length==15,"effect wire");
  byte[] effect=last(b);int prior=a.client.owner.wires.size();a.meff=2;SC_SkillSound.broadcastM(source,123,65537);check(a.client.owner.wires.size()==prior&&Arrays.equals(effect,last(b)),"M recipient option");
  a.meff=0;SC_SkillSound.broadcastWide(source,123,65537);check(world.radius==50&&Arrays.equals(last(a),last(b)),"wide radius");
  check(source.client.owner.wires.isEmpty()&&other.client.owner.wires.isEmpty()&&offline.client.owner.wires.isEmpty(),"self/map/connected filtering");
  check(!CTLMFilter.nearby(source,other,500,false),"cross-map filter");a.x=source.x+3;check(CTLMFilter.nearby(source,a,3,false)&&!CTLMFilter.nearby(source,a,2,false),"inclusive radius");
  check(!CTLMFilter.insideRectangle(0,0,1,-1,0,1)&&CTLMFilter.insideRectangle(-1,2,-1,1,0,2),"rectangle empty/inclusive edges");
  a.client.owner.fail=true;try{SC_ChangeHeading.broadcastFast(source,1,2);throw new AssertionError("fault not exercised");}catch(IllegalStateException expected){}
  check(world.borrowed==world.released&&CAsyncSendConstBuffer.opens==CAsyncSendConstBuffer.closes,"snapshot/source finally cleanup");
  System.out.println("PASS production five broadcast encoders:shared/legacy byte equivalence,8 headings,coordinate truncation,self/map/connection/M filter,wide radius50,rectangle/radius boundaries,borrow/source cleanup on exception (world/transport fixtures)");
 }
}
