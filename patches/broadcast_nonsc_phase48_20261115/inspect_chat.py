#!/usr/bin/env python3
from pathlib import Path
import zipfile
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase47_FULL_SRC_20261115.zip"
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for short in ("SC_NewChat.java","CS_Craft.java","SC_ActionUI2.java","CS_Channel.java"):
  for path in [n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)][:1]:
   lines=z.read(path).decode("utf8").splitlines()
   print("=====FILE",path,"LEN",len(lines))
   if short=="SC_NewChat.java":
    for i,l in enumerate(lines):print(f"{i+1:4} {l}")
   elif short=="CS_Craft.java":
    for i,l in enumerate(lines):
     if "SC_NewChat.send(listener," in l:
      for j in range(max(0,i-18),min(len(lines),i+18)):print(f"{j+1:4} {lines[j]}")
   elif short=="SC_ActionUI2.java":
    hits=[i for i,l in enumerate(lines) if "public static boolean send(" in l]
    for i in hits:
     for j in range(max(0,i-2),min(len(lines),i+80)):print(f"{j+1:4} {lines[j]}")
   else:
    hits=[i for i,l in enumerate(lines) if "SC_NewChat.send(" in l]
    for i in hits:
     for j in range(max(0,i-10),min(len(lines),i+10)):print(f"{j+1:4} {lines[j]}")
