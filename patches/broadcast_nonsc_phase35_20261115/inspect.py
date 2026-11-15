#!/usr/bin/env python3
"""Read-only targeted leftover fanout audit from verified Phase34 whole SRC."""
from pathlib import Path
import zipfile,re
src=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase34_FULL_SRC_20261115.zip"
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 names=z.namelist()
 for short in ("L1MonsterInstance.java","DominanceFloorLv11.java",
               "OrimController.java","L1PcInstance.java","CS_Channel.java",
               "MJL1TreasureChest.java","MJServerMacroLoader.java",
               "MJRaidObject.java","SC_ChartRank.java","MJKDAChartUpdator.java"):
  for p in [n for n in names if n.startswith("src/") and n.endswith("/"+short)]:
   lines=z.read(p).decode("utf8").splitlines()
   hits=[i for i,l in enumerate(lines) if re.search(r"\bSC_\w+\.send\(|\bpc\.sendPackets\(|broadcast\w*\(",l) and not l.lstrip().startswith("//")]
   print("\n==== FILE",p,"HITS",len(hits))
   selected=[i for i in hits if i<1450]
   if short=="L1PcInstance.java":selected=[i for i in selected if i+1 in range(3100,3260)]
   if short=="L1MonsterInstance.java":selected=[i for i in selected if (550<=i+1<=650 or 1120<=i+1<=1170)]
   if short=="OrimController.java":selected=[i for i in selected if i+1 in range(425,460) or i+1 in range(776,832)]
   if short=="DominanceFloorLv11.java":selected=[i for i in selected if i+1 in range(85,260)]
   covered=set()
   for h in selected[:30]:
    lo=max(0,h-13);hi=min(len(lines),h+13)
    print("--",h+1)
    for i in range(lo,hi):
     if i not in covered:print(f"{i+1:4} {lines[i]}")
     covered.add(i)
