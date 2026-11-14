#!/usr/bin/env python3
"""Remaining SC fanouts: 3 attack/skill paths + receipt-correct item shared SEND.

Does NOT change current shared broadcast routing, any existing outbound
method semantics, CIO execution ownership, or packet IDs. Every previous
direct sender remains text-identical and remains the fallback.
"""
from __future__ import annotations
import re,json,hashlib,zipfile,pathlib,sys
ROOT=pathlib.Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
from patches.ctransport_broadcast_expand4_20261115 import expand4 as helper
BASE="src/l1j/server/server/model/network/core/sc/packet/"
CORE="src/l1j/server/server/model/network/core/"
M=helper.method
R=helper.replace
SEND="l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send"
PLAYERS="l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendPlayers"
SHA=lambda v:hashlib.sha256(v).hexdigest()

CUSER_CONFIRMED='''
    /**
     * Receipt-bearing constant-buffer admission for stateful broadcasts.
     * Unlike enqueueConstBuffer(), false means no shared frame was staged.
     * No existing sender semantics are changed.
     */
    public boolean enqueueConstBufferConfirmed(CAsyncSendConstBuffer.Source source, int pendingLimit) {
        CIOBufferEx lease = null;
        boolean accepted = false, failed = false;
        _stateLock.lock();
        try {
            if (_closed != 0 || _closing != 0 || !ownsConstBuffer(source)
                    || _outstandingWireBytes + _reservedWireBytes >= pendingLimit) return false;
            lease = _pool.acquireShared(source);
            accepted = lease != null && stageOutboundFrameLocked(lease, false);
            failed = !accepted;
        } catch (Throwable failure) {
            failed = true;
        } finally {
            try { if (!accepted && lease != null) _pool.release(lease); }
            finally { _stateLock.unlock(); }
        }
        if (accepted) finishStagedSend(lease, false);
        else if (failed) close(CloseReason.SEND_REJECTED);
        return accepted;
    }
'''

BUFFER_CONFIRMED='''
    /** True only when this recipient's shared SEND lease was staged.
     * Used by broadcasts that must update world state after SEND acceptance.
     */
    public final synchronized boolean applyConfirmed(CUserSocket target, int recipientId) {
        if (closed) throw new IllegalStateException("broadcast already closed");
        if (target == null || exclude(recipientId) || !target.isClientConnected()) return false;
        if (!target.supportsConstBuffer()) return false;
        final int limit = recipientId == senderId ? Integer.MAX_VALUE : pendingLimit;
        if (target.pendingSendBytes() >= limit) return false;
        if (source == null) source = target.createConstBuffer(wire);
        if (source == null || !target.ownsConstBuffer(source)) return false;
        return target.enqueueConstBufferConfirmed(source, limit);
    }
'''

SEND_PLAYERS='''
    /** Same visible-player/fallback rules as send(), but preserves the PC
     * receiver for packet encoders with a L1PcInstance direct-send contract.
     */
    public static void sendPlayers(L1Object anchor, byte[] wire, boolean fast, int radius,
            Predicate<L1PcInstance> filter, Consumer<L1PcInstance> legacy) {
        if (anchor == null) return;
        L1World world = L1World.getInstance();
        boolean borrowed = fast && radius < 0;
        List<L1PcInstance> recipients = borrowed ? world.borrowVisiblePlayerFast(anchor)
                : radius < 0 ? world.getVisiblePlayer(anchor) : world.getVisiblePlayer(anchor, radius);
        try (var shared = new CAsyncSendConstBufferExceptSelf(wire, anchor.getId(), Integer.MAX_VALUE)) {
            for (L1PcInstance pc : recipients) {
                if (!CTLMFilter.sameWorldOther(anchor, pc)
                        || filter != null && !filter.test(pc)) continue;
                CIOClient client = pc.getNetConnection();
                if (client != null && client.isConnected()
                        && !shared.apply(client.getConnection(), pc.getId()))
                    legacy.accept(pc);
            }
        } finally { if (borrowed) world.releaseVisiblePlayerFast(recipients); }
    }
'''

RANGE_WIRE='''
    private static byte[] sharedWire(int id,int x,int y,int heading,int seq,int gfx,
            int action,int type,int[] ids,int count) {
        final long size=24L+6L*count;
        if(count<0 || size>16384) return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate((int)size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_ATTACK_MANY).put((byte)action)
         .putInt(id).putShort((short)x).putShort((short)y).put((byte)heading)
         .putInt(seq).putShort((short)gfx).put((byte)type)
         .putShort((short)0).putShort((short)count);
        for(int i=0;i<count;i++)b.putInt(ids[i]).put((byte)32).put((byte)action);
        return b.array();
    }
'''

ARROW_WIRE='''
    private static byte[] sharedWire(int attackerId,int ox,int oy,int oh,int actId,
            int targetId,int spellGfx,int tx,int ty,boolean isHit,int seq) {
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(WIRE_SIZE)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)WIRE_SIZE).put((byte)Opcodes.S_ATTACK).put((byte)actId)
         .putInt(attackerId).putInt(targetId).put((byte)(isHit?6:0))
         .put((byte)0).put((byte)oh).putInt(seq).putShort((short)spellGfx)
         .put((byte)0).putShort((short)ox).putShort((short)oy)
         .putShort((short)tx).putShort((short)ty)
         .put((byte)0).put((byte)0).put((byte)0).put((byte)0).put((byte)0);
        return b.array();
    }
'''

ATTACK_WIRE='''
    private static byte[] sharedWire(int actionId,int chaId,int targetObjId,
            int isHit,int heading,int seq,int spellGfx,int myX,int myY,
            int targetX,int targetY) {
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(WIRE_SIZE)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)WIRE_SIZE).put((byte)Opcodes.S_ATTACK).put((byte)actionId)
         .putInt(chaId).putInt(targetObjId).put((byte)isHit).put((byte)0)
         .put((byte)heading).putInt(seq).putShort((short)spellGfx).put((byte)6)
         .putShort((short)myX).putShort((short)myY)
         .putShort((short)targetX).putShort((short)targetY)
         .put((byte)0).put((byte)0).put((byte)0).put((byte)0).put((byte)0);
        return b.array();
    }
'''

def at_end(s,code):
    i=s.rfind("}")
    if i<0:raise ValueError("No class closing brace")
    return s[:i]+"\n"+code+"\n"+s[i:]

def old_send_assert(before,after,method_name,kind="private"):
    if M(before,kind,method_name)[-1]!=M(after,kind,method_name)[-1]:
        raise AssertionError("Original sender changed "+method_name)

def replace_fast_loop(body,marker,replacement):
    start=body.find(marker)
    if start<0:raise ValueError("Expected original AOI loop "+marker)
    # The loop extends to the method body's end, with no trailing actions.
    if body[start:].count("borrowVisiblePlayerFast(")!=1:
        raise ValueError("Unexpected multiple AOI loops")
    return body[:start]+replacement

def range_skill(s):
    old=M(s,"private","dispatch");body=old[3]
    marker='        {\n            final L1World __cioWorld0'
    replacement='''        final byte[] wire=sharedWire(id,x,y,heading,seq,gfx,action,type,ids,used);
        if(wire!=null) {
            '''+PLAYERS+'''(caster,wire,true,-1,null,
                viewer -> send(viewer,id,x,y,heading,seq,gfx,action,type,ids,used));
        } else {
'''+body[body.index(marker):].strip()+'''
        }
'''
    new=replace_fast_loop(body,marker,replacement)
    out=R(s,old,old[-1].replace(body,new,1))
    out=at_end(out,RANGE_WIRE)
    old_send_assert(s,out,"send","private")
    return out

def arrow_skill(s):
    out=s
    chosen=[
        lambda p:"L1PcInstance pc" in p and "targetObj" in p,
        lambda p:"L1Character cha" in p and "L1Character anchor" not in p,
        lambda p:"L1Character anchor" in p and "attackerId" in p,
    ]
    for kind,check in [("sendAndBroadcast",chosen[0]),("broadcast",chosen[1]),("broadcast",chosen[2])]:
        old=M(out,"public",kind,check)
        params=old[2];body=old[3]
        mm=re.search(r'(?m)^[ \t]*\{[ \t]*\n[ \t]*final L1World __cioWorld',body)
        if mm is None:raise ValueError("No outgoing AOI fanout in "+kind+" "+params)
        anchor="pc" if kind=="sendAndBroadcast" else "cha" if "L1Character cha" in params else "anchor"
        target="targetObj" if "int targetObj" in params else "targetId"
        tx="x" if "int x" in params else "tx"
        ty="y" if "int y" in params else "ty"
        args=f"attackerId,ox,oy,oh,actId,{target},spellGfx,{tx},{ty},isHit,seq"
        newbody=body[:mm.start()]+f'''        {SEND}({anchor}, sharedWire({args}), true, -1, null,
                client -> send0(client,{args}));
''' 
        out=R(out,old,old[-1].replace(body,newbody,1))
    # Explicit receiver lists are *not* AOI: retain order, duplicates, and
    # inclusion of sender; use the non-except-self immutable buffer.
    old=M(out,"public","sendToReceivers")
    body=old[3]
    marker='        for (L1Object o : receivers)'
    k=body.find(marker)
    if k<0:raise ValueError("Missing receiver loop")
    repl='''        try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                sharedWire(attackerId,ox,oy,oh,actId,targetId,spellGfx,tx,ty,isHit,seq),
                Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for (L1Object o : receivers) {
                if (!(o instanceof L1PcInstance)) continue;
                final L1PcInstance pc=(L1PcInstance)o;
                final CIOClient client=pc.getNetConnection();
                if(client!=null && client.isConnected()
                        && !shared.apply(client.getConnection(),pc.getId()))
                    send0(client,attackerId,ox,oy,oh,actId,targetId,spellGfx,tx,ty,isHit,seq);
            }
        }
'''
    out=R(out,old,old[-1].replace(body,body[:k]+repl,1))
    out=at_end(out,ARROW_WIRE)
    old_send_assert(s,out,"send0","private")
    return out

def attack_skill(s):
    out=s
    cases=[
        ("broadcast",lambda p:"L1Character cha" in p and "boolean withCastMotion" in p),
        ("sendAndBroadcast",lambda p:"L1PcInstance pc" in p and "boolean withCastMotion" in p),
    ]
    for name,check in cases:
        old=M(out,"public",name,check)
        params=old[2];body=old[3]
        marker=re.search(r'(?m)^[ \t]*\{[ \t]*\n[ \t]*final L1World __cioWorld',body)
        if not marker:raise ValueError("No AOI loop in "+name)
        anchor="pc" if name=="sendAndBroadcast" else "cha"
        args="fixedActionId,chaId,targetObj,isHit,heading,seq,spellGfx,myX,myY,x,y"
        new=body[:marker.start()]+f'''        {SEND}({anchor},sharedWire({args}),true,-1,null,
                client -> send0(client,{args}));
'''
        out=R(out,old,old[-1].replace(body,new,1))
    prepared=M(out,"private","broadcastPrepared")
    signature=prepared[2]
    names=[x[1] for x in helper.prev.args(signature)]
    if names!=["anchor","actionId","chaId","targetObjId","isHit","heading","seq",
               "spellGfx","myX","myY","targetX","targetY"]:
        raise ValueError("Unexpected prepared attack skill signature")
    args="actionId,chaId,targetObjId,isHit,heading,seq,spellGfx,myX,myY,targetX,targetY"
    new=f'''private static void broadcastPrepared({signature}) {{
        {SEND}(anchor,sharedWire({args}),true,-1,null,
                client -> send0(client,{args}));
    }}'''
    out=R(out,prepared,new)
    out=at_end(out,ATTACK_WIRE)
    old_send_assert(s,out,"send0","private")
    return out

def drop_item(s):
    sender=M(s,"private","send",lambda p:"int groundGfxId" in p and "String itemName" in p)
    body=sender[3]
    begin=body.index("buffer.writeC(Opcodes.S_PUT_OBJECT);")
    end=body.index("committed =",begin)
    operations=body[begin:end].rstrip()
    if operations.count("buffer.write")<17:
        raise ValueError("Drop item payload not matched")
    operation_code=operations.replace("buffer.write","b.write")
    generator=f'''
    private static byte[] sharedWire(int x,int y,int objectId,int groundGfxId,
            int lightRange,int count,String itemName) {{
        final String text=itemName==null?"":itemName;
        final long max=(long)FIXED_WIRE_BYTES+((long)text.length()<<1);
        if(max>16384 || max>ConnectionConfig.MAX_WIRE_PACKET_SIZE)return null;
        final java.nio.ByteBuffer heap=java.nio.ByteBuffer.allocate((int)max)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        final WriteBuffer b=new WriteBuffer();
        b.armExternal(heap,0,(int)max);
        b.beginDirect();
        b.position(ConnectionConfig.HEADER_SIZE);
        try {{
            {operation_code.replace('b.writeS(itemName)', 'b.writeS(text)')}
            final int len=b.position();
            if(len>16384)return null;
            heap.putShort(0,(short)len);
            return java.util.Arrays.copyOf(heap.array(),len);
        }} catch(Throwable ignored) {{
            return null;
        }} finally {{
            b.releaseResources();
        }}
    }}
'''
    old=M(s,"public","broadcast",lambda p:"boolean addKnownObject" in p)
    before=old[3]
    # Limit pre-serialization to exactly the already captured snapshot. Any
    # unsupported/oversized source returns null and runs the original block.
    marker='int viewers = 0;'
    if marker not in before:raise ValueError("Missing item viewer counter")
    # Keep the original nested loop with SEND success receipts.
    first=before.index("if (send(")
    last=before.index(")) {",first)+len(")) {")
    call=before[first:last]
    if "itemName" not in call or "count" not in call:raise ValueError("Unexpected item send call")
    # Replace the send boolean expression with new receipt-bearing attempt,
    # but use the exact original direct send when admission is not confirmed.
    prefix=before[:first]
    suffix=before[last:]
    rewritten=prefix+'''boolean delivered = shared.applyConfirmed(client.getConnection(), pc.getId());
                    if (!delivered) delivered = send(client, x, y, objectId, groundGfxId,
                            lightRange, count, itemName);
                    if (delivered) {'''+suffix
    loop_start=before.index("{\n",before.index("int viewers = 0;"))
    loop_end=before.rfind("return viewers;")
    if loop_end<loop_start:raise ValueError("Missing viewer result")
    original_loop=before[loop_start:loop_end]
    # keep original loop as fallback; avoid the original send() in normal
    # shared path but still only register a player on a confirmed enqueue.
    normal=rewritten[loop_start:rewritten.rfind("return viewers;")]
    new_body=before[:loop_start]+'''
        final byte[] frozen=sharedWire(x,y,objectId,groundGfxId,lightRange,count,itemName);
        if(frozen==null) {
'''+original_loop+'''
            return viewers;
        }
        try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                frozen,item.getId(),Integer.MAX_VALUE)) {
'''+normal+'''
        }
        return viewers;
'''
    out=R(s,old,old[-1].replace(before,new_body,1))
    out=at_end(out,generator)
    if sender[-1]!=M(out,"private","send",lambda p:"int groundGfxId" in p and "String itemName" in p)[-1]:
        raise AssertionError("Ground item individual send changed")
    return out

def integrate(files):
    def edit(path,fn):
        if path not in files:raise FileNotFoundError(path)
        prevsource=files[path].decode("utf8")
        nxt=fn(prevsource)
        if nxt==prevsource:raise ValueError("Expected modified source "+path)
        files[path]=nxt.encode("utf8")
    # Add new API only. Existing enqueueConstBuffer/apply remain text-identical.
    edit(CORE+"connection/CUserSocket.java",lambda s:at_end(s,CUSER_CONFIRMED))
    edit(CORE+"broadcast/CAsyncSendConstBuffer.java",
         lambda s:s[:s.index("    @Override public final synchronized void close()")]
         +BUFFER_CONFIRMED+"\n"
         +s[s.index("    @Override public final synchronized void close()"):])
    edit(CORE+"broadcast/CSharedBroadcast.java",lambda s:at_end(s,SEND_PLAYERS))
    edit(BASE+"SC_RangeSkill.java",range_skill)
    edit(BASE+"SC_UseArrowSkill.java",arrow_skill)
    edit(BASE+"SC_UseAttackSkill.java",attack_skill)
    edit(BASE+"SC_DropItem.java",drop_item)
    return files
