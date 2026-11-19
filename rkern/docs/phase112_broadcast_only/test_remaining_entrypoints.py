#!/usr/bin/env python3
"""Compile actual production broadcast entrypoints in focused typed mocks."""
from pathlib import Path
import re,shutil,subprocess,tempfile,os
ROOT=Path(os.environ.get('BROADCAST_COMPARE_ROOT',str(Path(__file__).resolve().parents[2])))

def extract(source, name):
    text=(ROOT/'src'/source).read_text()
    m=re.search(r'(?m)^\s*(?:public|private)\s+\w+\s+'+re.escape(name)+r'\s*\(',text)
    if not m:raise AssertionError('missing '+source+':'+name)
    a=m.start();p=text.index('{',m.end());depth=0
    for i in range(p,len(text)):
        if text[i]=='{': depth+=1
        elif text[i]=='}':
            depth-=1
            if depth==0: return text[a:i+1]
    raise AssertionError('unclosed '+name)

with tempfile.TemporaryDirectory() as t:
    tmp=Path(t); src=tmp/'src'; shutil.copytree(ROOT/'docs/phase87_audit/tests/world',src)
    pkg=src/'l1j/server/server/model/network/core/broadcast'
    for f in ('CAsyncSendConstBuffer.java','CAsyncSendConstBufferExceptSelf.java','CSharedBroadcast.java','CTLMFilter.java'):
        shutil.copy2(ROOT/'src/l1j/server/server/model/network/core/broadcast'/f,pkg/f)
    w=src/'l1j/server/server/model/L1World.java';text=w.read_text();assert text.rstrip().endswith('}');w.write_text(text.rstrip()[:-1]+' public java.util.List<l1j.server.server.model.Instance.L1PcInstance> getRecognizePlayer(L1Object obj){return visible;} }')
    p=src/'l1j/server/server/model/Instance/L1PcInstance.java';text=p.read_text();assert text.rstrip().endswith('}');p.write_text(text.rstrip()[:-1]+' public void setChatParty(Object o){party=o;} public Object party; public String getName(){return "TEST";} }')
    java='''package l1j.server.server.model;
import java.util.*;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.transport.CIOServer;
import l1j.server.server.model.network.core.session.CIOClient;

public final class RemainingFanout112 {
 static void ck(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static L1PcInstance pc(CIOServer server,int id){return new L1PcInstance(id,1,new CIOClient(new CUserSocket(server)));}
 public static void main(String[]args) {
  CIOServer owner=new CIOServer();L1World world=L1World.getInstance();
  L1PcInstance bad=pc(owner,10), good=pc(owner,11);
  world.visible=new ArrayList<>(List.of(bad,good));
  MJEffectTriggerInstance effect=new MJEffectTriggerInstance(100,1);
  bad.client.socket.failEnqueueOnce=true;
  effect.do_move(10,20,3); // state update must survive optional visual SEND failure
  ck(effect.x==10 && effect.y==20,"effect position changed");
  ck(good.client.socket.frames.size()==1,"effect movement missing after bad viewer");
  bad.client.socket.failEnqueueOnce=true;
  effect.do_action(2);
  ck(good.client.socket.frames.size()==2,"effect action missing after bad viewer");
  bad.client.socket.failEnqueueOnce=true;
  MJL1TreasureChest chest=new MJL1TreasureChest(101,1);
  chest.testShowOpen();
  ck(good.client.socket.frames.size()==3,"chest-open broadcast missing after bad viewer");
  good.client.socket.drain();
  // Registration before notification must remain intact if a prior member SEND fails.
  L1ChatParty party=new L1ChatParty(); party._membersList.add(bad); party._leader=bad;
  bad.client.socket.failEnqueueOnce=true;
  party.addMember(good);
  ck(party._membersList.size()==2 && good.party==party,"party registration interrupted by notification failure");
  ck(good.client.socket.frames.size()==4,"party notification missing after failed member");
  good.client.socket.drain();
  ck(owner.releases==4,"Native shared source leak in remaining 4 entrypoints: "+owner.releases);
  System.out.println("112 remaining production broadcast entrypoints: effect move/action, chest-open, chat-party notification PASS");
 }
}
class Config {static final int MAX_CHAT_PT=10;}
class SC_ServerMessage {static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int type,String name){return b->b.put((byte)0x41);}}
class SC_DoActionGFX {static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int id,int action){return b->b.put((byte)0x42);}}
class SC_MoveChar {static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int id,int x,int y,int h){return b->b.put((byte)0x43);}}
class L1ChatParty {
 final List<L1PcInstance> _membersList=new ArrayList<>();L1PcInstance _leader;
 void setLeader(L1PcInstance pc){_leader=pc;}
'''
    java+=extract('l1j/server/server/model/L1ChatParty.java','addMember')+'\n}\n'
    java+='''class MJEffectTriggerInstance extends L1Character {
 int x,y;
 MJEffectTriggerInstance(int id,int map){super(id,map);}
 void setX(int xx){x=xx;}void setY(int yy){y=yy;}
'''
    java+=extract('l1j/server/MJTemplate/L1Instance/MJEffectTriggerInstance.java','do_move')+'\n'
    java+=extract('l1j/server/MJTemplate/L1Instance/MJEffectTriggerInstance.java','do_action')+'\n}\n'
    java+='''class MJL1TreasureChest extends L1Character {
 final int CHEST_OPEN=10;
 MJL1TreasureChest(int id,int map){super(id,map);}
 void testShowOpen(){showOpen();}
'''
    java+=extract('l1j/server/MJTemplate/TreasureChest/MJL1TreasureChest.java','showOpen')+'\n}\n'
    location=src/'l1j/server/server/model/RemainingFanout112.java';location.write_text(java)
    if 'BROADCAST_COMPARE_ROOT' not in os.environ:
        assert java.count('.sendWrittenRecipientsBestEffort(')==4
    classes=tmp/'classes';classes.mkdir()
    files=[str(p) for p in src.rglob('*.java')]
    subprocess.run(['javac','-encoding','UTF-8','--enable-preview','--release','21','-d',str(classes),*files],check=True,stdout=subprocess.DEVNULL)
    subprocess.run(['java','--enable-preview','-cp',str(classes),'l1j.server.server.model.RemainingFanout112'],check=True)
