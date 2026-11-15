from pathlib import Path
import zipfile,re,collections
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase40_FULL_SRC_20261115.zip"
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for n in z.namelist():
  if not n.endswith(".java") or not n.startswith("src/"):continue
  lines=z.read(n).decode("utf8").splitlines()
  hits=[]
  for i,l in enumerate(lines):
   if re.search(r'\.broadcastPacket\s*\(|\.broadcastPacketToAll\s*\(|\.broadcastPacketExceptTarget\s*\(|\.broadcastPacketForFindInvis\s*\(|\.broadcastPacketForFindHidden\s*\(|\.broadcastPacketForFindInvis\s*\(|\.broadcastPacketToTarget\s*\(',l):
    hits.append(i)
  if hits:
   print("===",n,len(hits))
   for i in hits[:18]:
    print("---",i+1)
    for j in range(max(0,i-4),min(len(lines),i+5)):print(f"{j+1:4} {lines[j]}")

with zipfile.ZipFile(p) as z:
 for suffix in ("SC_SkillSound.java","L1Character.java","L1World.java","CSharedBroadcast.java","DragonPearl.java"):
  for n in z.namelist():
   if n.endswith("/"+suffix):
    print("===== TARGET",n)
    lines=z.read(n).decode("utf8").splitlines()
    if suffix in ("SC_SkillSound.java","DragonPearl.java"): spans=[(0,len(lines))]
    else:
     hits=[i for i,l in enumerate(lines) if "broadcastPacket(" in l or "broadcastPacketToAll(" in l or "sendAround" in l or "sendSight" in l]
     spans=[(max(0,i-3),min(len(lines),i+10)) for i in hits[:12]]
    printed=set()
    for a,b in spans:
     for i in range(a,b):
      if i not in printed:print(i+1,lines[i]);printed.add(i)
