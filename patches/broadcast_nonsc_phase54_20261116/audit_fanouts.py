#!/usr/bin/env python3
from pathlib import Path
import zipfile,re,collections
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase53_FULL_SRC_20261116.zip"
RX=re.compile(r'\bfor\s*\(\s*(?:final\s+)?(?:L1PcInstance|L1Character)\s+(\w+)\s*:\s*([^\n;]+)\)\s*\{')
SEND=re.compile(r'\b(SC_[A-Za-z0-9_]+)\.send\(\s*([A-Za-z_]\w*)\s*,([\s\S]{0,500}?)\);')
CALL=re.compile(r'\b([A-Za-z_]\w*)\.sendPackets\(\s*([\s\S]{0,300}?)\);')
def mask(s):
 o=list(s);i=0;n=len(s);mode="c";e=False
 while i<n:
  a=s[i];b=s[i+1] if i+1<n else ""
  if mode=="c":
   if a=='"':mode="q"
   elif a=="'":mode="h"
   elif a=="/" and b=="/":o[i]=o[i+1]=" ";i+=2;mode="l";continue
   elif a=="/" and b=="*":o[i]=o[i+1]=" ";i+=2;mode="b";continue
  elif mode in ("q","h"):
   if e:e=False
   elif a=="\\":e=True
   elif a==('"' if mode=="q" else "'"):mode="c"
  elif mode=="l":
   if a=="\n":mode="c"
   else:o[i]=" "
  elif mode=="b":
   if a=="*" and b=="/":o[i]=o[i+1]=" ";i+=2;mode="c";continue
   if a!="\n":o[i]=" "
  i+=1
 return "".join(o)
rows=[]
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for path in z.namelist():
  if not(path.startswith("src/") and path.endswith(".java")):continue
  raw=z.read(path).decode("utf8",errors="replace");safe=mask(raw)
  for m in RX.finditer(safe):
   rec=m.group(1);src=m.group(2);st=m.end()-1;depth=0;end=-1
   for k in range(st,min(len(safe),st+25000)):
    if safe[k]=="{":depth+=1
    elif safe[k]=="}":
     depth-=1
     if depth==0:end=k+1;break
   if end<0:continue
   body=safe[m.end():end];text=raw[m.end():end]
   for rx,label in ((SEND,"SC"),(CALL,"SENDPACKETS")):
    for x in rx.finditer(body):
     if x.group(2 if label=="SC" else 1)!=rec:continue
     expr=x.group(3 if label=="SC" else 2)
     if len(expr)>280:continue
     code=text[x.start():x.end()].strip().replace("\n"," ")
     if len(code)>300:code=code[:300]
     personal=bool(re.search(r'\b'+re.escape(rec)+r'\s*\.\s*get',expr))
     rank=(0 if personal else 6)+(4 if ('"' in expr) else 0)+(3 if "shared" not in body else 0)
     line=raw.count("\n",0,m.start())+1
     rows.append((rank,path,line,src,code))
 rows.sort(reverse=True)
 print("TOTAL_LIVE_DIRECT_CALLS_IN_PC_LOOPS",len(rows))
 print("TOP_DIRECT_FILES",collections.Counter(x[1] for x in rows).most_common(60))
 for rank,path,line,source,code in rows[:170]:
  print("DIRECT",rank,path,line,"FROM",source[:72],"CALL",code)
 for short,needles in {
  "CS_Result.java":["SC_SystemMessage.send(","sendPackets(","SC_"],
  "GMCommands.java":["for (L1PcInstance", "SC_SystemMessage.send("],
  "MJRaidMessage.java":["SC_SystemMessage.send("],
  "SkillHandlers.java":["SC_SystemMessage.send("],
  "SC_Pledge.java":["sendBless("],
  "CS_Craft.java":["for (L1PcInstance"],
 }.items():
  paths=[n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]
  for path in paths[:1]:
   lines=z.read(path).decode("utf8",errors="replace").splitlines()
   hits=[i for i,line in enumerate(lines) if any(k in line for k in needles)]
   print("DETAIL",path,"hits",len(hits))
   for i in hits[:6]:
    print("-- line",i+1)
    for j in range(max(0,i-5),min(len(lines),i+8)):print(j+1,lines[j])
