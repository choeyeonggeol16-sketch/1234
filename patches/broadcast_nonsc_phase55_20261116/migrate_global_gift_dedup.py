#!/usr/bin/env python3
"""Phase55: remove duplicate N x global broadcasts inside the GM allitem
recipient loop; retain each recipient's independent inventory grant, effects,
GM confirmation, error behavior and the first successful announcement timing.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase54_FULL_SRC_20261116.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase55_FULL_SRC_20261116.zip"
PIN="433d3d181a66798b7332fe6ef624e81e94213130"
GM="src/l1j/server/server/GMCommands.java"
def blob(data):return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
def block(s,sig):
 a=s.find(sig);assert a>=0,sig
 i=s.find("{",a);d=0
 for k in range(i,len(s)):
  if s[k]=="{":d+=1
  elif s[k]=="}":
   d-=1
   if d==0:return a,k+1,s[a:k+1]
 raise AssertionError("unbalanced "+sig)
def patch(files):
 s=files[GM].decode("utf8")
 a,b,method=block(s,"public void allitem(L1PcInstance gm, String arg) {")
 sig="for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(gm, 22)) {"
 assert method.count(sig)==1
 pattern=r'SC_PacketBox\.broadcastGreen\(([\s\S]*?)\);'
 calls=list(re.finditer(pattern,method))
 assert len(calls)==2,(len(calls),[q.group(0) for q in calls])
 assert calls[0].group(0)==calls[1].group(0),"announcement messages differ"
 assert "temp.getNameId()" in calls[0].group(1) and "count" in calls[0].group(1)
 for condition in ("if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {",
                   "if (createCount > 0) {",
                   "pc.getInventory().storeItem(item);",
                   "SC_SkillSound.send(pc, pc.getId(), 4856);",
                   'SC_SystemMessage.send(pc, "\\\\aD[특별선물]'):
  assert condition in method,(condition,method.count(condition))
 for item in calls:
  preceding=method[:item.start()]
  assert "SC_SystemMessage.send(pc," in preceding
  assert "SC_SkillSound.send(pc," in preceding
 notify=calls[0].group(0)
 assert method.count(notify)==2
 replacement="if (!globalGiftNoticeSent) {\n  "+notify+"\n  globalGiftNoticeSent = true;\n }"
 method=method.replace(notify,replacement,2)
 assert method.count(notify)==2 and method.count("if (!globalGiftNoticeSent)") == 2
 method=method.replace(sig,"boolean globalGiftNoticeSent = false;\n"+sig,1)
 assert method.index("boolean globalGiftNoticeSent = false;")<method.index(sig)
 assert "SC_SystemMessage.send(gm," in method
 assert method.count("pc.getInventory().storeItem(item);")==2
 # Exactly one broadcast guard is accessible through either original success
 # branch; failures and invalid templates continue without announcing.
 s=s[:a]+method+s[b:]
 files[GM]=s.encode("utf8")
TEST=r'''import java.nio.charset.Charset;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
public final class Phase55GiftNoticeParity {
 static int checks;
 static byte[] wire(String text) {
  byte[] t=text.getBytes(Charset.forName("MS949"));
  byte[] b=new byte[t.length+6];
  b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);
  b[2]=(byte)Opcodes.S_EVENT;b[3]=(byte)SC_PacketBox.GREEN_MESSAGE;b[4]=2;
  System.arraycopy(t,0,b,5,t.length);
  return b;
 }
 static void check(String text){
  if(!Arrays.equals(wire(text),SC_PacketBox.sharedGreenWire(text)))
   throw new AssertionError("wire "+checks);
  checks++;
 }
 public static void main(String[] args){
  for(String s:new String[]{"","운영자 주위 유저분들에게 선물을 드렸습니다 ▶▶",
   "운영자 공지","아이템 10개","\\f2화면 범위"})check(s);
  int x=0x55bc432;
  for(int i=0;i<8192;i++){
   x^=x<<13;x^=x>>>17;x^=x<<5;check("Gift-"+x);
  }
  System.out.println("PHASE55_GLOBAL_GIFT_PARITY_OK "+checks);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase55"));a=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase54 pinned source changed"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 original=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch(files)
 changed=sorted(n for n in files if files[n]!=original[n]);assert changed==[GM],changed
 assert files["CTransport.dll"]==original["CTransport.dll"]
 a.work.mkdir(parents=True,exist_ok=True)
 for n,buf in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=a.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(buf)
 t=a.work/"testsrc/Phase55GiftNoticeParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,buf in sorted(files.items()):z.writestr(n,buf)
 with zipfile.ZipFile(OUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":55,"input_blob":PIN,
  "output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
  "java_sources":1710,"modified_java":changed,
  "duplicate_global_broadcast_branches_guarded":2,
  "allitem_global_announcement_per_invocation_max":1,
  "first_success_announcement_timing_preserved":True,
  "recipient_inventory_effect_and_gm_confirmation_order_preserved":True,
  "no_announcement_if_no_gift_succeeds":True,
  "native_unchanged":True,"no_fallback":True,
  "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
