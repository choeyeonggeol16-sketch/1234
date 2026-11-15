#!/usr/bin/env python3
"""Read-only audit of remaining fanout on verified phase23 whole SRC."""
from pathlib import Path
import zipfile,hashlib,re
root=Path(__file__).resolve().parents[2]
src=root/"downloads/RKern_Broadcast_NonSC_Phase23_FULL_SRC_20261115.zip"
raw=src.read_bytes()
sha=hashlib.sha1(b"blob "+str(len(raw)).encode()+b"\0"+raw).hexdigest()
assert sha=="6ae749fe9a0b961ed12d6d41e3b36538aba9bee7",sha
targets=("SC_SkillSound.java","MJL1TreasureChest.java","L1Character.java",
         "MJLFCObject.java","MJItemSkillModel.java","L1World.java",
         "MJL1LiftGateInstance.java","SC_PacketBox.java","LFCMessages.java")
with zipfile.ZipFile(src) as z:
    assert z.testzip() is None
    names=z.namelist()
    for short in targets:
        paths=[n for n in names if n.startswith("src/") and n.endswith("/"+short)]
        print("\n==========",short,paths,"==========")
        for path in paths[:1]:
            lines=z.read(path).decode("utf8").splitlines()
            if short in ("SC_SkillSound.java","MJL1TreasureChest.java","MJLFCObject.java","MJItemSkillModel.java","MJL1LiftGateInstance.java"):
                for i,line in enumerate(lines,1):
                    print(f"{i:4} {line}")
            else:
                hits=[i for i,line in enumerate(lines) if re.search(r"broadcastPacket|broadcast_map|sendPackets\(|sharedWire|sendRecipients|sendArray|sendGroups",line)]
                spans=[]
                for i in hits:
                    lo=max(0,i-5);hi=min(len(lines),i+10)
                    if spans and lo<=spans[-1][1]:spans[-1]=(spans[-1][0],max(spans[-1][1],hi))
                    else:spans.append((lo,hi))
                for lo,hi in spans[:18]:
                    for i in range(lo,hi):print(f"{i+1:4} {lines[i]}")
    print("\nTOTAL JAVA",sum(n.endswith(".java") for n in names))
