from pathlib import Path
import json,re,hashlib
ROOT=Path(__file__).resolve().parents[2]
DOC=Path(__file__).resolve().parent
F=ROOT/'src/l1j/server/server/model/item/function'
M=json.loads((DOC/'SOURCE_METHODS.json').read_text('utf8'))
S=json.loads((DOC/'SWITCH_MAP.json').read_text('utf8'))
H=json.loads((DOC/'CLASS_SHA256.json').read_text('utf8'))
for cls,h in H.items():
 p=F/(cls+'.java')
 assert p.is_file(),p
 assert hashlib.sha256(p.read_bytes()).hexdigest()==h,p
for old in ['DurableItemActions','WorldItemHelpers','CraftAndBuffItems','ConsumableRewardItems']:
 assert not (F/(old+'.java')).exists(),old
for p in (ROOT/'src').rglob('*.java'):
 text=p.read_text('utf8')
 assert not re.search(r'\b(DurableItemActions|WorldItemHelpers|CraftAndBuffItems|ConsumableRewardItems)\b',text),p
routes={'world':'WorldItemActions','travel':'TravelAndRecoveryItems','craft':'SupplementalCraftRouter','rewards':'SupplementalRewardRouter'}
for domain,name in routes.items():
 recs=S[domain]['groups']
 route=(F/(name+'.java')).read_text('utf8')
 ids=[str(k) for r in recs for k in r['ids']]
 assert sorted(ids)==sorted(re.findall(r'(?m)^[ \t]*case\s+([^:\n]+):',route)),domain
 for r in recs: assert (F/(r['class']+'.java')).exists(),r['class']
print('PASS 4 domain routers, 227 switch IDs, 180 case groups, method class identities and stale name scan')
