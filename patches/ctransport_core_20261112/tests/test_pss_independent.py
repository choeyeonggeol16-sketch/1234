"""Production pairing/lifecycle + real independent TCP; only game-world objects are stubs."""
from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
sources={
'l1j/server/Config.java':'package l1j.server;public class Config{public static int GAME_SERVER_PORT=5000;}',
'l1j/server/server/model/network/utill/ConnectionState.java':'package l1j.server.server.model.network.utill;public enum ConnectionState{IN_GAME,LOGIN}',
'l1j/server/server/model/network/core/CIOClient.java':'''package l1j.server.server.model.network.core;
import l1j.server.server.model.character.pss.window.PlaySupportWindow;import l1j.server.server.model.Instance.L1PcInstance;import l1j.server.server.model.network.utill.ConnectionState;
public class CIOClient{public boolean connected=true,ready=true;public L1PcInstance pc;public Object automation=new Object();private PlaySupportWindow.Session session;
public boolean isConnected(){return connected;}public boolean isGameSessionReady(){return ready;}public ConnectionState getConnectionState(){return ConnectionState.IN_GAME;}public L1PcInstance getActiveChar(){return pc;}public String getIp(){return "127.0.0.1";}
public synchronized PlaySupportWindow.Session pssWindowSession(){if(session==null)session=new PlaySupportWindow.Session(this);return session;}public PlaySupportWindow.Session peekPssWindowSession(){return session;}}
''',
'l1j/server/server/model/Instance/L1PcInstance.java':'''package l1j.server.server.model.Instance;
import java.util.*;import l1j.server.server.model.network.core.CIOClient;
public class L1PcInstance{public CIOClient client;public int stamp=7;public boolean logout;public Inventory inventory=new Inventory();public CIOClient getNetConnection(){return client;}public int getId(){return 77;}public int getSessionStamp(){return stamp;}public boolean isLogoutStarted(){return logout;}public Inventory getInventory(){return inventory;}public static class Inventory{public Map<Integer,L1ItemInstance> items=new HashMap<>();public L1ItemInstance getItem(int id){return items.get(id);}}}
''',
'l1j/server/server/model/Instance/L1ItemInstance.java':'''package l1j.server.server.model.Instance;public class L1ItemInstance{public int count=1;public int getId(){return 88;}public int getCount(){return count;}public Template getItem(){return new Template();}public static class Template{public int getItemId(){return 900000901;}}}''',
'l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java':'package l1j.server.server.model.network.core.sc.packet;public class SC_SystemMessage{public static boolean send(l1j.server.server.model.Instance.L1PcInstance pc,String message){return true;}}',
'IndependentPssContract.java':'''import java.net.*;import java.nio.*;import java.util.*;
import l1j.server.server.model.character.pss.window.PlaySupportWindow;import l1j.server.server.model.network.core.CIOClient;import l1j.server.server.model.Instance.*;
public class IndependentPssContract{
static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
static int free()throws Exception{try(ServerSocket listener=new ServerSocket(0)){return listener.getLocalPort();}}
static CIOClient client(){var c=new CIOClient();c.pc=new L1PcInstance();c.pc.client=c;return c;}
static byte[] token(int seed){byte[] t=new byte[16];for(int i=0;i<16;i++)t[i]=(byte)(seed+i);return t;}
static void register(CIOClient c,byte[] token){byte[] none={};PlaySupportWindow.registerStart(c,none,0,0);for(byte b:token)PlaySupportWindow.registerByte(c,b&255,none,0,0);PlaySupportWindow.registerCommit(c,none,0,0);}
static Socket helper(int port,byte[] token)throws Exception{Socket s=new Socket("127.0.0.1",port);s.setSoTimeout(2000);s.getOutputStream().write(ByteBuffer.allocate(24).put(new byte[]{'P','S','S','U','I',1,0,0}).put(token).array());check(s.getInputStream().read()==16,"authenticated BOUND");return s;}
public static void main(String[] args)throws Exception{
int port=free();System.setProperty("pss.uiBridge.port",""+port);
try{
 check(PlaySupportWindow.startIndependent(),"independent listener startup");
 var c=client();Object automation=c.automation;byte[] nonce=token(3);register(c,nonce);
 try(Socket peer=helper(port,nonce)){
  check(PlaySupportWindow.open(c.pc)&&peer.getInputStream().read()==1,"independent OPEN");
  var item=new L1ItemInstance();c.pc.inventory.items.put(88,item);
  check(PlaySupportWindow.tryOpenItem(c,c.pc,item)&&peer.getInputStream().read()==1,"owned item OPEN");
  check(item.count==1&&c.automation==automation,"UI changed hunting/item ownership");
  c.ready=false;c.pssWindowSession().close();check(peer.getInputStream().read()==-1,"logout detach");
 }
 var second=client();register(second,token(9));
 try(Socket peer=helper(port,token(9))){PlaySupportWindow.stopIndependent();check(peer.getInputStream().read()==-1,"PSS-only shutdown");check(second.connected&&second.ready,"PSS shutdown affected gameplay session");}
 var invalid=client();PlaySupportWindow.registerStart(invalid,new byte[]{1},0,1);check(invalid.peekPssWindowSession()==null,"bad bootstrap allocated session");
 try(ServerSocket collision=new ServerSocket(0)){
  System.setProperty("pss.uiBridge.port",""+collision.getLocalPort());check(!PlaySupportWindow.startIndependent(),"port collision accepted");check(second.connected&&second.ready,"PSS bind failure affected game session");
 }
 System.setProperty("pss.uiBridge.port",""+free());check(PlaySupportWindow.startIndependent(),"PSS restart");
 check(PlaySupportWindow.handleLinkCommand(second.pc,".psslink 00112233445566778899aabbccddeeff"),"manual companion registration");
 System.out.println("PASS production PSS startup/pairing/item OPEN/logout/restart; PSS shutdown/bind failure isolated from game session; no CTransport/FFM dependency");
}finally{PlaySupportWindow.stopIndependent();System.clearProperty("pss.uiBridge.port");}
}}
'''}
with tempfile.TemporaryDirectory(prefix='pss-independent-')as directory:
 base=Path(directory);paths=[]
 for name,body in sources.items():
  p=base/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);paths.append(str(p))
 prod=ROOT/'src/l1j/server/server/model/character/pss/window'
 paths += [str(prod/'PlaySupportWindow.java'),str(prod/'PssWindowTransport.java')]
 subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',str(base/'classes'),*paths],check=True)
 subprocess.run(['java','-cp',str(base/'classes'),'IndependentPssContract'],check=True,timeout=30)
