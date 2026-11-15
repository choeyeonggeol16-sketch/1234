from pathlib import Path
import zipfile,re
root=Path(__file__).resolve().parents[2]
src=root/"downloads/RKern_Broadcast_NonSC_Phase25_FULL_SRC_20261115.zip"
targets=("ShipTimeController.java","BossController.java","MJRaidValakas.java",
  "MJRaidAntaras.java","MJRaidFafurion.java","MJRaidRindvior.java",
  "L1ClanJoin.java","L1Racing.java","L1DeathMatch.java",
  "DungeonTimeUpdator.java","MJRaidObject.java","MJCastleWar.java",
  "L1HauntedHouse.java","FishingTimeController.java")
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 for short in targets:
  found=[n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]
  for path in found:
   lines=z.read(path).decode("utf8").splitlines()
   hits=[i for i,l in enumerate(lines) if re.search(r"SC_(?:SystemMessage|ServerMessage|PacketBox|SkillSound|DisplayEffect|NpcChatPacket)\.(?:send|broadcast)|broadcastPacket|sendPackets\(",l)]
   print("===",path,len(lines),"hits",len(hits))
   taken=set()
   for i in hits[:65]:
    lo=max(0,i-10);hi=min(len(lines),i+8)
    if all(j in taken for j in range(lo,hi)):continue
    print("---",i+1)
    for j in range(lo,hi):
     if j not in taken:print(f"{j+1:4} {lines[j]}")
     taken.add(j)
