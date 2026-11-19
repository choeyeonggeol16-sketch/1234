#!/usr/bin/env python3
"""Extract actual Phase93 MJLFCObject.close() and compile against typed game/socket stubs.
This verifies red->blue score-read order and winner/exit/release after visual failures.
"""
import pathlib,subprocess,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[3]
s=(ROOT/'src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCObject.java').read_text()
needle='public void close(){';a=s.index(needle);op=s.index('{',a);depth=0
for i in range(op,len(s)):
 if s[i]=='{':depth+=1
 if s[i]=='}':
  depth-=1
  if depth==0:break
method=s[a:i+1]
package='l1j.server.MJInstanceSystem.MJLFC.Template'
main=f'''package {package};
import java.util.*;
import java.util.logging.*;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.MJInstanceSystem.MJInstanceSpace;
import l1j.server.MJInstanceSystem.MJInstanceEnums.LFCMessages;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
public class MJLFCObject {{
 static final Logger _log=Logger.getLogger("lfc93");
 ArrayList<L1PcInstance> _red=new ArrayList<>(),_blue=new ArrayList<>();
 boolean _isrun=true; int rewards, exits; L1PcInstance rewarded;
 void compensate(ArrayList<L1PcInstance> winners,ArrayList<L1PcInstance> losers) {{rewards++;rewarded=winners.get(0);}}
 void getBack() {{exits++;}}
 {method}
 public static void main(String[]args) {{
  _log.setLevel(Level.OFF);
  for(int side=0;side<2;side++) for(int mode=0;mode<4;mode++) {{
   MJInstanceSpace.releases=0;
   var b=l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.class;
   l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failOpen=mode==1;
   l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer.failClose=mode==2;
   l1j.server.server.model.network.core.broadcast.CSharedBroadcast.failSend=mode==3;
   MJLFCObject arena=new MJLFCObject();
   arena._red.add(new L1PcInstance(1,side==0?100:1));
   arena._blue.add(new L1PcInstance(2,side==1?100:1));
   arena.close();
   // Original implementation records red damage in blueDmg and vice versa.
   int winner=side==0?2:1;
   if(arena.rewarded==null || arena.rewarded.id!=winner||arena.rewards!=1||arena.exits!=1||arena._isrun||MJInstanceSpace.releases!=1)
    throw new AssertionError("LFC result skipped side="+side+" mode="+mode);
  }}
  System.out.println("93 extracted production LFC close: 2 outcomes x 4 source/send/release modes PASS");
 }}
}}
class Thread {{static void sleep(long millis) {{}}}}
'''
files={
 'MJLFCObject.java':main,
 'L1PcInstance.java':'''package l1j.server.server.model.Instance;
public class L1PcInstance {
 public final int id;private final int damage;public L1PcInstance(int id,int damage){this.id=id;this.damage=damage;}
 public boolean isDead(){return false;}public int getDamageFromLfc(){return damage;}
}
''',
 'MJInstanceSpace.java':'''package l1j.server.MJInstanceSystem;
public class MJInstanceSpace {public static int releases;public static MJInstanceSpace getInstance(){return new MJInstanceSpace();}
 public void releaseInstance(Object o){releases++;}}
''',
 'LFCMessages.java':'''package l1j.server.MJInstanceSystem.MJInstanceEnums;
public enum LFCMessages {INGAME_CLOSE;public String get(){return "close";}}
''',
 'SC_PacketBox.java':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
public class SC_PacketBox {public static CSharedBroadcast.NativeWireWriter nativeGreenWriter(String s){return b->{};}}
''',
 'CAsyncSendConstBuffer.java':'''package l1j.server.server.model.network.core.broadcast;
public class CAsyncSendConstBuffer implements AutoCloseable {
 public static boolean failOpen,failClose;
 public CAsyncSendConstBuffer(CSharedBroadcast.NativeWireWriter w,int sender,int pending){
 if(failOpen)throw new IllegalStateException("source creation");}
 @Override public void close(){if(failClose)throw new IllegalStateException("source release");}
}
''',
 'CSharedBroadcast.java':'''package l1j.server.server.model.network.core.broadcast;
import java.nio.ByteBuffer;
import l1j.server.server.model.Instance.L1PcInstance;
public class CSharedBroadcast {
 @FunctionalInterface public interface NativeWireWriter {void write(ByteBuffer b);}
 public static boolean failSend;
 public static void sendRecipientBestEffort(CAsyncSendConstBuffer s,L1PcInstance pc){
 if(s==null||pc==null)return;try{if(failSend)throw new IllegalStateException("visual send");}
 catch(RuntimeException failure) { /* the actual production method logs and continues */ }
 }
}
'''
}
with tempfile.TemporaryDirectory() as temp:
 base=pathlib.Path(temp);dst=[]
 for name,content in files.items():
  pkg=content.split('package ')[1].split(';')[0].strip();p=base/'src'/pkg.replace('.','/')/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content);dst.append(str(p))
 out=base/'classes';out.mkdir()
 r=subprocess.run(['javac','--release','21','-d',str(out),*dst],capture_output=True,text=True)
 if r.returncode:raise RuntimeError(r.stderr)
 r=subprocess.run(['java','-cp',str(out),package+'.MJLFCObject'],capture_output=True,text=True)
 if r.returncode:raise RuntimeError(r.stdout+'\n'+r.stderr)
 print(r.stdout.strip())
