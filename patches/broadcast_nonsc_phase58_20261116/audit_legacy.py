#!/usr/bin/env python3
"""Inventory residual active legacy broad/multi-recipient sends in pinned phase57."""
import zipfile,hashlib,re,collections,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase57_FULL_SRC_20261116.zip"
SHA="24a988a953a65f2004744100682d103145e9a908"
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
        if mode in ("string","char"):
            out.append(c);i+=1
            if escaped:escaped=False
            elif c=="\\":escaped=True
            elif c==('"' if mode=="string" else "'"):mode="code"
    return "".join(out)

raw=INPUT.read_bytes()
assert blob(raw)==SHA
with zipfile.ZipFile(INPUT) as z:
    assert z.testzip() is None
    source={n:z.read(n).decode("utf-8-sig",errors="replace") for n in z.namelist() if n.endswith(".java")}
assert len(source)==1710
out=ROOT/"reports/broadcast_nonsc_phase58_20261116"
out.mkdir(parents=True,exist_ok=True)
report=[]
counts=collections.Counter()
pattern={
"broadcastPacket":r"\bbroadcastPacket\s*\(",
"broadcastPacketToAll":r"\bbroadcastPacketToAll\s*\(",
"Broadcaster":r"\bBroadcaster\s*\.",
"genericBroadcastMethod":r"\bvoid\s+broadcast\s*\(\s*ServerPacket",
"packetReuseSend":r"\.sendPackets\s*\(\s*(?:pck|packet|pkt|sp|serverbasepacket)\s*,",
"sharedFanout":r"\bCSharedBroadcast\s*\.",
"legacyNewPacketInLoop":r"\bnew\s+S_[A-Za-z0-9_]+\s*\(",
}
for name,source_text in sorted(source.items()):
    s=strip_comments(source_text)
    for label,regex in pattern.items():
        for match in re.finditer(regex,s):
            idx=s.count("\n",0,match.start())+1
            lines=source_text.splitlines()
            nearby=" | ".join(line.strip() for line in lines[max(0,idx-3):idx+3])
            report.append(f"{name}:{idx} [{label}] {nearby[:650]}")
            counts[label]+=1
    if "MJEffectTriggerInstance" in source_text and not name.endswith("/MJEffectTriggerInstance.java"):
        for i,line in enumerate(source_text.splitlines(),1):
            if "MJEffectTriggerInstance" in line:
                report.append(f"{name}:{i} [triggerTypeUsage] {line.strip()[:400]}")
                counts["triggerTypeUsage"]+=1
    if re.search(r"\bget(?:Declared)?Method\s*\(\s*[^\n]*broadcast",s):
        report.append(name+" [reflection broadcast method may be looked up]")
        counts["reflectionCandidate"]+=1

for suffix in ("MJInstanceEnums.java","MJEffectTriggerInstance.java","MJCompanionTeleportHandler.java","L1CompanionInstance.java"):
    matches=[(n,s) for n,s in source.items() if n.endswith("/"+suffix)]
    for ix,(name,body) in enumerate(matches):
        (out/(suffix.removesuffix(".java")+(str(ix) if ix else "")+".txt")).write_text("SOURCE: "+name+"\n"+body,encoding="utf-8")
(out/"RESIDUAL_CALLSITES.txt").write_text("\n".join(report),encoding="utf-8")
(out/"COUNTS.json").write_text(json.dumps(dict(counts),indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
print("PHASE58_AUDIT_OK",len(source),dict(counts))
