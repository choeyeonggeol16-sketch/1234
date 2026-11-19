from pathlib import Path
import re,collections
root=Path(__file__).resolve().parents[2]/'src'
D=root/'l1j/server/server/model/item/function'
names=['ItemActionDispatcher','SupplementalItemActions','WorldItemActions','SupplementalCraftRouter','SupplementalRewardRouter','AdvancedAbilityItems','TravelAndRecoveryItems','ItemTargetDecoder']
total=0
for name in names:
    s=(D/(name+'.java')).read_text(); s=re.sub(r'/\*.*?\*/|//[^\n]*','',s,flags=re.S); vals=[]; unresolved=[]
    for block in re.findall(r'\bcase\s+([^:\-]+?)(?:->|:)',s):
        for lit in block.split(','):
            lit=lit.strip()
            if lit.isdecimal(): vals.append(int(lit));continue
            if re.fullmatch(r'\w+\.\w+',lit):
                clazz,c=lit.split('.'); p=next(iter(root.rglob(clazz+'.java')),None)
                v=re.search(r'\b'+re.escape(c)+r'\s*=\s*(\d+)\s*;',p.read_text()) if p else None
                if v: vals.append(int(v.group(1)));continue
            unresolved.append(lit)
    duplicate=[(v,n) for v,n in collections.Counter(vals).items() if n>1]
    assert not duplicate and not unresolved,(name,duplicate,unresolved)
    total+=len(vals)
    print(name,'resolved',len(vals),'duplicate',duplicate,'unresolved',unresolved)
print('CASE_LABELS_TOTAL',total)
