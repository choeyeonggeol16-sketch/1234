#!/usr/bin/env python3
"""Re-run exhaustive source audit at phase62 and compare to phase56 lineage.
Counts executable sites only (comments and string literals are ignored).
An inventory is not proof of actual Windows runtime correctness.
"""
from pathlib import Path
import hashlib,collections,json,zipfile,re
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase56_FULL_SRC_20261116.zip"
CURR=ROOT/"downloads/RKern_Broadcast_NonSC_Phase62_FULL_SRC_20261116.zip"
PIN="556a289819e5f746c3ef0ba217cef54b981274ef"
REPORT=ROOT/"reports/broadcast_nonsc_phase63_20261116"
def blob(data):return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\x00"+data).hexdigest()
def executable(text):
    # Keep newlines and identifiers while replacing all comments and literal
    # contents with spaces. This handles escapes and Java text blocks.
    out=[];i=0;n=len(text);mode="code";esc=False
    while i<n:
        c=text[i];nxt=text[i+1] if i+1<n else ""
        if mode=="code":
            if text.startswith('"""',i):
                out.extend("   ");i+=3;mode="text";continue
            if c=="/" and nxt in ("/","*"):
                out.extend("  ");mode="line" if nxt=="/" else "block";i+=2;continue
            if c=='"' or c=="'":
                out.append(" ");mode="string" if c=='"' else "char";esc=False;i+=1;continue
            out.append(c);i+=1;continue
        if mode=="line":
            if c=="\n":out.append("\n");mode="code"
            else:out.append(" ")
            i+=1;continue
        if mode=="block":
            if c=="*" and nxt=="/":
                out.extend("  ");i+=2;mode="code";continue
            out.append("\n" if c=="\n" else " ");i+=1;continue
        if mode=="text":
            if text.startswith('"""',i):
                out.extend("   ");i+=3;mode="code";continue
            out.append("\n" if c=="\n" else " ");i+=1;continue
        out.append("\n" if c=="\n" else " ")
        if esc:esc=False
        elif c=="\\":esc=True
        elif c==('"' if mode=="string" else "'"):mode="code"
        i+=1
    return "".join(out)

raw=CURR.read_bytes();assert blob(raw)==PIN
with zipfile.ZipFile(BASE) as z:
    assert z.testzip() is None
    old={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
with zipfile.ZipFile(CURR) as z:
    assert z.testzip() is None
    curr={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
assert set(old)==set(curr)
java={k:v.decode("utf-8-sig",errors="replace") for k,v in curr.items() if k.endswith(".java")}
assert len(java)==1710
changed=sorted(n for n in old if old[n]!=curr[n])
assert all(n.endswith(".java") for n in changed)
assert curr["CTransport.dll"]==old["CTransport.dll"]
legacy={
 "broadcastPacket":r"\bbroadcastPacket\s*\(",
 "broadcastPacketToAll":r"\bbroadcastPacketToAll\s*\(",
 "Broadcaster":r"\bBroadcaster\s*\.",
 "genericServerPacketBroadcast":r"\b(?:public|private|protected)\s+(?:static\s+)?void\s+broadcast\s*\(\s*ServerPacket\b",
 "packetReuseInFanout":r"\b(?:sendPackets|sendPacket)\s*\(\s*(?:pck|packet|pkt|sp|serverbasepacket)\s*,\s*false\s*\)",
}
counters=collections.Counter();hits=[]
modern=collections.Counter()
broadcast_methods=[]
suspects=[]
for path,body in sorted(java.items()):
    ex=executable(body)
    for label,regex in legacy.items():
        for match in re.finditer(regex,ex):
            line=ex.count("\n",0,match.start())+1
            counters[label]+=1
            hits.append(f"{path}:{line} {label}")
    if "model/network/core/sc/packet/" in path or "model/network/core/broadcast/" in path:
        for m in re.finditer(r"\b(?:public|protected|private)\s+static\s+(?:void|boolean)\s+(broadcast\w*)\s*\(",ex):
            modern[path.rsplit("/",1)[-1]]+=1
            broadcast_methods.append(f"{path}:{ex.count(chr(10),0,m.start())+1} {m.group(1)}")
    for m in re.finditer(r"\b(?:for|while)\s*\(",ex):
        start=m.end()
        pos=start;depth=1
        while pos<len(ex) and depth:
            if ex[pos]=="(":depth+=1
            elif ex[pos]==")":depth-=1
            pos+=1
        if depth:continue
        # inspect the loop body bounded to 16 lines for common repeated encoding
        tail=ex[pos:pos+1200]
        x=re.search(r"\b(?:SC_\w+\.send|sendDirectRaw|new\s+S_\w+)\s*\(",tail)
        if x is None:continue
        # skip gigantic parent scopes; this is a risk hint not a proven error.
        ln=ex.count("\n",0,m.start())+1
        if len(executable(body).splitlines())<ln:continue
        snippet=body.splitlines()[ln-1].strip()[:150]
        suspects.append(f"{path}:{ln} {snippet}")
REPORT.mkdir(exist_ok=True,parents=True)
summary={
 "source":"phase62",
 "files":len(curr),
 "java_files":len(java),
 "changed_from_phase56":changed,
 "identical_native_dll":True,
 "identical_non_java_files":True,
 "legacy_active_sites":dict(counters),
 "modern_sc_broadcast_methods":sum(modern.values()),
 "modern_sc_broadcast_owners":len(modern),
 "loop_suspects_unreviewed":len(suspects),
 "loop_suspects_are_not_proven_bugs":True,
 "actual_windows_runtime_tested_here":False,
 "source_sha256":hashlib.sha256(raw).hexdigest()
}
(REPORT/"SUMMARY.json").write_text(json.dumps(summary,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
(REPORT/"LEGACY_CALLS.txt").write_text("\n".join(hits)+"\n",encoding="utf-8")
(REPORT/"BROADCAST_METHODS.txt").write_text("\n".join(broadcast_methods)+"\n",encoding="utf-8")
(REPORT/"LOOP_REVIEW_QUEUE.txt").write_text("\n".join(sorted(set(suspects)))+"\n",encoding="utf-8")
print("PHASE63_FULL_AUDIT",json.dumps(summary,ensure_ascii=False))
assert not any(counters.values()),counters
