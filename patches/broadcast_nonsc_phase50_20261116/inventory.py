#!/usr/bin/env python3
"""Inventory active non-shared packet fanouts from phase49 full source.
Output targeted contexts, never mistake commented-out calls for live code.
"""
from pathlib import Path
import re,zipfile,collections
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase49_FULL_SRC_20261115.zip"
def mask(text):
 o=list(text);i=0;n=len(text);mode="code";escaped=False
 while i<n:
  c=text[i];d=text[i+1] if i+1<n else ""
  if mode=="code":
   if c=='"':mode="str"
   elif c=="'":mode="chr"
   elif c=="/" and d=="/":o[i]=o[i+1]=" ";i+=2;mode="line";continue
   elif c=="/" and d=="*":o[i]=o[i+1]=" ";i+=2;mode="block";continue
  elif mode in ("str","chr"):
   if escaped:escaped=False
   elif c=="\\":escaped=True
   elif c==('"' if mode=="str" else "'"):mode="code"
  elif mode=="line":
   if c=="\n":mode="code"
   else:o[i]=" "
  elif mode=="block":
   if c=="*" and d=="/":o[i]=o[i+1]=" ";i+=2;mode="code";continue
   if c!="\n":o[i]=" "
  i+=1
 return "".join(o)
rx=re.compile(r'\bfor\s*\(\s*(?:(?:final|var)\s+)?(?:L1PcInstance|L1Character|L1Object)\s+(\w+)\s*:\s*[^;]{1,240}\)\s*\{')
# 'SC' and direct sends in loops
sc=re.compile(r'\b(SC_[A-Za-z0-9_]+)\.(send|send[A-Z]\w*)\s*\(\s*(\w+)\s*[,)]')
packet=re.compile(r'\b(?:sendPackets|sendPacket)\s*\(')
lines_by_file={};candidates=[]
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for name in z.namelist():
  if not(name.startswith("src/") and name.endswith(".java")):continue
  raw=z.read(name).decode("utf8",errors="replace");masked=mask(raw)
  lines_by_file[name]=raw.splitlines()
  for m in rx.finditer(masked):
   var=m.group(1);op=m.end()-1;depth=0;stop=None
   for at in range(op,min(len(masked),op+50000)):
    if masked[at]=="{":depth+=1
    elif masked[at]=="}":
     depth-=1
     if depth==0:stop=at+1;break
   if stop is None:continue
   body=masked[op:stop]
   hits=[(k.group(1),k.group(2),k.group(3),masked[op+k.start():op+k.end()]) for k in sc.finditer(body) if k.group(3)==var]
   # Also ChatData.send and member.sendPackets (original string)
   extra=[x for x in re.findall(r'(?:packet|sChatPacket|chatData)\.send\(\s*'+re.escape(var)+r'\s*\)',body)]
   extra+=re.findall(r'\b'+re.escape(var)+r'\.sendPackets\(',body)
   if hits or extra:
    line=raw.count("\n",0,m.start())+1
    raw_body=raw[op:stop]
    sendlines=[]
    for idx,linev in enumerate(raw_body.splitlines()):
     if re.search(r'\bSC_\w+\.send\(\s*'+re.escape(var)+r'\b|\b'+re.escape(var)+r'\.sendPackets\(|(?:packet|sChatPacket|chatData)\.send\(\s*'+re.escape(var)+r'\b',linev):
      sendlines.append(linev.strip()[:170])
    candidates.append((name,line,var,hits,extra,sendlines))
 print("JAVA",len(lines_by_file),"ACTIVE_DIRECT_LOOP_CANDIDATES",len(candidates))
 print("TOP_FILES",collections.Counter(n for n,_,_,_,_,_ in candidates).most_common(80))
 for n,line,var,hits,extra,sendlines in candidates[:350]:
  print("CANDIDATE",n,line,var,"direct="+repr([x[0]+"."+x[1] for x in hits][:12]),"extra="+repr(extra[:8]),"lines="+repr(sendlines[:8])[:600])
 print("=== TARGETED SOURCE CONTEXT ===")
 focus={
  "CS_Craft.java":["SC_NewChat.sharedWire(packet)","ChatType.Alliance","ChatType.Guardian"],
  "SC_NewChat.java":["public static byte[] sharedWire(ChatData d)"],
  "CSharedBroadcast.java":["public static","sendRecipient("],
  "L1World.java":["broadcastServerMessage("],
  "SC_PacketBox.java":["public static byte[] sharedGreenWire("],
  "L1MonsterInstance.java":["BossCoinGet"],
  "SC_ActionUI2.java":["broadcast("],
  "MJRaidMessage.java":["sendMessage(ArrayList"],
  "CS_Craft.java":["SC_NewChat.sharedWire(packet)"],
  "CS_Chat.java":["SC_NewChat.send"],
 }
 for short,terms in focus.items():
  for n,ls in lines_by_file.items():
   if not n.endswith("/"+short):continue
   print("FOCUS",n,len(ls))
   idxs=[i for i,l in enumerate(ls) if any(x in l for x in terms)]
   for i in idxs[:8]:
    lo=max(0,i-12);hi=min(len(ls),i+28)
    print("----")
    for j in range(lo,hi):print(f"{j+1} {ls[j]}")
