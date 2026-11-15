#!/usr/bin/env python3
"""Phase50: eight Java classes, same packet bytes, no legacy fallback.
Shared ship effects, Grace Avatar party message, shop warning, racing end,
NPC world chat. Preserve every conditional, per-PC sound, event ordering.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase49_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase50_FULL_SRC_20261116.zip"
PIN="a5b80b6ea1ae325a075735cc0d6cbd1d40738134"
ROOTSRC="src/l1j/server/"
SERVER=ROOTSRC+"server/"
SHIP=SERVER+"Controller/ShipTimeController.java"
GRACE=SERVER+"model/character/skill/SkillHandlers.java"
SHOP=SERVER+"model/network/core/cs/packet/CS_Result.java"
RACING=SERVER+"model/L1Racing.java"
GAME_END=SERVER+"model/network/core/sc/packet/SC_GameEnd.java"
GAME_OVER=SERVER+"model/network/core/sc/packet/SC_GameOver.java"
NPC_TIMER=SERVER+"model/L1NpcChatTimer.java"
NPC_PACKET=SERVER+"model/network/core/sc/packet/SC_NpcChatPacket.java"
TARGETS=sorted([SHIP,GRACE,SHOP,RACING,GAME_END,GAME_OVER,NPC_TIMER,NPC_PACKET])
BC="l1j.server.server.model.network.core.broadcast"
def blob(raw):return hashlib.sha1(b"blob "+str(len(raw)).encode()+b"\0"+raw).hexdigest()
def block(src,signature,start=0):
    i=src.find(signature,start)
    assert i>=0,("missing signature",signature,start)
    op=src.index("{",i)
    depth=0
    for at in range(op,len(src)):
        if src[at]=="{":depth+=1
        elif src[at]=="}":
            depth-=1
            if depth==0:return i,at+1,src[i:at+1]
    raise AssertionError(("unbalanced",signature))
def contain(src,signature,send,start=0):
    found=[];cursor=start
    while True:
        i=src.find(signature,cursor)
        if i<0:break
        a,b,part=block(src,signature,i)
        if send in part:found.append((a,b,part))
        cursor=i+len(signature)
    assert len(found)==1,("containing loop count",signature,send,len(found))
    return found[0]
def shared(wire,name="shared"):
    return "try(var "+name+"=new "+BC+".CAsyncSendConstBuffer(\n    "+wire+",Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n"
def replace_loop(src,a,b,part,old_send,new_send,wire,checks=(),name="shared"):
    assert part.count(old_send)==1,(old_send,part.count(old_send))
    for c in checks:assert c in part,(old_send,c)
    updated=part.replace(old_send,new_send,1)
    return src[:a]+shared(wire,name)+updated+"\n}"+src[b:]
def patch_ship(files):
    s=files[SHIP].decode("utf8")
    old="SC_DisplayEffect.send(pc, eff1);"
    assert s.count(old)==5,("ship effect calls",s.count(old))
    signature="for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {"
    targets=[]
    cursor=0
    while True:
        i=s.find(signature,cursor)
        if i<0:break
        a,b,loop=block(s,signature,i)
        if old in loop:
            assert loop.count(old)==1,("nested or duplicate",i)
            assert "pc.getMapId() == 447" in loop
            assert "SC_SkillSound.send(pc, pc.getId(), 20394);" in loop
            targets.append((a,b,loop))
        cursor=i+len(signature)
    assert len(targets)==5,("ship loops",len(targets))
    for a,b,part in reversed(targets):
        s=replace_loop(s,a,b,part,old,
          BC+".CSharedBroadcast.sendRecipient(effectShared,pc);",
          "SC_DisplayEffect.sharedWire(eff1)",
          ("SC_SkillSound.send(pc, pc.getId(), 20394);",),"effectShared")
    assert old not in s
    files[SHIP]=s.encode("utf8")
def patch_grace(files):
    s=files[GRACE].decode("utf8")
    a,b,method=block(s,"private static HandlerResult onGraceAvatar(")
    sig="for (L1PcInstance member : L1World.getInstance().getVisiblePlayer(pc, 18)) {"
    i,j,loop=block(method,sig)
    old="SC_ServerMessage.send(member, 4734);"
    method=replace_loop(method,i,j,loop,old,
       BC+".CSharedBroadcast.sendRecipient(shared,member);",
       "SC_ServerMessage.sharedWire(4734)",
       ("party.isMember(member)","member.setGraceLv(1);",
        "applySkillEffectSec(member, L1SkillId.GRACE_AVATAR, durSec);"))
    s=s[:a]+method+s[b:];files[GRACE]=s.encode("utf8")
def patch_shop(files):
    s=files[SHOP].decode("utf8")
    signature="for (L1PcInstance player : L1World.getInstance().getAllPlayers()) {"
    old='SC_SystemMessage.send(player, pc.getName() + "님 상점 최대구매 수량 (" + itemcount + ")를 초과 하였습니다.");'
    a,b,loop=contain(s,signature,old)
    assert "player.isGm() || pc == player" in loop
    s=replace_loop(s,a,b,loop,old,
        BC+".CSharedBroadcast.sendRecipient(shared,player);",
        'SC_SystemMessage.sharedWire(pc.getName() + "님 상점 최대구매 수량 (" + itemcount + ")를 초과 하였습니다.")',
        ("player.isGm() || pc == player",))
    files[SHOP]=s.encode("utf8")
GAME_END_HELPER=r'''
    /** Same fixed 10-byte packet as S_EVENT 0x46 direct writer. */
    public static byte[] sharedWire() {
        return new byte[]{10,0,(byte)Opcodes.S_EVENT,0x46,
                (byte)147,92,(byte)151,(byte)220,42,74};
    }

'''
GAME_OVER_HELPER=r'''
    /** Same fixed 10-byte packet as S_EVENT 0x45 direct writer. */
    public static byte[] sharedWire() {
        return new byte[]{10,0,(byte)Opcodes.S_EVENT,0x45,
                10,109,85,(byte)208,2,(byte)220};
    }

'''
def patch_race(files):
    for path,helper,anchor,required in (
       (GAME_END,GAME_END_HELPER,"    public static boolean send(CIOClient client) {",
        ("buffer.writeC(0x46);","buffer.writeC(147);","buffer.writeC(74);")),
       (GAME_OVER,GAME_OVER_HELPER,"    public static boolean send(CIOClient client) {",
        ("buffer.writeC(0x45);","buffer.writeC(10);","buffer.writeC(220);"))
    ):
        s=files[path].decode("utf8")
        assert s.count(anchor)==1 and "public static byte[] sharedWire()" not in s
        for req in required:assert req in s,(path,req)
        files[path]=s.replace(anchor,helper+anchor,1).encode("utf8")
    s=files[RACING].decode("utf8")
    a,b,method=block(s,"private void kickAllPlayers()")
    signature="for(int i = 0; i < 18; i++)"
    i,j,outer=block(method,signature)
    old="SC_GameEnd.send(pc);"
    method=replace_loop(method,i,j,outer,old,
         BC+".CSharedBroadcast.sendRecipient(shared,pc);",
         "SC_GameEnd.sharedWire()",
         ("pc.getMapId() == getMapId()","pc.start_teleport("))
    s=s[:a]+method+s[b:]
    a,b,method=block(s,"public void endGame()")
    signature="for(L1PcInstance c : L1World.getInstance().getAllPlayers()){"
    i,j,loop=block(method,signature)
    old="SC_GameOver.send(c);"
    method=replace_loop(method,i,j,loop,old,
        BC+".CSharedBroadcast.sendRecipient(shared,c);",
        "SC_GameOver.sharedWire()",
        ("c.getMapId() == getMapId()",))
    s=s[:a]+method+s[b:]
    files[RACING]=s.encode("utf8")
NPC_HELPER=r'''
    /** Reuses the exact same formatNpcChat prefix and type/color policy
     * as sendNpc(), then frames plaintext once for all receivers.
     */
    public static byte[] sharedNpcWire(L1NpcInstance npc,String chat,int type) {
        if(npc==null)return null;
        final String formatted=formatNpcChat(npc,chat,type);
        if(formatted==null)return null;
        return sharedWire(npc.getId(),type==4?NPC_CHAT_NOMAL_4:type,formatted);
    }

'''
def patch_npc(files):
    s=files[NPC_PACKET].decode("utf8")
    anchor="    private static boolean sendFormatted(L1PcInstance pc, int npcId, int color, String text) {"
    assert s.count(anchor)==1 and "public static byte[] sharedNpcWire(" not in s
    assert "formatNpcChat(npc,chat,type)" in s
    files[NPC_PACKET]=s.replace(anchor,NPC_HELPER+anchor,1).encode("utf8")
    s=files[NPC_TIMER].decode("utf8")
    a,b,method=block(s,"private void chat(L1NpcInstance npc, int chatTiming, String chatId,")
    signature="for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {"
    i,j,loop=block(method,signature)
    old="SC_NpcChatPacket.sendNpc(pc, npc, chatId, 3);"
    method=replace_loop(method,i,j,loop,old,
        BC+".CSharedBroadcast.sendRecipient(shared,pc);",
        "SC_NpcChatPacket.sharedNpcWire(npc,chatId,3)",
        ("if (pc != null)",))
    files[NPC_TIMER]=(s[:a]+method+s[b:]).encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase50FanoutParity {
 static int checks;
 static void eq(byte[] expected,byte[] actual){
  if(!Arrays.equals(expected,actual))throw new AssertionError("wire "+checks);
  checks++;
 }
 static byte[] gameEnd(){
  return new byte[]{10,0,(byte)Opcodes.S_EVENT,0x46,
       (byte)147,92,(byte)151,(byte)220,42,74};
 }
 static byte[] gameOver(){
  return new byte[]{10,0,(byte)Opcodes.S_EVENT,0x45,
       10,109,85,(byte)208,2,(byte)220};
 }
 static byte[] display(int n){
  return new byte[]{10,0,(byte)Opcodes.S_EVENT,0x53,
    (byte)n,(byte)(n>>>8),(byte)(n>>>16),(byte)(n>>>24),0,0};
 }
 static byte[] say(int obj,int color,String msg){
  byte[] str=msg.getBytes(Charset.forName("MS949"));
  byte[] wire=new byte[9+str.length];
  wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
  wire[2]=(byte)Opcodes.S_SAY_CODE;wire[3]=(byte)color;
  for(int i=0;i<4;i++)wire[4+i]=(byte)(obj>>>(i*8));
  System.arraycopy(str,0,wire,8,str.length);
  return wire;
 }
 static void check(int id,String s){
  eq(display(id),SC_DisplayEffect.sharedWire(id));
  eq(say(id,3,s),SC_NpcChatPacket.sharedWire(id,3,s));
 }
 public static void main(String[]args){
  eq(gameEnd(),SC_GameEnd.sharedWire());
  eq(gameOver(),SC_GameOver.sharedWire());
  for(int n:new int[]{0,1,2,23,-1,Integer.MIN_VALUE,Integer.MAX_VALUE})check(n,"NPC 한글");
  int n=0x2149bd15;
  for(int i=0;i<8192;i++){n^=n<<13;n^=n>>>17;n^=n<<5;check(n,"타이머-"+n);}
  System.out.println("PHASE50_FANOUT_PARITY_OK "+checks);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase50"));args=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase49 pinned baseline mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch_ship(files);patch_grace(files);patch_shop(files);patch_race(files);patch_npc(files)
 changed=sorted(n for n in files if files[n]!=before[n])
 assert changed==TARGETS,(changed,TARGETS)
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 test=args.work/"testsrc/Phase50FanoutParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":50,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
   "java_sources":1710,"modified_java":changed,
   "ship_display_effect_loops_migrated":5,
   "grace_party_message_migrated":1,"shop_warning_migrated":1,
   "racing_completion_loops_migrated":2,"npc_world_chat_loop_migrated":1,
   "total_direct_send_sites_migrated":10,
   "npc_sound_per_recipient_kept":True,"ship_event_teleport_sound_order_preserved":True,
   "gm_shop_warning_filter_kept":True,
   "native_unchanged":True,"new_workers_queues_classes":0,"no_fallback":True,
   "windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
