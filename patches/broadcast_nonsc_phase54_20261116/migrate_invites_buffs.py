#!/usr/bin/env python3
"""Phase54: replace two clan-party final system notices and GM area buff
notifications with one shared plaintext frame per actual fanout. No new queue,
worker, fallback, recipient reordering or modifications to side effects.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase53_FULL_SRC_20261116.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase54_FULL_SRC_20261116.zip"
PIN="15c63a1834e0954899b85a649152c0ec02085183"
BASE="src/l1j/server/server/"
USER=BASE+"UserCommands.java"
GM=BASE+"GMCommands.java"
CHANGED=sorted([USER,GM])
BC="l1j.server.server.model.network.core.broadcast"
SY="l1j.server.server.model.network.core.sc.packet.SC_SystemMessage"
def blob(data):return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
def block(text,sig,begin=0):
 a=text.find(sig,begin);assert a>=0,(sig,begin)
 opening=text.find("{",a);assert opening>=0
 d=0
 for i in range(opening,len(text)):
  if text[i]=="{":d+=1
  elif text[i]=="}":
   d-=1
   if d==0:return a,i+1,text[a:i+1]
 raise AssertionError(("unclosed block",sig))
def patch_user(files):
 s=files[USER].decode("utf8")
 for method in ("public void BloodParty(","public void BloodParty3("):
  start,end,orig=block(s,method)
  signature="for (L1PcInstance member : clan.getOnlineClanMember()) {"
  at,b,loop=block(orig,signature)
  regex=re.compile(r'\bmember\.sendPackets\(("(?:\\.|[^"\\])*")\);')
  hits=list(regex.finditer(loop))
  assert len(hits)==1,(method,len(hits))
  literal=hits[0].group(1)
  assert "Auto-loot" in literal or "자동분배" in literal
  assert loop.count("CSharedBroadcast.sendRecipient(ynShared,member)") == 1
  assert loop.count("CSharedBroadcast.sendRecipient(shared,member)") == 1
  for required in ("member.setPartyID(pc.getId());","member.isPrivateShop()",
                   "member.isInParty()","member.getAutoHunt()"):
   assert required in loop,(method,required)
  mod=loop[:hits[0].start()]+BC+".CSharedBroadcast.sendRecipient(noticeShared,member);"+loop[hits[0].end():]
  new=("try(var noticeShared=new "+BC+".CAsyncSendConstBuffer(\n"+
       SY+".sharedWire("+literal+"),Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n"+
       mod+"\n}")
  orig=orig[:at]+new+orig[b:]
  s=s[:start]+orig+s[end:]
 files[USER]=s.encode("utf8")
def patch_gm(files):
 s=files[GM].decode("utf8")
 a,b,method=block(s,"private void applyGmAreaBuff(")
 needle="SC_SystemMessage.send(pc, message);"
 assert method.count(needle)==1
 q="for (L1PcInstance pc :"
 i,j,loop=block(method,q)
 assert loop.count(needle)==1
 for key in ("L1World.getInstance().getVisiblePlayer(gm, 22)",
             "pc.isPrivateShop()","pc.isDead()","pc.isGhost()",
             "L1SkillUse.cast(","appliedCount"):
  assert key in loop,(key,loop[:350])
 modified=loop.replace(needle,BC+".CSharedBroadcast.sendRecipient(buffShared,pc);",1)
 replacement=("try(var buffShared=new "+BC+".CAsyncSendConstBuffer(\n"+
              SY+".sharedWire(message),Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n"+
              modified+"\n}")
 method=method[:i]+replacement+method[j:]
 s=s[:a]+method+s[b:]
 files[GM]=s.encode("utf8")
TEST=r'''import java.nio.charset.Charset;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
public final class Phase54InviteBuffParity {
 static int total;
 static final Charset MS949=Charset.forName("MS949");
 static void check(String s){
  final byte[] msg=s.getBytes(MS949);
  final byte[] b=new byte[msg.length+5];
  b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);
  b[2]=(byte)Opcodes.S_MESSAGE;b[3]=9;
  System.arraycopy(msg,0,b,4,msg.length);
  if(!Arrays.equals(b,SC_SystemMessage.sharedWire(s)))
   throw new AssertionError("wire mismatch "+total);
  total++;
 }
 public static void main(String[]args){
  for(String v:new String[]{"","\\fQ자동분배는 한 화면에 파티원들이 있을 경우 분배됩니다.",
   "\\fQAuto-loot only works when party members are on the same screen.",
   "운영자 범위 버프 적용", "테스트", "\\f3원형 버프"})check(v);
  int x=0x54b60c1;
  for(int i=0;i<8192;i++){
   x^=x<<13;x^=x>>>17;x^=x<<5;
   check("동일 수신자 알림 "+x);
  }
  System.out.println("PHASE54_INVITE_BUFF_PARITY_OK "+total);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase54"));a=p.parse_args()
 assert blob(SRC.read_bytes())==PIN,"phase53 source blob mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  contents={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 old=contents.copy()
 assert sum(n.endswith(".java") for n in contents)==1710 and "CTransport.dll" in contents
 patch_user(contents);patch_gm(contents)
 changed=sorted(n for n in contents if old[n]!=contents[n]);assert changed==CHANGED,changed
 assert old["CTransport.dll"]==contents["CTransport.dll"]
 a.work.mkdir(parents=True,exist_ok=True)
 for n,buf in contents.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=a.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(buf)
 t=a.work/"testsrc/Phase54InviteBuffParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,buf in sorted(contents.items()):z.writestr(n,buf)
 with zipfile.ZipFile(OUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(contents)
 report={"phase":54,"input_blob":PIN,
         "output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
         "java_sources":1710,"modified_java":changed,
         "clan_party_final_notices_migrated":2,
         "gm_area_buff_notices_migrated":1,
         "clan_invite_yn_green_system_fifo_kept":True,
         "party_invite_recipient_filter_and_state_kept":True,
         "gm_buff_skill_apply_before_notice_kept":True,
         "native_unchanged":True,"no_fallback":True,
         "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
