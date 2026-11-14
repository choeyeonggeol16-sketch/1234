#!/usr/bin/env python3
"""Phase23: Orim status/colored notices share one S_EVENT frame for each event.
Constructor-16 (colored/score) and constructor-7 (round) wire layouts are
reproduced exactly, with original recipient array order and checkMember() gates.
"""
from pathlib import Path
import hashlib,json,zipfile,argparse
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase22_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase23_FULL_SRC_20261115.zip"
PIN="f7eb5fa0ee33f50b58b0e05ed646baa0401bf9dd"
BASE="src/l1j/server/"
PACKET=BASE+"server/model/network/core/sc/packet/SC_PacketBox.java"
ORIM=BASE+"IndunSystem/Orim/OrimController.java"
TARGETS=[PACKET,ORIM]
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
def blobsha(buf):return hashlib.sha1(b"blob "+str(len(buf)).encode()+b"\0"+buf).hexdigest()
def method(src,sig):
    assert src.count(sig)==1,(sig,src.count(sig))
    a=src.index(sig);b=src.index("{",a);n=0
    for i in range(b,len(src)):
        if src[i]=="{":n+=1
        elif src[i]=="}":
            n-=1
            if n==0:return a,i+1,src[a:i+1]
    raise AssertionError(sig)
ADD=r'''
    /** Constructor 16 GREEN_MESSAGE color variations: S_EVENT, 84, 2,
     * 26204/LE, color-byte (RED=51 or YELLOW=61), MS949 string+NUL.
     */
    public static byte[] sharedColorNoticeWire(int color,String text) {
        if(color!=RED_MESSAGE && color!=YELLOW_MESSAGE)
            throw new IllegalArgumentException("not a color message");
        final byte[] encoded=(text==null?"":text)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=9+encoded.length;
        if(size>0xffff)return null;
        final byte[] wire=new byte[size];
        wire[0]=(byte)size;wire[1]=(byte)(size>>>8);
        wire[2]=(byte)Opcodes.S_EVENT;wire[3]=(byte)GREEN_MESSAGE;
        wire[4]=2;wire[5]=(byte)26204;wire[6]=(byte)(26204>>>8);
        wire[7]=(byte)color;
        System.arraycopy(encoded,0,wire,8,encoded.length);
        return wire;
    }

    /** Constructor 16 GREEN_MESSAGE/SCORE_MARK: one subtype then S text. */
    public static byte[] sharedScoreWire(String text) {
        final byte[] encoded=(text==null?"":text)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=6+encoded.length;
        if(size>0xffff)return null;
        final byte[] wire=new byte[size];
        wire[0]=(byte)size;wire[1]=(byte)(size>>>8);
        wire[2]=(byte)Opcodes.S_EVENT;wire[3]=(byte)GREEN_MESSAGE;
        wire[4]=(byte)SCORE_MARK;
        System.arraycopy(encoded,0,wire,5,encoded.length);
        return wire;
    }

    /** Constructor 7 ROUND1: S_EVENT, ROUND1, int stage, int 12. */
    public static byte[] sharedRoundWire(int stage) {
        final byte[] wire=new byte[12];
        wire[0]=12;wire[2]=(byte)Opcodes.S_EVENT;wire[3]=(byte)ROUND1;
        wire[4]=(byte)stage;wire[5]=(byte)(stage>>>8);
        wire[6]=(byte)(stage>>>16);wire[7]=(byte)(stage>>>24);
        wire[8]=12;
        return wire;
    }

'''
def patch(files):
    src=files[PACKET].decode("utf8")
    assert "public static byte[] sharedColorNoticeWire(" not in src
    assert "public static byte[] sharedScoreWire(" not in src
    assert "public static byte[] sharedRoundWire(" not in src
    anchor="    /** Wire-compatible constructor 7 / MAP_TIMER:"
    assert src.count(anchor)==1
    files[PACKET]=src.replace(anchor,ADD+anchor,1).encode("utf8")
    src=files[ORIM].decode("utf8")
    replacements=[
      ("private void sendMessage1(String msg) {",
       ["getPlayMemberArray()","SC_PacketBox.YELLOW_MESSAGE","SC_PacketBox.GREEN_MESSAGE"],
       """private void sendMessage1(String msg) {
          """+FQ+""".sendArray(getPlayMemberArray(),
                  SC_PacketBox.sharedColorNoticeWire(SC_PacketBox.YELLOW_MESSAGE,msg));
      }"""),
      ("private void sendMessage2(String msg) {",
       ["getPlayMemberArray()","SC_PacketBox.RED_MESSAGE","SC_PacketBox.GREEN_MESSAGE"],
       """private void sendMessage2(String msg) {
          """+FQ+""".sendArray(getPlayMemberArray(),
                  SC_PacketBox.sharedColorNoticeWire(SC_PacketBox.RED_MESSAGE,msg));
      }"""),
      ("private void stageMark(int stageNum) {",
       ["checkMember();","getPlayMemberArray()","SC_PacketBox.ROUND1"],
       """private void stageMark(int stageNum) {
          checkMember();
          """+FQ+""".sendArray(getPlayMemberArray(),SC_PacketBox.sharedRoundWire(stageNum));
      }"""),
      ("private void scoreMark() {",
       ["checkMember();","getPlayMemberArray()","SC_PacketBox.SCORE_MARK"],
       """private void scoreMark() {
          checkMember();
          """+FQ+""".sendArray(getPlayMemberArray(),
                  SC_PacketBox.sharedScoreWire(Integer.toString(totalScore)));
      }""")
    ]
    for sig,checks,new in replacements:
        a,b,old=method(src,sig)
        for check in checks:assert check in old,(sig,check)
        src=src[:a]+new+src[b:]
    files[ORIM]=src.encode("utf8")

TEST=r'''import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
public final class Phase23OrimParity {
  static int checked;
  static void eq(byte[] expected,byte[] actual){
    if(!Arrays.equals(expected,actual))throw new AssertionError("orim frame parity "+checked);
    checked++;
  }
  static byte[] color(int mode,String message) {
    byte[] text=message.getBytes(Charset.forName("MS949"));
    ByteBuffer b=ByteBuffer.allocate(9+text.length).order(ByteOrder.LITTLE_ENDIAN);
    b.putShort((short)(9+text.length)).put((byte)Opcodes.S_EVENT)
     .put((byte)SC_PacketBox.GREEN_MESSAGE).put((byte)2)
     .putShort((short)26204).put((byte)mode).put(text).put((byte)0);
    return b.array();
  }
  static byte[] score(String message) {
    byte[] text=message.getBytes(Charset.forName("MS949"));
    ByteBuffer b=ByteBuffer.allocate(6+text.length).order(ByteOrder.LITTLE_ENDIAN);
    b.putShort((short)(6+text.length)).put((byte)Opcodes.S_EVENT)
     .put((byte)SC_PacketBox.GREEN_MESSAGE).put((byte)SC_PacketBox.SCORE_MARK)
     .put(text).put((byte)0);
    return b.array();
  }
  static byte[] round(int num) {
    return ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        .putShort((short)12).put((byte)Opcodes.S_EVENT).put((byte)SC_PacketBox.ROUND1)
        .putInt(num).putInt(12).array();
  }
  static void check(int n,String msg) {
    eq(color(SC_PacketBox.YELLOW_MESSAGE,msg),
       SC_PacketBox.sharedColorNoticeWire(SC_PacketBox.YELLOW_MESSAGE,msg));
    eq(color(SC_PacketBox.RED_MESSAGE,msg),
       SC_PacketBox.sharedColorNoticeWire(SC_PacketBox.RED_MESSAGE,msg));
    eq(score(msg),SC_PacketBox.sharedScoreWire(msg));
    eq(round(n),SC_PacketBox.sharedRoundWire(n));
  }
  public static void main(String[] args) {
    for(String s:new String[]{"","올딘 : 저놈을 잡아 들여라!","오림 점수: 12","TEST"})
       check(0,s);
    int r=0x1527331;
    for(int i=0;i<4096;i++){
      r^=r<<13;r^=r>>>17;r^=r<<5;
      check(r,"오림 "+r);
    }
    System.out.println("PHASE23_ORIM_PARITY_OK "+checked);
  }
}'''
def main():
    a=argparse.ArgumentParser();a.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase23"));args=a.parse_args()
    b=SRC.read_bytes()
    assert blobsha(b)==PIN,"phase22 source pin mismatch"
    with zipfile.ZipFile(SRC) as z:
        assert z.testzip() is None
        files={f.filename:z.read(f) for f in z.infolist() if not f.is_dir()}
    original=files.copy()
    assert sum(p.endswith(".java") for p in files)==1710 and "CTransport.dll" in files
    patch(files)
    changed=sorted(p for p in files if original[p]!=files[p])
    assert changed==sorted(TARGETS),changed
    assert original["CTransport.dll"]==files["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for p,data in files.items():
        if p.startswith("src/") and p.endswith(".java"):
            target=args.work/p;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    test=args.work/"testsrc/Phase23OrimParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,data in sorted(files.items()):z.writestr(p,data)
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    report={"phase":23,"input_blob":PIN,
      "output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
      "java_sources":1710,"modified_java":changed,
      "orim_colored_notice_loops":2,"orim_round_score_loops":2,
      "party_array_order_preserved":True,"native_unchanged":True,
      "no_workers_queues_added":True,"windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))
if __name__=="__main__":main()
