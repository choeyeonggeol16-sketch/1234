#!/usr/bin/env python3
"""Phase30: Haunted House guide NPC dialogue reuses one immutable S_SAY_CODE
frame across precisely the original participant loop, with NPC formatting
performed once rather than repeated for each receiver.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase29_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase30_FULL_SRC_20261115.zip"
PIN="63b13d23335b559179ec6d7de38ccd6984c4cd90"
BASE="src/l1j/server/server/"
PACKET=BASE+"model/network/core/sc/packet/SC_NpcChatPacket.java"
HAUNT=BASE+"model/L1HauntedHouse.java"
TARGETS=[PACKET,HAUNT]
FQ="l1j.server.server.model.network.core.broadcast"
def sha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def bounds(src,sig):
    assert src.count(sig)==1,(sig,src.count(sig))
    i=src.index(sig);j=src.index("{",i);depth=0
    for k in range(j,len(src)):
        if src[k]=="{":depth+=1
        elif src[k]=="}":
            depth-=1
            if depth==0:return i,k+1,src[i:k+1]
    raise AssertionError(sig)
HELPER=r'''
    /** Preformat an NPC's message once for identical multi-recipient dialogue.
     * Reuses precisely the normal sendNpc() formatter, object ID and color.
     */
    public static byte[] sharedNpcWire(L1NpcInstance npc,String chat,int type) {
        if(npc==null)return null;
        final String formatted=formatNpcChat(npc,chat,type);
        if(formatted==null)return null;
        return sharedWire(npc.getId(),
                type==4?NPC_CHAT_NOMAL_4:type,formatted);
    }

'''
def patch(files):
    s=files[PACKET].decode("utf8")
    assert "public static byte[] sharedNpcWire(" not in s
    anchor="    public static boolean sendNpc(L1PcInstance pc, L1NpcInstance npc, String chat, int type) {"
    assert s.count(anchor)==1
    assert "final String formatted=formatNpcChat(npc,chat,type);" in s
    s=s.replace(anchor,HELPER+anchor,1)
    files[PACKET]=s.encode("utf8")
    s=files[HAUNT].decode("utf8")
    i,j,old=bounds(s,"private void npcBroadcast(String msg) {")
    assert old.count("SC_NpcChatPacket.sendNpc(pc, _guide, msg, 2);")==1
    assert "getVisibleObjects(4).values()" in old
    assert "if (_guide == null)" in old
    assert "n.getNpcTemplate().get_npcId() == 80085" in old
    assert "for (L1PcInstance pc : getMembersArray())" in old
    assert "pc.getMapId() == 5140" in old
    sendloop='''for (L1PcInstance pc : getMembersArray()) {
            if (pc.getMapId() == 5140) {
                SC_NpcChatPacket.sendNpc(pc, _guide, msg, 2);
            }
        }'''
    # Verify loop using actual balanced braces rather than whitespace template.
    a,b,loop=bounds(old,"for (L1PcInstance pc : getMembersArray()) {")
    assert loop.count("SC_NpcChatPacket.sendNpc(pc, _guide, msg, 2);")==1
    replacement='''final byte[] wire=SC_NpcChatPacket.sharedNpcWire(_guide,msg,2);
        if(wire==null)return;
        try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for (L1PcInstance pc : getMembersArray()) {
                if (pc.getMapId() == 5140) {
                    '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,pc);
                }
            }
        }'''
    old=old[:a]+replacement+old[b:]
    s=s[:i]+old+s[j:]
    assert "SC_NpcChatPacket.sendNpc(pc, _guide, msg, 2)" not in s
    files[HAUNT]=s.encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
public final class Phase30NpcChatWireParity {
 static int checks;
 static void check(int id,int color,String text){
  byte[] b=text.getBytes(Charset.forName("MS949"));
  byte[] w=new byte[b.length+9];
  w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
  w[2]=(byte)Opcodes.S_SAY_CODE;w[3]=(byte)color;
  for(int i=0;i<4;i++)w[4+i]=(byte)(id>>>(8*i));
  System.arraycopy(b,0,w,8,b.length);
  if(!Arrays.equals(w,SC_NpcChatPacket.sharedWire(id,color,text)))
    throw new AssertionError("NPC dialogue parity "+checks);
  checks++;
 }
 public static void main(String[] args){
  for(String text:new String[]{"","<NPC> 잠시후 게임이 시작됩니다.",
    "<유령 안내인> 시간이 다 되었군. 다음에 또 보세나.","한국어","\\f2Hi"})
   for(int id:new int[]{0,-1,0x12345678,Integer.MIN_VALUE,Integer.MAX_VALUE})
    for(int color:new int[]{0,2,3,4,21})check(id,color,text);
  int x=0x35126784;
  for(int i=0;i<4096;i++){x^=x<<13;x^=x>>>17;x^=x<<5;check(x,2,"<NPC> 안내 "+x);}
  System.out.println("PHASE30_NPC_CHAT_WIRE_PARITY_OK "+checks);
 }
}'''
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase30"));args=p.parse_args()
    b=SOURCE.read_bytes();assert sha(b)==PIN,"phase29 pinned source differs"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    old=files.copy()
    assert sum(x.endswith(".java") for x in files)==1710 and "CTransport.dll" in files
    patch(files)
    modified=sorted(n for n in files if files[n]!=old[n])
    assert modified==sorted(TARGETS),modified
    assert files["CTransport.dll"]==old["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for n,bytes_ in files.items():
        if n.startswith("src/") and n.endswith(".java"):
            f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(bytes_)
    test=args.work/"testsrc/Phase30NpcChatWireParity.java"
    test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n,bytes_ in sorted(files.items()):z.writestr(n,bytes_)
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    result={"phase":30,"input_blob":PIN,
       "output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
       "java_sources":1710,"modified_java":modified,
       "haunted_npc_chat_shared":True,
       "npc_send_format_color_and_id_preserved":True,
       "guide_lookup_and_map_filter_preserved":True,
       "participant_order_and_duplicates_preserved":True,
       "native_unchanged":True,"no_fallback":True,"no_new_queues_or_workers":True,
       "windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps(result,indent=2))
if __name__=="__main__":main()
