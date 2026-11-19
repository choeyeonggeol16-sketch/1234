from pathlib import Path
import re
root=Path(__file__).resolve().parents[2]/'src/l1j/server/server'
d=root/'model/item/function'
def cases(p):
    s=re.sub(r'/\*.*?\*/|//[^\n]*','',p.read_text(),flags=re.S)
    return {int(v.strip()) for g in re.findall(r'\bcase\s+([^:\-]+?)(?:->|:)',s) for v in g.split(',') if v.strip().isdecimal()}
s=(root/'utils/SupplementalItemIds.java').read_text().split('ITEM_IDS = {')[1].split('};')[0]
a=set(map(int,re.findall(r'\b\d+\b',s)));b=cases(d/'SupplementalItemActions.java');c=cases(d/'ItemActionDispatcher.java')
only=a-b; omitted=b-a; overlaps=a&c
aliases={i for i in only if 3000000+(i-300000) in b and 300000<=i<301000}
print('registry_only',len(only),sorted(only));print('possible_missing_zero',len(aliases),sorted(aliases));print('dispatch_only',sorted(omitted));print('main_overlaps',sorted(overlaps))
assert len(only)==53 and len(aliases)==40 and omitted=={3000147} and overlaps=={41019,41303,41304}
assert not only&c
print('Unverified IDs intentionally unchanged; no new activation or remapping.')
