#!/usr/bin/env python3
from pathlib import Path
import zipfile,re,collections
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase43_FULL_SRC_20261115.zip"
def mask(text):
 o=list(text);i=0;n=len(text);mode="c";escape=False
 while i<n:
  c=text[i];d=text[i+1] if i+1<n else ""
  if mode=="c":
   if c=='"':mode="s"
   elif c=="'":mode="q"
   elif c=="/" and d=="/":o[i]=o[i+1]=" ";i+=2;mode="l";continue
   elif c=="/" and d=="*":o[i]=o[i+1]=" ";i+=2;mode="b";continue
  elif mode in ("s","q"):
   if escape:escape=False
   elif c=="\\":escape=True
   elif c==('"' if mode=="s" else "'"):mode="c"
  elif mode=="l":
   if c=="\n":mode="c"
   else:o[i]=" "
  elif mode=="b":
   if c=="*" and d=="/":o[i]=o[i+1]=" ";i+=2;mode="c";continue
   if c!="\n":o[i]=" "
  i+=1
 return "".join(o)
hits=[]
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for name in z.namelist():
  if not name.startswith("src/") or not name.endswith(".java"):continue
  src=z.read(name).decode("utf8",errors="replace");dead=mask(src)
  for m in re.finditer(r'for\s*\(\s*(?:final\s+)?L1PcInstance\s+(\w+)\s*:\s*([^;\n]+)\)\s*\{',dead):
   recipient=m.group(1);scope=m.group(2).strip()
   if len(scope)>140:continue
   cursor=m.end()-1;balance=0;end=None
   for j in range(cursor,min(len(dead),cursor+35000)):
    if dead[j]=="{":balance+=1
    elif dead[j]=="}":
     balance-=1
     if balance==0:end=j+1;break
   if end is None:continue
   content=dead[m.end():end];orig=src[m.end():end]
   pattern=re.compile(r'\b(SC_\w+)\.send\(\s*'+re.escape(recipient)+r'\s*,[^\n]*')
   sends=pattern.findall(content)
   if not sends:continue
   first=m.start();line=src[:first].count("\n")+1
   info=[]
   for h in pattern.finditer(content):
    lineText=orig[h.start():h.end()].strip()
    if len(lineText)>160:lineText=lineText[:160]
    info.append(lineText)
   # Record selected fanouts; exclude loops all personalized by id/connection.
   if all(re.search(r"\b"+re.escape(recipient)+r"\s*\.\s*get",s) for s in info):continue
   hits.append((name,line,recipient,scope,info))
 print("DIRECT_PLAIN_FANOUT_COUNT",len(hits))
 for name,line,var,scope,info in hits[:170]:
  print("LOOP",name,line,"receiver",var,"from",scope[:90],"calls",str(info[:6])[:550])
 for short,needles in {
  "HomeTownTimeController.java":["SC_PacketBox.send(pc, SC_PacketBox.MSG_TOWN_LEADER"],
  "SC_PacketBox.java":["case MSG_TOWN_LEADER:","MSG_TOWN_LEADER ="],
  "Queen1.java":["clanname +","getVisibleObjects(_map).values()"],
  "L1War.java":["SC_SystemMessage.send","SC_ServerMessage.send"],
  "L1InnRoom.java":["SC_SystemMessage.send"],
  "MJRaidMessage.java":["public void sendMessage(ArrayList<L1PcInstance> pcs)"]
 }.items():
  path=next((n for n in z.namelist() if n.endswith("/"+short) and n.startswith("src/")),None)
  if path is None:continue
  ls=z.read(path).decode("utf8").splitlines()
  print("===TARGET",path)
  printCount=0;seen=set()
  for i,x in enumerate(ls):
   if any(needle in x for needle in needles):
    for j in range(max(0,i-12),min(len(ls),i+13)):
     if j not in seen:print(j+1,ls[j]);seen.add(j)
    printCount+=1
    if printCount>5:break
