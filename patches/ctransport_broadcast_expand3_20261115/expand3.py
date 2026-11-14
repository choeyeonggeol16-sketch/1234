#!/usr/bin/env python3
"""Third safe wave: replace nine remaining recipient-invariant AOI broadcasts.

Retain primitive snapshot semantics, exact 20-byte attack miss variant, all
existing per-socket serializers, FIFO/FFM/CIO, drop-item registration and
recipient-dependent effects.
"""
from __future__ import annotations
import argparse, hashlib, importlib, json, pathlib, re, sys, zipfile

ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from patches.ctransport_broadcast_expand2_20261115 import expand2 as previous

INPUT="CTransport_SharedBroadcast_23Packet_FULL_SRC_20261115.zip"
EXPECTED="0e43849bd1675594f1c33b2df19c16477e0fb5572a34f582383f5b95866cf5ba"
OUTPUT="CTransport_SharedBroadcast_32Packet_FULL_SRC_20261115.zip"
BASE=previous.BASE
SHARED=previous.SHARED
HASH=previous.SHA

FIXED={
    "SC_AttackStatus":(19,("broadcast","broadcastFast")),
    "SC_Lawful":(13,("broadcast","broadcastFast")),
    "SC_Liquor":(8,("broadcast","broadcastFast")),
    "SC_SkillIconBlessOfEva":(9,("broadcast","broadcastFast")),
    "SC_SkillIconWindShackle":(11,("broadcast","broadcastFast")),
    "SC_SkillIconWisdomPotion":(7,("broadcast","broadcastFast")),
    "SC_Fishing":(18,("broadcast",)),
    "SC_Resurrection":(16,("broadcast",)),
}
OTHER=("SC_AttackMissPacket",)
# Existing generic source-derived test generator is parameterized by SPECS.
previous.SPECS=FIXED

def resurrection(original):
    """Keep the old target/user/classId snapshots, only replace fanout."""
    name="SC_Resurrection"
    size=16
    start,end,signature,body,full=previous.extract_method(original,"send","CIOClient client")
    parsed=previous.args(signature)
    assert parsed[0]==("CIOClient","client")
    params=parsed[1:]
    writes=previous.parse_ops(body,name,size)
    result=original
    a,b,sig,oldbody,whole=previous.extract_method(result,"broadcast")
    assert "final int targetId" in oldbody and "final int userId" in oldbody and "final int targetClassId" in oldbody
    oldloop=re.compile(r'for\s*\(\s*L1PcInstance\s+pc\s*:\s*L1World\.getInstance\(\)\.getVisiblePlayer\(anchor\)\s*\)\s*send\(\s*pc\s*,\s*targetId\s*,\s*type\s*,\s*userId\s*,\s*targetClassId\s*\)\s*;')
    replacement=f'''{SHARED}(anchor, sharedWire(targetId, type, userId, targetClassId),
                false, -1, null,
                client -> send(client, targetId, type, userId, targetClassId));'''
    newbody,n=oldloop.subn(lambda m: replacement,oldbody)
    assert n==1,"Resurrection AOI snapshot not recognized"
    result=result[:a]+whole.replace(oldbody,newbody,1)+result[b:]
    index=previous.extract_method(result,"send","CIOClient client")[0]
    result=result[:index]+previous.generate_wire_method(name,params,writes,size)+result[index:]
    assert previous.extract_method(result,"send","CIOClient client")[-1]==full
    assert SHARED in previous.extract_method(result,"broadcast")[3]
    return result,(writes,params)

def miss_overload(source):
    methods=[]
    p=re.compile(r'\bpublic\s+static\s+void\s+broadcast\s*\(')
    for m in p.finditer(source):
        k=m.end()
        lvl=1
        while lvl:
            lvl+=(source[k]=='(')-(source[k]==')')
            k+=1
        sig=source[m.end():k-1]
        if not re.search(r'\bint\s+actId\b',sig):
            continue
        while source[k].isspace():k+=1
        assert source[k]=='{'
        begin=k
        k+=1
        lvl=1
        while lvl:
            lvl+=(source[k]=='{')-(source[k]=='}')
            k+=1
        methods.append((m.start(),k,sig,source[begin+1:k-1]))
    if len(methods)!=1:
        raise ValueError("Attack miss overload not unique: "+str(len(methods)))
    return methods[0]

def attack_miss(original):
    start,end,sig,body=miss_overload(original)
    assert 'borrowVisiblePlayerFast(attacker)' in body
    assert 'send0(' in body and 'true' in body
    # The source selects 20 bytes when send0(..., hasExtraByte=true).
    # All frame fields correspond to its untouched WriteBuffer calls.
    replacement='''public static void broadcast(L1Character attacker, int targetId, int actId) {
        if (attacker == null) return;
        final int attackerId = attacker.getId();
        final int heading = attacker.getHeading();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(attacker,
                sharedWire(attackerId, targetId, actId, heading), true, -1, null,
                client -> send0(client, attackerId, targetId, actId, heading, true));
    }'''
    result=original[:start]+replacement+original[end:]
    wire='''    private static byte[] sharedWire(int attackerId, int targetId, int actId, int heading) {
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(20)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 20);
        b.put((byte) Opcodes.S_ATTACK);
        b.put((byte) actId);
        b.putInt(attackerId);
        b.putInt(targetId);
        b.put((byte) 0);
        b.put((byte) 0);
        b.put((byte) heading);
        b.putInt(0);
        b.put((byte) 0);
        return b.array();
    }

'''
    marker=re.search(r'\bprivate\s+static\s+boolean\s+send0\s*\(',result)
    assert marker is not None
    result=result[:marker.start()]+wire+result[marker.start():]
    assert result[result.index('private static boolean send0'):]==original[original.index('private static boolean send0'):]
    assert "sharedWire(attackerId, targetId, actId, heading)" in result
    return result

MISS_TEST=r'''import java.nio.*;
import java.util.*;
import java.lang.reflect.*;
import l1j.server.server.Opcodes;
public final class AttackMissWireParity {
  public static void main(String[] args) throws Exception {
    Class<?> c=Class.forName("l1j.server.server.model.network.core.sc.packet.SC_AttackMissPacket");
    Method m=c.getDeclaredMethod("sharedWire",int.class,int.class,int.class,int.class);
    m.setAccessible(true);
    for (int k=0;k<256;k++) {
      int attackerId=(int)(0x1234abcdL ^ ((k+1L)*0x9e3779b9L));
      int targetId=(int)(0x34567fedL ^ ((k+1L)*0x5bd1e995L));
      int actId=k-63;
      int heading=k*7;
      ByteBuffer expected=ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
      expected.putShort((short)20);
      expected.put((byte)Opcodes.S_ATTACK);
      expected.put((byte)actId);
      expected.putInt(attackerId);
      expected.putInt(targetId);
      expected.put((byte)0).put((byte)0).put((byte)heading);
      expected.putInt(0).put((byte)0);
      byte[] wire=(byte[])m.invoke(null,attackerId,targetId,actId,heading);
      if(!Arrays.equals(expected.array(),wire))throw new AssertionError("SC_AttackMissPacket case "+k);
    }
    System.out.println("ATTACK_MISS_SHARED_WIRE_PARITY_OK cases=256");
  }
}
'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--input',type=pathlib.Path,default=ROOT/'downloads'/INPUT)
    p.add_argument('--output',type=pathlib.Path,default=ROOT/'downloads'/OUTPUT)
    p.add_argument('--work',type=pathlib.Path,default=pathlib.Path('/tmp/mainserver-broadcast-3'))
    a=p.parse_args()
    initial=a.input.read_bytes()
    if HASH(initial)!=EXPECTED:
        raise ValueError('Refusing to modify non-authoritative input ZIP '+HASH(initial))
    with zipfile.ZipFile(a.input) as z:
        assert z.testzip() is None
        entries={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    before=previous.remaining(entries)
    parity={}
    for name in FIXED:
        key=BASE+name+'.java'
        old=entries[key].decode('utf8')
        if name=='SC_Resurrection':
            new,details=resurrection(old)
        else:
            new,writes,fields=previous.build_and_modify(name,old)
            details=(writes,fields)
        if new==old:raise ValueError('No change '+name)
        entries[key]=new.encode('utf8')
        parity[name]=details
    key=BASE+'SC_AttackMissPacket.java'
    old=entries[key].decode('utf8')
    entries[key]=attack_miss(old).encode('utf8')
    assert len(parity)==8
    after=previous.remaining(entries)
    assert not any(name+'.java' in after for name in list(FIXED)+list(OTHER))
    assert sum(x.endswith('.java') for x in entries)==1711
    assert HASH(entries['CTransport.dll'])=='0801f22fedac95ee29d33c747c5e4019c3b36d79d31615c7d84cc79405258606'
    a.work.mkdir(parents=True,exist_ok=True)
    for path,blob in entries.items():
        if path.startswith('src/'):
            target=a.work/path
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(blob)
    d=a.work/'testsrc'
    d.mkdir(parents=True,exist_ok=True)
    (d/'SharedBroadcastWireParity2.java').write_text(previous.make_java_parity(parity))
    (d/'AttackMissWireParity.java').write_text(MISS_TEST)
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,blob in sorted(entries.items()):z.writestr(path,blob)
    with zipfile.ZipFile(a.output) as z:assert z.testzip() is None
    audit={
        'input_verified_sha256':EXPECTED,
        'output_sha256':HASH(a.output.read_bytes()),
        'new_shared_broadcast_classes':list(FIXED)+list(OTHER),
        'migrated_classes':9,
        'wire_parity_test_cases':8*256+256,
        'total_src_java_files':1711,
        'remaining_unconverted_packet_broadcast_classes':sorted(after),
        'remaining_unconverted_count':len(after),
        'legacy_send_methods_unchanged':True,
        'socket_send_core_unchanged':True,
        'actual_live_windows_tested':False,
    }
    (a.work/'audit.json').write_text(json.dumps(audit,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps(audit,indent=2,ensure_ascii=False))

if __name__=='__main__':
    main()
