#!/usr/bin/env python3
"""Phase48: world/trade chat uses one immutable plaintext framed chat packet
per broadcast, not one encoder invocation per listener. Preserve live-world
walk, per-receiver spam table, show-trade/world predicates, chat order and log.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase47_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase48_FULL_SRC_20261115.zip"
PIN="89d08e95e3b3a7f3d532c88e45cac16f39ea4eb8"
CHAT="src/l1j/server/server/model/network/core/sc/packet/SC_NewChat.java"
CRAFT="src/l1j/server/server/model/network/core/cs/packet/CS_Craft.java"
TARGETS=sorted([CHAT,CRAFT])
BC="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def bounds(s,sig,start=0):
 i=s.find(sig,start)
 if i<0:raise AssertionError(("not found",sig))
 at=s.index("{",i);count=0
 for k in range(at,len(s)):
  if s[k]=="{":count+=1
  elif s[k]=="}":
   count-=1
   if count==0:return i,k+1,s[i:k+1]
 raise AssertionError(("unbalanced",sig))
def patch(files):
 s=files[CHAT].decode("utf8")
 old="    private static byte[] sharedWire(ChatData d) {"
 assert s.count(old)==1 and "final byte[] wire=sharedWire(data);" in s
 assert "writePayload(frame.buffer(), type, chatType, textBytes, targetNameBytes," in s
 assert "final ChatData data=prepare(sender,type,chatType,text,targetName);" in s
 s=s.replace(old,"    public static byte[] sharedWire(ChatData d) {",1)
 files[CHAT]=s.encode("utf8")
 s=files[CRAFT].decode("utf8")
 sig="for (L1PcInstance listener : L1World.getInstance().getAllPlayers()) {"
 needle="SC_NewChat.send(listener, pc, 4, chatType, text, \"\");"
 candidates=[];search=0
 while True:
  k=s.find(sig,search)
  if k<0:break
  a,b,loop=bounds(s,sig,k)
  if needle in loop:candidates.append((a,b,loop))
  search=k+len(sig)
 assert len(candidates)==1,len(candidates)
 a,b,loop=candidates[0]
 assert loop.count(needle)==2
 for marker in ("SpamTable.getInstance().getExcludeTable(listener.getId())",
                "spam.contains(0, pc.getName())",
                "listener.isShowTradeChat()","listener.isShowWorldChat()",
                "chatType == 12","chatType == 3"):
  assert marker in loop,marker
 assert s.index("SC_PacketBox.send(pc, SC_PacketBox.FOOD, pc.get_food());")<a
 assert "LoggerInstance.getInstance().addChat(ChatType.Global, pc, text);" in s[b:]
 replacement=loop.replace(needle,BC+".CSharedBroadcast.sendRecipient(chatShared,listener);")
 wrapped='''final byte[] chatWire=(chatType==3 || chatType==12)
         ? SC_NewChat.sharedWire(SC_NewChat.prepare(pc,4,chatType,text,""))
         : null;
         try(var chatShared=chatWire==null ? null : new '''+BC+'''.CAsyncSendConstBuffer(
               chatWire,Integer.MIN_VALUE,Integer.MAX_VALUE)){
         '''+replacement+'''
         }'''
 s=s[:a]+wrapped+s[b:]
 assert needle not in s
 files[CRAFT]=s.encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_NewChat;
public final class Phase48WorldChatParity {
 static int count;
 static Charset MS949=Charset.forName("MS949");
 static byte[] oldWire(int chatType,String s) {
  byte[] t=s.getBytes(MS949);
  final int size=t.length+15;
  byte[] v=new byte[size];int i=0;
  v[i++]=(byte)size;v[i++]=(byte)(size>>>8);
  v[i++]=(byte)Opcodes.S_EXTENDED_PROTOBUF;
  v[i++]=4;v[i++]=2;v[i++]=8;v[i++]=0;
  v[i++]=0x10;v[i++]=(byte)chatType;
  v[i++]=0x1a;v[i++]=(byte)t.length;
  System.arraycopy(t,0,v,i,t.length);i+=t.length;
  v[i++]=0x2a;v[i++]=0;v[i++]=0;v[i++]=0;
  if(i!=size)throw new AssertionError("size "+i+" != "+size);
  return v;
 }
 static void check(int chatType,String text) {
  byte[] actual=SC_NewChat.sharedWire(SC_NewChat.prepare(null,4,chatType,text,""));
  byte[] want=oldWire(chatType,text);
  if(!Arrays.equals(want,actual))throw new AssertionError("wire parity "+count);
  count++;
 }
 public static void main(String[] args){
  for(String s:new String[]{"","월드 채팅","거래 채팅","\\f2안녕하세요","이벤트", "X".repeat(255),"한".repeat(127)})
   for(int c:new int[]{3,12})check(c,s);
  int n=0x145bcaf;
  for(int i=0;i<8192;i++){n^=n<<13;n^=n>>>17;n^=n<<5;check(3,"월드-"+n);check(12,"거래-"+n);}
  System.out.println("PHASE48_WORLD_CHAT_PARITY_OK "+count);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase48"));a=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase47 source pin mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 old=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch(files)
 changed=sorted(n for n in files if files[n]!=old[n])
 assert changed==TARGETS,changed
 assert files["CTransport.dll"]==old["CTransport.dll"]
 a.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=a.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 test=a.work/"testsrc/Phase48WorldChatParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":48,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
         "java_sources":1710,"modified_java":changed,
         "world_trade_direct_send_sites_removed":2,
         "one_shared_wire_per_world_trade_message":True,
         "spam_list_per_listener_preserved":True,
         "show_trade_world_predicates_preserved":True,
         "live_all_players_iteration_order_preserved":True,
         "world_chat_logging_after_loop_preserved":True,
         "native_unchanged":True,"no_fallback":True,
         "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
