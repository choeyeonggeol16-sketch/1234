#!/usr/bin/env python3
"""Phase49: six in-game chat broadcast groups and three racing start packet
types use one immutable plaintext per fanout. Preserve chat spam/visibility,
party rank, live iteration and game start -> lap -> list -> ETC order.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase48_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase49_FULL_SRC_20261115.zip"
PIN="309b97d2f0750fddd62054d0c3342b7367f55a81"
BASE="src/l1j/server/"
CRAFT=BASE+"server/model/network/core/cs/packet/CS_Craft.java"
RACE=BASE+"server/model/L1Racing.java"
START=BASE+"server/model/network/core/sc/packet/SC_GameStart.java"
RAP=BASE+"server/model/network/core/sc/packet/SC_GameRap.java"
ETC=BASE+"server/model/network/core/sc/packet/SC_EtcPacket.java"
TARGETS=sorted([CRAFT,RACE,START,RAP,ETC])
BC="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def brace(s,sig,start=0):
 i=s.find(sig,start);assert i>=0,(sig,start)
 j=s.index("{",i);depth=0
 for k in range(j,len(s)):
  if s[k]=="{":depth+=1
  elif s[k]=="}":
   depth-=1
   if depth==0:return i,k+1,s[i:k+1]
 raise AssertionError(sig)
def shared(wire,name="shared"):
 return ("final byte[] chatBytes="+wire+";\n" if name=="chatShared" else "")+(
        "try(var "+name+"="+("chatBytes==null?null:" if name=="chatShared" else "")+
        "new "+BC+".CAsyncSendConstBuffer(\n"+
        ("chatBytes" if name=="chatShared" else wire)+
        ",Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n")
START_HELPER=r'''
    /** Immutable fixed 10-byte start frame, S_EVENT 0x40 0x05. */
    public static byte[] sharedWire() {
        return new byte[]{10,0,(byte)Opcodes.S_EVENT,0x40,0x05,0,0,0,0,0};
    }

'''
RAP_HELPER=r'''
    /** Immutable 8-byte race lap frame, retaining little-endian H(4), H(value). */
    public static byte[] sharedWire(int value) {
        return new byte[]{8,0,(byte)Opcodes.S_EVENT,0x43,4,0,
                (byte)value,(byte)(value>>>8)};
    }

'''
ETC_HELPER=r'''
    /** Constructor type 4: 10-byte S_EVENT 0x41 100 00 00 00 00 00. */
    public static byte[] sharedRacingWire() {
        return new byte[]{10,0,(byte)Opcodes.S_EVENT,0x41,100,0,0,0,0,0};
    }

'''
def patch_packets(files):
 for path,anchor,helper,required in (
   (START,"    public static boolean send(CIOClient client) {",START_HELPER,
    ("buffer.writeC(0x40);","buffer.writeC(0x05);")),
   (RAP,"    public static boolean send(CIOClient client, int value) {",RAP_HELPER,
    ("buffer.writeC(0x43);","buffer.writeH(0x04);","buffer.writeH(value);")),
   (ETC,"    public static boolean send(CIOClient client, int type) {",ETC_HELPER,
    ("case 4:","buffer.writeC(0x41);","buffer.writeC(100);"))
 ):
  s=files[path].decode("utf8")
  assert s.count(anchor)==1,(path,anchor)
  for text in required:assert text in s,(path,text)
  assert "public static byte[] sharedRacingWire(" not in s and "public static byte[] sharedWire(" not in s
  files[path]=s.replace(anchor,helper+anchor,1).encode("utf8")
def patch_chat(files):
 s=files[CRAFT].decode("utf8")
 old="packet.send(listener);"
 assert s.count(old)==5,(old,s.count(old))
 marker="for (L1PcInstance listener :"
 found=[]
 search=0
 while True:
  i=s.find(marker,search)
  if i<0:break
  a,b,loop=brace(s,marker,i)
  if old in loop:found.append((a,b,loop))
  search=i+len(marker)
 assert len(found)==5,found
 for a,b,loop in reversed(found):
  assert loop.count(old)==1
  assert "SpamTable.getInstance().getExcludeTable(listener.getId())" in loop
  modified=loop.replace(old,BC+".CSharedBroadcast.sendRecipient(chatShared,listener);",1)
  wrap=shared("SC_NewChat.sharedWire(packet)","chatShared")+modified+"\n}"
  s=s[:a]+wrap+s[b:]
 assert old not in s
 selfcall="sChatPacket.send(pc);"
 recpcall="sChatPacket.send(listener);"
 assert s.count(selfcall)==1 and s.count(recpcall)==1
 sig="for (L1PcInstance listener : L1World.getInstance().getVisiblePlayerFast(pc)) {"
 a,b,loop=brace(s,sig)
 assert recpcall in loop and "listener.isOutsideChat()" in loop
 assert "SpamTable.getInstance().getExcludeTable(listener.getId())" in loop
 i=s.rfind("final L1ExcludingList spamList =",0,s.index(selfcall))
 assert i>=0 and i<a
 segment=s[i:b]
 assert segment.count(selfcall)==1 and segment.count(recpcall)==1
 segment=segment.replace(selfcall,BC+".CSharedBroadcast.sendRecipient(chatShared,pc);",1)
 segment=segment.replace(recpcall,BC+".CSharedBroadcast.sendRecipient(chatShared,listener);",1)
 wrap=shared("SC_NewChat.sharedWire(sChatPacket)","chatShared")+segment+"\n}"
 s=s[:i]+wrap+s[b:]
 assert selfcall not in s and recpcall not in s
 for marker in ("SC_NewChat.prepare(pc, 4, chatType, chatText, \"\")",
                "SpamTable.getInstance().getExcludeTable(listener.getId())",
                "LoggerInstance.getInstance().addChat(ChatType.Normal, pc, chatText);"):
  assert marker in s
 files[CRAFT]=s.encode("utf8")
def patch_race(files):
 s=files[RACE].decode("utf8")
 sig="for(L1PcInstance pc : toArray(일반)){"
 starts=[];search=0
 while True:
  i=s.find(sig,search)
  if i<0:break
  a,b,loop=brace(s,sig,i)
  if "SC_GameRap.send(pc, 1);" in loop:starts.append((a,b,loop))
  search=i+len(sig)
 assert len(starts)==1,starts
 a,b,loop=starts[0]
 oldcalls=(
   "SC_GameStart.send(pc);",
   "SC_GameRap.send(pc, 1);",
   "SC_EtcPacket.send(pc, 4);"
 )
 assert all(loop.count(call)==1 for call in oldcalls),loop[:800]
 assert "L1PolyMorph.doPoly(pc," in loop and "SC_GameList.send(pc, i++);" in loop
 modified=loop
 for old,new in zip(oldcalls,(
     BC+".CSharedBroadcast.sendRecipient(startShared,pc);",
     BC+".CSharedBroadcast.sendRecipient(rapShared,pc);",
     BC+".CSharedBroadcast.sendRecipient(etcShared,pc);"
 )):
  modified=modified.replace(old,new,1)
 opening=("try(var startShared=new "+BC+".CAsyncSendConstBuffer(\n"+
          "SC_GameStart.sharedWire(),Integer.MIN_VALUE,Integer.MAX_VALUE);\n"+
          "var rapShared=new "+BC+".CAsyncSendConstBuffer(\n"+
          "SC_GameRap.sharedWire(1),Integer.MIN_VALUE,Integer.MAX_VALUE);\n"+
          "var etcShared=new "+BC+".CAsyncSendConstBuffer(\n"+
          "SC_EtcPacket.sharedRacingWire(),Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n")
 s=s[:a]+opening+modified+"\n}"+s[b:]
 files[RACE]=s.encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase49ChatRaceParity {
 static int cases;
 static void eq(byte[] a,byte[] b) {
  if(!Arrays.equals(a,b))throw new AssertionError("wire "+cases);
  cases++;
 }
 static byte[] start() {
  return new byte[]{10,0,(byte)Opcodes.S_EVENT,0x40,5,0,0,0,0,0};
 }
 static byte[] rap(int n){
  return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)8).put((byte)Opcodes.S_EVENT).put((byte)0x43)
   .putShort((short)4).putShort((short)n).array();
 }
 static byte[] etc() {
  return new byte[]{10,0,(byte)Opcodes.S_EVENT,0x41,100,0,0,0,0,0};
 }
 static byte[] chat(String s,int type) {
  byte[] t=s.getBytes(Charset.forName("MS949"));
  byte[] b=new byte[t.length+15+(type==0?6:0)];int i=0;
  b[i++]=(byte)b.length;b[i++]=(byte)(b.length>>>8);
  b[i++]=(byte)Opcodes.S_EXTENDED_PROTOBUF;
  b[i++]=4;b[i++]=2;b[i++]=8;b[i++]=0;
  b[i++]=0x10;b[i++]=(byte)type;b[i++]=0x1a;b[i++]=(byte)t.length;
  System.arraycopy(t,0,b,i,t.length);i+=t.length;
  b[i++]=0x2a;b[i++]=0;
  if(type==0){b[i++]=0x38;b[i++]=0;b[i++]=0x40;b[i++]=0;b[i++]=0x48;b[i++]=0;}
  b[i++]=0;b[i++]=0;
  if(i!=b.length)throw new AssertionError("length");
  return b;
 }
 static void check(int x){
  eq(rap(x),SC_GameRap.sharedWire(x));
  // World, guild, party, alliance, guardian and local chat all share one
  // type-4 packet writer; no recipient-specific payload bytes.
  for(int type:new int[]{0,4,11,13,14,17}) {
   String text="메시지-"+x;
   eq(chat(text,type),SC_NewChat.sharedWire(SC_NewChat.prepare(null,4,type,text,"")));
  }
 }
 public static void main(String[]args){
  eq(start(),SC_GameStart.sharedWire());
  eq(etc(),SC_EtcPacket.sharedRacingWire());
  for(int x:new int[]{0,1,2,255,256,65535,-1,Integer.MIN_VALUE,Integer.MAX_VALUE})check(x);
  int x=0x3189aee;
  for(int i=0;i<4096;i++){x^=x<<13;x^=x>>>17;x^=x<<5;check(x);}
  System.out.println("PHASE49_CHAT_RACING_PARITY_OK "+cases);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase49"));a=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase48 source mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 old=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch_packets(files);patch_chat(files);patch_race(files)
 changed=sorted(n for n in files if files[n]!=old[n]);assert changed==TARGETS,changed
 assert files["CTransport.dll"]==old["CTransport.dll"]
 a.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=a.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 test=a.work/"testsrc/Phase49ChatRaceParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":49,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
  "java_sources":1710,"modified_java":changed,
  "chat_direct_send_sites_removed":7,"racing_direct_send_sites_removed":3,
  "chat_group_fanout_loops":6,
  "chat_visibility_exclusion_rank_and_party_filters_preserved":True,
  "self_then_nearby_chat_order_preserved":True,
  "game_morph_start_rap_list_etc_message_fifo_preserved":True,
  "racing_personalized_rank_list_left_intact":True,
  "native_unchanged":True,"no_fallback":True,
  "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
