#!/usr/bin/env python3
"""Phase14: treasure chest nested visibility cloud effects reuse one const frame.
Crucial: preserve original two independent AOI queries and repeated recipients;
the previous 'broadcast around every outer viewer' must never be simplified to
one chest AOI broadcast. Preserve CTLMFilter map/self check per inner anchor.
"""
from pathlib import Path
import hashlib,zipfile,json,argparse
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase13_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase14_FULL_SRC_20261115.zip"
PIN="9d3d02c635e1d62010b5e2d8d33c931ee2474ab1"
CHEST="src/l1j/server/MJTemplate/TreasureChest/MJL1TreasureChest.java"
SHARED="src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java"
C="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

def blobsha(data):return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
def method(s,signature):
    assert s.count(signature)==1,(signature,s.count(signature))
    i=s.index(signature);j=s.index("{",i);depth=0
    for k in range(j,len(s)):
        if s[k]=="{":depth+=1
        elif s[k]=="}":
            depth-=1
            if not depth:return i,k+1,s[i:k+1]
    raise AssertionError(signature)

ADD=r'''
    /** Preserve the original nested AOI semantics used by treasure-chest
     * cloud effects: for each chest viewer, rebroadcast around that viewer.
     * Do not coalesce, deduplicate, or change the iteration sequence.
     * The same immutable frame is nevertheless serialized only once.
     */
    public static void sendNestedVisible(L1Object origin,byte[] wire) {
        if(origin==null || wire==null)return;
        final L1World world=L1World.getInstance();
        try(var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for(L1PcInstance anchor:world.getVisiblePlayer(origin)) {
                if(anchor==null)continue;
                for(L1PcInstance recipient:world.getVisiblePlayer(anchor)) {
                    if(!CTLMFilter.sameWorldOther(anchor,recipient))continue;
                    final CIOClient client=recipient.getNetConnection();
                    if(client!=null && client.isConnected())
                        shared.send(client.getConnection(),recipient.getId());
                }
            }
        }
    }

'''
def patch_shared(src):
    assert "public static void sendNestedVisible(" not in src
    marker="    /** Global broadcast includes the sender and uses the normal socket FIFO. */"
    assert src.count(marker)==1
    assert "public static void sendRecipients(" in src
    return src.replace(marker,ADD+marker,1)
def patch_chest(src):
    signature="private void showCloud(int i){"
    a,b,old=method(src,signature)
    assert "L1World.getInstance().getVisiblePlayer(this)" in old
    assert "SC_SkillSound.broadcast(pc, getId(), i)" in old
    new="""private void showCloud(int i){
        """+C+""".sendNestedVisible(
                this,SC_SkillSound.sharedWire(getId(),i));
    }"""
    assert "showCloud(12157)" in src and "showCloud(12158)" in src
    return src[:a]+new+src[b:]
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
public final class ChestCloudParity14 {
    static int checks;
    static byte[] reference(int id,int gfx) {
        return ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN)
           .putShort((short)15).put((byte)Opcodes.S_EFFECT).putInt(id)
           .putShort((short)gfx).putShort((short)0).putInt(0).array();
    }
    static void check(int id,int gfx) {
        if(!Arrays.equals(reference(id,gfx),SC_SkillSound.sharedWire(id,gfx)))
            throw new AssertionError("cloud parity "+checks);
        checks++;
    }
    public static void main(String[] args) {
        for(int id:new int[]{0,1,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,0x12345678})
          for(int gfx:new int[]{0,1,12157,12158,65535}) check(id,gfx);
        int r=0x1a5b4ef;
        for(int i=0;i<4096;i++) {
            r^=r<<13;r^=r>>>17;r^=r<<5;check(r,r>>>13);
        }
        System.out.println("CHEST_CLOUD_PARITY14_OK "+checks);
    }
}'''
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase14"));a=p.parse_args()
    raw=SOURCE.read_bytes()
    assert blobsha(raw)==PIN,"phase13 verified source mismatch"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    old=files.copy()
    assert sum(p.endswith(".java") for p in files)==1711 and "CTransport.dll" in files
    files[SHARED]=patch_shared(files[SHARED].decode("utf8")).encode("utf8")
    files[CHEST]=patch_chest(files[CHEST].decode("utf8")).encode("utf8")
    changes=sorted(p for p in files if files[p]!=old[p])
    assert changes==sorted([SHARED,CHEST]),changes
    assert files["CTransport.dll"]==old["CTransport.dll"]
    a.work.mkdir(parents=True,exist_ok=True)
    for p,bytes_ in files.items():
        if p.startswith("src/") and p.endswith(".java"):
            d=a.work/p;d.parent.mkdir(parents=True,exist_ok=True);d.write_bytes(bytes_)
    tst=a.work/"testsrc/ChestCloudParity14.java";tst.parent.mkdir(parents=True,exist_ok=True);tst.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,b in sorted(files.items()):z.writestr(p,b)
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    report={"phase":14,"input_blob":PIN,
       "sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
       "modified_java":changes,"java_sources":1711,
       "nested_AOI_order_and_duplicates_preserved":True,
       "one_shared_cloud_frame":True,"native_dll_unchanged":True,
       "no_legacy_fallback":True,"windows_runtime_tested":False}
    (a.work/"audit.json").write_text(json.dumps(report,indent=2)+"\n")
    print(json.dumps(report,indent=2))
if __name__=="__main__":main()
