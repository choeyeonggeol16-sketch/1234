#!/usr/bin/env python3
"""Phase60: source-wide, zero-mutation broadcast callsite inventory.
Every listed class and code excerpt comes from pinned, CRC-checked phase58 SRC.
The inventory is static only; Windows IOCP lifetime remains a runtime gate.
"""
from pathlib import Path
from collections import defaultdict,Counter
import zipfile,re,json,hashlib
ROOT=Path(__file__).resolve().parents[2]
ZIP=ROOT/"downloads/RKern_Broadcast_NonSC_Phase58_FULL_SRC_20261116.zip"
PIN="7593606d8cb04fb90207e7fc53dc70f9de19bbfb218d8cd9be3fc8247e0a65f3"
assert hashlib.sha256(ZIP.read_bytes()).hexdigest()==PIN
with zipfile.ZipFile(ZIP) as z:
    assert z.testzip() is None
    files={n:z.read(n).decode("utf-8-sig",errors="replace") for n in z.namelist() if n.endswith(".java")}
assert len(files)==1710
out=ROOT/"reports/broadcast_nonsc_phase60_20261116"
out.mkdir(parents=True,exist_ok=True)
def mask(s):
    a=list(s);i=0;mode=0;escaped=False
    while i<len(a):
        c=s[i];d=s[i+1] if i+1<len(a) else ""
        if mode==0:
            if c=="/" and d=="/":a[i]=a[i+1]=" ";i+=2;mode=1;continue
            if c=="/" and d=="*":a[i]=a[i+1]=" ";i+=2;mode=2;continue
            if c=='"':mode=3;escaped=False
            elif c=="'":mode=4;escaped=False
            i+=1;continue
        if mode==1:
            if c=="\n":mode=0
            else:a[i]=" "
            i+=1;continue
        if mode==2:
            if c=="*" and d=="/":a[i]=a[i+1]=" ";i+=2;mode=0;continue
            if c!="\n":a[i]=" "
            i+=1;continue
        # Mask string/char content as well so debug text does not count as active code.
        if c!="\n":a[i]=" "
        i+=1
        if escaped:escaped=False
        elif c=="\\":escaped=True
        elif c==('"' if mode==3 else "'"):mode=0
    return "".join(a)
# Receiver scanning captures receiver symbols, including broadcast with optional spaces.
rx=re.compile(r"(?<![\w$])(?P<receiver>[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*\.\s*broadcast\s*\(")
calls=[]
by=defaultdict(list)
for name,src in sorted(files.items()):
    m=mask(src)
    for match in rx.finditer(m):
        line=m.count("\n",0,match.start())+1
        receiver=match.group("receiver")
        src_line=src.splitlines()[line-1].strip()
        group=("SC_static" if receiver.startswith("SC_") else
               "other_static" if receiver.startswith(("CSharedBroadcast","L1World","MJ")) else
               "instance_or_unknown")
        row={"file":name,"line":line,"receiver":receiver,"kind":group,"code":src_line}
        calls.append(row);by[receiver].append(row)
# No false claims from old broad string count: compare all literal occurrences.
literal_calls=sum(mask(s).count(".broadcast(") for s in files.values())
assert len(calls)>=literal_calls-2,(len(calls),literal_calls)
sc_rows=[(r,len(v)) for r,v in by.items() if r.startswith("SC_")]
sc_rows.sort(key=lambda x:(-x[1],x[0]))
other_rows=[(r,len(v)) for r,v in by.items() if not r.startswith("SC_")]
other_rows.sort(key=lambda x:(-x[1],x[0]))
summary={
 "input_zip_sha256":PIN,
 "java_sources":len(files),
 "matched_broadcast_calls":len(calls),
 "exact_no_whitespace_dot_broadcast_calls":literal_calls,
 "receiver_count":len(by),
 "sc_receiver_count":len(sc_rows),
 "other_receiver_count":len(other_rows),
 "sc_receiver_calls":sum(n for _,n in sc_rows),
 "other_receiver_calls":sum(n for _,n in other_rows),
 "production_files_changed":0,
 "classes_not_searched_in_comments_strings":True,
 "verified_runtime_io_completion":False}
(out/"SUMMARY.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2)+"\n")
(out/"CALL_SITES.json").write_text(json.dumps(calls,ensure_ascii=False,indent=2)+"\n")
lines=["# Phase60 exact receiver counts (source only)","",f"Matched {len(calls)}, exact literal form {literal_calls}, receivers {len(by)}.","","## SC receivers"]
for receiver,n in sc_rows:
    first=by[receiver][:3]
    lines.append(f"- {receiver}: {n} — "+", ".join(f"{x['file']}:{x['line']}" for x in first))
lines+=["","## Other receivers"]
for receiver,n in other_rows:
    first=by[receiver][:7]
    lines.append(f"- {receiver}: {n} — "+", ".join(f"{x['file']}:{x['line']}" for x in first))
(out/"CALLS_BY_RECEIVER.md").write_text("\n".join(lines)+"\n",encoding="utf-8")
# Dump exact broadcast method implementations for review with safe length limits
impl=[]
for receiver,_ in sc_rows:
    base=receiver.rsplit(".",1)[-1]+".java"
    matches=[(n,s) for n,s in files.items() if n.endswith("/"+base)]
    impl.append(f"\n## {receiver}: source implementations {len(matches)}")
    for name,src in matches:
        sl=src.splitlines(); m=mask(src)
        # Show all broadcast method definitions (not caller sites) plus first helper methods.
        signatures=list(re.finditer(r"\b(?:public|private|protected)\s+(?:static\s+)?(?:void|boolean|int|byte\[\]|[A-Za-z0-9_<>, ?\[\]]+)\s+(?:broadcast|broadcastRecipients|shared[A-Za-z0-9_]*|sendToAll)\s*\(",m))
        impl.append("### "+name+f" ({len(signatures)} method candidates)")
        for x in signatures[:15]:
            start=m.count("\n",0,x.start())
            # Handle brace nesting to capture complete small method. Bound to 60 lines for review.
            b=m.find("{",x.end());depth=0;end=min(len(m),b+1)
            if b<0:continue
            for i in range(b,len(m)):
                if m[i]=="{":depth+=1
                elif m[i]=="}":
                    depth-=1
                    if depth==0:end=i+1;break
            firstline=start+1;endline=m.count("\n",0,end)+1
            if endline-firstline>70:
                impl.append(f"  {firstline}-{endline} (long method; first 70 lines):")
                endline=firstline+69
            impl.append("\n".join(f"{i+1}: {sl[i]}" for i in range(start,min(len(sl),endline))))
        if not signatures:impl.append("  NO direct broadcast method declaration; inspect inheritance.")
(out/"SC_BROADCAST_IMPLEMENTATIONS.md").write_text("\n\n".join(impl)+"\n",encoding="utf-8")
# Central sending/ownership context for deterministic human inspection.
cores=[
 "CSharedBroadcast.java","CAsyncSendConstBuffer.java","CAsyncSendConstBufferExceptSelf.java",
 "CUserSocket.java","CIOServer.java","SC_MoveChar.java","SC_ChatPacket.java","L1MJRaidPoisonInstance.java"]
for base in cores:
    match=[(n,s) for n,s in files.items() if n.endswith("/"+base)]
    if not match:continue
    label=base.removesuffix(".java")
    # Limit CUserSocket to sections that mention const buffers and lifecycle
    for name,src in match:
        if base in ("CUserSocket.java","CIOServer.java"):
            sl=src.splitlines()
            indexes=[i for i,x in enumerate(sl) if re.search(r"enqueueConstBufferConfirmed|sendFrozenWire|createConstBuffer|releaseConstBuffer|ownsConstBuffer|pendingSendBytes|sendFramePackets|sendConstBuffer|retire|closeWithBudget",x)]
            keep=set()
            for i in indexes:
                keep.update(range(max(0,i-8),min(len(sl),i+18)))
            txt="\n".join(f"{i+1}: {sl[i]}" for i in sorted(keep))
        else:
            txt="\n".join(f"{i+1}: {x}" for i,x in enumerate(src.splitlines()))
        (out/(label+"_REVIEW.txt")).write_text(f"SOURCE {name}\n"+txt[:260000],encoding="utf-8")
print("PHASE60_INVENTORY",json.dumps(summary,ensure_ascii=False))
