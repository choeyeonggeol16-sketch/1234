#!/usr/bin/env python3
"""Continue replacing per-recipient AOI packet serialization with immutable shared frames.

Input: exact prior Java 21 compiled 13-class full source ZIP.
Scope: ten strictly fixed-layout, recipient-invariant visual/status broadcasters.
Never alter direct-send methods, socket FIFO, sender inclusion, CTransport, or PSS.
"""
from __future__ import annotations
import argparse, hashlib, json, pathlib, re, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
INPUT = "CTransport_SharedBroadcast_8Packet_Expanded_FULL_SRC_20261115.zip"
EXPECTED = "e4b8a685638332410ffb1b8f2216a9981202c03f61af2bc25892c9c975a3068a"
OUTPUT = "CTransport_SharedBroadcast_23Packet_FULL_SRC_20261115.zip"
BASE = "src/l1j/server/server/model/network/core/sc/packet/"
SHARED = "l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send"
SHA = lambda data: hashlib.sha256(data).hexdigest()

# WIRE_SIZE values are checked independently against the original send
# implementation's exact primitive write sequence and length.
SPECS = {
    "SC_Door": (9, ("broadcast",)),
    "SC_PinkName": (8, ("broadcast", "broadcastFast")),
    "SC_CharSpeedUpdate": (10, ("broadcast",)),
    "SC_Invis": (8, ("broadcast", "broadcastFast")),
    "SC_SkillHaste": (10, ("broadcast", "broadcastFast")),
    "SC_SkillBrave": (10, ("broadcast", "broadcastFast")),
    "SC_SkillIconAura": (7, ("broadcast", "broadcastFast")),
    "SC_SkillIconShield": (10, ("broadcast", "broadcastFast")),
    "SC_TrueTargetNew": (18, ("broadcastFast",)),
    "SC_HPMeter": (11, ("broadcast",)),
}

def extract_method(text, name, receiver=None):
    pattern = re.compile(r'\bpublic\s+static\s+(?:boolean|void)\s+' + re.escape(name) + r'\s*\(')
    matches = []
    for m in pattern.finditer(text):
        p = m.end()
        level = 1
        while level:
            if p >= len(text): raise ValueError(f"unclosed parameter list {name}")
            level += (text[p] == '(') - (text[p] == ')')
            p += 1
        params = text[m.end():p-1]
        if receiver and not re.search(r'\b'+re.escape(receiver)+r'\b', params):
            continue
        while p < len(text) and text[p].isspace(): p += 1
        if text[p] != '{': raise ValueError("method without opening brace")
        open_brace = p
        p += 1
        depth = 1
        while depth:
            if p >= len(text): raise ValueError("method without closing brace")
            depth += (text[p] == '{') - (text[p] == '}')
            p += 1
        matches.append((m.start(), p, params, text[open_brace+1:p-1], text[m.start():p]))
    if len(matches) != 1:
        raise ValueError(f"Expected exactly one {name}({receiver}), found {len(matches)}")
    return matches[0]

def args(params):
    result = []
    for item in params.split(','):
        item = item.strip()
        if not item: continue
        typ,name = item.rsplit(None,1)
        result.append((typ.strip(),name.strip()))
    return result

def parse_ops(body, class_name, size):
    p = re.compile(r'\bbuffer\.write([CHD])\s*\(')
    writes = []
    for m in p.finditer(body):
        i=m.end()
        begin=i
        depth=1
        while depth:
            if i>=len(body):raise ValueError("unclosed write expression "+class_name)
            depth+=(body[i]=='(')-(body[i]==')')
            i+=1
        expr=body[begin:i-1].strip()
        if not re.match(r'\s*;',body[i:]):
            raise ValueError("unexpected packet writer syntax "+class_name)
        writes.append((m[1],expr))
    if not writes or len(writes)!=len(re.findall(r'\bbuffer\.write',body)):
        raise ValueError("non-primitive writes or absent send serializer "+class_name)
    wire=2+sum({'C':1,'H':2,'D':4}[op] for op,expr in writes)
    if wire!=size:
        raise ValueError(f"{class_name} size mismatch: source writes={wire}, assumed={size}")
    return writes

def encode_expr(op,expr,buf='buffer'):
    name={'C':'put','H':'putShort','D':'putInt'}[op]
    typ={'C':'byte','H':'short','D':'int'}[op]
    return f'{buf}.{name}(({typ})({expr}));'

def generate_wire_method(name, params, writes, size):
    for typ,var in params:
        if typ not in ('int','boolean','long'):
            raise ValueError("Unsupported shared encoder type "+name+":"+typ)
    decl=', '.join(typ+' '+var for typ,var in params)
    rows=[f'    private static byte[] sharedWire({decl}) {{',
          f'        final java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate({size})',
          '                .order(java.nio.ByteOrder.LITTLE_ENDIAN);',
          f'        b.putShort((short) {size});']
    rows += ['        '+encode_expr(op,expr,'b') for op,expr in writes]
    rows += ['        return b.array();','    }','']
    return '\n'.join(rows)

def replace_broadcast(name,s,method,send_params):
    start,end,params,body,full=extract_method(s,method)
    receiver=args(params)
    if not receiver or receiver[0][1]!='anchor':
        raise ValueError("Unexpected AOI anchor "+name+"."+method+": "+str(receiver))
    arg_types = [(t,n) for t,n in receiver[1:]]
    if name=='SC_HPMeter':
        # The original applies a defensive snapshot of HP/MP before its
        # broadcast loop, including catch(Exception); retain that verbatim.
        needle=re.compile(r'for\s*\(\s*L1PcInstance\s+pc\s*:\s*L1World\.getInstance\(\)\.getVisiblePlayer\(anchor\)\s*\)\s*send\s*\(\s*pc\s*,\s*objectId\s*,\s*hpRatio\s*,\s*mpRatio\s*\)\s*;')
        replacement=f'{SHARED}(anchor, sharedWire(objectId, hpRatio, mpRatio), false, -1, null,\n                client -> send(client, objectId, hpRatio, mpRatio));'
        newbody,count=needle.subn(lambda m:replacement,body)
        if count!=1:raise ValueError("HP snapshot-send loop not found")
        updated=s[:start]+full.replace(body,newbody,1)+s[end:]
    else:
        if arg_types!=send_params:
            raise ValueError(f"broadcast arguments mismatch {name}: {arg_types} vs {send_params}")
        if SHARED in body:
            raise ValueError("already shared: "+name)
        if "getVisiblePlayer(" not in body and "borrowVisiblePlayerFast(" not in body:
            raise ValueError("no existing AOI broadcast: "+name)
        fast='true' if method=='broadcastFast' else 'false'
        values=', '.join(n for t,n in arg_types)
        replacement=f'''public static void {method}({params}) {{
        if (anchor == null) return;
        {SHARED}(anchor, sharedWire({values}), {fast}, -1, null,
                client -> send(client, {values}));
    }}'''
        updated=s[:start]+replacement+s[end:]
    return updated

def build_and_modify(name,old):
    size,methods=SPECS[name]
    _,_,send_signature,body,original_sender=extract_method(old,'send','CIOClient client')
    parsed=args(send_signature)
    if parsed[0]!=('CIOClient','client'):
        raise ValueError("direct send method signature changed "+name)
    send_params=parsed[1:]
    operations=parse_ops(body,name,size)
    result=old
    for method in methods:
        result=replace_broadcast(name,result,method,send_params)
    insertion=extract_method(result,'send','CIOClient client')[0]
    wire_method=generate_wire_method(name,send_params,operations,size)
    result=result[:insertion]+wire_method+result[insertion:]
    new_sender=extract_method(result,'send','CIOClient client')[-1]
    if original_sender!=new_sender:
        raise ValueError("Direct packet sender changed "+name)
    if SHARED not in result:
        raise AssertionError("Missing shared path "+name)
    for method in methods:
        _,_,_,newbody,_=extract_method(result,method)
        if SHARED not in newbody:
            raise AssertionError("Old per-recipient method still in use: "+name+" "+method)
    return result,operations,send_params

def make_java_parity(rows):
    header=['import java.nio.*;',
            'import java.util.*;',
            'import java.lang.reflect.*;',
            'import l1j.server.server.Opcodes;',
            'public final class SharedBroadcastWireParity2 {',
            '  static int cases=0;',
            '  static void eq(byte[] a,byte[] b,String n,int i) {',
            '    if (!Arrays.equals(a,b)) throw new AssertionError(n+" case "+i+" actual "+Arrays.toString(a)+" expected "+Arrays.toString(b));',
            '    cases++;',
            '  }',
            '  public static void main(String[] args) throws Exception {']
    for name in SPECS:header.append('    check'+name+'();')
    header+=['    if(cases != 256*'+str(len(SPECS))+')throw new AssertionError("incorrect case count "+cases);',
             '    System.out.println("SHARED_BROADCAST_WIRE_PARITY_2_OK cases="+cases);','  }']
    for name,(operations,fields) in rows.items():
        size=SPECS[name][0]
        clssig=','.join({'int':'int.class','boolean':'boolean.class','long':'long.class'}[typ] for typ,n in fields)
        argsig=','.join(('Boolean.valueOf('+n+')' if t=='boolean' else 'Long.valueOf('+n+')' if t=='long' else 'Integer.valueOf('+n+')') for t,n in fields)
        header+=['  private static void check'+name+'() throws Exception {',
                 '    Class<?> cls=Class.forName("l1j.server.server.model.network.core.sc.packet.'+name+'");',
                 '    Method m=cls.getDeclaredMethod("sharedWire",'+clssig+');',
                 '    m.setAccessible(true);',
                 '    for (int k=0;k<256;k++) {']
        for i,(typ,n) in enumerate(fields):
            if typ=='boolean':
                header.append(f'      boolean {n}=(k&1)==0;')
            elif typ=='long':
                header.append(f'      long {n}=0x0102030405060708L ^ ((long)(k+1)*0x9E3779B97F4A7C15L);')
            else:
                header.append(f'      int {n}=(int)(0x87654321L ^ ((long)(k+1)*0x9E3779B9L) ^ (0x531871L * {i+1}));')
        header += [f'      ByteBuffer expected=ByteBuffer.allocate({size}).order(ByteOrder.LITTLE_ENDIAN);',
                   f'      expected.putShort((short) {size});']
        header += ['      '+encode_expr(op,expr,'expected') for op,expr in operations]
        header += ['      eq((byte[])m.invoke(null,new Object[]{'+argsig+'}),expected.array(),"'+name+'",k);',
                   '    }','  }']
    return '\n'.join(header+['}'])+'\n'

def remaining(files):
    found={}
    for path,blob in files.items():
        if not (path.startswith(BASE) and path.endswith('.java')):
            continue
        s=blob.decode('utf8')
        # This is a conservative approximate inventory, not a claim of
        # exhaustive call-site coverage or correctness of broad migration.
        if re.search(r'\b(?:broadcast|broadcastFast|broadcastM|broadcastWide|broadcastAll|sendAndBroadcast)\s*\(',s):
            if not re.search(r'\bCSharedBroadcast\.send\(',s):
                found[path.split('/')[-1]]=len(s)
    return found

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--input',type=pathlib.Path,default=ROOT/'downloads'/INPUT)
    ap.add_argument('--output',type=pathlib.Path,default=ROOT/'downloads'/OUTPUT)
    ap.add_argument('--work',type=pathlib.Path,default=pathlib.Path('/tmp/mainserver-broadcast-2'))
    a=ap.parse_args()
    source=a.input.read_bytes()
    if SHA(source)!=EXPECTED:
        raise ValueError("Refusing to modify non-authoritative baseline "+SHA(source))
    with zipfile.ZipFile(a.input) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    before=remaining(files)
    rowmap={}
    for name in SPECS:
        key=BASE+name+'.java'
        code=files[key].decode('utf8')
        updated,operations,fields=build_and_modify(name,code)
        files[key]=updated.encode('utf8')
        rowmap[name]=(operations,fields)
    after=remaining(files)
    assert set(SPECS).isdisjoint(after)
    assert len(files)==len(set(files))
    assert sum(name.endswith('.java') for name in files)==1711
    assert SHA(files['CTransport.dll'])=='0801f22fedac95ee29d33c747c5e4019c3b36d79d31615c7d84cc79405258606'
    a.work.mkdir(parents=True,exist_ok=True)
    for name,blob in files.items():
        if name.startswith('src/'):
            path=a.work/name
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(blob)
    test=a.work/'testsrc'/'SharedBroadcastWireParity2.java'
    test.parent.mkdir(parents=True,exist_ok=True)
    test.write_text(make_java_parity(rowmap),encoding='utf8')
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,blob in sorted(files.items()):z.writestr(path,blob)
    with zipfile.ZipFile(a.output) as z:
        assert z.testzip() is None
    audit={
        'baseline_sha256':EXPECTED,
        'output_sha256':SHA(a.output.read_bytes()),
        'new_shared_packet_classes':list(SPECS),
        'new_shared_classes_count':len(SPECS),
        'new_wire_parity_cases':len(SPECS)*256,
        'java_files_changed':len(SPECS),
        'source_java_files':1711,
        'known_packet_classes_with_broadcast_but_no_shared_path_before':sorted(before),
        'known_packet_classes_with_broadcast_but_no_shared_path_after':sorted(after),
        'known_unconverted_after_count':len(after),
        'direct_sender_code_changed':False,
        'shared_buffer_core_changed':False,
        'native_dll_changed':False,
        'full_in_game_windows_soak_verified':False,
    }
    (a.work/'audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps(audit,ensure_ascii=False,indent=2))

if __name__=='__main__':
    main()
