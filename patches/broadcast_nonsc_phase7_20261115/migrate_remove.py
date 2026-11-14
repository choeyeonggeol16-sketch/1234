#!/usr/bin/env python3
"""Phase7: replace three per-viewer remove-object encodings with shared wire.

Source is the verified phase6 ZIP with exact pinned Git blob hash.
Preserves original knowledge mutation timing, recipient selection, and
per-player socket FIFO ordering. No generic ServerPacket or fallback.
"""
import argparse,hashlib,json,pathlib,re,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase6_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase7_FULL_SRC_20261115.zip"
PIN="2a759d2fc875bd7141246958e0c267ac2660a0eb"
BASE="src/l1j/server/server/"
GROUND=BASE+"model/L1GroundInventory.java"
EFFECT=BASE+"model/Instance/L1EffectInstance.java"
CHEST="src/l1j/server/MJTemplate/TreasureChest/MJL1TreasureChest.java"
REMOVE=BASE+"model/network/core/sc/packet/SC_RemoveObject.java"
BCAST="l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer"

def gitblob(data):
 return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()

def replace_once(src,pattern,replacement,label):
 new,count=re.subn(pattern,replacement,src,count=1,flags=re.MULTILINE)
 if count!=1 or re.search(pattern,new,re.MULTILINE):raise AssertionError("ambiguous: "+label)
 return new

def wire_for(oid):
 return "SC_RemoveObject.broadcastWire("+oid+")"

def make_send(clientexpr,idexpr):
 return f'''final var client={clientexpr};
                if(client!=null && client.isConnected()) {{
                    final var socket=client.getConnection();
                    if(socket!=null && socket.isClientConnected())
                        shared.send(socket,{idexpr});
                }}'''

GROUND_OLD=(r'for\s*\(\s*L1PcInstance\s+pc\s*:\s*'
 r'L1World\.getInstance\(\)\.getVisiblePlayerFast\(item\)\s*\)\s*\{'
 r'\s*SC_RemoveObject\.send\(\s*pc\.getNetConnection\(\)\s*,\s*item\s*\)\s*;'
 r'\s*pc\.removeKnownObject\(item\)\s*;\s*\}')
GROUND_NEW='''try(var shared=new '''+BCAST+'''(
                '''+wire_for("item.getId()")+''',Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayerFast(item)) {
                '''+make_send("pc.getNetConnection()","pc.getId()")+'''
                pc.removeKnownObject(item);
            }
        }'''
# For ground items the old order is SEND first, removeKnown second.
def ground(src):
 assert src.count("public void deleteItem(L1ItemInstance item)") == 1
 return replace_once(src,GROUND_OLD,lambda m:GROUND_NEW,"ground delete")

EFFECT_OLD=(r'for\s*\(\s*L1PcInstance\s+pc\s*:\s*'
 r'L1World\.getInstance\(\)\.getVisiblePlayerFast\(this\)\s*\)\s*\{'
 r'\s*if\s*\(\s*pc\s*==\s*null\s*\)\s*continue\s*;'
 r'\s*pc\.removeKnownObject\(this\)\s*;'
 r'\s*SC_RemoveObject\.send\(\s*pc\.getNetConnection\(\)\s*,\s*this\s*\)\s*;'
 r'\s*\}')
EFFECT_NEW='''try(var shared=new '''+BCAST+'''(
                '''+wire_for("getId()")+''',Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayerFast(this)) {
                if(pc==null)continue;
                pc.removeKnownObject(this);
                '''+make_send("pc.getNetConnection()","pc.getId()")+'''
            }
        }'''
# Effect instances forget world object before SEND; preserve exactly.
def effect(src):
 assert src.count("public void deleteMe()")==1
 return replace_once(src,EFFECT_OLD,lambda m:EFFECT_NEW,"effect remove")

def method_bounds(src,signature):
 if src.count(signature)!=1:raise AssertionError("method ambiguous "+signature)
 a=src.index(signature);start=src.index("{",a);depth=0
 for i in range(start,len(src)):
  if src[i]=="{":depth+=1
  if src[i]=="}":
   depth-=1
   if depth==0:return a,i+1,src[a:i+1]
 raise AssertionError("not balanced "+signature)

def chest(src):
 a,b,old=method_bounds(src,"private void remove() {")
 assert "getVisiblePlayer(this)" in old
 assert old.count("pc.removeKnownObject(this)") == 1
 assert old.count("SC_RemoveObject.send(") == 1
 assert old.index("pc.removeKnownObject(this)") < old.index("SC_RemoveObject.send(")
 new='''private void remove() {
        try(var shared=new '''+BCAST+'''(
                '''+wire_for("getId()")+''',Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayer(this)) {
                if(pc==null)continue;
                pc.removeKnownObject(this);
                '''+make_send("pc.getNetConnection()","pc.getId()")+'''
            }
        }
    }'''
 return src[:a]+new+src[b:]

TEST=r'''import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
public class RemoveObjectSharedParity {
  static int count;
  static void ok(boolean v) {if(!v)throw new AssertionError("parity "+count);count++;}
  public static void main(String[] args) {
    int v=0x13579246;
    for(int i=0;i<4096;i++){
      v^=v<<13;v^=v>>>17;v^=v<<5;
      int id=(i==0?0:(i==1?-1:(i==2?Integer.MIN_VALUE:(i==3?Integer.MAX_VALUE:v))));
      byte[] b=SC_RemoveObject.broadcastWire(id);
      byte[] expected=new byte[9];
      expected[0]=9;expected[2]=(byte)Opcodes.S_REMOVE_OBJECT;
      expected[3]=(byte)id; expected[4]=(byte)(id>>8);
      expected[5]=(byte)(id>>16); expected[6]=(byte)(id>>24);
      ok(Arrays.equals(expected,b));
      ok(b.length==9 && b[7]==0 && b[8]==0);
    }
    System.out.println("REMOVE_OBJECT_SHARED_WIRE_PARITY_OK "+count);
  }
}'''

def main():
 parser=argparse.ArgumentParser()
 parser.add_argument("--input",type=pathlib.Path,default=INPUT)
 parser.add_argument("--output",type=pathlib.Path,default=OUTPUT)
 parser.add_argument("--work",type=pathlib.Path,default=pathlib.Path("/tmp/broadcast-nonsc-phase7"))
 args=parser.parse_args()
 raw=args.input.read_bytes()
 assert gitblob(raw)==PIN,"not verified phase6 full source"
 with zipfile.ZipFile(args.input) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert sum(x.endswith(".java") for x in files)==1711
 assert "CTransport.dll" in files
 assert "public static byte[] broadcastWire(int objectId)" in files[REMOVE].decode()
 for path,fn in ((GROUND,ground),(EFFECT,effect),(CHEST,chest)):
  src=files[path].decode("utf8")
  target=fn(src)
  assert target!=src
  files[path]=target.encode("utf8")
 changed=sorted(x for x in files if files[x]!=before[x])
 assert changed==sorted([GROUND,EFFECT,CHEST])
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for path,data in files.items():
  if path.startswith("src/") and path.endswith(".java"):
   p=args.work/path
   p.parent.mkdir(parents=True,exist_ok=True)
   p.write_bytes(data)
 test=args.work/"testsrc/RemoveObjectSharedParity.java"
 test.parent.mkdir(parents=True,exist_ok=True)
 test.write_text(TEST)
 args.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(args.output,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for path,data in sorted(files.items()):z.writestr(path,data)
 with zipfile.ZipFile(args.output) as z:
  assert z.testzip() is None
  assert set(z.namelist())==set(files)
 report={
  "phase":7,"input_blob":PIN,"output_sha256":hashlib.sha256(args.output.read_bytes()).hexdigest(),
  "changed_files":changed,"full_java_sources":1711,
  "item_send_before_forget":True,"effect_and_chest_forget_before_send":True,
  "per_viewer_order_preserved":True,"no_fallback":True,"native_dll_unchanged":True,
  "windows_runtime_tested":False
 }
 (args.work/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
 print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=="__main__":main()
