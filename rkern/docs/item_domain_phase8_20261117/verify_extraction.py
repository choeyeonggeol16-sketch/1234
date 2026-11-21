#!/usr/bin/env python3
"""Regression: exact moved effect blocks, ordered doll teardown, and direct routing."""
from pathlib import Path
import re,json,hashlib
ROOT=Path(__file__).resolve().parents[2]
F=ROOT/'src/l1j/server/server/model/item/function'
M=json.loads((Path(__file__).parent/'EXTRACTION_SHA256.json').read_text('utf8'))

def h(s): return hashlib.sha256(s.encode('utf8')).hexdigest()

def match_brace(s,pos):
    assert s[pos]=='{'
    count=0;state='code';i=pos
    while i<len(s):
        c=s[i];n=s[i+1] if i+1<len(s) else ''
        if state=='code':
            if c=='"':state='string'
            elif c=="'":state='character'
            elif c=='/' and n=='/':state='line';i+=1
            elif c=='/' and n=='*':state='block';i+=1
            elif c=='{':count+=1
            elif c=='}':
                count-=1
                if count==0:return i
        elif state in ('string','character'):
            if c=='\\':i+=1
            elif (c=='"' if state=='string' else c=="'"):state='code'
        elif state=='line':
            if c=='\n':state='code'
        elif state=='block':
            if c=='*' and n=='/':state='code';i+=1
        i+=1
    raise AssertionError('unclosed Java block')

def method_body(s,method,pattern):
    r=re.search(pattern,s)
    assert r,('missing method',method)
    start=s.index('{',r.start(),r.end())
    end=match_brace(s,start)
    return s[start+1:end]

router=(F/'AdvancedAbilityItems.java').read_text('utf8')
assert not (F/'ActiveSkillItems.java').exists()
assert 'ActiveSkillItems.' not in ''.join(p.read_text('utf8') for p in F.glob('*.java'))
for itemId,item in M['active_skills'].items():
    c=(F/(item['class']+'.java')).read_text('utf8')
    body=method_body(c,'use',r'static void use\(L1PcInstance pc\)\s*\{')
    assert h(body.strip())==item['body_sha256'],('active skill changed',itemId)
    assert f'case {itemId} -> {item["class"]}.use(pc);' in router,('misrouted skill',itemId)
assert 'case 880800 -> DurableSkillItems.usePotentialScroll(pc, l1iteminstance);' in router
assert 'case 70740 -> ImmuneBladeSkillItems.UseImmuneBlade(pc);' in router
prime=(F/'PrimeSkillConsumable.java').read_text('utf8')
assert prime.count('isInWarAreaAndWarTime(pc)')==2
assert 'CastleWarBusiness.getInstance().isNowWar(castleId)' in prime
# Enchantment methods are still public static in SpecialEnchantScrolls, but effect work is moved.
enchant=(F/'SpecialEnchantScrolls.java').read_text('utf8')
for name,info in M['enchantment_methods'].items():
    source=(F/(info['class']+'.java')).read_text('utf8')
    pattern=r'(?m)^[ \t]*static void '+re.escape(name)+r'\(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target\)\s*\{'
    body=method_body(source,name,pattern)
    assert h(body.strip())==info['body_sha256'],('enchant body changed',name)
    pat=r'(?m)^[ \t]*public static void '+re.escape(name)+r'\(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target\)\s*\{'
    public_body=method_body(enchant,name,pat)
    assert public_body.strip()==f'{info["class"]}.{name}(pc, scroll, target);',('bad wrapper',name)
assert 'public static boolean isOwnedScrollTarget' in enchant
assert 'public static boolean isAccessoryEnchantTarget' in enchant
assert 'public static int qualityBonus' in enchant
# 31 conditional doll effects retain exact bytes and traversal order; no merged or dropped effects.
assembled=[]
for info in M['doll_effects']:
    src=(F/(info['class']+'.java')).read_text('utf8')
    pat=r'(?m)^\t\tif\(pc\.hasSkillEffect\(L1SkillId\.'+re.escape(info['skill'])+r'\)\)\s*\{'
    m=re.search(pat,src)
    assert m,('missing doll skill',info['skill'])
    idx=src.index('{',m.start(),m.end()); end=match_brace(src,idx)
    block=src[m.start():end+1]
    assert h(block.strip())==info['body_sha256'],('doll effect changed',info['skill'])
    assembled.append(info['skill'])
assert len(assembled)==31 and len(set(assembled))==31
public=(F/'DollEffectItems.java').read_text('utf8')
body=method_body(public,'RemoveDollPotential',r'public static void RemoveDollPotential\(L1PcInstance pc\)\s*\{')
assert [line.strip() for line in body.splitlines() if line.strip()]==([f'{cls}.remove(pc);' for cls in M['doll_groups']]+[M['doll_final_notification']])
print('PASS phase8: 7 skill bodies, 9 enchant bodies, 31 doll effect blocks unchanged; direct dispatch & 4 ordered doll delegates')
