#!/usr/bin/env python3
"""Phase61 strict minimal-diff broadcast migration:
keep phase60 raid poison shared-frame fanout, restore byte-identical phase58
SC_ChatPacket (already shared before phase60), test direct REMOVE wire parity.
No fallback, new queues, threads or native changes.
"""
from pathlib import Path
import hashlib,zipfile,argparse,json,re,runpy
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase58_FULL_SRC_20261116.zip"
PREVIOUS=ROOT/"downloads/RKern_Broadcast_NonSC_Phase60_FULL_SRC_20261116.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase61_FULL_SRC_20261116.zip"
BASE_SHA="7593606d8cb04fb90207e7fc53dc70f9de19bbfb218d8cd9be3fc8247e0a65f3"
PREVIOUS_SHA="064ac992cbbf8bb71eba084eb2b33cc44bea2ce11f13e622d08a48ea643da362"
CHAT="src/l1j/server/server/model/network/core/sc/packet/SC_ChatPacket.java"
POISON="src/l1j/server/MJRaidSystem/Util/L1MJRaidPoisonInstance.java"
REMOVE="src/l1j/server/server/model/network/core/sc/packet/SC_RemoveObject.java"
TEST=r"""
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;

public final class Phase61RemoveParity {
  public static void main(String[] args) throws Exception {
    final int[] edges={0,1,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,
        0x78563412,0x01000000,0x00010000,0x00000100,0x7f80ff00};
    final Random rnd=new Random(0x61ad0c1);
    int checked=0;
    for (int k=0;k<10010;k++) {
      final int id=k<edges.length?edges[k]:rnd.nextInt();
      final byte[] wire=SC_RemoveObject.broadcastWire(id);
      final ByteBuffer buf=ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
      final WriteBuffer w=new WriteBuffer();
      w.armExternal(buf,0,64);
      w.reset(Phase61RemoveParity.class);
      // Exact original SC_RemoveObject.send() writes, in unchanged field order.
      w.writeC(Opcodes.S_REMOVE_OBJECT);
      w.writeD(id);
      w.writeH(0);
      final int n=w.position();
      final byte[] expected=new byte[n+2];
      expected[0]=(byte)(n+2);expected[1]=(byte)((n+2)>>>8);
      buf.position(0);buf.get(expected,2,n);
      if(wire.length!=9||!Arrays.equals(wire,expected))
         throw new AssertionError("REMOVE parity objectId="+id
            +" got="+Arrays.toString(wire)+" expected="+Arrays.toString(expected));
      w.detach();
      checked++;
    }
    System.out.println("PHASE61_REMOVE_WIRE_PARITY_OK "+checked);
  }
}
"""
def contents(path):
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None,path
        return {info.filename:z.read(info) for info in z.infolist() if not info.is_dir()}
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase61"))
    work=p.parse_args().work
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==BASE_SHA
    assert hashlib.sha256(PREVIOUS.read_bytes()).hexdigest()==PREVIOUS_SHA
    old=contents(BASE);current=contents(PREVIOUS)
    assert set(old)==set(current)
    assert sum(n.endswith(".java") for n in current)==1710
    prior_changed={n for n in old if old[n]!=current[n]}
    assert prior_changed=={CHAT,POISON},prior_changed
    # Do not risk changing chat recipient snapshot timing; old implementation
    # already sends a single sharedWire to the exact recipients.
    assert b"CSharedBroadcast.sendRecipients(" in old[CHAT]
    files=current.copy();files[CHAT]=old[CHAT]
    changed={n for n in files if old[n]!=files[n]}
    assert changed=={POISON},changed
    assert files["CTransport.dll"]==old["CTransport.dll"]
    assert files[REMOVE]==old[REMOVE]
    assert all(v==old[n] for n,v in files.items() if n!=POISON)
    obj=files[REMOVE].decode("utf8")
    assert re.search(r"buffer\.writeC\(Opcodes\.S_REMOVE_OBJECT\)\s*;\s*buffer\.writeD\(objectId\)\s*;\s*buffer\.writeH\(0\)",obj)
    assert "public static byte[] broadcastWire(int objectId)" in obj
    poison=files[POISON].decode("utf8")
    assert poison.count("SC_RemoveObject.broadcastWire(getId())")==1
    assert poison.count("CSharedBroadcast.sendRecipient(shared, pc);")==1
    assert poison.count("pc.removeKnownObject(this);")==1
    assert poison.count("getRecognizePlayer(this)") == 1
    assert poison.index("pc.removeKnownObject(this);")<poison.index("CSharedBroadcast.sendRecipient(shared, pc);")
    assert poison.index("CSharedBroadcast.sendRecipient(shared, pc);")<poison.index("removeAllKnownObjects();")
    assert "SC_RemoveObject.send(" not in poison
    work.mkdir(parents=True,exist_ok=True)
    for name,body in files.items():
        if name.startswith("src/") and name.endswith(".java"):
            target=work/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(body)
    t=work/"testsrc";t.mkdir(parents=True,exist_ok=True)
    (t/"Phase61RemoveParity.java").write_text(TEST,encoding="utf8")
    prev=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase57_20261116/migrate_identical_wire_fanouts.py"))
    (t/"Phase57SharedFrameParity.java").write_text(prev["TEST"],encoding="utf8")
    chat=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase60_20261116/migrate_chat_poison.py"))
    (t/"Phase60ChatParity.java").write_text(chat["TEST_SOURCE"],encoding="utf8")
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n in sorted(files):z.writestr(n,files[n])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    audit={
      "phase":61,"source_base_sha256":BASE_SHA,
      "source_phase60_sha256":PREVIOUS_SHA,
      "java_count":1710,
      "changed_since_phase58":sorted(changed),
      "phase60_chat_redundant_modification_removed":True,
      "phase60_raid_poison_single_frame_retained":True,
      "recognize_iteration_known_object_order_unchanged":True,
      "SC_RemoveObject_direct_payload_equal_shared_payload": "byte-level Java regression required",
      "no_new_workers_queues_fallback":True,
      "native_unchanged":True,
      "windows_iocp_runtime_tested":False,
      "zip_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest()}
    (work/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n")
    print("PHASE61_MINIMAL_MIGRATION",json.dumps(audit,ensure_ascii=False))
if __name__=="__main__":main()
