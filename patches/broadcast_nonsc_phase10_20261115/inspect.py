#!/usr/bin/env python3
"""Extract active non-SC generic broadcast paths from phase9 pinned ZIP."""
import pathlib,hashlib,zipfile,re
root=pathlib.Path(__file__).resolve().parents[2]
file=root/"downloads/RKern_Broadcast_NonSC_Phase9_FULL_SRC_20261115.zip"
buf=file.read_bytes()
sha=hashlib.sha1(b"blob "+str(len(buf)).encode()+b"\0"+buf).hexdigest()
assert sha=="41714f4421c439aecb00f941abd7bc1e6b94ca0b",sha
focus=[
"MJLFCObject.java","SC_PacketBox.java","MJItemSkillModel.java",
"L1Character.java","SC_SystemMessage.java","MJL1TreasureChest.java",
"MJL1LiftGateInstance.java","CS_Chat.java","SC_NpcChatPacket.java"]
result=["phase9_git_blob="+sha+"\n"]
with zipfile.ZipFile(file) as z:
 assert z.testzip() is None
 for name in focus:
  files=[n for n in z.namelist() if n.startswith("src/") and n.endswith("/"+name)]
  if len(files)!=1:
   result.append("AMBIGUOUS "+name+" "+repr(files)+"\n");continue
  filename=files[0]
  lines=z.read(filename).decode("utf8").splitlines()
  result.append(f"\n========== {filename} ({len(lines)} lines) ==========\n")
  if name=="SC_SystemMessage.java": spans=[(1,len(lines))]
  elif name=="MJItemSkillModel.java": spans=[(170,215)]
  elif name=="L1Character.java": spans=[(335,420)]
  elif name=="MJL1TreasureChest.java": spans=[(159,199)]
  elif name=="MJL1LiftGateInstance.java": spans=[(160,245)]
  else:
   hits=[i for i,l in enumerate(lines) if any(v in l for v in
     ("broadcastPacket(", "broadcastGreen(", "broadcastPacketToAll(", "broadcastPacketM(", "broadcastPacketFast(",
      "SC_PacketBox.GREEN_MESSAGE","SC_PacketBox.MAP_TIMER", "sendGreen", "sendTo","broadcast("))]
   spans=[]
   for i in hits:
    lo=max(1,i-10);hi=min(len(lines),i+15)
    if spans and lo<=spans[-1][1]+2:spans[-1]=(spans[-1][0],max(hi,spans[-1][1]))
    else:spans.append((lo,hi))
  for lo,hi in spans[:28]:
   result.extend(f"{i}: {lines[i-1]}\n" for i in range(lo,min(hi,len(lines))+1))
dest=root/"reports/broadcast_nonsc_phase10_20261115/FOCUS.txt"
dest.parent.mkdir(parents=True,exist_ok=True)
dest.write_text("".join(result),encoding="utf8")
print("PHASE10_FOCUS_OK",len(buf),dest.stat().st_size)
