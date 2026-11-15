#!/usr/bin/env python3
"""Phase28: ship arrival and storm green notices use immutable shared frames.
Original all-player traversal and map predicates stay; the personalized
effect/sound and inventory mutation continue in their exact original order.
"""
from pathlib import Path
import argparse,hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase27_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase28_FULL_SRC_20261115.zip"
PIN="4340c9ab936fe21b380e653dfdad42e2a082f118"
TARGET="src/l1j/server/server/Controller/ShipTimeController.java"
FQ="l1j.server.server.model.network.core.broadcast"
LOOP="for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {"
def sha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def after(src,anchor,signature=LOOP):
    assert src.count(anchor)==1,(anchor,src.count(anchor))
    beg=src.index(signature,src.index(anchor))
    pos=src.index("{",beg);depth=0
    for i in range(pos,len(src)):
        if src[i]=="{":depth+=1
        elif src[i]=="}":
            depth-=1
            if depth==0:return beg,i+1,src[beg:i+1]
    raise AssertionError("unbalanced "+anchor)
def patch(src):
    dock='if(Config.배알림 == 0){'
    a,b,old=after(src,dock)
    first='SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "해적섬 행 배가 선착장에 정박했습니다.");'
    second='SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "서둘러 배에 승선해주십시오.");'
    assert old.count(first)==1 and old.count(second)==1
    assert old.count("SC_SkillSound.send(pc, pc.getId(), 631);")==1
    assert "pc.getMapId() == 445" in old
    updated=old.replace(first,FQ+".CSharedBroadcast.sendRecipient(docked,pc);")
    updated=updated.replace(second,FQ+".CSharedBroadcast.sendRecipient(boarding,pc);")
    wrapped='''try(var docked=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_PacketBox.sharedGreenWire("해적섬 행 배가 선착장에 정박했습니다."),
                Integer.MIN_VALUE,Integer.MAX_VALUE);
            var boarding=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_PacketBox.sharedGreenWire("서둘러 배에 승선해주십시오."),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+updated+'''
        }'''
    src=src[:a]+wrapped+src[b:]
    cases=(
      ('String box = "갑자기 파도가 거세게 휘몰아칩니다.";', "pc.getInventory().consumeItem(40303, 1)"),
      ('String box = "선박이 심하게 흔들리고 있습니다.";', "SC_DisplayEffect.send(pc, eff1);"))
    for anchor,side_effect in cases:
        a,b,old=after(src,anchor)
        msg="SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, box);"
        assert old.count(msg)==1,(anchor,old.count(msg))
        assert side_effect in old,(anchor,side_effect)
        assert "SC_SkillSound.send(pc, pc.getId(), 20394);" in old
        assert "pc.getMapId() == 447" in old
        modified=old.replace(msg,FQ+".CSharedBroadcast.sendRecipient(shared,pc);",1)
        wrapped='''try(var shared=new '''+FQ+'''.CAsyncSendConstBuffer(
                SC_PacketBox.sharedGreenWire(box),Integer.MIN_VALUE,Integer.MAX_VALUE)){
            '''+modified+'''
        }'''
        src=src[:a]+wrapped+src[b:]
    assert src.count("CSharedBroadcast.sendRecipient(docked,pc);")==1
    assert src.count("CSharedBroadcast.sendRecipient(boarding,pc);")==1
    assert src.count("CSharedBroadcast.sendRecipient(shared,pc);")==2
    return src
TEST=r'''import java.util.Arrays;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
public final class Phase28ShipGreenParity {
 static int checks;
 static void test(String msg){
  byte[] t=msg.getBytes(Charset.forName("MS949"));
  byte[] wire=new byte[t.length+6];
  wire[0]=(byte)wire.length;wire[1]=(byte)(wire.length>>>8);
  wire[2]=(byte)Opcodes.S_EVENT;wire[3]=(byte)SC_PacketBox.GREEN_MESSAGE;
  wire[4]=2;System.arraycopy(t,0,wire,5,t.length);
  if(!Arrays.equals(wire,SC_PacketBox.sharedGreenWire(msg)))
    throw new AssertionError("ship parity "+checks);
  checks++;
 }
 public static void main(String[] args){
  for(String s:new String[]{"","해적섬 행 배가 선착장에 정박했습니다.",
   "서둘러 배에 승선해주십시오.","갑자기 파도가 거세게 휘몰아칩니다.",
   "선박이 심하게 흔들리고 있습니다.","\\f3Storm"})test(s);
  int r=0x5127abcd;
  for(int i=0;i<4096;i++){r^=r<<13;r^=r>>>17;r^=r<<5;test("배 이동 "+r);}
  System.out.println("PHASE28_SHIP_GREEN_PARITY_OK "+checks);
 }
}'''
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase28"));args=p.parse_args()
    raw=SOURCE.read_bytes();assert sha(raw)==PIN,"phase27 input mismatch"
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    baseline=files.copy()
    assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
    files[TARGET]=patch(files[TARGET].decode("utf8")).encode("utf8")
    changed=sorted(n for n in files if files[n]!=baseline[n])
    assert changed==[TARGET],changed
    assert files["CTransport.dll"]==baseline["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for n,b in files.items():
        if n.startswith("src/") and n.endswith(".java"):
            f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(b)
    f=args.work/"testsrc/Phase28ShipGreenParity.java";f.parent.mkdir(parents=True,exist_ok=True);f.write_text(TEST)
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for n,b in sorted(files.items()):z.writestr(n,b)
    with zipfile.ZipFile(OUTPUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
    result={"phase":28,"input_blob":PIN,
      "output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
      "java_sources":1710,"modified_java":changed,
      "ship_green_notice_sends":4,"ship_notice_loops":3,
      "map_predicate_and_personal_fx_preserved":True,
      "per_user_inventory_mutation_order_preserved":True,
      "native_unchanged":True,"no_fallback":True,"no_new_workers_or_queues":True,
      "windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=="__main__":main()
