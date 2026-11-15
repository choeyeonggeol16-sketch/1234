#!/usr/bin/env python3
"""Inspect scheduled broadcast macro provider types and fallback reachability."""
from pathlib import Path
import zipfile,re
src=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase35_FULL_SRC_20261115.zip"
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 names=z.namelist()
 targets=[n for n in names if n.startswith("src/l1j/server/MJServerMacroSystem/") and n.endswith(".java")]
 targets+=[n for n in names if n.startswith("src/") and n.endswith(("/SC_IconMessage.java","/SC_ChatMessageNoti.java"))]
 print("TARGETS",targets)
 for n in targets:
  lines=z.read(n).decode("utf8").splitlines()
  print("======== FILE",n,"LINES",len(lines))
  if n.endswith("/MJServerMacroLoader.java"):
   spans=[(1,len(lines))]
  elif n.endswith(("/SC_IconMessage.java","/SC_ChatMessageNoti.java")):
   spans=[(1,min(len(lines),180))]
  else:spans=[(1,min(len(lines),250))]
  for a,b in spans:
   for i in range(a-1,b):print(f"{i+1:4} {lines[i]}")
