#!/usr/bin/env python3
"""Phase61: remove repeated GM broadcast encoders (global chat, clan leave).
Keeps original all-player/array recipient order; never changes transport policy.
"""
import argparse,hashlib,zipfile,runpy,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase60_FULL_SRC_20261116.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase61_FULL_SRC_20261116.zip"
PIN="d9bb631ddc63f924d1160887728730634237a56e"
CHAT="src/l1j/server/server/model/network/core/sc/packet/SC_ChatPacket.java"
GM="src/l1j/server/server/GMCommands.java"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def swap_loop(s,start_marker,contains,replacement):
    assert s.count(start_marker)==1,(start_marker,s.count(start_marker))
    start=s.index(start_marker)
    brace=s.index("{",start)
    depth=0
    for end in range(brace,len(s)):
        if s[end]=="{":depth+=1
        elif s[end]=="}":
            depth-=1
            if depth==0:
                old=s[start:end+1]
                assert contains in old,(contains,old)
                return s[:start]+replacement+s[end+1:]
    raise AssertionError("unclosed "+start_marker)
TARGET="""
    /** Frame for GM target-name chat; no packet allocation in recipient loop. */
    public static byte[] sharedTargetWire(String targetName, int type, String chat) {
        final String formatted="["+(targetName==null?"":targetName)+"] "
                +(chat==null?"":chat);
        return sharedWire(Opcodes.S_MESSAGE,type,0,formatted,false);
    }

"""
CHAT_REPLACE="""l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll(
                        SC_ChatPacket.sharedTargetWire(name, 0x03, msg));"""
CLAN_REPLACE="""l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendArray(
                        clanMember,
                        SC_ServerMessage.sharedWire(ServerMessage.LEAVE_CLAN, param, clan.getClanName()));"""
TEST=r"""
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class Phase61GmParity {
    private static byte[] directServer(int id,String a,String b) {
        final ByteBuffer bb=ByteBuffer.allocate(65535).order(ByteOrder.LITTLE_ENDIAN);
        final WriteBuffer w=new WriteBuffer();
        w.armExternal(bb,0,65535);
        w.reset(Phase61GmParity.class);
        w.writeC(Opcodes.S_MESSAGE_CODE);w.writeH(id);
        w.writeC((a!=null?1:0)+(b!=null?1:0));
        if(a!=null)w.writeS(a);
        if(b!=null)w.writeS(b);
        final int n=w.position();
        final byte[] expected=new byte[n+2];
        expected[0]=(byte)(n+2);expected[1]=(byte)((n+2)>>>8);
        bb.position(0);bb.get(expected,2,n);
        w.detach();
        return expected;
    }
    public static void main(String[] args) throws Exception {
        final Method m=SC_ChatPacket.class.getDeclaredMethod("sharedWire",
                int.class,int.class,int.class,String.class,boolean.class);
        m.setAccessible(true);
        final String[] texts={"","hello","혈맹 탈퇴","[GM] 한글","한글★",null,"😀","A".repeat(250)};
        int checks=0;
        for(String name:texts)for(String message:texts)for(int type:new int[]{0,1,3,15}) {
            final String formatted="["+(name==null?"":name)+"] "+(message==null?"":message);
            final byte[] reference=(byte[])m.invoke(null,Opcodes.S_MESSAGE,type,0,formatted,false);
            final byte[] actual=SC_ChatPacket.sharedTargetWire(name,type,message);
            if(!Arrays.equals(actual,reference))
                throw new AssertionError("target chat mismatch");
            checks++;
        }
        for(String a:texts)for(String b:texts)for(int type:new int[]{0,1,3,94,127,65535}) {
            final byte[] actual=SC_ServerMessage.sharedWire(type,a,b);
            if(!Arrays.equals(actual,directServer(type,a,b)))
                throw new AssertionError("server message mismatch "+type+"/"+a+"/"+b);
            checks++;
        }
        System.out.println("PHASE61_GM_WIRE_PARITY_PASS="+checks);
    }
}
"""
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase61"))
    work=p.parse_args().work
    raw=INPUT.read_bytes();assert blob(raw)==PIN,"phase60 pin changed"
    with zipfile.ZipFile(INPUT) as z:
        assert z.testzip() is None
        original={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    assert sum(x.endswith(".java") for x in original)==1710
    files=original.copy()
    s=files[CHAT].decode("utf-8")
    anchor="    public static boolean sendTargetMsg(L1PcInstance receiver, String targetName, int type, String chat) {"
    assert s.count(anchor)==1
    assert "sharedTargetWire(" not in s
    files[CHAT]=s.replace(anchor,TARGET+anchor,1).encode("utf-8")
    s=files[GM].decode("utf-8")
    s=swap_loop(s,
        "for (L1PcInstance listner : L1World.getInstance().getAllPlayers())",
        "SC_ChatPacket.sendTargetMsg(listner, name, 0x03, msg);",CHAT_REPLACE)
    s=swap_loop(s,
        "for (int i = 0; i < clanMember.length; i++)",
        "SC_ServerMessage.send(clanMember[i], ServerMessage.LEAVE_CLAN",CLAN_REPLACE)
    files[GM]=s.encode("utf-8")
    changed=sorted(k for k in files if files[k]!=original[k])
    assert changed==sorted((CHAT,GM)),changed
    assert files["CTransport.dll"]==original["CTransport.dll"]
    assert all(files[k]==v for k,v in original.items() if k not in changed)
    work.mkdir(parents=True,exist_ok=True)
    for k,v in files.items():
        if k.startswith("src/") and k.endswith(".java"):
            f=work/k;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(v)
    t=work/"testsrc";t.mkdir(exist_ok=True)
    t.joinpath("Phase61GmParity.java").write_text(TEST,encoding="utf8")
    prior=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase57_20261116/migrate_identical_wire_fanouts.py"))
    t.joinpath("Phase57SharedFrameParity.java").write_text(prior["TEST"],encoding="utf8")
    p60=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase60_20261116/migrate_chat_poison.py"))
    t.joinpath("Phase60ChatParity.java").write_text(p60["TEST_SOURCE"],encoding="utf8")
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for k in sorted(files):z.writestr(k,files[k])
    with zipfile.ZipFile(OUTPUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
    audit={"phase":61,"java":1710,"modified":changed,"global_gm_chat_shared":1,
        "clan_leave_shared":1,"no_legacy_serializer_in_fanout":True,
        "other_paths_unchanged":True,"native_unchanged":True,
        "new_workers_queues":0,"windows_iocp_runtime_tested":False,
        "sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest()}
    (work/"audit.json").write_text(json.dumps(audit,indent=2,ensure_ascii=False),encoding="utf8")
    print(json.dumps(audit,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
