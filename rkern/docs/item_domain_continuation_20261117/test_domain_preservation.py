from __future__ import annotations
from pathlib import Path
import hashlib,json,zipfile,re,sys
ROOT=Path(__file__).resolve().parents[2]
DOC=Path(__file__).resolve().parent
F=ROOT/'src/l1j/server/server/model/item/function'
M=json.loads((DOC/'METHOD_MAP.json').read_text('utf-8'))
EXPECTED=json.loads((DOC/'BASELINE_CALLER_SHA256.json').read_text('utf-8'))
NEW_GROUPS={}
for row in M: NEW_GROUPS.setdefault(row['previous'],{}).setdefault(row['current'],[]).append(row['method'])

def sha(src):return hashlib.sha256(src.encode('utf-8')).hexdigest()

def extract(src,name):
    # Extract method declaration and complete body with Java string/comment awareness.
    rx=re.compile(r'(?m)^\s*(?:(?:public|private|protected)\s+)?static\s+(?:ItemActionResult|boolean|void)\s+'+re.escape(name)+r'\s*\(')
    match=rx.search(src)
    if not match:raise AssertionError('Missing method '+name)
    start=src.rfind('\n',0,match.start())+1
    pos=match.end();paren=1
    while paren:
        if src[pos]=='(':paren+=1
        elif src[pos]==')':paren-=1
        pos+=1
    while src[pos]!='{':pos+=1
    pos+=1;depth=1;state='code'
    while depth:
        if pos >= len(src):raise AssertionError('Unbalanced '+name)
        a=src[pos];b=src[pos+1:pos+2]
        if state=='code':
            if a=='"':state='str'
            elif a=="'":state='chr'
            elif a=='/' and b=='/':state='line';pos+=1
            elif a=='/' and b=='*':state='block';pos+=1
            elif a=='{':depth+=1
            elif a=='}':depth-=1
        elif state=='str':
            if a=='\\':pos+=1
            elif a=='"':state='code'
        elif state=='chr':
            if a=='\\':pos+=1
            elif a=="'":state='code'
        elif state=='line':
            if a=='\n':state='code'
        elif state=='block':
            if a=='*' and b=='/':state='code';pos+=1
        pos+=1
    return src[start:pos]

assert len(M)==47,len(M)
for row in M:
    p=F/(row['current']+'.java')
    assert p.exists(),p
    s=p.read_text('utf-8')
    body=extract(s,row['method'])
    assert sha(body)==row['body_sha256'],(row['current'],row['method'],sha(body),row['body_sha256'])
    assert s.count('class '+row['current']+' ')==1,p
for old in NEW_GROUPS:
    assert not (F/(old+'.java')).exists(),old
    for p in (ROOT/'src').rglob('*.java'):
        assert not re.search(r'\b'+re.escape(old)+r'\b',p.read_text('utf-8')), (old,p)
# Caller byte-for-byte baseline after reversing only declared class symbol substitutions.
for filename,expected_hash in EXPECTED.items():
    s=(ROOT/filename).read_text('utf-8')
    for old, group in NEW_GROUPS.items():
        for cls, methods in group.items():
            for m in methods:
                s=s.replace(cls+'.'+m,old+'.'+m)
                s=s.replace('import static l1j.server.server.model.item.function.'+cls+'.'+m+';',
                            'import static l1j.server.server.model.item.function.'+old+'.'+m+';')
    # misc static wildcard imports were expanded to its new domain classes.
    group=NEW_GROUPS.get('MiscItemActions',{})
    block='\n'.join('import static l1j.server.server.model.item.function.'+cls+'.*;' for cls in group)
    if block in s:
        s=s.replace(block,'import static l1j.server.server.model.item.function.MiscItemActions.*;')
    assert sha(s)==expected_hash,(filename,sha(s),expected_hash)
print('PASS exact original method bodies=',len(M),'new domain classes=',len(set(x['current'] for x in M)))
print('PASS source callers unchanged except class symbol/import rewiring=',len(EXPECTED))
