#!/usr/bin/env python3
import zipfile
from pathlib import Path
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase48_FULL_SRC_20261115.zip"
with zipfile.ZipFile(p) as z:
 for short,needles in {
  "WriteBuffer.java":["void write7B(","public void write7B(","public final void write7B("],
  "CS_Craft.java":["SC_ActionUI2.send(member, 2723","for (L1PcInstance member : clan.getOnlineClanMember())"],
  "SC_ActionUI2.java":["public static boolean send(L1PcInstance pc, int i, int t, int o, int gf, int ms)"],
  "L1PcInstance.java":["SC_SkillSound.send(partymember","SC_PacketBox.send(this, SC_PacketBox.GREEN_MESSAGE"],
  "CS_Craft.java":["SC_NewChat.send(listener,"],
  "L1Racing.java":["SC_GameRap.send(pc,"],
  "SC_GameRap.java":[""]
 }.items():
  paths=[n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+short)]
  for path in paths[:1]:
   lines=z.read(path).decode("utf8").splitlines()
   hits=[i for i,l in enumerate(lines) if any(v in l for v in needles)]
   print("===FILE",path,"HITS",len(hits))
   seen=set()
   for i in (hits[:6] if hits else [0]):
    lo=max(0,i-14);hi=min(len(lines),i+(70 if short in ("WriteBuffer.java","SC_ActionUI2.java","SC_GameRap.java") else 25))
    print("--",i+1)
    for j in range(lo,hi):
     if j not in seen:print(f"{j+1:4} {lines[j]}")
     seen.add(j)
