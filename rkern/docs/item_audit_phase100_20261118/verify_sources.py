#!/usr/bin/env python3
"""Self-contained source and scope check for phase 1-100 audit fix."""
from pathlib import Path
import re,zipfile,hashlib
ROOT=Path(__file__).resolve().parents[2]
S=ROOT/'src'
files=list(S.rglob('*.java'))
assert len(files)==1922,len(files)
classes={str(p.relative_to(S))[:-5].replace('/','.') for p in files}
unresolved=[]
for p in files:
 data=p.read_text(encoding='utf-8-sig',errors='replace')
 for static,imp in re.findall(r'^\s*import\s+(static\s+)?(l1j\.[\w\.\*]+)\s*;',data,re.M):
  if static:imp='.'.join(imp.split('.')[:-1])
  elif imp.endswith('.*'):continue
  if imp not in classes and not any(imp.startswith(c+'.') for c in classes):unresolved.append((str(p.relative_to(S)),imp))
assert not unresolved,unresolved[:20]
carddir=S/'l1j/server/server/model/item/Card'
account=(S/'l1j/server/server/Account.java').read_text()
for cls,base,count in [('L1CardAlchemy1.java',101,34),('L1CardAlchemy2.java',1,36),('L1CardAlchemy3.java',201,11)]:
 text=(carddir/cls).read_text()
 consumers={base+int(n):name for n,name in re.findall(r'_cardConsumers\[(\d+)\]\s*=\s*acc\s*->\s*acc\.set\w+\(acc\.(get\w+)\(\)\s*-\s*1\)',text)}
 stock={int(n):name for n,name in re.findall(r'case (\d+) -> acc\.(get\w+)\(\);',text)}
 assert len(consumers)==count and stock==consumers,(cls,stock,consumers)
 for name in stock.values(): assert re.search(r'\b'+name+r'\s*\(',account),(cls,name)
 assert 'copiesFor(acc' in text and 'cardA == cardB' in text
warrior=(S/'l1j/server/server/model/item/function/WarriorSkillBookItems.java').read_text()
assert warrior.index('if (skill == null)') < warrior.index('pc.getInventory().removeItem(item, 1)')
print('PASS: 1922 Java files, no unresolved explicit project imports, 81 stock mappings exactly match debit getters; missing warrior template fail-closed')
