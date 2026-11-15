#!/usr/bin/env python3
from pathlib import Path
import zipfile,re,collections
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase54_FULL_SRC_20261116.zip"
rx=re.compile(r'\bfor\s*\([^;\n]*(?:L1PcInstance|L1Character|L1Object)[^;\n]*\)\s*\{')
bc=re.compile(r'\b(SC_\w+|L1World\.getInstance\(\))\.(broadcast\w*)\s*\(')
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 hits=[]
 for name in z.namelist():
  if not(name.startswith("src/") and name.endswith(".java")):continue
  src=z.read(name).decode("utf8",errors="replace")
  for m in rx.finditer(src):
   at=m.end()-1;depth=0;end=0
   for i in range(at,min(len(src),at+28000)):
    if src[i]=="{":depth+=1
    elif src[i]=="}":
     depth-=1
     if not depth:end=i+1;break
   if not end:continue
   body=src[at:end]
   for n in bc.finditer(body):
    if "//" in body[max(0,n.start()-35):n.start()].split("\n")[-1]:continue
    line=src.count("\n",0,m.start())+1
    hits.append((name,line,body[n.start():min(len(body),n.start()+210)].split("\n")[0]))
 print("NESTED_BROADCAST_HITS",len(hits))
 for row in hits[:180]:print("NESTED",*row)
 for short,spans in {
 "GMCommands.java":[(2950,3060),(3105,3199),(3720,3739)],
 "SC_PacketBox.java":[(1,65)],
 "SC_SkillSound.java":[(1,100)],
 "MJRaidMessage.java":[(1,120)],
 "CSharedBroadcast.java":[(1,220)],
 }.items():
  names=[n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]
  for name in names[:1]:
   lines=z.read(name).decode("utf8").splitlines()
   print("===FILE",name,"LINES",len(lines))
   for a,b in spans:
    print("== RANGE",a,b)
    for i in range(max(0,a-1),min(len(lines),b)):print(f"{i+1:4} {lines[i]}")
