package l1j.server.server.model.network.core.broadcast;
import java.util.*;
import l1j.server.server.model.*;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;
public final class WorldFanoutRegression87 {
 static void ck(boolean b,String s){if(!b)throw new AssertionError(s);}
 static L1PcInstance p(CIOServer owner,int id,int map,int team){
  var s=new CUserSocket(owner);var pc=new L1PcInstance(id,map,new CIOClient(s));pc.team=team;return pc;
 }
 static int n(L1PcInstance p){return p.client.socket.frames.size();}
 static CSharedBroadcast.NativeWireWriter wire(int id){return b->b.put((byte)id).put((byte)0x23);}
 static void verify(L1PcInstance p,int index,int id){byte[] x=p.client.socket.frames.get(index);
  ck(x.length==4,"frame len");ck(((x[0]&255)|((x[1]&255)<<8))==4,"header");
  ck((x[2]&255)==id,"frame payload");}
 static void drain(L1PcInstance...pc){for(var x:pc)x.client.socket.drain();}
 public static void main(String[]args){
  CIOServer owner=new CIOServer();var world=L1World.getInstance();
  var self=p(owner,100,1,10);var a=p(owner,101,1,10);var b=p(owner,102,1,20);
  var wrongMap=p(owner,103,2,10);var closed=p(owner,104,1,10);closed.client.socket.connected=false;
  world.visible=new ArrayList<>(List.of(self,a,b,wrongMap,closed,a));world.all=List.of(self,a,b,wrongMap,closed);
  int before=world.releases;
  CSharedBroadcast.sendWritten(self,wire(11),true,-1,p->p.get_current_combat_team_id()==10);
  ck(n(self)==0&&n(a)==2&&n(b)==0&&n(wrongMap)==0&&n(closed)==0,"sameworld/self/filter/duplicate");
  ck(world.releases==before+1,"fast borrowed list released");
  drain(self,a,b,wrongMap,closed);
  CSharedBroadcast.sendWrittenRecipients(List.of(self,a,self),wire(12));
  ck(n(self)==2&&n(a)==3,"explicit order, self, duplicate");verify(self,0,12);verify(a,2,12);
  drain(self,a,b,wrongMap,closed);
  CSharedBroadcast.sendWrittenMapTeamFrames(1,10,true,new CSharedBroadcast.NativeWireWriter[]{wire(21),wire(22)});
  ck(n(self)==4&&n(a)==7&&n(b)==0&&n(wrongMap)==0,"map and team filtered");
  verify(self,2,21);verify(self,3,22);verify(a,3,21);verify(a,4,22);
  drain(self,a,b,wrongMap,closed);
  a.shop=true;b.world=false;wrongMap.gm=true;
  CSharedBroadcast.sendWrittenAllPlayable(wire(31));
  ck(n(self)==5&&n(a)==7&&n(b)==0&&n(wrongMap)==1,"all playable filters");
  CSharedBroadcast.sendWrittenAllGms(wire(32));
  ck(n(wrongMap)==2&&n(self)==5,"GMs only");
  drain(self,a,b,wrongMap,closed);
  byte[] raw=new byte[]{5,6,7,8};
  try(var packet=CSharedBroadcast.openRaw(raw)){
   raw[0]=99;
   CSharedBroadcast.sendRecipient(packet,self);
  }
  ck(n(self)==6,"snapshot delivered");byte[] actual=self.client.socket.frames.get(5);
  ck(actual.length==6&&actual[2]==5&&actual[3]==6&&actual[4]==7&&actual[5]==8,"raw snapshot isolation");
  drain(self,a,b,wrongMap,closed);
  world.visible=new ArrayList<>(List.of(a,b));
  var item=new L1ItemInstance(999,1);
  int recipients=CSharedBroadcast.sendWrittenGroundItem(item,true,wire(41));
  ck(recipients==2&&a.known==1&&b.known==1,"ground item admission before known");
  drain(a,b);
  // Fast borrowed visibility must release on native encoder exception.
  world.visible=List.of(a);
  before=world.releases;
  boolean failed=false;
  try{CSharedBroadcast.sendWritten(self,x->{throw new IllegalStateException("injected encode failure");},true,-1,null);}
  catch(IllegalStateException expected){failed=true;}
  ck(failed&&world.releases==before+1,"exception borrows released");
  drain(a);
  System.out.println("87 real CSharedBroadcast: AOI filter/self/duplicate/group FIFO/Raw/ground item/exception release PASS");
 }
}
