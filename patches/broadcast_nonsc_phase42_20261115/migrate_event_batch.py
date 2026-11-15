#!/usr/bin/env python3
"""Phase42: batch-migrate four active global event fanouts, preserving
live iterator ordering, event teleport/GM/YN side effects and per-user FIFO.
No workers, queues, fallback or recipient snapshots.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase41_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase42_FULL_SRC_20261115.zip"
PIN="7b384882518b5ed5a1178a28aa2def92fec5e44c"
BASE="src/l1j/server/"
BUG=BASE+"server/Controller/BugRaceController.java"
ACCEPT=BASE+"MJTemplate/DateSchedulerModel/Acceptor/AcceptorToNpcListenerAdapter.java"
BOSS=BASE+"server/Controller/BossController.java"
CASTLE=BASE+"MJWarSystem/MJCastleWar.java"
FILES=sorted([BUG,ACCEPT,BOSS,CASTLE])
NATIVE="CTransport.dll"
FQ="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def method(src,sig,start=0):
    a=src.find(sig,start)
    assert a>=0,(sig,start)
    opening=src.find("{",a)
    assert opening>=0
    depth=0
    for i in range(opening,len(src)):
        if src[i]=="{":depth+=1
        elif src[i]=="}":
            depth-=1
            if depth==0:return a,i+1,src[a:i+1]
    raise AssertionError("unclosed method/loop: "+sig)
def replace_block(src,anchor,loop_sig,old_send,new_send,frame_expr,required=()):
    assert src.count(anchor)==1,(anchor,src.count(anchor))
    z=src.index(anchor)
    a,b,old=method(src,loop_sig,z)
    assert old.count(old_send)==1,(anchor,old.count(old_send))
    for marker in required:
        assert marker in old,(anchor,marker)
    new=old.replace(old_send,new_send,1)
    wrapped=("try(var shared=new "+FQ+".CAsyncSendConstBuffer(\n"
            "      "+frame_expr+",Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n"
            +new+"\n}")
    return src[:a]+wrapped+src[b:]
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase42FanoutParity {
 static int checks; static final Charset MS949=Charset.forName("MS949");
 static void eq(byte[] want,byte[] got){
  if(!Arrays.equals(want,got)) throw new AssertionError("wire parity "+checks);
  checks++;
 }
 static byte[] system(String s){
  byte[] t=s.getBytes(MS949);byte[] b=new byte[t.length+5];
  b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);
  b[2]=(byte)Opcodes.S_MESSAGE;b[3]=9;System.arraycopy(t,0,b,4,t.length);
  return b;
 }
 static byte[] green(String s){
  byte[] t=s.getBytes(MS949);byte[] b=new byte[t.length+6];
  b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);
  b[2]=(byte)Opcodes.S_EVENT;b[3]=(byte)SC_PacketBox.GREEN_MESSAGE;b[4]=2;
  System.arraycopy(t,0,b,5,t.length);return b;
 }
 static byte[] bossFx(int n){
  return ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).putShort((short)10)
  .put((byte)Opcodes.S_EVENT).put((byte)0x53).putInt(n).putShort((short)0).array();
 }
 static byte[] server(int n){
  return ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN).putShort((short)6)
  .put((byte)Opcodes.S_MESSAGE_CODE).putShort((short)n).put((byte)0).array();
 }
 static void check(String msg,int id){
  eq(system(msg),SC_SystemMessage.sharedWire(msg));
  eq(green(msg),SC_PacketBox.sharedGreenWire(msg));
  eq(bossFx(id),SC_DisplayEffect.sharedWire(id));
  eq(server(id),SC_ServerMessage.sharedWire(id));
 }
 public static void main(String[] args){
  for(String t:new String[]{"","경기 안내","보스 소환 알림","성 입성 안내","한글", "Hello World"})
   check(t,643);
  int x=0x7a1057df;
  for(int i=0;i<4096;i++){
   x^=x<<13;x^=x>>>17;x^=x<<5;
   check("이벤트 방송 "+x,x);
  }
  System.out.println("PHASE42_FANOUT_PARITY_OK "+checks);
 }
}'''
def patch(files):
    s=files[BUG].decode("utf8")
    s=replace_block(s,"public void BroadcastAllUser(String text) {",
      "for (L1PcInstance player : L1World.getInstance().getAllPlayers()) {",
      "SC_SystemMessage.send(player, text);",
      FQ+".CSharedBroadcast.sendRecipient(shared,player);",
      "SC_SystemMessage.sharedWire(text)",("try {","catch (Exception exception)"))
    files[BUG]=s.encode("utf8")
    s=files[ACCEPT].decode("utf8")
    s=replace_block(s,"ThreadPool.getInstance().execute(new Runnable(){",
      "for(int i=0; i<2; ++i){",
      "SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);",
      FQ+".CSharedBroadcast.sendRecipient(shared,pc);",
      "SC_PacketBox.sharedGreenWire(message)",
      ("L1World.getInstance().getAllPlayers()","if(i == 0)","pc.start_teleport(","pc.do_simple_teleport("))
    files[ACCEPT]=s.encode("utf8")
    s=files[BOSS].decode("utf8")
    s=replace_block(s,"if (display_effect > 0) {",
      "for (final L1PcInstance pc : L1World.getInstance().getAllPlayers()) {",
      "SC_DisplayEffect.send(pc, display_effect);",
      FQ+".CSharedBroadcast.sendRecipient(shared,pc);",
      "SC_DisplayEffect.sharedWire(display_effect)",
      ("pc.setBossYN(npc.getNpcId())","SC_MessageYN.send(pc,","pc.isPrivateShop()","pc.getMap().isEscapable()"))
    files[BOSS]=s.encode("utf8")
    s=files[CASTLE].decode("utf8")
    s=replace_block(s,"final L1Location loc = createGetBackLocation();",
      "for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){",
      "SC_ServerMessage.send(pc, 643);",
      FQ+".CSharedBroadcast.sendRecipient(shared,pc);",
      "SC_ServerMessage.sharedWire(643)",
      ("L1CastleLocation.checkInWarArea","teleport(pc,","offLordBuff(pc);",
       "MJCastleWarBusiness.move(pc);","SC_CastleMaster.send(pc,"))
    files[CASTLE]=s.encode("utf8")
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase42"));a=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase41 source pin mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and NATIVE in files
 patch(files)
 changed=sorted(n for n in files if before[n]!=files[n])
 assert changed==FILES,changed
 assert files[NATIVE]==before[NATIVE]
 a.work.mkdir(parents=True,exist_ok=True)
 for name,content in files.items():
  if name.startswith("src/") and name.endswith(".java"):
   f=a.work/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(content)
 t=a.work/"testsrc/Phase42FanoutParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for name,content in sorted(files.items()):z.writestr(name,content)
 with zipfile.ZipFile(OUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":42,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
  "java_sources":1710,"modified_java":changed,"event_fanout_loops_migrated":4,
  "bugrace_per_recipient_exception_boundary_preserved":True,
  "npc_listener_two_pass_teleport_order_preserved":True,
  "boss_yn_and_display_order_preserved":True,
  "castle_player_move_and_send_order_preserved":True,
  "native_unchanged":True,"no_fallback":True,"new_workers_queues_classes":0,
  "windows_runtime_tested":False}
 (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
