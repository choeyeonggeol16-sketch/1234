#!/usr/bin/env python3
"""Phase38: Orim identical location FX (twice per PC) and HADIN_DISPLAY
scripted UI frames share one immutable native send source per effect stage.
Keep per-client two packet FIFO order, duplicates, NPC spawning and shelling
RNG exactly as before; do NOT coalesce per-player random shell trajectories.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase37_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase38_FULL_SRC_20261115.zip"
PIN="ccf332efda1fe6c2f357311efa6d461a390cc9af"
BASE="src/l1j/server/"
ORIM=BASE+"IndunSystem/Orim/OrimController.java"
FX=BASE+"server/model/network/core/sc/packet/SC_EffectLocation.java"
BOX=BASE+"server/model/network/core/sc/packet/SC_PacketBox.java"
MODIFIED=sorted([ORIM,FX,BOX])
FQ="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def method(src,sig):
 assert src.count(sig)==1,(sig,src.count(sig))
 a=src.index(sig);b=src.index("{",a);depth=0
 for j in range(b,len(src)):
  if src[j]=="{":depth+=1
  elif src[j]=="}":
   depth-=1
   if depth==0:return a,j+1,src[a:j+1]
 raise AssertionError("unclosed "+sig)
def patch(files):
 s=files[FX].decode("utf8")
 assert s.count("private static byte[] sharedWire(int x, int y, int gfxId)") ==1
 s=s.replace("private static byte[] sharedWire(int x, int y, int gfxId)",
             "public static byte[] sharedWire(int x, int y, int gfxId)",1)
 files[FX]=s.encode("utf8")
 s=files[BOX].decode("utf8")
 needle="    /** Constructor 7 / MINIGAME_TIME:"
 assert s.count(needle)==1
 assert "case HADIN_DISPLAY:" in s and "buffer.writeC(_val1);" in s
 assert "public static byte[] sharedHadinDisplayWire(" not in s
 add='''    /** Constructor 7 / HADIN_DISPLAY: S_EVENT, subcode, one value byte. */
    public static byte[] sharedHadinDisplayWire(int value) {
        return new byte[]{5,0,(byte)Opcodes.S_EVENT,(byte)HADIN_DISPLAY,
                          (byte)value};
    }

'''
 s=s.replace(needle,add+needle,1)
 files[BOX]=s.encode("utf8")
 s=files[ORIM].decode("utf8")
 a,b,old=method(s,"private void eventSpawn(int i) {")
 for t in ("switch (i)", "case 0:",
           "SC_EffectLocation.send(pc, 32801, 32788, 8142);",
           "case 1:","case 2:","L1SpawnUtil.spawn6("):
  assert t in old,t
 assert old.count("SC_EffectLocation.send(pc, 32801, 32788, 8142);")==2
 start='''for (L1PcInstance pc : getPlayMemberArray())
\t\t\t\ttry {
\t\t\t\t\tSC_EffectLocation.send(pc, 32801, 32788, 8142);
\t\t\t\t\tSC_EffectLocation.send(pc, 32801, 32788, 8142);
\t\t\t\t} catch (Exception e) {
\t\t\t\t\t
\t\t\t\t}'''
 assert old.count(start)==1
 new='''try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                    SC_EffectLocation.sharedWire(32801,32788,8142),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)) {
                    for (L1PcInstance pc:getPlayMemberArray())
                        try {
                            '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,pc);
                            '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,pc);
                        } catch(Exception e) {
                        }
                }'''
 old=old.replace(start,new,1)
 s=s[:a]+old+s[b:]
 a,b,old=method(s,"private void Effect(int i) {")
 assert old.count("for (L1PcInstance c : getPlayMemberArray())")==1
 for val in ("case 0:", "case 1:", "case 2:", "case 3:",
             "SC_PacketBox.send(c, SC_PacketBox.HADIN_DISPLAY, 1);",
             "SC_PacketBox.send(c, SC_PacketBox.HADIN_DISPLAY, 2);",
             "SC_PacketBox.send(c, SC_PacketBox.HADIN_DISPLAY, 4);"):
  assert val in old,val
 assert old.count("SC_PacketBox.send(c, SC_PacketBox.HADIN_DISPLAY, 1);")==2
 assert old.count("SC_PacketBox.send(c, SC_PacketBox.HADIN_DISPLAY, 2);")==2
 new='''private void Effect(int i) {
        final int first = switch(i) {
            case 0,1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            default -> 0;
        };
        if(first==0)return;
        try(var primary=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_PacketBox.sharedHadinDisplayWire(first),
                Integer.MIN_VALUE,Integer.MAX_VALUE);
            var secondary=i==0 ? new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_PacketBox.sharedHadinDisplayWire(2),
                Integer.MIN_VALUE,Integer.MAX_VALUE) : null) {
            for(L1PcInstance c:getPlayMemberArray())
                try {
                    '''+FQ+'''.CSharedBroadcast.sendRecipient(primary,c);
                    if(secondary!=null)
                        '''+FQ+'''.CSharedBroadcast.sendRecipient(secondary,c);
                } catch(Exception e) {
                }
        }
    }'''
 s=s[:a]+new+s[b:]
 assert "for (L1PcInstance pc : getPlayMemberArray())" in s
 assert "int x = 32789 + _random.nextInt(18);" in s
 assert "SC_EffectLocation.send(pc, x, y, 762)" in s
 assert "SC_EffectLocation.send(pc, x, 32815, 8233)" in s
 assert "Effect(2);" in s
 assert "Thread.sleep(1000L);" in s
 files[ORIM]=s.encode("utf8")
 return {"orim_shared_location_double_fx_loop":1,
         "orim_shared_hadin_display_event_loop":1,
         "orim_duplicate_sends_preserved":True,
         "orim_first_secondary_packet_order_preserved":True,
         "orim_random_shell_effects_not_coalesced":True,
         "orim_effect_stage_and_spawn_order_preserved":True}
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase38OrimPacketParity {
 static int checks;
 static void eq(byte[] a,byte[] b) {
  if(!Arrays.equals(a,b))throw new AssertionError("Orim parity "+checks);checks++;
 }
 static byte[] loc(int x,int y,int gfx) {
  return ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)10).put((byte)Opcodes.S_EFFECT_LOC)
   .putShort((short)x).putShort((short)y).putShort((short)gfx)
   .put((byte)0).array();
 }
 static byte[] display(int id) {
  return new byte[]{5,0,(byte)Opcodes.S_EVENT,
       (byte)SC_PacketBox.HADIN_DISPLAY,(byte)id};
 }
 static void test(int x,int y,int fx,int displayId){
  eq(loc(x,y,fx),SC_EffectLocation.sharedWire(x,y,fx));
  eq(display(displayId),SC_PacketBox.sharedHadinDisplayWire(displayId));
 }
 public static void main(String[] args) {
  for(int value:new int[]{0,1,2,4,5,127,128,255,256,-1,32768,65535,
       Integer.MIN_VALUE,Integer.MAX_VALUE})test(value,32801,8142,value);
  int x=0x71343513;
  for(int i=0;i<8192;i++) {
   x^=x<<13;x^=x>>>17;x^=x<<5;
   test(x,x>>>8,x>>>16,x>>>24);
  }
  System.out.println("PHASE38_ORIM_WIRE_PARITY_OK "+checks);
 }
}'''
def main():
 a=argparse.ArgumentParser();a.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase38"));args=a.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase37 input changed"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 details=patch(files)
 changed=sorted(n for n in files if files[n]!=before[n])
 assert changed==MODIFIED,changed
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   p=args.work/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 t=args.work/"testsrc/Phase38OrimPacketParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":38,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
       "java_sources":1710,"modified_java":changed,**details,
       "native_unchanged":True,"no_legacy_fallback":True,
       "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
 print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
