#!/usr/bin/env python3
"""Exercise actual PSS parsing/sending, CIO session CAS and real packet dispatch."""
from pathlib import Path
import os,re,subprocess,tempfile

ROOT=Path(__file__).resolve().parents[2]
CP=os.pathsep.join([os.environ.get('ITEM_BUILD_CLASSES',str(ROOT/'build/item-processing/classes')),
    *map(str,Path(os.environ.get('ITEM_DEPS_DIR',str(ROOT/'lib'))).glob('*.jar'))])
files={}
def put(name,text):files[Path(name+'.java')]=text
put('l1j/server/server/model/map/L1Map','package l1j.server.server.model.map;public class L1Map {public boolean inside=true,usable=true;public boolean isInMap(int x,int y){return inside;}public boolean isUsableItem(){return usable;}}')
put('l1j/server/server/model/Instance/L1PcInstance','''package l1j.server.server.model.Instance;
import l1j.server.server.model.network.core.CIOClient;public class L1PcInstance {
 public CIOClient client;public boolean gm=true,dead,teleport,shop,logout,auto,ghost,itemBusy,tradeResolving;public int stamp=7,map=4,resets,seeded,guidance,tradeId;public boolean potion;
 public final l1j.server.server.model.map.L1Map loc=new l1j.server.server.model.map.L1Map();
 public CIOClient getNetConnection(){return client;}public boolean isGm(){return gm;}public boolean isDead(){return dead;}public boolean get_teleport(){return teleport;}public boolean isPrivateShop(){return shop;}public boolean isLogoutStarted(){return logout;}
 public boolean isGhost(){return ghost;}public boolean isItemTransferBusy(){return itemBusy;}public boolean isTradeResolving(){return tradeResolving;}public int getTradeID(){return tradeId;}public int getAccessLevel(){return gm?200:0;}
 public int getSessionStamp(){return stamp;}public int getMapId(){return map;}public l1j.server.server.model.map.L1Map getMap(){return loc;}public int getX(){return 10;}public int getY(){return 10;}public int getId(){return 77;}public String getName(){return "test";}
 public final l1j.server.server.model.L1PcInventory inventory=new l1j.server.server.model.L1PcInventory();public l1j.server.server.model.L1PcInventory getInventory(){return inventory;}
 public boolean getAutoHunt(){return auto;}public void setAutoHunt(boolean flag){auto=flag;}public void setAutoPotion(boolean flag){potion=flag;}public void setAutoMapId(int map){}public void setAutoPolyID(int sprite){}public int getCurrentSpriteId(){return 123;}
}''')
put('l1j/server/server/templates/L1Item','package l1j.server.server.templates;public class L1Item {public final int id;public final String name;public L1Item(int id,String name){this.id=id;this.name=name;}public int getItemId(){return id;}public String getName(){return name;}public int getUseType(){return 0;}}')
put('l1j/server/server/model/Instance/L1ItemInstance','package l1j.server.server.model.Instance;public class L1ItemInstance {private final int id;private final l1j.server.server.templates.L1Item item;public int count=1;public L1ItemInstance(int id,l1j.server.server.templates.L1Item item){this.id=id;this.item=item;}public int getId(){return id;}public int getItemId(){return item.getItemId();}public l1j.server.server.templates.L1Item getItem(){return item;}public int getCount(){return count;}public String getLogName(){return item.getName();}}')
put('l1j/server/server/model/L1PcInventory','package l1j.server.server.model;public class L1PcInventory {public final java.util.Map<Integer,l1j.server.server.model.Instance.L1ItemInstance> owned=new java.util.HashMap<>();public int consumeCalls;public l1j.server.server.model.Instance.L1ItemInstance getItem(int id){return owned.get(id);}public boolean consumeItem(int id,int count){consumeCalls++;return true;}}')
put('l1j/server/server/model/network/core/sc/packet/SC_SystemMessage','package l1j.server.server.model.network.core.sc.packet;public class SC_SystemMessage {public static boolean send(l1j.server.server.model.Instance.L1PcInstance pc,String text){return true;}}')
put('l1j/server/server/model/network/core/sc/packet/SC_ServerMessage','package l1j.server.server.model.network.core.sc.packet;public class SC_ServerMessage {public static boolean send(l1j.server.server.model.Instance.L1PcInstance pc,int id,String... text){return true;}}')
put('l1j/server/server/model/network/core/sc/packet/SC_Paralysis','package l1j.server.server.model.network.core.sc.packet;public class SC_Paralysis {public static boolean send(l1j.server.server.model.Instance.L1PcInstance pc,int id,boolean on){return true;}}')
put('l1j/server/server/model/L1World','package l1j.server.server.model;public class L1World {private static final L1World INSTANCE=new L1World();public static L1World getInstance(){return INSTANCE;}public l1j.server.server.model.Instance.L1PcInstance getPlayer(String name){return null;}}')
put('l1j/server/server/utils/CommonUtil','package l1j.server.server.utils;public class CommonUtil {public static boolean teleport_check(l1j.server.server.model.Instance.L1PcInstance pc,l1j.server.server.model.Instance.L1ItemInstance item){return false;}}')
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

put('l1j/server/server/model/character/pss/LatestWindowContract','package l1j.server.server.model.character.pss;\nimport java.net.*;import java.util.*;import l1j.server.server.model.Instance.*;import l1j.server.server.model.network.core.*;import l1j.server.server.model.network.utill.ConnectionState;import l1j.server.server.model.character.pss.window.*;\npublic class LatestWindowContract {\n static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}\n public static void main(String[] args)throws Exception{\n  var network=new CIOUserSocket();var client=new CIOClient(network,false);var pc=new L1PcInstance();pc.client=client;client.setActiveChar(pc);client.setConnectionState(ConnectionState.IN_GAME);client.publishGameSessionReady(pc);\n  var field=PlaySupportWindow.class.getDeclaredField("transport");field.setAccessible(true);\n  try(var server=new PssWindowTransport(new InetSocketAddress("127.0.0.1",0),8)){\n   field.set(null,server);byte[] token=HexFormat.of().parseHex("00112233445566778899aabbccddeeff");\n   PlaySupportWindow.registerStart(client,new byte[0],0,0);for(byte n:token)PlaySupportWindow.registerByte(client,n&255,new byte[0],0,0);PlaySupportWindow.registerCommit(client,new byte[0],0,0);\n   try(var peer=new Socket("127.0.0.1",server.port())){\n    peer.setSoTimeout(2000);peer.getOutputStream().write(new byte[]{\'P\',\'S\',\'S\',\'U\',\'I\',2,0,0});peer.getOutputStream().write(token);check(peer.getInputStream().read()==16,"v2 bind");\n    var item=new L1ItemInstance(88,new l1j.server.server.templates.L1Item(900000901,"Latest custom item name"));pc.inventory.owned.put(88,item);\n    pc.itemBusy=true;pc.tradeResolving=true;pc.tradeId=99;pc.dead=true;pc.ghost=true;pc.teleport=true;pc.map=5166;pc.loc.usable=false;\n    l1j.server.server.model.network.core.cs.packet.CS_ItemUse.handle(client,88,0,0,0,null,0);\n    check(peer.getInputStream().read()==1,"item did not open directly");check(item.count==1&&pc.inventory.consumeCalls==0,"item consumed");check(client.getAutomationSession()==null,"window started PSS");\n    item.count=0;l1j.server.server.model.network.core.cs.packet.CS_ItemUse.handle(client,88,0,0,0,null,0);item.count=1;\n    var forged=new L1ItemInstance(88,new l1j.server.server.templates.L1Item(900000901,"Forged item"));check(PlaySupportWindow.tryOpenItem(client,pc,forged),"special item not handled");\n    check(PlaySupportWindow.open(pc)&&peer.getInputStream().read()==1,"invalid item path emitted OPEN");\n    PlaySupportWindow.blockedShot(pc,123);check(PlaySupportWindow.open(pc)&&peer.getInputStream().read()==1,"non-PSS combat notification");\n    pc.dead=pc.ghost=pc.teleport=false;pc.map=4;\n    check(client.tryClaimAutomation(new ClientAutomationSession(1,7,4,System.nanoTime()+600_000_000_000L)),"native ownership");\n    PlaySupportWindow.blockedShot(pc,0x12345678);check(Arrays.equals(peer.getInputStream().readNBytes(5),HexFormat.of().parseHex("0378563412")),"blocked-shot wire");\n    PlaySupportWindow.blockedShot(pc,0x12345678);check(PlaySupportWindow.open(pc)&&peer.getInputStream().read()==1,"repeat throttle");\n   }\n  }finally{field.set(null,null);}\n  System.out.println("PASS latest direct item dispatch, ownership/quantity, no consumption, v2 binding, native-only LOS observation and throttling; assertions="+checks);\n  System.out.println("Unmodified daily quota/start lifecycle is covered by full source compilation and byte-preservation checks, not simulated here.");\n }\n}')
with tempfile.TemporaryDirectory() as td:
 d=Path(td)
 for path,text in files.items():
  p=d/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
 real=list((ROOT/'src/l1j/server/server/model/character/pss/window').glob('*.java'))
 real += [ROOT/'src/l1j/server/server/model/network/core/cs/packet'/name for name in ['User.java','CItem.java','CS_ItemUse.java']]
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--enable-preview','-source','21','-target','21','-encoding','UTF-8','-cp',CP,'-d',td,*map(str,d.rglob('*.java')),*map(str,real)],check=True,timeout=60)
 subprocess.run(['java','--enable-preview','-ea','-cp',td+os.pathsep+CP,'l1j.server.server.model.character.pss.LatestWindowContract'],check=True,timeout=30)
