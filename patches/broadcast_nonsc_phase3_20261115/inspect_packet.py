#!/usr/bin/env python3
import pathlib,zipfile,hashlib
ROOT=pathlib.Path(__file__).resolve().parents[2]
src=ROOT/"downloads/RKern_Broadcast_NonSC_Phase2_FULL_SRC_20261115.zip"
assert hashlib.sha256(src.read_bytes()).hexdigest()=="c3788d17f4b3e66f41a86205e59b8f16ce3b06b45f9bfedd3d26abe054a15f91"
targets=[
"src/l1j/server/server/serverpackets/S_SkillSound.java",
"src/l1j/server/server/model/item/function/DragonPearl.java",
"src/l1j/server/server/model/network/core/cs/packet/CS_ActionUi.java",
"src/l1j/server/MJItemSkillSystem/Model/MJItemSkillModel.java"]
all=[]
with zipfile.ZipFile(src) as z:
 for p in targets:
  try:s=z.read(p).decode('utf8')
  except KeyError:all.append("MISSING "+p);continue
  rows=s.splitlines()
  all+=["======== "+p+" ========"]
  for i,line in enumerate(rows,1):
   if p.endswith("CS_ActionUi.java") and not (1340<=i<=1410):continue
   if p.endswith("MJItemSkillModel.java") and not (155<=i<=230):continue
   all.append(str(i)+": "+line)
out=ROOT/"reports/broadcast_nonsc_phase3_20261115/EXACT_PACKET_SOURCES.txt"
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text("\n".join(all)+"\n")
print("CAPTURED",len(all))
