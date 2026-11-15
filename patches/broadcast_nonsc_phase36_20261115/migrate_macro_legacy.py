#!/usr/bin/env python3
"""Phase36: remove provably dead ServerPacket fallback from DB macro fanout,
make its packet storage strictly String, and share 1 frozen system/green
frame per scheduled broadcast. Other supported macro types remain in-order.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase35_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase36_FULL_SRC_20261115.zip"
PIN="df35393c246acd847b841178f41d75d50567e1e9"
BASE="src/l1j/server/MJServerMacroSystem/"
LOADER=BASE+"MJServerMacroLoader.java"
OBJ=BASE+"MJServerMacroObject.java"
TARGETS=sorted([LOADER,OBJ])
BC="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def patch(files):
 src=files[OBJ].decode("utf8")
 checks=("pcks.add(msg);","public Object get(){","Object sbp = pcks.get(curIdx);",
         "selectedType = packetTypes[curIdx];","if(++curIdx >= pcks.size())")
 for ck in checks:assert ck in src,(OBJ,ck)
 assert src.count("ArrayList<Object>")==2,src.count("ArrayList<Object>")
 src,n=re.subn(r"\bArrayList<Object>\b","ArrayList<String>",src)
 # Word-boundary after '>' does not match; replace exactly and count.
 if n!=2:
  src=files[OBJ].decode("utf8")
  assert src.count("ArrayList<Object>")==2
  src=src.replace("ArrayList<Object>","ArrayList<String>")
 src=src.replace("public Object get(){","public String get(){",1)
 src=src.replace("Object sbp = pcks.get(curIdx);","String sbp = pcks.get(curIdx);",1)
 assert "ArrayList<Object>" not in src and "public Object get()" not in src
 assert "import l1j.server.server.model.network.packet.ServerPacket;" in src
 src=src.replace("import l1j.server.server.model.network.packet.ServerPacket;\n","",1)
 files[OBJ]=src.encode("utf8")
 src=files[LOADER].decode("utf8")
 assert "import l1j.server.server.model.network.packet.ServerPacket;" in src
 start="                final Object packet = obj.get();"
 finish="obj.nextMs = cur + obj.delay;"
 assert src.count(start)==1 and src.count(finish)==1
 a=src.index(start);b=src.index(finish,a)
 old=src[a:b]
 for ck in (
   "Collection<L1PcInstance> col", # not inside this segment; checked below separately
 ):
  pass
 for ck in ("for (L1PcInstance p : col)", "obj.getSelectedType() == 0",
            "SC_SystemMessage.send(p, (String) packet)",
            "SC_PacketBox.send(p, SC_PacketBox.GREEN_MESSAGE, (String) packet)",
            "SC_IconMessage.sendGm(p, (String) packet)",
            "SC_ChatMessageNoti.sendNotice(p, (String) packet, Config.GAME_SERVER_NAME)",
            "SC_ChatMessageNoti.sendWhisper(p, (String) packet, \"오늘의 검색어\")",
            "packet instanceof ServerPacket","p.sendPackets((ServerPacket) packet, false)"):
  assert ck in old,(LOADER,ck)
 assert "Collection<L1PcInstance> col = L1World.getInstance().getAllPlayers();" in src
 assert "if (!_isrun) break;" in src
 new='''final String packet=obj.get();
                final int selectedType=obj.getSelectedType();
                if(selectedType==0){
                    '''+BC+'''.sendRecipients(col,SC_SystemMessage.sharedWire(packet));
                } else if(selectedType==2){
                    '''+BC+'''.sendRecipients(col,SC_PacketBox.sharedGreenWire(packet));
                } else {
                    for(L1PcInstance p:col){
                        if(selectedType==1){
                            SC_IconMessage.sendGm(p,packet);
                        } else if(selectedType==3){
                            SC_ChatMessageNoti.sendNotice(p,packet,Config.GAME_SERVER_NAME);
                        } else if(selectedType==4){
                            SC_ChatMessageNoti.sendWhisper(p,packet,"오늘의 검색어");
                        }
                    }
                }

                '''
 src=src[:a]+new+src[b:]
 src=src.replace("import l1j.server.server.model.network.packet.ServerPacket;\n","",1)
 assert "ServerPacket" not in src and "(String) packet" not in src
 assert "SC_SystemMessage.send(p," not in src
 assert "SC_PacketBox.send(p, SC_PacketBox.GREEN_MESSAGE" not in src
 assert "obj.nextMs = cur + obj.delay" in src and "Collections.sort(list)" in src
 assert "tickPending.set(false)" in src and "scheduleWithFixedDelay(" in src
 files[LOADER]=src.encode("utf8")
 return {"legacy_ServerPacket_macro_fallback_removed":1,
   "macro_shared_system_green_categories":2,
   "macro_pcks_strongly_typed_strings":True,
   "macro_type0_to4_mapping_unchanged":True,
   "macro_icon_notice_whisper_order_unchanged":True,
   "macro_db_scheduling_and_tick_owner_unchanged":True,
   "live_world_collection_reused":True}
TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase36MacroNoticeParity {
 static int count;
 static void eq(byte[] a,byte[] b){
  if(!Arrays.equals(a,b))throw new AssertionError("macro parity "+count);count++;
 }
 static byte[] system(String s){
  byte[] t=s.getBytes(Charset.forName("MS949"));byte[] w=new byte[t.length+5];
  w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
  w[2]=(byte)Opcodes.S_MESSAGE;w[3]=9;
  System.arraycopy(t,0,w,4,t.length);return w;
 }
 static byte[] green(String s){
  byte[] t=s.getBytes(Charset.forName("MS949"));byte[] w=new byte[t.length+6];
  w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
  w[2]=(byte)Opcodes.S_EVENT;w[3]=(byte)SC_PacketBox.GREEN_MESSAGE;
  w[4]=2;System.arraycopy(t,0,w,5,t.length);return w;
 }
 static void test(String s){
  eq(system(s),SC_SystemMessage.sharedWire(s));
  eq(green(s),SC_PacketBox.sharedGreenWire(s));
 }
 public static void main(String[] args){
  for(String s:new String[]{"","서버 자동공지","GM 안내","그린","한글","\\f3Hello"})test(s);
  int r=0x417ab35;
  for(int i=0;i<8192;i++){r^=r<<13;r^=r>>>17;r^=r<<5;test("[공지] "+r);}
  System.out.println("PHASE36_MACRO_PACKET_PARITY_OK "+count);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase36"));args=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase35 baseline changed"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 old=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 details=patch(files)
 changed=sorted(n for n in files if files[n]!=old[n])
 assert changed==TARGETS,changed
 assert files["CTransport.dll"]==old["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 t=args.work/"testsrc/Phase36MacroNoticeParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":36,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
   "java_sources":1710,"modified_java":changed,**details,
   "native_unchanged":True,"no_legacy_fallback":True,
   "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
 print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
