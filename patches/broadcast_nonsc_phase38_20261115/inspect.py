#!/usr/bin/env python3
"""Read-only exact phase37 Orim effect packet and script sequence audit."""
from pathlib import Path
import zipfile
src=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase37_FULL_SRC_20261115.zip"
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 cases=(("OrimController.java",[(1,135),(428,460),(770,851),(896,950)]),
   ("SC_EffectLocation.java",[(1,160)]),
   ("SC_PacketBox.java",[(1,175),(230,285),(320,460)]),
   ("CSharedBroadcast.java",[(1,135),(218,260)]))
 for short,spans in cases:
  for p in [n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]:
   lines=z.read(p).decode("utf8").splitlines()
   print("===== FILE",p,len(lines))
   for a,b in spans:
    for i in range(a-1,min(len(lines),b)):print(f"{i+1:4} {lines[i]}")
