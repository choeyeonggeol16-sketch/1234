#!/usr/bin/env python3
import pathlib,zipfile,hashlib,re,json,collections
ROOT=pathlib.Path(__file__).resolve().parents[2]
ZIP=ROOT/"downloads/RKern_Broadcast_FullAudit_Central2_FULL_SRC_20261115.zip"
EXPECTED="32ca38db2dbe46f0fb58dd2d064c15c124b7069acc8d19e008a5b312704a652b"
assert hashlib.sha256(ZIP.read_bytes()).hexdigest()==EXPECTED
interesting={
"SpecialEventHandler.java": ("class ","doGiveEventStaff","getAllPlayers","sendPackets"),
"MJL1LiftGateInstance.java": ("broadcast(", "notifyChanged(", "notifyDispose(", "sendPackets("),
"MJL1TreasureChest.java": ("showCloud(", "showOpen(", "remove(", "sendPackets("),
"L1Character.java": ("broadcastPacket(", "broadcastPacketM(", "broadcastPacketFast(", "wideBroadcastPacket("),
"L1World.java": ("broadcast_map(", "broadcastPacketToAll("),
"L1MonsterInstance.java": ("BossCoinGet(", "BossCoinGet1(", "SC_SkillSound.broadcast("),
"L1WeaponSkill.java": ("broadcastPacket(", "sendPackets(", "getVisiblePlayer("),
"SC_SkillSound.java": ("broadcast(", "send(", "sharedWire("),
"SC_SystemMessage.java": ("broadcastRecipients(", "sharedWire(", "broadcast("),
"BytesWritablePacket.java": ("class BytesWritablePacket",),
"Proto.java": ("sendRaw(",),
}
out=[]
with zipfile.ZipFile(ZIP) as z:
 paths=[p for p in z.namelist() if p.endswith('.java') and p.startswith("src/")]
 for short,match in interesting.items():
  found=[p for p in paths if p.endswith('/'+short)]
  if len(found)!=1:raise ValueError((short,found))
  p=found[0];data=z.read(p).decode("utf8");rows=data.splitlines()
  positions=set()
  for i,line in enumerate(rows):
   if any(x in line for x in match):
    for j in range(max(0,i-7),min(len(rows),i+30)):positions.add(j)
  if short in ("SpecialEventHandler.java","MJL1LiftGateInstance.java","SC_SkillSound.java"):positions=set(range(min(len(rows),510)))
  if short=="L1World.java":positions={i for i in positions if 330<=i<=810}
  if short=="L1Character.java":positions={i for i in positions if 300<=i<=490}
  if short=="L1MonsterInstance.java":positions={i for i in positions if 560<=i<=680}
  if short=="L1WeaponSkill.java":positions=set(sorted(positions)[:300])
  out+=["="*70,"FILE "+p+" ("+str(len(rows))+" lines)"]
  last=-2
  for i in sorted(positions):
   if i!=last+1:out.append("...")
   out.append(str(i+1)+": "+rows[i])
   last=i
from pathlib import Path
dest=ROOT/"reports/broadcast_nonsc_phase2_20261115/SOURCES.txt"
dest.parent.mkdir(parents=True,exist_ok=True)
dest.write_text("\n".join(out)+"\n")
print("CAPTURED",len(out),"LINES")
