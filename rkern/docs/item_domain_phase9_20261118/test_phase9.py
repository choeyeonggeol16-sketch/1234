#!/usr/bin/env python3
"""Verify precise item-function extraction, stable gateways and typed combat facade."""
from __future__ import annotations
import hashlib, json, re, subprocess, tempfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'src/l1j/server/server/model/item/function'
M=json.loads((Path(__file__).parent/'MANIFEST.json').read_text(encoding='utf-8'))

def digest(s):return hashlib.sha256(s.encode('utf-8')).hexdigest()

def extract(s,method):
    pat=r'(?m)^[ \t]*(?:public[ \t]+|private[ \t]+)?static[ \t]+[\w<>\[\]?]+[ \t]+'+re.escape(method)+r'[ \t]*\([^;{}]*?\)[ \t]*\{'
    matches=list(re.finditer(pat,s,re.S))
    assert len(matches)==1,('method declaration',method,len(matches))
    m=matches[0]; a=m.start(); i=m.end()-1; level=0;state='code'
    while i<len(s):
        x=s[i]; y=s[i+1:i+2]
        if state=='line':
            if x=='\n':state='code'
        elif state=='block':
            if x=='*' and y=='/':state='code';i+=1
        elif state=='str':
            if x=='\\':i+=1
            elif x=='"':state='code'
        elif state=='char':
            if x=='\\':i+=1
            elif x=="'":state='code'
        else:
            if x=='/' and y=='/':state='line';i+=1
            elif x=='/' and y=='*':state='block';i+=1
            elif x=='"':state='str'
            elif x=="'":state='char'
            elif x=='{':level+=1
            elif x=='}':
                level-=1
                if level==0:return s[a:i+1]
        i+=1
    raise AssertionError('unclosed method: '+method)

assert len(M['methods'])==18
new_classes=set()
for row in M['methods']:
    p=BASE/(row['new']+'.java')
    text=p.read_text(encoding='utf-8')
    assert digest(extract(text,row['method']))==row['sha256'],('body changed',row)
    assert text.count('final class '+row['new']+' {')==1,row
    new_classes.add(row['new'])
assert len(new_classes)==17
for old in M['old_groups_removed']:
    assert not (BASE/(old+'.java')).exists(),old
    for p in (ROOT/'src').rglob('*.java'):
        assert not re.search(r'\b'+re.escape(old)+r'\b',p.read_text('utf-8')),('dangling reference',old,p)

# Each transactional caller is byte-identical to Phase 8 after only replacing its
# new owner qualifier with the old owner qualifier.
for source,method in M['callers'].items():
    text=(BASE/(source+'.java')).read_text(encoding='utf-8')
    row=next(r for r in M['methods'] if r['old']=='DurableGoodsItems' and r['method']==method)
    old=row['old']+'.'+method
    new=row['new']+'.'+method
    assert text.count(new)==1,(source,new)
    text=text.replace(new,old)
    assert digest(text)==M['baseline_source_sha256'][source],('unexpected caller delta',source)

# The CombatAbilityItems facade's signatures and all thirteen dispatch targets
# remain stable (nine extracted effects plus four CombatSupportItems helpers).
facade=(BASE/'CombatAbilityItems.java').read_text(encoding='utf-8')
for row in M['methods']:
    if row['old'] in ('StunCombatItems','ShadowCombatItems','CommandCombatItems','MagicCombatItems'):
        before=row['old']+'.'+row['method']
        after=row['new']+'.'+row['method']
        assert facade.count(after)==1,('missing delegation',after)
        facade=facade.replace(after,before)
assert digest(facade)==M['baseline_source_sha256']['CombatAbilityItems']

# The public CombatSkillItemEffects facade remains compatible; its production
# implementations moved without body edits into three focused classes.
facade=(BASE/'CombatSkillItemEffects.java').read_text(encoding='utf-8')
for row in M['methods']:
    if row['old']=='CombatSkillItemEffects':
        body=extract(facade,row['method'])
        assert body.count(row['new']+'.'+row['method']+'(')==1
        assert body.count(';')==1,('unexpected extra effects in facade',row)
assert digest((BASE/'ItemActionDispatcher.java').read_text(encoding='utf-8'))==M['unchanged_dispatch_sha256']

# Per-class Random aliases must point to the same shared, long-lived RNG as before.
for name in ('BlazeStunItemEffect','ElvenStrikeItemEffect','ShadowBindingItemEffect','DemolitionItemEffect','EternityItemEffect'):
    s=(BASE/(name+'.java')).read_text(encoding='utf-8')
    if ' _random' in s and '_random.next' in s:
        assert 'private static final Random _random = CreatureItemActions._random;' in s,name

# Type-check and run the *actual* CombatAbilityItems facade with matching typed
# mock callees. No external server dependencies are needed for this part.
with tempfile.TemporaryDirectory(prefix='rkern_phase9_facade_') as tmp:
    t=Path(tmp)
    def put(path,body):
        f=t/path;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body,encoding='utf-8')
    m='l1j/server/server/model/Instance/'
    put('l1j/server/server/model/L1Character.java','package l1j.server.server.model; public class L1Character {}\n')
    for cls,superc in [('L1PcInstance','l1j.server.server.model.L1Character'),('L1NpcInstance','l1j.server.server.model.L1Character'),('L1ItemInstance','Object')]:
        put(m+cls+'.java',f'package l1j.server.server.model.Instance; public class {cls} extends {superc} '+'{}\n')
    prefix='package l1j.server.server.model.item.function;\nimport l1j.server.server.model.L1Character;\nimport l1j.server.server.model.Instance.*;\n'
    put('l1j/server/server/model/item/function/CreatureItemActions.java',prefix+'final class CreatureItemActions {}\n')
    skills=[r for r in M['methods'] if r['old'] in ('StunCombatItems','ShadowCombatItems','CommandCombatItems','MagicCombatItems')]
    for owner in sorted(set(r['new'] for r in skills)):
        methods=[]
        for r in skills:
            if r['new']!=owner:continue
            if r['method']=='calcMrDefense':
                methods.append('static double calcMrDefense(int mr,double dmg) { Trace.last="'+owner+'"; return dmg+mr; }')
            else:
                methods.append('static void '+r['method']+'(L1PcInstance pc,int id,L1ItemInstance item) { Trace.last="'+owner+'"; }')
        put('l1j/server/server/model/item/function/'+owner+'.java',prefix+'final class '+owner+' {'+' '.join(methods)+'}\n')
    put('l1j/server/server/model/item/function/CombatSupportItems.java',prefix+'''final class CombatSupportItems {
     static int targetDirection(L1PcInstance pc,int x,int y){Trace.last="direction";return 3;}
     static void Start(L1PcInstance pc,L1Character c){Trace.last="Start";}
     static void Start1(L1PcInstance pc,L1NpcInstance c){Trace.last="Start1";}
     static void 액션(L1PcInstance pc,L1Character c){Trace.last="action";}
     static void 액션1(L1PcInstance pc,L1NpcInstance c){Trace.last="action1";}
    }\n''')
    put('l1j/server/server/model/item/function/Trace.java',prefix+'final class Trace { static String last; }\n')
    checks=['L1PcInstance p=new L1PcInstance(); L1ItemInstance i=new L1ItemInstance(); L1NpcInstance n=new L1NpcInstance();']
    for row in skills:
        mname=row['method']; owner=row['new']
        call=('double got=CombatAbilityItems.calcMrDefense(10,30.0); if (got!=40.0) throw new AssertionError("MR");' if mname=='calcMrDefense'
              else f'CombatAbilityItems.{mname}(p,123,i);')
        checks.append(f'Trace.last="";{call} if(!"{owner}".equals(Trace.last))throw new AssertionError("{mname}");')
    checks+=['if(CombatAbilityItems.targetDirection(p,1,1)!=3) throw new AssertionError("direction");',
             'CombatAbilityItems.Start(p,p); CombatAbilityItems.Start1(p,n); CombatAbilityItems.액션(p,p); CombatAbilityItems.액션1(p,n);',
             'System.out.println("PASS phase9 typed CombatAbilityItems production facade: 9 effects + 5 support routes");']
    put('l1j/server/server/model/item/function/Phase9FacadeTest.java',prefix+'public final class Phase9FacadeTest { public static void main(String[] x){ '+' '.join(checks)+' } }\n')
    java=t/'l1j/server/server/model/item/function/CombatAbilityItems.java'
    java.write_bytes((BASE/'CombatAbilityItems.java').read_bytes())
    r=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(t),*[str(p) for p in t.rglob('*.java')]],text=True,capture_output=True)
    assert r.returncode==0,r.stderr
    r=subprocess.run(['java','-cp',str(t),'l1j.server.server.model.item.function.Phase9FacadeTest'],text=True,capture_output=True)
    assert r.returncode==0,r.stderr
    print(r.stdout.strip())
print('PASS Phase 9: 18 byte-identical methods, 17 focused classes, 5 obsolete classes removed')
print('PASS Phase 9: six CacheD transaction entrypoints, stable combat ABI, unchanged ItemActionDispatcher')
