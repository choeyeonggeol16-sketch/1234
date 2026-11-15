#!/usr/bin/env python3
import zipfile
from pathlib import Path
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase46_FULL_SRC_20261115.zip"
cases={
"CS_Channel.java":["SC_PacketBox.send(member, SC_PacketBox.PLEDGE_EMBLEM_STATUS"],
"SC_PacketBox.java":["case PLEDGE_EMBLEM_STATUS:","public static boolean send(L1PcInstance pc, int type, int value)"],
"L1Ress.java":["SC_MessageYN.send(tg, 322"],
"SC_ActionUI2.java":[""],
"CS_Craft.java":["SC_ActionUI2.send(member, 2723"],
"CS_ActionUi.java":["SC_ActionUI.send(member, bytes);"],
"SC_ActionUI.java":[""],
"L1MonsterInstance.java":["SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE"],
"SC_NewChat.java":[""],
"CS_Craft.java":["SC_NewChat.send(listener"],
}
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for short,items in cases.items():
  paths=[n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]
  for path in paths[:1]:
   lines=z.read(path).decode("utf8").splitlines()
   print("===FILE",path)
   seen=set()
   for pat in items:
    matches=[0] if not pat else [i for i,l in enumerate(lines) if pat in l][:5]
    for i in matches:
     print("----",pat,"AT",i+1)
     lo=max(0,i-20);hi=min(len(lines),i+(105 if not pat else 25))
     for j in range(lo,hi):
      if j not in seen:print(f"{j+1:4} {lines[j]}")
      seen.add(j)
