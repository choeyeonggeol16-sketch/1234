#!/usr/bin/env python3
from pathlib import Path
import zipfile
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase52_FULL_SRC_20261116.zip"
targets={
"SkillHandlers.java":[(642,720)],
"ItemUseActiveSkills.java":[(307,360)],
"ItemUseConsumableRewards.java":[(127,158)],
"GMCommands.java":[(2947,3033),(4555,4609)],
"SC_SystemMessage.java":[(1,140)],
"CS_Result.java":[(368,389)],
"CGetItemAction.java":[(391,439)],
"DropShareEngine.java":[(164,186)],
"SC_Pledge.java":[(1,170)],
"AuraController.java":[(272,301)],
"SC_TrueTargetNew.java":[(1,72)]
}
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for short,ranges in targets.items():
  paths=[n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]
  for path in paths[:1]:
   lines=z.read(path).decode("utf8",errors="replace").splitlines()
   print("===FILE",path,len(lines))
   for a,b in ranges:
    print("BLOCK",a,b)
    for i in range(max(0,a-1),min(len(lines),b)):print(f"{i+1:4} {lines[i]}")
