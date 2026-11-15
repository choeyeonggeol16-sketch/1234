#!/usr/bin/env python3
from pathlib import Path
import zipfile
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase44_FULL_SRC_20261115.zip"
cases={
"HomeTownTimeController.java":["SC_PacketBox.send(pc, SC_PacketBox.MSG_TOWN_LEADER"],
"SC_PacketBox.java":["case MSG_TOWN_LEADER:","case PLEDGE_EMBLEM_STATUS:","case BUFFICON:","PLEDGE_EMBLEM_STATUS =","BUFFICON ="],
"ItemUseClan.java":["SC_PacketBox.send(member, SC_PacketBox.GREEN_MESSAGE","SC_SystemMessage.send(member, notice)"],
"ItemUseConsignment.java":["for (L1PcInstance pc1 :","SC_SystemMessage.send(pc1,"],
"L1PcInstance.java":["SC_SkillSound.send(partymember, getId(), 14446);"],
"GMCommands.java":["SC_ServerMessage.send(clanMembers, 94"],
"CS_RankControl.java":["SC_SystemMessage.send(member, target.getName()"],
"ItemUseConsumableRewards.java":["SC_PacketBox.send(member, SC_PacketBox.GREEN_MESSAGE"],
"L1BuffUtil.java":["SC_PacketBox.send(target, SC_PacketBox.BUFFICON"],
"CS_Channel.java":["SC_PacketBox.send(member, SC_PacketBox.PLEDGE_EMBLEM_STATUS"],
"SC_MessageYN.java":["sharedWire("],
"SC_OnlyEffect.java":["broadcast("],
"ItemUseCommandCombat.java":["SC_SystemMessage.send(partymember,"]
}
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for short,patterns in cases.items():
  paths=[n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]
  for path in paths[:1]:
   lines=z.read(path).decode("utf8").splitlines()
   hits=[i for i,l in enumerate(lines) if any(t in l for t in patterns)]
   print("===FILE",path,"MATCHES",len(hits))
   seen=set()
   for i in hits[:12]:
    lo=max(0,i-16);hi=min(len(lines),i+14)
    print("--- AT",i+1)
    for j in range(lo,hi):
     if j not in seen:print(f"{j+1:4} {lines[j]}")
     seen.add(j)

with zipfile.ZipFile(p) as z:
 for short,terms in {
  "CItemUseClan.java":["SC_PacketBox.send(member,","SC_SystemMessage.send(member,notice)","SC_SystemMessage.send(member, notice)"],
  "SC_PacketBox.java":["case 13:","case 6:","case 1:"],
  "CS_Channel.java":["SC_PacketBox.send(member, SC_PacketBox.PLEDGE_EMBLEM_STATUS"]
 }.items():
  path=next((n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)),None)
  if path is None:continue
  lines=z.read(path).decode("utf8").splitlines()
  print("===EXTRA",path)
  hits=[i for i,line in enumerate(lines) if any(x in line for x in terms)]
  for i in hits[:8]:
   print("--- AT",i+1)
   for j in range(max(0,i-20),min(len(lines),i+18)):print(f"{j+1:4} {lines[j]}")
