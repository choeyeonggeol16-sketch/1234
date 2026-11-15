from pathlib import Path
import zipfile
root=Path(__file__).resolve().parents[2]
src=root/"downloads/RKern_Broadcast_NonSC_Phase24_FULL_SRC_20261115.zip"
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 for short in ("BossRaidSystem.java","QueenSystem2.java","SC_ServerMessage.java","L1Clan.java","HadinTrap.java","Queen1.java","Azmodan.java","CS_Channel.java"):
  found=[x for x in z.namelist() if x.startswith("src/") and x.endswith("/"+short)]
  for path in found:
   lines=z.read(path).decode("utf8").splitlines()
   print("======= TARGET",path)
   if short in ("BossRaidSystem.java","QueenSystem2.java","SC_ServerMessage.java"):
    spans=[(0,min(len(lines),250))]
   elif short=="HadinTrap.java":spans=[(140,min(len(lines),235))]
   elif short=="L1Clan.java":
    hits=[i for i,l in enumerate(lines) if "getOnlineClanMember" in l]
    spans=[(max(0,i-5),min(len(lines),i+32)) for i in hits]
   elif short in ("Queen1.java","Azmodan.java"):
    hits=[i for i,l in enumerate(lines) if "SC_SystemMessage.send(member" in l]
    spans=[(max(0,i-14),min(len(lines),i+9)) for i in hits]
   else:spans=[(0,min(130,len(lines)))]
   for start,end in spans:
    for i in range(start,end):print(f"{i+1:4} {lines[i]}")
