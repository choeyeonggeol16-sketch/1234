#!/usr/bin/env python3
"""Source-faithfulness and dispatch audit for the Phase16 extracted classes."""
from pathlib import Path
import json,re,hashlib
R=Path(__file__).resolve().parents[2]
F=R/'src/l1j/server/server/model/item/function'
D=R/'docs/item_domain_phase16_20261118'
M=json.loads((D/'MANIFEST.json').read_text('utf-8'))

def closing(s,brace):
 i=brace;d=0;state=0
 while i<len(s):
  c=s[i];n=s[i+1] if i+1<len(s) else ''
  if state==0:
   if c=='"':state=1
   elif c=="'":state=2
   elif c=='/' and n=='/':state=3;i+=1
   elif c=='/' and n=='*':state=4;i+=1
   elif c=='{':d+=1
   elif c=='}':
    d-=1
    if d==0:return i
  elif state==1:
   if c=='\\':i+=1
   elif c=='"':state=0
  elif state==2:
   if c=='\\':i+=1
   elif c=="'":state=0
  elif state==3:
   if c=='\n':state=0
  elif state==4:
   if c=='*' and n=='/':state=0;i+=1
  i+=1
 raise AssertionError('unbalanced braces')

for entry in M['extracted']:
 owner=entry['owner'];new=entry['new_owner'];region=entry['region']
 original=(D/(owner+'.java.baseline')).read_text('utf-8')
 result=(F/(new+'.java')).read_text('utf-8')
 if region.startswith('pc.is'):
  m=re.search(r'if\s*\(pc\.'+re.escape(region[3:])+r'\(\)\)\s*\{',original)
  assert m,region
  b=m.end()-1;block=original[b+1:closing(original,b)]
 elif region in ('ranking class polymorph','ranking class polymorph1','myth class polymorph'):
  m=re.search(r'if\s*\(s\.equalsIgnoreCase\("'+re.escape(region)+r'"\)\)\s*\{',original)
  assert m,region
  b=m.end()-1;block=original[b+1:closing(original,b)]
 else:
  method=region
  pat=r'(?m)^\s*(?:(?:public|private|protected)\s+)?static\s+[\w<>\[\], ?]+\s+'+re.escape(method)+r'\s*\('
  m=re.search(pat,original);assert m,region
  b=original.index('{',m.end());block=original[b+1:closing(original,b)]
 assert hashlib.sha256(block.encode()).hexdigest()==entry['sha256'],new
 assert block in result, ('production method lost original statements',new)
 assert result.count(block)==1,('duplicate block',new)

recruit=(F/'RecruitStarterKit.java').read_text('utf-8')
original=(D/'RecruitStarterKit.java.baseline').read_text('utf-8')
assert recruit.count('consumeItem(3000366, 1)')==original.count('consumeItem(3000366, 1)')==1
members=['Warrior','Knight','Dragonknight','Crown','Wizard','Blackwizard','Elf','Darkelf']
positions=[recruit.index('RecruitStarter'+x+'Kit.grant(pc)') for x in members]
assert positions==sorted(positions)
assert recruit.index('consumeItem(3000366, 1)')<min(positions)
assert original.count('봉인템(pc,')-1==sum((F/(('RecruitStarter'+x+'Kit')+'.java')).read_text('utf-8').count('봉인템(pc,') for x in members)
assert (F/'RecruitStarterItemGrant.java').read_text('utf-8').count('storeItem(item)')==1

passive=(F/'PassiveSkillBooks.java').read_text('utf-8')
assert passive.count('PassiveSkillLearningRules.ruleFor(itemId)')==1
assert passive.count('PassiveSkillLearningEffects.effectFor(pc, itemId)')==1
rules=(F/'PassiveSkillLearningRules.java').read_text('utf-8')
effects=(F/'PassiveSkillLearningEffects.java').read_text('utf-8')
assert rules.count('private static final ItemLearningRule ')==27
assert len(re.findall(r'case\s+\d+\s*->\s*\(\)\s*->',effects))==27
poly=(F/'PolymorphScrollEffects.java').read_text('utf-8')
for cls in ['RankingClassPolymorphNames','MythicClassPolymorphNames','AlternateRankingPolymorphNames']:
 assert poly.count(cls+'.select(pc, s)')==1
assert poly.index('RankingClassPolymorphNames.select(')<poly.index('MythicClassPolymorphNames.select(')<poly.index('AlternateRankingPolymorphNames.select(')
assert poly.index('AlternateRankingPolymorphNames.select(')<poly.index('RankUserLoader.getInstance().isRankPoly(')

bless=(F/'BlessingConsumables.java').read_text('utf-8')
assert bless.count('AinBlessingGemConsumables.useGemCase(')==1
assert 'AinBlessingItems.useGemCase(' not in bless
receipt=(F/'AinBlessingItems.java').read_text('utf-8')
prior=(D/'AinBlessingItems.java.baseline').read_text('utf-8')
assert receipt[receipt.index('public static void consume('):receipt.index('\n\t// Preserve')]==prior[prior.index('public static void consume('):prior.index('\n\t// Preserve')]
assert receipt[receipt.index('private static boolean atMaximum('):]==prior[prior.index('private static boolean atMaximum('):]
assert len(M['new_classes'])==15 and len(M['modified_classes'])==5
for n in M['new_classes']:assert (F/(n+'.java')).exists()
for p in R.glob('src/**/*.java'):
 if p.name in [n+'.java' for n in M['new_classes']]: assert p.parent==F
print('PASS Phase16: 15 extracted method/branch bodies exact, order and original SQL/receipt unchanged')
