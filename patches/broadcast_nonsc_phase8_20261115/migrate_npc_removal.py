#!/usr/bin/env python3
"""Phase8: immutable shared removal for NPC deletion/teleport and PC teleport cleanup.

Input: exact validated phase7 complete source, Git blob SHA pinned.
Keep AOI membership snapshots, all known-object changes, effect->remove
ordering, self exclusion, original catch and lifecycle behavior.
"""
import argparse,hashlib,json,pathlib,re,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase7_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase8_FULL_SRC_20261115.zip"
PIN="c522a52d159834dc9b9aadbe05cff25f6da609f3"
NPC="src/l1j/server/server/model/Instance/L1NpcInstance.java"
TELEPORT="src/l1j/server/server/model/item/teleport/L1Teleport.java"
REMOVE="src/l1j/server/server/model/network/core/sc/packet/SC_RemoveObject.java"
BCAST="l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer"

def blob_sha(data):
 return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()

def method_bounds(src,signature):
 if src.count(signature)!=1:raise AssertionError("ambiguous declaration "+signature)
 a=src.index(signature);begin=src.index("{",a);depth=0
 for i in range(begin,len(src)):
  if src[i]=="{":depth+=1
  elif src[i]=="}":
   depth-=1
   if depth==0:return a,i+1,src[a:i+1]
 raise AssertionError("unbalanced method: "+signature)

def once(src,pattern,replacement,label):
 matches=list(re.finditer(pattern,src,re.MULTILINE))
 if len(matches)!=1:raise AssertionError(label+" expected one match, got "+str(len(matches)))
 m=matches[0]
 return src[:m.start()]+replacement+src[m.end():]

def shared_open(objectId):
 return "try(var shared=new "+BCAST+"(SC_RemoveObject.broadcastWire("+objectId+"),Integer.MIN_VALUE,Integer.MAX_VALUE)) {"

def send(sharedId="pc"):
 return f'''final var client={sharedId}.getNetConnection();
                    if(client!=null && client.isConnected()) {{
                        final var socket=client.getConnection();
                        if(socket!=null && socket.isClientConnected())
                            shared.send(socket,{sharedId}.getId());
                    }}'''

# NPC deleteMe captures AOI *before* leaveAndRemoveObject; retain that snapshot.
DELETEME=(r'for\s*\(\s*L1PcInstance\s+pc\s*:\s*players\s*\)\s*\{\s*'
 r'if\s*\(\s*pc\s*!=\s*null\s*\)\s*\{\s*'
 r'pc\.removeKnownObject\(this\)\s*;\s*'
 r'SC_RemoveObject\.send\(\s*pc\.getNetConnection\(\)\s*,\s*this\s*\)\s*;\s*'
 r'\}\s*\}')

DELETEME_NEW=shared_open("getId()")+'''
                for(L1PcInstance pc:players) {
                    if(pc!=null) {
                        pc.removeKnownObject(this);
                        '''+send()+'''
                    }
                }
            }'''

# NPC teleport: effect 169 first, removal second, known-object reset third.
NPC_TELE=(r'for\s*\(\s*L1PcInstance\s+pc\s*:\s*'
 r'L1World\.getInstance\(\)\.getVisiblePlayerFast\(this\)\s*\)\s*\{\s*'
 r'if\s*\(\s*pc\s*==\s*null\s*\)\s*\{\s*continue\s*;\s*\}\s*'
 r'SC_SkillSound\.send\(\s*pc\s*,\s*getId\(\)\s*,\s*169\s*\)\s*;\s*'
 r'SC_RemoveObject\.send\(\s*pc\.getNetConnection\(\)\s*,\s*this\s*\)\s*;\s*'
 r'pc\.removeKnownObject\(this\)\s*;\s*\}')
NPC_TELE_NEW=shared_open("getId()")+'''
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayerFast(this)) {
                if(pc==null)continue;
                SC_SkillSound.send(pc,getId(),169);
                '''+send()+'''
                pc.removeKnownObject(this);
            }
        }'''

# Alternate NPC cleanup uses getRecognizePlayer(this), not the fast AOI snapshot.
ALTERNATE=DELETEME
ALTERNATE_NEW=DELETEME_NEW

def npc(src):
 a,b,body=method_bounds(src,"public void deleteMe() {")
 assert "getVisiblePlayerFast(this)" in body
 assert "L1World.getInstance().leaveAndRemoveObject(this)" in body
 assert body.index("getVisiblePlayerFast(this)") < body.index("leaveAndRemoveObject(this)")
 body=once(body,DELETEME,DELETEME_NEW,"NPC deleteMe")
 src=src[:a]+body+src[b:]
 a,b,body=method_bounds(src,"public void teleport(int nx, int ny, int dir) {")
 assert body.index("SC_SkillSound.send") < body.index("SC_RemoveObject.send") < body.index("pc.removeKnownObject(this)")
 body=once(body,NPC_TELE,NPC_TELE_NEW,"NPC teleport")
 src=src[:a]+body+src[b:]
 marker="getRecognizePlayer(this);"
 if src.count(marker)!=1:raise AssertionError("NPC recognize cleanup not unique")
 a=src.index(marker)
 b=src.index("removeAllKnownObjects();",a)
 segment=src[a:b]
 assert "if (players.size() > 0)" in segment
 segment=once(segment,ALTERNATE,ALTERNATE_NEW,"NPC recognize cleanup")
 src=src[:a]+segment+src[b:]
 return src

# Original teleport cleanup ignores null viewers and the teleporting PC itself.
PC_TELE=(r'for\s*\(\s*L1PcInstance\s+v\s*:\s*world\.getVisiblePlayerFast\(pc\)\s*\)'
 r'\s*\{\s*if\s*\(\s*v\s*==\s*null\s*\|\|\s*v\s*==\s*pc\s*\)\s*\{\s*continue\s*;\s*\}\s*'
 r'v\.removeKnownObject\(pc\)\s*;\s*'
 r'SC_RemoveObject\.send\(\s*v\.getNetConnection\(\)\s*,\s*pc\s*\)\s*;\s*\}')
PC_TELE_NEW=shared_open("pc.getId()")+'''
                for(L1PcInstance v:world.getVisiblePlayerFast(pc)) {
                    if(v==null || v==pc)continue;
                    v.removeKnownObject(pc);
                    '''+send("v")+'''
                }
            }'''

def teleport(src):
 a,b,body=method_bounds(src,"private static void clearPcFromOldViewers(L1PcInstance pc) {")
 assert "catch (Exception e)" in body
 assert body.index("v.removeKnownObject(pc)") < body.index("SC_RemoveObject.send")
 body=once(body,PC_TELE,PC_TELE_NEW,"player old-viewer removal")
 return src[:a]+body+src[b:]

TEST=r'''import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
public final class SharedRemovalParity8 {
 public static void main(String[] args) {
  int count=0,v=0x55aa9966;
  for(int i=0;i<4096;i++){
   v^=v<<13;v^=v>>>17;v^=v<<5;
   int id=i==0?0:i==1?-1:i==2?Integer.MIN_VALUE:i==3?Integer.MAX_VALUE:v;
   byte[] expected=new byte[9];
   expected[0]=9;expected[2]=(byte)Opcodes.S_REMOVE_OBJECT;
   expected[3]=(byte)id;expected[4]=(byte)(id>>>8);
   expected[5]=(byte)(id>>>16);expected[6]=(byte)(id>>>24);
   byte[] actual=SC_RemoveObject.broadcastWire(id);
   if(!Arrays.equals(expected,actual))throw new AssertionError("wire mismatch: "+i);
   count+=2;
  }
  System.out.println("SHARED_REMOVAL_PARITY8_OK "+count);
 }
}'''

def main():
 p=argparse.ArgumentParser()
 p.add_argument("--input",type=pathlib.Path,default=INPUT)
 p.add_argument("--output",type=pathlib.Path,default=OUTPUT)
 p.add_argument("--work",type=pathlib.Path,default=pathlib.Path("/tmp/broadcast-nonsc-phase8"))
 args=p.parse_args()
 data=args.input.read_bytes()
 assert blob_sha(data)==PIN,"Not the exact phase7 compiled full source"
 with zipfile.ZipFile(args.input) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 before=files.copy()
 assert "CTransport.dll" in files
 assert sum(n.endswith(".java") for n in files)==1711
 assert "public static byte[] broadcastWire(int objectId)" in files[REMOVE].decode()
 for path,fn in [(NPC,npc),(TELEPORT,teleport)]:
  old=files[path].decode("utf8")
  new=fn(old)
  assert old!=new
  files[path]=new.encode("utf8")
 changed=sorted(n for n in files if files[n]!=before[n])
 assert changed==sorted((NPC,TELEPORT)),changed
 assert files["CTransport.dll"]==before["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for name,raw in files.items():
  if name.startswith("src/") and name.endswith(".java"):
   dest=args.work/name
   dest.parent.mkdir(parents=True,exist_ok=True)
   dest.write_bytes(raw)
 test=args.work/"testsrc/SharedRemovalParity8.java"
 test.parent.mkdir(parents=True,exist_ok=True)
 test.write_text(TEST)
 args.output.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(args.output,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for name,raw in sorted(files.items()):z.writestr(name,raw)
 with zipfile.ZipFile(args.output) as z:
  assert z.testzip() is None
  assert set(z.namelist())==set(files)
 report={
  "phase":8,"source_blob":PIN,
  "output_sha256":hashlib.sha256(args.output.read_bytes()).hexdigest(),
  "modified_files":changed,"full_java_sources":1711,
  "npc_delete_aoi_snapshot_order_preserved":True,
  "npc_teleport_effect_remove_known_order_preserved":True,
  "npc_recognize_filter_preserved":True,"pc_teleport_exclude_self_preserved":True,
  "no_fallback":True,"native_dll_unchanged":True,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2)+"\n")
 print(json.dumps(report,indent=2))
if __name__=="__main__":main()
