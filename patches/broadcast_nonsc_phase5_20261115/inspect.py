#!/usr/bin/env python3
"""Read-only extraction of candidate broadcasts from verified phase4 ZIP."""
import hashlib,pathlib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase4_FULL_SRC_20261115.zip"
EXPECTED_BLOB="47588e2fbef3990f042080efa9b4eabe090c93ae"
OUT=ROOT/"reports/broadcast_nonsc_phase5_20261115/FOCUS.txt"
SELECT={
  "CS_Chat.java": [(1,180)],
  "CS_Craft.java": [(750,870)],
  "SC_NewChat.java": [(1,400)],
  "SC_PacketBox.java": [(1,220)],
  "MJBattleTournamentArea.java": [(185,270)],
  "MJLFCObject.java": [(175,280)],
  "BossController.java": [(245,315),(455,520)],
  "CSharedBroadcast.java": [(1,220)],
  "SC_SystemMessage.java": [(1,130)],
}
data=SOURCE.read_bytes()
sha=hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
if sha!=EXPECTED_BLOB:raise AssertionError("Source ZIP is not verified phase4")
with zipfile.ZipFile(SOURCE) as z:
    assert z.testzip() is None
    names=z.namelist()
    pieces=[f"Git blob: {sha}\nSHA256: {hashlib.sha256(data).hexdigest()}\n"]
    for name,ranges in SELECT.items():
        files=[p for p in names if p.endswith("/"+name) and p.startswith("src/")]
        if len(files)!=1:
            pieces.append(f"\nMISSING_OR_AMBIGUOUS {name}: {files}\n")
            continue
        path=files[0]
        lines=z.read(path).decode("utf8").splitlines()
        pieces.append(f"\n========== {path} ({len(lines)} lines) ==========\n")
        for lo,hi in ranges:
            pieces.extend(f"{i}: {lines[i-1]}\n" for i in range(max(1,lo),min(hi,len(lines))+1))
OUT.parent.mkdir(parents=True,exist_ok=True)
OUT.write_text("".join(pieces),encoding="utf8")
print("PHASE5_INSPECT_OK",OUT,OUT.stat().st_size)
