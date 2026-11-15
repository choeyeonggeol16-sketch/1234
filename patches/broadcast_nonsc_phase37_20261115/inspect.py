#!/usr/bin/env python3
from pathlib import Path
import zipfile
zfile=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase36_FULL_SRC_20261115.zip"
with zipfile.ZipFile(zfile) as z:
 assert z.testzip() is None
 for short,spans in (
   ("SC_ChartRank.java",[(1,400)]),
   ("MJKDAChartUpdator.java",[(1,160)]),
   ("SC_SystemMessage.java",[(1,85)]),
   ("L1MonsterInstance.java",[(560,640)]),
   ("L1World.java",[(730,785)]),
 ):
  for path in [n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]:
   lines=z.read(path).decode("utf8").splitlines()
   print("====== FILE",path,"LINES",len(lines))
   for a,b in spans:
    for i in range(a-1,min(len(lines),b)):print(f"{i+1:4} {lines[i]}")

 print("======= WRITE BUFFER BIT/RAW")
 for p in z.namelist():
  if not p.startswith("src/") or not p.endswith("/WriteBuffer.java"):continue
  lines=z.read(p).decode("utf8").splitlines()
  for i,line in enumerate(lines):
   if any(x in line for x in ("writeBit(", "writeByte(", "writeBytes(", "writeH(")):
    print("--",p,i+1)
    for j in range(max(0,i-4),min(len(lines),i+20)):print(f"{j+1:4} {lines[j]}")
