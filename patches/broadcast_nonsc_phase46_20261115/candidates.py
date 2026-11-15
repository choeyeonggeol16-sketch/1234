#!/usr/bin/env python3
from pathlib import Path
import zipfile
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase45_FULL_SRC_20261115.zip"
cases={
"SC_MessageYN.java":[""],
"BossController.java":["SC_MessageYN.send(pc, CS_Attr.MSGCODE_622_BOSS"],
"L1HauntedHouse.java":["SC_MessageYN.send(player, 1256"],
"L1Racing.java":["SC_MessageYN.send(player, 1256"],
"L1DeathMatch.java":["SC_MessageYN.send(player, 1268"],
"UserCommands.java":["SC_MessageYN.send(member, 954"],
"CS_Channel.java":["SC_PacketBox.send(member, SC_PacketBox.PLEDGE_EMBLEM_STATUS"],
"SC_CastleMaster.java":[""],
"MJCastleWar.java":["SC_CastleMaster.send(pc"],
"SC_TrueTargetNew.java":[""],
"ItemUseCommandCombat.java":["SC_TrueTargetNew.send(partymember"],
"L1MonsterInstance.java":["SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE"],
}
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for short,patterns in cases.items():
  path=next((n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)),None)
  if path is None:continue
  lines=z.read(path).decode("utf8").splitlines()
  print("==FILE",path,"TOTAL",len(lines))
  seen=set()
  for needle in patterns:
   hits=[0] if needle=="" else [i for i,line in enumerate(lines) if needle in line][:7]
   for i in hits:
    bef=0 if needle=="" else 17
    aft=115 if needle=="" else 25
    print("--",needle,"AT",i+1)
    for j in range(max(0,i-bef),min(len(lines),i+aft)):
     if j not in seen:print(f"{j+1:4} {lines[j]}")
     seen.add(j)
