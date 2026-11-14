#!/usr/bin/env python3
"""Safe AOI broadcast wave 6: stat snapshots, protobuf explosion, shop actions.

Latest 44-class complete SRC only. Existing original direct send functions,
network FIFO, shared buffer lifetimes, native transport and gameplay unchanged.
"""
from __future__ import annotations
import argparse, hashlib, json, pathlib, sys, zipfile, re

ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from patches.ctransport_broadcast_expand4_20261115 import expand4 as prev
INPUT="CTransport_SharedBroadcast_44Packet_FULL_SRC_20261115.zip"
EXPECTED="e6d319993b6ebb75b314b74c9fbb70e8149e003e7a7d1b5efc984eb9d0c940b7"
OUTPUT="CTransport_SharedBroadcast_48Packet_FULL_SRC_20261115.zip"
BASE=prev.BASE
SHARED=prev.SHARED
SHA=lambda b:hashlib.sha256(b).hexdigest()
NEW=("SC_Dexup","SC_Strup","SC_ExplosionNoti","SC_DoActionShop")

STATS={
    "SC_Dexup":('''final int dex=actor.getAbility()==null?0:actor.getAbility().getTotalDex();
        final byte[] wire=sharedWire(dex,type,time);''',
'''    private static byte[] sharedWire(int dex,int type,int time){
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(11)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)11).put((byte)Opcodes.S_MAGE_DEXTERITY)
         .putShort((short)time).put((byte)dex).put((byte)type).putInt(0);
        return b.array();
    }

'''),
    "SC_Strup":('''final int totalStr=actor.getAbility().getTotalStr();
        final int weight100=actor.getInventory().getWeight100();
        final byte[] wire=sharedWire(totalStr,weight100,type,time);''',
'''    private static byte[] sharedWire(int totalStr,int weight100,int type,int time){
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(12)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)12).put((byte)Opcodes.S_MAGE_STRENGTH)
         .putShort((short)time).put((byte)totalStr)
         .put((byte)weight100).put((byte)type).putInt(0);
        return b.array();
    }

''')
}
EXPLOSION=r'''    private static void writeVar(java.nio.ByteBuffer b,long value) {
        if(value<0L) value&=0xFFFFFFFFL;
        while((value & ~0x7fL)!=0L) {
            b.put((byte)((value&0x7fL)|0x80L));value>>>=7;
        }
        b.put((byte)(value&0x7fL));
    }
    private static byte[] sharedWire(int objectId,long mills) {
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(29)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.position(2);
        b.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)0x0332);
        b.put((byte)0x08);writeVar(b,objectId);
        b.put((byte)0x10);writeVar(b,mills);
        b.putShort((short)0);
        final int size=b.position();
        b.putShort(0,(short)size);
        return java.util.Arrays.copyOf(b.array(),size);
    }

'''

SHOP=r'''    private static byte[] sharedWire(int id,int action,byte[] message) {
        final int size=8+(message==null?0:message.length);
        if(size>16384)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_ACTION)
         .putInt(id).put((byte)action);
        if(message!=null)b.put(message);
        return b.array();
    }
    private static byte[] sharedWire(int id,int action,String message) {
        final byte[] data=(message==null?"":message)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=9+data.length;
        if(size>16384)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_ACTION)
         .putInt(id).put((byte)action).put(data).put((byte)0);
        return b.array();
    }

'''

def inject(s,wire):
    pos=s.rfind("}")
    assert pos>=0
    return s[:pos]+"\n"+wire+s[pos:]

def stats(name,s):
    params=STATS[name]
    out=s
    for method_name in ("broadcast","broadcastFast"):
        old=prev.method(out,"public",method_name)
        start,end,signature,body,full=old
        if "L1PcInstance actor" not in signature or "L1Character anchor" not in signature:
            raise ValueError(name+" unexpected broadcast signature "+signature)
        if method_name=="broadcastFast":assert "borrowVisiblePlayerFast" in body
        else:assert "getVisiblePlayer" in body
        # Original actor-dependent send remains unchanged for every recipient
        # if the actor's required state is absent.
        invalid=('actor == null' if name=="SC_Dexup"
                 else 'actor == null || actor.getAbility()==null || actor.getInventory()==null')
        replacement=f'''public static void {method_name}({signature}) {{
        if(anchor==null)return;
        if({invalid}) {{
            {body.strip()}
            return;
        }}
        {params[0]}
        {SHARED}(anchor,wire,{str(method_name=="broadcastFast").lower()},-1,null,
                client -> send(client,actor,type,time));
    }}'''
        out=prev.replace(out,old,replacement)
    return inject(out,params[1])

def explosion(s):
    old=prev.method(s,"public","broadcast")
    assert "getVisiblePlayer" in old[3]
    start,end,signature,body,full=old
    new=f'''public static void broadcast({signature}) {{
        if(anchor==null)return;
        {SHARED}(anchor,sharedWire(objectId,mills),false,-1,null,
                client -> send(client,objectId,mills));
    }}'''
    out=prev.replace(s,old,new)
    return inject(out,EXPLOSION)

def shop(s):
    out=s
    for type_name in ("byte[]","String"):
        def match(p):
            return "L1Character anchor" in p and type_name+" message" in p
        old=prev.method(out,"public","broadcast",match)
        start,end,signature,body,full=old
        assert "getVisiblePlayer" in body
        new=f'''public static void broadcast({signature}) {{
        if(anchor==null)return;
        final byte[] wire=sharedWire(id,action,message);
        if(wire==null){{
            {body.strip()}
            return;
        }}
        {SHARED}(anchor,wire,false,-1,null,
                client -> send(client,id,action,message));
    }}'''
        out=prev.replace(out,old,new)
    return inject(out,SHOP)

TEST=r'''import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.reflect.*;
import l1j.server.server.Opcodes;
public final class SharedBroadcastWireParity6 {
 static int cases;
 static byte[] call(String name,String method,Class<?>[] types,Object... values)throws Exception{
   Class<?> cl=Class.forName("l1j.server.server.model.network.core.sc.packet."+name);
   Method m=cl.getDeclaredMethod(method,types);m.setAccessible(true);
   return (byte[])m.invoke(null,values);
 }
 static void check(byte[] raw,ByteBuffer expected,String name,int k) {
   if(!Arrays.equals(raw,expected.array()))
     throw new AssertionError(name+" i="+k+" expected="+Arrays.toString(expected.array())+" actual="+Arrays.toString(raw));
   cases++;
 }
 static ByteBuffer b(int len){ByteBuffer b=ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);b.putShort((short)len);return b;}
 static void writeVar(ByteBuffer b,long v) {
   if(v<0L)v&=0xffffffffL;
   while((v&~127L)!=0L){b.put((byte)((v&127L)|128L));v>>>=7;}
   b.put((byte)v);
 }
 public static void main(String[] args)throws Exception {
   for(int i=0;i<256;i++){
     int str=i*457-211,weight=i*81-93,type=i*19-38,time=i*23-1000;
     ByteBuffer dex=b(11);dex.put((byte)Opcodes.S_MAGE_DEXTERITY).putShort((short)time).put((byte)str).put((byte)type).putInt(0);
     check(call("SC_Dexup","sharedWire",new Class<?>[]{int.class,int.class,int.class},str,type,time),dex,"dexup",i);
     ByteBuffer strength=b(12);strength.put((byte)Opcodes.S_MAGE_STRENGTH).putShort((short)time).put((byte)str).put((byte)weight).put((byte)type).putInt(0);
     check(call("SC_Strup","sharedWire",new Class<?>[]{int.class,int.class,int.class,int.class},str,weight,type,time),strength,"strup",i);
     int objectId=(int)(0x12345678L ^((i+1L)*0x9e3779b9L));
     long mills=(i%3==0)?-1L:(i%3==1?(((long)i)<<36)+128L:i*10007L);
     ByteBuffer exploding=ByteBuffer.allocate(29).order(ByteOrder.LITTLE_ENDIAN);
     exploding.position(2);exploding.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)0x0332);
     exploding.put((byte)0x08);writeVar(exploding,objectId);
     exploding.put((byte)0x10);writeVar(exploding,mills);
     exploding.putShort((short)0);
     int size=exploding.position();exploding.putShort(0,(short)size);
     ByteBuffer expected=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
     expected.put(exploding.array(),0,size);
     check(call("SC_ExplosionNoti","sharedWire",new Class<?>[]{int.class,long.class},objectId,mills),expected,"explosion",i);
     byte[] msg=new byte[i%30];for(int j=0;j<msg.length;j++)msg[j]=(byte)(i^j);
     ByteBuffer bytes=b(8+msg.length);bytes.put((byte)Opcodes.S_ACTION).putInt(objectId).put((byte)type).put(msg);
     check(call("SC_DoActionShop","sharedWire",new Class<?>[]{int.class,int.class,byte[].class},objectId,type,msg),bytes,"shopbytes",i);
   }
   for(String text:new String[]{"","a","한글","리니지","漢字","😀","\u0000text"}) {
     byte[] data=text.getBytes(Charset.forName("MS949"));
     ByteBuffer e=b(9+data.length);e.put((byte)Opcodes.S_ACTION).putInt(0x12345678).put((byte)3).put(data).put((byte)0);
     check(call("SC_DoActionShop","sharedWire",new Class<?>[]{int.class,int.class,String.class},0x12345678,3,text),e,"shopstring",text.length());
   }
   byte[] huge=new byte[17000];
   if(call("SC_DoActionShop","sharedWire",new Class<?>[]{int.class,int.class,byte[].class},1,1,huge)!=null)
      throw new AssertionError("oversize action must use direct fallback");
   System.out.println("SHARED_BROADCAST_WIRE_PARITY_6_OK checks="+cases);
 }
}
'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--input',type=pathlib.Path,default=ROOT/'downloads'/INPUT)
    p.add_argument('--output',type=pathlib.Path,default=ROOT/'downloads'/OUTPUT)
    p.add_argument('--work',type=pathlib.Path,default=pathlib.Path('/tmp/broadcast-wave6'))
    a=p.parse_args()
    raw=a.input.read_bytes()
    if SHA(raw)!=EXPECTED:raise ValueError("Wrong source baseline "+SHA(raw))
    with zipfile.ZipFile(a.input) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    for name in NEW:
        path=BASE+name+'.java'
        original=files[path].decode('utf8')
        if name in STATS:new=stats(name,original)
        elif name=="SC_ExplosionNoti":new=explosion(original)
        else:new=shop(original)
        if new==original:raise ValueError("No changes "+name)
        files[path]=new.encode('utf8')
    after={}
    for name,blob in files.items():
        if not (name.startswith(BASE) and name.endswith('.java')):continue
        text=blob.decode('utf8')
        if re.search(r'\b(?:broadcast|broadcastFast|broadcastM|broadcastWide|broadcastAll|broadcastClan|sendAndBroadcast)\s*\(',text):
            if not re.search(r'\bCSharedBroadcast\.send(?:All)?\s*\(',text):
                after[name.split("/")[-1]]=len(text)
    assert not any(n+'.java' in after for n in NEW)
    assert sum(n.endswith('.java') for n in files)==1711
    assert SHA(files['CTransport.dll'])=="0801f22fedac95ee29d33c747c5e4019c3b36d79d31615c7d84cc79405258606"
    a.work.mkdir(parents=True,exist_ok=True)
    for name,blob in files.items():
        if name.startswith('src/'):
            dst=a.work/name;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(blob)
    tst=a.work/'testsrc'/'SharedBroadcastWireParity6.java'
    tst.parent.mkdir(parents=True,exist_ok=True);tst.write_text(TEST)
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,blob in sorted(files.items()):z.writestr(path,blob)
    with zipfile.ZipFile(a.output) as z:assert z.testzip() is None
    report={'baseline_sha256':EXPECTED,'output_sha256':SHA(a.output.read_bytes()),
            'new_classes':list(NEW),'new_class_count':len(NEW),
            'shared_packet_class_total':44+len(NEW),
            'java_file_count':1711,'dll_unchanged':True,
            'remaining_legacy_packet_broadcast_classes':sorted(after),
            'remaining_count':len(after),
            'full_windows_runtime_tested':False}
    (a.work/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=="__main__":main()
