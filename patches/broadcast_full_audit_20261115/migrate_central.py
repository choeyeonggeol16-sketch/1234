#!/usr/bin/env python3
"""First safe non-SC legacy broadcast removal based on full-source AST audit.

Replaces exactly two central repeated-send paths:
  L1World.broadcast_map(mapId,String) -> one shared MS949 frame
  L1Character.broadcastPacket(byte[]) -> one framed raw protobuf packet
Does NOT coalesce ServerPacket.writeImpl(CIOClient,WriteBuffer), as it can
encode recipient-dependent contents. No native, world state, or packet ID edits.
"""
import argparse,hashlib,json,pathlib,re,zipfile,sys

ROOT=pathlib.Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/CTransport_SharedBroadcast_NoLegacy_FULL_SRC_20261115.zip"
EXPECTED="006f9c53143461f0bff5b5d0e6edd588644ef09efe4ca7b791a4724426d18efb"
OUTPUT=ROOT/"downloads/RKern_Broadcast_FullAudit_Central2_FULL_SRC_20261115.zip"
SRC="src/l1j/server/server/model/"
KEYS={
"world":SRC+"L1World.java",
"char":SRC+"L1Character.java",
"system":SRC+"network/core/sc/packet/SC_SystemMessage.java",
"shared":SRC+"network/core/broadcast/CSharedBroadcast.java",
}
SHA=lambda raw:hashlib.sha256(raw).hexdigest()

OLD_WORLD="""    public void broadcast_map(int map_id, String message){
        for (L1PcInstance pc : getVisiblePlayers(map_id)) {
            if (pc != null) SC_SystemMessage.send(pc, message);
        }
    }"""
NEW_WORLD="""    public void broadcast_map(int map_id, String message){
        SC_SystemMessage.broadcastRecipients(getVisiblePlayers(map_id), message);
    }"""

OLD_CHARACTER="""\tpublic void broadcastPacket(byte[] data) {
\t    for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(this)) {
\t        l1j.server.server.packets.Protocol.MainServer.Proto.Proto.sendRaw(pc, data);
\t    }
\t}"""
NEW_CHARACTER="""\tpublic void broadcastPacket(byte[] data) {
\t    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRawRecipients(
\t            L1World.getInstance().getVisiblePlayer(this), data);
\t}"""

NEW_SHARED=r'''
    /**
     * Broadcast an already framed immutable packet to a specified recipient
     * collection. Retains its exact inclusion/ordering semantics (no AOI or
     * self-exclusion override). The packet-specific serializer never runs in
     * the recipient loop.
     */
    public static void sendRecipients(Iterable<? extends L1PcInstance> recipients,
            byte[] wire) {
        if (recipients == null || wire == null) return;
        try (var shared=new CAsyncSendConstBuffer(wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for (L1PcInstance pc:recipients) {
                if (pc == null) continue;
                final CIOClient client=pc.getNetConnection();
                if (client != null && client.isConnected())
                    shared.send(client.getConnection(),pc.getId());
            }
        }
    }

    /** Proto.sendRaw uses an unframed already-encoded payload. Convert once
     * to its 2-byte little-endian wire length, then broadcast shared bytes.
     */
    private static byte[] frameRaw(byte[] encoded) {
        if(encoded==null || encoded.length==0 || encoded.length>65533)return null;
        final int size=encoded.length+2;
        final byte[] frame=new byte[size];
        frame[0]=(byte)size;
        frame[1]=(byte)(size>>>8);
        System.arraycopy(encoded,0,frame,2,encoded.length);
        return frame;
    }

    public static void sendRawRecipients(Iterable<? extends L1PcInstance> recipients,
            byte[] encoded) {
        final byte[] framed=frameRaw(encoded);
        if(framed!=null)sendRecipients(recipients,framed);
    }
'''
NEW_SC_SYSTEM=r'''
    /** Map/party recipients: serialize one frame and send to exactly the
     * specified players, with no per-client legacy serialization.
     */
    public static void broadcastRecipients(
            Iterable<? extends L1PcInstance> recipients,String message) {
        final byte[] wire=sharedWire(message);
        if(wire!=null)
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                    .sendRecipients(recipients,wire);
    }

'''

REGRESSION=r'''import java.lang.reflect.*;
import java.nio.charset.*;
import java.util.*;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
public final class BroadcastCentral2WireRegression {
    static int testCount;
    static void check(boolean b,String description){
        if(!b)throw new AssertionError(description);
        testCount++;
    }
    static byte[] frameRaw(byte[] raw)throws Exception{
        Method m=CSharedBroadcast.class.getDeclaredMethod("frameRaw",byte[].class);
        m.setAccessible(true);
        return (byte[])m.invoke(null,(Object)raw);
    }
    static byte[] system(String s)throws Exception{
        Class<?> clazz=Class.forName("l1j.server.server.model.network.core.sc.packet.SC_SystemMessage");
        Method m=clazz.getDeclaredMethod("sharedWire",String.class);
        m.setAccessible(true);
        return (byte[])m.invoke(null,s);
    }
    public static void main(String[] args)throws Exception {
        check(frameRaw(null)==null,"null");
        check(frameRaw(new byte[0])==null,"empty");
        check(frameRaw(new byte[65534])==null,"overflow");
        for(int size:new int[]{1,2,3,7,16,127,128,2048,16382,16383,16384,20000,65533}){
            byte[] original=new byte[size];
            for(int i=0;i<size;i++)original[i]=(byte)(i*37+13);
            byte[] frame=frameRaw(original);
            check(frame!=null && frame.length==size+2,"frame length "+size);
            check((frame[0]&255)+((frame[1]&255)<<8)==size+2,"little-endian size "+size);
            check(Arrays.equals(Arrays.copyOfRange(frame,2,frame.length),original),"payload "+size);
            original[0]^=0x7f;
            check(frame[2]!=(byte)original[0],"frame not aliased "+size);
        }
        String[] texts={"","a","한글","테스트 메시지","漢字","😀","abc\\0xyz"};
        for(String s:texts){
            byte[] bytes=s.getBytes(Charset.forName("MS949"));
            byte[] wire=system(s);
            check(wire.length==5+bytes.length,"system size "+s);
            check((wire[0]&255)+((wire[1]&255)<<8)==wire.length,"system header");
            check(wire[2]==(byte)Opcodes.S_MESSAGE && wire[3]==9,"system opcode");
            check(Arrays.equals(Arrays.copyOfRange(wire,4,wire.length-1),bytes),"MS949 text "+s);
            check(wire[wire.length-1]==0,"text terminator "+s);
        }
        check(system(null).length==5,"null message");
        System.out.println("BROADCAST_CENTRAL2_REGRESSION_OK cases="+testCount);
    }
}
'''

def replace_one(source,old,new,label):
    if source.count(old)!=1:
        raise ValueError(f"Not a single authoritative {label} source match: {source.count(old)}")
    return source.replace(old,new,1)

def patch(files):
    src={k:files[v].decode('utf8') for k,v in KEYS.items()}
    if src['world'].count(OLD_WORLD)!=1:
        print("WORLD SNIPPET:")
        p=src['world'].find("public void broadcast_map(int map_id, String message)")
        print(repr(src['world'][p-4:p+210]))
        raise ValueError("authoritative L1World method mismatch")
    files[KEYS['world']]=replace_one(src['world'],OLD_WORLD,NEW_WORLD,"L1World.broadcast_map(String)").encode()
    if src['char'].count(OLD_CHARACTER)!=1:
        print("CHAR SNIPPET:")
        p=src['char'].find("public void broadcastPacket(byte[] data)")
        print(repr(src['char'][p-4:p+230]))
        raise ValueError("authoritative L1Character byte broadcast mismatch")
    files[KEYS['char']]=replace_one(src['char'],OLD_CHARACTER,NEW_CHARACTER,"L1Character.broadcastPacket(byte[])").encode()
    anchor="    /** Global broadcast includes the sender and uses the normal socket FIFO. */"
    if src['shared'].count(anchor)!=1:raise ValueError("shared source insertion anchor mismatch")
    files[KEYS['shared']]=src['shared'].replace(anchor,NEW_SHARED+"\n"+anchor,1).encode()
    anchor="    private static void writeContent(WriteBuffer buffer, String msg, boolean nameId) {"
    if src['system'].count(anchor)!=1:raise ValueError("system message encoder source mismatch")
    files[KEYS['system']]=src['system'].replace(anchor,NEW_SC_SYSTEM+anchor,1).encode()
    assert sum(p.endswith('.java') for p in files)==1711
    return files

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--input",type=pathlib.Path,default=INPUT)
    p.add_argument("--output",type=pathlib.Path,default=OUTPUT)
    p.add_argument("--work",type=pathlib.Path,default=pathlib.Path("/tmp/broadcast-central2"))
    a=p.parse_args()
    if SHA(a.input.read_bytes())!=EXPECTED:
        raise ValueError("Latest full SRC not matched by sha256")
    with zipfile.ZipFile(a.input) as z:
        assert z.testzip() is None
        files={f.filename:z.read(f) for f in z.infolist() if not f.is_dir()}
    original=dict(files)
    patch(files)
    diff=sorted(k for k in files if files[k]!=original[k])
    if set(diff)!=set(KEYS.values()):
        raise AssertionError("unexpected code mutations "+str(diff))
    if files['CTransport.dll']!=original['CTransport.dll']:
        raise AssertionError("Transport DLL changed")
    assert all(files[k]==original[k] for k in files if k not in set(KEYS.values()))
    assert "Proto.sendRaw(pc, data)" not in files[KEYS["char"]].decode()
    assert "SC_SystemMessage.send(pc, message)" not in files[KEYS["world"]].decode()
    # Persist the full audit with the tested complete source as one deliverable.
    reports=[
      ("reports/broadcast_full_audit_20261115/AUDIT_KO.md","docs/broadcast_full_audit_20261115/AUDIT_KO.md"),
      ("reports/broadcast_full_audit_20261115/audit.json","docs/broadcast_full_audit_20261115/audit.json")]
    for source,target in reports:
        files[target]=(ROOT/source).read_bytes()
    docs=("Review of all 1711 Java files; 322 non-SC candidate broadcast loops in 108 files.\n"
          "This patch eliminates two confirmed recipient-invariant central broadcaster loops.\n"
          "The generic ServerPacket.writeImpl(CIOClient,WriteBuffer) path is NOT rewritten:\n"
          "it may depend on recipient state and needs class-by-class analysis.\n"
          "Detected AST parse-error files: 4; static inventory is not proof of complete removal.\n"
          "Original no-legacy 55-class SC broadcast implementation preserved.\n"
          "Windows IOCP/game runtime remains untested.\n")
    files["docs/broadcast_full_audit_20261115/STATUS.txt"]=docs.encode('utf8')
    a.work.mkdir(parents=True,exist_ok=True)
    for path,blob in files.items():
        if path.startswith("src/"):
            dst=a.work/path
            dst.parent.mkdir(parents=True,exist_ok=True)
            dst.write_bytes(blob)
    test=a.work/"testsrc/BroadcastCentral2WireRegression.java"
    test.parent.mkdir(parents=True,exist_ok=True)
    test.write_text(REGRESSION)
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,blob in sorted(files.items()):z.writestr(path,blob)
    with zipfile.ZipFile(a.output) as z:
        assert z.testzip() is None
    report=dict(input_sha256=EXPECTED,output_sha256=SHA(a.output.read_bytes()),java_count=1711,
                changed_files=diff,candidate_loops=322,candidate_files=108,
                changed_legacy_broadcast_loops=2,legacy_fallbacks_added=0,
                wire_format_changed=False,network_send_api_behavior_changed=False,
                original_native_dll_unchanged=True,
                windows_runtime_tested=False)
    (a.work/"build.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
