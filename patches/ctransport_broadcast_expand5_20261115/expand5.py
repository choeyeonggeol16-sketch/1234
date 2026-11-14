#!/usr/bin/env python3
"""Share identical server-wide/pledge messages without altering recipient policy.

Full-source exact input SHA required. Reuse the existing CAsyncSendConstBuffer,
per-socket FIFO and fallback sender. Add sendAll to existing CSharedBroadcast,
without creating workers/queues/classes. Large (>16 KiB) wire messages take
their original path, preserving legacy oversized delivery.
"""
from __future__ import annotations
import argparse, hashlib, json, pathlib, re, zipfile, sys
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from patches.ctransport_broadcast_expand4_20261115 import expand4 as last

INPUT="CTransport_SharedBroadcast_38Packet_FULL_SRC_20261115.zip"
EXPECTED="1a184bfc2957df28d7952d10fe7b45b38e1ccfecaf79ec09599816a34251385a"
OUTPUT="CTransport_SharedBroadcast_44Packet_FULL_SRC_20261115.zip"
BASE=last.BASE
SHA=lambda b:hashlib.sha256(b).hexdigest()
BROADCAST="src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java"
CLASSES=("SC_Weather","SC_ServerMessage","SC_SystemMessage",
         "SC_TopMessage","SC_MessageYN","SC_War")
PKG="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

SEND_ALL=r'''
    /** One immutable frame for every online player, including the sender.
     * Unsupported connections use their unchanged individual serializer.
     */
    public static void sendAll(byte[] wire, Consumer<L1PcInstance> legacy) {
        try (var shared = new CAsyncSendConstBuffer(wire, Integer.MIN_VALUE,
                Integer.MAX_VALUE)) {
            for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
                if (pc == null) continue;
                final CIOClient client = pc.getNetConnection();
                if (client != null && client.isConnected()
                        && !shared.apply(client.getConnection(), pc.getId())) {
                    legacy.accept(pc);
                }
            }
        }
    }
'''

WIRES={
"SC_Weather":'''
    private static byte[] sharedWire(int weather) {
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)4).put((byte)Opcodes.S_WEATHER).put((byte)weather);
        return b.array();
    }
''',
"SC_ServerMessage":'''
    private static byte[] sharedWire(int type) {
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(6)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)6).put((byte)Opcodes.S_MESSAGE_CODE);
        b.putShort((short)type).put((byte)0);
        return b.array();
    }
''',
"SC_SystemMessage":'''
    private static byte[] sharedWire(String message) {
        final byte[] text=(message==null ? "" : message)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=5+text.length;
        if(size>16384)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_MESSAGE)
         .put((byte)0x09).put(text).put((byte)0);
        return b.array();
    }
''',
"SC_TopMessage":'''
    private static byte[] sharedWire(String msg) {
        final byte[] text=(msg==null ? "" : msg)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=8+text.length;
        if(size>16384)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)121).put((byte)84).put((byte)2)
         .put(text).put((byte)0).put((byte)0).put((byte)0);
        return b.array();
    }
''',
"SC_MessageYN":'''
    private static byte[] sharedWire(int type, String message) {
        final byte[] text=(message==null ? "" : message)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=12+text.length;
        if(size>16384)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_ASK).putShort((short)0)
         .putInt(0).putShort((short)type).put(text).put((byte)0);
        return b.array();
    }
''',
"SC_War":'''
    private static byte[] sharedWire(int type, String clan1, String clan2) {
        final java.nio.charset.Charset ms949=java.nio.charset.Charset.forName("MS949");
        final byte[] a=(clan1==null ? "" : clan1).getBytes(ms949);
        final byte[] b=(clan2==null ? "" : clan2).getBytes(ms949);
        final long size=6L+a.length+b.length;
        if(size>16384)return null;
        final java.nio.ByteBuffer out=java.nio.ByteBuffer.allocate((int)size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        out.putShort((short)size).put((byte)Opcodes.S_WAR).put((byte)type)
           .put(a).put((byte)0).put(b).put((byte)0);
        return out.array();
    }
''',
}

def replace_global(name,s):
    source=last.method(s,"public", "broadcastAll" if name in ("SC_TopMessage","SC_Weather","SC_War") else "broadcast")
    start,end,params,body,full=source
    assert "getAllPlayers()" in body,name
    types=last.prev.args(params)
    names=[n for t,n in types]
    args=", ".join(names)
    legacy="pc -> send(pc, "+args+")"
    guard="final byte[] wire=sharedWire("+args+");"
    fallback="" if name in ("SC_Weather","SC_ServerMessage") else f'''if(wire==null){{
            {body.strip()}
            return;
        }}'''
    output=f'''public static void {"broadcastAll" if name in ("SC_TopMessage","SC_Weather","SC_War") else "broadcast"}({params}) {{
        {guard}
        {fallback}
        {PKG}.sendAll(wire, {legacy});
    }}'''
    s=last.replace(s,source,output)
    if name=="SC_War":
        v=last.method(s,"public","broadcastClan")
        c0,c1,cp,cb,cf=v
        assert "createOnlineMembers()" in cb and "member.player" in cb
        copy=f'''public static void broadcastClan({cp}) {{
        if (clan == null) return;
        final byte[] wire=sharedWire(type, clan1, clan2);
        if (wire==null) {{
            {cb.strip()}
            return;
        }}
        try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                wire, Integer.MIN_VALUE, Integer.MAX_VALUE)) {{
            clan.createOnlineMembers().forEach(member -> {{
                final L1PcInstance player=member.player;
                if(player==null)return;
                final CIOClient client=player.getNetConnection();
                if(client!=null && client.isConnected()
                        && !shared.apply(client.getConnection(), player.getId()))
                    send(player, type, clan1, clan2);
            }});
        }}
    }}'''
        s=last.replace(s,v,copy)
    assert "private static byte[] sharedWire" not in s
    ins=last.method(s,"public","send",None)[0]
    s=s[:ins]+WIRES[name]+"\n"+s[ins:]
    return s

JAVA_TEST=r'''import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.reflect.*;
import l1j.server.server.Opcodes;
public class SharedGlobalBroadcastParity {
 static int tests=0;
 static byte[] encode(String s) {
   return (s==null?"":s).getBytes(Charset.forName("MS949"));
 }
 static ByteBuffer b(int len){ByteBuffer x=ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);x.putShort((short)len);return x;}
 static byte[] call(String name,Class<?>[] types,Object...args)throws Exception{
   Class<?> c=Class.forName("l1j.server.server.model.network.core.sc.packet."+name);
   Method m=c.getDeclaredMethod("sharedWire",types);m.setAccessible(true);
   return (byte[])m.invoke(null,args);
 }
 static void eq(byte[] raw,ByteBuffer e,String n,int i) {
   if(!Arrays.equals(raw,e.array()))throw new AssertionError(n+" case="+i+" actual="+Arrays.toString(raw)+" ref="+Arrays.toString(e.array()));
   tests++;
 }
 public static void main(String[] args)throws Exception{
   for(int i=0;i<256;i++) {
     int k=(int)(0x7f135601L ^ ((i+1L)*0x9e3779b9L));
     ByteBuffer weather=b(4);weather.put((byte)Opcodes.S_WEATHER).put((byte)k);
     eq(call("SC_Weather",new Class<?>[]{int.class},k),weather,"weather",i);
     ByteBuffer message=b(6);message.put((byte)Opcodes.S_MESSAGE_CODE).putShort((short)k).put((byte)0);
     eq(call("SC_ServerMessage",new Class<?>[]{int.class},k),message,"servermessage",i);
   }
   String[] values={"","a","abc","리니지","거래 공지","가나다","영어123","漢字","😀","가\u0000나다"};
   for(int i=0;i<values.length;i++){
     String str=values[i];byte[] txt=encode(str);
     ByteBuffer system=b(5+txt.length);
     system.put((byte)Opcodes.S_MESSAGE).put((byte)0x09).put(txt).put((byte)0);
     eq(call("SC_SystemMessage",new Class<?>[]{String.class},str),system,"system",i);
     ByteBuffer top=b(8+txt.length);top.put((byte)121).put((byte)84).put((byte)2).put(txt).put((byte)0).put((byte)0).put((byte)0);
     eq(call("SC_TopMessage",new Class<?>[]{String.class},str),top,"top",i);
     ByteBuffer yn=b(12+txt.length);yn.put((byte)Opcodes.S_ASK).putShort((short)0).putInt(0).putShort((short)(0xabc0+i)).put(txt).put((byte)0);
     eq(call("SC_MessageYN",new Class<?>[]{int.class,String.class},0xabc0+i,str),yn,"yn",i);
     byte[] other=encode(values[values.length-1-i]);
     ByteBuffer war=b(6+txt.length+other.length);
     war.put((byte)Opcodes.S_WAR).put((byte)i).put(txt).put((byte)0).put(other).put((byte)0);
     eq(call("SC_War",new Class<?>[]{int.class,String.class,String.class},i,str,values[values.length-1-i]),war,"war",i);
   }
   String huge="가".repeat(9000);
   for(String type:new String[]{"SC_SystemMessage","SC_TopMessage"}) {
     if(call(type,new Class<?>[]{String.class},huge)!=null)throw new AssertionError("Oversized "+type);
   }
   if(call("SC_War",new Class<?>[]{int.class,String.class,String.class},1,huge,"")!=null)
     throw new AssertionError("Oversized war");
   System.out.println("SHARED_GLOBAL_BROADCAST_PARITY_OK checks="+tests);
 }
}
'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--input",type=pathlib.Path,default=ROOT/"downloads"/INPUT)
    p.add_argument("--output",type=pathlib.Path,default=ROOT/"downloads"/OUTPUT)
    p.add_argument("--work",type=pathlib.Path,default=pathlib.Path("/tmp/shared-globals"))
    a=p.parse_args()
    raw=a.input.read_bytes()
    if SHA(raw)!=EXPECTED:raise ValueError("Latest full source hash mismatch "+SHA(raw))
    with zipfile.ZipFile(a.input) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    old_remaining=last.prev.remaining(files)
    core=files[BROADCAST].decode('utf8')
    assert "public static void sendAll(" not in core
    assert core.count('public static void send(')==1
    pos=core.rfind('}')
    files[BROADCAST]=(core[:pos]+SEND_ALL+"\n"+core[pos:]).encode('utf8')
    for name in CLASSES:
        path=BASE+name+'.java'
        original=files[path].decode('utf8')
        result=replace_global(name,original)
        if result==original:raise AssertionError("No broadcast change "+name)
        files[path]=result.encode('utf8')
    after=last.prev.remaining(files)
    assert not any(c+'.java' in after for c in CLASSES)
    assert sum(p.endswith('.java') for p in files)==1711
    assert SHA(files['CTransport.dll'])=="0801f22fedac95ee29d33c747c5e4019c3b36d79d31615c7d84cc79405258606"
    a.work.mkdir(parents=True,exist_ok=True)
    for path,data in files.items():
        if path.startswith('src/'):
            dest=a.work/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
    tests=a.work/"testsrc"/"SharedGlobalBroadcastParity.java"
    tests.parent.mkdir(parents=True,exist_ok=True)
    tests.write_text(JAVA_TEST)
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,d in sorted(files.items()):z.writestr(p,d)
    with zipfile.ZipFile(a.output) as z:assert z.testzip() is None
    report={"base_sha256":EXPECTED,"output_sha256":SHA(a.output.read_bytes()),
            "new_shared_classes":list(CLASSES),"new_shared_count":len(CLASSES),
            "total_shared_classes":38+len(CLASSES),
            "java_files_changed":len(CLASSES)+1,"java_files_total":1711,
            "remaining_classes":sorted(after),"remaining_count":len(after),
            "native_dll_unchanged":True,"full_windows_runtime_tested":False}
    (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
