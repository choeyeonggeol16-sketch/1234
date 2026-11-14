#!/usr/bin/env python3
"""Phase16: prove the full production source no longer requires the old
ServerPacket broadcast entry points. Retain raw-byte / string shared
broadcast methods. Remove only source-unreferenced generic interfaces and
the dead Broadcaster class. No changes to protocol/IOCP/FIFO/game behavior.
"""
from pathlib import Path
import zipfile,hashlib,json,argparse
ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase15_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase16_FULL_SRC_20261115.zip"
PIN="05b6529cdc5bd0cef2c665212d5bfc570d5e065a"
BASE="src/l1j/server/"
DELETE="src/l1j/server/server/model/Broadcaster.java"
TARGETS={
 BASE+"server/model/L1Character.java":[
  "public void broadcastPacket(ServerPacket[] pcs) {",
  "public void broadcastPacket(ServerPacket[] pcs, boolean isClear) {",
  "public void broadcastPacket(ServerPacket packet) {",
  "public void broadcastPacket(ServerPacket packet, int clanid) {",
  "public void broadcastPacket(ServerPacket packet, boolean isClear) {",
  "public void broadcastPacket(ServerPacket packet, L1Character target) {",
 ],
 BASE+"server/model/L1World.java":[
  "public void broadcastPacketToAll(ServerPacket[] pcks) {",
  "public void broadcastPacketToAll(ServerPacket[] pcks, boolean isClear) {",
  "public void broadcastPacketToAll(WritablePacket<CIOClient>[] packets) {",
  "public void broadcastPacketToAll(ServerPacket packet) {",
  "public void broadcastPacketToAll(ServerPacket packet, boolean clear) {",
 ],
 BASE+"server/model/character/action/CAttackAction.java":[
  "protected static void broadcast(L1Character c, ServerPacket pck) {",
 ],
 BASE+"MJItemSkillSystem/Model/MJItemSkillModel.java":[
  "protected void broadcast(L1Character c, ServerPacket pck){",
  "protected void sendPackets(L1Character c, ServerPacket pck){",
 ],
}
def blobsha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def remove_method(s,sig):
 assert s.count(sig)==1,(sig,s.count(sig))
 a=s.index(sig);b=s.index("{",a);depth=0
 for i in range(b,len(s)):
  if s[i]=="{":depth+=1
  elif s[i]=="}":
   depth-=1
   if depth==0:
    return s[:a]+s[i+1:]
 raise AssertionError("unbalanced "+sig)
def main():
 ap=argparse.ArgumentParser();ap.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase16"));args=ap.parse_args()
 raw=INPUT.read_bytes()
 assert blobsha(raw)==PIN,"phase15 source mismatch"
 with zipfile.ZipFile(INPUT) as z:
  assert z.testzip() is None
  files={f.filename:z.read(f) for f in z.infolist() if not f.is_dir()}
 old=files.copy()
 assert sum(n.endswith(".java") for n in files)==1711 and "CTransport.dll" in files
 assert b"public static void broadcastPacket(L1Character cha, ServerPacket packet)" in files[DELETE]
 files[DELETE]=remove_method(files[DELETE].decode("utf8"),
  "public static void broadcastPacket(L1Character cha, ServerPacket packet) {").encode("utf8")
 for path,methods in TARGETS.items():
  source=files[path].decode("utf8")
  for sig in methods:source=remove_method(source,sig)
  files[path]=source.encode("utf8")
 modified=sorted(n for n in list(TARGETS)+[DELETE] if old[n]!=files[n])
 assert modified==sorted(list(TARGETS)+[DELETE])
 assert set(old)==set(files)
 assert files["CTransport.dll"]==old["CTransport.dll"]
 # Still used shared raw and system-message routes MUST survive.
 assert b"public void broadcastPacket(byte[] data)" in files[BASE+"server/model/L1Character.java"]
 assert b"public void broadcastPacketToAll(String message)" in files[BASE+"server/model/L1World.java"]
 assert b"public void broadcast_map(int map_id, String message)" in files[BASE+"server/model/L1World.java"]
 # No other bytes modified.
 for path in set(old)&set(files):
  if path not in TARGETS and path!=DELETE:assert files[path]==old[path],path
 args.work.mkdir(parents=True,exist_ok=True)
 for path,data in files.items():
  if path.startswith("src/") and path.endswith(".java"):
   dest=args.work/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
 OUTPUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for path,data in sorted(files.items()):z.writestr(path,data)
 with zipfile.ZipFile(OUTPUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
 result={"phase":16,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
    "java_sources":sum(n.endswith(".java") for n in files),
    "modified_java":modified,"deleted_java":[],
    "obsolete_generic_methods_removed":sum(len(x) for x in TARGETS.values())+1,
    "raw_and_string_shared_broadcast_preserved":True,"native_unchanged":True,
    "no_legacy_fallback":True,"windows_runtime_tested":False}
 assert result["java_sources"]==1711
 (args.work/"audit.json").write_text(json.dumps(result,indent=2)+"\n")
 print(json.dumps(result,indent=2))
if __name__=="__main__":main()
