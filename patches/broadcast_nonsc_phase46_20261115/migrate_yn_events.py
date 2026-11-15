#!/usr/bin/env python3
"""Phase46 - consolidate eight identical ASK/castle/target packets in five
live event systems. Never reorder recipient, event side effects or FIFO.
Preserve original direct sender for unrelated one-to-one usage.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase45_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase46_FULL_SRC_20261115.zip"
PIN="f71b76828ebeb1f5dd2ade290c283aaba9783f59"
BASE="src/l1j/server/"
ASK=BASE+"server/model/network/core/sc/packet/SC_MessageYN.java"
CASTLE_PACKET=BASE+"server/model/network/core/sc/packet/SC_CastleMaster.java"
TRUE_PACKET=BASE+"server/model/network/core/sc/packet/SC_TrueTargetNew.java"
BOSS=BASE+"server/Controller/BossController.java"
HAUNT=BASE+"server/model/L1HauntedHouse.java"
RACING=BASE+"server/model/L1Racing.java"
DEATH=BASE+"server/model/L1DeathMatch.java"
USER=BASE+"server/UserCommands.java"
CASTLE=BASE+"MJWarSystem/MJCastleWar.java"
COMBAT=BASE+"server/model/item/function/ItemUseCommandCombat.java"
TARGETS=sorted([ASK,CASTLE_PACKET,TRUE_PACKET,BOSS,HAUNT,RACING,DEATH,USER,CASTLE,COMBAT])
BC="l1j.server.server.model.network.core.broadcast"
def git_blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def block(src,sig,start=0):
    i=src.find(sig,start)
    if i<0:raise AssertionError(("missing",sig))
    j=src.find("{",i)
    if j<0:raise AssertionError(("no opening brace",sig))
    depth=0
    for k in range(j,len(src)):
        if src[k]=="{":depth+=1
        elif src[k]=="}":
            depth-=1
            if depth==0:return i,k+1,src[i:k+1]
    raise AssertionError(("unbalanced",sig))
def shared(wire,var="shared"):
    return "try(var "+var+"=new "+BC+".CAsyncSendConstBuffer(\n    "+wire+",Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n"
def wrap_loop(src,loop_sig,send_text,wire,var="shared",start=0,guards=()):
    a,b,original=block(src,loop_sig,start)
    assert original.count(send_text)==1,(loop_sig,send_text,original.count(send_text))
    for q in guards:assert q in original,(q,loop_sig)
    replacement=original.replace(send_text,BC+".CSharedBroadcast.sendRecipient("+var+","+
                              send_text[send_text.find("(")+1:send_text.find(",")].strip()+");",1)
    return src[:a]+shared(wire,var)+replacement+"\n}"+src[b:]
def add_ask(files):
    s=files[ASK].decode("utf8")
    sig="    private static byte[] sharedWire(int type, String message) {"
    assert s.count(sig)==1
    assert "public static byte[] sharedIndexedWire(" not in s
    assert "b.putShort((short)size).put((byte)Opcodes.S_ASK)" in s
    s=s.replace(sig,"    public static byte[] sharedWire(int type, String message) {",1)
    at=s.index("    public static byte[] sharedWire(int type, String message) {")
    indexed=r'''
    /** Exact direct-writer S_ASK frame, including original idx, type and
     * MS949 null-terminated message. The idx is not a per-user token.
     */
    public static byte[] sharedIndexedWire(int idx,int type,String message) {
        final byte[] text=(message==null?"":message)
                .getBytes(java.nio.charset.Charset.forName("MS949"));
        final int size=12+text.length;
        if(size>0xffff)return null;
        final java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(size)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size).put((byte)Opcodes.S_ASK).putShort((short)0)
         .putInt(idx).putShort((short)type).put(text).put((byte)0);
        return b.array();
    }

'''
    files[ASK]=(s[:at]+indexed+s[at:]).encode("utf8")
def add_castle(files):
    s=files[CASTLE_PACKET].decode("utf8")
    sig="    public static boolean send(CIOClient client, int type, int objectId) {"
    assert s.count(sig)==1 and "sharedWire(int type,int objectId)" not in s
    for req in ("buffer.writeC(Opcodes.S_CASTLE_OWNER);","buffer.writeC(type);",
                "buffer.writeD(objectId);"):
        assert req in s
    helper=r'''
    /** S_CASTLE_OWNER frame identical to the eight-byte direct writer. */
    public static byte[] sharedWire(int type,int objectId) {
        return java.nio.ByteBuffer.allocate(8)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short)8).put((byte)Opcodes.S_CASTLE_OWNER)
                .put((byte)type).putInt(objectId).array();
    }

'''
    files[CASTLE_PACKET]=s.replace(sig,helper+sig,1).encode("utf8")
def add_true(files):
    s=files[TRUE_PACKET].decode("utf8")
    sig="private static byte[] sharedWire(int targetId, boolean active) {"
    assert s.count(sig)==1
    assert "S_EVENT" in s and "0xc2" in s
    files[TRUE_PACKET]=s.replace(sig,"public static byte[] sharedWire(int targetId, boolean active) {",1).encode("utf8")
def migrate_boss(files):
    s=files[BOSS].decode("utf8")
    old="SC_MessageYN.send(pc, CS_Attr.MSGCODE_622_BOSS, 622, b.getYnMessage());"
    assert s.count(old)==1
    begin="try(var shared=new "+BC+".CAsyncSendConstBuffer("
    i=s.find(begin);assert i>=0
    end=s.find("Integer.MIN_VALUE,Integer.MAX_VALUE)) {",i)
    assert end>=0
    assert "SC_DisplayEffect.sharedWire(display_effect)" in s[i:end]
    s=s[:end]+"""Integer.MIN_VALUE,Integer.MAX_VALUE);
             var ynShared=new """+BC+""".CAsyncSendConstBuffer(
                 SC_MessageYN.sharedIndexedWire(
                      CS_Attr.MSGCODE_622_BOSS,622,b.getYnMessage()),
                 """+s[end:]
    # The final resource uses original )) { after replacing its value prefix.
    assert "ynShared=new" in s
    s=s.replace(old,BC+".CSharedBroadcast.sendRecipient(ynShared,pc);",1)
    assert s.index("pc.setBossYN(npc.getNpcId());")<s.index("sendRecipient(ynShared,pc);")
    files[BOSS]=s.encode("utf8")
def migrate_games(files):
    for path,loop_sig,original,wire,guard in [
        (HAUNT,"for (L1PcInstance player : getMembersArray()) {",
         'SC_MessageYN.send(player, 1256, "");','SC_MessageYN.sharedWire(1256,"")',
         "player.getMap().getId() != 5140"),
        (RACING,"for(L1PcInstance player : toArray(일반)){",
         'SC_MessageYN.send(player, 1256, "");','SC_MessageYN.sharedWire(1256,"")',
         "player.getMap().getId() != getMapId()"),
        (DEATH,"for (L1PcInstance player : getMembersArray()) {",
         'SC_MessageYN.send(player, 1268, "");','SC_MessageYN.sharedWire(1268,"")',
         "player.getMap().getId() != 5153")
    ]:
        s=files[path].decode("utf8")
        assert s.count(original)==1,(path,s.count(original))
        # Find precisely the loop enclosing the matching ASK call;
        # DeathMatch has several member loops.
        positions=[];search=0
        while True:
            i=s.find(loop_sig,search)
            if i<0:break
            a,b,part=block(s,loop_sig,i)
            if original in part:positions.append(i)
            search=i+len(loop_sig)
        assert len(positions)==1,(path,positions)
        s=wrap_loop(s,loop_sig,original,wire,start=positions[0],guards=(guard,))
        files[path]=s.encode("utf8")
def migrate_clan_invites(files):
    s=files[USER].decode("utf8")
    call="SC_MessageYN.send(member, 954, pc.getName());"
    sig="for (L1PcInstance member : clan.getOnlineClanMember()) {"
    assert s.count(call)==2,(USER,s.count(call))
    starts=[];search=0
    while True:
        i=s.find(sig,search)
        if i<0:break
        a,b,part=block(s,sig,i)
        if call in part:starts.append(i)
        search=i+len(sig)
    assert len(starts)==2,starts
    for i in reversed(starts):
        a,b,original=block(s,sig,i)
        assert original.count(call)==1
        for q in ("member.setPartyID(pc.getId());","member.isPrivateShop()",
                  "member.isInParty()","member.sendPackets("):
            assert q in original,(q,original[:150])
        changed=original.replace(call,BC+".CSharedBroadcast.sendRecipient(ynShared,member);",1)
        s=s[:a]+shared("SC_MessageYN.sharedWire(954,pc.getName())","ynShared")+changed+"\n}"+s[b:]
    files[USER]=s.encode("utf8")
def migrate_castle(files):
    s=files[CASTLE].decode("utf8")
    old="SC_CastleMaster.send(pc, _castleId, nextDefense.getLeaderId());"
    assert s.count(old)==1
    sig="for(L1PcInstance pc : L1World.getInstance().getAllPlayers()){"
    a,b,part=block(s,sig,s.index("final L1Location loc = createGetBackLocation();"))
    assert old in part and "MJCastleWarBusiness.move(pc);" in part
    changed=part.replace(old,BC+".CSharedBroadcast.sendRecipient(ownerShared,pc);",1)
    s=s[:a]+shared("SC_CastleMaster.sharedWire(_castleId,nextDefense.getLeaderId())","ownerShared")+changed+"\n}"+s[b:]
    files[CASTLE]=s.encode("utf8")
def migrate_combat(files):
    s=files[COMBAT].decode("utf8")
    old="SC_TrueTargetNew.send(partymember, target.getId(), true);"
    assert s.count(old)==1
    sig="for (L1PcInstance partymember : pc.getParty().getMembers()) {"
    a,b,part=block(s,sig)
    assert old in part
    for req in ("partymember.getId() == member.getId()","sendRecipient(shared,partymember)"):
        assert req in part
    changed=part.replace(old,BC+".CSharedBroadcast.sendRecipient(targetShared,partymember);",1)
    s=s[:a]+shared("SC_TrueTargetNew.sharedWire(target.getId(),true)","targetShared")+changed+"\n}"+s[b:]
    files[COMBAT]=s.encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase46YnEventsParity {
 static int checks;
 static final Charset MS949=Charset.forName("MS949");
 static void eq(byte[] a,byte[] b){if(!Arrays.equals(a,b))throw new AssertionError("wire "+checks);checks++;}
 static byte[] ask(int idx,int kind,String msg){
  byte[] t=msg.getBytes(MS949);
  return ByteBuffer.allocate(t.length+12).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)(t.length+12)).put((byte)Opcodes.S_ASK)
   .putShort((short)0).putInt(idx).putShort((short)kind).put(t).put((byte)0).array();
 }
 static byte[] castle(int kind,int objectId){
  return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)8).put((byte)Opcodes.S_CASTLE_OWNER)
   .put((byte)kind).putInt(objectId).array();
 }
 static byte[] target(int id,boolean active){
  return ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN)
    .putShort((short)18).put((byte)Opcodes.S_EVENT).put((byte)0xc2)
    .putInt(id).put((byte)0x4f).put((byte)0x33).put((byte)0).put((byte)0)
    .putInt(active?1:0).putShort((short)0).array();
 }
 static void check(int id,String message){
  eq(ask(0,1256,message),SC_MessageYN.sharedWire(1256,message));
  eq(ask(id,622,message),SC_MessageYN.sharedIndexedWire(id,622,message));
  eq(castle(id,id*7),SC_CastleMaster.sharedWire(id,id*7));
  eq(target(id,true),SC_TrueTargetNew.sharedWire(id,true));
  eq(target(id,false),SC_TrueTargetNew.sharedWire(id,false));
 }
 public static void main(String[] args){
  for(String s:new String[]{"","켄트","성주","입장하시겠습니까?", "혈맹 파티 초대", "보스 알림","\\f2world"})check(954,s);
  int r=0x5dd471b;
  for(int i=0;i<8192;i++){r^=r<<13;r^=r>>>17;r^=r<<5;check(r,"안내 "+r);}
  System.out.println("PHASE46_YN_EVENTS_PARITY_OK "+checks);
 }
}'''
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase46"))
    args=p.parse_args()
    raw=SRC.read_bytes();assert git_blob(raw)==PIN,"phase45 pinned ZIP changed"
    with zipfile.ZipFile(SRC) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    before=files.copy()
    assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
    add_ask(files);add_castle(files);add_true(files)
    migrate_boss(files);migrate_games(files);migrate_clan_invites(files)
    migrate_castle(files);migrate_combat(files)
    changed=sorted(n for n in files if files[n]!=before[n])
    assert changed==TARGETS,(changed,TARGETS)
    assert files["CTransport.dll"]==before["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for n,data in files.items():
        if n.startswith("src/") and n.endswith(".java"):
            f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    t=args.work/"testsrc/Phase46YnEventsParity.java"
    t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n,data in sorted(files.items()):z.writestr(n,data)
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    report={"phase":46,"input_blob":PIN,
        "output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "java_sources":1710,"modified_java":changed,
        "per_receiver_direct_sends_removed":8,"ask_fanout_sites":6,
        "castle_owner_fanout_sites":1,"true_target_fanout_sites":1,
        "boss_effect_state_ask_fifo_preserved":True,
        "minigame_map_and_party_filters_preserved":True,
        "clan_invite_state_and_packet_order_preserved":True,
        "castle_move_then_owner_wire_preserved":True,
        "native_unchanged":True,"no_legacy_fallback":True,
        "new_workers_queues_classes":0,"windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
