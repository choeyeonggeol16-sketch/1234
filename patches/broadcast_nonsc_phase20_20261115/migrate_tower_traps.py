#!/usr/bin/env python3
"""Phase20: eliminate 30 repeated Tower of Dominance notices, both trap display
fanout loops, and the LFC thorn effect's duplicated recipient serialization.
No change to event timing, phase advancement, target list order, duplicates,
per-socket cipher/FIFO, or CTransport native library.
"""
from pathlib import Path
import hashlib,json,zipfile,regex as re,argparse
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase19_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase20_FULL_SRC_20261115.zip"
PIN="044d938762d010f9f294f65eae8f99f902030b4b"
BASE="src/l1j/server/"
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
TOWERS=[BASE+f"TowerOfDominance/BossController/DominanceFloorLv{i}.java" for i in range(1,11)]
LFC=BASE+"MJInstanceSystem/MJLFC/Template/MJLFCWarSquareTrap.java"
LFCWIRE=BASE+"server/model/network/core/sc/packet/SC_LFCTrapEffect.java"
DISPLAY=BASE+"MJTemplate/Trap/DisplayEffectTrap.java"
TARGETS=TOWERS+[LFC,LFCWIRE,DISPLAY]
def blobsha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()

NOTICE=re.compile(r'for\s*\(\s*L1PcInstance\s+pc\s*:\s*PcCK\(\)\s*\)\s*\{\s*SC_PacketBox\.send\(\s*pc\s*,\s*SC_PacketBox\.GREEN_MESSAGE\s*,\s*("(?:\\.|[^"\\])*")\s*\)\s*;\s*\}',re.S)
DISPLAYLOOP=re.compile(r'for\s*\(\s*L1PcInstance\s+pc\s*:\s*pc_array\s*\)\s*\{\s*if\s*\(\s*pc\s*==\s*null\s*\)\s*continue\s*;\s*SC_DisplayEffect\.send\(\s*pc\s*,\s*asset_id\s*\)\s*;\s*\}',re.S)
THORN=re.compile(r'if\s*\(\s*_red\s*!=\s*null\s*\)\s*for\s*\(\s*L1PcInstance\s+pc\s*:\s*_red\s*\)\s*\{\s*SC_LFCTrapEffect\.send\(\s*pc\s*,\s*effectObjectId\s*,\s*6132\s*\)\s*;\s*\}\s*if\s*\(\s*_blue\s*!=\s*null\s*\)\s*for\s*\(\s*L1PcInstance\s+pc\s*:\s*_blue\s*\)\s*\{\s*SC_LFCTrapEffect\.send\(\s*pc\s*,\s*effectObjectId\s*,\s*6132\s*\)\s*;\s*\}',re.S)

def patch(files):
    for path in TOWERS:
        src=files[path].decode("utf8")
        src,n=NOTICE.subn(lambda match:FQ+".sendRecipients(PcCK(),SC_PacketBox.sharedGreenWire("+match.group(1)+"));",src)
        assert n==3,(path,n)
        files[path]=src.encode("utf8")
    src=files[DISPLAY].decode("utf8")
    src,n=DISPLAYLOOP.subn(lambda m:FQ+".sendArray(pc_array,SC_DisplayEffect.sharedWire(asset_id));",src)
    assert n==2,n
    marker="int asset_id = get_asset_id();"
    assert src.count(marker)==1
    # Original first foreach threw when a null array was passed. Preserve it.
    src=src.replace(marker,marker+"\n        java.util.Objects.requireNonNull(pc_array);",1)
    files[DISPLAY]=src.encode("utf8")
    src=files[LFC].decode("utf8")
    src,n=THORN.subn(FQ+".sendGroups(_red,_blue,SC_LFCTrapEffect.sharedWire(effectObjectId,6132));",src)
    assert n==1,n
    files[LFC]=src.encode("utf8")
    src=files[LFCWIRE].decode("utf8")
    assert "public static byte[] sharedWire(" not in src
    marker="    public static boolean send(CIOClient client, int objectId, int gfxId) {"
    assert src.count(marker)==1
    addition=r'''    /** Exact native FIFO frame for S_EFFECT/objectId/gfxId, one per thorn. */
    public static byte[] sharedWire(int objectId,int gfxId) {
        final byte[] wire=new byte[9];
        wire[0]=9;
        wire[2]=(byte)Opcodes.S_EFFECT;
        wire[3]=(byte)objectId;
        wire[4]=(byte)(objectId>>>8);
        wire[5]=(byte)(objectId>>>16);
        wire[6]=(byte)(objectId>>>24);
        wire[7]=(byte)gfxId;
        wire[8]=(byte)(gfxId>>>8);
        return wire;
    }
'''
    files[LFCWIRE]=src.replace(marker,addition+"\n"+marker,1).encode("utf8")
PARITY=r'''import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase20TrapNoticeParity {
  static int tests;
  static void yes(boolean b){if(!b)throw new AssertionError("phase20 wire "+tests);tests++;}
  static byte[] thorn(int id,int gfx){
    return ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
      .putShort((short)9).put((byte)Opcodes.S_EFFECT).putInt(id).putShort((short)gfx).array();
  }
  static byte[] green(String s){
    byte[] txt=s.getBytes(Charset.forName("MS949"));
    byte[] wire=new byte[6+txt.length];
    wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
    wire[2]=(byte)Opcodes.S_EVENT;wire[3]=(byte)SC_PacketBox.GREEN_MESSAGE;wire[4]=2;
    System.arraycopy(txt,0,wire,5,txt.length);return wire;
  }
  public static void main(String[] args){
    for(int id:new int[]{0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,0x7ABCDE12})
      for(int gfx:new int[]{0,6132,-1,65535})yes(Arrays.equals(thorn(id,gfx),SC_LFCTrapEffect.sharedWire(id,gfx)));
    for(String s:new String[]{"\\fY$27422","\\fY$27678","Hello","한글",""}){
      yes(Arrays.equals(green(s),SC_PacketBox.sharedGreenWire(s)));
    }
    int r=0x567be;
    for(int i=0;i<4096;i++) {
      r^=r<<13;r^=r>>>17;r^=r<<5;
      yes(Arrays.equals(thorn(r,i),SC_LFCTrapEffect.sharedWire(r,i)));
      String message="\\fY$"+(27422+i);
      yes(Arrays.equals(green(message),SC_PacketBox.sharedGreenWire(message)));
    }
    System.out.println("PHASE20_TRAP_NOTICE_PARITY_OK "+tests);
  }
}'''
def main():
    parser=argparse.ArgumentParser();parser.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase20"));args=parser.parse_args()
    buf=SOURCE.read_bytes()
    assert blobsha(buf)==PIN,"phase19 source pin mismatch"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    original=files.copy()
    assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
    patch(files)
    changed=sorted(p for p in files if files[p]!=original[p])
    assert changed==sorted(TARGETS),changed
    assert original["CTransport.dll"]==files["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for n,b in files.items():
        if n.startswith("src/") and n.endswith(".java"):
            file=args.work/n;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(b)
    tst=args.work/"testsrc/Phase20TrapNoticeParity.java"
    tst.parent.mkdir(parents=True,exist_ok=True);tst.write_text(PARITY)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n,b in sorted(files.items()):z.writestr(n,b)
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    info={"phase":20,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
          "java_sources":1710,"modified_java":changed,
          "tower_green_loops_migrated":30,"trap_effect_loops_migrated":3,
          "tower_floor_count":10,"native_unchanged":True,
          "recipients_order_duplicates_preserved":True,
          "no_threads_queues_added":True,"windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(info,indent=2)+"\n")
    print(json.dumps(info,indent=2))
if __name__=="__main__":main()
