#!/usr/bin/env python3
"""Verify all extracted item-use bodies, wrapper wiring and rune ID coverage."""
from pathlib import Path
import re, json, hashlib
ROOT=Path(__file__).resolve().parents[2]
F=ROOT/'src/l1j/server/server/model/item/function'
M=json.loads((Path(__file__).parent/'EXTRACTION_SHA256.json').read_text('utf8'))

def h(s):return hashlib.sha256(s.encode('utf8')).hexdigest()

def end_method(s,p):
    assert s[p]=='{'
    i=p;level=0;state='code'
    while i<len(s):
        c=s[i];n=s[i+1] if i+1<len(s) else ''
        if state=='code':
            if c=='/' and n=='/':state='line';i+=2;continue
            if c=='/' and n=='*':state='block';i+=2;continue
            if c=='"':state='string';i+=1;continue
            if c=="'":state='char';i+=1;continue
            if c=='{':level+=1
            elif c=='}':
                level-=1
                if level==0:return i+1
        elif state=='line':
            if c=='\n':state='code'
        elif state=='block':
            if c=='*' and n=='/':state='code';i+=2;continue
        else:
            if c=='\\':i+=2;continue
            if c==('"' if state=='string' else "'"):state='code'
        i+=1
    raise AssertionError('Unclosed method body')

def find_method(s,method):
    pat=re.compile(r'(?m)^[ \t]*public static (?:void|boolean|int) '+re.escape(method)+r'\([^\n)]*\)\s*\{')
    mat=pat.search(s)
    assert mat,method
    return s[mat.start():end_method(s,mat.end()-1)]

origins={k:(F/(k+'.java')).read_text('utf8') for k in M['baseline']}
for name,v in M['methods'].items():
    moved=find_method((F/(v['class']+'.java')).read_text('utf8'),name)
    assert h(moved)==v['sha256'],('moved body mismatch',name)
    origin=origins[v['origin']]
    assert origin.count(v['wrapper'])==1,('wrong forwarding wrapper',name)
    origins[v['origin']]=origin.replace(v['wrapper'],moved,1)
for name in ('EnchantmentItems','PotionEffects','TransformationItems'):
    assert h(origins[name])==M['baseline'][name],('original reconstruction mismatch',name)

rune=(F/'RuneRewardPackages.java').read_text('utf8')
case_keys=[]
for cls in [n for n in M['new_classes'] if n.startswith('SealedRuneLevel')]:
    text=(F/(cls+'.java')).read_text('utf8')
    matches=list(re.finditer(r'(?m)^[ \t]*case\s+(\d+)\s*:',text))
    assert len(matches)==5,cls
    for i,mm in enumerate(matches):
        end=matches[i+1].start() if i+1<len(matches) else text.index('            default: break;')
        block=text[mm.start():end].strip()
        id=mm.group(1)
        # The case's effect body is preserved byte-for-byte; only whitespace around blocks differs.
        original_block=h(block)
        assert M['rune_cases'][id]['class']==cls
        # A stored original SHA matches the case, modulo surrounding whitespace.
        assert original_block==M['rune_cases'][id]['sha256'],('rune reward case changed',id)
        assert re.search(r'\bbreak\s*;\s*$',block),(cls,id)
        case_keys.append(id)
    labels=', '.join(str(int(mm.group(1))) for mm in matches)
    assert f'case {labels} -> {cls}.stage(pc, itemId, tx, messages);' in rune,cls
assert len(case_keys)==25 and len(set(case_keys))==25
assert set(case_keys)==set(M['rune_cases'])
assert len(re.findall(r'-> SealedRuneLevel\d+Rewards.stage\(',rune))==5
print('PASS',len(M['methods']),'method bodies byte-identical, 3 original classes reconstructed; 25 rune IDs routed into 5 handlers')
