from pathlib import Path
import zipfile
p=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase29_FULL_SRC_20261115.zip"
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for n in z.namelist():
  if not n.endswith(("SC_NpcChatPacket.java","L1HauntedHouse.java","CSharedBroadcast.java")):continue
  lines=z.read(n).decode("utf8").splitlines()
  print("============== FILE",n,"TOTAL",len(lines))
  if n.endswith("SC_NpcChatPacket.java"):
   spans=[(0,len(lines))]
  elif n.endswith("L1HauntedHouse.java"):
   spans=[(212,265)]
  else:
   spans=[(0,65),(188,230)]
  for a,b in spans:
   for i in range(a,min(len(lines),b)):
    print(i+1,lines[i])
