#!/usr/bin/env python3
"""Phase21: ten floor-wide screen-effect loops, one immutable native frame.
Retain private-shop/entered-world eligibility and each world iteration order;
preserve the special zero-effect no-send rule and native FIFO.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile,re
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase20_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase21_FULL_SRC_20261115.zip"
PIN="c8a37a8262d7c83c05d37246267fccc46a850019"
BASE="src/l1j/server/"
HELPER=BASE+"server/model/network/core/broadcast/CSharedBroadcast.java"
TOWERS=[BASE+f"TowerOfDominance/BossController/DominanceFloorLv{i}.java" for i in range(1,11)]
TARGETS=[HELPER]+TOWERS
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
PATTERN=re.compile(r'for\s*\(\s*L1PcInstance\s+pc\s*:\s*L1World\.getInstance\(\)\.getAllPlayers\(\)\s*\)\s*\{\s*if\s*\(\s*pc\.isPrivateShop\(\)\s*\|\|\s*!pc\.is_world\(\)\s*\)\s*continue\s*;\s*if\s*\(\s*effect\s*>\s*0\s*\)\s*SC_DisplayEffect\.send\(\s*pc\s*,\s*effect\s*\)\s*;\s*\}',re.S)
def blobsha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def patch(files):
    src=files[HELPER].decode("utf8")
    marker="    /**\n     * One frame for two existing recipient lists."
    assert src.count(marker)==1
    add=r'''    /** Global screen effects: keep the original live-world and private-shop
     * predicate, walking the same all-players view and retaining duplicate
     * recipients without a temporary selection array.
     */
    public static void sendAllPlayable(byte[] wire) {
        if(wire==null)return;
        try(var shared=new CAsyncSendConstBuffer(wire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:L1World.getInstance().getAllPlayers()) {
                if(pc.isPrivateShop() || !pc.is_world())continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null || !client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket!=null && socket.isClientConnected())
                    shared.send(socket,pc.getId());
            }
        }
    }

'''
    files[HELPER]=src.replace(marker,add+marker,1).encode("utf8")
    for path in TOWERS:
        src=files[path].decode("utf8")
        replacement="if(effect > 0) "+FQ+".sendAllPlayable(SC_DisplayEffect.sharedWire(effect));"
        src,count=PATTERN.subn(replacement,src)
        assert count==1,(path,count)
        files[path]=src.encode("utf8")
TEST=r'''import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_DisplayEffect;
public final class Phase21GlobalDisplayParity {
 static int n;
 static void test(int v) {
  byte[] original=ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
     .putShort((short)10).put((byte)Opcodes.S_EVENT).put((byte)0x53)
     .putInt(v).putShort((short)0).array();
  if(!Arrays.equals(original,SC_DisplayEffect.sharedWire(v)))
      throw new AssertionError("display parity "+n);
  n++;
 }
 public static void main(String[] unused) {
  for(int v:new int[]{0,1,2,3,6,Integer.MAX_VALUE,Integer.MIN_VALUE,-1})test(v);
  int r=0x6a721ca;
  for(int i=0;i<4096;i++){r^=r<<13;r^=r>>>17;r^=r<<5;test(r);}
  System.out.println("PHASE21_GLOBAL_DISPLAY_PARITY_OK "+n);
 }
}'''
def main():
    ap=argparse.ArgumentParser();ap.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase21"));args=ap.parse_args()
    raw=SRC.read_bytes()
    assert blobsha(raw)==PIN,"phase20 source ZIP mismatch"
    with zipfile.ZipFile(SRC) as z:
        assert z.testzip() is None
        files={f.filename:z.read(f) for f in z.infolist() if not f.is_dir()}
    original=files.copy()
    assert sum(p.endswith(".java") for p in files)==1710 and "CTransport.dll" in files
    patch(files)
    changes=sorted(p for p in files if original[p]!=files[p])
    assert changes==sorted(TARGETS),changes
    assert original["CTransport.dll"]==files["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for p,data in files.items():
        if p.startswith("src/") and p.endswith(".java"):
            f=args.work/p;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    test=args.work/"testsrc/Phase21GlobalDisplayParity.java"
    test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,data in sorted(files.items()):z.writestr(p,data)
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    audit={"phase":21,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
       "modified_java":changes,"java_sources":1710,
       "global_display_loops_shared":10,"private_shop_and_world_filter_preserved":True,
       "original_iteration_order_preserved":True,"native_unchanged":True,
       "no_new_queues_or_threads":True,"windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(audit,indent=2)+"\n")
    print(json.dumps(audit,indent=2))
if __name__=="__main__":main()
