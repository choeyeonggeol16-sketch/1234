#!/usr/bin/env python3
"""Classify every phase64 SC_*.broadcast method body by actual fanout strategy.
This is a static inventory; never silently equate method naming to legacy code.
"""
from pathlib import Path
import json,zipfile,re,collections,hashlib
ROOT=Path(__file__).resolve().parents[2]
ZIP=ROOT/"downloads/RKern_Broadcast_NonSC_Phase64_FULL_SRC_20261116.zip"
OUT=ROOT/"reports/broadcast_nonsc_phase64_20261116"
# Reuse the comment/string elision from the verified full lineage scanner.
from full_lineage_audit import executable
rows=[]
with zipfile.ZipFile(ZIP) as z:
    files={x.filename:z.read(x).decode("utf-8-sig",errors="replace") for x in z.infolist()
        if x.filename.startswith("src/") and "/network/core/sc/packet/" in x.filename and x.filename.endswith(".java")}
for path,source in sorted(files.items()):
    masked=executable(source)
    for m in re.finditer(r"\b(?:public|protected|private)\s+static\s+(?:final\s+)?(?:boolean|void)\s+(broadcast\w*)\s*\(",masked):
        # Find opening brace of method (after balanced parameter parentheses).
        start=m.end();d=1;pos=start
        while pos<len(masked) and d:
            if masked[pos]=="(":d+=1
            elif masked[pos]==")":d-=1
            pos+=1
        if d:continue
        brace=masked.find("{",pos)
        if brace<0 or ";" in masked[pos:brace]:continue
        d=1;end=brace+1
        while end<len(masked) and d:
            if masked[end]=="{":d+=1
            elif masked[end]=="}":d-=1
            end+=1
        if d:continue
        body=masked[brace:end]
        has_shared=bool(re.search(r"\b(?:CSharedBroadcast|CAsyncSendConstBuffer)\b",body))
        calls_direct=bool(re.search(r"\b(?:sendDirectRaw|SC_\w+\.send|sendPackets)\s*\(",body))
        loops=bool(re.search(r"\b(?:for|while)\s*\(",body))
        creates_packet=bool(re.search(r"\bnew\s+(?:S_|SC_)\w+",body))
        if has_shared and not calls_direct: label="shared"
        elif has_shared and calls_direct: label="mixed"
        elif loops and calls_direct: label="direct-in-loop"
        elif calls_direct: label="direct-no-loop"
        elif creates_packet: label="packet-factory"
        else: label="delegate-or-other"
        rows.append({"class":path.rsplit("/",1)[-1].removesuffix(".java"),
            "path":path,"line":masked.count("\n",0,m.start())+1,
            "method":m.group(1),"classification":label,"loops":loops,
            "direct":calls_direct,"shared":has_shared,"creates_packet":creates_packet})
groups=collections.Counter(r["classification"] for r in rows)
OUT.mkdir(exist_ok=True,parents=True)
(OUT/"SC_BROADCAST_METHODS.json").write_text(json.dumps({"methods":len(rows),"groups":dict(groups),"rows":rows},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
(OUT/"SC_METHOD_REVIEW.md").write_text("# SC broadcast method classification\n\n"+
    "\n".join(f"- {r['classification']}: {r['class']}.{r['method']} {r['path']}:{r['line']}" for r in rows)+"\n",encoding="utf-8")
print("PHASE63_SC_BROADCAST_CLASSIFY",len(rows),dict(groups))
print("DIRECT_CANDIDATES")
for r in rows:
    if r["classification"] in ("direct-in-loop","mixed"):
        print(r["path"]+":"+str(r["line"]),r["method"],r["classification"])

assert not any(r["classification"] in ("direct-in-loop","mixed") for r in rows),"active direct legacy SC broadcaster remains"
