#!/usr/bin/env python3
"""Read-only phase80 all-broadcast regression audit with SHA-pinned whole SRC."""
from pathlib import Path
import zipfile,hashlib,json,re,collections
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase80_FULL_SRC_20261116.zip"
PIN="a0d534b066395b12a3a9e545cb094602e1cd568ea80ddca2900ff46a0a54e391"
REPORT=ROOT/"reports/nc_broadcast_full_audit81"
REPORT.mkdir(parents=True,exist_ok=True)
assert hashlib.sha256(SRC.read_bytes()).hexdigest()==PIN
with zipfile.ZipFile(SRC) as z:
    assert z.testzip() is None
    names=z.namelist()
    assert len(names)==1734 and sum(p.endswith(".java") for p in names)==1710
    all_src={name:z.read(name).decode("utf-8",errors="replace") for name in names if name.endswith(".java") or name.endswith(".cpp")}
searches={
  "broadcast_call":r"\b(?:CSharedBroadcast|CAsyncSendConstBuffer(?:ExceptSelf)?)\s*\.\s*(?:send|sendAll|sendRecipients|sendWritten|sendExtended|forWritten|forExtended)\w*\s*\(",
  "direct_source":r"\b(?:openWrittenSource|createPooledWritableConstBuffer|sealPooledWritableConstBuffer|enqueueConstBufferConfirmed|sendFrozenWire|releaseConstBuffer)\s*\(",
  "old_packet_loop":r"\b(?:sendPackets|broadCast|broadcastPacket|broadcastPackets)\s*\(",
  "native_pool":r"\b(?:borrow|release|retain|source|CIOBufferEx)\b",
  "legacy_wire":r"\b(?:sharedWire|sharedDirectAdmissionWire|ByteBuffer\.allocate|Arrays\.copyOf)\s*\("
}
matches={key:[] for key in searches}
for name,src in all_src.items():
    if not (name.startswith("src/") or name.startswith("native/")):continue
    for line,txt in enumerate(src.splitlines(),1):
        for key,pattern in searches.items():
            if re.search(pattern,txt):
                matches[key].append([name,line,txt.strip()[:225]])
summary={
  "baseline_sha256":PIN,
  "zip_entries":len(names),
  "java_count":sum(n.endswith(".java") for n in names),
  "source_count":len(all_src),
  "matches_count":{k:len(v) for k,v in matches.items()},
  "native_abi_expected":"0x000a0009",
}
for key,rows in matches.items():
    (REPORT/(key+".json")).write_text(json.dumps(rows,ensure_ascii=False,indent=2)+"\n")
focus=("CSharedBroadcast.java","CAsyncSendConstBuffer.java","CAsyncSendConstBufferExceptSelf.java",
    "CUserSocket.java","CIOServer.java","CIOConfig.java","CIOBufferPool.java",
    "CIOThreadCallback.java","SC_AreaAttackEffect.java","SC_ChatPacket.java",
    "SC_DropItem.java","SC_UseArrowSkill.java","SC_TopMessage.java",
    "SC_NewChat.java","SC_IconMessage.java","SC_SystemMessage.java",
    "SC_War.java","SC_WorldPutObject.java","SC_PacketBox.java","CTransport.cpp")
for fname in focus:
    cand=[n for n in all_src if n.endswith("/"+fname)]
    if len(cand)==1:(REPORT/fname).write_text(all_src[cand[0]],encoding="utf-8")
    else:summary["not_unique_"+fname]=cand[:8]
(REPORT/"summary.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2)+"\n")
print("AUDIT81_SHA_PINNED_SOURCE_SCANNED",json.dumps(summary),flush=True)
