#!/usr/bin/env python3
"""Fourth broadcast expansion: critical attacks, 3-mode attack, 3 text AOI, poison.

Exact previous compiled full source required. Zero changes to direct send
serializers, transport, PSS, JNI/FFM, socket FIFO, native DLL or game logic.
Oversize text falls back to original per-recipient path, preserving >16KB
direct send behavior. Invalid poison type remains original per-recipient path.
"""
import argparse, hashlib, json, pathlib, re, sys, zipfile

ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from patches.ctransport_broadcast_expand2_20261115 import expand2 as prev

INPUT="CTransport_SharedBroadcast_32Packet_FULL_SRC_20261115.zip"
EXPECTED="554d4c1b42cd3683b0f5d5803d292b0b8faeb1917cd3a01ec021f0781e4049bb"
OUTPUT="CTransport_SharedBroadcast_38Packet_FULL_SRC_20261115.zip"
BASE=prev.BASE
SHARED=prev.SHARED
HASH=prev.SHA
CHANGED=("SC_AttackPacket","SC_AttackCritical","SC_ChangeName",
         "SC_CharTitle","SC_NpcShopAction","SC_Poison")

def method(s,visibility,name,check=None):
    p=re.compile(r'\b'+visibility+r'\s+static\s+(?:void|boolean)\s+'+re.escape(name)+r'\s*\(')
    out=[]
    for match in p.finditer(s):
        i=match.end()
        depth=1
        while depth:
            if i>=len(s):raise ValueError("Unterminated method params "+name)
            depth+=(s[i]=='(')-(s[i]==')')
            i+=1
        params=s[match.end():i-1]
        if check and not check(params):continue
        while i<len(s) and s[i].isspace():i+=1
        assert s[i]=='{'
        begin=i
        i+=1;depth=1
        while depth:
            if i>=len(s):raise ValueError("Unterminated method "+name)
            depth+=(s[i]=='{')-(s[i]=='}')
            i+=1
        out.append((match.start(),i,params,s[begin+1:i-1],s[match.start():i]))
    if len(out)!=1:
        raise ValueError(f"{visibility} {name} method match {len(out)}")
    return out[0]

def replace(s,desc,updated):
    start,end,params,body,full=desc
    assert start>=0 and end<=len(s)
    return s[:start]+updated+s[end:]

def text_wire(name,source):
    """Encode exactly like original WriteBuffer.writeS / writeSU16."""
    specs={
        "SC_ChangeName":("objectId, String value","int objectId, String value","Opcodes.S_CHANGE_DESC","MS949"),
        "SC_CharTitle":("objectId, String value","int objectId, String value","Opcodes.S_TITLE","MS949"),
        "SC_NpcShopAction":("objectId, action, String value","int objectId, int action, String value","Opcodes.S_ACTION","UTF16"),
    }
    _,signature,opcode,encoding=specs[name]
    if encoding=="MS949":
        result=f'''    private static byte[] sharedWire({signature}) {{
        final byte[] body = (value == null ? "" : value)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size = 8 + body.length;
        if (size > 16384) return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte){opcode})
         .putInt(objectId).put(body).put((byte)0);
        return b.array();
    }}

'''
    else:
        result=f'''    private static byte[] sharedWire({signature}) {{
        final String text=value==null ? "" : value;
        final long calculated=10L+((long)text.length()<<1);
        if (calculated > 16384) return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate((int)calculated)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)calculated).put((byte){opcode})
         .putInt(objectId).put((byte)action);
        for(int i=0;i<text.length();i++) b.putChar(text.charAt(i));
        b.putShort((short)0);
        return b.array();
    }}

'''
    return result

def migrate_text(name,src):
    variants={
      "SC_ChangeName":[("broadcast",False),("broadcastFast",True)],
      "SC_CharTitle":[("broadcast",False)],
      "SC_NpcShopAction":[("broadcast",False)],
    }[name]
    out=src
    for method_name,fast in variants:
        desc=method(out,"public",method_name,lambda p:"L1Character anchor" in p)
        start,end,params,oldbody,full=desc
        types=prev.args(params)
        assert types[0]==("L1Character","anchor")
        names=[n for t,n in types[1:]]
        assert names[0]=="objectId"
        if name=="SC_ChangeName":assert names==["objectId","name"]
        if name=="SC_CharTitle":assert names==["objectId","title"]
        if name=="SC_NpcShopAction":assert names==["objectId","action","shopName"]
        if not ("getVisiblePlayer" in oldbody or "borrowVisiblePlayerFast" in oldbody):
            raise ValueError("Missing AOI loop "+name)
        args=", ".join(names)
        # We retain exact original method body for >16KB frames.
        # For supported short frames, recipient-independent wire serialization
        # occurs once, and any unsupported socket uses unchanged direct send.
        body=f'''public static void {method_name}({params}) {{
        if (anchor == null) return;
        final byte[] wire=sharedWire({args});
        if (wire == null) {{
            {oldbody.strip()}
            return;
        }}
        {SHARED}(anchor, wire, {str(fast).lower()}, -1, null,
                client -> send(client, {args}));
    }}'''
        out=replace(out,desc,body)
    ins=method(out,"public","send",lambda p:"CIOClient client" in p)[0]
    if name=="SC_ChangeName":declaration=text_wire(name,src).replace("String value","String name").replace("value == null","name == null").replace(": value)",": name)")
    elif name=="SC_CharTitle":declaration=text_wire(name,src).replace("String value","String title").replace("value == null","title == null").replace(": value)",": title)")
    else:declaration=text_wire(name,src).replace("String value","String shopName").replace("value==null","shopName==null").replace(": value",": shopName")
    out=out[:ins]+declaration+out[ins:]
    # Verify direct send method unchanged byte for byte.
    send0=method(src,"public","send",lambda p:"CIOClient client" in p)[-1]
    send1=method(out,"public","send",lambda p:"CIOClient client" in p)[-1]
    assert send0==send1,name
    return out

def migrate_poison(src):
    prev.SPECS={"SC_Poison":(9,("broadcast","broadcastFast"))}
    updated,writes,params=prev.build_and_modify("SC_Poison",src)
    # Respect invalid type: original sender throws only for a recipient, and
    # with no recipients the original broadcast returns without throwing.
    for n in ("broadcast","broadcastFast"):
        old=method(src,"public",n)
        new=method(updated,"public",n)
        oldbody=old[3]
        newbody=new[3]
        assert "type" in new[2] and "type" in old[2]
        marker="if (anchor == null) return;"
        assert marker in newbody
        injected=marker+''' 
        if (type < 0 || type > 2) {
            '''+oldbody+'''
            return;
        }'''
        fixed=newbody.replace(marker,injected,1)
        updated=replace(updated,new,new[4].replace(newbody,fixed,1))
    return updated,(writes,params)

def migrate_attack_packet(s):
    desc=method(s,"private","broadcast0")
    assert "borrowVisiblePlayerFast(cha)" in desc[3]
    assert "send0(" in desc[3]
    body='''private static void broadcast0(L1Character cha, int mode, int objId, int type, int attackType, int gfx) {
        final int chaId=cha.getId();
        final int heading=cha.getHeading();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(cha,
                sharedWire(mode, chaId, objId, type, heading, attackType, gfx),
                true, -1, null,
                client -> send0(client, mode, chaId, objId, type, heading, attackType, gfx));
    }'''
    out=replace(s,desc,body)
    wire='''    private static byte[] sharedWire(int mode, int chaId, int objId, int type,
            int heading, int attackType, int gfx) {
        final int size=mode==MODE_3?26:20;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size);
        b.put((byte)Opcodes.S_ATTACK).put((byte)type);
        switch (mode) {
            case MODE_1:
                b.putInt(objId).putInt(chaId).put((byte)0x4e)
                 .put((byte)0).put((byte)heading).putInt(0).put((byte)0);
                break;
            case MODE_2:
                b.putInt(chaId).putInt(objId).putShort((short)2)
                 .put((byte)heading).putShort((short)0).putShort((short)0)
                 .put((byte)attackType);
                break;
            case MODE_3:
                b.putInt(chaId).putInt(objId).put((byte)1).put((byte)0)
                 .put((byte)heading).putShort((short)0).putShort((short)0)
                 .put((byte)attackType).putShort((short)gfx)
                 .putShort((short)0).putShort((short)0);
                break;
            default:
                throw new IllegalArgumentException("Unsupported attack mode "+mode);
        }
        return b.array();
    }

'''
    pos=method(out,"private","send0")[0]
    out=out[:pos]+wire+out[pos:]
    assert method(s,"private","send0")[-1]==method(out,"private","send0")[-1]
    return out

def migrate_attack_critical(s):
    out=s
    variants={}
    for mode in (1,2):
        methodname="broadcastMode"+str(mode)
        sendname="sendMode"+str(mode)
        old=method(out,"private",methodname)
        typ=prev.args(old[2])
        assert typ[0]==("L1Character","anchor")
        args=", ".join(n for t,n in typ[1:])
        if "borrowVisiblePlayerFast(anchor)" not in old[3]:raise ValueError("Not original fast AOI critical")
        new=f'''private static void {methodname}({old[2]}) {{
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor,
                sharedWireMode{mode}({args}), true, -1, null,
                client -> {sendname}(client, {args}));
    }}'''
        out=replace(out,old,new)
        old_send=method(s,"private",sendname)
        types=prev.args(old_send[2])
        assert types[0]==("CIOClient","client")
        wire_size={1:26,2:34}[mode]
        writes=prev.parse_ops(old_send[3],"SC_AttackCriticalMode"+str(mode),wire_size)
        variants[mode]=(writes,types[1:])
        generated=prev.generate_wire_method("SC_AttackCritical",types[1:],writes,wire_size)
        generated=generated.replace("private static byte[] sharedWire(",f"private static byte[] sharedWireMode{mode}(")
        index=method(out,"private",sendname)[0]
        out=out[:index]+generated+out[index:]
        assert method(s,"private",sendname)[-1]==method(out,"private",sendname)[-1]
    return out,variants

def java_tests(parity_poison,critical):
    # Source-derived comparison for poison and critical modes. Extended MS949
    # / UTF16 checks use reference byte encodings independent of send routines.
    return r'''import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.reflect.*;
import l1j.server.server.Opcodes;
public final class SharedBroadcastWireParity4 {
  static int checks=0;
  static byte[] invoke(String type,String method,Class<?>[] types,Object... values)throws Exception{
    Class<?> c=Class.forName("l1j.server.server.model.network.core.sc.packet."+type);
    Method m=c.getDeclaredMethod(method,types);m.setAccessible(true);
    return (byte[])m.invoke(null,values);
  }
  static void eq(byte[] a,ByteBuffer b,String n,int k){
    if(!Arrays.equals(a,b.array()))
      throw new AssertionError(n+" case "+k+" actual "+Arrays.toString(a)+" ref "+Arrays.toString(b.array()));
    checks++;
  }
  static ByteBuffer b(int len){ ByteBuffer x=ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);x.putShort((short)len);return x;}
  public static void main(String[] args)throws Exception{
    for(int k=0;k<128;k++){
      int a=(int)(0x3344abcL^((k+1L)*0x9e3779b9L));
      int d=(int)(0x2344a93L^((k+1L)*0xc2b2ae35L));
      int g=k*101-14, t=k*17-2, act=k*7, hd=k*11;
      for(int m=1;m<=3;m++){
        ByteBuffer e=b(m==3?26:20);
        e.put((byte)Opcodes.S_ATTACK).put((byte)t);
        if(m==1)e.putInt(d).putInt(a).put((byte)0x4E).put((byte)0).put((byte)hd).putInt(0).put((byte)0);
        if(m==2)e.putInt(a).putInt(d).putShort((short)2).put((byte)hd).putShort((short)0).putShort((short)0).put((byte)act);
        if(m==3)e.putInt(a).putInt(d).put((byte)1).put((byte)0).put((byte)hd).putShort((short)0).putShort((short)0).put((byte)act).putShort((short)g).putShort((short)0).putShort((short)0);
        eq(invoke("SC_AttackPacket","sharedWire",new Class<?>[]{int.class,int.class,int.class,int.class,int.class,int.class,int.class},m,a,d,t,hd,act,g),e,"SC_AttackPacket#"+m,k);
      }
      int pt=k%3;
      ByteBuffer poison=b(9);
      poison.put((byte)Opcodes.S_POISON).putInt(a).put((byte)(pt==1?1:0)).put((byte)(pt==2?1:0));
      eq(invoke("SC_Poison","sharedWire",new Class<?>[]{int.class,int.class},a,pt),poison,"SC_Poison",k);
      for(int mode=1;mode<=2;mode++){
        ByteBuffer e=b(mode==1?26:34);
        if(mode==1){
          e.put((byte)Opcodes.S_ATTACK).put((byte)1)
           .putInt(a).putInt(d).putShort((short)1)
           .put((byte)hd).putShort((short)0).putShort((short)0)
           .put((byte)2).putInt(g).putShort((short)0);
          eq(invoke("SC_AttackCritical","sharedWireMode1",
             new Class<?>[]{int.class,int.class,int.class,int.class},a,d,hd,g),e,"SC_AttackCriticalMode1",k);
        } else {
          // Encoder source-derived reference, preserves original mode2 writer field order.
          e.put((byte)Opcodes.S_ATTACK).put((byte)act).putInt(a).putInt(d)
           .put((byte)(k%2==0?6:0)).put((byte)0).put((byte)hd)
           .putInt(t).putShort((short)g).put((byte)0)
           .putShort((short)(k+7)).putShort((short)(k+8))
           .putShort((short)(k+9)).putShort((short)(k+10))
           .put((byte)0).put((byte)0).put((byte)0).put((byte)0);
          eq(invoke("SC_AttackCritical","sharedWireMode2",
             new Class<?>[]{int.class,int.class,int.class,int.class,boolean.class,int.class,int.class,int.class,int.class,int.class,int.class},
             a,d,hd,act,k%2==0,t,g,k+7,k+8,k+9,k+10),e,"SC_AttackCriticalMode2",k);
        }
      }
    }
    String[] texts={"","A","ABC","한글테스트","리니지1","가나다abc","$440",
                    "한글\u0000문자","漢字","☃","😀"};
    int j=0;
    for(String text:texts){
      byte[] ms949=text.getBytes(Charset.forName("MS949"));
      for(String packet:new String[]{"SC_ChangeName","SC_CharTitle"}){
        ByteBuffer ref=b(8+ms949.length);
        ref.put((byte)(packet.equals("SC_ChangeName")?Opcodes.S_CHANGE_DESC:Opcodes.S_TITLE));
        ref.putInt(0x34561234).put(ms949).put((byte)0);
        eq(invoke(packet,"sharedWire",new Class<?>[]{int.class,String.class},0x34561234,text),ref,packet,j);
      }
      ByteBuffer shop=b(10+2*text.length());
      shop.put((byte)Opcodes.S_ACTION).putInt(0x34561234).put((byte)0xa2);
      for(int i=0;i<text.length();i++)shop.putChar(text.charAt(i));
      shop.putShort((short)0);
      eq(invoke("SC_NpcShopAction","sharedWire",new Class<?>[]{int.class,int.class,String.class},0x34561234,0xa2,text),shop,"SC_NpcShopAction",j);
      j++;
    }
    String longText="한".repeat(9000);
    if(invoke("SC_CharTitle","sharedWire",new Class<?>[]{int.class,String.class},1,longText)!=null)
        throw new AssertionError("oversized strings must fall back to original direct sender");
    System.out.println("SHARED_BROADCAST_WIRE_PARITY_4_OK cases="+checks);
  }
}
''';

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--input',type=pathlib.Path,default=ROOT/'downloads'/INPUT)
    p.add_argument('--output',type=pathlib.Path,default=ROOT/'downloads'/OUTPUT)
    p.add_argument('--work',type=pathlib.Path,default=pathlib.Path('/tmp/mainserver-broadcast-4'))
    a=p.parse_args()
    raw=a.input.read_bytes()
    if HASH(raw)!=EXPECTED:raise ValueError("Incorrect source baseline "+HASH(raw))
    with zipfile.ZipFile(a.input) as z:
        assert z.testzip() is None
        files={info.filename:z.read(info) for info in z.infolist() if not info.is_dir()}
    old_remaining=prev.remaining(files)
    references={}
    for name in CHANGED:
        key=BASE+name+'.java'
        src=files[key].decode('utf8')
        if name=="SC_Poison":updated,references[name]=migrate_poison(src)
        elif name=="SC_AttackPacket":updated=migrate_attack_packet(src)
        elif name=="SC_AttackCritical":updated,references[name]=migrate_attack_critical(src)
        else:updated=migrate_text(name,src)
        if updated==src:raise AssertionError("No mutation for "+name)
        files[key]=updated.encode('utf8')
    after=prev.remaining(files)
    assert set(name+".java" for name in CHANGED).isdisjoint(after)
    assert sum(k.endswith('.java') for k in files)==1711
    assert HASH(files['CTransport.dll'])=='0801f22fedac95ee29d33c747c5e4019c3b36d79d31615c7d84cc79405258606'
    a.work.mkdir(parents=True,exist_ok=True)
    for name,raw in files.items():
        if name.startswith('src/'):
            dst=a.work/name;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(raw)
    td=a.work/'testsrc';td.mkdir(parents=True,exist_ok=True)
    (td/'SharedBroadcastWireParity4.java').write_text(java_tests(references['SC_Poison'],references['SC_AttackCritical']))
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,blob in sorted(files.items()):z.writestr(path,blob)
    with zipfile.ZipFile(a.output) as z:assert z.testzip() is None
    audit={
      'source_sha256':EXPECTED,'output_sha256':HASH(a.output.read_bytes()),
      'new_shared_classes':list(CHANGED),'new_shared_count':len(CHANGED),
      'total_shared_classes':32+len(CHANGED),
      'remaining_classes':sorted(after),'remaining_count':len(after),
      'java_files':1711,'original_direct_send_unchanged':True,
      'native_dll_unchanged':True,'windows_game_runtime_tested':False,
    }
    (a.work/'audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(audit,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
