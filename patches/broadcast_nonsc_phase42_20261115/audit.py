#!/usr/bin/env python3
from pathlib import Path
import zipfile,re,collections
root=Path(__file__).resolve().parents[2]
src=root/"downloads/RKern_Broadcast_NonSC_Phase41_FULL_SRC_20261115.zip"
def mask_comments(text):
    # Replace comment content with spaces while retaining line layout, respecting quoted strings and chars.
    out=list(text); i=0; n=len(text); mode="code"; esc=False
    while i<n:
        c=text[i]; d=text[i+1] if i+1<n else ""
        if mode=="code":
            if c=='"':mode="string"
            elif c=="'":mode="char"
            elif c=="/" and d=="/":
                out[i]=out[i+1]=" ";i+=2;mode="line";continue
            elif c=="/" and d=="*":
                out[i]=out[i+1]=" ";i+=2;mode="block";continue
        elif mode=="string":
            if esc:esc=False
            elif c=="\\":esc=True
            elif c=='"':mode="code"
        elif mode=="char":
            if esc:esc=False
            elif c=="\\":esc=True
            elif c=="'":mode="code"
        elif mode=="line":
            if c=="\n":mode="code"
            else:out[i]=" "
        elif mode=="block":
            if c=="*" and d=="/":
                out[i]=out[i+1]=" ";i+=2;mode="code";continue
            if c!="\n":out[i]=" "
        i+=1
    return "".join(out)
pattern=re.compile(r'\b(?:broadcastPacket|broadcastPacketToAll|broadcastPacketForFindInvis|broadcastPacketExceptTargetSight|broadcastPacketExceptTarget|broadcastPacketForFindHidden)\s*\(|\bBroadcaster\.\w+\s*\(')
live=[]
sendloops=[]
focused=("L1Character.java","L1World.java","Broadcaster.java","L1PcInstance.java","CSharedBroadcast.java")
with zipfile.ZipFile(src) as z:
    assert z.testzip() is None
    for name in z.namelist():
        if not (name.startswith("src/") and name.endswith(".java")):continue
        raw=z.read(name).decode("utf8",errors="replace")
        masked=mask_comments(raw)
        lines=raw.splitlines();ml=masked.splitlines()
        for i,line in enumerate(ml):
            if pattern.search(line):
                live.append((name,i+1,lines[i].strip()))
            if re.search(r'\b(?:SC_\w+)\.send\(\s*(?:pc|player|member|p)\s*,',line):
                prev="\n".join(ml[max(0,i-12):i])
                if re.search(r'\bfor\s*\(|\bwhile\s*\(',prev):
                    sendloops.append((name,i+1,lines[i].strip()))
    print("LIVE_LEGACY_BROADCAST_COUNT",len(live))
    print("LIVE_LEGACY_FILES",collections.Counter(n for n,_,_ in live).most_common())
    for n,line,call in live[:160]:print("LEGACY",n,line,call)
    print("POTENTIAL_DIRECT_SEND_LOOP_COUNT",len(sendloops))
    print("TOP_DIRECT_SEND_LOOP_FILES",collections.Counter(n for n,_,_ in sendloops).most_common(55))
    for n,line,call in sendloops[:130]:print("LOOP",n,line,call)
    for short in focused:
        entries=[n for n in z.namelist() if n.endswith("/"+short) and n.startswith("src/")]
        for name in entries[:1]:
            raw=z.read(name).decode("utf8",errors="replace");lines=raw.splitlines();ml=mask_comments(raw).splitlines()
            hits=[i for i,l in enumerate(ml) if re.search(r'\b(?:public|private|protected)\s+(?:static\s+)?(?:void|boolean)\s+(?:broadcastPacket|broadcastPacketToAll|broadcastPacketExceptTarget|sendRecipient|sendAll|sendVisible)',l)]
            print("FOCUSED",name,len(lines),"hits",len(hits))
            for i in hits[:22]:
                for k in range(max(0,i-1),min(len(lines),i+19)):print(f"{k+1}: {lines[k]}")
    print("PHASE42_AUDIT_OK")
