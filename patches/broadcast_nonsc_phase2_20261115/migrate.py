#!/usr/bin/env python3
"""Non-SC broadcast phase2: remove repeated encoding in event and lift-gate fanout.

Authoritative input: phase1 compiled full SRC. Exact SHA and byte-identical
unchanged files required. Event rewards, individual GM HTML, gate object state,
recipient list and per-recipient packet ORDER stay unchanged. No packet-level
fallbacks, worker, queue, or native transport changes.
"""
from __future__ import annotations
import argparse,hashlib,json,pathlib,re,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
SOURCE="downloads/RKern_Broadcast_FullAudit_Central2_FULL_SRC_20261115.zip"
SOURCE_SHA="32ca38db2dbe46f0fb58dd2d064c15c124b7069acc8d19e008a5b312704a652b"
OUTPUT="downloads/RKern_Broadcast_NonSC_Phase2_FULL_SRC_20261115.zip"
ROOT_JAVA="src/l1j/server/server/"
CHANGES={
    "event":ROOT_JAVA+"SpecialEventHandler.java",
    "gate":ROOT_JAVA+"MJTemplate/L1Instance/MJL1LiftGateInstance.java",
    "message":ROOT_JAVA+"model/network/core/sc/packet/SC_SystemMessage.java",
    "action":ROOT_JAVA+"model/network/core/sc/packet/SC_DoActionGFX.java",
    "remove":ROOT_JAVA+"model/network/core/sc/packet/SC_RemoveObject.java",
    "attr":ROOT_JAVA+"model/network/core/sc/packet/SC_ChangeAttr.java",
}
SHA=lambda s:hashlib.sha256(s).hexdigest()

def insert_before_closing_class(s,helper,label):
    i=s.rfind('}')
    if i<0:raise ValueError("No Java closing class "+label)
    return s[:i]+"\n"+helper+"\n"+s[i:]

def method_bounds(s,signature):
    loc=s.find(signature)
    if loc<0 or s.count(signature)!=1:
        raise ValueError("Expected exactly one method declaration "+signature)
    brace=s.find('{',loc+len(signature))
    if brace<0:raise ValueError("missing method body "+signature)
    depth=0;pos=brace
    while pos<len(s):
        if s[pos]=='{':depth+=1
        if s[pos]=='}':
            depth-=1
            if depth==0:return loc,pos+1,s[loc:pos+1]
        pos+=1
    raise ValueError("method not balanced "+signature)

def event_source(s):
    # Single scoped immutable frame created outside each individualized reward
    # iteration. Preserve grant->personal HTML->notice ordering per player.
    specs=[
        ("doGiveEventStaff","\\\\aH메티스님께서 코마의 축복 코인이 지급 하였습니다."),
        ("doGiveEventStaff1","\\\\aH메티스님께서 드래곤의 토파즈가 지급 하였습니다."),
        ("doGiveEventStaff2","\\\\aG메티스님께서 소환주문서 1장이 지급 하였습니다."),
    ]
    for name,text in specs:
        a,b,old=method_bounds(s,"public void "+name+"()")
        marker="pc.sendPackets("
        assert old.count(marker)==1,(name,old.count(marker))
        match=re.search(r'\bpc\.sendPackets\(\s*("(?:\\.|[^"\\])*")\s*\)\s*;',old)
        if match is None:raise ValueError("event notification not found "+name)
        original=match.group(1)
        # Compare Java string literal verbatim against the expected message.
        decoded=original[1:-1]
        if decoded!=text:raise ValueError("Event message changed "+name+" "+repr(decoded))
        replacement='''final l1j.server.server.model.network.core.session.CIOClient connection=pc.getNetConnection();
                if(connection!=null && connection.isConnected()) {
                    broadcast.send(connection.getConnection(),pc.getId());
                }'''
        rewritten=old[:match.start()]+replacement+old[match.end():]
        if rewritten.count("for (L1PcInstance pc : L1World.getInstance().getAllPlayers())")!=1:
            raise ValueError("Event membership loop changed "+name)
        if rewritten.find("SC_GMHtml.send(")>rewritten.find("broadcast.send("):
            raise ValueError("GMHTML must occur before broadcast to the same recipient")
        begin=rewritten.index("{")+1
        # Place outer try around the existing loop and its individual grants.
        body=rewritten[begin:-1]
        wire='SC_SystemMessage.sharedWire('+original+')'
        rewritten=(rewritten[:begin]+'''
        final byte[] noticeWire='''+wire+''';
        try (var broadcast=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                noticeWire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
'''+body+'''
        }
    }''')
        s=s[:a]+rewritten+s[b:]
    for _,txt in specs:
        assert s.count('SC_SystemMessage.sharedWire("'+txt+'")')==1
    return s

PUBLIC_MESSAGE_OLD="private static byte[] sharedWire(String message)"
PUBLIC_MESSAGE_NEW="public static byte[] sharedWire(String message)"
def message_source(s):
    if s.count(PUBLIC_MESSAGE_OLD)!=1:raise ValueError("SC_SystemMessage source changed")
    return s.replace(PUBLIC_MESSAGE_OLD,PUBLIC_MESSAGE_NEW,1)

ACTION_HELPER='''
    /** Immutable action frame usable across several recipients. */
    public static byte[] broadcastWire(int objectId,int actionId) {
        return java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short)8)
                .put((byte)l1j.server.server.Opcodes.S_ACTION)
                .putInt(objectId).put((byte)actionId).array();
    }
'''
def action_source(s):
    if "public static byte[] broadcastWire(" in s:raise ValueError("action helper already exists")
    assert "connection.sendAction(objectId, actionId)" in s
    return insert_before_closing_class(s,ACTION_HELPER,"SC_DoActionGFX")

REMOVE_HELPER='''
    /** Immutable remove-object frame, including the original 2-byte trailer. */
    public static byte[] broadcastWire(int objectId) {
        return java.nio.ByteBuffer.allocate(WIRE_SIZE)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short)WIRE_SIZE)
                .put((byte)Opcodes.S_REMOVE_OBJECT)
                .putInt(objectId).putShort((short)0).array();
    }
'''
def remove_source(s):
    if "public static byte[] broadcastWire(" in s:raise ValueError("remove helper already exists")
    assert "buffer.writeC(Opcodes.S_REMOVE_OBJECT)" in s
    return insert_before_closing_class(s,REMOVE_HELPER,"SC_RemoveObject")

ATTR_HELPER='''
    /** Same nine wire bytes as the individual direct SC_ChangeAttr sender. */
    public static byte[] broadcastWire(int x,int y,boolean width,boolean close) {
        return java.nio.ByteBuffer.allocate(9)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short)9)
                .put((byte)Opcodes.S_CHANGE_ATTR)
                .putShort((short)x).putShort((short)y)
                .put((byte)(width?1:0))
                .put((byte)(close?1:0)).array();
    }
'''
def attr_source(s):
    if "public static byte[] broadcastWire(" in s:raise ValueError("attr helper already exists")
    assert "buffer.writeC(Opcodes.S_CHANGE_ATTR)" in s
    return insert_before_closing_class(s,ATTR_HELPER,"SC_ChangeAttr")

GATE_HELPERS='''
    /** One const buffer per unchanged tile attribute packet per gate event. */
    private l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer[]
            createSharedAttrs(boolean closed) {
        final int count=_attrCoordinates.length>>>1;
        final var frames=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer[count];
        int made=0;
        try {
            for(int i=0;i<count;i++) {
                final int idx=i<<1;
                final byte[] wire=SC_ChangeAttr.broadcastWire(_attrCoordinates[idx],
                        _attrCoordinates[idx+1],_isWidth,closed);
                frames[made++]=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                        wire,Integer.MIN_VALUE,Integer.MAX_VALUE);
            }
            return frames;
        } catch (RuntimeException | Error failure) {
            closeSharedAttrs(frames);
            throw failure;
        }
    }

    private static void closeSharedAttrs(
            l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer[] frames) {
        if(frames==null)return;
        for(var frame:frames)if(frame!=null)frame.close();
    }

    private static void sendSharedAttrs(
            l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer[] attrs,
            l1j.server.server.model.network.core.session.CIOClient client,int playerId) {
        if(client==null || !client.isConnected())return;
        final var socket=client.getConnection();
        if(socket==null)return;
        for(var frame:attrs)frame.send(socket,playerId);
    }
'''

GATE_DISPOSE='''public void notifyDispose() {
        final java.util.List<L1PcInstance> viewers=L1World.getInstance().getVisiblePlayer(this);
        if(viewers.isEmpty())return;
        final int objectId=getId(),action=_act;
        final var attrFrames=createSharedAttrs(isClose());
        try {
            try (var actionFrame=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                         SC_DoActionGFX.broadcastWire(objectId,action),Integer.MIN_VALUE,Integer.MAX_VALUE);
                 var removeFrame=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                         SC_RemoveObject.broadcastWire(objectId),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
                for(L1PcInstance pc:viewers) {
                    if(pc==null)continue;
                    pc.removeKnownObject(this);
                    final var client=pc.getNetConnection();
                    // Preserve original order: known-object removal, tile attributes,
                    // action and finally object removal for each specific player.
                    sendSharedAttrs(attrFrames,client,pc.getId());
                    if(client!=null && client.isConnected()) {
                        final var socket=client.getConnection();
                        if(socket!=null) {
                            actionFrame.send(socket,pc.getId());
                            removeFrame.send(socket,pc.getId());
                        }
                    }
                }
            }
        } finally { closeSharedAttrs(attrFrames); }
    }'''

GATE_CHANGED='''public void notifyChanged() {
        final java.util.List<L1PcInstance> viewers=L1World.getInstance().getVisiblePlayer(this);
        if(viewers.isEmpty())return;
        final int objectId=getId(),action=_act;
        final var attrFrames=createSharedAttrs(isClose());
        try {
            try (var actionFrame=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                         SC_DoActionGFX.broadcastWire(objectId,action),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
                for(L1PcInstance pc:viewers) {
                    if(pc==null)continue;
                    final var client=pc.getNetConnection();
                    // Preserve original order: action first, then tile attributes.
                    if(client!=null && client.isConnected()) {
                        final var socket=client.getConnection();
                        if(socket!=null)actionFrame.send(socket,pc.getId());
                    }
                    sendSharedAttrs(attrFrames,client,pc.getId());
                }
            }
        } finally { closeSharedAttrs(attrFrames); }
    }'''

def gate_source(s):
    # onPerceive() remains a unicast operation: only notify* broadcasts change.
    original_sendattrs=method_bounds(s,"private void sendAttrs(L1PcInstance pc)")[2]
    for method,new in [("notifyDispose",GATE_DISPOSE),("notifyChanged",GATE_CHANGED)]:
        a,b,old=method_bounds(s,"public void "+method+"()")
        assert "L1World.getInstance().getVisiblePlayer(this)" in old
        if method=="notifyDispose":
            assert "removeKnownObject(this)" in old and "SC_RemoveObject.send(" in old
        else:assert "SC_DoActionGFX.send(" in old
        s=s[:a]+new+s[b:]
    if method_bounds(s,"private void sendAttrs(L1PcInstance pc)")[2]!=original_sendattrs:
        raise ValueError("Gate unicast perception changed")
    return insert_before_closing_class(s,GATE_HELPERS,"gate")

# Independent per-field wire references; test all byte fields and event strings.
JAVA_TEST=r'''import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class BroadcastPhase2WireParity {
    private static int checked;
    private static ByteBuffer buffer(int length) {
        final ByteBuffer b=ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)length);
        return b;
    }
    private static void compare(byte[] actual,ByteBuffer reference,String name,int index) {
        if(!Arrays.equals(actual,reference.array()))
            throw new AssertionError(name+" case "+index+" wanted "+
                    Arrays.toString(reference.array())+" got "+Arrays.toString(actual));
        checked++;
    }
    public static void main(String[] args) {
        for(int k=0;k<512;k++) {
            final int objectId=(int)(0xabcdef12L^((k+1L)*0x9e3779b9L));
            final int act=k*31-85;
            final int x=k*113-470,y=k*29-300,flags=k*17;
            final boolean width=(k&1)!=0,closed=(k&2)!=0;
            ByteBuffer a=buffer(8);
            a.put((byte)Opcodes.S_ACTION).putInt(objectId).put((byte)act);
            compare(SC_DoActionGFX.broadcastWire(objectId,act),a,"action",k);
            ByteBuffer r=buffer(9);
            r.put((byte)Opcodes.S_REMOVE_OBJECT).putInt(objectId).putShort((short)0);
            compare(SC_RemoveObject.broadcastWire(objectId),r,"remove",k);
            ByteBuffer t=buffer(9);
            t.put((byte)Opcodes.S_CHANGE_ATTR).putShort((short)x).putShort((short)y)
             .put((byte)(width?1:0)).put((byte)(closed?1:0));
            compare(SC_ChangeAttr.broadcastWire(x,y,width,closed),t,"tileattr",k);
        }
        String[] messages={
            "\\aH메티스님께서 코마의 축복 코인이 지급 하였습니다.",
            "\\aH메티스님께서 드래곤의 토파즈가 지급 하였습니다.",
            "\\aG메티스님께서 소환주문서 1장이 지급 하였습니다.",
            "","ASCII","한글","漢字","😀","a\0b"
        };
        for(int k=0;k<messages.length;k++){
            final byte[] ms949=messages[k].getBytes(Charset.forName("MS949"));
            ByteBuffer m=buffer(ms949.length+5);
            m.put((byte)Opcodes.S_MESSAGE).put((byte)0x09).put(ms949).put((byte)0);
            compare(SC_SystemMessage.sharedWire(messages[k]),m,"notice",k);
        }
        System.out.println("BROADCAST_NONSC_PHASE2_WIRE_PARITY_OK cases="+checked);
    }
}
'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--input",type=pathlib.Path,default=ROOT/SOURCE)
    p.add_argument("--output",type=pathlib.Path,default=ROOT/OUTPUT)
    p.add_argument("--work",type=pathlib.Path,default=pathlib.Path("/tmp/broadcast-nonsc-phase2"))
    a=p.parse_args()
    if SHA(a.input.read_bytes())!=SOURCE_SHA:
        raise ValueError("Input source ZIP SHA256 mismatched. New source must be rebased explicitly.")
    with zipfile.ZipFile(a.input) as z:
        assert z.testzip() is None
        contents={f.filename:z.read(f) for f in z.infolist() if not f.is_dir()}
    original=dict(contents)
    transforms={"event":event_source,"gate":gate_source,"message":message_source,
                "action":action_source,"remove":remove_source,"attr":attr_source}
    for name,fn in transforms.items():
        path=CHANGES[name]
        before=contents[path].decode("utf8")
        after=fn(before)
        if after==before:raise ValueError("Expected modification "+path)
        contents[path]=after.encode("utf8")
    changed=[p for p in contents if contents[p]!=original[p]]
    assert set(changed)==set(CHANGES.values()),changed
    assert all(contents[p]==original[p] for p in contents if p not in CHANGES.values())
    assert contents['CTransport.dll']==original['CTransport.dll']
    assert sum(k.endswith(".java") for k in contents)==1711
    gate=contents[CHANGES["gate"]].decode()
    assert "SC_DoActionGFX.send(" not in method_bounds(gate,"public void notifyChanged()")[2]
    assert "SC_RemoveObject.send(" not in method_bounds(gate,"public void notifyDispose()")[2]
    assert "sendAttrs(pc)" not in method_bounds(gate,"public void notifyChanged()")[2]
    for name in ("doGiveEventStaff","doGiveEventStaff1","doGiveEventStaff2"):
        assert "pc.sendPackets(" not in method_bounds(contents[CHANGES['event']].decode(),"public void "+name+"()")[2]
    a.work.mkdir(parents=True,exist_ok=True)
    for path,blob in contents.items():
        if path.startswith('src/'):
            dst=a.work/path
            dst.parent.mkdir(parents=True,exist_ok=True)
            dst.write_bytes(blob)
    test=a.work/"testsrc/BroadcastPhase2WireParity.java"
    test.parent.mkdir(parents=True,exist_ok=True)
    test.write_text(JAVA_TEST)
    # Retain the full source audit and earlier protocol cleanup in the single ZIP.
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,blob in sorted(contents.items()):z.writestr(path,blob)
    with zipfile.ZipFile(a.output) as z:
        assert z.testzip() is None
    report={
        "input_sha256":SOURCE_SHA,
        "output_sha256":SHA(a.output.read_bytes()),
        "changed_java":sorted(changed),
        "changed_java_count":len(changed),
        "completed_event_broadcasts":3,
        "completed_gate_broadcasts":2,
        "new_fallback_branches":0,
        "new_threads_or_queues":0,
        "code_files_java":1711,
        "native_dll_unchanged":True,
        "retained_audit_candidate_loops":322,
        "full_windows_runtime_tested":False
    }
    (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
