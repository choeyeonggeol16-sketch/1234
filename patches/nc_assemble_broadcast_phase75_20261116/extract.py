#!/usr/bin/env python3
"""Extract only relevant 74th baseline SC packet source for next migration review."""
from pathlib import Path
import zipfile,hashlib
root=Path(__file__).resolve().parents[2]
p=root/"downloads/RKern_NC_SPacket_Assemble_Phase74_FULL_SRC_20261116.zip"
assert hashlib.sha256(p.read_bytes()).hexdigest()=="4f5d0f8cdb1850693aa0b36ef1fba5c609292233f6c7558102f047287a24b3d8"
names=("SC_ChangeShape","SC_ChangeName","SC_CharTitle","SC_Dexup","SC_Strup","SC_Lawful","SC_Liquor","SC_PinkName","SC_SkillBrave","SC_SkillHaste","SC_Door","SC_NpcShopAction")
out=root/"reports/nc_phase75_inspect";out.mkdir(exist_ok=True)
with zipfile.ZipFile(p) as z:
 for n in names:
  entry="src/l1j/server/server/model/network/core/sc/packet/"+n+".java"
  (out/(n+".java")).write_bytes(z.read(entry))
print("EXTRACTED",len(names))
