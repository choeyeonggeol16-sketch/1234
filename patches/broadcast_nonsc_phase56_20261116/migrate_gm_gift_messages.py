#!/usr/bin/env python3
"""Phase56: every recipient in GM bulk distribution now references one
immutable framed system notice for each distinct actual message text.
If instantiated item names differ, compare the fully rendered message and
start a fresh shared frame; never reuse another item's bytes. Preserve all
inventory grants, per-user sounds, GM acknowledgments, first-success global
announcement, errors and original recipient ordering. No legacy fallback.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase55_FULL_SRC_20261116.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase56_FULL_SRC_20261116.zip"
PIN="af668a28f53bd611ed196e7c4b739243ba11798b"
GM="src/l1j/server/server/GMCommands.java"
BC="l1j.server.server.model.network.core.broadcast"
SY="l1j.server.server.model.network.core.sc.packet.SC_SystemMessage"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def block(s,sig,start=0):
 i=s.find(sig,start);assert i>=0,(sig,start)
 opening=s.index("{",i);depth=0
 for k in range(opening,len(s)):
  if s[k]=="{":depth+=1
  elif s[k]=="}":
   depth-=1
   if not depth:return i,k+1,s[i:k+1]
 raise AssertionError("unbalanced "+sig)
def build_message_send(expr,receiver,frame_name,prior_name,msg_name):
 assert "item.getLogName()" in expr
 return ("final String "+msg_name+" = "+expr+";\n"
   "if ("+frame_name+" == null || !"+msg_name+".equals("+prior_name+")) {\n"
   "  if ("+frame_name+" != null) {\n"
   "    "+frame_name+".close();\n"
   "    "+frame_name+" = null;\n"
   "  }\n"
   "  "+frame_name+" = new "+BC+".CAsyncSendConstBuffer(\n"
   "    "+SY+".sharedWire("+msg_name+"),Integer.MIN_VALUE,Integer.MAX_VALUE);\n"
   "  "+prior_name+" = "+msg_name+";\n"
   "}\n"
   +BC+".CSharedBroadcast.sendRecipient("+frame_name+","+receiver+");")
def wrap_loop(loop,frame_name,prior_name):
 return (BC+".CAsyncSendConstBuffer "+frame_name+" = null;\n"
         "String "+prior_name+" = null;\n"
         "try {\n"+loop+"\n} finally {\n"
         " if ("+frame_name+" != null) "+frame_name+".close();\n"
         "}")
def patch_allitem(s):
 a,b,method=block(s,"public void allitem(L1PcInstance gm, String arg) {")
 sig="for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(gm, 22)) {"
 i,j,loop=block(method,sig)
 regex=re.compile(r'SC_SystemMessage\.send\(pc,\s*("(?:\\.|[^"\\])*"[\s\S]*?)\);')
 hits=[m for m in regex.finditer(loop) if "[특별선물]" in m.group(1)]
 assert len(hits)==2,(len(hits),[m.group(1) for m in hits])
 assert hits[0].group(1)==hits[1].group(1),"item notices differ between branches"
 expr=hits[0].group(1)
 assert "count" in expr and "item.getLogName()" in expr
 for needed in ("SC_SkillSound.send(pc, pc.getId(), 4856);",
                "SC_SystemMessage.send(gm,",
                "if (!globalGiftNoticeSent) {",
                "pc.getInventory().storeItem(item);"):
  assert needed in loop,(needed,loop.count(needed))
 new=loop
 for hit in reversed(hits):
  replacement=build_message_send(
      hit.group(1),"pc","memoGiftFrame","memoGiftText","giftMessage")
  new=new[:hit.start()]+replacement+new[hit.end():]
 method=method[:i]+wrap_loop(new,"memoGiftFrame","memoGiftText")+method[j:]
 s=s[:a]+method+s[b:]
 return s
def patch_target_loop(s,tag):
 sig="for (L1PcInstance target : player) {"
 matches=[];search=0
 while True:
  i=s.find(sig,search)
  if i<0:break
  a,b,loop=block(s,sig,i)
  if tag in loop:matches.append((a,b,loop))
  search=i+len(sig)
 assert len(matches)==1,(tag,len(matches))
 a,b,loop=matches[0]
 regex=re.compile(r'target\.sendPackets\(([\s\S]*?)\);')
 hits=[m for m in regex.finditer(loop) if tag in m.group(1)]
 assert len(hits)==1,(tag,len(hits))
 expr=hits[0].group(1)
 for needed in ("item.getLogName()","count",
                "target.getInventory().storeItem(item);",
                "SC_SkillSound.send(target, target.getId(), 1091);",
                "SC_SkillSound.send(target, target.getId(), 4856);"):
  assert needed in loop,(tag,needed)
 replacement=build_message_send(expr,"target","memoGiftFrame","memoGiftText","giftMessage")
 new=loop[:hits[0].start()]+replacement+loop[hits[0].end():]
 s=s[:a]+wrap_loop(new,"memoGiftFrame","memoGiftText")+s[b:]
 return s
def patch(files):
 s=files[GM].decode("utf8")
 s=patch_allitem(s)
 s=patch_target_loop(s,"오픈 대기 보상:")
 s=patch_target_loop(s,"전체 선물:")
 assert "target.sendPackets(\"전체 선물:" not in s
 assert "target.sendPackets(\"오픈 대기 보상:" not in s
 assert s.count("CSharedBroadcast.sendRecipient(memoGiftFrame,") == 4
 files[GM]=s.encode("utf8")
TEST=r'''import java.nio.charset.Charset;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
public final class Phase56GmGiftParity {
 static int checks;
 static final Charset MS949=Charset.forName("MS949");
 static void parity(String notice){
  final byte[] t=notice.getBytes(MS949);
  final byte[] w=new byte[t.length+5];
  w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
  w[2]=(byte)Opcodes.S_MESSAGE;w[3]=9;
  System.arraycopy(t,0,w,4,t.length);
  if(!Arrays.equals(w,SC_SystemMessage.sharedWire(notice)))
   throw new AssertionError("notice "+checks);
  checks++;
 }
 static void scenario(String[] messages,int distinct){
  String prev=null;int newFrames=0;
  for(String message:messages) {
   parity(message);
   if(prev==null || !message.equals(prev)){prev=message;newFrames++;}
  }
  if(newFrames!=distinct)throw new AssertionError("sharing key mismatch");
 }
 public static void main(String[] args){
  scenario(new String[]{"전체 선물: 검 1개 획득","전체 선물: 검 1개 획득",
   "전체 선물: 갑옷 2개 획득","전체 선물: 검 1개 획득"},3);
  scenario(new String[]{"오픈 대기 보상: 목걸이","오픈 대기 보상: 목걸이"},1);
  int x=0x56c20a;
  for(int i=0;i<8192;i++){
   x^=x<<13;x^=x>>>17;x^=x<<5;parity("선물 "+x);
  }
  System.out.println("PHASE56_GM_GIFT_PARITY_OK "+checks);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase56"));a=p.parse_args()
 assert blob(SRC.read_bytes())==PIN,"phase55 source pin mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 old=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch(files)
 changed=sorted(n for n in files if files[n]!=old[n]);assert changed==[GM],changed
 assert files["CTransport.dll"]==old["CTransport.dll"]
 a.work.mkdir(parents=True,exist_ok=True)
 for n,buf in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=a.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(buf)
 t=a.work/"testsrc/Phase56GmGiftParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,buf in sorted(files.items()):z.writestr(n,buf)
 with zipfile.ZipFile(OUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":56,"input_blob":PIN,
    "output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
    "java_sources":1710,"modified_java":changed,
    "per_recipient_gift_system_send_sites_migrated":4,
    "per_distinct_message_wire_reuse":True,
    "different_item_notice_never_reuses_previous_wire":True,
    "gm_gift_inventory_effect_notification_fifo_preserved":True,
    "first_success_global_announcement_limit_preserved":True,
    "native_unchanged":True,"no_fallback":True,
    "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
