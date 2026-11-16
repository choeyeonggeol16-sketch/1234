#!/usr/bin/env python3
from pathlib import Path
import hashlib, zipfile, re, json, collections
root=Path(__file__).resolve().parents[2]
src=root/"downloads/RKern_NC_SPacket_Assemble_Phase82_FULL_SRC_20261116.zip"
assert hashlib.sha256(src.read_bytes()).hexdigest()=="2be793057e4dc515212b03d409e41114dadfa21f656b83cae67f94111d8dbb00"
out=root/"reports/nc_phase83_legacy_context";out.mkdir(parents=True,exist_ok=True)
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
           "SC_NewChat","SC_War","SC_WorldPutObject"]
        if name.startswith("src/") and "/sc/packet/" in name and Path(name).stem in focus:
            (out/Path(name).name).write_text(s)
for method,vals in by_type.items():
    (out/("calls_"+method+".json")).write_text(json.dumps(vals,ensure_ascii=False,indent=2))
(out/"summary.json").write_text(json.dumps({"total":len(rows),"methods":dict(ct),
    "files":len(set(x["file"] for x in rows)),
    "sc_contexts":list(p.name for p in out.glob("SC_*.java"))},ensure_ascii=False,indent=2))
print("PHASE83_LEGACY_INVENTORY",json.dumps(dict(ct)),flush=True)
