#!/usr/bin/env python3
"""READ-ONLY audit against exact verified phase31 source zip."""
from pathlib import Path
import re,zipfile,collections,hashlib
src=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase31_FULL_SRC_20261115.zip"
raw=src.read_bytes()
blob=hashlib.sha1(b"blob "+str(len(raw)).encode()+b"\0"+raw).hexdigest()
assert blob=="901075630eb0dcc562b5fe36aa4fd626ff396b68",blob
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 entries=z.namelist();print("JAVA_COUNT",sum(p.endswith(".java") for p in entries))
 focus=("L1World.java","L1Character.java","L1PcInstance.java","L1Party.java","L1Clan.java",
        "L1ClanJoin.java","L1Shop.java","MJRaidMessage.java","L1Racing.java","CSharedBroadcast.java")
 for short in focus:
  matches=[n for n in entries if n.startswith("src/") and n.endswith("/"+short)]
  for path in matches:
   lines=z.read(path).decode("utf8").splitlines()
   print("\n===FOCUS",path,"lines",len(lines))
   rx=re.compile(r"\bbroadcast\w*\s*\(|\bsend\w*\s*\(|\bgetOnlineClanMember\s*\(")
   hits=[i for i,l in enumerate(lines) if rx.search(l)]
   for i in hits[:75]:
    if "sendPackets" in lines[i] and short=="L1PcInstance.java":continue
    if i>0 and lines[i].lstrip().startswith("//"):continue
    print(f"{i+1} {lines[i][:215]}")
 methods=collections.Counter()
 fanouts=[]
 for path in entries:
  if not path.startswith("src/") or not path.endswith(".java"):continue
  lines=z.read(path).decode("utf8",errors="replace").splitlines()
  for i,l in enumerate(lines):
   m=re.search(r"(?:\w+)\.(broadcast\w*)\s*\(",l)
   if m and not l.lstrip().startswith("//"):
    methods[m.group(1)]+=1
   if re.search(r"(SC_\w+\.send\(|pc\.sendPackets\(|member\.sendPackets\()",l):
    # likely fanout only when loop close (last 6 lines) has recipient declaration
    preceding="\n".join(lines[max(0,i-11):i])
    if re.search(r"for\s*\(\s*(?:final\s+)?(?:L1PcInstance|L1Object|var)\s+\w+\s*:",preceding):
     fanouts.append((path,i+1,l.strip()))
 print("\n===BROADCAST_METHOD_COUNTS",methods.most_common(70))
 print("===POTENTIAL_FANOUT_COUNT",len(fanouts))
 byfile=collections.Counter(p for p,_,_ in fanouts)
 print("===POTENTIAL_FANOUT_FILES",byfile.most_common(75))
 print("===PRIORITY_EXAMPLES")
 for p,line,msg in fanouts[:170]:print(p,line,msg[:180])
