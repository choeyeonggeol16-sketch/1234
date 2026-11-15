#!/usr/bin/env python3
"""Phase24: convert precisely matched indun green notice fanouts to one wire.
Retain the existing L1World.getVisibleObjects(_map).values() iteration and
its iteration order, including non-PC filtering and duplicate recipients.
Per-recipient clan-name announcements are deliberately NOT converted.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase23_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase24_FULL_SRC_20261115.zip"
PIN="6ae749fe9a0b961ed12d6d41e3b36538aba9bee7"
BASE="src/l1j/server/"
SHARED=BASE+"server/model/network/core/broadcast/CSharedBroadcast.java"
BOSS=BASE+"IndunSystem/BossRaid/BossRaid.java"
QUEEN=BASE+"IndunSystem/BossRaid2/Queen1.java"
AZ=BASE+"IndunSystem/ClanDungeon/Azmodan.java"
DEMON=BASE+"IndunSystem/Ice/Demon/Demon.java"
HADIN=BASE+"IndunSystem/Hadin/HadinTrap.java"
RAID=BASE+"MJRaidSystem/MJRaidMessage.java"
TARGETS=[SHARED,BOSS,QUEEN,AZ,DEMON,HADIN,RAID]
CLASS="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
BUFFER="l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer"
def sha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def bounds(src,sig):
    assert src.count(sig)==1,(sig,src.count(sig))
    a=src.index(sig);b=src.index("{",a);depth=0
    for k in range(b,len(src)):
        if src[k]=="{":depth+=1
        elif src[k]=="}":
            depth-=1
            if depth==0:return a,k+1,src[a:k+1]
    raise AssertionError("unterminated "+sig)
ADD=r'''
    /** One immutable GREEN_MESSAGE or other framed packet for the existing
     * L1Object registry value traversal. Only PCs receive the frame.
     * No AOI selection replacement, sorting, deduplication or copy.
     */
    public static void sendObjectPlayers(
            Iterable<? extends L1Object> objects, byte[] wire) {
        if(objects==null || wire==null)return;
        try(var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1Object obj:objects){
                if(obj instanceof L1PcInstance)
                    sendRecipient(shared,(L1PcInstance)obj);
            }
        }
    }

'''
LOOP=re.compile(
    r"for\s*\(\s*L1Object\s+obj\s*:\s*L1World\.getInstance\(\)\.getVisibleObjects\(_map\)\.values\(\)\s*\)\s*\{\s*"
    r"if\s*\(\s*obj\s+instanceof\s+L1PcInstance\s*\)\s*\{\s*"
    r"L1PcInstance\s+pc\s*=\s*\(L1PcInstance\)\s*obj;\s*"
    r"SC_PacketBox\.send\(\s*pc\s*,\s*SC_PacketBox\.GREEN_MESSAGE\s*,\s*(.*?)\s*\);\s*"
    r"\}\s*\}",re.DOTALL)
HADIN_LOOP=re.compile(
    r"for\s*\(\s*L1Object\s+ob\s*:\s*cklist\s*\)\s*\{\s*"
    r"if\s*\(\s*ob\s*==\s*null\s*\)\s*continue;\s*"
    r"if\s*\(\s*ob\s+instanceof\s+L1PcInstance\s*\)\s*\{\s*"
    r"L1PcInstance\s+pc\s*=\s*\(L1PcInstance\)\s*ob;\s*"
    r"SC_PacketBox\.send\(\s*pc\s*,\s*SC_PacketBox\.GREEN_MESSAGE\s*,\s*(.*?)\s*\);\s*"
    r"\}\s*\}",re.DOTALL)
def migrate(files):
    s=files[SHARED].decode("utf8")
    anchor="    public static void sendRecipients(Iterable<? extends L1PcInstance> recipients,"
    assert s.count(anchor)==1 and "public static void sendObjectPlayers(" not in s
    files[SHARED]=s.replace(anchor,ADD+anchor,1).encode("utf8")
    count=0
    for path,expected in ((BOSS,1),(QUEEN,5),(AZ,2),(DEMON,2)):
        s=files[path].decode("utf8")
        matches=list(LOOP.finditer(s))
        assert len(matches)==expected,(path,len(matches),expected)
        for m in matches:
            expr=m.group(1).strip()
            assert "pc." not in expr and "getClanname" not in expr,(path,expr)
            assert "SC_PacketBox" not in expr,(path,expr)
        s,n=LOOP.subn(lambda m:
            CLASS+".sendObjectPlayers(\n"+
            "                    L1World.getInstance().getVisibleObjects(_map).values(),\n"+
            "                    SC_PacketBox.sharedGreenWire("+m.group(1).strip()+"));",s)
        assert n==expected
        files[path]=s.encode("utf8")
        count+=n
    s=files[HADIN].decode("utf8")
    matches=list(HADIN_LOOP.finditer(s))
    assert len(matches)==1,len(matches)
    assert matches[0].group(1).strip()=='"$"+count'
    s,n=HADIN_LOOP.subn(
      CLASS+".sendObjectPlayers(cklist,SC_PacketBox.sharedGreenWire(\"$\"+count));",s)
    assert n==1
    files[HADIN]=s.encode("utf8");count+=n
    s=files[RAID].decode("utf8")
    for sig,wire,oldname in (
      ("public void sendSystemMessage(ArrayList<L1PcInstance> pcs){","SC_SystemMessage.sharedWire(_msg)","SC_SystemMessage.send(pc, _msg)"),
      ("public void sendGreenMessage(ArrayList<L1PcInstance> pcs){","SC_PacketBox.sharedGreenWire(_msg)","SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, _msg)")):
        i,j,old=bounds(s,sig)
        assert old.count(oldname)==1,(sig,old)
        assert "int size = pcs.size();" in old
        new='''public void '''+("sendSystemMessage" if "sendSystemMessage" in sig else "sendGreenMessage")+'''(ArrayList<L1PcInstance> pcs){
        int size=pcs.size();
        try(var shared=new '''+BUFFER+'''(
                '''+wire+''',Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(int i=0;i<size;i++){
                L1PcInstance pc=pcs.get(i);
                if(pc!=null)'''+CLASS+'''.sendRecipient(shared,pc);
            }
        }
    }'''
        s=s[:i]+new+s[j:]
    files[RAID]=s.encode("utf8")
    assert "pc.getClanname()" in files[QUEEN].decode("utf8")
    return count
TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase24IndunGreenParity {
    static int checks;
    static void eq(byte[] a,byte[] b){
        if(!Arrays.equals(a,b))throw new AssertionError("packet parity "+checks);
        checks++;
    }
    static byte[] green(String s){
        byte[] t=s.getBytes(Charset.forName("MS949"));
        byte[] wire=new byte[t.length+6];
        wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
        wire[2]=(byte)Opcodes.S_EVENT;wire[3]=(byte)SC_PacketBox.GREEN_MESSAGE;
        wire[4]=2;System.arraycopy(t,0,wire,5,t.length);
        return wire;
    }
    static byte[] system(String s){
        byte[] t=s.getBytes(Charset.forName("MS949"));
        byte[] wire=new byte[t.length+5];
        wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
        wire[2]=(byte)Opcodes.S_MESSAGE;wire[3]=9;
        System.arraycopy(t,0,wire,4,t.length);
        return wire;
    }
    static void check(String s){
        eq(green(s),SC_PacketBox.sharedGreenWire(s));
        eq(system(s),SC_SystemMessage.sharedWire(s));
    }
    public static void main(String[] args){
        for(String s:new String[]{"","$1","10분 후에 마을로 강제 이동 됩니다.",
            "\\f3??? : 나에게 도착 할 수 있을거라 생각하는가? 어리석군...",
            "30초후 지하통로가 닫힙니다.","기존의 기록을 갱신하는데 실패하였습니다."})check(s);
        int v=0x1234567;
        for(int i=0;i<4096;i++){
            v^=v<<13;v^=v>>>17;v^=v<<5;
            check("인던 "+v+"분 후에 마을로 강제 이동 됩니다.");
        }
        System.out.println("PHASE24_INDUN_WIRE_PARITY_OK "+checks);
    }
}'''
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase24"));args=p.parse_args()
    raw=SOURCE.read_bytes()
    assert sha(raw)==PIN,"phase23 baseline changed"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    before=files.copy()
    assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
    count=migrate(files)
    assert count==11,count
    changed=sorted(p for p in files if files[p]!=before[p])
    assert changed==sorted(TARGETS),changed
    assert files["CTransport.dll"]==before["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for p,raw in files.items():
        if p.startswith("src/") and p.endswith(".java"):
            f=args.work/p;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(raw)
    test=args.work/"testsrc/Phase24IndunGreenParity.java"
    test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,data in sorted(files.items()):z.writestr(p,data)
    with zipfile.ZipFile(OUTPUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
    report={"phase":24,"input_blob":PIN,
      "output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
      "java_sources":1710,"modified_java":changed,"shared_green_indun_loops":count,
      "raid_message_list_loops":2,"per_pc_clan_message_unchanged":True,
      "map_object_iteration_preserved":True,"recipient_order_and_duplicates_preserved":True,
      "native_unchanged":True,"no_workers_or_queues_added":True,"no_legacy_fallback":True,
      "windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
    print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
