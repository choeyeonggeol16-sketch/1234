#!/usr/bin/env python3
"""Inventory every SC-class broadcast path in the SHA-pinned phase74 archive.
The report includes the exact source for every remaining candidate, not inferred
packet layout; prevents changes to non-broadcast 1:1 SEND methods.
"""
from pathlib import Path
import collections,hashlib,json,re,zipfile
root=Path(__file__).resolve().parents[2]
name="RKern_NC_SPacket_Assemble_Phase74_FULL_SRC_20261116.zip"
p=root/"downloads"/name
sha=hashlib.sha256(p.read_bytes()).hexdigest()
assert sha=="4f5d0f8cdb1850693aa0b36ef1fba5c609292233f6c7558102f047287a24b3d8",sha
out=root/"reports"/"nc_sc_remaining_20261116"
out.mkdir(parents=True,exist_ok=True)
summary={"sha256":sha,"total_SC":0,"with_broadcast":0,"unconverted_SC":0,"remaining_shared_wire":0,"others":[],"candidates":[]}
with zipfile.ZipFile(p) as z:
    assert z.testzip() is None
    for n in z.namelist():
        if not n.endswith(".java") or "/sc/packet/SC_" not in n:continue
        summary["total_SC"]+=1
        raw=z.read(n)
        text=raw.decode("utf-8")
        lines=text.splitlines()
        methods=[]
        for i,line in enumerate(lines):
            if re.search(r'\bbroadcast(?:Fast|Wide|M|\w*)?\s*\(',line):
                methods.append({"line":i+1,"declaration":line.strip()[:220]})
        calls=[{"line":i+1,"source":line.strip()[:220]} for i,line in enumerate(lines)
               if "CSharedBroadcast." in line or ".broadcastPacket" in line
                  or "broadcastPacket(" in line or "sharedWire(" in line]
        if methods or calls: summary["with_broadcast"]+=1
        remaining=any("CSharedBroadcast.send(" in l or "CSharedBroadcast.sendRecipients(" in l
                      or "CSharedBroadcast.sendRaw" in l or "CSharedBroadcast.sendArray(" in l
                      or "CSharedBroadcast.sendObjectPlayers(" in l for l in lines)
        if remaining:
            summary["unconverted_SC"]+=1
            if "sharedWire(" in text:summary["remaining_shared_wire"]+=1
            summary["candidates"].append({"name":Path(n).name,"path":n,"bytes":len(raw),"methods":methods,"calls":calls})
            (out/Path(n).name).write_bytes(raw)
        elif methods or calls:
            summary["others"].append({"name":Path(n).name,"methods":methods,"calls":calls[:10]})
    for path in ("src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java",
        "src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java",
        "src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBufferExceptSelf.java",
        "src/l1j/server/server/model/network/core/transport/CIOServer.java",
        "native/CTransport.cpp","native/interop_abi.hpp",
        "src/l1j/server/server/model/network/core/config/CIOConfig.java"):
        (out/Path(path).name).write_bytes(z.read(path))
(out/"inventory.json").write_text(json.dumps(summary,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
print("SC_INVENTORY",summary["total_SC"],summary["with_broadcast"],summary["unconverted_SC"],"with sharedWire",summary["remaining_shared_wire"])
print("CANDIDATES",",".join(x["name"] for x in summary["candidates"]))
