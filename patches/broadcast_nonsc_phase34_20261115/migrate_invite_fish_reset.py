#!/usr/bin/env python3
"""Phase34: share clan-invite green notice, fishing two ordered event fanouts,
dungeon reset message, chat-party leave notice while preserving original
member/map filters, direct effects and teleports in their original order.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase33_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase34_FULL_SRC_20261115.zip"
PIN="5a4363457d17f00d976d0f5c4a43a0f430624d55"
BASE="src/l1j/server/"
USER=BASE+"server/UserCommands.java"
FISH=BASE+"server/Controller/FishingTimeController.java"
DUN=BASE+"MJDungeonTimer/Updator/DungeonTimeUpdator.java"
CHAT=BASE+"server/model/L1ChatParty.java"
CHANGED=sorted([USER,FISH,DUN,CHAT])
BC="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def block(src,start):
    assert src.count(start)==1,(start,src.count(start))
    a=src.index(start);b=src.index("{",a);level=0
    for i in range(b,len(src)):
        if src[i]=="{":level+=1
        elif src[i]=="}":
            level-=1
            if level==0:return a,i+1,src[a:i+1]
    raise AssertionError("unclosed "+start)
INVITE=re.compile(r"SC_PacketBox\.send\(member,\s*SC_PacketBox\.GREEN_MESSAGE,\s*(\"(?:[^\"\\]|\\.)*\")\);")
def patch(files):
    src=files[USER].decode("utf8")
    head="for (L1PcInstance member : clan.getOnlineClanMember()) {"
    offsets=[m.start() for m in re.finditer(re.escape(head),src)]
    # Only replace loops that actually send a fixed GREEN_MESSAGE to all eligible clan members.
    candidates=[]
    for pos in offsets:
        braces=src.index("{",pos);dep=0
        for j in range(braces,len(src)):
            if src[j]=="{":dep+=1
            elif src[j]=="}":
                dep-=1
                if dep==0:
                    body=src[pos:j+1]
                    if INVITE.search(body):candidates.append((pos,j+1,body))
                    break
    assert len(candidates)==2,[(a,len(b)) for a,_,b in candidates]
    for a,b,loop in reversed(candidates):
        matches=list(INVITE.finditer(loop))
        assert len(matches)==1,(len(matches),loop)
        m=matches[0]
        text=m.group(1)
        assert "member.setPartyID(pc.getId())" in loop
        assert "SC_MessageYN.send(member, 954, pc.getName())" in loop
        assert "member.sendPackets(" in loop
        assert "member.isPrivateShop()" in loop and "member.isInParty()" in loop
        assert "member.getAutoHunt()" in loop
        updated=loop.replace(m.group(0),BC+".CSharedBroadcast.sendRecipient(shared,member);",1)
        wrapper='''try(var shared=new '''+BC+'''.CAsyncSendConstBuffer(
                SC_PacketBox.sharedGreenWire('''+text+'''),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+updated+'''
        }'''
        src=src[:a]+wrapper+src[b:]
    files[USER]=src.encode("utf8")
    src=files[FISH].decode("utf8")
    sig="private void distribute_message(\n            int owner_object_id,\n            String message,\n            int effect_id) {"
    a,b,old=block(src,sig)
    for check in ("for (L1PcInstance viewer : L1World.getInstance().getVisiblePlayers(5490))",
                  "SC_PacketBox.send(viewer, SC_PacketBox.GREEN_MESSAGE, message)",
                  "SC_SkillSound.send(viewer, owner_object_id, effect_id)"):
        assert check in old,check
    assert old.count("getVisiblePlayers(5490)")==2
    new='''private void distribute_message(
            int owner_object_id,String message,int effect_id){
        // Preserve two independent AOI lookups, first all notices, then all FX.
        '''+BC+'''.CSharedBroadcast.sendRecipients(
                L1World.getInstance().getVisiblePlayers(5490),
                SC_PacketBox.sharedGreenWire(message));
        '''+BC+'''.CSharedBroadcast.sendRecipients(
                L1World.getInstance().getVisiblePlayers(5490),
                SC_SkillSound.sharedWire(owner_object_id,effect_id));
    }'''
    src=src[:a]+new+src[b:]
    files[FISH]=src.encode("utf8")
    src=files[DUN].decode("utf8")
    sig="private void teleportAfterReset() {"
    a,b,old=block(src,sig)
    for token in ("for (int i = 0; i < 2; ++i)","getAllPlayers()",
                  "if (i == 0) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message)",
                  "pc.start_teleport(33430, 32823, 4, pc.getHeading(), 169, true, false)",
                  "pc.do_simple_teleport(33436, 32812, 4)",
                  "SC_SkillSound.send(pc, pc.getId(), 491)"):
        assert token in old,(sig,token)
    new=old.replace(
      "if (i == 0) SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);",
      "if(i==0)"+BC+".CSharedBroadcast.sendRecipient(shared,pc);",1)
    target="for (int i = 0; i < 2; ++i) {"
    assert new.count(target)==1
    new=new.replace(target,'''try(var shared=new '''+BC+'''.CAsyncSendConstBuffer(
                SC_PacketBox.sharedGreenWire(message),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+target,1)
    # One additional close brace at method end, after both complete passes.
    closeidx=new.rfind("}")
    assert closeidx==len(new)-1
    new=new[:closeidx]+"    }\n"+new[closeidx:]
    src=src[:a]+new+src[b:]
    files[DUN]=src.encode("utf8")
    src=files[CHAT].decode("utf8")
    sig="public void leaveMember(L1PcInstance pc) {"
    a,b,old=block(src,sig)
    for token in ("isLeader(pc)","breakup()","getNumOfMembers() == 2",
                  "removeMember(pc)","removeMember(leader)",
                  "sendLeftMessage(pc, pc)","sendLeftMessage(leader, pc)",
                  "sendLeftMessage(member, pc)"):
        assert token in old,(sig,token)
    new='''public void leaveMember(L1PcInstance pc){
        if(isLeader(pc)){
            breakup();
        } else {
            try(var shared=new '''+BC+'''.CAsyncSendConstBuffer(
                    SC_ServerMessage.sharedWire(420,pc.getName()),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
                if(getNumOfMembers()==2){
                    removeMember(pc);
                    L1PcInstance leader=getLeader();
                    removeMember(leader);
                    '''+BC+'''.CSharedBroadcast.sendRecipient(shared,pc);
                    '''+BC+'''.CSharedBroadcast.sendRecipient(shared,leader);
                } else {
                    removeMember(pc);
                    for(L1PcInstance member:getMembers())
                        '''+BC+'''.CSharedBroadcast.sendRecipient(shared,member);
                    '''+BC+'''.CSharedBroadcast.sendRecipient(shared,pc);
                }
            }
        }
    }'''
    src=src[:a]+new+src[b:]
    # The private old one-recipient helper is now truly unused.
    helper="private void sendLeftMessage(L1PcInstance sendTo, L1PcInstance left) {"
    a,b,old=block(src,helper)
    assert "SC_ServerMessage.send(sendTo, 420, left.getName())" in old
    src=src[:a]+src[b:]
    assert "sendLeftMessage(" not in src
    files[CHAT]=src.encode("utf8")
    return {"clan_invite_green_loops":2,"fishing_notice_effect_loops":2,
      "dungeon_reset_notice_loop":1,"chat_leave_notice_loop":1,
      "fishing_two_aoi_snapshots_retained":True,
      "dungeon_two_pass_teleport_order_retained":True,
      "invite_ack_and_warning_order_retained":True,
      "chat_leave_self_and_leader_notice_order_retained":True,
      "unused_chat_private_sender_removed":1}
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase34NoticeEffectParity {
 static int count;
 static void eq(byte[] expected,byte[] actual) {
  if(!Arrays.equals(expected,actual))throw new AssertionError("packet parity "+count);count++;
 }
 static byte[] green(String text){
  byte[] msg=text.getBytes(Charset.forName("MS949"));
  byte[] wire=new byte[msg.length+6];
  wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
  wire[2]=(byte)Opcodes.S_EVENT;wire[3]=(byte)SC_PacketBox.GREEN_MESSAGE;
  wire[4]=2;System.arraycopy(msg,0,wire,5,msg.length);return wire;
 }
 static byte[] sound(int id,int gfx){
  return ByteBuffer.allocate(15).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)15).put((byte)Opcodes.S_EFFECT).putInt(id)
   .putShort((short)gfx).putShort((short)0).putInt(0).array();
 }
 static byte[] server(int code,String text) {
  byte[] msg=text.getBytes(Charset.forName("MS949"));
  return ByteBuffer.allocate(msg.length+7).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)(msg.length+7)).put((byte)Opcodes.S_MESSAGE_CODE)
   .putShort((short)code).put((byte)1).put(msg).put((byte)0).array();
 }
 static void test(String msg,int id,int gfx){
  eq(green(msg),SC_PacketBox.sharedGreenWire(msg));
  eq(sound(id,gfx),SC_SkillSound.sharedWire(id,gfx));
  eq(server(420,msg),SC_ServerMessage.sharedWire(420,msg));
 }
 public static void main(String[] args){
  for(String text:new String[]{"","\\f:혈맹 초대 안내",
       "You have received a clan party invitation.","던전 초기화","낚시 효과"})test(text,5490,491);
  int x=0x1743892a;
  for(int i=0;i<4096;i++) {
   x^=x<<13;x^=x>>>17;x^=x<<5;test("낚시 알림 "+x,x,x>>16);
  }
  System.out.println("PHASE34_NOTICE_EFFECT_PARITY_OK "+count);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase34"));args=p.parse_args()
 raw=SRC.read_bytes()
 assert blob(raw)==PIN,"phase33 pinned input changed"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 result=patch(files)
 changed=sorted(n for n in files if files[n]!=before[n])
 assert changed==CHANGED,changed
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 t=args.work/"testsrc/Phase34NoticeEffectParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":34,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
    "java_sources":1710,"modified_java":changed,**result,"native_unchanged":True,
    "no_legacy_fallback":True,"new_workers_queues_classes":0,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
 print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
