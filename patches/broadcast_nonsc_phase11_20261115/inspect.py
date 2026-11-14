#!/usr/bin/env python3
"""Read-only phase11 focus audit from the pinned phase10 source ZIP."""
from pathlib import Path
import zipfile,re,hashlib
root=Path(__file__).resolve().parents[2]
src=root/"downloads/RKern_Broadcast_NonSC_Phase10_FULL_SRC_20261115.zip"
b=src.read_bytes()
sha=hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
assert sha=="508c8343ba1f959861690b922f28d25c9c16afa7",sha
targets={
"LFCMessages.java":None,
"SpecialEventHandler.java":None,
"MJL1LiftGateInstance.java":None,
"MJBattleTournamentArea.java":None,
"MJItemSkillModel.java":None,
"MJL1TreasureChest.java":None,
"SC_NpcChatPacket.java":None,
"SC_PacketBox.java":None,
"MJLFCObject.java":None,
"L1World.java":None,
}
pat=("broadcastPacket","sendGreenMsgToList","broadcast(","sendPackets(","SC_SkillSound.broadcast","L1World.getInstance().get")
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 names=z.namelist()
 for short in targets:
  found=[n for n in names if n.startswith("src/") and n.endswith("/"+short)]
  print("\n==========",short,found,"==========")
  if len(found)!=1:continue
  lines=z.read(found[0]).decode("utf8").splitlines()
  if short in ("LFCMessages.java","SpecialEventHandler.java","MJL1LiftGateInstance.java","MJBattleTournamentArea.java","MJItemSkillModel.java","SC_NpcChatPacket.java"):
   spans=[(1,min(len(lines),350))]
  elif short in ("L1World.java","SC_PacketBox.java","MJLFCObject.java","MJL1TreasureChest.java"):
   hits=[i for i,line in enumerate(lines) if any(key in line for key in pat)]
   spans=[]
   for i in hits:
    lo=max(1,i-8);hi=min(len(lines),i+17)
    if spans and lo<=spans[-1][1]+2:spans[-1]=(spans[-1][0],max(hi,spans[-1][1]))
    else:spans.append((lo,hi))
  else:spans=[]
  for lo,hi in spans[:24]:
   for i in range(lo,hi+1):
    print(f"{i:4} {lines[i-1]}")
