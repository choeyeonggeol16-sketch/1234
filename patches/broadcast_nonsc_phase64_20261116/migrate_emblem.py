#!/usr/bin/env python3
"""Phase64: eliminate the remaining live emblem per-recipient serializer.
Preserves exact AOI list, recipient order/duplicates and 12-byte LE wire.
No new worker, queue, fallback, native ABI or class.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile,runpy
ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase62_FULL_SRC_20261116.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase64_FULL_SRC_20261116.zip"
PIN="556a289819e5f746c3ef0ba217cef54b981274ef"
TARGET="src/l1j/server/server/model/network/core/sc/packet/SC_ReturnedStat.java"
OLD="""    public static void broadcastEmblem(L1Character source, int objId, int emblemId) {
        if (source == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(source))
            sendEmblem(pc, objId, emblemId);
    }"""
NEW="""    /** Same S_VOICE_CHAT/0x3c body as sendEmblem, framed exactly once.
     * Native SEND still encrypts separately for each socket's rolling key.
     */
    private static byte[] emblemWire(int objectId, int emblemId) {
        final byte[] wire = new byte[12];
        wire[0] = 12;
        wire[2] = (byte) Opcodes.S_VOICE_CHAT;
        wire[3] = 0x3c;
        wire[4] = (byte) objectId;
        wire[5] = (byte) (objectId >>> 8);
        wire[6] = (byte) (objectId >>> 16);
        wire[7] = (byte) (objectId >>> 24);
        wire[8] = (byte) emblemId;
        wire[9] = (byte) (emblemId >>> 8);
        wire[10] = (byte) (emblemId >>> 16);
        wire[11] = (byte) (emblemId >>> 24);
        return wire;
    }

    public static void broadcastEmblem(L1Character source, int objId, int emblemId) {
        if (source == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipients(
                L1World.getInstance().getVisiblePlayer(source),
                emblemWire(objId, emblemId));
    }"""
TEST=r"""
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;

public final class Phase64EmblemParity {
    private static byte[] reference(int id,int emblem) {
        ByteBuffer bb=ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
        WriteBuffer w=new WriteBuffer();
        w.armExternal(bb,0,64);
        w.reset(Phase64EmblemParity.class);
        w.writeC(Opcodes.S_VOICE_CHAT);
        w.writeC(0x3c);
        w.writeD(id);
        w.writeD(emblem);
        int length=w.position();
        if(length!=10)throw new AssertionError("emblem payload not 10 bytes");
        byte[] expected=new byte[12];expected[0]=12;
        bb.position(0);bb.get(expected,2,10);
        w.detach();
        return expected;
    }
    public static void main(String[] args)throws Exception {
        Method m=SC_ReturnedStat.class.getDeclaredMethod(
                "emblemWire",int.class,int.class);
        m.setAccessible(true);
        Random random=new Random(0x632026);
        int[] edges={0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,
             0x01234567,0x80000001,0xff00ff00,1,255,256,65535};
        int checked=0;
        for(int a:edges)for(int b:edges) {
            byte[] actual=(byte[])m.invoke(null,a,b);
            if(!Arrays.equals(actual,reference(a,b)))
                throw new AssertionError("edge mismatch "+a+"/"+b);
            checked++;
        }
        for(int i=0;i<20000;i++) {
            int id=random.nextInt(),emblem=random.nextInt();
            byte[] actual=(byte[])m.invoke(null,id,emblem);
            if(!Arrays.equals(actual,reference(id,emblem)))
                throw new AssertionError("emblem wire mismatch at sample "+i);
            checked++;
        }
        System.out.println("PHASE64_EMBLEM_WIRE_PARITY_PASS="+checked);
    }
}
"""
def blob(data):return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\x00"+data).hexdigest()
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase64"))
    work=p.parse_args().work
    data=INPUT.read_bytes(); assert blob(data)==PIN,"pinned phase62 source changed"
    with zipfile.ZipFile(INPUT) as z:
        assert z.testzip() is None
        original={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    assert len(original)==1729 and sum(k.endswith(".java") for k in original)==1710
    files=original.copy()
    s=files[TARGET].decode("utf-8")
    assert s.count(OLD)==1,("unexpected emblem implementation",s[s.find("broadcastEmblem")-100:s.find("broadcastEmblem")+500])
    assert "emblemWire(" not in s
    files[TARGET]=s.replace(OLD,NEW,1).encode("utf-8")
    assert [n for n in files if files[n]!=original[n]]==[TARGET]
    assert all(files[n]==original[n] for n in files if n!=TARGET)
    assert files["CTransport.dll"]==original["CTransport.dll"]
    work.mkdir(parents=True,exist_ok=True)
    for n,contents in files.items():
        if n.startswith("src/") and n.endswith(".java"):
            d=work/n;d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes(contents)
    t=work/"testsrc";t.mkdir(exist_ok=True)
    t.joinpath("Phase64EmblemParity.java").write_text(TEST,encoding="utf-8")
    phase57=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase57_20261116/migrate_identical_wire_fanouts.py"))
    t.joinpath("Phase57SharedFrameParity.java").write_text(phase57["TEST"],encoding="utf-8")
    phase60=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase60_20261116/migrate_chat_poison.py"))
    t.joinpath("Phase60ChatParity.java").write_text(phase60["TEST_SOURCE"],encoding="utf-8")
    phase61=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase61_20261116/migrate_gm_messages.py"))
    t.joinpath("Phase61GmParity.java").write_text(phase61["TEST"],encoding="utf-8")
    phase62=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase62_20261116/restore_legacy_size_guard.py"))
    t.joinpath("Phase62AdmissionParity.java").write_text(phase62["TEST"],encoding="utf-8")
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n in sorted(files):z.writestr(n,files[n])
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    audit={"phase":64,"java_sources":1710,"entries":1729,"modified_java":[TARGET],
       "former_live_repeated_encoders_removed":1,"former_callers":3,
       "shared_native_SEND_path_preserved":True,
       "native_unchanged":True,"unrelated_files_identical":True,
       "new_worker_queue_fallback":False,"in_game_tested":False,
       "zip_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest()}
    (work/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(audit,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
