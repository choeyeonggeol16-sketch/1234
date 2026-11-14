#!/usr/bin/env python3
"""Single-capture WorldPutObject AOI broadcast.

The three full existing send methods remain untouched. We extract their exact
primitive snapshots and WriteBuffer serialization statements and run them once
against WriteBuffer backed by a temporary heap ByteBuffer for a shared frozen
frame. Error/oversize falls back to the original per-recipient paths.
"""
from __future__ import annotations
import pathlib, re, sys
ROOT=pathlib.Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
from patches.ctransport_broadcast_expand4_20261115 import expand4 as prev

M=prev.method
R=prev.replace
BROADCAST="l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send"

HELPERS=r'''
    /** Unsupported sockets still receive the exact same immutable snapshot
     * through their original reserved FIFO direct frame, without resampling
     * mutable NPC/PC/Builder fields per recipient.
     */
    private static boolean sendFrozen(CIOClient client, byte[] wire) {
        if(client==null || wire==null || !client.isConnected())return false;
        final CUserSocket socket=client.getConnection();
        if(socket==null || !socket.isClientConnected())return false;
        final CIOFrame frame=socket.beginDirectFrame(wire.length);
        if(frame==null)return false;
        boolean committed=false;
        try {
            final WriteBuffer writer=frame.buffer();
            final java.nio.ByteBuffer dst=writer.getBuffer();
            dst.position(writer.baseOffset()+writer.position());
            dst.put(wire,2,wire.length-2);
            writer.position(wire.length);
            committed=socket.commitDirectFrame(frame);
            return committed;
        } catch(Throwable ignored) {
            return false;
        } finally {
            if(!committed)socket.abortDirectFrame(frame);
        }
    }
'''

def method_detail(s,method_name,match=None):
    return M(s,"public",method_name,match)

def build_factory(source,name,which,params):
    """Return generated private method with IDENTICAL snapshot and payload."""
    old=method_detail(source,name,which)
    body=old[3]
    if name=="sendBuilder":
        start=body.index("final int x = s.x;")
    else:
        start=body.index("/* Capture once.")
    end=body.index("final CIOFrame frame",start)
    snippet=body[start:end]
    if snippet.count("final int reserve")!=1:
        raise AssertionError(f"Expected exactly one reserve in {name}")
    if "return true" in snippet:raise ValueError("Unsupported early return "+name)
    snippet=re.sub(r'\breturn false\s*;', 'return null;', snippet)
    if "return false;" in snippet:raise ValueError("Stale false return "+name)
    first=body.index("writeHeader(b);",end)
    final=body.index("writeTrailer(b);",first)+len("writeTrailer(b);")
    payload=body[first:final]
    if "connection" in payload or "client" in payload or "frame" in payload:
        raise ValueError("Recipient referenced inside "+name+" payload")
    if payload.count("writeHeader(b);")!=1 or payload.count("writeTrailer(b);")!=1:
        raise ValueError("Unexpected multiple payload blocks")
    source_guard="s==null" if name=="sendBuilder" else "npc==null" if name=="sendNpc" else "pc==null"
    result=f'''
    private static byte[] {which}({params}) {{
        if({source_guard})return null;
        try {{
            {snippet}
            if(reserve>16384)return null;
            final java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocate(reserve)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            final WriteBuffer b=new WriteBuffer();
            b.armExternal(bytes,0,reserve);
            b.beginDirect();
            b.position(ConnectionConfig.HEADER_SIZE);
            try {{
                {payload}
                final int length=b.position();
                if(length>16384)return null;
                bytes.putShort(0,(short)length);
                return java.util.Arrays.copyOf(bytes.array(),length);
            }} finally {{
                b.releaseResources();
            }}
        }} catch(Throwable ignored) {{
            return null;
        }}
    }}
'''
    return result,old[-1]

def broadcast(s,method,match,anchor,make_expr,fallback):
    old=M(s,"public",method,match)
    params=old[2]
    if "borrowVisiblePlayerFast(" not in old[3]:
        raise ValueError(method+" missing borrowed AOI")
    wire=f'''public static void {method}({params}) {{
        if({anchor}==null)return;
        final byte[] frozen={make_expr};
        if(frozen==null) {{
            {old[3].strip()}
            return;
        }}
        {BROADCAST}({anchor},frozen,true,-1,null,
                client -> sendFrozen(client,frozen));
    }}'''
    return R(s,old,wire)

def apply(s):
    before=s
    parts=[
        ("sendNpc",lambda p:"L1NpcInstance npc" in p,"sharedWireNpc","L1NpcInstance npc"),
        ("sendPcFull",lambda p:"String requestedName" in p and "boolean revealInvisibleName" in p,
         "sharedWirePcFull","L1PcInstance pc, String requestedName, boolean revealInvisibleName"),
        ("sendBuilder",lambda p:"WorldPutBuilder s" in p,"sharedWireBuilder","WorldPutBuilder s")
    ]
    factories=[]
    for original_name,select,new_name,signature in parts:
        factory,send_body=build_factory(s,original_name,select,new_name,signature) if False else (None,None)
        # method metadata separately passed; never guess sender argument count.
        full=M(s,"public",original_name,select)
        part=full[3]
        if original_name=="sendBuilder":start=part.index("final int x = s.x;")
        else:start=part.index("/* Capture once.")
        end=part.index("final CIOFrame frame",start)
        snapshot=part[start:end]
        assert snapshot.count("final int reserve")==1
        snapshot=re.sub(r'\breturn false\s*;','return null;',snapshot)
        first=part.index("writeHeader(b);",end)
        final=part.index("writeTrailer(b);",first)+len("writeTrailer(b);")
        payload=part[first:final]
        assert "client" not in payload and "connection" not in payload
        arg="s" if original_name=="sendBuilder" else "npc" if original_name=="sendNpc" else "pc"
        factory=f'''
    private static byte[] {new_name}({signature}) {{
        if({arg}==null)return null;
        try {{
            {snapshot}
            if(reserve>16384)return null;
            final java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocate(reserve)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            final WriteBuffer b=new WriteBuffer();
            b.armExternal(bytes,0,reserve);
            b.beginDirect();
            b.position(ConnectionConfig.HEADER_SIZE);
            try {{
                {payload}
                final int length=b.position();
                if(length>16384)return null;
                bytes.putShort(0,(short)length);
                return java.util.Arrays.copyOf(bytes.array(),length);
            }} finally {{
                b.releaseResources();
            }}
        }} catch(Throwable ignored) {{
            return null;
        }}
    }}
'''
        factories.append(factory)
    out=s
    for method,check,anchor,call in [
        ("broadcastNpc",lambda p:"L1NpcInstance npc" in p,"npc","sharedWireNpc(npc)"),
        ("broadcastPc",lambda p:"String requestedName" in p,"pc",
         "sharedWirePcFull(pc,requestedName,revealInvisibleName)"),
        ("broadcastBuilder",lambda p:"WorldPutBuilder builder" in p,"anchor",
         "sharedWireBuilder(builder)"),
    ]:
        out=broadcast(out,method,check,anchor,call,"sendFrozen")
    assert M(s,"public","sendNpc")[-1]==M(out,"public","sendNpc")[-1]
    assert M(s,"public","sendPcFull",lambda p:"String requestedName" in p)[-1]==M(out,"public","sendPcFull",lambda p:"String requestedName" in p)[-1]
    assert M(s,"public","sendBuilder")[-1]==M(out,"public","sendBuilder")[-1]
    insert=out.rfind("}")
    return out[:insert]+HELPERS+"\n".join(factories)+out[insert:]
