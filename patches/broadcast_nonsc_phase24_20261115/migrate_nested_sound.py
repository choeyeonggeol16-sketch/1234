#!/usr/bin/env python3
"""Phase24: one immutable skill-sound source for nested chest AOI fanout.
Original algorithm: for each visible chest viewer, SC_SkillSound.broadcast(viewer)
to visible others of that viewer, excluding that viewer and other maps.
Do NOT flatten nested recipient fanout or suppress duplicates.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase23_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase24_FULL_SRC_20261115.zip"
PIN="6ae749fe9a0b961ed12d6d41e3b36538aba9bee7"
SHARED="src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java"
CHEST="src/l1j/server/MJTemplate/TreasureChest/MJL1TreasureChest.java"
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
TARGETS=[SHARED,CHEST]
def sha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def bounds(s,sig):
    assert s.count(sig)==1,(sig,s.count(sig))
    i=s.index(sig);j=s.index("{",i);depth=0
    for k in range(j,len(s)):
        if s[k]=="{":depth+=1
        elif s[k]=="}":
            depth-=1
            if depth==0:return i,k+1,s[i:k+1]
    raise AssertionError("unterminated "+sig)
ADD=r'''
    /**
     * Preserve legacy nested AOI sound expansion exactly: each anchor viewer
     * independently selects its own visible player set and excludes itself.
     * Repeated recipients are deliberately sent repeatedly in original order.
     * Only the immutable plaintext and native SEND source are now reused.
     */
    public static void sendNestedVisible(
            Iterable<? extends L1PcInstance> anchors, byte[] wire) {
        if(anchors==null || wire==null)return;
        final L1World world=L1World.getInstance();
        try(var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for(L1PcInstance anchor:anchors) {
                if(anchor==null)continue;
                for(L1PcInstance pc:world.getVisiblePlayer(anchor)) {
                    if(!CTLMFilter.sameWorldOther(anchor,pc))continue;
                    final CIOClient client=pc.getNetConnection();
                    if(client!=null && client.isConnected())
                        shared.send(client.getConnection(),pc.getId());
                }
            }
        }
    }

'''
def migrate(files):
    s=files[SHARED].decode("utf8")
    marker="    public static void sendRecipients(Iterable<? extends L1PcInstance> recipients,"
    assert s.count(marker)==1
    assert "public static void sendNestedVisible(" not in s
    s=s.replace(marker,ADD+marker,1)
    files[SHARED]=s.encode("utf8")
    s=files[CHEST].decode("utf8")
    i,j,old=bounds(s,"private void showCloud(int i){")
    assert old.count("for(L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this))")==1,old
    assert old.count("SC_SkillSound.broadcast(pc, getId(), i);")==1,old
    new="""private void showCloud(int i){
        final java.util.List<L1PcInstance> viewers =
                L1World.getInstance().getVisiblePlayer(this);
        if(viewers.isEmpty())return;
        """+FQ+""".sendNestedVisible(
                viewers,SC_SkillSound.sharedWire(getId(),i));
    }"""
    files[CHEST]=(s[:i]+new+s[j:]).encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
public final class Phase24SkillSoundParity {
    static int count;
    static void check(int id,int fx) {
        byte[] expected=ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((short)15).put((byte)Opcodes.S_EFFECT).putInt(id)
            .putShort((short)fx).putShort((short)0).putInt(0).array();
        if(!Arrays.equals(expected,SC_SkillSound.sharedWire(id,fx)))
            throw new AssertionError("skill frame "+count);
        count++;
    }
    public static void main(String[] args) {
        for(int id:new int[]{0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,0x12345678})
            for(int fx:new int[]{0,1,12157,12158,65535,-1,Integer.MAX_VALUE})
                check(id,fx);
        int r=0x1234567;
        for(int i=0;i<8192;i++){
            r^=r<<13;r^=r>>>17;r^=r<<5;
            check(r,r>>>16);
        }
        System.out.println("PHASE24_SKILL_SOUND_WIRE_PARITY_OK "+count);
    }
}'''
def main():
    a=argparse.ArgumentParser()
    a.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase24"))
    args=a.parse_args()
    b=SOURCE.read_bytes()
    assert sha(b)==PIN,"phase23 baseline differs"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    before=files.copy()
    assert "CTransport.dll" in files and sum(n.endswith(".java") for n in files)==1710
    migrate(files)
    changed=sorted(p for p in files if files[p]!=before[p])
    assert changed==sorted(TARGETS),changed
    assert files["CTransport.dll"]==before["CTransport.dll"]
    shared=files[SHARED].decode("utf8")
    chest=files[CHEST].decode("utf8")
    assert "CTLMFilter.sameWorldOther(anchor,pc)" in shared
    assert "for(L1PcInstance anchor:anchors)" in shared
    assert "for(L1PcInstance pc:world.getVisiblePlayer(anchor))" in shared
    assert "sendNestedVisible(" in chest and "SC_SkillSound.broadcast(pc, getId(), i)" not in chest
    args.work.mkdir(parents=True,exist_ok=True)
    for p,raw in files.items():
        if p.startswith("src/") and p.endswith(".java"):
            f=args.work/p;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(raw)
    test=args.work/"testsrc/Phase24SkillSoundParity.java"
    test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,raw in sorted(files.items()):z.writestr(p,raw)
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    result={"phase":24,"input_blob":PIN,
      "output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
      "modified_java":changed,"java_sources":1710,
      "nested_AOI_preserved":True,"duplicate_receivers_preserved":True,
      "one_shared_buffer_per_chest_effect":True,"self_and_map_filter_preserved":True,
      "native_unchanged":True,"no_new_queues_or_workers":True,"no_legacy_fallback":True,
      "windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps(result,indent=2))
if __name__=="__main__":main()
