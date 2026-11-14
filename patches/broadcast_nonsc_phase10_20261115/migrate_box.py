#!/usr/bin/env python3
"""Phase10: identical global/map green notices and LFC team/timer packet-box
broadcasts use one immutable packet frame, not a per-recipient SC direct encode.

Source is pinned to the successfully compiled phase9 ZIP. All recipient order
and duplicates, packet opcodes, subcodes and payloads are retained.
No fallback, new class, queue, worker, or native DLL edits.
"""
from __future__ import annotations
import argparse,hashlib,json,pathlib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase9_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase10_FULL_SRC_20261115.zip"
PIN="41714f4421c439aecb00f941abd7bc1e6b94ca0b"
BOX="src/l1j/server/server/model/network/core/sc/packet/SC_PacketBox.java"
LFC="src/l1j/server/MJInstanceSystem/MJLFC/Template/MJLFCObject.java"
SHARED="src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java"
C="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

def blob_sha(data:bytes)->str:
    return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()

def method(src:str,signature:str):
    assert src.count(signature)==1,(signature,src.count(signature))
    start=src.index(signature)
    brace=src.index("{",start)
    depth=0
    for i in range(brace,len(src)):
        if src[i]=="{":depth+=1
        elif src[i]=="}":
            depth-=1
            if depth==0:return start,i+1,src[start:i+1]
    raise AssertionError("unbalanced "+signature)

def replace_method(src,signature,change):
    a,b,old=method(src,signature)
    new=change(old)
    assert old!=new,signature
    return src[:a]+new+src[b:]

GREEN_HELPER=r'''
    /**
     * Wire-compatible constructor 13 / GREEN_MESSAGE:
     * S_EVENT, GREEN_MESSAGE, 0x02, MS949 string with NUL terminator.
     * Immutable 2-byte framed payload, independent of receiving character.
     */
    public static byte[] sharedGreenWire(String message) {
        final byte[] encoded=(message==null ? "" : message)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int length=6+encoded.length;
        if(length>0xffff) return null;
        final byte[] wire=new byte[length];
        wire[0]=(byte)length;
        wire[1]=(byte)(length>>>8);
        wire[2]=(byte)Opcodes.S_EVENT;
        wire[3]=(byte)GREEN_MESSAGE;
        wire[4]=2;
        System.arraycopy(encoded,0,wire,5,encoded.length);
        return wire;
    }

    /** Wire-compatible constructor 7 / MAP_TIMER: S_EVENT, subcode, int32. */
    public static byte[] sharedMapTimerWire(int value) {
        final byte[] wire=new byte[8];
        wire[0]=8;
        wire[2]=(byte)Opcodes.S_EVENT;
        wire[3]=(byte)MAP_TIMER;
        wire[4]=(byte)value;
        wire[5]=(byte)(value>>>8);
        wire[6]=(byte)(value>>>16);
        wire[7]=(byte)(value>>>24);
        return wire;
    }

'''

def box(src):
    assert "public static byte[] sharedGreenWire(" not in src
    assert "public static byte[] sharedMapTimerWire(" not in src
    for snippet in ("case 13:","case GREEN_MESSAGE:","buffer.writeC(2);",
                    "buffer.writeS(_strVal1);","case MAP_TIMER:",
                    "buffer.writeD(_val1);","buffer.writeC(Opcodes.S_EVENT);",
                    "buffer.writeC(_subCode);"):
        assert snippet in src,snippet
    start="    public static void broadcastGreen(String message) {"
    assert src.count(start)==1
    src=src.replace(start,GREEN_HELPER+start,1)
    def global_green(old):
        assert "L1World.getInstance().getAllPlayers()" in old
        assert "send(pc, GREEN_MESSAGE, message)" in old
        return '''public static void broadcastGreen(String message) {
        '''+C+'''.sendAll(sharedGreenWire(message));
    }'''
    def map_green(old):
        assert "L1World.getInstance().getVisiblePlayers(mapId)" in old
        assert "send(pc, GREEN_MESSAGE, message)" in old
        return '''public static void broadcastGreenMap(int mapId, String message) {
        '''+C+'''.sendRecipients(
                L1World.getInstance().getVisiblePlayers(mapId),
                sharedGreenWire(message));
    }'''
    src=replace_method(src,start,global_green)
    return replace_method(src,"public static void broadcastGreenMap(int mapId, String message) {",map_green)

GROUPS=r'''
    /**
     * One frame for two existing recipient lists. Crucially preserve the
     * original first-list then second-list order, including duplicate members,
     * without constructing a temporary combined list.
     */
    public static void sendGroups(Iterable<? extends L1PcInstance> first,
            Iterable<? extends L1PcInstance> second, byte[] wire) {
        if(wire==null)return;
        try(var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            sendGroup(first,shared);
            sendGroup(second,shared);
        }
    }

    private static void sendGroup(Iterable<? extends L1PcInstance> members,
            CAsyncSendConstBuffer shared) {
        if(members==null)return;
        for(L1PcInstance pc:members) {
            if(pc==null)continue;
            final CIOClient client=pc.getNetConnection();
            if(client==null || !client.isConnected())continue;
            final var socket=client.getConnection();
            if(socket!=null && socket.isClientConnected())
                shared.send(socket,pc.getId());
        }
    }

'''

def shared(src):
    assert "public static void sendGroups(" not in src
    assert "public static void sendRecipients(" in src
    marker="    /** Global broadcast includes the sender and uses the normal socket FIFO. */"
    assert src.count(marker)==1
    return src.replace(marker,GROUPS+marker,1)

def lfc(src):
    def notice(old):
        assert old.count("this.broadcastPacket(SC_PacketBox.GREEN_MESSAGE, msg);")==1
        return old.replace("this.broadcastPacket(SC_PacketBox.GREEN_MESSAGE, msg);",
            C+".sendGroups(_red,_blue,SC_PacketBox.sharedGreenWire(msg));",1)
    def timer(old):
        assert old.count("this.broadcastPacket(SC_PacketBox.MAP_TIMER, _time);")==1
        return old.replace("this.broadcastPacket(SC_PacketBox.MAP_TIMER, _time);",
            C+".sendGroups(_red,_blue,SC_PacketBox.sharedMapTimerWire(_time));",1)
    src=replace_method(src,"protected void sendInPlayerMessage(String msg){",notice)
    return replace_method(src,"protected void waitCount() throws Exception{",timer)

TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
public final class PacketBoxSharedParity10 {
  static int tests;
  static void assertEqual(byte[] a,byte[] b) {
    if(!Arrays.equals(a,b))throw new AssertionError("byte parity "+tests);
    tests++;
  }
  static byte[] green(String message) {
    byte[] text=(message==null?"":message).getBytes(Charset.forName("MS949"));
    byte[] b=new byte[text.length+6];
    b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);
    b[2]=(byte)Opcodes.S_EVENT;
    b[3]=(byte)SC_PacketBox.GREEN_MESSAGE;b[4]=2;
    System.arraycopy(text,0,b,5,text.length);
    return b;
  }
  static byte[] timer(int v) {
    return new byte[]{8,0,(byte)Opcodes.S_EVENT,(byte)SC_PacketBox.MAP_TIMER,
      (byte)v,(byte)(v>>>8),(byte)(v>>>16),(byte)(v>>>24)};
  }
  public static void main(String[] args) {
    String[] fixed={"","foo","한국어","\\f3hello","123","你好吗","㍿",null};
    int[] values={0,1,-1,127,128,255,256,65535,Integer.MIN_VALUE,Integer.MAX_VALUE};
    for(String s:fixed)assertEqual(green(s),SC_PacketBox.sharedGreenWire(s));
    for(int v:values)assertEqual(timer(v),SC_PacketBox.sharedMapTimerWire(v));
    int rand=0x5678abcd;
    for(int i=0;i<4096;i++) {
      rand^=rand<<13;rand^=rand>>>17;rand^=rand<<5;
      String msg=i%2==0 ? ("이벤트"+rand+"\\f2") : ("LFC "+rand);
      assertEqual(green(msg),SC_PacketBox.sharedGreenWire(msg));
      assertEqual(timer(rand),SC_PacketBox.sharedMapTimerWire(rand));
    }
    System.out.println("PACKET_BOX_SHARED_PARITY10_OK "+tests);
  }
}'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--input",type=pathlib.Path,default=SOURCE)
    p.add_argument("--output",type=pathlib.Path,default=OUTPUT)
    p.add_argument("--work",type=pathlib.Path,default=pathlib.Path("/tmp/broadcast-nonsc-phase10"))
    args=p.parse_args()
    raw=args.input.read_bytes()
    assert blob_sha(raw)==PIN,"Not verified phase9 whole SRC"
    with zipfile.ZipFile(args.input) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    old=files.copy()
    assert sum(n.endswith(".java") for n in files)==1711
    assert "CTransport.dll" in files
    for path,fn in ((BOX,box),(SHARED,shared),(LFC,lfc)):
        data=files[path].decode("utf8")
        next=fn(data)
        assert next!=data
        files[path]=next.encode("utf8")
    changed=sorted(n for n in files if files[n]!=old[n])
    assert changed==sorted([BOX,SHARED,LFC]),changed
    assert files["CTransport.dll"]==old["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for name,data in files.items():
        if name.startswith("src/") and name.endswith(".java"):
            dest=args.work/name
            dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(data)
    test=args.work/"testsrc/PacketBoxSharedParity10.java"
    test.parent.mkdir(parents=True,exist_ok=True)
    test.write_text(TEST)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for name,data in sorted(files.items()):z.writestr(name,data)
    with zipfile.ZipFile(args.output) as z:
        assert z.testzip() is None
        assert set(z.namelist())==set(files)
    info={"phase":10,"input_blob":PIN,
          "output_sha256":hashlib.sha256(args.output.read_bytes()).hexdigest(),
          "modified_java":changed,"java_sources":1711,
          "global_green_shared":True,"map_green_shared":True,
          "lfc_green_and_timer_shared":True,
          "team_order_and_duplicate_recipients_preserved":True,
          "no_legacy_fallback":True,"native_dll_unchanged":True,
          "runtime_windows_tested":False}
    (args.work/"audit.json").write_text(json.dumps(info,indent=2,ensure_ascii=False)+"\n")
    print(json.dumps(info,indent=2,ensure_ascii=False))
if __name__=="__main__":
    main()
