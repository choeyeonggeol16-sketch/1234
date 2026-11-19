#!/usr/bin/env python3
"""Compile production Orim party-leader speech; detect historical self-duplicate and failure isolation."""
from pathlib import Path
import tempfile,subprocess,sys,os
ROOT=Path(__file__).resolve().parents[3]
s=Path(os.environ.get('RKERN_TEST_ORIM',ROOT/'src/l1j/server/IndunSystem/Orim/OrimController.java')).read_text()
needle='\tprivate void talkPartyleader(String msg) {'
a=s.index(needle);b=s.index('{',a);n=0
for i in range(b,len(s)):
 if s[i]=='{':n+=1
 elif s[i]=='}':
  n-=1
  if not n:break
method=s[a:i+1]
code='''package l1j.server.IndunSystem.Orim;
import java.util.*;
import l1j.server.server.model.network.core.broadcast.*;
public class OrimController {
 public static final List<Integer> sends=new ArrayList<>();
 public static boolean failSend, failOpen;
 static class Party {boolean isLeader(L1PcInstance who){return true;}}
 public static class L1PcInstance {public final int id;final Party party;
 L1PcInstance(int id,boolean party){this.id=id;this.party=party?new Party():null;}
 int getId(){return id;}Party getParty(){return party;}}
 static class SC_ChatPacket {static CSharedBroadcast.NativeWireWriter nativeWriter(int opcode,int channel,int sender,String text,boolean system){return b->b.put((byte)0x7f);}}
 static class L1World {static L1World getInstance(){return new L1World();}
 List<L1PcInstance> getVisiblePlayer(L1PcInstance who){return viewers;}}
 static List<L1PcInstance> viewers=new ArrayList<>();
 L1PcInstance[] members;
 void checkMember(){}
 L1PcInstance[] getPlayMemberArray(){return members;}
 /* METHOD */
 static void ck(boolean ok,String message){if(!ok)throw new AssertionError(message+" "+sends);}
 public static void main(String[] args){
  OrimController c=new OrimController();c.members=new L1PcInstance[]{new L1PcInstance(1,true)};
  viewers=List.of(new L1PcInstance(2,true),new L1PcInstance(3,true));
  sends.clear();c.talkPartyleader("story");
  ck(sends.equals(List.of(1,2,3)),"leader must be sent once and before AOI, not once for each viewer");
  ck(CAsyncSendConstBuffer.closed==1,"source release missing");
  sends.clear();failSend=true;c.talkPartyleader("failure");
  ck(sends.equals(List.of(1,3)),"failed viewer must not suppress next viewer");
  ck(CAsyncSendConstBuffer.closed==2,"failed socket stranded producer source");
  viewers=List.of(c.members[0],new L1PcInstance(4,true));sends.clear();failSend=false;c.talkPartyleader("self in AOI");
  ck(sends.equals(List.of(1,4)),"self appearing in AOI must not receive twice");
  viewers=List.of(); sends.clear();failSend=false;c.talkPartyleader("empty AOI");
  ck(sends.equals(List.of(1)),"leader must receive self even without viewers");
  c.members=null;sends.clear();c.talkPartyleader("null members");
  ck(sends.isEmpty(),"null members must not send");
  c.members=new L1PcInstance[0];sends.clear();c.talkPartyleader("empty party");
  ck(sends.isEmpty(),"empty party must not send");
  c.members=new L1PcInstance[]{new L1PcInstance(1,false)};c.talkPartyleader("no party");
  ck(sends.isEmpty(),"missing party must not send");
  System.out.println("100 Orim production speech: leader single-send, AOI FIFO, failed viewer, empty/invalid party (PASS)");
 }
}
'''.replace('/* METHOD */',method)
stub='''package l1j.server.server.model.network.core.broadcast;
import java.nio.ByteBuffer;
import l1j.server.IndunSystem.Orim.OrimController;
public final class CSharedBroadcast {
 public interface NativeWireWriter {void write(ByteBuffer b);}
 public static void sendRecipient(CAsyncSendConstBuffer shared,OrimController.L1PcInstance member){
  if(member.id==2&&OrimController.failSend)throw new IllegalStateException("socket failed");
  OrimController.sends.add(member.id);
 }
 public static void sendRecipientBestEffort(CAsyncSendConstBuffer shared,OrimController.L1PcInstance member){
  try{sendRecipient(shared,member);}catch(RuntimeException ignored){}
 }
 public static void closeOptional(CAsyncSendConstBuffer shared){if(shared!=null)try{shared.close();}catch(RuntimeException ignored){}}
}
'''
cb='''package l1j.server.server.model.network.core.broadcast;
public class CAsyncSendConstBuffer implements AutoCloseable {
 public static int closed;
 public CAsyncSendConstBuffer(CSharedBroadcast.NativeWireWriter wire,int senderId,int limit){}
 public void close(){closed++;}
}
'''
with tempfile.TemporaryDirectory() as t:
 d=Path(t)
 for name,body in {'l1j/server/IndunSystem/Orim/OrimController.java':code,'l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java':stub,'l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java':cb,'l1j/server/server/Opcodes.java':'package l1j.server.server; public final class Opcodes{public static final int S_SAY=5;}'}.items():
  p=d/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
 p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(d/'classes'),*map(str,d.rglob('*.java'))],capture_output=True,text=True)
 if p.returncode:print(p.stderr);sys.exit(p.returncode)
 p=subprocess.run(['java','-cp',str(d/'classes'),'l1j.server.IndunSystem.Orim.OrimController'],capture_output=True,text=True)
 if p.returncode:print(p.stdout,p.stderr);sys.exit(p.returncode)
 print(p.stdout.strip())
