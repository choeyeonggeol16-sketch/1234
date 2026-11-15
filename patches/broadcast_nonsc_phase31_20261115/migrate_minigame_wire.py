#!/usr/bin/env python3
"""Phase31: DeathMatch stage time and finish announcements use one immutable
S_EVENT frame for the entire recipient traversal. Preserve inventory,
teleport, ghost, RNG and per-recipient side-effect ordering exactly.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase30_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase31_FULL_SRC_20261115.zip"
PIN="793eb800bfa9a999e25f2fc278a68e135ebf6d1d"
BOX="src/l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java"
DM="src/l1j/server/server/model/L1DeathMatch.java"
TARGETS=[BOX,DM]
FQ="l1j.server.server.model.network.core.broadcast"
def sha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def bounds(src,sig):
    assert src.count(sig)==1,(sig,src.count(sig))
    i=src.index(sig);j=src.index("{",i);depth=0
    for k in range(j,len(src)):
        if src[k]=="{":depth+=1
        elif src[k]=="}":
            depth-=1
            if depth==0:return i,k+1,src[i:k+1]
    raise AssertionError("unbalanced "+sig)
HELPER=r'''
    /** Constructor 7 / MINIGAME_TIME: S_EVENT, subcode, little-endian u16. */
    public static byte[] sharedMiniGameTimeWire(int time) {
        return new byte[]{6,0,(byte)Opcodes.S_EVENT,(byte)MINIGAME_TIME,
                (byte)time,(byte)(time>>>8)};
    }

    /** Constructor 1 / MINIGAME_END: six original fixed payload bytes. */
    public static byte[] sharedMiniGameEndWire() {
        return new byte[]{10,0,(byte)Opcodes.S_EVENT,(byte)MINIGAME_END,
                (byte)147,(byte)92,(byte)151,(byte)220,(byte)42,(byte)74};
    }

'''
def modify(files):
    s=files[BOX].decode("utf8")
    marker="    /** Wire-compatible constructor 7 / MAP_TIMER:"
    assert s.count(marker)==1
    assert "public static byte[] sharedMiniGameTimeWire(" not in s
    for part in ("case MINIGAME_TIME:","buffer.writeH(_val1);",
                 "case MINIGAME_END:","buffer.writeC(147); buffer.writeC(92);",
                 "buffer.writeC(Opcodes.S_EVENT);"):
        assert part in s,part
    s=s.replace(marker,HELPER+marker,1)
    files[BOX]=s.encode("utf8")
    s=files[DM].decode("utf8")
    for sig,old_send,wire,checks in (
      ("private void startDeathMatch() {",
       "SC_PacketBox.send(pc, SC_PacketBox.MINIGAME_TIME, 1800);",
       "SC_PacketBox.sharedMiniGameTimeWire(1800)",
       ["pc.setDeathMatch(true);","pc.start_teleport(","removeMember(pc);"]),
      ("public void 우승자종료() {",
       "SC_PacketBox.send(pc, SC_PacketBox.MINIGAME_END);",
       "SC_PacketBox.sharedMiniGameEndWire()",
       ["pc.getInventory().storeItem(410007, 1);","pc.DeathMatchEndGhost();","pc.start_teleport("]),
      ("public void endDeathMatch() {",
       "SC_PacketBox.send(pc, SC_PacketBox.MINIGAME_END);",
       "SC_PacketBox.sharedMiniGameEndWire()",
       ["pc.getInventory().storeItem(410007, 1);","pc.DeathMatchEndGhost();","clearMembers();"])
    ):
        a,b,original=bounds(s,sig)
        loop_sig="for (L1PcInstance pc : getMembersArray()) {"
        x,y,loop=bounds(original,loop_sig)
        assert loop.count(old_send)==1,(sig,loop.count(old_send))
        assert original.count(old_send)==1
        for token in checks:
            assert token in original,(sig,token)
        rewritten=loop.replace(old_send,FQ+".CSharedBroadcast.sendRecipient(shared,pc);",1)
        wrapping='''try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                '''+wire+''',Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+rewritten+'''
        }'''
        updated=original[:x]+wrapping+original[y:]
        s=s[:a]+updated+s[b:]
    for sig in ("private void startDeathMatch() {","public void 우승자종료() {",
                "public void endDeathMatch() {"):
        _,_,method=bounds(s,sig)
        assert "CSharedBroadcast.sendRecipient(shared,pc)" in method
    files[DM]=s.encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
public final class Phase31MiniGameWireParity {
 static int checks;
 static void eq(byte[] a,byte[] b){
  if(!Arrays.equals(a,b))throw new AssertionError("minigame byte parity "+checks);checks++;
 }
 static byte[] time(int seconds){
  return ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)6).put((byte)Opcodes.S_EVENT)
   .put((byte)SC_PacketBox.MINIGAME_TIME).putShort((short)seconds).array();
 }
 static byte[] ended(){
  return new byte[]{10,0,(byte)Opcodes.S_EVENT,(byte)SC_PacketBox.MINIGAME_END,
   (byte)147,(byte)92,(byte)151,(byte)220,(byte)42,(byte)74};
 }
 public static void main(String[] args){
  eq(ended(),SC_PacketBox.sharedMiniGameEndWire());
  for(int v:new int[]{0,1,1800,65535,-1,Integer.MIN_VALUE,Integer.MAX_VALUE})
    eq(time(v),SC_PacketBox.sharedMiniGameTimeWire(v));
  int v=0x723abc44;
  for(int i=0;i<8192;i++){v^=v<<13;v^=v>>>17;v^=v<<5;
   eq(time(v),SC_PacketBox.sharedMiniGameTimeWire(v));}
  System.out.println("PHASE31_MINIGAME_PARITY_OK "+checks);
 }
}'''
def main():
    a=argparse.ArgumentParser();a.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase31"));args=a.parse_args()
    b=SOURCE.read_bytes()
    assert sha(b)==PIN,"phase30 pinned source mismatch"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    old=files.copy()
    assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
    modify(files)
    changed=sorted(n for n in files if files[n]!=old[n])
    assert changed==sorted(TARGETS),changed
    assert files["CTransport.dll"]==old["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for n,data in files.items():
        if n.startswith("src/") and n.endswith(".java"):
            f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    test=args.work/"testsrc/Phase31MiniGameWireParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n,data in sorted(files.items()):z.writestr(n,data)
    with zipfile.ZipFile(OUTPUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
    info={"phase":31,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
      "java_sources":1710,"modified_java":changed,
      "deathmatch_timer_send_loops":1,"deathmatch_finish_send_loops":2,
      "per_recipient_rewards_teleport_ghost_rng_order_preserved":True,
      "member_array_order_duplicates_preserved":True,
      "native_unchanged":True,"no_fallback":True,"no_new_worker_queue":True,
      "windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(info,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(info,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
