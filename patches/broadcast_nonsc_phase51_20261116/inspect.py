#!/usr/bin/env python3
from pathlib import Path
import zipfile
src=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase50_FULL_SRC_20261116.zip"
targets={
"SC_ClanAttention.java":[(1,330)],
"CS_Attr.java":[(527,560)],
"CS_PledgeWatch.java":[(205,280)],
"SC_Party.java":[(1,270)],
"L1Party.java":[(237,260)],
"SC_ShowCmd.java":[(1,220)],
"MJKDA.java":[(320,382)],
"L1Gambling.java":[(49,96)],
"L1Gambling3.java":[(49,96)],
"OrimController.java":[(514,559)],
"SC_ChatPacket.java":[(1,245)]
}
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 for suffix,ranges in targets.items():
  names=[n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+suffix)]
  for name in names[:1]:
   lines=z.read(name).decode("utf8",errors="replace").splitlines()
   print("===FILE",name,len(lines))
   for st,end in ranges:
    print("---LINES",st,end)
    for i in range(max(0,st-1),min(len(lines),end)):
     print(f"{i+1:4} {lines[i]}")
