#!/usr/bin/env python3
"""Phase53: shared True Target notification and combat/clan gift messages.
No mutation to skill application, party filtering, CacheD receipt commit,
per-character sound, stat updates, or packet FIFO. No legacy fallback.
"""
from pathlib import Path
import re,argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase52_FULL_SRC_20261116.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase53_FULL_SRC_20261116.zip"
PIN="9c86be9455146421683f495170751c1f14375b2a"
BASE="src/l1j/server/server/"
SKILL=BASE+"model/character/skill/SkillHandlers.java"
REWARD=BASE+"model/item/function/ItemUseConsumableRewards.java"
ACTIVE=BASE+"model/item/function/ItemUseActiveSkills.java"
TARGETS=sorted([SKILL,REWARD,ACTIVE])
BC="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def block(s,signature,start=0):
 i=s.find(signature,start)
 assert i>=0,(signature,start)
 op=s.find("{",i)
 assert op>=0
 depth=0
 for k in range(op,len(s)):
  if s[k]=="{":depth+=1
  elif s[k]=="}":
   depth-=1
   if depth==0:return i,k+1,s[i:k+1]
 raise AssertionError(("unbalanced",signature))
def shared(wire,name):
 return ("try(var "+name+"=new "+BC+".CAsyncSendConstBuffer(\n"+
         wire+",Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n")
def find_loop_for(s,needle):
 sig="for (L1PcInstance player :"
 loops=[];start=0
 while True:
  i=s.find(sig,start)
  if i<0:break
  a,b,part=block(s,sig,i)
  if needle in part:loops.append((a,b,part))
  start=i+len(sig)
 assert len(loops)==1,(needle,len(loops))
 return loops[0]
def patch_target(files):
 s=files[SKILL].decode("utf8")
 anchor="final int targetId ="
 start=s.find(anchor)
 assert start>=0
 a,b,method=block(s,"private static HandlerResult onTrueTarget(") if "private static HandlerResult onTrueTarget(" in s else (None,None,None)
 if method is None:
  # Method name varies by skill handler refactor; the owned block is scoped
  # around exactly one caster and one clan-recognizer fanout.
  begin=s.rfind("private static HandlerResult",0,start)
  assert begin>=0
  a,b,method=block(s,"private static HandlerResult",begin)
 original="SC_TrueTargetNew.send(pri, targetId, true);"
 target="SC_TrueTargetNew.send(pc, targetId, true);"
 assert method.count(original)==1 and method.count(target)==1,(method.count(original),method.count(target))
 i=method.index(original)
 sig="for (L1PcInstance pc"
 k,l,loop=block(method,sig,i)
 assert target in loop and "pri.getClanid() == pc.getClanid()" in loop
 assert "TrueTargetManager.apply(" in method[l:],"target manager must remain after fanout"
 body=method[i:l]
 body=body.replace(original,BC+".CSharedBroadcast.sendRecipient(targetShared,pri);",1)
 body=body.replace(target,BC+".CSharedBroadcast.sendRecipient(targetShared,pc);",1)
 wrap=shared("SC_TrueTargetNew.sharedWire(targetId,true)","targetShared")+body+"\n}"
 method=method[:i]+wrap+method[l:]
 s=s[:a]+method+s[b:]
 files[SKILL]=s.encode("utf8")
def patch_reward(files):
 s=files[REWARD].decode("utf8")
 a=s.index("pointsClan.addWarPointCommitted(points);")
 loop_sig="for (L1PcInstance member : pointsClan.getOnlineClanMember()) {"
 i,j,loop=block(s,loop_sig,a)
 rx=re.compile(r'member\.sendPackets\((.*?)\);')
 match=list(rx.finditer(loop))
 assert len(match)==1,("reward system sends",len(match))
 expr=match[0].group(1)
 assert "pc.getName()" in expr and "points" in expr,expr
 prior=loop[:match[0].start()]
 assert "CSharedBroadcast.sendRecipient(shared,member);" in prior
 changed=loop[:match[0].start()]+BC+".CSharedBroadcast.sendRecipient(systemShared,member);"+loop[match[0].end():]
 wrapped=shared("SC_SystemMessage.sharedWire("+expr+")","systemShared")+changed+"\n}"
 s=s[:i]+wrapped+s[j:]
 # System message remains after GREEN_MESSAGE within COMMIT callback.
 assert "pointsClan.addWarPointCommitted(points);" in s[:i]
 files[REWARD]=s.encode("utf8")
def patch_active(files):
 s=files[ACTIVE].decode("utf8")
 specs=[
   ("프라임의 효과가 적용되었습니다.",("프라임의 효과가 적용되었습니다.","공성존의 프라임 효과가 적용되었습니다.")),
   ("님의 매스 이뮨 투 함이 시전되었습니다.",("님의 매스 이뮨 투 함이 시전되었습니다.","같은 혈맹이 아니므로 매스 이뮨 투 함 미적용"))
 ]
 # Locate both originally independent player fanouts and work backwards.
 locations=[]
 for indicator,phrases in specs:
  a,b,loop=find_loop_for(s,indicator)
  rx=re.compile(r'player\.sendPackets\((.*?)\);')
  hits=[m for m in rx.finditer(loop) if any(phrase in m.group(1) for phrase in phrases)]
  assert len(hits)==2,(indicator,len(hits))
  exprs=[m.group(1) for m in hits]
  for marker in phrases:assert any(marker in x for x in exprs),marker
  assert "SC_SkillSound.send(player, player.getId()," in loop
  locations.append((a,b,loop,hits,exprs))
 assert locations[0][0]!=locations[1][0]
 for a,b,loop,hits,exprs in sorted(locations,key=lambda x:x[0],reverse=True):
  new=loop
  for idx in reversed(range(2)):
   match=hits[idx]
   new=new[:match.start()]+BC+".CSharedBroadcast.sendRecipient("+("noticeA" if idx==0 else "noticeB")+",player);"+new[match.end():]
  opening="try("+ ";\n".join(
   "var "+("noticeA" if idx==0 else "noticeB")+"=new "+BC+".CAsyncSendConstBuffer(\n"+
   "SC_SystemMessage.sharedWire("+exprs[idx]+"),Integer.MIN_VALUE,Integer.MAX_VALUE)" for idx in range(2)
   )+") {\n"
  s=s[:a]+opening+new+"\n}"+s[b:]
 assert 'player.sendPackets("프라임의 효과가 적용되었습니다.");' not in s
 assert 'player.sendPackets("\\\\fY같은 혈맹이 아니므로 매스 이뮨 투 함 미적용");' not in s
 files[ACTIVE]=s.encode("utf8")
TEST=r'''import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase53CombatGiftParity {
 static int count;
 static Charset MS949=Charset.forName("MS949");
 static void eq(byte[] a,byte[] b){if(!Arrays.equals(a,b))throw new AssertionError("wire "+count);count++;}
 static byte[] target(int id,boolean active){
  return ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)18).put((byte)Opcodes.S_EVENT).put((byte)0xc2)
   .putInt(id).put((byte)0x4f).put((byte)0x33)
   .put((byte)0).put((byte)0).putInt(active?1:0).putShort((short)0).array();
 }
 static byte[] message(String text){
  byte[] b=text.getBytes(MS949);
  byte[] wire=new byte[b.length+5];
  wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
  wire[2]=(byte)Opcodes.S_MESSAGE;wire[3]=9;
  System.arraycopy(b,0,wire,4,b.length);
  return wire;
 }
 static void check(int id,String s){
  eq(target(id,true),SC_TrueTargetNew.sharedWire(id,true));
  eq(message(s),SC_SystemMessage.sharedWire(s));
 }
 public static void main(String[]args){
  for(String s:new String[]{"","프라임","혈맹","전투 지휘","매스 이뮨 투 함","포인트 상자","\\f3효과"})check(123,s);
  int x=0x53be291f;
  for(int i=0;i<8192;i++){x^=x<<13;x^=x>>>17;x^=x<<5;check(x,"안내-"+x);}
  System.out.println("PHASE53_COMBAT_GIFT_PARITY_OK "+count);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase53"));args=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase52 source pin mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch_target(files);patch_reward(files);patch_active(files)
 changed=sorted(n for n in files if files[n]!=before[n]);assert changed==TARGETS,(changed,TARGETS)
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 test=args.work/"testsrc/Phase53CombatGiftParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":53,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
  "java_sources":1710,"modified_java":changed,
  "true_target_direct_sites_migrated":2,"clan_reward_system_message_sites_migrated":1,
  "prime_mass_immunity_message_sites_migrated":4,
  "total_per_recipient_direct_send_sites_migrated":7,
  "true_target_caster_before_visible_clan_order_kept":True,
  "true_target_manager_apply_after_fanout_kept":True,
  "reward_commit_then_green_then_system_fifo_kept":True,
  "combat_buff_mutation_and_skill_sound_order_kept":True,
  "native_unchanged":True,"no_fallback":True,"new_workers_queues_classes":0,
  "windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
