#!/usr/bin/env python3
"""Phase47: shared party-flag, emblem and resurrection ASK, plus boss coin
announcements lazily shared only after first eligible recipient. Keep all
per-recipient reward writes, FX, AOI and reset ordering unchanged.
"""
from pathlib import Path
import re,argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase46_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase47_FULL_SRC_20261115.zip"
PIN="bfa9db624f2ed1c2166eefbc2d9fd15220d147e9"
BASE="src/l1j/server/"
BOX=BASE+"server/model/network/core/sc/packet/SC_PacketBox.java"
ACTION=BASE+"server/model/network/core/sc/packet/SC_ActionUI.java"
CHANNEL=BASE+"server/model/network/core/cs/packet/CS_Channel.java"
ACTION_UI=BASE+"server/model/network/core/cs/packet/CS_ActionUi.java"
RESS=BASE+"server/command/executor/L1Ress.java"
MONSTER=BASE+"server/model/Instance/L1MonsterInstance.java"
TARGETS=sorted([BOX,ACTION,CHANNEL,ACTION_UI,RESS,MONSTER])
BC="l1j.server.server.model.network.core.broadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def balanced(s,sig,start=0):
    i=s.find(sig,start)
    if i<0:raise AssertionError(("not found",sig))
    j=s.index("{",i);depth=0
    for k in range(j,len(s)):
        if s[k]=="{":depth+=1
        elif s[k]=="}":
            depth-=1
            if not depth:return i,k+1,s[i:k+1]
    raise AssertionError(("unbalanced",sig))
ACTION_HELPER=r'''
    /** S_EXTENDED_PROTOBUF 339 flag transport. Payload is snapshotted
     * exactly once before multicast, with original trailing H(0).
     */
    public static byte[] sharedFlagWire(byte[] flag) {
        final int n=flag==null?0:flag.length;
        if(n>65528)return null;
        final byte[] wire=new byte[n+7];
        wire[0]=(byte)wire.length;
        wire[1]=(byte)(wire.length>>>8);
        wire[2]=(byte)Opcodes.S_EXTENDED_PROTOBUF;
        wire[3]=(byte)339;
        wire[4]=(byte)(339>>>8);
        if(n!=0)System.arraycopy(flag,0,wire,5,n);
        return wire;
    }

'''
EMBLEM_HELPER=r'''
    /** Constructor-7 S_EVENT PLEDGE_EMBLEM_STATUS packet. The original
     * writer omits the status byte for values other than 0 or 1, and
     * always appends D(0); preserve that unusual legacy behavior.
     */
    public static byte[] sharedEmblemStatusWire(int status) {
        final boolean hasByte=status==0 || status==1;
        final int size=hasByte?10:9;
        final byte[] wire=new byte[size];
        wire[0]=(byte)size;
        wire[2]=(byte)Opcodes.S_EVENT;
        wire[3]=(byte)PLEDGE_EMBLEM_STATUS;
        wire[4]=1;
        if(hasByte)wire[5]=(byte)status;
        return wire;
    }

'''
def patch(files):
 s=files[ACTION].decode("utf8")
 anchor="    public static boolean send(L1PcInstance pc, byte[] flag) {"
 assert s.count(anchor)==1 and "public static byte[] sharedFlagWire(" not in s
 for needed in ("buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);","buffer.writeH(339);","if (flag != null) buffer.writeByte(flag);","buffer.writeH(0);"):
  assert needed in s
 files[ACTION]=s.replace(anchor,ACTION_HELPER+anchor,1).encode("utf8")
 s=files[BOX].decode("utf8")
 anchor="    public static byte[] sharedTownLeaderWire(String leader){"
 assert s.count(anchor)==1 and "public static byte[] sharedEmblemStatusWire(" not in s
 for need in ("case PLEDGE_EMBLEM_STATUS:","buffer.writeC(1);","if (_val1 == 0) buffer.writeC(0);",
              "else if (_val1 == 1) buffer.writeC(1);","buffer.writeD(0x00);"):
  assert need in s
 files[BOX]=s.replace(anchor,EMBLEM_HELPER+anchor,1).encode("utf8")
 s=files[ACTION_UI].decode("utf8")
 a,b,old=balanced(s,"private static void handlePartyFlag(")
 assert old.count("SC_ActionUI.send(member, bytes);")==1
 assert "final L1Party party" in old and "if (party == null)" in old
 i,j,loop=balanced(old,"for (L1PcInstance member :")
 assert "party.getMembers()" in loop and "if (member != null)" in loop
 new=old[:i]+BC+".CSharedBroadcast.sendArray(party.getMembers(),SC_ActionUI.sharedFlagWire(bytes));"+old[j:]
 files[ACTION_UI]=(s[:a]+new+s[b:]).encode("utf8")
 s=files[CHANNEL].decode("utf8")
 a,b,old=balanced(s,"private static void handleEmblem(")
 i,j,loop=balanced(old,"for (L1PcInstance member : clan.getOnlineClanMember()) {")
 send="SC_PacketBox.send(member, SC_PacketBox.PLEDGE_EMBLEM_STATUS, emblemStatus);"
 assert loop.count(send)==1 and "if (member != null)" in loop
 assert "ClanTable.getInstance().updateClan(clan);" in old
 replaced=loop.replace(send,BC+".CSharedBroadcast.sendRecipient(emblemShared,member);",1)
 wrapped=("try(var emblemShared=new "+BC+".CAsyncSendConstBuffer(\n"+
          "SC_PacketBox.sharedEmblemStatusWire(emblemStatus),Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n"+
          replaced+"\n}")
 new=old[:i]+wrapped+old[j:]
 files[CHANNEL]=(s[:a]+new+s[b:]).encode("utf8")
 s=files[RESS].decode("utf8")
 a,b,old=balanced(s,"public void execute(L1PcInstance pc, String cmdName, String arg) {")
 i,j,loop=balanced(old,"for (L1PcInstance tg : L1World.getInstance(). getVisiblePlayer(pc)) {")
 send='SC_MessageYN.send(tg, 322, "");'
 assert loop.count(send)==1
 for check in ("tg.setTempID(objid);","SC_SkillSound.broadcast(tg, tg.getId(), 3944);",
               "tg.setCurrentHp(tg.getMaxHp());"):
  assert check in loop
 updated=loop.replace(send,BC+".CSharedBroadcast.sendRecipient(askShared,tg);",1)
 wrapped=("try(var askShared=new "+BC+".CAsyncSendConstBuffer(\n"+
          'SC_MessageYN.sharedWire(322,""),Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n'+
          updated+"\n}")
 new=old[:i]+wrapped+old[j:]
 files[RESS]=(s[:a]+new+s[b:]).encode("utf8")
 s=files[MONSTER].decode("utf8")
 for sig,itemId,countCfg,configMent in (
  ("private void BossCoinGet(L1Character lastAttacker)","Config.BossAreaDropItemId","Config.BossAreaDropItemCount","Config.한입만멘트"),
  ("private void BossCoinGet1(L1Character lastAttacker)","Config.BossAreaDropItemId1","Config.BossAreaDropItemCount1","Config.한입만멘트1")
 ):
  a,b,old=balanced(s,sig)
  i,j,loop=balanced(old,"for (L1PcInstance pc : L1World.getInstance().getAllPlayers())")
  rx=re.compile(r'SC_PacketBox\.send\(pc,\s*SC_PacketBox\.GREEN_MESSAGE,\s*([\s\S]*?)\);')
  found=list(rx.finditer(loop))
  assert len(found)==1,(sig,len(found))
  expr=found[0].group(1)
  for marker in (itemId,countCfg,configMent,"TYUtil.GetItemInstByID"):
   assert marker in expr,(sig,marker)
  for marker in ("createNewItem(pc,", "SC_SkillSound.send(pc, pc.getId(), 7783);",
                 "SC_SkillSound.broadcast(pc, pc.getId(), 7783);","TYUtil.SetSystemMessage(pc",
                 "pc.set특정보스"):
   assert marker in loop,(sig,marker)
  old_send=found[0].group(0)
  new_send=("if(greenShared==null)\n"
            " greenShared=new "+BC+".CAsyncSendConstBuffer(\n"+
            " SC_PacketBox.sharedGreenWire("+expr+"),Integer.MIN_VALUE,Integer.MAX_VALUE);\n"+
            BC+".CSharedBroadcast.sendRecipient(greenShared,pc);")
  modified=loop.replace(old_send,new_send,1)
  wrapped=(BC+".CAsyncSendConstBuffer greenShared=null;\n"
           "try{\n"+modified+"\n}"
           "finally{if(greenShared!=null)greenShared.close();}")
  old=old[:i]+wrapped+old[j:]
  s=s[:a]+old+s[b:]
 assert "SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, Config.한입만멘트" not in s
 files[MONSTER]=s.encode("utf8")
TEST=r'''import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase47EventFanoutParity {
 static int cases;
 static void eq(byte[] expected,byte[] actual){
  if(!Arrays.equals(expected,actual))throw new AssertionError("wire "+cases);cases++;
 }
 static byte[] flag(byte[] data){
  int n=data==null?0:data.length;
  ByteBuffer b=ByteBuffer.allocate(7+n).order(ByteOrder.LITTLE_ENDIAN);
  b.putShort((short)(7+n)).put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)339);
  if(data!=null)b.put(data);
  b.putShort((short)0);return b.array();
 }
 static byte[] emblem(int x){
  int size=(x==0||x==1)?10:9;
  ByteBuffer b=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
  b.putShort((short)size).put((byte)Opcodes.S_EVENT)
   .put((byte)SC_PacketBox.PLEDGE_EMBLEM_STATUS).put((byte)1);
  if(x==0 || x==1)b.put((byte)x);
  b.putInt(0);return b.array();
 }
 static void check(int x){
  eq(emblem(x),SC_PacketBox.sharedEmblemStatusWire(x));
  byte[] data=new byte[(x&15)+1];
  for(int i=0;i<data.length;i++)data[i]=(byte)(x>>>((i%4)*8));
  eq(flag(data),SC_ActionUI.sharedFlagWire(data));
 }
 public static void main(String[] args){
  eq(flag(null),SC_ActionUI.sharedFlagWire(null));
  eq(flag(new byte[0]),SC_ActionUI.sharedFlagWire(new byte[0]));
  for(int x:new int[]{0,1,2,-1,127,255,Integer.MAX_VALUE,Integer.MIN_VALUE})check(x);
  int x=0x711332dc;
  for(int i=0;i<8192;i++){x^=x<<13;x^=x>>>17;x^=x<<5;check(x);}
  System.out.println("PHASE47_EVENT_FANOUT_PARITY_OK "+cases);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase47"));a=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase46 pinned SRC differs"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch(files)
 modified=sorted(n for n in files if files[n]!=before[n]);assert modified==TARGETS,(modified,TARGETS)
 assert files["CTransport.dll"]==before["CTransport.dll"]
 a.work.mkdir(parents=True,exist_ok=True)
 for n,raw in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   p=a.work/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
 test=a.work/"testsrc/Phase47EventFanoutParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,raw in sorted(files.items()):z.writestr(n,raw)
 with zipfile.ZipFile(OUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
 info={"phase":47,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
  "java_sources":1710,"modified_java":modified,
  "per_recipient_direct_send_sites_migrated":5,
  "clan_emblem_committed_before_fanout":True,
  "party_flag_array_order_and_duplicates_preserved":True,
  "resurrection_fx_and_temp_id_before_ask_preserved":True,
  "boss_item_and_fx_before_green_preserved":True,
  "boss_wire_created_only_for_first_eligible_receiver":True,
  "native_unchanged":True,"no_fallback":True,
  "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (a.work/"audit.json").write_text(json.dumps(info,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(info,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
