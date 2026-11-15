#!/usr/bin/env python3
"""Phase41: remove active L1Magic PC effect legacy broadcasts, preserving
send-before-AOI ordering and each success/failure branch. No legacy fallback.
"""
from pathlib import Path
import argparse,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_Broadcast_NonSC_Phase40_FULL_SRC_20261115.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase41_FULL_SRC_20261115.zip"
TARGET="src/l1j/server/server/model/L1Magic.java"
PIN="2df1ee034745d997cf56af05cb41ef6a8da666df"
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase41"));args=p.parse_args()
 raw=SRC.read_bytes()
 assert blob(raw)==PIN,"phase40 mismatch"
 with zipfile.ZipFile(SRC) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 old=files.copy();s=files[TARGET].decode("utf8")
 lines=s.splitlines(keepends=True)
 replacements=[]
 for i in range(len(lines)-1):
  first=lines[i].strip()
  second=lines[i+1].strip()
  for gfx in (14453,14452):
   send=f"_targetPc.sendPackets(new S_SkillSound(_targetPc.getId(), {gfx}));"
   broadcast=f"_targetPc.broadcastPacket(new S_SkillSound(_targetPc.getId(), {gfx}));"
   if first==send and second==broadcast:
    replacements.append((i,gfx))
 assert len(replacements)==4,[(i,repr(lines[i]),repr(lines[i+1])) for i in range(len(lines)-1) if "_targetPc.sendPackets(new S_SkillSound" in lines[i]][:12]
 for i,gfx in reversed(replacements):
  indent=lines[i][:len(lines[i])-len(lines[i].lstrip())]
  lines[i:i+2]=[indent+f"l1j.server.server.model.network.core.sc.packet.SC_SkillSound.sendAndBroadcast(_targetPc, _targetPc.getId(), {gfx});"+chr(10)]
 s="".join(lines)
 assert s.count("SC_SkillSound.sendAndBroadcast(_targetPc, _targetPc.getId(), 14453);")==2
 assert s.count("SC_SkillSound.sendAndBroadcast(_targetPc, _targetPc.getId(), 14452);")==2
 files[TARGET]=s.encode("utf8")
 changed=[n for n in files if files[n]!=old[n]]
 assert changed==[TARGET],changed
 assert files["CTransport.dll"]==old["CTransport.dll"]
 args.work.mkdir(parents=True,exist_ok=True)
 for n,data in files.items():
  if n.startswith("src/") and n.endswith(".java"):
   f=args.work/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(data)
 OUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for n,data in sorted(files.items()):z.writestr(n,data)
 with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":41,"input_blob":blob(raw),"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
   "java_sources":sum(n.endswith(".java") for n in files),"modified_java":changed,
   "legacy_pc_effect_broadcasts_removed":4,"send_before_aoi_preserved":True,
   "npc_and_commented_code_unchanged":True,"native_unchanged":True,
   "no_legacy_fallback":True,"new_workers_queues_classes":0,
   "windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2)+"\n")
 print(json.dumps(report,indent=2))
if __name__=="__main__":main()
