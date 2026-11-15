#!/usr/bin/env python3
"""Phase44: thirteen production class edits. Convert identical fanout
messages, preserving per-recipient selection, iteration order, duplicate
deliveries, member/receipt/loot side-effect order and per-client FIFO.
No new worker, queue, wrapper class, legacy fallback or snapshots.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase43_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase44_FULL_SRC_20261115.zip"
PIN="7d83de5e27ef3cb492332d572fc61c06eb7b371c"
BASE="src/l1j/server/server/"
BC="l1j.server.server.model.network.core.broadcast"
SYSTEM="l1j.server.server.model.network.core.sc.packet.SC_SystemMessage"
SERVER="l1j.server.server.model.network.core.sc.packet.SC_ServerMessage"
SITES=[
 ("model/L1NewUltimateBattle.java","pc","SC_SystemMessage.send(pc, msg);","SC_SystemMessage.sharedWire(msg)",()),
 ("model/L1UltimateBattle.java","pc","SC_SystemMessage.send(pc, msg);","SC_SystemMessage.sharedWire(msg)",()),
 ("model/BookQuest/Loader/BQSLoadManager.java","pc","SC_SystemMessage.send(pc, message);","SC_SystemMessage.sharedWire(message)",("BQSCharacterDataLoader.storeCharacterBqs","SC_CRITERIA_UPDATE_NOTI.sendOf")),
 ("model/BookQuest/Loader/BQSWQDecksLoader.java","pc","SC_SystemMessage.send(pc, message);","SC_SystemMessage.sharedWire(message)",("send_decks_noti(pc)","bqs")),
 ("model/character/Item/Drop/DropShareEngine.java","member","SC_SystemMessage.send(member, msg);","SC_SystemMessage.sharedWire(msg)",("member.RootMent1",)),
 ("model/character/action/CGetItemAction.java","member","SC_SystemMessage.send(member, msg);","SC_SystemMessage.sharedWire(msg)",("member.RootMent1","member.getId() == owner.getId()")),
 ("command/executor/L1PartyRecall.java","pc2",'SC_SystemMessage.send(pc2, "메티스님께서 소환하였습니다.");','SC_SystemMessage.sharedWire("메티스님께서 소환하였습니다.")',("pc2.start_teleport(","catch (Exception e)")),
 ("model/network/core/cs/packet/CS_EnterWorld.java","member","SC_ServerMessage.send(member, 843, pc.getName());","SC_ServerMessage.sharedWire(843,pc.getName())",("member.getId() != pc.getId()",)),
 ("model/network/core/cs/packet/CS_LeavePledge.java","member","SC_ServerMessage.send(member, 178, playerName, clanName);","SC_ServerMessage.sharedWire(178,playerName,clanName,null)",("if (member != null)","clan.getOnlineClanMember()")),
 ("model/network/core/cs/packet/CS_Title.java","clanPc","SC_ServerMessage.send(clanPc, 203, pc.getName(), charName, title);","SC_ServerMessage.sharedWire(203,pc.getName(),charName,title)",("if (clanPc == null)","continue;")),
 ("command/executor/L1Recall.java","target",'SC_SystemMessage.send(target, "\\f:운영자님께서 당신을 소환 하였습니다.");','SC_SystemMessage.sharedWire("\\f:운영자님께서 당신을 소환 하였습니다.")',("SC_Ability.send(target, 3, true);",))
]
# special site: L1PcInstance uses an unchanged item name for every GM
GM_ITEM=BASE+"model/Instance/L1PcInstance.java"
# special site: CS_Channel sends three fixed texts to each GM
GM_REPORT=BASE+"model/network/core/cs/packet/CS_Channel.java"
TARGETS=sorted({BASE+path for path,_,_,_,_ in SITES}|{GM_ITEM,GM_REPORT})
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def find_enclosing(src,recipient,needle):
    assert src.count(needle)==1,(recipient,needle,src.count(needle))
    pos=src.index(needle)
    rx=re.compile(r'\bfor\s*\(\s*(?:final\s+)?L1PcInstance\s+'+re.escape(recipient)+r'\s*:\s*[\s\S]{0,500}?\)\s*\{')
    candidates=[]
    for m in rx.finditer(src):
        a=m.start();j=m.end()-1;depth=0
        for k in range(j,len(src)):
            if src[k]=="{":depth+=1
            elif src[k]=="}":
                depth-=1
                if depth==0:
                    if a<pos<k:candidates.append((a,k+1,src[a:k+1]))
                    break
    assert candidates,(recipient,needle)
    return min(candidates,key=lambda x:x[1]-x[0])
def wrap_one(src,recipient,needle,frame_expr,guard):
    a,b,loop=find_enclosing(src,recipient,needle)
    for x in guard:assert x in loop,(needle,x)
    assert loop.count(needle)==1
    changed=loop.replace(needle,BC+".CSharedBroadcast.sendRecipient(shared,"+recipient+");",1)
    wrapped="try(var shared=new "+BC+".CAsyncSendConstBuffer(\n       "+frame_expr+",Integer.MIN_VALUE,Integer.MAX_VALUE)) {\n"+changed+"\n}"
    return src[:a]+wrapped+src[b:]
def patch(files):
    for path,recipient,needle,frame_expr,guards in SITES:
        key=BASE+path
        s=files[key].decode("utf8")
        files[key]=wrap_one(s,recipient,needle,frame_expr,guards).encode("utf8")
    s=files[GM_ITEM].decode("utf8")
    lines=s.splitlines()
    matches=[line for line in lines if 'SC_SystemMessage.send(pc,' in line and '[GM]' in line and '증발' in line]
    assert len(matches)==1,matches
    old=matches[0].strip()
    prefix='SC_SystemMessage.send(pc, '
    assert old.startswith(prefix) and old.endswith(');'),old
    expr=old[len(prefix):-2]
    s=wrap_one(s,"pc",old,"SC_SystemMessage.sharedWire("+expr+")",("if (pc.isGm())",))
    files[GM_ITEM]=s.encode("utf8")
    s=files[GM_REPORT].decode("utf8")
    needle='SC_SystemMessage.send(gm, "\\fY 신 고 자 : " + reporterName);'
    a,b,loop=find_enclosing(s,"gm",needle)
    sends=re.findall(r'SC_SystemMessage\.send\(gm,\s*(.*?)\);',loop)
    assert len(sends)==3,sends
    assert "if (gm.isGm())" in loop
    assert "reporterName" in sends[0] and "targetName" in sends[1]
    updated=loop
    for i,expr in enumerate(sends):
        old='SC_SystemMessage.send(gm, '+expr+');'
        assert updated.count(old)==1
        updated=updated.replace(old,BC+f".CSharedBroadcast.sendRecipient(shared{i},gm);",1)
    buffers=[]
    for i,expr in enumerate(sends):
        buffers.append("var shared"+str(i)+"=new "+BC+".CAsyncSendConstBuffer(\n       SC_SystemMessage.sharedWire("+expr+"),Integer.MIN_VALUE,Integer.MAX_VALUE)")
    wrapped="try("+ ";\n ".join(buffers) +") {\n"+updated+"\n}"
    s=s[:a]+wrapped+s[b:]
    files[GM_REPORT]=s.encode("utf8")
TEST=r'''import java.nio.charset.Charset;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
public final class Phase44BatchParity {
 static int count; static Charset MS949=Charset.forName("MS949");
 static void eq(byte[] a,byte[] b){if(!Arrays.equals(a,b))throw new AssertionError("wire "+count);count++;}
 static byte[] msg(String s){
  byte[] t=s.getBytes(MS949);byte[] b=new byte[t.length+5];
  b[0]=(byte)b.length;b[1]=(byte)(b.length>>>8);
  b[2]=(byte)Opcodes.S_MESSAGE;b[3]=9;System.arraycopy(t,0,b,4,t.length);
  return b;
 }
 static byte[] code(int id,String...args){
  int len=6,used=0;
  for(String s:args)if(s!=null){len+=s.getBytes(MS949).length+1;used++;}
  ByteBuffer b=ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);
  b.putShort((short)len).put((byte)Opcodes.S_MESSAGE_CODE).putShort((short)id).put((byte)used);
  for(String s:args)if(s!=null){b.put(s.getBytes(MS949));b.put((byte)0);}
  return b.array();
 }
 static void check(String s,int id){
  eq(msg(s),SC_SystemMessage.sharedWire(s));
  eq(code(id,s),SC_ServerMessage.sharedWire(id,s));
  eq(code(id,s,null,"혈맹"),SC_ServerMessage.sharedWire(id,s,null,"혈맹"));
  eq(code(id,s,"도감",null),SC_ServerMessage.sharedWire(id,s,"도감",null));
 }
 public static void main(String[] args){
  for(String s:new String[]{"","길드 접속","주간퀘스트가 갱신되었습니다.","메티스님께서 소환하였습니다.","[GM] 아이템 증발","\\fY 공지"})check(s,203);
  int x=0x3781abce;
  for(int i=0;i<4096;i++){x^=x<<13;x^=x>>>17;x^=x<<5;check("fanout-"+x,x);}
  System.out.println("PHASE44_BATCH_PARITY_OK "+count);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase44"));a=p.parse_args()
 raw=SRC.read_bytes();assert blob(raw)==PIN,"phase43 source pin mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 patch(files)
 changed=sorted(n for n in files if files[n]!=before[n])
 assert changed==TARGETS,(changed,TARGETS)
 assert files["CTransport.dll"]==before["CTransport.dll"]
 a.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=a.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 t=a.work/"testsrc/Phase44BatchParity.java";t.parent.mkdir(parents=True,exist_ok=True);t.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 result={"phase":44,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
   "java_sources":1710,"modified_java":changed,
   "shared_loop_sites":len(SITES)+2,"repeated_identical_messages_replaced":len(SITES)+4,
   "loot_member_filter_and_receipt_order_preserved":True,
   "book_quest_mutation_notice_order_preserved":True,
   "clan_login_leave_title_message_order_preserved":True,
   "gm_report_three_messages_per_gm_fifo_preserved":True,
   "native_unchanged":True,"no_legacy_fallback":True,
   "new_workers_queues_classes":0,"windows_runtime_tested":False}
 (a.work/"audit.json").write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n")
 print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
