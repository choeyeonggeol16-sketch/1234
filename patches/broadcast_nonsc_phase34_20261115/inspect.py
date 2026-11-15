#!/usr/bin/env python3
"""Read-only candidate audit from exact phase33 whole SRC."""
from pathlib import Path
import zipfile
src=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase33_FULL_SRC_20261115.zip"
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 target=[
 ("UserCommands.java",[(1138,1180),(1200,1250)]),
 ("FishingTimeController.java",[(558,616)]),
 ("DungeonTimeUpdator.java",[(128,195)]),
 ("L1ChatParty.java",[(85,135)]),
 ("SC_SkillSound.java",[(1,130)]),
 ("SC_DisplayEffect.java",[(1,110)]),
 ("SC_PacketBox.java",[(170,235)]),
 ("SC_SystemMessage.java",[(1,130)])
 ]
 for short,regions in target:
  matches=[x for x in z.namelist() if x.startswith("src/") and x.endswith("/"+short)]
  for p in matches:
   lines=z.read(p).decode("utf8").splitlines()
   print("======== TARGET",p)
   for a,b in regions:
    for i in range(a-1,min(len(lines),b)):print(f"{i+1:4} {lines[i]}")
