#!/usr/bin/env python3
"""Phase60: use one immutable wire for global chat and raid-poison removal.
No new queue, worker, class, fallback, or change to socket SEND ordering.
"""
from pathlib import Path
import argparse,hashlib,zipfile,runpy,json

ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase58_FULL_SRC_20261116.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase60_FULL_SRC_20261116.zip"
PIN="c895ee4251a3c57ccec5de75d84dc1eae9baeabb"
CHAT="src/l1j/server/server/model/network/core/sc/packet/SC_ChatPacket.java"
POISON="src/l1j/server/MJRaidSystem/Util/L1MJRaidPoisonInstance.java"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def change_method(src,signature,replacement):
    assert src.count(signature)==1,(signature,src.count(signature))
    start=src.index(signature)
    brace=src.index("{",start)
    d=0
    for end in range(brace,len(src)):
        c=src[end]
        if c=="{":d+=1
        elif c=="}":
            d-=1
            if d==0:
                return src[:start]+replacement+src[end+1:]
    raise AssertionError("unbalanced "+signature)

HELPERS="""
    // Frame construction is outside the recipient loop, once per broadcast.
    // writeS uses MS949 followed by one zero byte; the framing bytes are LE.
    private static final java.nio.charset.Charset SHARED_MS949 =
            java.nio.charset.Charset.forName("MS949");

    private static byte[] sharedWire(int opcode, int type, int objectId,
            String text, boolean includeObjectId) {
        final byte[] bytes=(text==null?"":text).getBytes(SHARED_MS949);
        final long frameLength=5L+(includeObjectId?4:0)+bytes.length;
        if(frameLength>65535L)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer
                .allocate((int)frameLength)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)frameLength);
        b.put((byte)opcode);
        b.put((byte)type);
        if(includeObjectId)b.putInt(objectId);
        b.put(bytes);
        b.put((byte)0);
        return b.array();
    }

"""
MESSAGE="""public static void broadcastMessage(String chat) {
        final byte[] wire=sharedWire(Opcodes.S_MESSAGE,1,0,chat,false);
        if(wire!=null)
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                    .sendRecipients(L1World.getInstance().getAllPlayers(),wire);
    }"""
OPCODE="""public static void broadcastOpcode(String chat, int opcode) {
        final byte[] wire=sharedWire(opcode,2,0,chat,false);
        if(wire!=null)
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                    .sendRecipients(L1World.getInstance().getAllPlayers(),wire);
    }"""
COMPLEX="""public static void broadcastComplex0(L1PcInstance source, String chat, int opcode) {
        if(source==null)return;
        final String name=source.getName()==null?"":source.getName();
        final byte[] wire=sharedWire(opcode,0,source.getId(),
                name+": "+(chat==null?"":chat),true);
        if(wire==null)return;
        final L1World world=L1World.getInstance();
        final java.util.List<L1PcInstance> visible=world.borrowVisiblePlayerFast(source);
        try {
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                    .sendRecipients(visible,wire);
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }
    }"""
OLD_POISON="""\t\tfor (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
\t\t\tif (pc == null) {
\t\t\t\tcontinue;
\t\t\t}

\t\t\tpc.removeKnownObject(this);
\t\t\tSC_RemoveObject.send(
\t\t\t\t\tpc.getNetConnection(),
\t\t\t\t\tthis);
\t\t}
"""
NEW_POISON="""\t\ttry (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
\t\t\t\tSC_RemoveObject.broadcastWire(getId()),
\t\t\t\tInteger.MIN_VALUE, Integer.MAX_VALUE)) {
\t\t\tfor (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) {
\t\t\t\tif (pc == null) {
\t\t\t\t\tcontinue;
\t\t\t\t}
\t\t\t\tpc.removeKnownObject(this);
\t\t\t\tl1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared, pc);
\t\t\t}
\t\t}
"""
TEST=StringNotUsed=None
TEST_SOURCE=r"""
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

public final class Phase60ChatParity {
    public static void main(String[] args) throws Exception {
        final Method m=SC_ChatPacket.class.getDeclaredMethod("sharedWire",
                int.class,int.class,int.class,String.class,boolean.class);
        m.setAccessible(true);
        final String[] samples={"","hello","채팅테스트","[GM] 서버 공지",
            "★한글123★","😀","가나다라마바사", "A\\0B","1".repeat(1000)};
        int tested=0;
        for(String sample:samples)for(int opcode:new int[]{2,3,14,127,255})
            for(int type:new int[]{0,1,2,15})for(boolean withId:new boolean[]{false,true}) {
                final int id=0x78563412;
                final byte[] wire=(byte[])m.invoke(null,opcode,type,id,sample,withId);
                final ByteBuffer bb=ByteBuffer.allocate(65535).order(ByteOrder.LITTLE_ENDIAN);
                final WriteBuffer w=new WriteBuffer();
                w.armExternal(bb,0,65535);
                w.reset(Phase60ChatParity.class);
                w.writeC(opcode);
                w.writeC(type);
                if(withId)w.writeD(id);
                w.writeS(sample);
                final int n=w.position();
                final byte[] expected=new byte[n+2];
                expected[0]=(byte)(n+2);expected[1]=(byte)((n+2)>>>8);
                bb.position(0);bb.get(expected,2,n);
                if(!Arrays.equals(wire,expected))
                    throw new AssertionError("wire mismatch: "+sample+"/"+opcode+"/"+type+"/"+withId);
                w.detach();tested++;
            }
        System.out.println("PHASE60_CHAT_WIRE_PARITY_PASS="+tested);
    }
}
"""
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase60"))
    work=p.parse_args().work
    data=INPUT.read_bytes()
    assert blob(data)==PIN,"phase58 pinned source changed"
    with zipfile.ZipFile(INPUT) as z:
        assert z.testzip() is None
        original={x.filename:z.read(x) for x in z.infolist() if not x.is_dir()}
    assert sum(p.endswith(".java") for p in original)==1710
    files=original.copy()
    s=files[CHAT].decode("utf-8")
    assert "SHARED_MS949" not in s
    assert s.count("    private SC_ChatPacket() {}")==1
    s=s.replace("    private SC_ChatPacket() {}",HELPERS+"    private SC_ChatPacket() {}",1)
    s=change_method(s,"public static void broadcastMessage(String chat)",MESSAGE)
    s=change_method(s,"public static void broadcastOpcode(String chat, int opcode)",OPCODE)
    s=change_method(s,"public static void broadcastComplex0(L1PcInstance source, String chat, int opcode)",COMPLEX)
    files[CHAT]=s.encode("utf-8")
    s=files[POISON].decode("utf-8")
    assert s.count(OLD_POISON)==1,"poison delete loop changed"
    files[POISON]=s.replace(OLD_POISON,NEW_POISON,1).encode("utf-8")
    changed=sorted(x for x in files if files[x]!=original[x])
    assert changed==sorted((CHAT,POISON))
    assert files["CTransport.dll"]==original["CTransport.dll"]
    assert all(v==files[k] for k,v in original.items() if k not in changed)
    work.mkdir(parents=True,exist_ok=True)
    for path,body in files.items():
        if path.startswith("src/") and path.endswith(".java"):
            dest=work/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(body)
    t=work/"testsrc/Phase60ChatParity.java";t.parent.mkdir(parents=True,exist_ok=True)
    t.write_text(TEST_SOURCE,encoding="utf8")
    prior=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase57_20261116/migrate_identical_wire_fanouts.py"))
    (work/"testsrc/Phase57SharedFrameParity.java").write_text(prior["TEST"],encoding="utf8")
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path in sorted(files):z.writestr(path,files[path])
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    audit={"phase":60,"java":1710,"modified":changed,
           "chat_fanouts_shared":3,"raid_remove_shared":1,
           "receiver_order_preserved":True,"per_recipient_direct_chat_encoders_removed":3,
           "unchanged_other_bytes":True,"native_unchanged":True,
           "no_new_workers_queues_fallback":True,"windows_runtime_tested":False,
           "sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest()}
    (work/"audit.json").write_text(json.dumps(audit,indent=2,ensure_ascii=False),encoding="utf8")
    print(json.dumps(audit,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
