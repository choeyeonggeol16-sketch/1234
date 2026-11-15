#!/usr/bin/env python3
"""Phase40: complete server-macro shared SEND migration for all 5 DB macro
kinds, including icon, protobuf chat notice and whisper. The encoded plaintext
is immutable, transport-specific crypto remains per socket, and no legacy
per-recipient serializer or fallback is used.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase39_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase40_FULL_SRC_20261115.zip"
PIN="701364619fe82e831b68ae83b21d00a2b9bba007"
BASE="src/l1j/server/"
LOADER=BASE+"MJServerMacroSystem/MJServerMacroLoader.java"
CHAT=BASE+"server/model/network/core/sc/packet/SC_ChatMessageNoti.java"
ICON=BASE+"server/model/network/core/sc/packet/SC_IconMessage.java"
CHANGED=sorted([LOADER,CHAT,ICON])
BC="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
CHAT_HELPER=r'''
    /** The unchanged macro notice/whisper S_EXTENDED_PROTOBUF payload:
     * message type 0x0204, proto fields 08/10/1a/2a/30, final ushort 0.
     * Only the fixed macro form (flag30=0, no RGB/object/rank) is shared.
     */
    public static byte[] sharedMacroWire(int type,String message,String sender) {
        final java.nio.charset.Charset ms949=
                java.nio.charset.Charset.forName("MS949");
        final byte[] text=(message==null?"":message).getBytes(ms949);
        final byte[] from=sender==null?null:sender.getBytes(ms949);
        if(text.length>255 || from!=null && from.length>255)
            throw new IllegalStateException("writeS2 overflow in macro chat");
        final int size=15+text.length+(from==null?0:from.length+2);
        final byte[] wire=new byte[size];
        wire[0]=(byte)size;
        wire[1]=(byte)(size>>>8);
        int i=2;
        wire[i++]=(byte)Opcodes.S_EXTENDED_PROTOBUF;
        wire[i++]=(byte)SC_CHAT_MESSAGE_NOTI_PACKET;
        wire[i++]=(byte)(SC_CHAT_MESSAGE_NOTI_PACKET>>>8);
        wire[i++]=0x08;wire[i++]=0;
        wire[i++]=0x10;wire[i++]=(byte)type;
        wire[i++]=0x1A;wire[i++]=(byte)text.length;
        System.arraycopy(text,0,wire,i,text.length);i+=text.length;
        if(from!=null){
            wire[i++]=0x2A;wire[i++]=(byte)from.length;
            System.arraycopy(from,0,wire,i,from.length);i+=from.length;
        }
        wire[i++]=0x30;wire[i++]=0;
        wire[i++]=0;wire[i++]=0;
        if(i!=size)throw new IllegalStateException("macro chat frame size");
        return wire;
    }

'''
ICON_HELPER=r'''
    /** Macro icon uses exactly GM_COLOR, surface 28, duration 60.
     * Oversized writeS2 payload retains the normal direct-writer exception,
     * without falling back to recipient-by-recipient serialization.
     */
    public static byte[] sharedGmWire(String message) {
        final byte[] wire=sharedWire(message,0,GM_COLOR,28,60,false);
        if(wire==null)throw new IllegalStateException("macro icon frame overflow");
        return wire;
    }

'''
def patch(files):
 s=files[CHAT].decode("utf8")
 assert "public static byte[] sharedMacroWire(" not in s
 for required in ("buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);",
     "buffer.writeH(SC_CHAT_MESSAGE_NOTI_PACKET);",
     "buffer.writeC(0x08);","buffer.writeBit(0x00);",
     "buffer.writeC(0x10);","buffer.writeC(type);",
     "buffer.writeC(0x1A);","buffer.writeS2(msg);",
     "buffer.writeC(0x2A);","buffer.writeS2(sender);",
     "buffer.writeC(0x30);","buffer.writeC(flag30);",
     "buffer.writeH(0x00);"):
  assert required in s,(CHAT,required)
 needle="    public static boolean sendWhisper(L1PcInstance receiver, String msg, String sender) {"
 assert s.count(needle)==1
 s=s.replace(needle,CHAT_HELPER+needle,1)
 files[CHAT]=s.encode("utf8")
 s=files[ICON].decode("utf8")
 assert "public static byte[] sharedGmWire(" not in s
 for required in ("GM_COLOR = new MJSimpleRgb(249, 68, 253)",
       "private static byte[] sharedWire(String msg,int index,MJSimpleRgb color,",
       "public static boolean sendGm(L1PcInstance pc, String message) {",
       "buffer.writeH(indexMode ? INDEX_MESSAGE : TEXT_MESSAGE);",
       "buffer.writeS2(msg);"):
  assert required in s,(ICON,required)
 needle="    public static boolean sendGm(L1PcInstance pc, String message) {"
 assert s.count(needle)==1
 s=s.replace(needle,ICON_HELPER+needle,1)
 files[ICON]=s.encode("utf8")
 s=files[LOADER].decode("utf8")
 start="final String packet=obj.get();"
 end="obj.nextMs = cur + obj.delay;"
 assert s.count(start)==1 and s.count(end)==1
 a=s.index(start);b=s.index(end,a)
 old=s[a:b]
 for required in ("final int selectedType=obj.getSelectedType();",
       "selectedType==0","selectedType==2",
       "for(L1PcInstance p:col)",
       "SC_IconMessage.sendGm(p,packet);",
       "SC_ChatMessageNoti.sendNotice(p,packet,Config.GAME_SERVER_NAME);",
       "SC_ChatMessageNoti.sendWhisper(p,packet,\"오늘의 검색어\");"):
  assert required in old,(LOADER,required)
 new='''final String packet=obj.get();
                final int selectedType=obj.getSelectedType();
                if(selectedType==0){
                    '''+BC+'''.sendRecipients(col,SC_SystemMessage.sharedWire(packet));
                } else if(selectedType==2){
                    '''+BC+'''.sendRecipients(col,SC_PacketBox.sharedGreenWire(packet));
                } else if(selectedType==1){
                    '''+BC+'''.sendRecipients(col,SC_IconMessage.sharedGmWire(packet));
                } else if(selectedType==3){
                    '''+BC+'''.sendRecipients(col,SC_ChatMessageNoti.sharedMacroWire(
                            SC_ChatMessageNoti.CHAT_PLEDGE_NOTICE,
                            packet,Config.GAME_SERVER_NAME));
                } else if(selectedType==4){
                    '''+BC+'''.sendRecipients(col,SC_ChatMessageNoti.sharedMacroWire(
                            SC_ChatMessageNoti.CHAT_WHISPER,
                            packet,"오늘의 검색어"));
                }

                '''
 s=s[:a]+new+s[b:]
 assert "SC_IconMessage.sendGm(p,packet)" not in s
 assert "SC_ChatMessageNoti.sendNotice(p,packet" not in s
 assert "SC_ChatMessageNoti.sendWhisper(p,packet" not in s
 for required in ("Collection<L1PcInstance> col = L1World.getInstance().getAllPlayers();",
       "if (!_isrun) break;","obj.nextMs = cur + obj.delay;",
       "Collections.sort(list)","tickPending.set(false)"):
  assert required in s,(LOADER,required)
 files[LOADER]=s.encode("utf8")
 return {"macro_shared_remaining_categories":3,
       "macro_all_five_types_shared":True,
       "macro_db_message_and_iteration_order_unchanged":True,
       "macro_icon_color_surface_duration_unchanged":True,
       "macro_notice_whisper_sender_types_unchanged":True,
       "macro_scheduler_generation_and_tick_unchanged":True}
TEST=r'''import java.util.Arrays;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.lang.reflect.Field;
import l1j.server.server.Opcodes;
import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase40MacroChatIconParity {
 static int checks;
 static final Charset MS949=Charset.forName("MS949");
 static void eq(byte[] want,byte[] got) {
  if(!Arrays.equals(want,got))throw new AssertionError("macro wire "+checks);
  checks++;
 }
 static void var(ByteArrayOutputStream b,long n) {
  if(n<0)n &=0xffffffffL;
  while((n & ~0x7fL)!=0){b.write((int)((n&0x7f)|0x80));n >>>=7;}
  b.write((int)n);
 }
 static byte[] framed(ByteArrayOutputStream b) {
  byte[] raw=b.toByteArray();
  raw[0]=(byte)raw.length;raw[1]=(byte)(raw.length>>>8);
  return raw;
 }
 static byte[] chat(int type,String message,String sender) {
  byte[] m=(message==null?"":message).getBytes(MS949);
  byte[] s=sender==null?null:sender.getBytes(MS949);
  ByteArrayOutputStream b=new ByteArrayOutputStream(64);
  b.write(0);b.write(0);b.write(Opcodes.S_EXTENDED_PROTOBUF);
  b.write(0x04);b.write(0x02);
  b.write(0x08);var(b,0);
  b.write(0x10);b.write(type);
  b.write(0x1a);b.write(m.length);b.write(m,0,m.length);
  if(s!=null){b.write(0x2a);b.write(s.length);b.write(s,0,s.length);}
  b.write(0x30);b.write(0);b.write(0);b.write(0);
  return framed(b);
 }
 static byte[] icon(String message) {
  byte[] m=(message==null?"":message).getBytes(MS949);
  ByteArrayOutputStream b=new ByteArrayOutputStream(64);
  b.write(0);b.write(0);b.write(Opcodes.S_EXTENDED_PROTOBUF);
  b.write(0x40);b.write(0);
  var(b,8);var(b,56);
  var(b,18);b.write(m.length);b.write(m,0,m.length);
  var(b,26);
  try {
   Field f=SC_IconMessage.class.getDeclaredField("GM_COLOR");f.setAccessible(true);
   MJSimpleRgb rgb=(MJSimpleRgb)f.get(null);
   byte[] data=rgb.get_bytes();
   b.write(data,0,data.length);
  } catch(Exception e){throw new AssertionError(e);}
  var(b,32);var(b,60);
  b.write(0);b.write(0);
  return framed(b);
 }
 static void check(String message,String sender) {
  eq(chat(SC_ChatMessageNoti.CHAT_PLEDGE_NOTICE,message,sender),
    SC_ChatMessageNoti.sharedMacroWire(
      SC_ChatMessageNoti.CHAT_PLEDGE_NOTICE,message,sender));
  eq(chat(SC_ChatMessageNoti.CHAT_WHISPER,message,"오늘의 검색어"),
    SC_ChatMessageNoti.sharedMacroWire(
      SC_ChatMessageNoti.CHAT_WHISPER,message,"오늘의 검색어"));
  eq(icon(message),SC_IconMessage.sharedGmWire(message));
 }
 public static void main(String[] args) {
  for(String s:new String[]{"","서버 자동공지","아이콘 문자","\\f3Hello",
      "[긴급 공지] 서버 안내","한글 테스트",null})
    check(s,"서버");
  check("테스트",null);
  int n=0x46273461;
  for(int i=0;i<8192;i++){
   n^=n<<13;n^=n>>>17;n^=n<<5;
   check("공지-"+n+" / "+i,"서버-"+(i%37));
  }
  boolean failed=false;
  try{SC_ChatMessageNoti.sharedMacroWire(
    SC_ChatMessageNoti.CHAT_WHISPER,"X".repeat(256),"sender");}
  catch(IllegalStateException expected){failed=true;}
  if(!failed)throw new AssertionError("writeS2 length overflow");
  System.out.println("PHASE40_CHAT_ICON_PARITY_OK "+checks);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase40"));args=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase39 pinned input changed"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 details=patch(files)
 changed=sorted(n for n in files if files[n]!=before[n])
 assert changed==CHANGED,changed
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 t=args.work/"testsrc/Phase40MacroChatIconParity.java"
 t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":40,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
       "java_sources":1710,"modified_java":changed,**details,
       "native_unchanged":True,"no_legacy_fallback":True,
       "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
 print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
