#!/usr/bin/env python3
"""Extract exact phase5 sources required for phase6 immutable display fanout."""
import hashlib,pathlib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase5_FULL_SRC_20261115.zip"
EXPECTED="8bcd787c6b01a19575db42f83c733b47bee573a3"
OUT=ROOT/"reports/broadcast_nonsc_phase6_20261115/FOCUS.txt"
data=SOURCE.read_bytes()
sha=hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
if sha!=EXPECTED:raise AssertionError("phase5 source mismatch")
with zipfile.ZipFile(SOURCE) as z:
    assert z.testzip() is None
    names=z.namelist()
    out=[f"phase5_git_blob={sha}\n"]
    for name in ("SC_DisplayEffect.java","MJBattleTournamentArea.java",
                 "CSharedBroadcast.java","SC_SkillSound.java","L1World.java"):
        found=[p for p in names if p.endswith("/"+name) and p.startswith("src/")]
        if len(found)!=1:
            out.append("MISSING_OR_AMBIGUOUS "+name+" "+repr(found)+"\n")
            continue
        src=found[0]
        lines=z.read(src).decode("utf8").splitlines()
        out.append(f"\n========== {src} ({len(lines)} lines) ==========\n")
        ranges= [(1,min(len(lines),450))] if name.startswith("SC_") or name=="CSharedBroadcast.java" else ([(210,280)] if name=="MJBattleTournamentArea.java" else [(390,440),(750,810)])
        for lo,hi in ranges:
            out.extend(f"{i}: {lines[i-1]}\n" for i in range(lo,min(hi,len(lines))+1))
OUT.parent.mkdir(parents=True,exist_ok=True)
OUT.write_text("".join(out),encoding="utf8")
print("PHASE6_INSPECT_OK",OUT,OUT.stat().st_size)
