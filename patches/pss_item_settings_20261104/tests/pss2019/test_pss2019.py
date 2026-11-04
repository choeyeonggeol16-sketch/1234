#!/usr/bin/env python3
"""Exercise actual PSS parsing/sending, CIO session CAS and real packet dispatch."""
from pathlib import Path
import os,re,subprocess,tempfile

ROOT=Path(__file__).resolve().parents[2]
CP=os.pathsep.join([os.environ.get('ITEM_BUILD_CLASSES',str(ROOT/'build/item-processing/classes')),
    *map(str,Path(os.environ.get('ITEM_DEPS_DIR',str(ROOT/'lib'))).glob('*.jar'))])
files={}
def put(name,text):files[Path(name+'.java')]=text
put('l1j/server/server/model/map/L1Map','package l1j.server.server.model.map;public class L1Map {public boolean inside=true;public boolean isInMap(int x,int y){return inside;}}')
put('l1j/server/server/model/Instance/L1PcInstance','''package l1j.server.server.model.Instance;
import l1j.server.server.model.network.core.CIOClient;public class L1PcInstance {
 public CIOClient client;public boolean gm=true,dead,teleport,shop,logout,auto;public int stamp=7,map=4,resets,seeded,guidance;public boolean potion;
 public final l1j.server.server.model.map.L1Map loc=new l1j.server.server.model.map.L1Map();
 public CIOClient getNetConnection(){return client;}public boolean isGm(){return gm;}public boolean isDead(){return dead;}public boolean get_teleport(){return teleport;}public boolean isPrivateShop(){return shop;}public boolean isLogoutStarted(){return logout;}
 public int getSessionStamp(){return stamp;}public int getMapId(){return map;}public l1j.server.server.model.map.L1Map getMap(){return loc;}public int getX(){return 10;}public int getY(){return 10;}public int getId(){return 77;}public String getName(){return "test";}
 public final l1j.server.server.model.L1PcInventory inventory=new l1j.server.server.model.L1PcInventory();public l1j.server.server.model.L1PcInventory getInventory(){return inventory;}
 public boolean getAutoHunt(){return auto;}public void setAutoHunt(boolean flag){auto=flag;}public void setAutoPotion(boolean flag){potion=flag;}public void setAutoMapId(int map){}public void setAutoPolyID(int sprite){}public int getCurrentSpriteId(){return 123;}
}''')
put('l1j/server/server/templates/L1Item','package l1j.server.server.templates;public class L1Item {public final int id;public final String name;public L1Item(int id,String name){this.id=id;this.name=name;}public int getItemId(){return id;}public String getName(){return name;}}')
put('l1j/server/server/model/Instance/L1ItemInstance','package l1j.server.server.model.Instance;public class L1ItemInstance {private final int id;private final l1j.server.server.templates.L1Item item;public long count=1;public L1ItemInstance(int id,l1j.server.server.templates.L1Item item){this.id=id;this.item=item;}public int getId(){return id;}public int getItemId(){return item.getItemId();}public l1j.server.server.templates.L1Item getItem(){return item;}public long getCount(){return count;}}')
put('l1j/server/server/model/L1PcInventory','package l1j.server.server.model;public class L1PcInventory {public final java.util.Map<Integer,l1j.server.server.model.Instance.L1ItemInstance> owned=new java.util.HashMap<>();public int consumeCalls;public l1j.server.server.model.Instance.L1ItemInstance getItem(int id){return owned.get(id);}public boolean consumeItem(int id,int count){consumeCalls++;return true;}}')
put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage','package l1j.server.server.model.network.core.sc.packet;public class SC_SystemMessage {public static boolean send(l1j.server.server.model.Instance.L1PcInstance pc,String text){return true;}}')
transport=ROOT/'tests/item_processing/stubs_transport/l1j/server/server/model/network/core'
put('l1j/server/server/model/network/core/CIOFrame',(transport/'CIOFrame.java').read_text())
socket=(transport/'CIOUserSocket.java').read_text();socket=socket[:socket.index('    public void sendLegacy(')]+'}\n';socket=socket.replace('import l1j.server.server.model.network.packet.ServerPacket;','')
socket=socket.replace('public int begins, commits, aborts, active;','public int begins, commits, aborts, active; public final java.util.List<byte[]> frames=new java.util.ArrayList<>(); public boolean isOpen(){return connected;} public String getRemoteAddress(){return "127.0.0.1";}')
socket=socket.replace('checksum = length','frames.add(lastBytes);\n        checksum = length')
put('l1j/server/server/model/network/core/CIOUserSocket',socket)

ah=(ROOT/'src/l1j/server/server/model/character/autohunt/AHExecutor.java').read_text()
def method(name):
 start=ah.index('    public void '+name+'(');a=ah.index('{',start);depth=1;i=a+1
 while depth:
  if ah[i]=='{':depth+=1
  elif ah[i]=='}':depth-=1
  i+=1
 return ah[start:i]
put('l1j/server/server/model/character/pss/AutoHarness','''package l1j.server.server.model.character.pss;
import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.network.core.CIOClient;
class AutoHarness {
 static class Config {static boolean AUTO_HUNT_DEFAULT_POTION=true;}
 static class SC_SystemMessage {static void send(L1PcInstance pc,String text){}}
 static class AHProcessor {static final AHProcessor INSTANCE=new AHProcessor();static AHProcessor getInstance(){return INSTANCE;}void seedKnownTargets(L1PcInstance pc){pc.seeded++;}}
 static class CIOThreadCallback {static final CIOThreadCallback INSTANCE=new CIOThreadCallback();static CIOThreadCallback getInstance(){return INSTANCE;}static boolean allow;
 interface Handle {boolean isCancelled();}Handle startAutoHunt(int id,long ms){return allow?()->false:null;}void stopAutoHunt(L1PcInstance pc){} }
 void resetAuto(L1PcInstance pc){pc.resets++;}void sendGuidancePackets(L1PcInstance pc){pc.guidance++;}
'''+method('startAutoHunt')+'\n'+method('LogoutAutoHunt')+'\n'+method('endAutoHunt').replace('toCharacterRefresh(pc);','/* No socket refresh in this boundary harness. */')+'\n}')
put('l1j/server/server/model/character/pss/PssContract',r'''package l1j.server.server.model.character.pss;
import java.util.*;import java.util.concurrent.*;import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.network.core.*;import l1j.server.server.model.network.utill.ConnectionState;import l1j.server.server.packets.Protocol.*;
class PssContract {
 static final long NOW=1_000_000_000L;static int checks,groups;
 record Fixture(CIOClient client,L1PcInstance pc,CIOUserSocket socket){}
 static Fixture fixture(){var socket=new CIOUserSocket();var client=new CIOClient(socket,false);var pc=new L1PcInstance();pc.client=client;client.setActiveChar(pc);client.setConnectionState(ConnectionState.IN_GAME);client.publishGameSessionReady(pc);return new Fixture(client,pc,socket);}
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static byte[] bytes(String hex){return HexFormat.of().parseHex(hex);}
 static String last(Fixture f){return HexFormat.of().formatHex(f.socket.lastBytes);}
 static void start(Fixture f,String body,long now){byte[] b=bytes(body);PlaySupport2019.start(f.client,b,0,b.length,now);}
 static void time(Fixture f,long now){PlaySupport2019.time(f.client,new byte[0],0,0,now);}
 static void finish(Fixture f,long now){PlaySupport2019.finish(f.client,new byte[]{8,4},0,2,now);}
 static void group(String name,Runnable test){test.run();groups++;System.out.println("PASS "+name);}
public static boolean canBypassMapRestrictions(L1PcInstance pc) {
        if (pc == null || !pc.isGm()) return false;
        final var client = pc.getNetConnection();
        final var session = client == null ? null : client.getAutomationSession();
        return client == null || !client.requiresPlayerTeleportRules()
                && (session == null || session == ClientAutomationSession.SERVER);
    }
 public static void main(String[] args)throws Exception {
  java.util.logging.Logger.getLogger(PlaySupport2019.class.getName()).setLevel(java.util.logging.Level.OFF);
  group("root/nested modes and exact 2102 success framing",()->{for(String body:new String[]{"0801","12020802","080112020803","08011a0200ff","08010000","120408020000","08011a01000000"}){var f=fixture();start(f,body,NOW);check(last(f).equals("b4360808000000"),"start wire "+last(f));check(f.client.getAutomationSession()!=null&&!f.pc.auto,"wrong hunting owner");check(f.socket.active==0,"lease leak");}});
  group("same-mode retry preserves deadline; mode change rejected",()->{var f=fixture();start(f,"0801",NOW);var session=f.client.getAutomationSession();start(f,"0801",NOW+1_000_000_000L);check(f.client.getAutomationSession()==session,"retry extended session");start(f,"0802",NOW);check(last(f).equals("b4360808630000")&&f.client.getAutomationSession()==session,"mode switch accepted");});
  group("600-second time and finish response fields",()->{var f=fixture();start(f,"0801",NOW);time(f,NOW+1_000_000_000L);check(last(f).equals("b43b0808d7040000"),"599 time wire "+last(f));finish(f,NOW);check(last(f).equals("b4380808d8040000"),"finish wire");check(f.client.getAutomationSession()==null,"finish retained session");finish(f,NOW);check(last(f).equals("b4380808000000"),"idempotent stop");});
  group("expiry/death/map-change force stop and zero time",()->{for(int kind=0;kind<3;kind++){var f=fixture();start(f,"0801",NOW);int reason=3;long now=NOW+600_000_000_000L;if(kind==1){f.pc.dead=true;reason=4;now=NOW;}if(kind==2){f.pc.map=9;reason=1;now=NOW;}f.socket.frames.clear();time(f,now);check(f.client.getAutomationSession()==null,"invalid session remains");check(f.socket.frames.size()==2,"missing force/time frames");check(HexFormat.of().formatHex(f.socket.frames.getFirst()).equals("b4390808"+String.format("%02x",reason)+"10000000"),"force body");check(last(f).equals("b43b0808000000"),"zero time");}});
  group("observed empty 2019 start body is GM-only and idempotent",()->{var f=fixture();start(f,"",NOW);check(last(f).equals("b4360808000000"),"empty start not approved");var session=f.client.getAutomationSession();check(session!=null&&session.mode()==2,"missing trial LOCAL state");start(f,"",NOW+2_000_000_000L);check(f.client.getAutomationSession()==session,"empty retry extended time");f=fixture();start(f,"0803",NOW);session=f.client.getAutomationSession();start(f,"",NOW+1_000_000_000L);check(f.client.getAutomationSession()==session&&session.mode()==3,"empty retry changed configured mode");for(int guard=0;guard<4;guard++){f=fixture();if(guard==0)f.pc.gm=false;if(guard==1)f.client.clearGameSessionReady();if(guard==2)f.pc.dead=true;if(guard==3)f.pc.auto=true;start(f,"",NOW);check(f.client.getAutomationSession()==null,"empty body bypassed guard "+guard);}f=fixture();PlaySupport2019.start(f.client,null,0,0,NOW);check(f.client.getAutomationSession()==null,"invalid null slice approved");});
  group("malformed/unknown modes and oversized body cannot start",()->{for(String body:new String[]{"0800","0804","12030801","088180808010","00","0b"}){var f=fixture();start(f,body,NOW);check(f.client.getAutomationSession()==null,"bad request admitted "+body);check(last(f).equals("b4360808630000"),"bad request ACK");}var f=fixture();byte[] huge=new byte[8193];PlaySupport2019.start(f.client,huge,0,huge.length,NOW);check(f.client.getAutomationSession()==null,"oversized body admitted");});
  group("GM/world/state guards",()->{var f=fixture();f.pc.gm=false;start(f,"0801",NOW);check(last(f).equals("b4360808630000"),"non-GM approved");for(int kind=0;kind<4;kind++){f=fixture();if(kind==0)f.client.clearGameSessionReady();if(kind==1)f.pc.logout=true;if(kind==2)f.client.setConnectionState(ConnectionState.AUTHENTICATED);if(kind==3)f.socket.connected=false;start(f,"0801",NOW);check(f.socket.commits==0&&f.client.getAutomationSession()==null,"unready user approved");}for(int kind=0;kind<4;kind++){f=fixture();if(kind==0)f.pc.dead=true;if(kind==1)f.pc.teleport=true;if(kind==2)f.pc.shop=true;if(kind==3)f.pc.loc.inside=false;start(f,"0801",NOW);check(f.client.getAutomationSession()==null,"invalid character approved");}});
  group("failed send rolls back native claim",()->{var f=fixture();f.socket.rejectCommit=true;start(f,"0801",NOW);check(f.client.getAutomationSession()==null&&f.socket.active==0,"failed ACK retained state/lease");f=fixture();f.socket.refuse=true;start(f,"0801",NOW);check(f.client.getAutomationSession()==null,"failed allocation retained state");});
  group("real CIO character/ready lifecycle clears native state",()->{var f=fixture();start(f,"0801",NOW);f.client.clearGameSessionReady();check(f.client.getAutomationSession()==null,"ready clear retained state");f=fixture();start(f,"0801",NOW);f.client.setActiveChar(null);check(f.client.getAutomationSession()==null&&!f.client.isGameSessionReady(),"detach retained state");f=fixture();start(f,"0801",NOW);var pc=new L1PcInstance();pc.client=f.client;f.client.setActiveChar(pc);check(f.client.getAutomationSession()==null&&!f.client.isGameSessionReady(),"relogin inherited state");});
  group("session stamp mismatch clears stale native session",()->{var f=fixture();start(f,"0801",NOW);f.pc.stamp++;time(f,NOW);check(f.client.getAutomationSession()==null,"stale stamp accepted");});
  group("actual server-AH start/stop admission excludes native PSS",()->{var f=fixture();var auto=new AutoHarness();start(f,"0801",NOW);AutoHarness.CIOThreadCallback.allow=true;auto.startAutoHunt(f.pc);check(!f.pc.auto&&f.pc.seeded==0&&f.pc.resets==0,"AH started over native PSS");finish(f,NOW);AutoHarness.CIOThreadCallback.allow=false;auto.startAutoHunt(f.pc);check(!f.pc.auto&&f.client.getAutomationSession()==null&&f.pc.guidance==0,"failed AH claim leaked");AutoHarness.CIOThreadCallback.allow=true;auto.startAutoHunt(f.pc);check(f.pc.auto&&f.client.getAutomationSession()==ClientAutomationSession.SERVER,"AH claim missing");start(f,"0801",NOW);check(last(f).equals("b4360808630000")&&f.client.getAutomationSession()==ClientAutomationSession.SERVER,"native replaced AH");auto.endAutoHunt(f.pc);check(!f.pc.auto&&f.client.getAutomationSession()==null,"AH stop retained slot");start(f,"0801",NOW);var nativeSession=f.client.getAutomationSession();auto.LogoutAutoHunt(f.pc);check(f.client.getAutomationSession()==nativeSession,"stale AH stop erased native session");});
  group("denied teleport policy preserves running PSS and sends no stop",()->{var f=fixture();start(f,"",NOW);var session=f.client.getAutomationSession();f.socket.frames.clear();for(int i=0;i<50;i++)check(!canBypassMapRestrictions(f.pc),"PSS regained GM map bypass");check(f.client.getAutomationSession()==session&&f.socket.frames.isEmpty(),"map refusal changed session or sent stop");time(f,NOW+1_000_000_000L);check(last(f).equals("b43b0808d7040000")&&f.client.getAutomationSession()==session,"PSS did not remain running");start(f,"",NOW+2_000_000_000L);check(last(f).equals("b4360808000000")&&f.client.getAutomationSession()==session,"retry changed running session");});
  group("registry routes 2101/2103/2106 through real CSProto",()->{for(int id:new int[]{2101,2103,2106})check(CSProto.isRoutable(id),"missing route "+id);var f=fixture();byte[] frame={0x51,0x35,8,2,0,8,1};CSProto.processFrame(f.client,frame,0,2101,2);check(last(f).equals("b4360808000000"),"dispatch did not reach PSS");f=fixture();byte[] empty={0x51,0x35,8,0,0};CSProto.processFrame(f.client,empty,0,2101,0);check(last(f).equals("b4360808000000")&&f.client.getAutomationSession()!=null,"zero-byte frame not dispatched");});
  group("2111 settings schema preserves explicit zeros and signed values",()->{
   byte[] body=bytes("0a120802100018002000281e300a380240004800");
   check(PlaySupport2019.parseSettings(new ProtoReader(body,0,body.length)).equals("mode=2 off_condition_death=0 off_condition_peace=0 off_condition_comeback=0 tel_condition_hp=30 tel_condition_peace=10 tel_condition_surrounded=2 tel_condition_pvp=0 exp_recovery=0"),"2111 field schema");
   for(String hex:new String[]{"0a020802","10090a0408025000","0a04080200000000"}){byte[] b=bytes(hex);check(PlaySupport2019.parseSettings(new ProtoReader(b,0,b.length)).equals("mode=2"),"unknown fields/padding "+hex);}
   byte[] signed=bytes("0a0b18ffffffffffffffffff01");check(PlaySupport2019.parseSettings(new ProtoReader(signed,0,signed.length)).equals("off_condition_peace=-1"),"signed setting value");
   for(String hex:new String[]{"","0802","0a04","0a0208","0a0108","0a020802000000","0a0208020001"}){byte[] b=bytes(hex);boolean rejected=false;try{PlaySupport2019.parseSettings(new ProtoReader(b,0,b.length));}catch(RuntimeException bad){rejected=true;}check(rejected,"malformed settings accepted "+hex);}
  });
  group("real 2111 dispatch logs settings without changing hunting ownership",()->{
   check(CSProto.isRoutable(2111),"2111 not registered");
   var logger=java.util.logging.Logger.getLogger(PlaySupport2019.class.getName());var records=new ArrayList<String>();
   var handler=new java.util.logging.Handler(){public void publish(java.util.logging.LogRecord r){records.add(r.getMessage());}public void flush(){}public void close(){}};
   logger.addHandler(handler);logger.setUseParentHandlers(false);logger.setLevel(java.util.logging.Level.ALL);
   try{
    var f=fixture();f.client.tryClaimAutomation(ClientAutomationSession.SERVER);f.pc.auto=true;byte[] frame=bytes("513f0804000a020802");
    CSProto.processFrame(f.client,frame,0,2111,4);
    check(records.size()==1&&records.get(0).contains("recv 2111")&&records.get(0).contains("mode=2")&&records.get(0).contains("body64=0a020802"),"2111 receipt not logged");
    check(f.pc.auto&&f.client.getAutomationSession()==ClientAutomationSession.SERVER,"2111 changed server AI");
    records.clear();f.pc.gm=false;CSProto.processFrame(f.client,frame,0,2111,4);check(records.isEmpty(),"non-GM diagnostic accepted");
    f.pc.gm=true;PlaySupport2019.settings(f.client,new byte[8193],0,8193);check(records.size()==1&&records.get(0).contains("rejected 2111"),"oversized body not rejected");
    records.clear();PlaySupport2019.settings(f.client,new byte[]{10,4},0,2);check(records.size()==1&&records.get(0).contains("rejected 2111"),"truncated body not rejected");
    check(f.pc.auto&&f.client.getAutomationSession()==ClientAutomationSession.SERVER,"invalid request changed AI");
   }finally{logger.removeHandler(handler);logger.setLevel(java.util.logging.Level.OFF);logger.setUseParentHandlers(true);}
  });
  group("GM 2111 probe uses exact outbound framing and preserves automation",()->{
   for(int m=0;m<=3;m++){var f=fixture();f.client.tryClaimAutomation(ClientAutomationSession.SERVER);f.pc.auto=true;PlaySupport2019.gmProbe2111(f.pc,m==0?"":Integer.toString(m));check(last(f).equals(m==0?"b43f080000":"b43f080a02080"+m+"0000"),"2111 probe wire "+last(f));check(f.client.getAutomationSession()==ClientAutomationSession.SERVER&&f.pc.auto,"probe changed AI");check(f.socket.active==0,"probe retained frame");}
   for(String bad:new String[]{"0","4","-1","2 extra","abc","2147483648"}){var f=fixture();PlaySupport2019.gmProbe2111(f.pc,bad);check(f.socket.frames.isEmpty()&&f.socket.begins==0,"invalid command sent "+bad);}
   var f=fixture();f.pc.gm=false;PlaySupport2019.gmProbe2111(f.pc,"");check(f.socket.begins==0,"non-GM probe admitted");
   f=fixture();f.client.clearGameSessionReady();PlaySupport2019.gmProbe2111(f.pc,"");check(f.socket.begins==0,"pre-world probe admitted");
   f=fixture();f.socket.refuse=true;PlaySupport2019.gmProbe2111(f.pc,"");check(f.socket.active==0&&f.socket.frames.isEmpty(),"refused frame leaked");
   f=fixture();f.socket.rejectCommit=true;PlaySupport2019.gmProbe2111(f.pc,"2");check(f.socket.active==0&&f.socket.aborts==1,"rejected frame not aborted");
   f=fixture();var nativeSession=new ClientAutomationSession(1,7,4,NOW+600_000_000_000L);f.client.tryClaimAutomation(nativeSession);PlaySupport2019.gmProbe2111(f.pc,null);check(f.client.getAutomationSession()==nativeSession,"probe changed native session");
  });
  group("actual bootstrap routes, authenticated companion, owned reusable item, logout cleanup",()->{
   l1j.server.server.model.character.pss.window.PssWindowTransport transport=null;
   java.lang.reflect.Field service=null;
   try{
    transport=new l1j.server.server.model.character.pss.window.PssWindowTransport(new java.net.InetSocketAddress("127.0.0.1",0),8);
    service=l1j.server.server.model.character.pss.window.PlaySupportWindow.class.getDeclaredField("transport");service.setAccessible(true);service.set(null,transport);
    var f=fixture();start(f,"0802",NOW);var automation=f.client.getAutomationSession();f.socket.frames.clear();
    java.util.function.IntConsumer incoming=(id)->{check(CSProto.isRoutable(id),"missing bootstrap route");byte[] frame={(byte)0x51,(byte)id,(byte)(id>>>8),0,0};CSProto.processFrame(f.client,frame,0,id,0);};
    byte[] nonce=new byte[16];for(int i=0;i<nonce.length;i++)nonce[i]=(byte)(i*17);
    incoming.accept(0x7df0);for(byte b:nonce)incoming.accept(0x7e00|(b&255));incoming.accept(0x7df1);
    try(var socket=new java.net.Socket("127.0.0.1",transport.port())){
     socket.setSoTimeout(2000);socket.getOutputStream().write(new byte[]{'P','S','S','U','I',1,0,0});socket.getOutputStream().write(nonce);check(socket.getInputStream().read()==16,"bootstrap binding failed");
     var item=new l1j.server.server.model.Instance.L1ItemInstance(88,new l1j.server.server.templates.L1Item(900000901,"PSS 설정서"));f.pc.inventory.owned.put(item.getId(),item);
     l1j.server.server.model.item.function.ItemUseExtensions.use(f.client,f.pc,item,item.getId(),0,0,0,null,0);
     check(socket.getInputStream().read()==1,"real item entry did not open companion UI");
     check(item.getCount()==1&&f.pc.inventory.consumeCalls==0,"settings item consumed");
     check(f.client.getAutomationSession()==automation&&f.socket.frames.isEmpty(),"window item changed PSS ownership or sent a start/stop packet");
     var forged=new l1j.server.server.model.Instance.L1ItemInstance(88,item.getItem());
     l1j.server.server.model.item.function.ItemUseExtensions.use(f.client,f.pc,forged,88,0,0,0,null,0);
     socket.setSoTimeout(100);boolean silent=false;try{socket.getInputStream().read();}catch(java.net.SocketTimeoutException expected){silent=true;}check(silent,"unowned item opened UI");
     socket.setSoTimeout(2000);f.client.clearGameSessionReady();check(socket.getInputStream().read()==-1&&f.client.peekPssWindowSession()==null,"logout retained companion session");
    }
   }catch(Exception failure){throw new RuntimeException(failure);}finally{if(transport!=null)transport.close();if(service!=null)try{service.set(null,null);}catch(Exception failure){throw new RuntimeException(failure);}}
  });
  group("source-only pairing fallback consumes private commands; validates input and game session",()->{
   var f=fixture();var window=l1j.server.server.model.character.pss.window.PlaySupportWindow.class;
   check(!l1j.server.server.model.character.pss.window.PlaySupportWindow.handleLinkCommand(f.pc,"ordinary chat"),"chat consumed");
   check(!l1j.server.server.model.character.pss.window.PlaySupportWindow.handleLinkCommand(f.pc,".psslinkXYZ"),"other command consumed");
   check(l1j.server.server.model.character.pss.window.PlaySupportWindow.handleLinkCommand(f.pc,".psslink invalid"),"bad nonce may leak to chat");
   check(f.client.peekPssWindowSession()==null,"bad nonce allocated session");
   l1j.server.server.model.character.pss.window.PssWindowTransport transport=null;java.lang.reflect.Field service=null;
   try{
    transport=new l1j.server.server.model.character.pss.window.PssWindowTransport(new java.net.InetSocketAddress("127.0.0.1",0),8);service=window.getDeclaredField("transport");service.setAccessible(true);service.set(null,transport);
    check(l1j.server.server.model.character.pss.window.PlaySupportWindow.handleLinkCommand(f.pc,".psslink 00112233445566778899aabbccddeeff"),"valid command not consumed");
    try(var socket=new java.net.Socket("127.0.0.1",transport.port())){
     socket.setSoTimeout(2000);socket.getOutputStream().write(bytes("505353554901000000112233445566778899aabbccddeeff"));
     check(socket.getInputStream().read()==16,"source-only pairing failed");
     check(l1j.server.server.model.character.pss.window.PlaySupportWindow.open(f.pc)&&socket.getInputStream().read()==1,"source-only item/open failed");
     check(f.client.getAutomationSession()==null,"pairing started PSS");
     f.client.clearGameSessionReady();check(socket.getInputStream().read()==-1,"fallback logout retained peer");
    }
    check(l1j.server.server.model.character.pss.window.PlaySupportWindow.handleLinkCommand(f.pc,".psslink 00112233445566778899aabbccddeeff")&&f.client.peekPssWindowSession()==null,"pre-world pairing admitted");
   }catch(Exception failure){throw new RuntimeException(failure);}finally{if(transport!=null)transport.close();if(service!=null)try{service.set(null,null);}catch(Exception failure){throw new RuntimeException(failure);}}
  });
  var pool=Executors.newFixedThreadPool(2);try {for(int i=0;i<300;i++){var f=fixture();var nativeSession=new ClientAutomationSession(1,7,4,NOW+600_000_000_000L);var barrier=new CyclicBarrier(2);var a=pool.submit(()->{barrier.await();return f.client.tryClaimAutomation(ClientAutomationSession.SERVER);});var b=pool.submit(()->{barrier.await();return f.client.tryClaimAutomation(nativeSession);});check(a.get()^b.get(),"both automation modes claimed");var owner=f.client.getAutomationSession();check(!f.client.releaseAutomation(owner==nativeSession?ClientAutomationSession.SERVER:nativeSession),"wrong owner released slot");check(f.client.releaseAutomation(owner),"owner release failed");}}finally{pool.shutdownNow();}
  System.out.println("PASS 300 concurrent server/native claim races using the actual CIOClient CAS");
  System.out.println("RESULT groups="+groups+" assertions="+checks+"; native game client execution not tested");
 }
}''')
with tempfile.TemporaryDirectory() as td:
 d=Path(td)
 for path,text in files.items():
  p=d/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
 real=sorted((ROOT/'src/l1j/server/server/model/character/pss').glob('*.java'))
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-encoding','UTF-8','-cp',CP,'-d',td,*map(str,d.rglob('*.java')),*map(str,real)],check=True,timeout=60)
 subprocess.run(['java','--enable-preview','-ea','-cp',td+os.pathsep+CP,'l1j.server.server.model.character.pss.PssContract'],check=True,timeout=60)
