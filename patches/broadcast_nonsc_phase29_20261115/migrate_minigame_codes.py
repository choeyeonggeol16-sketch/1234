#!/usr/bin/env python3
"""Phase29: race, deathmatch and haunted-house status sends become one native
immutable frame per event pass. Keep individual inventory/teleport/send order,
member removals, map predicates, group order and their original loop structure.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase28_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase29_FULL_SRC_20261115.zip"
PIN="358b3789327925888eab485184621fbf08e08cdc"
BASE="src/l1j/server/server/model/"
RACE=BASE+"L1Racing.java"
DEATH=BASE+"L1DeathMatch.java"
HAUNT=BASE+"L1HauntedHouse.java"
TARGETS=[RACE,DEATH,HAUNT]
FQ="l1j.server.server.model.network.core.broadcast"
def sha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def bounds(src,sig,occ=0):
    pos=-1
    for i in range(occ+1):
        pos=src.find(sig,pos+1)
        if pos<0:raise AssertionError((sig,occ,i))
    beg=src.index("{",pos);depth=0
    for j in range(beg,len(src)):
        if src[j]=="{":depth+=1
        elif src[j]=="}":
            depth-=1
            if depth==0:return pos,j+1,src[pos:j+1]
    raise AssertionError("unbalanced "+sig)
def wrap(src,method_sig,loop_sig,send,code,expected,occ=0):
    a,b,method=bounds(src,method_sig)
    p,q,loop=bounds(method,loop_sig,occ)
    assert loop.count(send)==1,(method_sig,send,loop.count(send))
    for token in expected:assert token in loop,(method_sig,token)
    repl=loop.replace(send,FQ+".CSharedBroadcast.sendRecipient(shared,pc);",1)
    assert repl!=loop
    new='''try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                    SC_ServerMessage.sharedWire('''+str(code)+'''),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+repl+'''
        }'''
    method=method[:p]+new+method[q:]
    return src[:a]+method+src[b:]
def patch(files):
    src=files[RACE].decode("utf8")
    src=wrap(src,"private boolean readyPetRacing() {",
        "for(int i = 0; i < 18; i++)",
        "SC_ServerMessage.send(pc, 1264);",1264,
        ["for (L1PcInstance pc : toArray(i))","pc.getMapId() == getMapId()",
         "pc.getInventory().storeItem(40308, 1000)","removeMember(pc)"])
    src=wrap(src,"private boolean readyPetRacing() {",
        "for(L1PcInstance pc : toArray(일반))",
        "SC_ServerMessage.send(pc, 1258);",1258,
        ["L1PolyMorph.doPoly(pc","SC_GameStart.send(pc)","SC_GameRap.send(pc, 1)",
         "SC_EtcPacket.send(pc, 4)"])
    files[RACE]=src.encode("utf8")
    src=files[DEATH].decode("utf8")
    method_sig="private void readyDeathMatch() {"
    loop_sig="for (L1PcInstance pc : getMembersArray()) {"
    src=wrap(src,method_sig,loop_sig,
        "SC_ServerMessage.send(pc, 1270);",1270,
        ["pc.getMapId() == 5153","removeMember(pc)","pc.getInventory().storeItem(40308, 1000)"],occ=0)
    src=wrap(src,method_sig,loop_sig,
        "SC_ServerMessage.send(pc, 1269);",1269,
        ["pc.getInventory().storeItem(410018, 1)"],occ=1)
    files[DEATH]=src.encode("utf8")
    src=files[HAUNT].decode("utf8")
    src=wrap(src,"private boolean readyHauntedHouse() {",
        "for (L1PcInstance pc : getMembersArray()) {",
        "SC_ServerMessage.send(pc, 1264);",1264,
        ["pc.getMapId() == 5140","pc.getInventory().storeItem(40308, 1000)",
         "pc.start_teleport(","removeMember(pc)"])
    assert "CSharedBroadcast.sendArray(getMembersArray(),SC_ServerMessage.sharedWire(1257))" in src
    files[HAUNT]=src.encode("utf8")
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
public final class Phase29EventCodeParity {
 static int tested;
 static void check(int id){
  byte[] expected=ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
    .putShort((short)6).put((byte)Opcodes.S_MESSAGE_CODE)
    .putShort((short)id).put((byte)0).array();
  if(!Arrays.equals(expected,SC_ServerMessage.sharedWire(id)))
    throw new AssertionError("type wire "+tested);
  tested++;
 }
 public static void main(String[] args){
  for(int v:new int[]{0,1258,1264,1269,1270,-1,65535,Integer.MIN_VALUE,Integer.MAX_VALUE})check(v);
  int r=0x3ac5712;
  for(int i=0;i<8192;i++){r^=r<<13;r^=r>>>17;r^=r<<5;check(r);}
  System.out.println("PHASE29_EVENT_CODES_PARITY_OK "+tested);
 }
}'''
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase29"));args=p.parse_args()
    raw=SOURCE.read_bytes();assert sha(raw)==PIN,"phase28 pin mismatch"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    before=files.copy()
    assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
    patch(files)
    changed=sorted(p for p in files if files[p]!=before[p])
    assert changed==sorted(TARGETS),changed
    assert files["CTransport.dll"]==before["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for n,data in files.items():
        if n.startswith("src/") and n.endswith(".java"):
            f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
    test=args.work/"testsrc/Phase29EventCodeParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n,data in sorted(files.items()):z.writestr(n,data)
    with zipfile.ZipFile(OUTPUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
    result={"phase":29,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
      "java_sources":1710,"modified_java":changed,"status_loops_shared":5,
      "racing_and_deathmatch_order_preserved":True,
      "individual_item_teleport_member_order_preserved":True,
      "haunted_house_status_reused":True,
      "native_unchanged":True,"no_fallback":True,"no_new_worker_or_queue":True,
      "windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
