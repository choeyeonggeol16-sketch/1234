#!/usr/bin/env python3
"""Inventory every SC packet and every broadcast entry point from pinned phase74.
Read-only audit. No regex-based blind rewrite of packet behavior.
"""
import collections,hashlib,json,pathlib,re,sys,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
ZIP=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase74_FULL_SRC_20261116.zip"
PIN="4f5d0f8cdb1850693aa0b36ef1fba5c609292233f6c7558102f047287a24b3d8"
assert hashlib.sha256(ZIP.read_bytes()).hexdigest()==PIN
with zipfile.ZipFile(ZIP) as z:
    assert z.testzip() is None
    sources={n:z.read(n).decode("utf-8-sig",errors="replace") for n in z.namelist() if n.endswith(".java")}
rows=[]
for name,src in sorted(sources.items()):
    if not pathlib.PurePosixPath(name).name.startswith("SC_"):continue
    direct=len(re.findall(r"\\bbroadcast(?:Fast)?\\s*\\(",src))
    shared=len(re.findall(r"CSharedBroadcast\\.(?:send|sendFormatted|sendAction|sendMove)\\s*\\(",src))
    formatted=len(re.findall(r"CSharedBroadcast\\.sendFormatted\\s*\\(",src))
    legacy=len(re.findall(r"\\b(?:broadcastPacket|broadcastPacketForFindInvis|broadcastPacketAll|sendPacketsToAll)\\s*\\(",src))
    bytewire=len(re.findall(r"\\bsharedWire\\s*\\(",src))
    bytebuffer=len(re.findall(r"ByteBuffer\\.allocate\\s*\\(",src))
    class_name=pathlib.PurePosixPath(name).name
    rows.append(dict(name=class_name,path=name,broadcast_methods=direct,shared_calls=shared,formatted_calls=formatted,legacy_calls=legacy,sharedWire_calls=bytewire,byteBuffer_allocations=bytebuffer,lines=src.count("\\n")+1))
counts=collections.Counter()
for row in rows:
    if row["broadcast_methods"] or row["shared_calls"] or row["legacy_calls"]:counts["broadcast_related"]+=1
    if row["shared_calls"]:counts["uses_shared_broadcast"]+=1
    if row["formatted_calls"]:counts["uses_native_formatted"]+=1
    if row["sharedWire_calls"]:counts["has_shared_wire"]+=1
    if row["legacy_calls"]:counts["legacy_calls"]+=1
    if not row["broadcast_methods"] and not row["shared_calls"] and not row["legacy_calls"]:counts["unicast_or_other"]+=1
report={"baseline_sha256":PIN,"all_sc_files":len(rows),"counts":dict(counts),"rows":rows}
out=ROOT/"patches/nc_assemble_broadcast_phase75_20261116/inventory.json"
out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
print("PHASE75_SC_INVENTORY",json.dumps({"all_sc_files":len(rows),"counts":dict(counts)},ensure_ascii=False))
