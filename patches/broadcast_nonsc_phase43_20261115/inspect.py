#!/usr/bin/env python3
from pathlib import Path
import zipfile,re
zip_path=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase42_FULL_SRC_20261115.zip"
tasks={
"MJCastleWar.java":[("private void selectMode()",0,35),("SC_IconMessage.send(pc, 1510",15,12)],
"SC_IconMessage.java":[("public static boolean send(",8,25),("private static byte[] sharedWire(",12,48),("INDEX_MESSAGE",0,10)],
"SC_WarStartMessage.java":[("",0,280)],
"MJRaidObject.java":[("SC_OnlyEffect.send(pc, pc.getId(), 7783)",28,32)],
"SC_PacketBox.java":[("case DRAGONRAID_BUFF:",20,15),("sharedRaid",4,8),("sharedWire",3,15),("DRAGONRAID_BUFF",8,16)],
"MJRaidValakas.java":[("SC_DisplayEffect.send(pc, s_blackDisplay)",18,25),("SC_SkillSound.send(pc, pc.getId(), 15837)",18,20)],
"HomeTownTimeController.java":[("SC_PacketBox.send(pc, SC_PacketBox.MSG_TOWN_LEADER",25,20)],
"SC_OnlyEffect.java":[("sharedWire(",10,26),("broadcast(",3,17)],
"SC_ChangeAttr.java":[("",0,150)],
"MJL1LiftGateInstance.java":[("SC_ChangeAttr.send(pc",20,25)]
}
with zipfile.ZipFile(zip_path) as z:
 assert z.testzip() is None
 for short,patterns in tasks.items():
  paths=[n for n in z.namelist() if n.endswith("/"+short) and n.startswith("src/")]
  for n in paths:
   lines=z.read(n).decode("utf8").splitlines()
   print("======= FILE",n,"TOTAL",len(lines))
   seen=set()
   for needle,bef,aft in patterns:
    matching=([0] if not needle else [i for i,l in enumerate(lines) if needle in l][:3])
    for i in matching:
     print("--- PATTERN",needle,"AT",i+1)
     for k in range(max(0,i-bef),min(len(lines),i+aft)):
      if k not in seen:print(f"{k+1:4} {lines[k]}")
      seen.add(k)
