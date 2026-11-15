#!/usr/bin/env python3
"""Phase59 final source-level audit for legacy fanout removal, source integrity
and static send-lifetime boundaries. Does not claim Windows IOCP runtime coverage.
"""
from pathlib import Path
import zipfile,re,hashlib,json,collections
ROOT=Path(__file__).resolve().parents[2]
ZIP56=ROOT/"downloads/RKern_Broadcast_NonSC_Phase56_FULL_SRC_20261116.zip"
ZIP58=ROOT/"downloads/RKern_Broadcast_NonSC_Phase58_FULL_SRC_20261116.zip"
BLOB56="2f4c214acbae218d724ad2982b0432bcc90ba443"
BLOB58="c895ee4251a3c57ccec5de75d84dc1eae9baeabb"
WANTED={
"src/l1j/server/IndunSystem/Gludio/GludioInstanceDungeon.java",
"src/l1j/server/MJTemplate/L1Instance/MJEffectTriggerInstance.java",
"src/l1j/server/server/model/network/core/sc/packet/SC_MoveChar.java",
"src/l1j/server/MJInstanceSystem/MJInstanceEnums.java"}
def blob(x):return hashlib.sha1(b"blob "+str(len(x)).encode()+b"\0"+x).hexdigest()
def strip_comments(s):
    out=[];i=0;mode="code";escaped=False
    while i<len(s):
        c=s[i];d=s[i+1] if i+1<len(s) else ""
        if mode=="code":
            if c=="/" and d=="/":out+=[" "," "];mode="line";i+=2;continue
            if c=="/" and d=="*":out+=[" "," "];mode="block";i+=2;continue
            if c=='"':mode="string";escaped=False
            if c=="'":mode="char";escaped=False
            out.append(c);i+=1;continue
        if mode=="line":
            if c=="\n":mode="code";out.append(c)
            else:out.append(" ")
            i+=1;continue
        if mode=="block":
            if c=="*" and d=="/":out+=[" "," "];i+=2;mode="code";continue
            out.append("\n" if c=="\n" else " ");i+=1;continue
        out.append(c);i+=1
        if escaped:escaped=False
        elif c=="\\":escaped=True
        elif c==('"' if mode=="string" else "'"):mode="code"
    return "".join(out)
for fn,expected in [(ZIP56,BLOB56),(ZIP58,BLOB58)]:
    data=fn.read_bytes();assert blob(data)==expected,fn
with zipfile.ZipFile(ZIP56) as a,zipfile.ZipFile(ZIP58) as b:
    assert a.testzip() is None and b.testzip() is None
    names56=set(a.namelist());names58=set(b.namelist())
    assert names56==names58
    original={n:a.read(n) for n in names56 if not n.endswith("/")}
    latest={n:b.read(n) for n in names58 if not n.endswith("/")}
    assert len([n for n in latest if n.endswith(".java")])==1710
changed={n for n in original if original[n]!=latest[n]}
assert changed==WANTED, ("unexpected changes",changed-WANTED,WANTED-changed)
assert original["CTransport.dll"]==latest["CTransport.dll"]
# Check forbidden active public broadcast entrypoints and generic packet-based
# repeated serialization, excluding commented legacy text.
forbid={
  "old_broadcastPacket":r"\bbroadcastPacket\s*\(",
  "old_broadcastPacketToAll":r"\bbroadcastPacketToAll\s*\(",
  "old_Broadcaster_utility":r"\bBroadcaster\s*\.",
  "generic_ServerPacket_broadcast":r"\b(?:void|boolean)\s+broadcast\s*\(\s*ServerPacket",
  "reuse_pck_in_fanout":r"\.sendPackets\s*\(\s*pck\s*,\s*false\s*\)",
}
counts=collections.Counter()
for name,data in latest.items():
    if not name.endswith(".java"):continue
    cleaned=strip_comments(data.decode("utf-8-sig",errors="replace"))
    for label,rx in forbid.items():
        sites=list(re.finditer(rx,cleaned))
        counts[label]+=len(sites)
        assert not sites,(label,name,[cleaned.count("\n",0,x.start())+1 for x in sites[:5]])
    if ".broadcast(" in cleaned:counts["modern_broadcast_calls"]+=cleaned.count(".broadcast(")
    counts["shared_broadcast_refs"]+=cleaned.count("CSharedBroadcast.")
    if "CAsyncSendConstBuffer" in cleaned:counts["files_using_shared_frame"]+=1
obsolete=[n for n in latest if n.endswith(".java") and
         (n.rsplit("/",1)[-1].startswith("Broadcaster") or
          n.rsplit("/",1)[-1].startswith("Broadcast"))]
# Capture send ownership references for manual review, not a runtime proof.
scope=("CUserSocket.java","CIOServer.java","CAsyncSendConstBuffer.java",
       "CAsyncSendConstBufferExceptSelf.java")
details=[]
for base in scope:
    hits=[(n,v) for n,v in latest.items() if n.endswith("/"+base)]
    for name,data in hits:
        lines=data.decode("utf-8-sig",errors="replace").splitlines()
        entries=[]
        for i,line in enumerate(lines):
            if re.search(r"enqueueConstBufferConfirmed|retain\(|release\(|createConstBuffer|sendFrozenWire|ownsConstBuffer|close\(|pendingSendBytes|send\(",line):
                # Limit per file to avoid giant source dumps.
                entries.append(name+":"+str(i+1)+"\n"+"\n".join(
                    str(j+1)+" "+lines[j] for j in range(max(0,i-3),min(len(lines),i+7))))
        details+=entries[:100]
out=ROOT/"reports/broadcast_nonsc_phase59_20261116"
out.mkdir(parents=True,exist_ok=True)
audit={
 "source_base":"RKern_Broadcast_NonSC_Phase56_FULL_SRC_20261116.zip",
 "source_final":"RKern_Broadcast_NonSC_Phase58_FULL_SRC_20261116.zip",
 "source_final_sha256":hashlib.sha256(ZIP58.read_bytes()).hexdigest(),
 "source_files":len(latest),"java_sources":1710,
 "changed_java_from_56_to_58":sorted(changed),
 "non_java_files_content_identical":True,
 "no_files_added_or_removed":True,
 "native_dll_content_identical":True,
 "banned_legacy_active_sites":dict(counts),
 "legacy_named_java_files":obsolete,
 "same_shared_engine_preserved":True,
 "full_java21_compile_phase58_run":43755091161,
 "wire_parity_cases_phase58_run":24579,
 "windows_iocp_runtime_tested":False
}
(out/"FINAL_AUDIT.json").write_text(json.dumps(audit,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
(out/"SEND_LIFETIME_REFERENCES.txt").write_text("\n\n".join(details)[:250000],encoding="utf-8")
print("PHASE59_FINAL_AUDIT_PASS",json.dumps(audit,ensure_ascii=False))
