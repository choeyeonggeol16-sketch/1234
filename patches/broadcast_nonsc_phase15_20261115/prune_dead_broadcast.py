#!/usr/bin/env python3
"""Phase15: remove obsolete *unreferenced* legacy broadcast overloads.
Do not alter the active ServerPacket family or change any packet behavior.
The entire phase14 production source is compiled with these methods absent.
"""
from pathlib import Path
import hashlib,zipfile,json,argparse
ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase14_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase15_FULL_SRC_20261115.zip"
PIN="85b50b789b9152d35d480352a2dfde76ca042f45"
BASE="src/l1j/server/"
TARGETS={
 BASE+"MJInstanceSystem/MJLFC/Template/MJLFCObject.java":[
   "protected void broadcastPacket(int subCode, int value) {",
   "protected void broadcastPacket(int subCode, String value) {",
   "protected void broadcastPacket(ServerPacket pck){",
 ],
 BASE+"MJTemplate/L1Instance/MJL1LiftGateInstance.java":[
   "public void broadcast(ServerPacket packet){",
   "public void broadcast(ServerPacket packet, boolean isClear){",
 ],
 BASE+"server/model/L1World.java":[
   "public void broadcast_map(int map_id, ServerPacket pck){",
   "public void broadcast_map(int map_id, ServerPacket pck, boolean is_clear){",
   "public void broadcast_map(int map_id, ServerPacket[] pcks){",
   "public void broadcast_map(int map_id, ServerPacket[] pcks, boolean is_clear){",
 ],
 BASE+"server/model/L1Character.java":[
   "public void broadcastPacketM(ServerPacket packet) {",
   "public void broadcastPacketFast(ServerPacket packet) {",
   "public void wideBroadcastPacket(ServerPacket packet) {",
 ]
}
def sha(buf):return hashlib.sha1(b"blob "+str(len(buf)).encode()+b"\0"+buf).hexdigest()
def bounds(src,sig):
 assert src.count(sig)==1,(sig,src.count(sig))
 a=src.index(sig);b=src.index("{",a);depth=0
 for i in range(b,len(src)):
  if src[i]=="{":depth+=1
  elif src[i]=="}":
   depth-=1
   if depth==0:return a,i+1
 raise ValueError("method braces missing "+sig)
def patch(src,sigs):
 for sig in sigs:
  a,b=bounds(src,sig)
  src=src[:a]+src[b:]
 return src
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase15"));args=p.parse_args()
 raw=INPUT.read_bytes()
 assert sha(raw)==PIN,"Phase14 verified source mismatch"
 with zipfile.ZipFile(INPUT) as z:
  assert z.testzip() is None
  files={n.filename:z.read(n) for n in z.infolist() if not n.is_dir()}
 assert sum(x.endswith(".java") for x in files)==1711
 assert "CTransport.dll" in files
 # All deleted methods have zero other static callsites.
 disallowed={
   "broadcastPacketM(":["src/l1j/server/server/model/L1Character.java"],
   "broadcastPacketFast(":["src/l1j/server/server/model/L1Character.java"],
   "wideBroadcastPacket(":["src/l1j/server/server/model/L1Character.java"],
 }
 for needle,owners in disallowed.items():
  for n,b in files.items():
   if n.startswith("src/") and n.endswith(".java") and n not in owners:
    assert needle not in b.decode("utf8"),(n,needle)
 old=files.copy()
 for name,sigs in TARGETS.items():
  assert name in files,name
  source=patch(files[name].decode("utf8"),sigs)
  if name.endswith("MJLFCObject.java") or name.endswith("MJL1LiftGateInstance.java"):
   if source.count("ServerPacket")==1:
    source=source.replace("import l1j.server.server.model.network.packet.ServerPacket;\n","")
  files[name]=source.encode("utf8")
 changes=sorted(p for p in files if files[p]!=old[p])
 assert changes==sorted(TARGETS),changes
 assert files["CTransport.dll"]==old["CTransport.dll"]
 assert b"public void broadcast_map(int map_id, String message)" in files[BASE+"server/model/L1World.java"]
 assert b"public void broadcastPacket(ServerPacket packet)" in files[BASE+"server/model/L1Character.java"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,b in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   path=args.work/n;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b)
 OUTPUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,b in sorted(files.items()):z.writestr(n,b)
 with zipfile.ZipFile(OUTPUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":15,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
         "java_sources":1711,"modified_java":changes,"obsolete_legacy_overloads_removed":sum(map(len,TARGETS.values())),
         "all_production_java_compiled":False,"active_generic_paths_untouched":True,
         "native_unchanged":True,"no_legacy_fallback":True,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2)+"\n")
 print(json.dumps(report,indent=2))
if __name__=="__main__":main()
