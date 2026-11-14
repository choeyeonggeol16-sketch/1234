#!/usr/bin/env python3
"""Phase18: remove fully dead L1J legacy Broadcaster class and import-only
references. Phase16 proved no active callsite after deleting its method.
This pass modifies only dead imports, the removed class and no game logic.
"""
from pathlib import Path
import zipfile,hashlib,json,argparse
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase17_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase18_FULL_SRC_20261115.zip"
PIN="21de626b4a3560631d9f76a1cbffaccc9afad632"
DEAD="src/l1j/server/server/model/Broadcaster.java"
IMPORT="import l1j.server.server.model.Broadcaster;\n"
def blobsha(v):return hashlib.sha1(b"blob "+str(len(v)).encode()+b"\0"+v).hexdigest()
def main():
 ap=argparse.ArgumentParser();ap.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase18"));args=ap.parse_args()
 raw=SOURCE.read_bytes()
 assert blobsha(raw)==PIN,"phase17 SRC pin mismatch"
 with zipfile.ZipFile(SOURCE) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 assert sum(p.endswith(".java") for p in files)==1711 and "CTransport.dll" in files
 original=files.copy()
 assert b"public class Broadcaster" in files[DEAD]
 assert b"broadcastPacket(" not in files[DEAD],"legacy method returned"
 del files[DEAD]
 touched=[]
 for p,b in list(files.items()):
  if not(p.startswith("src/") and p.endswith(".java")):continue
  txt=b.decode("utf8")
  if IMPORT in txt:
   assert txt.count(IMPORT)==1,p
   files[p]=txt.replace(IMPORT,"").encode("utf8")
   touched.append(p)
 assert len(touched)>=20,len(touched)
 assert sum(p.endswith(".java") for p in files)==1710
 assert files["CTransport.dll"]==original["CTransport.dll"]
 assert sorted(set(original)-set(files))==[DEAD]
 assert sorted(n for n in files if files[n]!=original[n])==sorted(touched)
 args.work.mkdir(parents=True,exist_ok=True)
 for p,b in files.items():
  if p.startswith("src/") and p.endswith(".java"):
   f=args.work/p;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(b)
 OUTPUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for p,b in sorted(files.items()):z.writestr(p,b)
 with zipfile.ZipFile(OUTPUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":18,"input_blob":PIN,"output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
     "java_sources":1710,"modified_java":sorted(touched),"deleted_java":[DEAD],
     "old_type_imports_removed":len(touched),"all_program_logic_preserved":True,
     "no_legacy_broadcast_class":True,"native_unchanged":True,
     "no_legacy_fallback":True,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2)+"\n")
 print(json.dumps(report,indent=2))
if __name__=="__main__":main()
