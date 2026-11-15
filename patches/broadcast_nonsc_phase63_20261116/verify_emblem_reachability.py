#!/usr/bin/env python3
"""Verify remaining SC_ReturnedStat.broadcastEmblem reachability on pinned
phase62 (production ZIP only, not historical loose source mirrors).
"""
from pathlib import Path
import zipfile,re,json
ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase62_FULL_SRC_20261116.zip"
OUT=ROOT/"reports/broadcast_nonsc_phase63_20261116"
with zipfile.ZipFile(SOURCE) as z:
    data={x.filename:z.read(x).decode("utf-8-sig",errors="replace") for x in z.infolist()
        if x.filename.startswith("src/") and x.filename.endswith(".java")}
name="src/l1j/server/server/model/network/core/sc/packet/SC_ReturnedStat.java"
s=data[name]
start=s.index("public static void broadcastEmblem(")
snippet=s[start:s.index("\n    }\n",start)+6]
result=[]
for path,body in data.items():
    for match in re.finditer(r"\b(?:SC_ReturnedStat|ReturnedStat)\s*\.\s*broadcastEmblem\s*\(",body):
        line=body.count("\n",0,match.start())+1
        result.append({"path":path,"line":line,"source":body.splitlines()[line-1].strip()[:200]})
report={"emblem_source_path":name,"definition":snippet,"nonself_calls":result,"nonself_call_count":len(result),
 "game_source_files":len(data)}
OUT.mkdir(exist_ok=True,parents=True)
(OUT/"EMBLEM_REACHABILITY.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print("PHASE63_EMBLEM_REACHABILITY",json.dumps(report,ensure_ascii=False))
