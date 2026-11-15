#!/usr/bin/env python3
"""Scan the pinned phase23 full SRC for remaining live per-recipient sends."""
from pathlib import Path
import zipfile,re,collections
root=Path(__file__).resolve().parents[2]
src=root/"downloads/RKern_Broadcast_NonSC_Phase23_FULL_SRC_20261115.zip"
candidates=[]
patterns=(re.compile(r"\b(?:SC_[A-Za-z0-9_]+)\.send\("),
          re.compile(r"\bpc\.sendPackets\("),
          re.compile(r"\b(?:SC_[A-Za-z0-9_]+)\.broadcast\("),
          re.compile(r"\b(?:SC_[A-Za-z0-9_]+)\.sendAndBroadcast\("))
with zipfile.ZipFile(src) as z:
    assert z.testzip() is None
    for name in z.namelist():
        if not (name.startswith("src/") and name.endswith(".java")):continue
        data=z.read(name).decode("utf8",errors="replace")
        lines=data.splitlines()
        for k,l in enumerate(lines):
            if not any(p.search(l) for p in patterns):continue
            lookback="\n".join(lines[max(0,k-15):k])
            if not any(word in lookback for word in ("for (","for(","while (","while(")):continue
            if "sendPackets" in l and "CSharedBroadcast" in name:continue
            candidates.append((name,k+1,l.strip(),lookback[-350:]))
print("POTENTIAL LOOP SENDS",len(candidates))
counts=collections.Counter(x[0] for x in candidates)
print("TOP FILES",counts.most_common(95))
for name,line,l,prev in candidates[:240]:
    print(f"\n### {name}:{line}\nPREV {prev}\nSEND {l}")
print("TOTAL_JAVA",sum(1 for n in z.namelist() if n.endswith(".java")))
