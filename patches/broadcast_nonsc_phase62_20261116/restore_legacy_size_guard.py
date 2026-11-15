#!/usr/bin/env python3
"""Phase62: close a real broadcast-migration acceptance regression.
The legacy per-recipient direct serializer refused a text once its *reserved*
capacity (32 + 4*UTF16-codeunits) exceeded 0xffff; sharedWire only checked the
actual wire size. Preserve the old boundary on the four migrated chat fanouts.
No legacy serializer, alternate queue/worker, or compatibility send fallback.
"""
import argparse,hashlib,json,zipfile,runpy
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase61_FULL_SRC_20261116.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase62_FULL_SRC_20261116.zip"
INPUT_BLOB_SHA1="9703fa377e1c9e7d6aa4cd1b59c2b1baebffc999"
CHAT="src/l1j/server/server/model/network/core/sc/packet/SC_ChatPacket.java"

def gitblob(raw):
    return hashlib.sha1(b"blob "+str(len(raw)).encode()+b"\0"+raw).hexdigest()

def edit_method(source,signature):
    assert source.count(signature)==1,(signature,source.count(signature))
    start=source.index(signature)
    brace=source.index("{",start)
    depth=0
    for p in range(brace,len(source)):
        if source[p]=="{": depth+=1
        elif source[p]=="}":
            depth-=1
            if not depth:
                old=source[start:p+1]
                assert old.count("sharedWire(")==1,(signature,old.count("sharedWire("))
                return source[:start]+old.replace("sharedWire(","sharedDirectAdmissionWire(",1)+source[p+1:]
    raise AssertionError(signature+" unbalanced")

HELPER=r"""
    /**
     * The previous sendDirectRaw caller reserved 32 + 4*(text1.length +
     * text2.length) bytes BEFORE encoding; beginDirectFrame rejected sizes
     * above CIOBufferPool.MAX_WIRE_BYTES (65535). The four migrated fanouts
     * all passed text2="" and must preserve that refusal boundary.
     *
     * Actual encoding still happens once by the existing sharedWire helper.
     * This is a size guard, not a serializer or a fallback path.
     */
    private static byte[] sharedDirectAdmissionWire(int opcode, int type,
            int objectId, String text, boolean includeObjectId) {
        final String safe = text == null ? "" : text;
        if (32L + 4L * safe.length() > 0xFFFFL) return null;
        return sharedWire(opcode, type, objectId, safe, includeObjectId);
    }

"""

TEST=r"""
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Random;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

public final class Phase62AdmissionParity {
    private static void check(boolean condition, String message) {
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        final Method newMethod=SC_ChatPacket.class.getDeclaredMethod(
            "sharedDirectAdmissionWire",int.class,int.class,int.class,
            String.class,boolean.class);
        final Method plain=SC_ChatPacket.class.getDeclaredMethod(
            "sharedWire",int.class,int.class,int.class,
            String.class,boolean.class);
        newMethod.setAccessible(true);
        plain.setAccessible(true);
        int checks=0;
        for(int length:new int[] {0,1,2,64,1024,4096,8192,16000,16374,16375,16376,16377,25000,60000}) {
            for(String unit:new String[]{"A","가","漢","\ud83d\ude00"}) {
                final String value=unit.repeat(length);
                for(boolean withId:new boolean[]{false,true}) {
                    final byte[] got=(byte[])newMethod.invoke(null,
                        Opcodes.S_MESSAGE,3,0x10203040,value,withId);
                    final boolean oldAllowed=32L+4L*value.length()<=65535;
                    if(!oldAllowed)check(got==null,"previously refused length "+value.length());
                    else {
                        final byte[] expected=(byte[])plain.invoke(null,
                            Opcodes.S_MESSAGE,3,0x10203040,value,withId);
                        check(Arrays.equals(got,expected),"allowed wire changed");
                    }
                    checks++;
                }
            }
        }
        final Random rng=new Random(0x63b26);
        for(int i=0;i<3000;i++) {
            final StringBuilder sb=new StringBuilder();
            final int len=rng.nextInt(25000);
            for(int k=0;k<len;k++) {
                final int x=rng.nextInt(10);
                sb.append(x<4?'A':x<7?'가':x<9?'漢':'x');
            }
            final String val=sb.toString();
            final byte[] got=(byte[])newMethod.invoke(null,
                Opcodes.S_MESSAGE,2,0,val,false);
            if(32L+4L*len>65535)check(got==null,"fuzz over-boundary");
            else check(Arrays.equals(got,(byte[])plain.invoke(null,
                    Opcodes.S_MESSAGE,2,0,val,false)),
                    "fuzz wire changed");
            checks++;
        }
        check(SC_ChatPacket.sharedTargetWire("",3,"A".repeat(16372))!=null,
            "target GM at boundary should be allowed");
        check(SC_ChatPacket.sharedTargetWire("",3,"A".repeat(16373))==null,
            "target GM oversized request must be refused");
        checks+=2;
        System.out.println("PHASE62_DIRECT_ADMISSION_PARITY_PASS="+checks);
    }
}
"""

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase62"))
    work=parser.parse_args().work
    data=INPUT.read_bytes()
    assert gitblob(data)==INPUT_BLOB_SHA1,"phase61 source pin mismatch"
    with zipfile.ZipFile(INPUT) as z:
        assert z.testzip() is None
        original={entry.filename:z.read(entry) for entry in z.infolist() if not entry.is_dir()}
    assert sum(n.endswith(".java") for n in original)==1710
    assert "CTransport.dll" in original
    files=original.copy()
    s=files[CHAT].decode("utf-8")
    assert "sharedDirectAdmissionWire(" not in s
    for signature in [
        "public static void broadcastMessage(String chat)",
        "public static void broadcastOpcode(String chat, int opcode)",
        "public static void broadcastComplex0(L1PcInstance source, String chat, int opcode)",
        "public static byte[] sharedTargetWire(String targetName, int type, String chat)"
    ]:
        s=edit_method(s,signature)
    marker="    private static boolean sendDirectRaw("
    assert s.count(marker)==1,marker
    s=s.replace(marker,HELPER+marker,1)
    assert s.count("sharedDirectAdmissionWire(")==5 # 4 callers + helper
    files[CHAT]=s.encode("utf-8")
    changed=[name for name in files if files[name]!=original[name]]
    assert changed==[CHAT],changed
    assert all(original[x]==files[x] for x in original if x!=CHAT)
    work.mkdir(parents=True,exist_ok=True)
    for name,body in files.items():
        if name.startswith("src/") and name.endswith(".java"):
            dst=work/name;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(body)
    testdir=work/"testsrc"
    testdir.mkdir(exist_ok=True)
    testdir.joinpath("Phase62AdmissionParity.java").write_text(TEST,encoding="utf-8")
    phase57=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase57_20261116/migrate_identical_wire_fanouts.py"))
    testdir.joinpath("Phase57SharedFrameParity.java").write_text(phase57["TEST"],encoding="utf-8")
    phase60=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase60_20261116/migrate_chat_poison.py"))
    testdir.joinpath("Phase60ChatParity.java").write_text(phase60["TEST_SOURCE"],encoding="utf-8")
    phase61=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase61_20261116/migrate_gm_messages.py"))
    testdir.joinpath("Phase61GmParity.java").write_text(phase61["TEST"],encoding="utf-8")
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as out:
        for name in sorted(files):out.writestr(name,files[name])
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    report={
       "phase":62,"issue":"Shared chat fanout accepted frames previously refused by sendDirectRaw reservation-capacity check",
       "previous_refusal_requested_capacity":"32 + 4 * UTF16_length > 65535",
       "changed_java":[CHAT],"java_count":1710,"unchanged_other_bytes":True,
       "native_unchanged":True,"new_fallback_worker_queue":False,
       "windows_iocp_runtime_tested":False,
       "source_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    }
    (work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
