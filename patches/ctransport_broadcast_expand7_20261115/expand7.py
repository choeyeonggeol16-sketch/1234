#!/usr/bin/env python3
"""Wave 7: share previously snapshotted chat and icon/message broadcasts.

Preserves original standalone direct send serializers, ChatData construction,
outgoing FIFO, IOCP/FFM/native and sender inclusion. Unsupported, oversized,
or invalid 1-byte MS949-length messages retain the old per-recipient path.
"""
from __future__ import annotations
import argparse,hashlib,json,pathlib,re,sys,zipfile

ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT))
from patches.ctransport_broadcast_expand4_20261115 import expand4 as prev
INPUT="CTransport_SharedBroadcast_48Packet_FULL_SRC_20261115.zip"
EXPECTED="89897194cb6a3157a8e1f57243fe5d4db2acc39453a4255239a730fb8c46a682"
OUTPUT="CTransport_SharedBroadcast_50Packet_FULL_SRC_20261115.zip"
BASE=prev.BASE
SHA=lambda b:hashlib.sha256(b).hexdigest()
GLOBAL="l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendAll"

ICON_HELPERS=r'''
    private static void putVar(java.nio.ByteBuffer b,long value) {
        if(value<0L)value&=0xffffffffL;
        while((value & ~0x7fL)!=0L){
            b.put((byte)((value&0x7fL)|0x80L));
            value>>>=7;
        }
        b.put((byte)value);
    }

    private static byte[] sharedWire(String msg,int index,MJSimpleRgb color,
            int surface,int duration,boolean indexMode) {
        final String text=msg==null?"":msg;
        final byte[] rgb=color==null?null:color.get_bytes();
        final byte[] bytes=indexMode?null:text.getBytes(java.nio.charset.Charset.forName("MS949"));
        // Source's writeS2() rejects encoded lengths above 255. Fall back
        // to unchanged direct handler, which preserves that validation.
        if(!indexMode && bytes.length>255)return null;
        final long reserve=64L+(indexMode?0L:((long)text.length()<<2))
                           +(rgb==null?0:rgb.length)+2;
        if(reserve>16384)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate((int)reserve)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.position(2);
        b.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
        b.putShort((short)(indexMode?INDEX_MESSAGE:TEXT_MESSAGE));
        if(indexMode){
            b.put((byte)0x08);putVar(b,surface*2);
            b.put((byte)0x10);putVar(b,index*2);
            b.put((byte)0x1a);
            if(rgb!=null)b.put(rgb);
            b.put((byte)0x20);putVar(b,duration);
        } else {
            putVar(b,0x08);putVar(b,surface*2);
            putVar(b,0x12);
            b.put((byte)bytes.length).put(bytes);
            putVar(b,0x1a);
            if(rgb!=null)b.put(rgb);
            putVar(b,0x20);putVar(b,duration);
        }
        b.putShort((short)0);
        final int size=b.position();
        b.putShort(0,(short)size);
        return java.util.Arrays.copyOf(b.array(),size);
    }
'''

CHAT_HELPERS=r'''
    /** Preserves the historical writeK(int) (not generic protobuf varint).
     * In particular it uses signed int division and Java's 5-byte path.
     */
    private static void putK(java.nio.ByteBuffer b,int value) {
        final int v=value/128;
        if(v==0)b.put((byte)value);
        else if(v<=127)b.put((byte)((value&0x7f)+128)).put((byte)v);
        else if(v<=16383)b.put((byte)((value&0x7f)+128))
                .put((byte)((v&0x7f)+128)).put((byte)(v/128));
        else if(v<=2097151)b.put((byte)((value&0x7f)+128))
                .put((byte)((v&0x7f)+128)).put((byte)(((v/128)&0x7f)+128))
                .put((byte)(v/16384));
        else b.put((byte)((value&0x7f)+128))
                .put((byte)((v&0x7f)+128)).put((byte)(((v/128)&0x7f)+128))
                .put((byte)(((v/16384)&0x7f)+128)).put((byte)(v/2097152));
    }

    private static byte[] sharedWire(ChatData d) {
        final long capacity=2L+64L+d.textBytes.length+d.targetBytes.length
                              +d.senderBytes.length;
        if(capacity>16384)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate((int)capacity)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.position(2);
        b.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
        if(d.type==3)b.put((byte)0x03);
        else if(d.type==4)b.put((byte)0x04);
        b.put((byte)0x02).put((byte)0x08).put((byte)0x00)
         .put((byte)0x10).put((byte)d.chatType);
        b.put((byte)0x1a).put((byte)d.textBytes.length).put(d.textBytes);
        if(d.type==3) {
            b.put((byte)0x22);
            if(d.chatType==0)
                b.put((byte)0x00).put((byte)0x28).put((byte)0x00)
                 .put((byte)0x30).put((byte)0x18).put((byte)0x38).put((byte)0x00);
            else if(d.chatType==1)
                b.put((byte)d.targetBytes.length).put(d.targetBytes)
                 .put((byte)0x28).put((byte)0x00).put((byte)0x30)
                 .put((byte)0x00).putShort((short)0);
        } else if(d.type==4) {
            b.put((byte)0x2a).put((byte)d.senderBytes.length).put(d.senderBytes);
            if(d.chatType==0) {
                b.put((byte)0x38);putK(b,d.pcObjId);
                b.put((byte)0x40);putK(b,d.pcX);
                b.put((byte)0x48);putK(b,d.pcY);
            }
            if(d.rankLevel!=0)b.put((byte)0x50).put((byte)d.rankLevel);
            b.putShort((short)0);
        }
        final int size=b.position();
        if(size>16384)return null;
        b.putShort(0,(short)size);
        return java.util.Arrays.copyOf(b.array(),size);
    }
'''

def migrate_icon(s):
    out=s
    for is_text in (True,False):
        check=(lambda p: "String message" in p) if is_text else (lambda p: "int index" in p)
        desc=prev.method(out,"public","broadcast",check)
        a,b,params,oldbody,full=desc
        if "getAllPlayers()" not in oldbody:raise ValueError("Original icon global loop missing")
        call=("message, 0, color, surface, duration, false" if is_text
              else '"", index, color, surface, duration, true')
        fallback="pc -> send(pc, "+("message" if is_text else "index")+", color, surface, duration)"
        new=f'''public static void broadcast({params}) {{
        final byte[] wire=sharedWire({call});
        if(wire==null) {{
            {oldbody.strip()}
            return;
        }}
        {GLOBAL}(wire,{fallback});
    }}'''
        out=prev.replace(out,desc,new)
    original_sender=prev.method(s,"private","sendInternal")[-1]
    assert original_sender==prev.method(out,"private","sendInternal")[-1]
    i=out.rfind("}")
    return out[:i]+ICON_HELPERS+out[i:]

def migrate_chat(s):
    desc=prev.method(s,"public","broadcastAll")
    a,b,params,oldbody,full=desc
    if "final ChatData data = prepare(" not in oldbody or "data.send(receiver)" not in oldbody:
        raise ValueError("ChatData snapshot flow unexpectedly changed")
    body=f'''public static void broadcastAll({params}) {{
        final ChatData data=prepare(sender,type,chatType,text,targetName);
        final byte[] wire=sharedWire(data);
        if(wire==null) {{
            for (L1PcInstance receiver : L1World.getInstance().getAllPlayers())
                data.send(receiver);
            return;
        }}
        {GLOBAL}(wire,data::send);
    }}'''
    out=prev.replace(s,desc,body)
    assert prev.method(out,"private","sendFrame")[-1]==prev.method(s,"private","sendFrame")[-1]
    assert prev.method(out,"private","writePayload")[-1]==prev.method(s,"private","writePayload")[-1]
    i=out.rfind("}")
    return out[:i]+CHAT_HELPERS+out[i:]

TEST=r'''import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.lang.reflect.*;
import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_NewChat;
public final class SharedGlobalEncodingParity7 {
  static int cases;
  static ByteBuffer b(int cap){ ByteBuffer x=ByteBuffer.allocate(cap).order(ByteOrder.LITTLE_ENDIAN);x.position(2);return x; }
  static void eq(byte[] actual,ByteBuffer e,String test,int k) {
    int size=e.position();e.putShort(0,(short)size);
    if(!Arrays.equals(actual,Arrays.copyOf(e.array(),size)))
      throw new AssertionError(test+" case "+k+" expected="+Arrays.toString(Arrays.copyOf(e.array(),size))+" actual="+Arrays.toString(actual));
    cases++;
  }
  static void putVar(ByteBuffer b,long v) {
    if(v<0L)v&=0xffffffffL;
    while((v&~127L)!=0){b.put((byte)((v&127L)|128L));v>>>=7;}
    b.put((byte)v);
  }
  static void putK(ByteBuffer b,int value) {
    int v=value/128;
    if(v==0)b.put((byte)value);
    else if(v<=127)b.put((byte)((value&127)+128)).put((byte)v);
    else if(v<=16383)b.put((byte)((value&127)+128)).put((byte)((v&127)+128)).put((byte)(v/128));
    else if(v<=2097151)b.put((byte)((value&127)+128)).put((byte)((v&127)+128)).put((byte)(((v/128)&127)+128)).put((byte)(v/16384));
    else b.put((byte)((value&127)+128)).put((byte)((v&127)+128)).put((byte)(((v/128)&127)+128)).put((byte)(((v/16384)&127)+128)).put((byte)(v/2097152));
  }
  static byte[] icon(String msg,int index,MJSimpleRgb rgb,int surface,int duration,boolean indexed)throws Exception{
    Class<?> c=Class.forName("l1j.server.server.model.network.core.sc.packet.SC_IconMessage");
    Method m=c.getDeclaredMethod("sharedWire",String.class,int.class,MJSimpleRgb.class,int.class,int.class,boolean.class);
    m.setAccessible(true);
    return (byte[])m.invoke(null,msg,index,rgb,surface,duration,indexed);
  }
  static byte[] chat(SC_NewChat.ChatData d)throws Exception{
    Method m=SC_NewChat.class.getDeclaredMethod("sharedWire",SC_NewChat.ChatData.class);
    m.setAccessible(true);
    return (byte[])m.invoke(null,d);
  }
  public static void main(String[] args)throws Exception {
    String[] words={"","a","리니지","KOREA","한글","漢字","😀","A\\u0000B"};
    MJSimpleRgb color=new MJSimpleRgb(249,68,253);
    for(int i=0;i<words.length;i++){
      String msg=words[i];byte[] chars=msg.getBytes(Charset.forName("MS949"));
      byte[] rgb=(i%2==0?color.get_bytes():null);
      MJSimpleRgb actualColor=i%2==0?color:null;
      int surface=i*13-27,duration=i*99-444,index=i*73-31;
      ByteBuffer x=b(256);
      x.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)0x40);
      putVar(x,0x08);putVar(x,surface*2);putVar(x,0x12);
      x.put((byte)chars.length).put(chars);
      putVar(x,0x1a);if(rgb!=null)x.put(rgb);
      putVar(x,0x20);putVar(x,duration);x.putShort((short)0);
      eq(icon(msg,0,actualColor,surface,duration,false),x,"icontext",i);
      ByteBuffer y=b(256);
      y.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)0x67);
      y.put((byte)0x08);putVar(y,surface*2);y.put((byte)0x10);putVar(y,index*2);
      y.put((byte)0x1a);if(rgb!=null)y.put(rgb);
      y.put((byte)0x20);putVar(y,duration);y.putShort((short)0);
      eq(icon("",index,actualColor,surface,duration,true),y,"iconindex",i);
    }
    int k=0;
    for(int type:new int[]{3,4,0})for(int chatType:new int[]{0,1,12})for(String word:words){
      String target="대상";
      SC_NewChat.ChatData snapshot=SC_NewChat.prepare(null,type,chatType,word,target);
      byte[] text=word.getBytes(Charset.forName("MS949"));
      byte[] targetB=(type==3&&chatType==1?target.getBytes(Charset.forName("MS949")):new byte[0]);
      ByteBuffer e=b(256);
      e.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
      if(type==3)e.put((byte)0x03);else if(type==4)e.put((byte)0x04);
      e.put((byte)0x02).put((byte)0x08).put((byte)0).put((byte)0x10).put((byte)chatType)
       .put((byte)0x1a).put((byte)text.length).put(text);
      if(type==3) {
        e.put((byte)0x22);
        if(chatType==0)e.put((byte)0).put((byte)0x28).put((byte)0).put((byte)0x30).put((byte)0x18).put((byte)0x38).put((byte)0);
        else if(chatType==1)e.put((byte)targetB.length).put(targetB).put((byte)0x28).put((byte)0).put((byte)0x30).put((byte)0).putShort((short)0);
      } else if(type==4) {
        e.put((byte)0x2a).put((byte)0);
        if(chatType==0) {
          e.put((byte)0x38);putK(e,0);e.put((byte)0x40);putK(e,0);e.put((byte)0x48);putK(e,0);
        }
        e.putShort((short)0);
      }
      eq(chat(snapshot),e,"chat",k++);
    }
    if(icon("a".repeat(300),0,null,0,0,false)!=null)
       throw new AssertionError("writeS2 overflow must preserve legacy path");
    SC_NewChat.ChatData huge=SC_NewChat.prepare(null,3,0,"a".repeat(17000),null);
    if(chat(huge)!=null)throw new AssertionError("oversized chat must preserve legacy path");
    System.out.println("SHARED_GLOBAL_ENCODING_PARITY_7_OK checks="+cases);
  }
}
'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--input',type=pathlib.Path,default=ROOT/'downloads'/INPUT)
    p.add_argument('--output',type=pathlib.Path,default=ROOT/'downloads'/OUTPUT)
    p.add_argument('--work',type=pathlib.Path,default=pathlib.Path('/tmp/broadcast-wave7'))
    a=p.parse_args()
    raw=a.input.read_bytes()
    if SHA(raw)!=EXPECTED:raise ValueError("Wrong source baseline "+SHA(raw))
    with zipfile.ZipFile(a.input) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    for name,fun in [("SC_NewChat",migrate_chat),("SC_IconMessage",migrate_icon)]:
        path=BASE+name+".java"
        src=files[path].decode('utf8');new=fun(src)
        if new==src:raise ValueError("No changes "+name)
        files[path]=new.encode('utf8')
    remains=[]
    for path,data in files.items():
        if not path.startswith(BASE) or not path.endswith('.java'):continue
        s=data.decode('utf8')
        if re.search(r'\b(?:broadcast|broadcastFast|broadcastM|broadcastWide|broadcastAll|broadcastClan|sendAndBroadcast)\s*\(',s) and not re.search(r'\bCSharedBroadcast\.send(?:All)?\s*\(',s):
            remains.append(path.split('/')[-1])
    assert "SC_NewChat.java" not in remains and "SC_IconMessage.java" not in remains
    assert sum(p.endswith('.java') for p in files)==1711
    assert SHA(files['CTransport.dll'])=="0801f22fedac95ee29d33c747c5e4019c3b36d79d31615c7d84cc79405258606"
    a.work.mkdir(parents=True,exist_ok=True)
    for path,data in files.items():
        if path.startswith('src/'):
            dst=a.work/path;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(data)
    test=a.work/'testsrc'/'SharedGlobalEncodingParity7.java'
    test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name,data in sorted(files.items()):z.writestr(name,data)
    with zipfile.ZipFile(a.output) as z:assert z.testzip() is None
    report={"input_sha256":EXPECTED,"output_sha256":SHA(a.output.read_bytes()),
            "new_shared_classes":["SC_NewChat","SC_IconMessage"],"new_count":2,
            "total_shared_packet_classes":50,"all_java_files":1711,
            "remaining_unconverted_classes":sorted(remains),"remaining_count":len(remains),
            "legacy_direct_senders_unchanged":True,"dll_unchanged":True,
            "windows_game_integration_tested":False}
    (a.work/'audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
