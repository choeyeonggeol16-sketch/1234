#!/usr/bin/env python3
"""Phase39 targeted last-source audit for party loot ServerMessage and boss rewards."""
from pathlib import Path
import zipfile
src=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase38_FULL_SRC_20261115.zip"
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 for short,spans in (
  ("L1MonsterInstance.java",[(540,636),(1105,1165)]),
  ("SC_ServerMessage.java",[(1,150)]),
  ("CSharedBroadcast.java",[(220,257)]),
 ):
  for p in [n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]:
   lines=z.read(p).decode("utf8").splitlines()
   print("===== FILE",p)
   for a,b in spans:
    for i in range(a-1,min(len(lines),b)):print(f"{i+1:4} {lines[i]}")
