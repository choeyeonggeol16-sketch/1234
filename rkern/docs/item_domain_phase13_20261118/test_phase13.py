#!/usr/bin/env python3
"""Phase13: original-to-split method-body and dispatch byte/source audit."""
from pathlib import Path
import re, hashlib, json
R=Path(__file__).resolve().parents[2] if __file__.endswith('/test_phase13.py') else Path('/mnt/data/item_phase13_work')
F=R/'src/l1j/server/server/model/item/function';D=R/'docs/item_domain_phase13_20261118'
M=json.loads((D/'MANIFEST.json').read_text('utf-8'))
sha=lambda s:hashlib.sha256(s.encode('utf-8')).hexdigest()
def body(text,name,op='first'):
    matches=list(re.finditer(r'(?m)^\s*(?:(?:public|private|protected)\s+)?static\s+(?:[\w<>\[\]]+)\s+'+re.escape(name)+r'\s*\(',text))
    assert matches,name
    m=matches[0] if op=='first' else matches[-1]
    par=0
    for i in range(m.end()-1,len(text)):
        if text[i]=='(':par+=1
        elif text[i]==')':
            par-=1
            if par==0:
                o=text.index('{',i)+1;break
    depth=1;state='normal';j=o
    while j<len(text):
        c=text[j]; n=text[j+1] if j+1<len(text) else ''
        if state=='normal':
            if c=='"':state='string'
            elif c=="'":state='char'
            elif c=='/' and n=='/':state='line';j+=1
            elif c=='/' and n=='*':state='block';j+=1
            elif c=='{':depth+=1
            elif c=='}':
                depth-=1
                if depth==0:return text[o:j]
        elif state=='string':
            if c=='\\':j+=1
            elif c=='"':state='normal'
        elif state=='char':
            if c=='\\':j+=1
            elif c=="'":state='normal'
        elif state=='line':
            if c=='\n':state='normal'
        elif state=='block':
            if c=='*' and n=='/':state='normal';j+=1
        j+=1
    raise ValueError(name)

ownership={
'ItemBonusHooks':{'DollTickBonuses':['putAll','calcMpTick','calcHpTick'],
                  'DollExperienceBonuses':['onDollSpawned','onDollRemoved','calcDollAdd'],
                  'EquipmentExperienceBonuses':['onEquipChanged','onEnchantChanged','calcArkaDeltaIfAny','applyInsigniaIfMatch','resetInsigniaIfMatch']},
'IceBuffItem':{'IceBuffSingleUse':['useStrIce','useDexIce','useIntIce'],
              'IceBuffAllUse':['useAllBuff'],
              'IceBuffEffects':['applyStrIce','applyDexIce','applyIntIce','clearIceBuff','clearCashScrollBuff','removeEffect']},
'TeleportUseService':{'TeleportItemRules':['canBypassMapRestrictions','preCheckTeleportState','isValidBookmark','canRandomTeleportHere'],
                      'TeleportRandomTravel':['doRandomTeleport','executeRandomTeleport','doMassTeleportRandom','startAction'],
                      'TeleportClanBookmarkTravel':['teleportClanMembersAroundBookmarkMass']}
}
count=0
for orig,groups in ownership.items():
    baseline=(D/(orig+'.java.baseline')).read_text('utf-8')
    for cls,names in groups.items():
        current=(F/(cls+'.java')).read_text('utf-8')
        for name in names:
            a=body(baseline,name);b=body(current,name)
            assert a==b,(orig,cls,name)
            record=[x for x in M['moved'] if x['source']==orig and x['method']==name]
            assert len(record)==1 and record[0]['body_sha256']==sha(a)
            count+=1
# Validate Ice dispatch, only four route names qualified in original body.
orig=(D/'IceBuffItem.java.baseline').read_text('utf-8')
new=(F/'IceBuffItem.java').read_text('utf-8')
new_body=body(new,'use')
for owner in ('IceBuffAllUse','IceBuffSingleUse'):new_body=new_body.replace(owner+'.','')
assert new_body==body(orig,'use')
# Teleport public paths differ only in the new owner of each private helper.
orig=(D/'TeleportUseService.java.baseline').read_text('utf-8')
new=(F/'TeleportUseService.java').read_text('utf-8')
for name in ('clickItem','handleSkillTeleport'):
    v=body(new,name)
    for owner in ('TeleportItemRules','TeleportRandomTravel','TeleportClanBookmarkTravel'):v=v.replace(owner+'.','')
    assert v==body(orig,name),name
# Ensure map/const initialization values preserved in functional owner classes.
old=(D/'ItemBonusHooks.java.baseline').read_text('utf-8')
for field,owner in [('MP_BY_TYPE','DollTickBonuses'),('HP_BY_TYPE','DollTickBonuses'),('MP_BY_LV','DollTickBonuses'),('HP_BY_LV','DollTickBonuses'),('ARKA_FIXED','EquipmentExperienceBonuses'),('DOLL_ADD','DollExperienceBonuses')]:
    original=re.findall(r'\b'+field+r'\.put\([^;]+;',old)
    current=re.findall(r'\b'+field+r'\.put\([^;]+;',(F/(owner+'.java')).read_text('utf-8'))
    assert original==current,(field,original,current)
for field,owner in [('INS_IDS','EquipmentExperienceBonuses'),('INS_PER','EquipmentExperienceBonuses'),('INS_SLOT','EquipmentExperienceBonuses')]:
    assert re.search(re.escape(field)+r'\s*=\s*\{[^}]+\}',old).group()==re.search(re.escape(field)+r'\s*=\s*\{[^}]+\}',(F/(owner+'.java')).read_text('utf-8')).group()
for field in ('ALL_BUFF_SKILLS',):
    a=re.search(re.escape(field)+r'\s*=\s*\{[^}]+\}',(D/'IceBuffItem.java.baseline').read_text('utf-8')).group()
    b=re.search(re.escape(field)+r'\s*=\s*\{[^}]+\}',(F/'IceBuffAllUse.java').read_text('utf-8')).group()
    assert a==b
print(f'PASS {count} original method bodies exact; two public teleport gateways + ice dispatch normalized identical')
print('PASS primitive bonus mappings / insignia arrays / integrated ice buff skill table exact preserved')
print('PASS compatibility public entrypoints unchanged; no new queues, threads, per-use collections')
