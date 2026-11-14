#!/usr/bin/env python3
"""Phase11: share four recipient-independent NPC/player chat fanouts.
Preserve AOI and borrowed-fast lifetime; no generic ServerPacket conversion,
no native changes, no fallback, no queue/worker.
"""
from pathlib import Path
import hashlib,zipfile,json,argparse
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase10_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase11_FULL_SRC_20261115.zip"
PIN="508c8343ba1f959861690b922f28d25c9c16afa7"
TARGET="src/l1j/server/server/model/network/core/sc/packet/SC_NpcChatPacket.java"
SHARED="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

def blobsha(data):
    return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
def method(src,sig):
    assert src.count(sig)==1,(sig,src.count(sig))
    a=src.index(sig);b=src.index("{",a);depth=0
    for i in range(b,len(src)):
        if src[i]=="{":depth+=1
        elif src[i]=="}":
            depth-=1
            if depth==0:return a,i+1,src[a:i+1]
    raise AssertionError("unterminated "+sig)
def swap(src,sig,expected,replacement):
    a,b,old=method(src,sig)
    for part in expected: assert part in old,(sig,part)
    return src[:a]+replacement+src[b:]
WIRE=r'''
    /** Identical S_SAY_CODE payload for all viewers; socket-specific cipher
     * is applied by the existing native FIFO, never to this shared plaintext.
     */
    public static byte[] sharedWire(int objectId,int color,String text) {
        if(text==null)return null;
        final byte[] chars=text.getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=9+chars.length;
        if(size>0xffff)return null;
        final byte[] wire=new byte[size];
        wire[0]=(byte)size;
        wire[1]=(byte)(size>>>8);
        wire[2]=(byte)Opcodes.S_SAY_CODE;
        wire[3]=(byte)color;
        wire[4]=(byte)objectId;
        wire[5]=(byte)(objectId>>>8);
        wire[6]=(byte)(objectId>>>16);
        wire[7]=(byte)(objectId>>>24);
        System.arraycopy(chars,0,wire,8,chars.length);
        return wire;
    }

'''
def patch(src):
    assert "public static byte[] sharedWire(" not in src
    assert "buffer.writeC(Opcodes.S_SAY_CODE);" in src
    assert "buffer.writeC(color);" in src
    assert "buffer.writeD(npcId);" in src
    assert "buffer.writeS(text);" in src
    sig="    private static boolean sendFormatted(L1PcInstance pc, int npcId, int color, String text) {"
    assert src.count(sig)==1
    src=src.replace(sig,WIRE+sig,1)
    src=swap(src,
        "public static void broadcastCharacter(L1Character source, L1Character cha, String chat) {",
        ["getVisiblePlayer(source)","sendFormatted(pc, objId, 0, message);"],
        """public static void broadcastCharacter(L1Character source, L1Character cha, String chat) {
        if(source==null || cha==null)return;
        final String message=chat==null?"":chat;
        """+SHARED+""".sendRecipients(
                L1World.getInstance().getVisiblePlayer(source),
                sharedWire(cha.getId(),0,message));
    }""")
    src=swap(src,
        "public static void broadcastNpcFast(L1Character source, L1NpcInstance npc, String chat, int type) {",
        ["formatNpcChat(npc, chat, type)","borrowVisiblePlayerFast(source)","releaseVisiblePlayerFast(visible)","sendFormatted(pc, npcId, color, formatted);"],
        """public static void broadcastNpcFast(L1Character source, L1NpcInstance npc, String chat, int type) {
        if(source==null || npc==null)return;
        final String formatted=formatNpcChat(npc,chat,type);
        if(formatted==null)return;
        final int color=type==4?NPC_CHAT_NOMAL_4:type;
        final byte[] wire=sharedWire(npc.getId(),color,formatted);
        final L1World world=L1World.getInstance();
        final java.util.List<L1PcInstance> visible=world.borrowVisiblePlayerFast(source);
        try {
            """+SHARED+""".sendRecipients(visible,wire);
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }
    }""")
    src=swap(src,
        "public static void broadcastNpc(L1Character source, L1NpcInstance npc, String chat, int type) {",
        ["formatNpcChat(npc, chat, type)","getVisiblePlayer(source)","sendFormatted(pc, npcId, color, formatted);"],
        """public static void broadcastNpc(L1Character source, L1NpcInstance npc, String chat, int type) {
        if(source==null || npc==null)return;
        final String formatted=formatNpcChat(npc,chat,type);
        if(formatted==null)return;
        final int color=type==4?NPC_CHAT_NOMAL_4:type;
        """+SHARED+""".sendRecipients(
                L1World.getInstance().getVisiblePlayer(source),
                sharedWire(npc.getId(),color,formatted));
    }""")
    src=swap(src,
        "public static void broadcastNpcWide(L1Character source, L1NpcInstance npc, String chat, int type) {",
        ["formatNpcChat(npc, chat, type)","getVisiblePlayer(source, 50)","sendFormatted(pc, npcId, color, formatted);"],
        """public static void broadcastNpcWide(L1Character source, L1NpcInstance npc, String chat, int type) {
        if(source==null || npc==null)return;
        final String formatted=formatNpcChat(npc,chat,type);
        if(formatted==null)return;
        final int color=type==4?NPC_CHAT_NOMAL_4:type;
        """+SHARED+""".sendRecipients(
                L1World.getInstance().getVisiblePlayer(source,50),
                sharedWire(npc.getId(),color,formatted));
    }""")
    assert src.count("sendFormatted(pc,")==2,"only single-target send methods retain encoder"
    return src

TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
public final class SharedNpcChatParity11 {
    static int checks=0;
    static byte[] original(int id,int color,String text) {
        if(text==null)return null;
        byte[] b=text.getBytes(Charset.forName("MS949"));
        byte[] w=new byte[b.length+9];w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
        w[2]=(byte)Opcodes.S_SAY_CODE;w[3]=(byte)color;
        for(int i=0;i<4;i++)w[4+i]=(byte)(id>>>(i*8));
        System.arraycopy(b,0,w,8,b.length);
        return w;
    }
    static void compare(int id,int color,String text) {
        if(!Arrays.equals(original(id,color,text),SC_NpcChatPacket.sharedWire(id,color,text)))
            throw new AssertionError("mismatch "+checks);
        checks++;
    }
    public static void main(String[] args) {
        String[] texts={"","한국어","NPC: 안녕","[몬스터] 보스","\\f2hello","𠮷",null};
        for(String s:texts)for(int id:new int[]{0,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,0x12345678})
            for(int color:new int[]{0,1,3,4,21,255})compare(id,color,s);
        int r=0x1b5369a;
        for(int i=0;i<4096;i++){r^=r<<13;r^=r>>>17;r^=r<<5;
            compare(r,i&255,i%2==0?"보스 방송"+r:"FastNPC_"+r+"\\f2");
        }
        System.out.println("SHARED_NPC_CHAT_PARITY11_OK "+checks);
    }
}'''
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase11"))
    args=parser.parse_args()
    data=SOURCE.read_bytes()
    assert blobsha(data)==PIN,"phase10 baseline ZIP mismatch"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    before=files.copy()
    assert sum(p.endswith(".java") for p in files)==1711
    assert "CTransport.dll" in files
    files[TARGET]=patch(files[TARGET].decode("utf8")).encode("utf8")
    changed=sorted(p for p in files if files[p]!=before[p])
    assert changed==[TARGET],changed
    assert files["CTransport.dll"]==before["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for p,bytes_ in files.items():
        if p.startswith("src/") and p.endswith(".java"):
            f=args.work/p;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(bytes_)
    test=args.work/"testsrc/SharedNpcChatParity11.java"
    test.parent.mkdir(parents=True,exist_ok=True)
    test.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for p,b in sorted(files.items()):z.writestr(p,b)
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    audit={"phase":11,"baseline_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
        "modified_java":changed,"java_sources":1711,"native_unchanged":True,
        "four_chat_broadcasts_share_one_frame":True,
        "AOI_regular_fast_wide_preserved":True,"borrowed_fast_released":True,
        "per_socket_cipher_fifo_preserved":True,"no_fallback":True,"windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(audit,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
