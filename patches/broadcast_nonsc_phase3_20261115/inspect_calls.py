#!/usr/bin/env python3
import zipfile,pathlib,hashlib,re,collections,json
ROOT=pathlib.Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase2_FULL_SRC_20261115.zip"
EXPECTED="c3788d17f4b3e66f41a86205e59b8f16ce3b06b45f9bfedd3d26abe054a15f91"
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest()==EXPECTED
FIND=("broadcastPacket","broadcastPacketFast","broadcastPacketM","wideBroadcastPacket","broadcastPacketToAll","broadcast_map","broadcastAll")
pat=re.compile(r"\b("+ "|".join(FIND) +r")\s*\(")
rows=[];stats=collections.Counter();srcs=collections.Counter()
with zipfile.ZipFile(SOURCE) as z:
 for path in z.namelist():
  if not path.startswith("src/") or not path.endswith(".java"):continue
  s=z.read(path).decode("utf-8")
  for m in pat.finditer(s):
   p=m.end();d=1
   while p<len(s) and d:
    d+=(s[p]=="(")-(s[p]==")");p+=1
   signature=s[m.start():p]
   front=s[max(0,m.start()-85):m.start()]
   if re.search(r"\b(?:void|boolean|int|static)\s+$",front):kind="method-def"
   else:kind="call"
   l=s.count("\n",0,m.start())+1
   item={"path":path,"line":l,"name":m[1],"kind":kind,"call":" ".join(signature.split())[:450],
         "context":" ".join(s[max(0,m.start()-120):min(len(s),p+140)].split())[:460]}
   rows.append(item);stats[(kind,m[1])]+=1;srcs[(kind,path)]+=1
out=ROOT/"reports/broadcast_nonsc_phase3_20261115"
out.mkdir(parents=True,exist_ok=True)
(out/"CALLS.json").write_text(json.dumps({"stats":[[k[0],k[1],v] for k,v in stats.most_common()],
                                           "sites":rows},indent=2,ensure_ascii=False)+"\n")
report=["# Generic ServerPacket central broadcaster call sites","",
        "Source SHA256: "+EXPECTED,"Source file count: 1711","",
        "## Counts"]
for (kind,name),value in stats.most_common():report.append("- "+kind+" "+name+": "+str(value))
report+=["","## Potential calls grouped by source","",""]
for r in rows:
 if r["kind"]=="call":report.append(r["path"]+":"+str(r["line"])+"  "+r["call"]+"  | "+r["context"])
(out/"CALLS.txt").write_text("\n".join(report)+"\n")
print("TOTAL",len(rows),"CALLS",sum(v for (k,n),v in stats.items() if k=="call"))
for x in report[:180]:print(x[:400])
