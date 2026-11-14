#!/usr/bin/env python3
import pathlib,zipfile,hashlib,re
root=pathlib.Path(__file__).resolve().parents[2]
p=root/"downloads/RKern_Broadcast_NonSC_Phase6_FULL_SRC_20261115.zip"
data=p.read_bytes()
sha=hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
assert sha=="2a759d2fc875bd7141246958e0c267ac2660a0eb",sha
names=["L1GroundInventory.java","L1EffectInstance.java","MJL1TreasureChest.java","SC_RemoveObject.java","L1NpcInstance.java","L1Teleport.java"]
out=["phase6_blob="+sha+"\n"]
with zipfile.ZipFile(p) as z:
 assert z.testzip() is None
 for name in names:
  fs=[x for x in z.namelist() if x.startswith("src/") and x.endswith("/"+name)]
  assert len(fs)==1,(name,fs)
  source=fs[0]; lines=z.read(source).decode("utf-8").splitlines()
  out.append("\n==== "+source+" ====\n")
  if name=="SC_RemoveObject.java": blocks=[(1,len(lines))]
  else:
   hits=[i for i,s in enumerate(lines) if any(x in s for x in ("getVisiblePlayer","removeKnownObject","SC_RemoveObject.send","SC_RemoveObject.broadcast","broadcastPacket"))]
   spans=[]
   for i in hits:
    lo=max(1,i-22);hi=min(len(lines),i+35)
    if spans and lo<=spans[-1][1]+1:spans[-1]=(spans[-1][0],max(hi,spans[-1][1]))
    else:spans.append((lo,hi))
   blocks=spans[:18]
  for lo,hi in blocks:
   out.extend(f"{i}: {lines[i-1]}\n" for i in range(lo,hi+1))
dst=root/"reports/broadcast_nonsc_phase7_20261115/FOCUS.txt"
dst.parent.mkdir(parents=True,exist_ok=True)
dst.write_text("".join(out),encoding="utf-8")
print("PHASE7_INSPECT_OK",len(data),dst.stat().st_size)
