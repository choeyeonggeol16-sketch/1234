#!/usr/bin/env python3
"""Compare Phase16 method bytes with Phase17 handlers, except owner-qualified calls."""
from pathlib import Path
import json,re,hashlib
DOC=Path(__file__).resolve().parent
ROOT=DOC.parents[1]
F=ROOT/'src/l1j/server/server/model/item/function'
M=json.loads((DOC/'MANIFEST.json').read_text('utf8'))
sha=lambda x:hashlib.sha256(x.encode('utf8')).hexdigest()
old={n:(DOC/(n+'.baseline.java')).read_text('utf8') for n in ['DurableSkillItems','ResolventTransactions','CreatureItemActions','ItemActionDispatcher','ItemDissolutionScrolls','ResolventItemFacade','AdvancedAbilityItems','ItemLearningRule']}
def method(s,name):
 pattern=r'(?m)^[ \t]*(?:(?:public|protected|private)[ \t]+)?static[ \t]+(?:void|boolean|int|long|String|Runnable)[ \t]+'+re.escape(name)+r'[ \t]*\('
 m=re.search(pattern,s);assert m,(name,'method absent')
 b=s.index('{',m.end());depth=0;i=b;state='code'
 while i<len(s):
  c=s[i];nx=s[i+1] if i+1<len(s) else ''
  if state=='code':
   if c=='"':state='str'
   elif c=="'":state='char'
   elif c=='/' and nx=='/':state='line';i+=1
   elif c=='/' and nx=='*':state='block';i+=1
   elif c=='{':depth+=1
   elif c=='}':
    depth-=1
    if depth==0:return s[m.start():i+1],s[b+1:i+1]
  elif state in ('str','char'):
   if c=='\\':i+=1
   elif c==('"' if state=='str' else "'"):state='code'
  elif state=='line':
   if c=='\n':state='code'
  elif state=='block':
   if c=='*' and nx=='/':i+=1;state='code'
  i+=1
 raise AssertionError('unclosed')
originals={'DurableSkillItems':old['DurableSkillItems'],'ResolventTransactions':old['ResolventTransactions'],'CreatureItemActions':old['CreatureItemActions']}
for record in M['extracted']:
 src=originals[record['from']];dest=(F/(record['to']+'.java')).read_text('utf8')
 a,body=method(src,record['method']);b,_=method(dest,record['method'])
 assert sha(a)==record['original_sha256'] and sha(body)==record['original_body_sha256'],record
 if record['method']=='usePotentialScroll':
  a=a.replace('useDurablePotentialScroll(pc, item, skill, notice)','PotentialScrollTransaction.useDurablePotentialScroll(pc, item, skill, notice)')
 elif record['method']=='useDurablePotentialScroll':
  a=a.replace('applyPotentialSkill(pc, newSkill)','PotentialSkillEffects.applyPotentialSkill(pc, newSkill)')
 elif record['method']=='applyPotentialSkill':
  a=a.replace('private static void applyPotentialSkill','static void applyPotentialSkill',1)
 elif record['from']=='ResolventTransactions' and record['method'] in ['useResolvent','useResolventRoll','useResolventCount','useDragonResolvent']:
  a=a.replace('validResolventPair(', 'ResolventPairRules.validResolventPair(').replace('settleResolvent(', 'ResolventSettlement.settleResolvent(')
 assert a==b,('method mismatch',record['from'],record['method'])
# Exact static array and SQL mappings, no new per-event allocations.
s=old['DurableSkillItems'];t=s[s.index('    // Tier probabilities'):s.index('    static void usePotentialScroll')]
a=t.index('    private static final int[] POTENTIAL_CLEAR_SKILLS')
assert t[:a] in (F/'PotentialScrollSelection.java').read_text('utf8')
assert t[a:] in (F/'PotentialScrollTransaction.java').read_text('utf8')
# Existing 16th-phase dispatcher switch remains intact; only four static-import owners changed.
new=(F/'ItemActionDispatcher.java').read_text('utf8')
for n,cls in [('정상의가호','TopRankerBlessingItems'),('정상의가호2','TopRankerBlessingItems'),('useFieldObjectRemovalWand','NpcRemovalWandItems'),('use_bug_down_wand','BugRaceControlWandItems')]:
 new=new.replace(f'import static l1j.server.server.model.item.function.{cls}.{n};',f'import static l1j.server.server.model.item.function.CreatureItemActions.{n};')
assert new==old['ItemActionDispatcher'],'dispatcher changed beyond static imports'
for name in ['ItemDissolutionScrolls','ResolventItemFacade','AdvancedAbilityItems','ItemLearningRule']:
 s=(F/(name+'.java')).read_text('utf8')
 s=s.replace('OrdinaryResolventItems.', 'ResolventTransactions.').replace('DragonResolventItems.', 'ResolventTransactions.')
 s=s.replace('PotentialScrollSelection.', 'DurableSkillItems.').replace('DurablePassiveLearningTransaction.', 'DurableSkillItems.')
 assert s==old[name],('unexpected caller changes',name)
for n,cls in [('useDurableSkillReagent','DurableSkillReagentTransaction'),('useDurablePassive','DurablePassiveLearningTransaction'),('usePotentialScroll','PotentialScrollSelection'),('useDurablePotentialScroll','PotentialScrollTransaction')]:
 assert not any('DurableSkillItems.'+n+'(' in p.read_text('utf8') for p in F.glob('*.java'))
for p in F.glob('*.java'):
 txt=p.read_text('utf8')
 assert 'ResolventTransactions.' not in txt and 'DurableSkillItems.' not in txt,p
assert not (F/'ResolventTransactions.java').exists()
assert not (F/'DurableSkillItems.java').exists()
assert (F/'CreatureItemActions.java').read_text('utf8').count('public static boolean createNewItem(')==1
print('Phase17 structure: exact 18 method sources, potential tier/SQL arrays, dispatcher and caller wiring PASS')
