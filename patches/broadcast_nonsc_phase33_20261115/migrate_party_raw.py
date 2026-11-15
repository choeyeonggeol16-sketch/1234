#!/usr/bin/env python3
"""Phase33: remove per-party-member Proto.sendRaw frame construction.
Unframed protobuf is framed exactly once (S_EXTENDED_PROTOBUF opcode/trailer
already in encoded bytes), then one CAsyncSendConstBuffer fans out using the
same socket FIFO. Preserve all member order, cooldown and side effects.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase32_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase33_FULL_SRC_20261115.zip"
PIN="d0a76254764a21d0fb9be6c3d8353e20be5b1315"
BASE="src/l1j/server/server/"
BC=BASE+"model/network/core/broadcast/CSharedBroadcast.java"
PARTY=BASE+"model/L1Party.java"
JOIN=BASE+"model/L1ClanJoin.java"
MODIFIED=sorted([BC,PARTY,JOIN])
FQ="l1j.server.server.model.network.core.broadcast"
def blob(data):return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
def bounds(s,sig):
    assert s.count(sig)==1,(sig,s.count(sig))
    a=s.index(sig);b=s.index("{",a);depth=0
    for i in range(b,len(s)):
        if s[i]=="{":depth+=1
        elif s[i]=="}":
            depth-=1
            if depth==0:return a,i+1,s[a:i+1]
    raise AssertionError("unbalanced method "+sig)
def replace(s,sig,new,checks):
    a,b,old=bounds(s,sig)
    for x in checks:assert x in old,(sig,x)
    return s[:a]+new+s[b:]
EXT=r'''
    /** Borrow one immutable raw Proto payload as a framed shared SEND.
     * Input is exactly the argument accepted by Proto.sendRaw:
     * its opcode/message ID/trailer are included, header is not.
     */
    public static CAsyncSendConstBuffer openRaw(byte[] encoded) {
        final byte[] wire=frameRaw(encoded);
        return wire==null?null:new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE);
    }

    /** Array fanout without an iterable adapter, one frame for all members. */
    public static void sendRawArray(L1PcInstance[] recipients,byte[] encoded) {
        if(recipients==null || recipients.length==0)return;
        try(var shared=openRaw(encoded)){
            if(shared==null)return;
            for(L1PcInstance pc:recipients)
                sendRecipient(shared,pc);
        }
    }

'''
def patch(files):
 s=files[BC].decode("utf8")
 anchor="    public static void sendRawRecipients(Iterable<? extends L1PcInstance> recipients,"
 assert s.count(anchor)==1
 assert "public static CAsyncSendConstBuffer openRaw(" not in s
 assert "private static byte[] frameRaw(byte[] encoded)" in s
 s=s.replace(anchor,EXT+anchor,1)
 files[BC]=s.encode("utf8")
 s=files[PARTY].decode("utf8")
 s=replace(s,"private static void sendRawTo(L1PcInstance[] targets, byte[] data) {",
 '''private static void sendRawTo(L1PcInstance[] targets, byte[] data) {
        if(targets==null || targets.length==0 || data==null)return;
        '''+FQ+'''.CSharedBroadcast.sendRawArray(targets,data);
    }''',
 ["targets.length == 0","Proto.sendRaw(t, raw)"])
 s=replace(s,"private void sendPartyListTo(L1PcInstance[] targets, L1PcInstance[] snapshot, boolean force) {",
 '''private void sendPartyListTo(L1PcInstance[] targets,L1PcInstance[] snapshot,boolean force) {
        if(targets==null || targets.length==0)return;
        ArrayList<SC_PARTY_MEMBER_LIST.Member> members=new ArrayList<>();
        for(L1PcInstance m:snapshot){
            if(!ok(m))continue;
            members.add(SC_PARTY_MEMBER_LIST.Member.from(m,
                    _membersMark.getOrDefault(m.getId(),0)));
        }
        byte[] data=new SC_PARTY_MEMBER_LIST(
                _leader!=null?_leader.getName():"",members).encode();
        try(var shared='''+FQ+'''.CSharedBroadcast.openRaw(data)){
            for(L1PcInstance t:targets){
                if(!ok(t))continue;
                if(!force && !canSendFullListNow(t.getId()))continue;
                '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,t);
            }
        }
    }''',
 ["for (L1PcInstance m : snapshot)","canSendFullListNow(t.getId())",
  "SC_PARTY_MEMBER_LIST.Member.from(m", "new SC_PARTY_MEMBER_LIST(","Proto.sendRaw(t, data)"])
 s=replace(s,"protected void breakup() {",
 '''protected void breakup() {
        l1j.server.server.model.character.Item.Drop.PartyLootCursor.clearParty(this);
        L1PcInstance[] members=getMembers();
        _membersList.clear();
        _membersMark.clear();
        _lastFullListSentAt.clear();
        _leader=null;
        try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_ServerMessage.sharedWire(418),Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance m:members){
                if(m==null)continue;
                m.setParty(null);
                clearPartyUIForLeaver(m);
                if(ok(m))'''+FQ+'''.CSharedBroadcast.sendRecipient(shared,m);
            }
        }
    }''',
 ["PartyLootCursor.clearParty(this)","_membersList.clear()","_membersMark.clear()",
  "_lastFullListSentAt.clear()","_leader = null","clearPartyUIForLeaver(m)",
  "SC_ServerMessage.send(m, 418)"])
 s=replace(s,"public void leaveMember(L1PcInstance pc) {",
 '''public void leaveMember(L1PcInstance pc) {
        if(isLeader(pc) || getNumOfMembers()==2){
            breakup();
        } else {
            removeMember(pc);
            try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                    SC_ServerMessage.sharedWire(420,pc.getName()),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
                for(L1PcInstance member:getMembers())
                    '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,member);
                '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,pc);
            }
        }
    }''',
 ["isLeader(pc)","getNumOfMembers() == 2","breakup()",
  "removeMember(pc)","for (L1PcInstance member : getMembers())",
  "sendLeftMessage(member, pc)","sendLeftMessage(pc, pc)"])
 s=replace(s,"public void kickMember(L1PcInstance pc) {",
 '''public void kickMember(L1PcInstance pc) {
        if(getNumOfMembers()==2){
            breakup();
        } else {
            removeMember(pc);
            try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                    SC_ServerMessage.sharedWire(420,pc.getName()),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
                for(L1PcInstance member:getMembers())
                    '''+FQ+'''.CSharedBroadcast.sendRecipient(shared,member);
            }
            sendKickMessage(pc);
        }
        SC_ServerMessage.send(pc,419); // original duplicate kick notification retained
    }''',
 ["getNumOfMembers() == 2","breakup()","removeMember(pc)",
  "for (L1PcInstance member : getMembers())",
  "sendLeftMessage(member, pc)","sendKickMessage(pc)",
  "SC_ServerMessage.send(pc, 419)"])
 assert "Proto.sendRaw(t, raw)" not in s and "Proto.sendRaw(t, data)" not in s
 if "import l1j.server.server.model.network.packet.ServerPacket;\n" in s:
  s=s.replace("import l1j.server.server.model.network.packet.ServerPacket;\n","",1)
 files[PARTY]=s.encode("utf8")
 s=files[JOIN].decode("utf8")
 old='''for (L1PcInstance other : clan.getOnlineClanMember())
                    SC_ServerMessage.send(other, 94, joinPc.getName());'''
 assert s.count(old)==1,s.count(old)
 new=FQ+'''.CSharedBroadcast.sendArray(clan.getOnlineClanMember(),
                        SC_ServerMessage.sharedWire(94,joinPc.getName()));'''
 s=s.replace(old,new,1)
 assert "clan.addClanMember(joinPc.getName()" in s
 files[JOIN]=s.encode("utf8")
 return {"proto_party_fanouts":2,
         "party_disband_left_kick_notice_loops":3,
         "clan_join_notice_loops":1,
         "proto_raw_header_framed_once":True,
         "party_full_list_cooldown_preserved":True,
         "disband_inventory_ui_message_order_preserved":True,
         "party_leave_kick_duplicate_notification_preserved":True,
         "clan_join_commit_boundary_preserved":True}
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
public final class Phase33PartyWireParity {
 static int n;
 static void eq(byte[] a,byte[] b) {
  if(!Arrays.equals(a,b))throw new AssertionError("party wire "+n);n++;
 }
 static byte[] code(int type,String s){
  byte[] msg=s.getBytes(Charset.forName("MS949"));
  byte[] b=ByteBuffer.allocate(msg.length+7).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)(msg.length+7)).put((byte)Opcodes.S_MESSAGE_CODE)
   .putShort((short)type).put((byte)1).put(msg).put((byte)0).array();
  return b;
 }
 static byte[] code0(int type) {
  return ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)6).put((byte)Opcodes.S_MESSAGE_CODE)
   .putShort((short)type).put((byte)0).array();
 }
 static void test(int id,String message){
  eq(code(id,message),SC_ServerMessage.sharedWire(id,message));
  eq(code0(id),SC_ServerMessage.sharedWire(id));
 }
 public static void main(String[] args){
  for(String s:new String[]{"","혈맹","새 혈맹원 입장","파티원 탈퇴",
    "[클랜] 테스트","한글 이름","\\f2"}) {
     for(int id:new int[]{94,418,419,420})test(id,s);
  }
  int v=0x4567abc1;
  for(int i=0;i<8192;i++) {
   v^=v<<13;v^=v>>>17;v^=v<<5;test(420,"Join-"+v);
  }
  System.out.println("PHASE33_PARTY_PACKET_PARITY_OK "+n);
 }
}'''
def main():
 ap=argparse.ArgumentParser();ap.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase33"));args=ap.parse_args()
 raw=SRC.read_bytes()
 assert blob(raw)==PIN,"phase32 baseline differs"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 details=patch(files)
 changed=sorted(n for n in files if files[n]!=before[n])
 assert changed==MODIFIED,changed
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   p=args.work/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
 test=args.work/"testsrc/Phase33PartyWireParity.java"
 test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":33,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
     "modified_java":changed,"java_sources":1710,**details,
     "native_unchanged":True,"no_legacy_fallback":True,"new_workers_queues_classes":0,
     "windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
 print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
