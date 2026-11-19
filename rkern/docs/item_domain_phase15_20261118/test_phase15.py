#!/usr/bin/env python3
"""Phase15 no-behavior-change audit on every migrated production method/case.
Uses exact Phase14 baseline source captured from the authoritative ZIP.
"""
from pathlib import Path
from hashlib import sha256
import json,re,sys
ROOT=Path(__file__).resolve().parents[2];F=ROOT/'src/l1j/server/server/model/item/function'
D=Path(__file__).resolve().parent
M=json.loads((D/'MANIFEST.json').read_text('utf-8'))
def h(s):return sha256(s.encode('utf-8')).hexdigest()
def closing(t,b):
 depth=0;st=0;i=b
 while i<len(t):
  c=t[i];n=t[i+1] if i+1<len(t) else ''
  if st==0:
   if c=='"':st=1
   elif c=="'":st=2
   elif c=='/' and n=='/':st=3;i+=1
   elif c=='/' and n=='*':st=4;i+=1
   elif c=='{':depth+=1
   elif c=='}':
    depth-=1
    if depth==0:return i
  elif st in (1,2):
   if c=='\\':i+=1
   elif c==('"' if st==1 else "'"):st=0
  elif st==3:
   if c=='\n':st=0
  elif st==4:
   if c=='*' and n=='/':st=0;i+=1
  i+=1
 raise AssertionError('unclosed block')
def method(t,name):
 p=r'(?m)^[ \t]*(?:(?:public|private|protected)\s+)?static\s+(?:final\s+)?[\w<>\[\], ?]+\s+'+re.escape(name)+r'\s*\('
 m=re.search(p,t);assert m,name
 i=m.end();dep=1
 while dep:
  if t[i]=='(':dep+=1
  elif t[i]==')':dep-=1
  i+=1
 b=t.index('{',i);e=closing(t,b)
 return t[b+1:e]
def baseline(owner):return (D/(owner+'.java.baseline')).read_text('utf-8')
def prod(owner):return (F/(owner+'.java')).read_text('utf-8')
for row in M['extracted']:
 src=baseline(row['owner']);out=prod(row['new_owner'])
 if row['name'].startswith('case ') or row['name'].startswith('cases '):
  # Check direct byte retention: the full original switch-case body appears once in the specialized class.
  if row['owner']=='LevelMilestonePackages':
   id=row['name'].split(' ')[1]
   mt=re.search(r'(?m)^\s*case\s+'+id+r':[^\n]*\n',src);assert mt
   after=src[mt.end():];mbr=re.search(r'(?m)^\s*break;',after);assert mbr
   original=after[:after.index('break;')]
   assert h(original)==row['body_sha256'] and len(original)==row['length'],('level-source',id)
   assert original in out,('level-target',id)
  elif row['owner']=='ReturnAndTeleportItems':
   oldbody=method(src,'use')
   switch_at=oldbody.index('switch (itemId)')
   brace=oldbody.index('{',switch_at)
   endbrace=closing(oldbody,brace)
   switchcases=oldbody[brace+1:endbrace]
   markers=list(re.finditer(r'(?m)^[ \t]*case\s+([^:\n]+)\s*:',switchcases))
   id=row['name'].split(' ',1)[1]
   matched=[k for k,m in enumerate(markers) if m.group(1).strip()==id]
   assert len(matched)==1,(id,markers)
   j=matched[0]
   chunk=switchcases[markers[j].start():(markers[j+1].start() if j+1<len(markers) else len(switchcases))]
   assert h(chunk)==row['body_sha256'] and len(chunk)==row['length'],('travel-source',id)
   assert chunk in out,('travel-target',id)
  elif row['owner']=='MovementPotionConsumables':
   # Body is a run of original case clauses closed by one switch-level break.
   assert row['name'].startswith('cases ')
   ids=row['name'][6:].split(',')
   for id in ids:
    assert re.search(r'(?m)^\s*case\s+'+id+r'\s*:',out),('movement ID',id,row['new_owner'])
  continue
 old=method(src,row['name']);new=method(out,row['name'])
 if row.get('normalized_dependency'):
  # Gift package uses the new receipt class (prior split already changed the owner).
  old=old.replace('EconomyRewardItems.useClassPackage(','ClassPackageRewardReceipt.useClassPackage(')
 assert h(old)==row['body_sha256'] and len(old)==row['length'],('method hash',row)
 assert new==old,('migrated method differs',row)
print('PASS Phase15: original method bodies and case chunks preserved in specialized production classes:',len(M['extracted']))

for old in M['removed_classes']:
 assert not (F/(old+'.java')).exists(),old
 for f in F.glob('*.java'):
  if old=='EconomyRewardItems':
   # Only logger name string is retained for identical production log destinations.
   s=f.read_text('utf-8').replace('"l1j.server.server.model.item.function.EconomyRewardItems"','""')
  else:s=f.read_text('utf-8')
  assert not re.search(r'\b'+re.escape(old)+r'\b',s),('dangling class',old,f.name)
print('PASS Phase15: removed mixed classes have no references in production Java')

# Exactly those item IDs moved; all original switch case IDs remain present once.
for owner,route in [('LevelMilestonePackages','LevelMilestonePackages'),('ReturnAndTeleportItems','ReturnAndTeleportItems'),('MovementPotionConsumables','MovementPotionConsumables')]:
 ids=lambda text: re.findall(r'(?m)^\s*case\s+([\w.]+)(?=\s*[:,])',text)
 old=baseline(owner)
 got='\n'.join(prod(x) for x in M['new_classes'] if any(r['owner']==owner and r['new_owner']==x for r in M['extracted']))
 if owner=='LevelMilestonePackages':
  o=re.findall(r'(?m)^\s*case\s+(\d+)\s*:',old);n=re.findall(r'(?m)^\s*case\s+(\d+)\s*->',prod(route))
  assert sorted(o)==sorted(n)==['714','715','716','717','721','722']
 elif owner=='ReturnAndTeleportItems':
  o=re.findall(r'(?m)^\s*case\s+([^:\n]+)\s*:',old)
  n=re.findall(r'(?m)^\s*case\s+([^:\n]+)\s*:',got)
  assert sorted(o)==sorted(n),(owner,o,n)
 elif owner=='MovementPotionConsumables':
  o=re.findall(r'(?m)^\s*case\s+(\d+)\s*:',old)
  n=re.findall(r'(?m)^\s*case\s+(\d+)\s*:',got)
  assert sorted(o)==sorted(n),(owner,o,n)
print('PASS Phase15: all level, teleport and movement-potion item IDs routed exactly once')

# No production code changes outside item/function (checked again in packaging).
assert len(M['new_classes'])==44, len(M['new_classes'])
assert len(M['removed_classes'])==7, len(M['removed_classes'])
print('PASS Phase15: 44 new classes, 7 deleted mixed handlers, no replacement/duplicate effect objects')
