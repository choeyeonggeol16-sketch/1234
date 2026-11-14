#!/usr/bin/env python3
"""Eliminate packet-specific legacy fallback from all shared SC broadcasts.

Each broadcast serializes once and routes frozen plaintext through ONE CUserSocket
entrypoint. Native <=16K uses its reference-counted constant-buffer; Java-path,
non-native, handshake, foreign-owner, and long frames use the SAME frozen wire
through an ordinary reserved FIFO direct frame. No packet serializer is invoked
per recipient, and no working socket is silently bypassed.

Unicast direct-send methods are intentionally preserved (they are not fallbacks).
"""
from __future__ import annotations
import argparse,hashlib,json,pathlib,re,zipfile,sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
SRC='downloads/CTransport_SharedBroadcast_FinalFive_FULL_SRC_20261115.zip'
BASE_SHA='4b2883de27c0c2175637086897e8ac6634cce3a234bb2896f7e3ddf1418edc12'
OUTPUT='downloads/CTransport_SharedBroadcast_NoLegacy_FULL_SRC_20261115.zip'
ROOT_JAVA='src/l1j/server/server/model/network/core/'
PACKETS=ROOT_JAVA+'sc/packet/'
SHA=lambda b:hashlib.sha256(b).hexdigest()
CALL_PATTERN=re.compile(r'\bCSharedBroadcast\.(sendAll|sendPlayers|send)\s*\(')

NEW_CORE='''package l1j.server.server.model.network.core.broadcast;

import java.util.List;
import java.util.function.Predicate;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

/** One immutable encoded frame, shared across every eligible recipient.
 * Packet-specific old serializers are never invoked during broadcast.
 */
public final class CSharedBroadcast {
    private CSharedBroadcast() {}

    public static void send(L1Object anchor, byte[] wire, boolean fast, int radius,
            Predicate<L1PcInstance> filter) {
        if (anchor == null || wire == null) return;
        final L1World world=L1World.getInstance();
        final boolean borrowed=fast && radius < 0;
        final List<L1PcInstance> recipients=borrowed
                ? world.borrowVisiblePlayerFast(anchor)
                : radius < 0 ? world.getVisiblePlayer(anchor)
                : world.getVisiblePlayer(anchor, radius);
        try (var shared=new CAsyncSendConstBufferExceptSelf(wire,anchor.getId(),Integer.MAX_VALUE)) {
            for (L1PcInstance pc:recipients) {
                if (!CTLMFilter.sameWorldOther(anchor,pc)
                        || filter != null && !filter.test(pc))continue;
                final CIOClient client=pc.getNetConnection();
                if(client!=null && client.isConnected())
                    shared.send(client.getConnection(),pc.getId());
            }
        } finally {
            if(borrowed)world.releaseVisiblePlayerFast(recipients);
        }
    }

    /** Global broadcast includes the sender and uses the normal socket FIFO. */
    public static void sendAll(byte[] wire) {
        if(wire==null)return;
        try (var shared=new CAsyncSendConstBuffer(wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for (L1PcInstance pc:L1World.getInstance().getAllPlayers()) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client!=null && client.isConnected())
                    shared.send(client.getConnection(),pc.getId());
            }
        }
    }
}
'''

SOCKET_API=r'''
    /** Uniform frozen-frame admission for transport modes without a reusable
     * native ConstBuffer handle. Bytes are already serialized once by the
     * broadcaster. This is NOT a packet-specific serializer or fallback.
     * The normal direct frame reserves a FIFO ticket before any byte is copied.
     */
    public boolean sendFrozenWire(byte[] frameBytes) {
        if(frameBytes==null || frameBytes.length<=CIOBufferPool.HEADER_BYTES
                || frameBytes.length>CIOBufferPool.MAX_WIRE_BYTES
                || ((frameBytes[0]&0xff)|((frameBytes[1]&0xff)<<8))!=frameBytes.length
                || !isClientConnected()) return false;
        final CIOFrame frame=beginDirectFrame(frameBytes.length);
        if(frame==null)return false;
        boolean committed=false;
        try {
            final l1j.server.server.model.network.buffer.WriteBuffer writer=frame.buffer();
            final java.nio.ByteBuffer memory=writer.getBuffer();
            memory.position(writer.baseOffset()+writer.position());
            memory.put(frameBytes,CIOBufferPool.HEADER_BYTES,
                    frameBytes.length-CIOBufferPool.HEADER_BYTES);
            writer.position(frameBytes.length);
            committed=commitDirectFrame(frame);
            return committed;
        } catch(Throwable failure) {
            return false;
        } finally {
            if(!committed)abortDirectFrame(frame);
        }
    }
'''

SHARED_SEND=r'''
    /**
     * The single shared-broadcast SEND contract.
     * Native <=16 KiB: retain one immutable ConstBuffer source per owner.
     * Other transports/large frames: enqueue those SAME frozen bytes directly.
     * No recipient-specific packet construction, no legacy serializer.
     * true means this socket actually admitted a SEND lease.
     */
    public final synchronized boolean send(CUserSocket target,int recipientId) {
        if(closed)throw new IllegalStateException("broadcast already closed");
        if(target==null || exclude(recipientId) || !target.isClientConnected())return false;
        final int limit=recipientId==senderId?Integer.MAX_VALUE:pendingLimit;
        if(wire.length<=16384 && target.supportsConstBuffer()
                && target.pendingSendBytes()<limit) {
            try {
                if(source==null)source=target.createConstBuffer(wire);
                if(source!=null && target.ownsConstBuffer(source)
                        && target.enqueueConstBufferConfirmed(source,limit))
                    return true;
            } catch (RuntimeException failure) {
                // The immutable plaintext is still available for the ordinary
                // socket FIFO frame path; no packet serializer is repeated.
            }
        }
        return target.sendFrozenWire(wire);
    }
'''

def matched_close(s,start,open_char='(',close_char=')'):
    assert s[start]==open_char
    depth=0;idx=start
    quote=None;slash=False
    while idx<len(s):
        c=s[idx]
        if quote:
            if slash:slash=False
            elif c=='\\':slash=True
            elif c==quote:quote=None
        elif c in '"\'':
            quote=c
        elif c==open_char:depth+=1
        elif c==close_char:
            depth-=1
            if depth==0:return idx+1
        idx+=1
    raise ValueError("Unclosed bracket at "+str(start))

def java_arguments(args):
    chunks=[];begin=0
    stack=[];quote=None;slash=False
    pairs={')':'(',']':'[','}':'{'}
    for i,c in enumerate(args):
        if quote:
            if slash:slash=False
            elif c=='\\':slash=True
            elif c==quote:quote=None
            continue
        if c in '"\'':
            quote=c
            continue
        if c in '([{':stack.append(c)
        elif c in ')]}':
            if not stack or stack.pop()!=pairs[c]:
                raise ValueError("Java args brace unbalanced: "+args[:100])
        elif c==',' and not stack:
            chunks.append(args[begin:i].strip());begin=i+1
    if stack or quote:raise ValueError("Java args unclosed")
    chunks.append(args[begin:].strip())
    return chunks

def replace_shared_calls(s):
    ranges=[];num=0;class_counts={}
    for m in CALL_PATTERN.finditer(s):
        open_idx=m.end()-1
        close=matched_close(s,open_idx)
        label=m[1]
        fields=java_arguments(s[open_idx+1:close-1])
        if label=='sendAll':
            if len(fields)!=2:raise ValueError("sendAll requires legacy arg "+str(fields))
            if not ('->' in fields[1] or '::' in fields[1]):
                raise ValueError("Unrecognized global legacy callback")
            fields=fields[:1];new_label='sendAll'
        else:
            if len(fields)!=6:raise ValueError("AOI requires legacy arg "+str(fields))
            if not ('->' in fields[5] or '::' in fields[5]):
                raise ValueError("Unexpected non-legacy sixth argument")
            fields=fields[:5];new_label='send'
        print("OLD_CALLBACK", label, (" ".join((args[-1] if label=="sendAll" else args[-1]).split()))[:320])
        rewritten="CSharedBroadcast."+new_label+"("+", ".join(fields)+")"
        ranges.append((m.start(),close,rewritten))
        num+=1
    for start,end,repl in reversed(ranges):
        s=s[:start]+repl+s[end:]
    return s,num

def find_java_method(s,needle):
    idx=s.index(needle)
    p=s.index('(',idx)
    params_end=matched_close(s,p)
    brace=s.index('{',params_end)
    end=matched_close(s,brace,'{','}')
    return idx,end,s[idx:end]

def remove_socket_old_enqueue(s):
    a,b,method=find_java_method(s,"public void enqueueConstBuffer(")
    assert 'stageOutboundFrameLocked(' in method and 'CloseReason.SEND_REJECTED' in method
    s=s[:a]+s[b:]
    assert 'public boolean enqueueConstBufferConfirmed(' in s
    assert 'public void enqueueConstBuffer(' not in s
    return s

def replace_shared_source(s):
    assert "frame.length > 16384" in s
    s=s.replace("frame.length > 16384","frame.length > 0xFFFF",1)
    a,b,method=find_java_method(s,"public final synchronized boolean apply(")
    assert 'target.enqueueConstBuffer(source, limit)' in method
    s=s[:a]+s[b:]
    a,b,method=find_java_method(s,"public final synchronized boolean applyConfirmed(")
    assert 'target.enqueueConstBufferConfirmed(source, limit)' in method
    s=s[:a]+s[b:]
    marker="    @Override public final synchronized void close()"
    if marker not in s:raise ValueError("missing shared close")
    s=s.replace(marker,SHARED_SEND+"\n"+marker,1)
    assert "public final synchronized boolean apply(" not in s
    assert "public final synchronized boolean applyConfirmed(" not in s
    return s

def remove_null_legacy_blocks(s):
    # Legacy branches originally sent the same frame by visiting each viewer.
    pat=re.compile(r'\bif\s*\(\s*(wire|frozen)\s*==\s*null\s*\)\s*\{')
    n=0
    while (m:=pat.search(s)):
        brace=s.index('{',m.start())
        end=matched_close(s,brace,'{','}')
        inside=s[brace+1:end-1]
        if not ("return;" in inside or "getVisiblePlayer" in inside or "send(" in inside):
            raise ValueError("Unrecognized wire-null branch: "+inside[:140])
        s=s[:m.start()]+f'if({m[1]}==null)return;'+s[end:]
        n+=1
    return s,n

def find_if_block(s,text_check):
    pat=re.compile(r'\bif\s*\(')
    for m in pat.finditer(s):
        end=matched_close(s,m.end()-1)
        cond=s[m.end():end-1]
        if text_check(cond):
            j=end
            while j<len(s) and s[j].isspace():j+=1
            if j>=len(s) or s[j]!='{':continue
            last=matched_close(s,j,'{','}')
            return m.start(),last,cond,s[j+1:last-1]
    return None

def remove_invalid_broadcast(s,class_name):
    if class_name in ('SC_Dexup.java','SC_Strup.java'):
        count=0
        while True:
            found=find_if_block(s,lambda c:'actor' in c and 'null' in c)
            if found is None:break
            a,b,cond,body=found
            if 'borrowVisiblePlayerFast' not in body and 'getVisiblePlayer' not in body:
                break
            s=s[:a]+f'if({cond})return;'+s[b:];count+=1
        if not count:raise ValueError("No actor invalid legacy loop "+class_name)
    if class_name=='SC_Poison.java':
        count=0
        while True:
            found=find_if_block(s,lambda c:'type' in c and '<' in c and '>' in c)
            if found is None:break
            a,b,cond,body=found
            if 'getVisiblePlayer' not in body and 'borrowVisiblePlayerFast' not in body:break
            s=s[:a]+f'if({cond})return;'+s[b:];count+=1
        if not count:raise ValueError("No poison invalid legacy loop")
    return s

def replace_raw_apply(s,cls):
    # An explicit fanout retains its exact iteration order, receiver list, and
    # duplicate semantics. It now uses one unified send receipt, not serializers.
    rx=re.compile(r'if\s*\(\s*client\s*!=\s*null\s*&&\s*client\.isConnected\(\)\s*&&\s*!shared\.apply\(\s*client\.getConnection\(\)\s*,\s*(pc|player)\.getId\(\)\s*\)\s*\)\s*(?:send0|send)\s*\([^;]*\);',re.S)
    def rewrite(m):
        who=m[1]
        return f'if(client!=null && client.isConnected()) shared.send(client.getConnection(),{who}.getId());'
    s,n=rx.subn(rewrite,s)
    if n!=1:raise ValueError("Expected exactly one explicit raw apply for "+cls+", found "+str(n))
    return s

def drop_item(s):
    a=s.index('if(frozen==null)')
    brace=s.index('{',a)
    end=matched_close(s,brace,'{','}')
    section=s[brace+1:end-1]
    if "return viewers;" not in section or "borrowVisiblePlayerFast" not in section:
        raise ValueError("Unrecognized ground-item fallback loop")
    s=s[:a]+"if(frozen==null)return viewers;"+s[end:]
    rx=re.compile(r'boolean\s+delivered\s*=\s*shared\.applyConfirmed\(\s*client\.getConnection\(\)\s*,\s*pc\.getId\(\)\s*\)\s*;\s*if\s*\(\s*!delivered\s*\)\s*delivered\s*=\s*send\(\s*client\s*,\s*x\s*,\s*y\s*,\s*objectId\s*,\s*groundGfxId\s*,\s*lightRange\s*,\s*count\s*,\s*itemName\s*\)\s*;',re.S)
    s,n=rx.subn('final boolean delivered=shared.send(client.getConnection(),pc.getId());',s)
    if n!=1:raise ValueError("Expected ground-item duplicate send exactly once, got "+str(n))
    if 'shared.applyConfirmed' in s:raise ValueError("Still using applyConfirmed")
    s=s.replace('if(max>16384 || max>ConnectionConfig.MAX_WIRE_PACKET_SIZE)return null;',
                'if(max>ConnectionConfig.MAX_WIRE_PACKET_SIZE)return null;')
    return s

def range_skill(s):
    m=re.search(r'\bif\s*\(\s*wire\s*!=\s*null\s*\)\s*\{',s)
    if not m:raise ValueError("Missing range wire branch")
    brace=s.index('{',m.start())
    end=matched_close(s,brace,'{','}')
    after=s[end:]
    n=re.match(r'\s*else\s*\{',after)
    if not n:raise ValueError("Missing legacy range else")
    else_open=end+n.end()-1
    else_end=matched_close(s,else_open,'{','}')
    if 'borrowVisiblePlayerFast' not in s[else_open:else_end]:
        raise ValueError("Range else is not legacy AOI")
    s=s[:end]+s[else_end:]
    # 65535 is the protocol limit; frames over 16KiB are carried by the
    # same frozen-frame interface (transport direct copy, no reserializing).
    s=s.replace('if(count<0 || size>16384) return null;',
                'if(count<0 || size>0xFFFF) return null;')
    return s

def world(s):
    if s.count('client -> sendFrozen(client,frozen)')!=0:
        raise ValueError("Unexpected surviving WorldPut lambda")
    # The original direct serializers are retained for unicast only; the
    # broadcast's single frozen snapshot is now the sole packet creation.
    if 'private static boolean sendFrozen(' in s:
        a,b,_=find_java_method(s,'private static boolean sendFrozen(')
        s=s[:a]+s[b:]
    # The old complexReserve was conservative and could reject a valid frame
    # merely because the maximum MS949 bound exceeded 65535. On exceptional
    # bounds reserve a full max frame, still using one immutable packet.
    for method in ('sharedWireNpc(', 'sharedWirePcFull(', 'sharedWireBuilder('):
        a,b,code=find_java_method(s,'private static byte[] '+method)
        code=code.replace('if(reserve>16384)return null;',
                          'final int capacity=reserve>0 && reserve<=0xFFFF?reserve:0xFFFF;')
        code=code.replace('if (reserve <= 0) {\n                return null;\n            }','')
        code=re.sub(r'if\s*\(\s*reserve\s*<=\s*0\s*\)\s*\{\s*return null\s*;\s*\}','',code)
        code=code.replace('java.nio.ByteBuffer.allocate(reserve)',
                          'java.nio.ByteBuffer.allocate(capacity)')
        code=code.replace('b.armExternal(bytes,0,reserve)','b.armExternal(bytes,0,capacity)')
        code=code.replace('if(length>16384)return null;','if(length>0xFFFF)return null;')
        if 'final int capacity=' not in code:raise ValueError("World factory bound not updated "+method)
        s=s[:a]+code+s[b:]
    return s

def upgrade_bounds(s):
    # The former 16 KiB bound was a limitation of native ConstBuffer, not
    # of Lineage's 16-bit packet length. The unified API carries bigger frames
    # through the same direct frame FIFO without using legacy serializers.
    s=s.replace('if (size > 16384)', 'if (size > 0xFFFF)')
    s=s.replace('if(size>16384)', 'if(size>0xFFFF)')
    s=s.replace('if (calculated > 16384)', 'if (calculated > 0xFFFF)')
    s=s.replace('if(calculated > 16384)', 'if(calculated > 0xFFFF)')
    s=s.replace('if(reserve>16384)', 'if(reserve>0xFFFF)')
    s=s.replace('if (reserve>16384)', 'if (reserve>0xFFFF)')
    s=s.replace('if(capacity>16384)', 'if(capacity>0xFFFF)')
    s=s.replace('if(capacity > 16384)', 'if(capacity > 0xFFFF)')
    s=s.replace('if(size>16384)return null;', 'if(size>0xFFFF)return null;')
    s=s.replace('if(capacity>16384)return null;', 'if(capacity>0xFFFF)return null;')
    s=s.replace('if(max>16384 || max>ConnectionConfig.MAX_WIRE_PACKET_SIZE)',
                'if(max>ConnectionConfig.MAX_WIRE_PACKET_SIZE)')
    return s

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--input',default=SRC,type=pathlib.Path)
    ap.add_argument('--output',default=OUTPUT,type=pathlib.Path)
    ap.add_argument('--work',default='/tmp/shared-broadcast-no-legacy',type=pathlib.Path)
    args=ap.parse_args()
    raw=args.input.read_bytes()
    if SHA(raw)!=BASE_SHA:raise ValueError('Wrong source input SHA256 '+SHA(raw))
    with zipfile.ZipFile(args.input) as z:
        if z.testzip():raise ValueError('Bad source ZIP')
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    orig=dict(files)
    changed=set()
    def edit(p,op):
        old=files[p].decode('utf8')
        new=op(old)
        if old!=new:files[p]=new.encode('utf8');changed.add(p)
        else:raise ValueError('Expected file change: '+p)
    core=ROOT_JAVA+'broadcast/CSharedBroadcast.java'
    files[core]=NEW_CORE.encode('utf8');changed.add(core)
    edit(ROOT_JAVA+'connection/CUserSocket.java',
         lambda s:remove_socket_old_enqueue(s[:s.rfind('}')]+SOCKET_API+s[s.rfind('}'):]))
    edit(ROOT_JAVA+'broadcast/CAsyncSendConstBuffer.java',replace_shared_source)
    calls_total=0;null_removed=0;explicit=0
    by_class={}
    for p in sorted(files):
        if not(p.startswith(PACKETS) and p.endswith('.java')):continue
        s=files[p].decode('utf8')
        name=p.split('/')[-1]
        s,n=replace_shared_calls(s);calls_total+=n
        if n:by_class[name]=n
        if name=='SC_DropItem.java':
            s=drop_item(s)
        if name=='SC_RangeSkill.java':
            s=range_skill(s)
        if name in ('SC_War.java','SC_UseArrowSkill.java'):
            s=replace_raw_apply(s,name);explicit+=1
        if name=='SC_WorldPutObject.java':
            s=world(s)
        if name in ('SC_Dexup.java','SC_Strup.java','SC_Poison.java'):
            s=remove_invalid_broadcast(s,name)
        s,nnull=remove_null_legacy_blocks(s);null_removed+=nnull
        s=upgrade_bounds(s)
        old=files[p].decode('utf8')
        if s!=old:files[p]=s.encode('utf8');changed.add(p)
    if calls_total!=88:raise ValueError(f'Expected 88 old call sites, found {calls_total}')
    if explicit!=2:raise ValueError("Explicit loops not migrated")
    if null_removed<10:raise ValueError("Expected significant legacy null guards")
    assert len(by_class)==54,(len(by_class),by_class)
    for p,b in files.items():
        if not p.endswith('.java'):continue
        s=b.decode('utf8')
        if p.startswith(PACKETS):
            if re.search(r'CSharedBroadcast\.sendPlayers\s*\(',s):raise ValueError('sendPlayers still used '+p)
            if re.search(r'!shared\.apply(?:Confirmed)?\s*\(',s):
                raise ValueError("old apply+serializer fallback "+p)
            for m in CALL_PATTERN.finditer(s):
                e=matched_close(s,m.end()-1)
                params=java_arguments(s[m.end():e-1])
                expected=1 if m[1]=='sendAll' else 5
                if len(params)!=expected:
                    raise ValueError(f"Remaining legacy callback in {p}: {len(params)}")
            if 'client -> sendFrozen(client,frozen)' in s:
                raise ValueError("Legacy world frozen send callback")
    assert len([p for p in files if p.endswith('.java')])==1711
    assert files['CTransport.dll']==orig['CTransport.dll']
    args.work.mkdir(parents=True,exist_ok=True)
    for p,b in files.items():
        if p.startswith('src/'):
            dst=args.work/p;dst.parent.mkdir(parents=True,exist_ok=True)
            dst.write_bytes(b)
    # Existing independently built Java21 parity tests from the prior stage.
    sys.path.insert(0,str(ROOT))
    from patches.ctransport_broadcast_final5_20261115 import build_final5
    test=args.work/'testsrc/l1j/server/server/model/network/core/sc/packet/SharedFinalFiveWireParity.java'
    test.parent.mkdir(parents=True,exist_ok=True)
    parity=build_final5.PARITY_SRC
    old_check='''if(invoke("SC_RangeSkill","sharedWire",types,1,2,3,4,5,6,7,8,ids,2800)!=null)
           throw new AssertionError("Oversized range frames must use legacy send");'''
    new_check='''byte[] large=invoke("SC_RangeSkill","sharedWire",types,1,2,3,4,5,6,7,8,ids,2800);
        if(large==null || large.length!=24+2800*6)
           throw new AssertionError("Large range frame was not encoded once");'''
    if old_check not in parity:raise ValueError("Previous range parity source changed")
    parity=parity.replace(old_check,new_check)
    test.write_text(parity,encoding='utf8')
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,b in sorted(files.items()):z.writestr(p,b)
    with zipfile.ZipFile(args.output) as z:
        assert z.testzip() is None
        assert sum(p.endswith('.java') for p in z.namelist())==1711
    report=dict(source_sha256=BASE_SHA,output_sha256=SHA(args.output.read_bytes()),
        changed_files=sorted(changed),changed_count=len(changed),
        original_broadcast_calls=88,legacy_callback_calls_removed=calls_total,
        callback_classes=len(by_class),legacy_null_broadcast_branches_removed=null_removed,
        explicit_legacy_sender_loops_removed=explicit,
        max_wire_bytes=65535, native_const_buffer_max=16384,
        all_java_files=1711,dll_unchanged=True,
        broadcasts_reencode_per_recipient=False,
        direct_unicast_sender_methods_preserved=True,
        windows_network_runtime_tested=False)
    (args.work/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
