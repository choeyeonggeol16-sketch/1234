#!/usr/bin/env python3
from pathlib import Path
import zipfile, re, hashlib, difflib
ROOT=Path(__file__).resolve().parents[2]
SRC=ROOT/'src'
ITEM=SRC/'l1j/server/server/model/item/function'
BASE=Path('/mnt/data/RKern_ItemFunction_Consolidation_Memory_Phase18_FULL_SRC_20261118.zip')
P='src/l1j/server/server/model/item/function/'

def method(text,name):
 pat=r'(?m)^\s*(?:(?:public|private|protected)\s+)?static\s+[\w<>\[\]]+\s+'+re.escape(name)+r'\s*\('
 m=re.search(pat,text)
 if not m: raise AssertionError('missing '+name)
 i=m.end()-1;level=0
 while i<len(text):
  if text[i]=='(':level+=1
  elif text[i]==')':
   level-=1
   if level==0:break
  i+=1
 pos=text.index('{',i)+1;start=pos;level=1;state='N'
 while pos<len(text):
  c=text[pos];n=text[pos+1] if pos+1<len(text) else ''
  if state=='N':
   if c=='"':state='S'
   elif c=="'":state='C'
   elif c=='/' and n=='/':state='L';pos+=1
   elif c=='/' and n=='*':state='B';pos+=1
   elif c=='{':level+=1
   elif c=='}':
    level-=1
    if level==0:return text[start:pos]
  elif state in ('S','C'):
   if c=='\\':pos+=1
   elif c==('"' if state=='S' else "'"):state='N'
  elif state=='L':
   if c=='\n':state='N'
  elif state=='B':
   if c=='*' and n=='/':pos+=1;state='N'
  pos+=1
 raise AssertionError(name)

removed=['IceBuffAllUse.java','IceBuffSingleUse.java','IceBuffEffects.java',
          'HastePotionEffects.java','BraveryPotionEffects.java','FruitPotionEffects.java'] + [f'RecruitStarter{s}Kit.java' for s in ('Warrior','Knight','Dragonknight','Crown','Wizard','Blackwizard','Elf','Darkelf')]

if BASE.is_file():
 with zipfile.ZipFile(BASE) as z:
  before={e.filename:z.read(e.filename) for e in z.infolist() if not e.is_dir()}
else:
 before={'src/l1j/server/server/model/item/function/'+p.name[:-len('.baseline')]:p.read_bytes() for p in (Path(__file__).resolve().parent/'baseline').glob('*.baseline')}
if True:
 latest={str(p.relative_to(ROOT)):p.read_bytes() for p in ROOT.rglob('*') if p.is_file() and not p.name.endswith('.class') and not str(p).startswith(str(ROOT/'docs/item_domain_phase19_20261118'))}
 # previous includes all baseline docs as well (some new documents are excluded intentionally)
 for f,blob in before.items():
  if f not in latest: assert f.startswith(P) and f.split('/')[-1] in removed,('UNEXPECTED REMOVE',f)
 for f,blob in latest.items():
  if f not in before:
   if BASE.is_file():raise AssertionError('UNEXPECTED ADD '+f)
   continue
  if blob!=before[f]:assert f.startswith(P) or f=='README_KO.md',('non-item edit',f)
 # Compare moved method source bodies exactly, including control flow and packet order.
 count=0
 for suffix in ('Warrior','Knight','Dragonknight','Crown','Wizard','Blackwizard','Elf','Darkelf'):
  b=before[P+f'RecruitStarter{suffix}Kit.java'].decode('utf8')
  a=(ITEM/'RecruitStarterKit.java').read_text('utf8')
  assert method(b,'grant')==method(a,'grant'+suffix),('starter',suffix)
  count+=1
 for src,target,methods in (
  ('IceBuffAllUse','IceBuffItem',['useAllBuff']),
  ('IceBuffSingleUse','IceBuffItem',['useStrIce','useDexIce','useIntIce']),
  ('IceBuffEffects','IceBuffItem',['applyStrIce','applyDexIce','applyIntIce','clearIceBuff','clearCashScrollBuff','removeEffect']),
  ('HastePotionEffects','MovementPotionEffects',['useGreenPotion']),
  ('BraveryPotionEffects','MovementPotionEffects',['useBravePotion']),
  ('FruitPotionEffects','MovementPotionEffects',['useFruit']),
 ):
  old=before[P+src+'.java'].decode('utf8');new=(ITEM/(target+'.java')).read_text('utf8')
  for n in methods:
   assert method(old,n)==method(new,n),(src,n)
   count+=1
 # Precisely identify formatting-only changes in other sources, with no additional code modifications.
 pattern=re.compile(r'String\.format\("((?:[^"\\]|\\.)*?)%d((?:[^"\\]|\\.)*?)",\s*([A-Za-z_]\w*)\)')
 formats=[]
 for f in latest:
  if f in before and before[f]!=latest[f] and f not in ('README_KO.md',P+'RecruitStarterKit.java',P+'IceBuffItem.java',P+'MovementPotionEffects.java'):
   s=before[f].decode('utf8')
   hits=[]
   def sub(m):
    prefix,suffix,var=m.groups()
    if '%' in prefix or '%' in suffix:return m.group(0)
    hits.append((prefix,suffix,var))
    return f'"{prefix}" + {var} + "{suffix}"'
   expected=pattern.sub(sub,s)
   assert expected.encode('utf8')==latest[f],('unexpected edits',f)
   assert hits,('unexpected edit',f)
   formats.extend(hits)
 # Caller methods still dispatch exactly once to each original branch.
 entry=(ITEM/'RecruitStarterKit.java').read_text()
 old_entry=before[P+'RecruitStarterKit.java'].decode('utf8')
 for suffix in ('Warrior','Knight','Dragonknight','Crown','Wizard','Blackwizard','Elf','Darkelf'):
  assert entry.count('grant'+suffix+'(pc);')==1
  assert old_entry.count('RecruitStarter'+suffix+'Kit.grant(pc);')==1
 ice=(ITEM/'IceBuffItem.java').read_text()
 for n in ['useAllBuff','useStrIce','useDexIce','useIntIce']:
  assert ice.count(n+'(pc, item);')==1
 for src in removed:
  cls=src[:-5]
  assert not (ITEM/src).exists()
  # Comments and historical docs excluded; source code must no longer depend on removed types.
  for f in SRC.rglob('*.java'):
   assert not re.search(r'\b'+re.escape(cls)+r'\b',f.read_text('utf8')),('dangling ref',cls,f)
 expected_deleted=set(P+x for x in removed)
 actual_deleted=set(before)-set(latest)
 assert actual_deleted==expected_deleted,(actual_deleted,expected_deleted)
 production_count=sum(x.endswith('.java') for x in latest if x.startswith('src/'))
 print('PASS Phase19 structure: '+str(count)+' exact original method bodies, '+str(len(removed))+' merged classes removed, '+str(len(formats))+' pure formatting allocation optimizations, production_java='+str(production_count))
 print('PHASE19_FORMATS',repr(formats))
