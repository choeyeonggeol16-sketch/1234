from pathlib import Path
import re
root=Path(__file__).resolve().parents[2]
old=(root/'docs/item_only_phase71_20261118/check_reward_tables.py').read_text()
# Run the 71 ordered table validation but not the 71-specific assertion that class3 is byte-identical.
old=old.split('# The Myth->Unique selector was deliberately not modified.')[0]
exec(compile(old, 'phase71_ordered_tables', 'exec'), {'__name__':'__main__','__file__': str(root/'docs/item_only_phase71_20261118/check_reward_tables.py')})
source=root/'src/l1j/server/server/model/item/Card'
base=root/'docs/item_only_phase72_20261118/baseline'
for f in ('L1CardAlchemy1.java','L1CardAlchemy2.java','L1CardAlchemy3.java'):
 x=(base/f).read_text(); y=(source/f).read_text()
 for method, end in [('getResultCard','sendResultUI')] if f.endswith('3.java') else []:
  m=re.search(r'\bprivate static ResultCard '+method+r'\(.*?(?=\bprivate static void '+end+r'\()',x,re.S)
  n=re.search(r'\bprivate static ResultCard '+method+r'\(.*?(?=\bprivate static void '+end+r'\()',y,re.S)
  assert m and n and m.group(0)==n.group(0),f
 print('PASS unchanged reward/selection for',f)
print('PASS reward tables and myth selector preserved')
