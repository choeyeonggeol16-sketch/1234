#!/usr/bin/env python3
"""Phase57: remove repeated per-recipient serializers from exact-same-wire
Gludio room notices and MJEffectTrigger move/action/dispose fanouts.
Do not touch personalized notices or polymorphic ServerPacket.writeImpl(client).
"""
from pathlib import Path
import argparse, hashlib, json, zipfile

ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase56_FULL_SRC_20261116.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase57_FULL_SRC_20261116.zip"
INPUT_BLOB="2f4c214acbae218d724ad2982b0432bcc90ba443"
PATH_GLUDIO="src/l1j/server/IndunSystem/Gludio/GludioInstanceDungeon.java"
PATH_TRIGGER="src/l1j/server/MJTemplate/L1Instance/MJEffectTriggerInstance.java"
PATH_MOVE="src/l1j/server/server/model/network/core/sc/packet/SC_MoveChar.java"

BC="l1j.server.server.model.network.core.broadcast"
PK="l1j.server.server.model.network.core.sc.packet"

def git_blob(data):
    return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()

def method_block(s, signature):
    start=s.find(signature)
    assert start>=0, signature
    brace=s.find("{",start)
    assert brace>=0
    depth=0
    for i in range(brace,len(s)):
        if s[i]=="{":depth+=1
        elif s[i]=="}":
            depth-=1
            if not depth: return start,i+1,s[start:i+1]
    raise AssertionError("unterminated method: "+signature)

def patch_gludio(files):
    s=files[PATH_GLUDIO].decode("utf-8")
    start,end,old=method_block(s,"private void notice(Room r,String msg)")
    assert old.count("m.pc.sendPackets(msg)") == 1
    new="""private void notice(Room r,String msg) {
        final byte[] wire = %s.SC_SystemMessage.sharedWire(msg);
        if (wire == null) return;
        try (var shared = new %s.CAsyncSendConstBuffer(
                wire, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (Member m : r.members) {
                if (m.pc != null)
                    %s.CSharedBroadcast.sendRecipient(shared, m.pc);
            }
        }
    }""" %(PK,BC,BC)
    files[PATH_GLUDIO]=(s[:start]+new+s[end:]).encode("utf-8")
    return old,new

def patch_move(files):
    s=files[PATH_MOVE].decode("utf-8")
    old="""        byte[] wire = java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_MOVE_OBJECT).putInt(objectId)
                .putShort((short) x).putShort((short) y).put((byte) heading).put((byte) 129).putInt(0).array();"""
    assert s.count(old)==1,"move serialization site changed"
    s=s.replace(old,"        byte[] wire = broadcastWire(objectId, x, y, heading);",1)
    anchor="    public static void broadcast(L1Character cha, int x, int y, int heading) {"
    assert s.count(anchor)==1
    code="""    /** Exact immutable 17-byte move frame, shared by any recipient set. */
    public static byte[] broadcastWire(int objectId, int x, int y, int heading) {
        return java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_MOVE_OBJECT).putInt(objectId)
                .putShort((short) x).putShort((short) y).put((byte) heading).put((byte) 129).putInt(0).array();
    }

"""
    s=s.replace(anchor,code+anchor,1)
    assert s.count("public static byte[] broadcastWire(")==1
    files[PATH_MOVE]=s.encode("utf-8")

def patch_trigger(files):
    s=files[PATH_TRIGGER].decode("utf-8")
    a,b,old=method_block(s,"public void do_move(int x, int y, int h)")
    assert "setX(x)" in old and "setY(y)" in old
    assert old.count("SC_MoveChar.send(")==1
    new="""public void do_move(int x, int y, int h){
        setX(x);
        setY(y);
        final byte[] wire = SC_MoveChar.broadcastWire(getId(), x, y, h);
        %s.CSharedBroadcast.sendRecipients(
                L1World.getInstance().getRecognizePlayer(this), wire);
    }"""%BC
    s=s[:a]+new+s[b:]
    a,b,old=method_block(s,"public void do_action(int action)")
    assert old.count("SC_DoActionGFX.send(")==1
    new="""public void do_action(int action){
        final byte[] wire = SC_DoActionGFX.broadcastWire(getId(), action);
        %s.CSharedBroadcast.sendRecipients(
                L1World.getInstance().getRecognizePlayer(this), wire);
    }"""%BC
    s=s[:a]+new+s[b:]
    a,b,old=method_block(s,"public void dispose()")
    assert old.count("SC_RemoveObject.send(")==1
    assert old.count("pc.removeKnownObject(this)")==1
    assert old.index("pc.removeKnownObject(this)") < old.index("SC_RemoveObject.send(")
    assert old.count("L1World.getInstance().removeVisibleObject(this)")==1
    assert old.count("L1World.getInstance().removeObject(this)")==1
    new="""public void dispose(){
        L1World.getInstance().removeVisibleObject(this);
        L1World.getInstance().removeObject(this);
        try (var shared = new %s.CAsyncSendConstBuffer(
                SC_RemoveObject.broadcastWire(getId()),
                Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
                if (pc == null) continue;
                pc.removeKnownObject(this);
                %s.CSharedBroadcast.sendRecipient(shared, pc);
            }
        }
    }"""%(BC,BC)
    s=s[:a]+new+s[b:]
    # Polymorphic ServerPacket.writeImpl(client) is deliberately not frozen.
    assert "public void broadcast(ServerPacket pck, boolean is_clear)" in s
    assert "pc.sendPackets(pck, false)" in s
    files[PATH_TRIGGER]=s.encode("utf-8")

TEST=String=None
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase57SharedFrameParity {
    static int cases=0;
    static void eq(byte[] exp,byte[] got) {
        if(!Arrays.equals(exp,got))
            throw new AssertionError("Mismatch case="+cases+" len="+got.length);
        cases++;
    }
    static byte[] move(int id,int x,int y,int heading){
        return ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((short)17).put((byte)Opcodes.S_MOVE_OBJECT).putInt(id)
            .putShort((short)x).putShort((short)y).put((byte)heading)
            .put((byte)129).putInt(0).array();
    }
    static byte[] action(int id,int code){
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((short)8).put((byte)Opcodes.S_ACTION)
            .putInt(id).put((byte)code).array();
    }
    static byte[] remove(int id){
        return ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((short)9).put((byte)Opcodes.S_REMOVE_OBJECT)
            .putInt(id).putShort((short)0).array();
    }
    static byte[] system(String s) {
        byte[] text=(s==null?"":s).getBytes(Charset.forName("MS949"));
        return ByteBuffer.allocate(5+text.length).order(ByteOrder.LITTLE_ENDIAN)
            .putShort((short)(5+text.length)).put((byte)Opcodes.S_MESSAGE)
            .put((byte)9).put(text).put((byte)0).array();
    }
    public static void main(String[] args){
        eq(system("글루디오 연구실에 입장했습니다."),
            SC_SystemMessage.sharedWire("글루디오 연구실에 입장했습니다."));
        eq(system("던전 처리 오류로 종료되었습니다."),
            SC_SystemMessage.sharedWire("던전 처리 오류로 종료되었습니다."));
        eq(system(null),SC_SystemMessage.sharedWire(null));
        int seed=0x4a80f123;
        for(int i=0;i<8192;i++){
            seed ^= seed<<13;seed ^= seed>>>17;seed ^= seed<<5;
            int id=seed;
            int x=seed>>>5;
            int y=seed>>>9;
            int h=i&7;
            eq(move(id,x,y,h),SC_MoveChar.broadcastWire(id,x,y,h));
            eq(action(id,h),SC_DoActionGFX.broadcastWire(id,h));
            eq(remove(id),SC_RemoveObject.broadcastWire(id));
        }
        System.out.println("PHASE57_SHARED_PARITY_OK "+cases);
    }
}'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase57"))
    args=p.parse_args()
    archive=INPUT.read_bytes()
    assert git_blob(archive)==INPUT_BLOB,"phase56 base pin mismatch"
    with zipfile.ZipFile(INPUT) as z:
        assert z.testzip() is None
        original={info.filename:z.read(info) for info in z.infolist() if not info.is_dir()}
    assert sum(k.endswith(".java") for k in original)==1710
    updated=original.copy()
    patch_gludio(updated)
    patch_move(updated)
    patch_trigger(updated)
    changed=sorted(k for k in updated if updated[k]!=original[k])
    assert changed==sorted([PATH_GLUDIO,PATH_MOVE,PATH_TRIGGER]),changed
    assert updated["CTransport.dll"]==original["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for k,v in updated.items():
        if k.startswith("src/") and k.endswith(".java"):
            f=args.work/k;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(v)
    t=args.work/"testsrc/Phase57SharedFrameParity.java"
    t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST,encoding="utf-8")
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for k in sorted(updated):z.writestr(k,updated[k])
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None
        assert set(z.namelist())==set(updated)
    report={
        "phase":57,"input_git_blob":INPUT_BLOB,
        "source_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
        "java_sources":1710,"modified_java":changed,
        "gludio_room_notice_same_frame":True,
        "effect_trigger_move_action_shared":True,
        "effect_trigger_dispose_remove_known_before_send":True,
        "polymorphic_client_specific_packet_intact":True,
        "native_unchanged":True,
        "new_fallbacks_threads_queues":0,
        "windows_runtime_tested":False
    }
    (args.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__": main()
