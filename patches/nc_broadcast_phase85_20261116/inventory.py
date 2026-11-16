#!/usr/bin/env python3
from pathlib import Path
import hashlib, zipfile, re, json, collections
root=Path(__file__).resolve().parents[2]
src=root/"downloads/RKern_NC_SPacket_Assemble_Phase84_FULL_SRC_20261116.zip"
assert hashlib.sha256(src.read_bytes()).hexdigest()=="5e442ada66a78c14075952e9114c1d0a41dccb45ef9c081aec16789c77599c3c"
out=root/"reports/nc_phase85_legacy_context";out.mkdir(parents=True,exist_ok=True)
rows=[];ct=collections.Counter();by_type=collections.defaultdict(list)
with zipfile.ZipFile(src) as z:
    for name in z.namelist():
        if not name.endswith(".java"):continue
        s=z.read(name).decode("utf-8")
        lines=s.splitlines()
        if name.startswith("src/") and not "/sc/packet/" in name:
            for m in re.finditer(r"\bCSharedBroadcast\s*\.\s*(send[A-Za-z]+|openRaw)\s*\(",s):
                p=m.end();depth=1;instring=False;esc=False;inchar=False
                for j in range(p,len(s)):
                    ch=s[j]
                    if esc:esc=False;continue
                    if ch=="\\" and (instring or inchar):esc=True;continue
                    if ch=='"' and not inchar:instring=not instring;continue
                    if ch=="'" and not instring:inchar=not inchar;continue
                    if instring or inchar:continue
                    if ch=="(":depth+=1
                    elif ch==")":
                        depth-=1
                        if not depth:break
                body=s[p:j]
                line=s.count("\n",0,m.start())+1
                summary={"file":name,"line":line,"method":m.group(1),
                         "args":body[:3000],
                         "context":"\n".join(lines[max(0,line-6):min(len(lines),line+7)])[:3800]}
                rows.append(summary)
                ct[m.group(1)]+=1
                by_type[m.group(1)].append(summary)
        focus=["SC_DisplayEffect","SC_SystemMessage","SC_ServerMessage","SC_PacketBox",
           "SC_ChatMessageNoti","SC_LFCTrapEffect","SC_EffectLocation",
           "SC_IconMessage","SC_MessageYN","SC_Weather","SC_PartyChat","SC_ChatPacket",
           "SC_OnlyEffect","SC_Trap","SC_Fishing","SC_PinkName","SC_Magic","SC_ChangeHeading",
           "SC_NewChat","SC_War","SC_WorldPutObject",
           "SC_MoveChar","SC_DoActionGFX","SC_SkillSound","SC_ActionUI","SC_ShowCmd",
           "SC_RemoveObject","SC_WORLD_PUT_OBJECT_NOTI","SC_SOCIAL_ACTION_NOTI",
           "SC_PartyMemberList","SC_PartyMember","SC_ClanAttention","SC_CastleMaster",
           "SC_GameStart","SC_GameRap","SC_EtcPacket","SC_GameEnd","SC_GameOver",
           "SC_Party","SC_TrueTargetNew","SC_NpcChatPacket","SC_ShowCmd",
           "SC_ChartRank","SC_ChangeAttr","SC_WarStartMessage","SC_NewChat",
           "SC_Attack","SC_ChatMessageNoti","SC_BossYN"]
        if name.startswith("src/") and "/sc/packet/" in name and Path(name).stem in focus:
            (out/Path(name).name).write_text(s)
# Inventory remaining wrapper constructors and their exact first source expression.
def parse_args(s,i):
    depth=1;start=i+1;chunks=[];stack=["("];string=None;esc=False
    for j in range(i+1,len(s)):
        c=s[j]
        if esc:esc=False;continue
        if string:
            if c=="\\":esc=True
            elif c==string:string=None
            continue
        if c in ("'",'"'):string=c;continue
        if c in "([{":stack.append(c)
        elif c in ")]}":
            stack.pop()
            if not stack:chunks.append(s[start:j]);return chunks
        elif c=="," and len(stack)==1:
            chunks.append(s[start:j]);start=j+1
    return []
ctor=[]
with zipfile.ZipFile(src) as z:
    for name in z.namelist():
        if not name.endswith(".java") or not name.startswith("src/"):continue
        ss=z.read(name).decode("utf-8")
        for m in re.finditer(r"new\s+(?:l1j\.server\.server\.model\.network\.core\.broadcast\.)?CAsyncSendConstBuffer\s*\(",ss):
            args=parse_args(ss,m.end()-1)
            ctor.append({"file":name,"line":ss.count("\n",0,m.start())+1,
                "args":[x.strip()[:2000] for x in args]})
(out/"constructors.json").write_text(json.dumps(ctor,ensure_ascii=False,indent=2))
source_names=sorted(set(v["file"] for v in ctor if "CSharedBroadcast.java" not in v["file"]
         and not v["args"][0].startswith("l1j.server.server.model.network.core.broadcast.CSharedBroadcast.")))
with zipfile.ZipFile(src) as z:
    for name in source_names:
        dest=out/"legacy_src"/name
        dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_text(z.read(name).decode("utf-8"))


# Identify still-used legacy array based helpers and byte[] wrappers globally
names=set()
with zipfile.ZipFile(src) as z:
    for name in z.namelist():
        if not name.endswith(".java") or not name.startswith("src/"):continue
        txt=z.read(name).decode("utf-8")
        for j,line in enumerate(txt.splitlines(),1):
            if "CSharedBroadcast" in line or "new CAsyncSendConstBuffer" in line or "new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer" in line:
                names.add((name,j,line.strip()[:700]))
(out/"references.json").write_text(json.dumps(sorted(names),ensure_ascii=False,indent=2))


with zipfile.ZipFile(src) as z:
    for filename in (
      "src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java",
      "src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java",
      "src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBufferExceptSelf.java"):
        (out/Path(filename).name).write_text(z.read(filename).decode("utf-8"))

for method,vals in by_type.items():
    (out/("calls_"+method+".json")).write_text(json.dumps(vals,ensure_ascii=False,indent=2))
(out/"summary.json").write_text(json.dumps({"total":len(rows),"methods":dict(ct),
    "files":len(set(x["file"] for x in rows)),
    "sc_contexts":list(p.name for p in out.glob("SC_*.java"))},ensure_ascii=False,indent=2))
print("PHASE85_LEGACY_INVENTORY",json.dumps(dict(ct)),flush=True)
