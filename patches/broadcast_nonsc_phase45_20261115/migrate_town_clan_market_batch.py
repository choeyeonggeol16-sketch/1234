#!/usr/bin/env python3
"""Phase45: nine-file batch eliminates twenty-two repeated identical SEND
calls. Honors event receipt commit ordering, per-recipient three-line market
notice FIFO, member-specific effects, party/self exclusion and world traversal.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase44_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase45_FULL_SRC_20261115.zip"
PIN="e50b8f14c49906edd80ad0bc9a03db68e4e1218d"
BASE="src/l1j/server/"
PACKET=BASE+"server/model/network/core/sc/packet/SC_PacketBox.java"
TOWN=BASE+"server/Controller/HomeTownTimeController.java"
PC=BASE+"server/model/Instance/L1PcInstance.java"
GM=BASE+"server/GMCommands.java"
RANK=BASE+"server/model/network/core/cs/packet/CS_RankControl.java"
REWARD=BASE+"server/model/item/function/ItemUseConsumableRewards.java"
COMBAT=BASE+"server/model/item/function/ItemUseCommandCombat.java"
CONSIGN=BASE+"server/model/item/function/ItemUseConsignment.java"
CLAN=BASE+"server/model/item/function/CItemUseClan.java"
TARGETS=sorted([PACKET,TOWN,PC,GM,RANK,REWARD,COMBAT,CONSIGN,CLAN])
BC="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def block(src,start):
    op=src.find("{",start)
    assert op>=0,start
    depth=0
    for i in range(op,len(src)):
        if src[i]=="{":depth+=1
        elif src[i]=="}":
            depth-=1
            if depth==0:return start,i+1,src[start:i+1]
    raise AssertionError("unbalanced")
def find_loop(src,signature,start=0):
    p=src.find(signature,start);assert p>=0,signature
    return block(src,p)
def wrap_one_loop(src,signature,replacements,wire_exprs,names,checks):
    a,b,old=find_loop(src,signature)
    assert len(wire_exprs)==len(names)
    for token in checks:assert token in old,(signature,token)
    new=old
    for old_send,new_send,expected in replacements:
        assert new.count(old_send)==expected,(signature,old_send,new.count(old_send),expected)
        new=new.replace(old_send,new_send)
    buffers=[]
    for name,wire in zip(names,wire_exprs):
        buffers.append("var "+name+"=new "+BC+".CAsyncSendConstBuffer(\n"+wire+",Integer.MIN_VALUE,Integer.MAX_VALUE)")
    wrapped="try("+ ";\n".join(buffers) +") {\n"+new+"\n}"
    return src[:a]+wrapped+src[b:]
HELPER=r'''
    /** S_EVENT constructor 13 / MSG_TOWN_LEADER, same MS949 nul string
     * as the direct SC_PacketBox sender, shared once per town.
     */
    public static byte[] sharedTownLeaderWire(String leader){
        final byte[] t=(leader==null?"":leader).getBytes(
                java.nio.charset.Charset.forName("MS949"));
        final int size=5+t.length;
        if(size>0xffff)return null;
        final byte[] wire=new byte[size];
        wire[0]=(byte)size;wire[1]=(byte)(size>>>8);
        wire[2]=(byte)Opcodes.S_EVENT;wire[3]=(byte)MSG_TOWN_LEADER;
        System.arraycopy(t,0,wire,4,t.length);
        return wire;
    }

'''
def patch(files):
 s=files[PACKET].decode("utf8")
 anchor="    public static byte[] sharedGreenWire(String message) {"
 assert s.count(anchor)==1 and "public static byte[] sharedTownLeaderWire(" not in s
 assert "case MSG_TOWN_LEADER:" in s and "buffer.writeS(_strVal1);" in s
 s=s.replace(anchor,HELPER+anchor,1);files[PACKET]=s.encode("utf8")
 s=files[TOWN].decode("utf8")
 old="SC_PacketBox.send(pc, SC_PacketBox.MSG_TOWN_LEADER, leader);"
 new=BC+".CSharedBroadcast.sendRecipient(shared,pc);"
 s=wrap_one_loop(s,"for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {",
  [(old,new,1)],["SC_PacketBox.sharedTownLeaderWire(leader)"],["shared"],
  ("pc.setContribution(0);","pc.getHomeTownId() == townId"))
 assert s.count(old)==0;files[TOWN]=s.encode("utf8")
 s=files[PC].decode("utf8")
 loopSig="for (L1PcInstance partymember : this.getParty().getMembers()) {"
 assert s.count(loopSig)==2,(PC,s.count(loopSig))
 old="SC_SkillSound.send(partymember, getId(), 14446);"
 assert s.count(old)==2,(PC,s.count(old))
 # Replace both independent loops from bottom to top; each opens one shared sound source.
 starts=[m.start() for m in re.finditer(re.escape(loopSig),s)]
 for start in reversed(starts):
  a,b,loop=block(s,start)
  assert loop.count(old)==1
  assert "partymember" in loop and "14446" in loop
  transformed=loop.replace(old,BC+".CSharedBroadcast.sendRecipient(shared,partymember);",1)
  wrapped=("try(var shared=new "+BC+".CAsyncSendConstBuffer(\n"+
      "SC_SkillSound.sharedWire(getId(),14446),Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n"+transformed+"\n}")
  s=s[:a]+wrapped+s[b:]
 assert s.count(old)==0;files[PC]=s.encode("utf8")
 s=files[GM].decode("utf8")
 old="SC_ServerMessage.send(clanMembers, 94, pc.getName());"
 s=wrap_one_loop(s,"for (L1PcInstance clanMembers : clan.getOnlineClanMember()) {",
  [(old,BC+".CSharedBroadcast.sendRecipient(shared,clanMembers);",1)],
  ["SC_ServerMessage.sharedWire(94,pc.getName())"],["shared"],("clanMembers",))
 files[GM]=s.encode("utf8")
 s=files[RANK].decode("utf8")
 loopSig="for (L1PcInstance member : clan.getOnlineClanMember()) {"
 a,b,loop=find_loop(s,loopSig)
 rx=re.compile(r'SC_SystemMessage\.send\(member,\s*(target\.getName\(\)[\s\S]*?)\);')
 matches=list(rx.finditer(loop));assert len(matches)==1,[x.group(1) for x in matches]
 expr=matches[0].group(1)
 assert "rankString" in expr
 original=matches[0].group(0)
 new=BC+".CSharedBroadcast.sendRecipient(shared,member);"
 s=wrap_one_loop(s,loopSig,[(original,new,1)],
  ["SC_SystemMessage.sharedWire("+expr+")"],["shared"],("if (member != null)",))
 files[RANK]=s.encode("utf8")
 s=files[REWARD].decode("utf8")
 loopSig="for (L1PcInstance member : pointsClan.getOnlineClanMember()) {"
 a,b,loop=find_loop(s,loopSig)
 rx=re.compile(r'SC_PacketBox\.send\(member,\s*SC_PacketBox\.GREEN_MESSAGE,\s*([\s\S]*?)\);')
 found=list(rx.finditer(loop))
 assert len(found)==1,len(found)
 expr=found[0].group(1)
 assert "pc.getName()" in expr and "points" in expr
 old=found[0].group(0)
 s=wrap_one_loop(s,loopSig,
   [(old,BC+".CSharedBroadcast.sendRecipient(shared,member);",1)],
   ["SC_PacketBox.sharedGreenWire("+expr+")"],["shared"],("member.sendPackets(",))
 files[REWARD]=s.encode("utf8")
 s=files[COMBAT].decode("utf8")
 loopSig="for (L1PcInstance partymember : pc.getParty().getMembers()) {"
 a,b,loop=find_loop(s,loopSig)
 rx=re.compile(r'SC_SystemMessage\.send\(partymember,\s*([\s\S]*?)\);')
 found=list(rx.finditer(loop));assert len(found)==2,len(found)
 assert found[0].group(1)==found[1].group(1),"different combat command messages"
 expr=found[0].group(1)
 assert "pc.getName()" in expr and "member.getName()" in expr
 old=found[0].group(0)
 s=wrap_one_loop(s,loopSig,[(old,BC+".CSharedBroadcast.sendRecipient(shared,partymember);",2)],
  ["SC_SystemMessage.sharedWire("+expr+")"],["shared"],
  ("SC_TrueTargetNew.send(partymember, target.getId(), true);","partymember.getId() == member.getId()"))
 files[COMBAT]=s.encode("utf8")
 s=files[CONSIGN].decode("utf8")
 loopSig="for (L1PcInstance pc1 : L1World.getInstance().getAllPlayers()) {"
 a,b,loop=find_loop(s,loopSig)
 rx=re.compile(r'SC_SystemMessage\.send\(pc1,\s*(.*?)\);')
 found=list(rx.finditer(loop))
 assert len(found)==12,(len(found),[m.group(1) for m in found])
 classified={}
 grouped={"header":[],"price":[],"name":[],"log":[]}
 for m in found:
  expr=m.group(1)
  if "해당물품이 등록되었습니다" in expr:cat="header"
  elif "판매금액" in expr:cat="price"
  elif "getLogName()" in expr:cat="log"
  elif "getName()" in expr:cat="name"
  else:raise AssertionError("unknown consignment notice: "+expr)
  grouped[cat].append(m.group(0));classified.setdefault(cat,expr)
 assert {k:len(v) for k,v in grouped.items()}=={"header":4,"price":4,"name":2,"log":2}
 newloop=loop
 for cat in ("header","price","name","log"):
  for oldexpr in set(grouped[cat]):
   assert newloop.count(oldexpr)==len(grouped[cat]),(cat,newloop.count(oldexpr))
   newloop=newloop.replace(oldexpr,BC+".CSharedBroadcast.sendRecipient("+cat+"Shared,pc1);")
 buffers=[]
 for cat in ("header","price","name","log"):
  buffers.append("var "+cat+"Shared=new "+BC+".CAsyncSendConstBuffer(\n"+
    "SC_SystemMessage.sharedWire("+classified[cat]+"),Integer.MIN_VALUE,Integer.MAX_VALUE)")
 wrapped="try("+ ";\n".join(buffers) +") {\n"+newloop+"\n}"
 assert "if (pc1.물품멘트)" in newloop and "l1iteminstance1.getItem().getType2()" in newloop
 s=s[:a]+wrapped+s[b:]
 files[CONSIGN]=s.encode("utf8")
 s=files[CLAN].decode("utf8")
 oldGreen="SC_PacketBox.send(member, SC_PacketBox.GREEN_MESSAGE, green);"
 oldNotice="SC_SystemMessage.send(member, notice);"
 s=wrap_one_loop(s,"for (L1PcInstance member : clan.getOnlineClanMember()) {",
  [(oldGreen,BC+".CSharedBroadcast.sendRecipient(greenShared,member);",1),
   (oldNotice,BC+".CSharedBroadcast.sendRecipient(noticeShared,member);",1)],
  ["SC_PacketBox.sharedGreenWire(green)","SC_SystemMessage.sharedWire(notice)"],
  ["greenShared","noticeShared"],
  ("member.setSkillEffect(skill, seconds * 1000);",
   "SC_InventoryIcon.icoNew(member, skill, icon, seconds, true);",
   "SC_SkillSound.send(member, member.getId(), 13566);",
   "SC_SkillSound.broadcast(member, member.getId(), 13566);"))
 files[CLAN]=s.encode("utf8")
TEST=r'''import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase45TownClanParity {
 static int checks;static final Charset MS949=Charset.forName("MS949");
 static void eq(byte[] want,byte[] actual){if(!Arrays.equals(want,actual))throw new AssertionError("wire "+checks);checks++;}
 static byte[] town(String t){
  byte[] raw=t.getBytes(MS949);byte[] wire=new byte[5+raw.length];
  wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
  wire[2]=(byte)Opcodes.S_EVENT;wire[3]=(byte)SC_PacketBox.MSG_TOWN_LEADER;
  System.arraycopy(raw,0,wire,4,raw.length);
  return wire;
 }
 static byte[] system(String s){
  byte[] raw=s.getBytes(MS949);byte[] wire=new byte[5+raw.length];
  wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
  wire[2]=(byte)Opcodes.S_MESSAGE;wire[3]=9;
  System.arraycopy(raw,0,wire,4,raw.length);
  return wire;
 }
 static byte[] green(String s){
  byte[] raw=s.getBytes(MS949);byte[] wire=new byte[6+raw.length];
  wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
  wire[2]=(byte)Opcodes.S_EVENT;wire[3]=(byte)SC_PacketBox.GREEN_MESSAGE;wire[4]=2;
  System.arraycopy(raw,0,wire,5,raw.length);
  return wire;
 }
 static byte[] code(int id,String s){
  byte[] raw=s.getBytes(MS949);byte[] wire=new byte[7+raw.length];
  wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
  wire[2]=(byte)Opcodes.S_MESSAGE_CODE;wire[3]=(byte)id;wire[4]=(byte)(id>>>8);wire[5]=1;
  System.arraycopy(raw,0,wire,6,raw.length);
  return wire;
 }
 static byte[] skill(int id,int gfx){
  return ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN)
    .putShort((short)15).put((byte)Opcodes.S_EFFECT).putInt(id).putShort((short)gfx)
    .putShort((short)0).putInt(0).array();
 }
 static void test(String msg,int id) {
  eq(town(msg),SC_PacketBox.sharedTownLeaderWire(msg));
  eq(system(msg),SC_SystemMessage.sharedWire(msg));
  eq(green(msg),SC_PacketBox.sharedGreenWire(msg));
  eq(code(id,msg),SC_ServerMessage.sharedWire(id,msg));
  eq(skill(id,14446),SC_SkillSound.sharedWire(id,14446));
 }
 public static void main(String[] args){
  for(String s:new String[]{"","켄트","마을 대표","포인트 상자","혈맹 버프","위탁판매 등록","\\f2월말 정산"})
   test(s,94);
  int r=0x4fea123;
  for(int i=0;i<4096;i++){r^=r<<13;r^=r>>>17;r^=r<<5;test("이벤트-"+r,r);}
  System.out.println("PHASE45_TOWN_CLAN_PARITY_OK "+checks);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase45"));a=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase44 source pin mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch(files)
 changed=sorted(n for n in files if files[n]!=before[n]);assert changed==TARGETS,(changed,TARGETS)
 assert files["CTransport.dll"]==before["CTransport.dll"]
 a.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=a.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 t=a.work/"testsrc/Phase45TownClanParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":45,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
  "java_sources":1710,"modified_java":changed,"per_recipient_direct_sends_migrated":22,
  "town_monthly_db_commit_before_fanout_preserved":True,
  "clan_buff_per_pc_effect_and_broadcast_order_preserved":True,
  "consignment_three_packets_per_pc_fifo_preserved":True,
  "party_skill_emission_order_preserved":True,
  "native_unchanged":True,"no_fallback":True,
  "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
