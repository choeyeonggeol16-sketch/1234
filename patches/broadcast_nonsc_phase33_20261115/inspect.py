#!/usr/bin/env python3
"""Read-only phase32 inspection of party hotpath and clan broadcast recipients."""
from pathlib import Path
import zipfile
zfile=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase32_FULL_SRC_20261115.zip"
with zipfile.ZipFile(zfile) as z:
 assert z.testzip() is None
 allnames=z.namelist()
 def view(short,spans):
  paths=[n for n in allnames if n.startswith("src/") and n.endswith("/"+short)]
  for p in paths:
   lines=z.read(p).decode("utf8").splitlines()
   print("===== FILE",p,"LINES",len(lines))
   for a,b in spans:
    for i in range(max(0,a-1),min(len(lines),b)):
     print(f"{i+1:4} {lines[i]}")
 view("L1Party.java",[(1,110),(298,393)])
 view("L1ChatParty.java",[(1,160)])
 view("L1ClanJoin.java",[(115,180)])
 view("UserCommands.java",[(1140,1245)])
 view("CSharedBroadcast.java",[(70,125),(222,266)])
 print("===== PROTO RAW IMPLEMENTATIONS")
 for path in allnames:
  if path.startswith("src/") and path.endswith(".java") and ("/Proto/" in path or path.endswith("/Proto.java")):
   lines=z.read(path).decode("utf8").splitlines()
   hits=[i for i,l in enumerate(lines) if "sendRaw(" in l or "class Proto " in l]
   for i in hits[:16]:
    print("====",path,i+1)
    for j in range(max(0,i-9),min(len(lines),i+32)):
     print(f"{j+1:4} {lines[j]}")
