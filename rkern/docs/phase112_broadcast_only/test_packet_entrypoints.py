#!/usr/bin/env python3
"""Compile and execute exact extracted production broadcast packet methods."""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile

root=Path(os.environ.get('BROADCAST_COMPARE_ROOT', str(Path(__file__).resolve().parents[2])))
base=root/'src/l1j/server/server/model/network/core/sc/packet'
methods={
    'SC_NpcChatPacket': ['broadcastCharacter'],
    'SC_SystemMessage': ['broadcastRecipients'],
    'SC_PacketBox': ['broadcastGreenMap'],
    'SC_ReturnedStat': ['broadcastEmblem'],
    'SC_ChatPacket': ['broadcastComplex0','fitsBroadcastChat','writeNativeChat'],
    'SC_War': ['broadcastClan','writeNativeWar'],
}

def method_body(code, name):
    m=re.search(r'(?m)^\s*(?:public|private)\s+static\s+[^\n{;]+?\b'+re.escape(name)+r'\s*\(', code)
    if not m: raise AssertionError('missing production method '+name)
    start=m.start(); brace=code.index('{',m.end())
    level=0
    for pos in range(brace,len(code)):
        if code[pos]=='{': level+=1
        if code[pos]=='}':
            level-=1
            if level==0:return code[start:pos+1]
    raise AssertionError('unclosed production method '+name)

with tempfile.TemporaryDirectory() as t:
    tmp=Path(t);dst=tmp/'src';shutil.copytree(root/'docs/phase87_audit/tests/world',dst)
    pkg=dst/'l1j/server/server/model/network/core/broadcast'
    for f in ('CAsyncSendConstBuffer.java','CAsyncSendConstBufferExceptSelf.java','CSharedBroadcast.java','CTLMFilter.java'):
        shutil.copy2(root/'src/l1j/server/server/model/network/core/broadcast'/f,pkg/f)
    opc=dst/'l1j/server/server/Opcodes.java'
    opc.write_text('package l1j.server.server; public final class Opcodes {public static final int S_ACTION=1,S_MOVE_OBJECT=2,S_MESSAGE=3,S_SAY_CODE=4,S_EVENT=5,S_VOICE_CHAT=6,S_WAR=7;}')
    pc=dst/'l1j/server/server/model/Instance/L1PcInstance.java'
    txt=pc.read_text();txt=txt.rstrip();assert txt.endswith('}');pc.write_text(txt[:-1]+' public String getName(){return "TEST";} }')
    generated='''package l1j.server.server.model.network.core.sc.packet;
import java.nio.ByteBuffer;
import java.util.*;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1World;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

public final class PacketFanout112 {
 static void ck(boolean value,String message) {if(!value)throw new AssertionError(message);}
 static void checkFrames(L1PcInstance bad,L1PcInstance good,int expectedCount,int opcode) {
  ck(bad.client.socket.frames.size()==0,"failed receiver was delivered");
  ck(good.client.socket.frames.size()==expectedCount,"healthy later recipients missing packets: "+good.client.socket.frames.size());
  for(byte[] b:good.client.socket.frames)ck((b[2]&255)==opcode,"opcode changed");
 }
 public static void main(String[]args){
  CIOServer owner=new CIOServer(); L1World world=L1World.getInstance();
  L1PcInstance anchor=new L1PcInstance(1000,1,new CIOClient(new CUserSocket(owner)));
  L1PcInstance bad=new L1PcInstance(1001,1,new CIOClient(new CUserSocket(owner)));
  L1PcInstance good=new L1PcInstance(1002,1,new CIOClient(new CUserSocket(owner)));
  world.visible=new ArrayList<>(List.of(bad,good,good));
  bad.client.socket.failEnqueueOnce=true;
  SC_SystemMessage.broadcastRecipients(world.visible,"SYSTEM");
  checkFrames(bad,good,2,Opcodes.S_MESSAGE);
  good.client.socket.drain();good.client.socket.frames.clear();
  bad.client.socket.failEnqueueOnce=true;
  SC_NpcChatPacket.broadcastCharacter(anchor,anchor,"NPC");
  checkFrames(bad,good,2,Opcodes.S_SAY_CODE);
  good.client.socket.drain();good.client.socket.frames.clear();
  bad.client.socket.failEnqueueOnce=true;
  SC_PacketBox.broadcastGreenMap(1,"GREEN");
  checkFrames(bad,good,2,Opcodes.S_EVENT);
  good.client.socket.drain();good.client.socket.frames.clear();
  bad.client.socket.failEnqueueOnce=true;
  SC_ReturnedStat.broadcastEmblem(anchor,17,18);
  checkFrames(bad,good,2,Opcodes.S_VOICE_CHAT);
  good.client.socket.drain();good.client.socket.frames.clear();
  bad.client.socket.failEnqueueOnce=true;
  SC_ChatPacket.broadcastComplex0(anchor,"SAY",Opcodes.S_MESSAGE);
  checkFrames(bad,good,2,Opcodes.S_MESSAGE);
  good.client.socket.drain();good.client.socket.frames.clear();
  L1Clan clan=new L1Clan();
  clan.members.add(new L1Clan.Member(bad));
  clan.members.add(new L1Clan.Member(good));
  clan.members.add(new L1Clan.Member(good));
  bad.client.socket.failEnqueueOnce=true;
  SC_War.broadcastClan(clan,1,"A","B");
  checkFrames(bad,good,2,Opcodes.S_WAR);
  good.client.socket.drain();
  ck(owner.releases==6,"shared Native source missing release; releases="+owner.releases);
  System.out.println("112 extracted production packet broadcasts: 6 entrypoints, recipient isolation/FIFO/bytes/NATIVE release PASS");
 }
}
'''
    for cls,names in methods.items():
        code=(base/(cls+'.java')).read_text()
        generated+='\nclass '+cls+' {\n'
        if cls=='SC_PacketBox': generated+='static final int GREEN_MESSAGE=84;\n'
        for name in names:
            generated+=method_body(code,name)+'\n'
            if name.startswith('broadcast'):
                if 'BROADCAST_COMPARE_ROOT' not in os.environ:
                    if cls=='SC_War':
                        assert 'sendWrittenClanBestEffort' in method_body(code,name), name
                    else:
                        assert 'sendWrittenRecipientsBestEffort' in method_body(code,name), name
        generated+='}\n'
    location=dst/'l1j/server/server/model/network/core/sc/packet';location.mkdir(parents=True)
    (location/'PacketFanout112.java').write_text(generated)
    classes=tmp/'classes';classes.mkdir()
    files=[str(p) for p in dst.rglob('*.java')]
    subprocess.run(['javac','-encoding','UTF-8','--enable-preview','--release','21','-d',str(classes),*files],check=True,stdout=subprocess.DEVNULL)
    subprocess.run(['java','--enable-preview','-cp',str(classes),'l1j.server.server.model.network.core.sc.packet.PacketFanout112'],check=True)
