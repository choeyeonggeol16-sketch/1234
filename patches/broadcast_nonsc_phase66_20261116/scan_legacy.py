#!/usr/bin/env python3
"""Phase66: identify dead legacy broadcast declarations in the exact phase64 full SRC.
Read-only inventory, before deleting any source.
"""
import json,re,zipfile,hashlib
from pathlib import Path
BASE=Path("downloads/RKern_Broadcast_NonSC_Phase64_FULL_SRC_20261116.zip")
OUT=Path("reports/broadcast_nonsc_phase66_20261116")
OUT.mkdir(parents=True,exist_ok=True)
def executable(s):
    # Preserve identifier/line positions; strip line and block comments and Java string/char/text literals.
    out=[];i=0;n=len(s);state="code";escape=False
    while i<n:
        c=s[i];q=s[i+1] if i+1<n else ''
        if state=="code":
            if s.startswith('"""',i):out.extend('   ');i+=3;state="text";continue
            if c=='/' and q in '/*' and q:
                out.extend('  ');i+=2;state="line" if q=='/' else "block";continue
            if c in ('"',"'"):
                out.append(' ');i+=1;state="string" if c=='"' else "char";escape=False;continue
            out.append(c);i+=1;continue
        if state=="line":
            out.append('\n' if c=='\n' else ' ')
            if c=='\n':state="code"
            i+=1;continue
        if state=="block":
            if c=='*' and q=='/':out.extend('  ');i+=2;state="code";continue
            out.append('\n' if c=='\n' else ' ');i+=1;continue
        if state=="text" and s.startswith('"""',i):out.extend('   ');i+=3;state="code";continue
        out.append('\n' if c=='\n' else ' ')
        if state!="text":
            if escape:escape=False
            elif c=='\\':escape=True
            elif c==('"' if state=="string" else "'"):state="code"
        i+=1
    return ''.join(out)
assert BASE.exists()
raw=BASE.read_bytes();hash256=hashlib.sha256(raw).hexdigest()
assert hash256=="1fd040e4bb4fe89c9c8fe1d8e5f6c2877fbfb6e990f5e551016fe8dc1557a23d",hash256
with zipfile.ZipFile(BASE) as z:
    assert z.testzip() is None
    js={p:z.read(p).decode('utf-8-sig',errors='replace') for p in z.namelist() if p.endswith('.java')}
assert len(js)==1710,len(js)
ex={p:executable(s) for p,s in js.items()}
names=sorted(n for n in js if re.search(r'broadcas',n,re.I))
candidates=[]
for p in names:
    cls=p.rsplit('/',1)[-1][:-5]
    token=re.compile(r'\b'+re.escape(cls)+r'\b')
    refs=[{'path':q,'count':len(token.findall(x))} for q,x in ex.items() if q!=p and token.search(x)]
    refs.sort(key=lambda x:x['path'])
    candidates.append({'path':p,'class':cls,'other_file_references':refs,'possible_dead':len(refs)==0})
legacy={}
patterns={
  'broadcastPacket':r'\bbroadcastPacket\s*\(',
  'broadcastPacketToAll':r'\bbroadcastPacketToAll\s*\(',
  'Broadcaster dot call':r'\bBroadcaster\s*\.',
  'ServerPacket generic broadcast method':r'\bbroadcast\s*\(\s*ServerPacket\b'}
for k,pat in patterns.items():
    rx=re.compile(pat)
    hits=[{'path':p,'count':len(rx.findall(code))} for p,code in ex.items() if rx.search(code)]
    legacy[k]=hits
out={'sha256':hash256,'java':len(js),'legacy':legacy,'broadcast_named_classes':candidates,'delete_performed':False}
(OUT/'inventory.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('PHASE66_INVENTORY',json.dumps(out,ensure_ascii=False))
