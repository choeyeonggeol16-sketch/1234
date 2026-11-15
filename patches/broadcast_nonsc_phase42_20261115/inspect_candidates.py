#!/usr/bin/env python3
from pathlib import Path
import zipfile,re
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase41_FULL_SRC_20261115.zip"
targets={
"BugRaceController.java":["SC_SystemMessage.send(player, text);"],
"FishingTimeController.java":["SC_ServerMessage.send(pc, 1136);"],
"MJRaidMessage.java":["SC_SystemMessage.send(pc, sb.toString());","SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, sb.toString());"],
"AcceptorToNpcListenerAdapter.java":["SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, message);"],
"MJCastleWar.java":["SC_ServerMessage.send(pc, 643);","SC_IconMessage.send(pc, 1510"],
"MJRaidObject.java":["SC_OnlyEffect.send(pc, pc.getId(), 7783);","SC_PacketBox.send(pc, SC_PacketBox.DRAGONRAID_BUFF"],
"BossController.java":["SC_DisplayEffect.send(pc, display_effect);"],
"L1MonsterInstance.java":["SC_SystemMessage.send("],
"L1Racing.java":["SC_"],
"SpecialEventHandler.java":["SC_"],
"SC_SystemMessage.java":["sharedWire("],
"SC_ServerMessage.java":["sharedWire("],
"SC_DisplayEffect.java":["sharedWire("],
}
with zipfile.ZipFile(p) as z:
 for short,patterns in targets.items():
  paths=[n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]
  for n in paths:
   lines=z.read(n).decode("utf8").splitlines()
   hits=[i for i,l in enumerate(lines) if any(t in l for t in patterns)]
   print("### FILE",n,"HITS",len(hits),"LEN",len(lines))
   blocks=[]
   for i in hits[:12]:
    lo=max(0,i-17);hi=min(len(lines),i+17)
    if blocks and lo<=blocks[-1][1]:blocks[-1]=(blocks[-1][0],max(blocks[-1][1],hi))
    else:blocks.append((lo,hi))
   for lo,hi in blocks[:8]:
    print("---")
    for i in range(lo,hi):print(f"{i+1:4}: {lines[i]}")
