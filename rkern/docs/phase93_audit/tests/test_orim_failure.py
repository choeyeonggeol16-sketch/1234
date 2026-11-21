#!/usr/bin/env python3
"""Compile the literal production handleAttackTrap method with typed failure stubs.
The stub deliberately throws during visual fanout; score and count are game state.
"""
import pathlib, subprocess, tempfile, textwrap
ROOT=pathlib.Path(__file__).resolve().parents[3]
src=(ROOT/'src/l1j/server/server/model/network/core/cs/packet/CS_ExtraCommand.java').read_text()
name='private static void handleAttackTrap(OrimController orim)'
pos=src.index(name);opening=src.index('{',pos);i=opening;depth=0
while i<len(src):
    if src[i]=='{':depth+=1
    elif src[i]=='}':
        depth-=1
        if depth==0:break
    i+=1
method=src[pos:i+1]
files={
'CS_ExtraCommand.java':'''package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.world.instance.dungeon.orim.OrimController;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.*;
public final class CS_ExtraCommand {
''' + method + '''
 public static void main(String[]args){
    for(boolean fail: new boolean[]{false,true}) {
      var orim=new OrimController();
      l1j.server.server.model.network.core.broadcast.CSharedBroadcast.fail=fail;
      handleAttackTrap(orim);
      if(orim.score!=50||orim.count!=1)throw new AssertionError("Orim state lost after optional SEND failure="+fail);
    }
    System.out.println("93 production Orim trap: normal + injected Native SEND failure preserve score/count PASS");
 }
}
''',
'OrimController.java':'''package l1j.server.server.model.world.instance.dungeon.orim;
import l1j.server.server.model.Instance.L1PcInstance;
public final class OrimController {
  public int score,count;private final L1PcInstance[] players={new L1PcInstance(1),new L1PcInstance(2)};
  public L1PcInstance[] getPlayMemberArray(){return players;}
  public int getAtCount(){return count;}
  public Object getShell1(){return new Object();}public Object getShell2(){return new Object();}
  public void addScore(int v){score+=v;}public void addAtCount(){count++;}
}
''',
'L1PcInstance.java':'''package l1j.server.server.model.Instance;
public final class L1PcInstance {private final int id;public L1PcInstance(int id){this.id=id;}public int getId(){return id;}}
''',
'SC_SkillSound.java':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_SkillSound {
 public static long pack(int id,int gfx){return ((long)id<<32)|gfx;}
 public static void sendPacked(L1PcInstance p,long v){}public static void broadcastPacked(L1PcInstance p,long v){}
}
''',
'SC_DoActionGFX.java':'''package l1j.server.server.model.network.core.sc.packet;
public final class SC_DoActionGFX {public static void broadcast(Object o,int gfx){}}
''',
'SC_EffectLocation.java':'''package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
public final class SC_EffectLocation {
 public static CSharedBroadcast.NativeWireWriter nativeWriter(int x,int y,int gfx){return b->{};}
}
''',
'CSharedBroadcast.java':'''package l1j.server.server.model.network.core.broadcast;
import java.nio.ByteBuffer;
import l1j.server.server.model.Instance.L1PcInstance;
public final class CSharedBroadcast {
 @FunctionalInterface public interface NativeWireWriter {void write(ByteBuffer b);}
 public static boolean fail;
 public static void sendWrittenArray(L1PcInstance[] a,NativeWireWriter w){if(fail)throw new IllegalStateException("Native send injected");}
 public static void sendWrittenArrayBestEffort(L1PcInstance[] a,NativeWireWriter w){try{sendWrittenArray(a,w);}catch(RuntimeException optionalFailure){}}
}
''',
}
with tempfile.TemporaryDirectory() as td:
    base=pathlib.Path(td); destinations=[]
    for name,contents in files.items():
        pkg=contents.split('package ')[1].split(';')[0].strip()
        dst=base/'src'/pkg.replace('.','/')/name;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_text(contents);destinations.append(str(dst))
    cp=base/'classes';cp.mkdir()
    c=subprocess.run(['javac','--release','21','-d',str(cp),*destinations],capture_output=True,text=True)
    if c.returncode:raise RuntimeError(c.stderr)
    r=subprocess.run(['java','-cp',str(cp),'l1j.server.server.model.network.core.cs.packet.CS_ExtraCommand'],capture_output=True,text=True)
    if r.returncode:raise RuntimeError(r.stdout+'\n'+r.stderr)
    print(r.stdout.strip())
