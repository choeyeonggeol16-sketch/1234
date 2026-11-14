#!/usr/bin/env python3
"""Conservative shared-broadcast expansion on the exact last validated full SRC.

Only eight fixed-layout, identical-for-all-recipients AOI broadcasters are
changed. Their original per-recipient send methods remain byte-identical.
No changes to CSharedBroadcast, socket FIFO, IOCP/FFM, Game logic, or buffers.
"""
from __future__ import annotations

from pathlib import Path
import argparse, hashlib, json, re, zipfile

ROOT=Path(__file__).resolve().parents[2]
INPUT="CTransport_MainServer_Proto_Consolidated_FULL_SRC_20261115.zip"
EXPECTED="9dcfe2dfeff45d9c30a777d41812b6c2f47ff43a5dc411c00dd3218a8f0ef54f"
OUTPUT="CTransport_SharedBroadcast_8Packet_Expanded_FULL_SRC_20261115.zip"
PREFIX="src/l1j/server/server/model/network/core/sc/packet/"
HASH=lambda b:hashlib.sha256(b).hexdigest()
SHARED="l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send"
PACKETS={
 "SC_Attack":(39,[("int",n) for n in "attackerId targetId damage heading seq effect x y endX endY".split()]),
 "SC_AttackPacketForNpc":(20,[("int",n) for n in "type npcObjectId chaId heading".split()]),
 "SC_EffectLocation":(10,[("int",n) for n in "x y gfxId".split()]),
 "SC_OnlyEffect":(9,[("int",n) for n in "objectId effectId".split()]),
 "SC_Sound":(6,[("int","sound")]),
 "SC_CharVisualUpdate":(12,[("long","packed")]),
 "SC_Light":(8,[("int",n) for n in ("objectId","type")]),
 "SC_ChangeShape":(18,[("int",n) for n in ("objectId","polyId","weaponState")]),
}
BUILDERS={
"SC_Attack":'''    private static byte[] sharedWire(int attackerId, int targetId, int damage, int heading,
            int seq, int effect, int x, int y, int endX, int endY) {
        return java.nio.ByteBuffer.allocate(39).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 39).put((byte) l1j.server.server.Opcodes.S_ATTACK)
                .put((byte) 1).putInt(attackerId).putInt(targetId)
                .putShort((short) damage).put((byte) heading).putInt(seq)
                .putShort((short) effect).put((byte) 0)
                .putShort((short) x).putShort((short) y)
                .putShort((short) endX).putShort((short) endY)
                .putInt(0).putShort((short) 0).put((byte) 0)
                .putShort((short) 0).array();
    }
''',
"SC_AttackPacketForNpc":'''    private static byte[] sharedWire(int type, int npcObjectId, int chaId, int heading) {
        return java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_ATTACK)
                .put((byte) type).putInt(npcObjectId).putInt(chaId)
                .putShort((short) 1).put((byte) heading)
                .putShort((short) 0).putShort((short) 0).put((byte) 0).array();
    }
''',
"SC_EffectLocation":'''    private static byte[] sharedWire(int x, int y, int gfxId) {
        return java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_EFFECT_LOC)
                .putShort((short) x).putShort((short) y)
                .putShort((short) gfxId).put((byte) 0).array();
    }
''',
"SC_OnlyEffect":'''    private static byte[] sharedWire(int objectId, int effectId) {
        return java.nio.ByteBuffer.allocate(9).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 9).put((byte) Opcodes.S_EFFECT)
                .putInt(objectId).putShort((short) effectId).array();
    }
''',
"SC_Sound":'''    private static byte[] sharedWire(int sound) {
        return java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_SOUND_EFFECT)
                .put((byte) 0).putShort((short) sound).array();
    }
''',
"SC_CharVisualUpdate":'''    private static byte[] sharedWire(long packed) {
        return java.nio.ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 12).put((byte) Opcodes.S_WIELD)
                .putInt((int) (packed >>> 32)).put((byte) packed)
                .put((byte) 0xFF).put((byte) 0xFF)
                .putShort((short) 0).array();
    }
''',
"SC_Light":'''    private static byte[] sharedWire(int objectId, int type) {
        return java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_CHANGE_LIGHT)
                .putInt(objectId).put((byte) type).array();
    }
''',
"SC_ChangeShape":'''    private static byte[] sharedWire(int objectId, int polyId, int weaponState) {
        // writeS("abcd") is MS949 ASCII bytes followed by a NUL terminator.
        return java.nio.ByteBuffer.allocate(18).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 18).put((byte) Opcodes.S_POLYMORPH)
                .putInt(objectId).putShort((short) polyId)
                .put((byte) weaponState).put((byte) 0xFF).put((byte) 0xFF)
                .put((byte) 0).put((byte) 'a').put((byte) 'b')
                .put((byte) 'c').put((byte) 'd').put((byte) 0).array();
    }
''',
}

def replace_method(s, signature, body):
    pat=re.compile(re.escape(signature).replace(r'\ ',r'\s*').replace(r'\(',r'\s*\(\s*').replace(r'\)',r'\s*\)\s*').replace(',',r'\s*,\s*')+r'\s*\{',re.S)
    matches=list(pat.finditer(s))
    if len(matches)!=1:
        raise ValueError(f"Expected one method {signature!r}, found {len(matches)}")
    m=matches[0]
    start=m.end()
    depth=1
    end=start
    while depth:
        if end>=len(s): raise ValueError("Unclosed method "+signature)
        ch=s[end]
        depth += (ch=='{')-(ch=='}')
        end+=1
    old=s[start:end-1]
    if 'CSharedBroadcast' in old or 'sharedWire(' in old:
        raise ValueError("Already patched "+signature)
    if not any(x in old for x in ('send(', 'send0(', 'sendPacked(', 'sendAndBroadcast', 'borrowVisiblePlayerFast')):
        raise ValueError("Original send loop not found "+signature)
    return s[:start]+"\n"+body+"\n    "+s[end-1:], old

def operations(s):
    # Read original production direct serializer, not its shared-wire copy.
    # Exact scanner handles nested casts/parentheses in writeD((int)(packed >> 32)).
    out=[]
    marker=re.compile(r'\bbuffer\.write([CDHS])\(')
    for m in marker.finditer(s):
        typ=m[1];i=m.end();depth=1;start=i
        while depth:
            ch=s[i];depth+=(ch=='(')-(ch==')');i+=1
        expr=s[start:i-1].strip()
        if not s[i:].lstrip().startswith(';'):
            raise ValueError("Unexpected serializer syntax")
        out.append((typ,expr))
    if not out:raise ValueError("No serializer writes")
    return out

def test_source(inputs):
    header=[
        "import java.nio.*;",
        "import java.util.*;",
        "import java.lang.reflect.*;",
        "import l1j.server.server.Opcodes;",
        "public final class SharedBroadcastWireParity {",
        "  static void eq(byte[] actual, byte[] expected, String name, int n) {",
        "    if (!Arrays.equals(actual, expected)) throw new AssertionError(name+\" case=\"+n+\" actual=\"+Arrays.toString(actual)+\" expected=\"+Arrays.toString(expected));",
        "  }",
        "  public static void main(String[] args) throws Exception {",
    ]
    for name in PACKETS:
        header.append("    test"+name+"();")
    header+=['    System.out.println("SHARED_BROADCAST_WIRE_PARITY_OK cases="+(128*'+str(len(PACKETS))+'));','  }']
    methods=[]
    for name,(size,fields) in PACKETS.items():
        op=operations(inputs[name])
        method=["  static void test"+name+"() throws Exception {",
                "    Class<?> cls=Class.forName(\"l1j.server.server.model.network.core.sc.packet."+name+"\");",
                '    Method m=cls.getDeclaredMethod("sharedWire",'+','.join('int.class' if typ=='int' else 'long.class' for typ,n in fields)+');',
                '    m.setAccessible(true);',
                '    for(int k=0;k<128;k++){']
        for idx,(typ,var) in enumerate(fields):
            if typ=='int':
                method.append(f'      int {var}=(int)(0x7f135601L ^ (((long) k+1)*0x9e3779b9L) ^ (0xAC6E01L * {idx+1}));')
            else:
                method.append(f'      long {var}=(0x1020304050607080L ^ (((long)k+1)*0x9e3779b97f4a7c15L));')
        method.append(f'      ByteBuffer b=ByteBuffer.allocate({size}).order(ByteOrder.LITTLE_ENDIAN);')
        method.append(f'      b.putShort((short){size});')
        for typ,expr in op:
            if typ=='C': method.append('      b.put((byte)('+expr+'));')
            elif typ=='H':method.append('      b.putShort((short)('+expr+'));')
            elif typ=='D':method.append('      b.putInt((int)('+expr+'));')
            elif typ=='S':
                if expr!='"abcd"': raise ValueError("Unexpected variable-length writeS "+name+":"+expr)
                method.append('      b.put((byte)\'a\').put((byte)\'b\').put((byte)\'c\').put((byte)\'d\').put((byte)0);')
        method.append(f'      Object[] arguments=new Object[]{{'+','.join('Integer.valueOf('+var+')' if typ=='int' else 'Long.valueOf('+var+')' for typ,var in fields)+'};')
        method.append(f'      eq((byte[])m.invoke(null,arguments),b.array(),"{name}",k);')
        method+=['    }','  }']
        methods+=method
    return '\n'.join(header+methods+['}'])+'\n'

def modify(name, original):
    s=original
    def put(signature,body):
        nonlocal s
        s,_=replace_method(s,signature,body)
    def shared(anchor,expr,fast=False,filter='null',radius='-1',fallback=None):
        fb=fallback or 'client -> send(client, '+expr+')'
        return f'        {SHARED}({anchor}, sharedWire({expr}), {str(fast).lower()}, {radius}, {filter},\n                {fb});'
    if name=="SC_Attack":
        put("public static void sendAndBroadcastFast(L1PcInstance pc, int targetId, int effect, int damage, int heading, int endX, int endY)",
'''        if (pc == null) return;
        final int attackerId = pc.getId();
        final int x = pc.getX();
        final int y = pc.getY();
        final int seq = SEQ.incrementAndGet();
        send(pc, attackerId, targetId, damage, heading, seq, effect, x, y, endX, endY);
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(pc,
                sharedWire(attackerId, targetId, damage, heading, seq, effect, x, y, endX, endY),
                true, -1, null,
                client -> send(client, attackerId, targetId, damage, heading, seq, effect, x, y, endX, endY));''')
    elif name=="SC_AttackPacketForNpc":
        put("public static void broadcast(L1Character anchor, L1Character cha, int npcObjectId, int type)",
'''        if (anchor == null) return;
        final int chaId = cha != null ? cha.getId() : 0;
        final int heading = cha != null ? cha.getHeading() : 0;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor,
                sharedWire(type, npcObjectId, chaId, heading), true, -1, null,
                client -> send0(client, type, npcObjectId, chaId, heading));''')
    elif name=="SC_EffectLocation":
        params="L1Character anchor, int x, int y, int gfxId"
        put("public static void broadcast("+params+")",shared("anchor","x, y, gfxId"))
        put("public static void broadcastFast(l1j.server.server.model.L1Character c, int x, int y, int gfxId)",
            shared("c","x, y, gfxId",fast=True))
        put("public static void broadcastM(l1j.server.server.model.L1Character c, int x, int y, int gfxId)",
            shared("c","x, y, gfxId",filter="pc -> pc.getLinMeff() != 2"))
        put("public static void broadcastWide(l1j.server.server.model.L1Character c, int x, int y, int gfxId)",
            shared("c","x, y, gfxId",radius="50"))
    elif name=="SC_OnlyEffect":
        put("public static void broadcast(L1Character anchor, int objectId, int effectId)",
            shared("anchor","objectId, effectId"))
        put("public static void broadcastFast(L1Character anchor, int objectId, int effectId)",
            shared("anchor","objectId, effectId",fast=True))
    elif name=="SC_Sound":
        put("public static void broadcast(L1Character anchor, int sound)",shared("anchor","sound"))
        put("public static void broadcastFast(L1Character anchor, int sound)",
            shared("anchor","sound",fast=True))
    elif name=="SC_CharVisualUpdate":
        put("public static void broadcastPacked(L1Character anchor, long packed)",
'''        if (anchor == null || packed == 0L) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor,
                sharedWire(packed), false, -1, null,
                client -> sendPacked(client, packed));''')
        put("public static void broadcastFastPacked(L1Character anchor, long packed)",
'''        if (anchor == null || packed == 0L) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor,
                sharedWire(packed), true, -1, null,
                client -> sendPacked(client, packed));''')
    elif name=="SC_Light":
        put("public static void broadcast(L1Character cha, int type)",
'''        if (cha == null) return;
        final int objectId = cha.getId();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(cha,
                sharedWire(objectId, type), true, -1, null,
                client -> send(client, objectId, type));''')
    elif name=="SC_ChangeShape":
        put("public static void broadcast(L1Character anchor, int objectId, int polyId, int weaponState)",
            shared("anchor","objectId, polyId, weaponState"))
        put("public static void broadcastFast(L1Character anchor, int objectId, int polyId, int weaponState)",
            shared("anchor","objectId, polyId, weaponState",fast=True))
    else:raise ValueError("Unreviewed class "+name)
    if "sharedWire(" in original:raise ValueError("already shared " + name)
    idx=s.find("    public static ")
    if idx<0:raise ValueError("No public static for wire insertion "+name)
    s=s[:idx]+BUILDERS[name]+"\n"+s[idx:]
    if SHARED not in s or "private static byte[] sharedWire" not in s:
        raise AssertionError(name)
    # Sender serialization methods remain byte-for-byte the same; only
    # broadcast method bodies and a new private serializer are changed.
    if operations(s)!=operations(original):
        raise RuntimeError("Original per-recipient serializer mutated: "+name)
    return s

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--input",type=Path,default=ROOT/"downloads"/INPUT)
    ap.add_argument("--output",type=Path,default=ROOT/"downloads"/OUTPUT)
    ap.add_argument("--work",type=Path,default=Path("/tmp/8-broadcasts"))
    args=ap.parse_args()
    original=args.input.read_bytes()
    if HASH(original)!=EXPECTED:raise ValueError("Latest full source hash mismatch")
    with zipfile.ZipFile(args.input) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    originals={}
    for name in PACKETS:
        path=PREFIX+name+".java"
        originals[name]=files[path].decode("utf8")
        files[path]=modify(name,originals[name]).encode("utf8")
    assert len([p for p in files if p.endswith(".java")])==1711
    assert "CTransport.dll" in files
    assert HASH(files["CTransport.dll"])=="0801f22fedac95ee29d33c747c5e4019c3b36d79d31615c7d84cc79405258606"
    args.work.mkdir(parents=True,exist_ok=True)
    for p,blob in files.items():
        if p.startswith("src/"):
            fp=args.work/p
            fp.parent.mkdir(parents=True,exist_ok=True)
            fp.write_bytes(blob)
    test=args.work/"testsrc"/"SharedBroadcastWireParity.java"
    test.parent.mkdir(parents=True,exist_ok=True)
    test.write_text(test_source(originals))
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,blob in sorted(files.items()):
            z.writestr(p,blob)
    with zipfile.ZipFile(args.output) as z:
        assert z.testzip() is None
    audit={"input_sha256":EXPECTED,"output_sha256":HASH(args.output.read_bytes()),
           "changed_classes":list(PACKETS),"changed_java_files":8,
           "java_source_count":1711,"shared_buffer_or_transport_changed":False,
           "original_direct_send_serializers_unchanged":True,
           "wire_parity_cases":128*len(PACKETS),
           "remaining_unmodified_due_risk":["SC_AttackPacket","SC_AttackCritical",
                "SC_UseAttackSkill","SC_UseArrowSkill","SC_RangeSkill",
                "SC_AreaAttackEffect","SC_ExplosionNoti","SC_Poison"],
           "windows_game_runtime_verified":False}
    (args.work/"audit.json").write_text(json.dumps(audit,indent=2,ensure_ascii=False)+"\n")
    print(json.dumps(audit,indent=2,ensure_ascii=False))

if __name__=="__main__":
    main()
