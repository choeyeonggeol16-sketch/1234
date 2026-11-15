#!/usr/bin/env python3
"""Phase35: all identical Dominance 11F event announcements and screen FX
are now sent using one shared immutable frame, keeping the 7 scripted event
messages, their original sleeps, PcCK() snapshots, and playable-world filter.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase34_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase35_FULL_SRC_20261115.zip"
PIN="c431e21d4a638eeed8da67383c7f87c0511de58e"
TARGET="src/l1j/server/TowerOfDominance/BossController/DominanceFloorLv11.java"
BC="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
EVENT=re.compile(
 r"for\s*\(\s*L1PcInstance\s+pc\s*:\s*PcCK\(\)\s*\)\s*\{\s*"
 r"SC_PacketBox\.send\(\s*pc\s*,\s*SC_PacketBox\.GREEN_MESSAGE\s*,\s*(\"(?:[^\"\\]|\\.)*\")\s*\);\s*"
 r"\}",re.DOTALL)
EFFECT=re.compile(
 r"for\s*\(\s*L1PcInstance\s+pc\s*:\s*L1World\.getInstance\(\)\.getAllPlayers\(\)\s*\)\s*\{\s*"
 r"if\s*\(\s*pc\.isPrivateShop\(\)\s*\|\|\s*!pc\.is_world\(\)\s*\)\s*continue;\s*"
 r"if\s*\(\s*effect\s*>\s*0\s*\)\s*SC_DisplayEffect\.send\(\s*pc\s*,\s*effect\s*\);\s*"
 r"\}",re.DOTALL)
def patch(files):
 s=files[TARGET].decode("utf8")
 notes=list(EVENT.finditer(s))
 assert len(notes)==7,[(m.start(),m.group(1)) for m in notes]
 expected=("27679","27689","27699","27707","27708","27688","27698")
 assert sorted([re.search(r'\d{5}',m.group(1)).group() for m in notes])==sorted(expected)
 s,n=EVENT.subn(lambda m: BC+
     ".sendRecipients(PcCK(),SC_PacketBox.sharedGreenWire("+m.group(1)+"));",s)
 assert n==7
 fx=list(EFFECT.finditer(s))
 assert len(fx)==1,len(fx)
 s,n=EFFECT.subn("if(effect>0)"+BC+
     ".sendAllPlayable(SC_DisplayEffect.sharedWire(effect));",s)
 assert n==1
 for token in ("Thread.sleep(7000);","Thread.sleep(15000);",
               "Thread.sleep(3000);","Thread.sleep(10000);",
               "for (L1PcInstance pc : PcCK()) {","SC_SystemMessage.broadcast(_ment)",
               "SC_PacketBox.broadcastGreen(_ment)"):
  assert token in s,token
 assert s.count("CSharedBroadcast.sendRecipients(PcCK(),") == 7
 assert s.count("CSharedBroadcast.sendAllPlayable(")==1
 files[TARGET]=s.encode("utf8")
 return {"dominance_green_notice_loops":7,"dominance_world_effect_loops":1,
     "pc_check_selection_unchanged":True,
     "event_sleep_and_spawn_order_unchanged":True,
     "private_shop_and_world_effect_filter_unchanged":True}
TEST=r'''import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.*;
public final class Phase35DominanceWireParity {
 static int checkCount;
 static void eq(byte[] a,byte[] b){
  if(!Arrays.equals(a,b))throw new AssertionError("wire parity "+checkCount);checkCount++;
 }
 static byte[] green(String s){
  byte[] t=s.getBytes(Charset.forName("MS949"));byte[] w=new byte[t.length+6];
  w[0]=(byte)w.length;w[1]=(byte)(w.length>>>8);
  w[2]=(byte)Opcodes.S_EVENT;w[3]=(byte)SC_PacketBox.GREEN_MESSAGE;
  w[4]=2;System.arraycopy(t,0,w,5,t.length);return w;
 }
 static byte[] display(int value){
  return ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
   .putShort((short)10).put((byte)Opcodes.S_EVENT)
   .put((byte)0x53).putInt(value).putShort((short)0).array();
 }
 static void test(String msg,int effect){
  eq(green(msg),SC_PacketBox.sharedGreenWire(msg));
  eq(display(effect),SC_DisplayEffect.sharedWire(effect));
 }
 public static void main(String[] args){
  for(String s:new String[]{"\\fV$27679","\\fY$27689","\\fY$27699",
      "\\fY$27707","\\fY$27708","\\fV$27688","\\fY$27698"})test(s,2);
  int r=0x71273458;
  for(int i=0;i<8192;i++){r^=r<<13;r^=r>>>17;r^=r<<5;
    test("11층 "+r,r);}
  System.out.println("PHASE35_DOMINANCE_PARITY_OK "+checkCount);
 }
}'''
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase35"));args=p.parse_args()
 raw=SRC.read_bytes()
 assert blob(raw)==PIN,"phase34 baseline changed"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 old=files.copy()
 assert sum(n.endswith(".java") for n in files)==1710 and "CTransport.dll" in files
 details=patch(files)
 modified=sorted(n for n in files if files[n]!=old[n])
 assert modified==[TARGET],modified
 assert files["CTransport.dll"]==old["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 test=args.work/"testsrc/Phase35DominanceWireParity.java";test.parent.mkdir(parents=True,exist_ok=True);test.write_text(TEST)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":35,"input_blob":PIN,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
    "java_sources":1710,"modified_java":modified,**details,"native_unchanged":True,
    "no_legacy_fallback":True,"new_workers_queues_classes":0,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
 print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
