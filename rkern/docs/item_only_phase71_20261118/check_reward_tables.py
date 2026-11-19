#!/usr/bin/env python3
"""Exact ordered (card ID, title, item ID) table parity, including duplicate weighting."""
from pathlib import Path
import re
DOC=Path(__file__).resolve().parent
P=DOC.parents[1]/'src/l1j/server/server/model/item/Card'

source_re=r'\{\s*"([^"\\]*)"\s*,\s*"([^"\\]*)"\s*,\s*(\d+)\s*\}'
dest_re=r'new ResultCard\("([^"\\]*)",\s*"([^"\\]*)",\s*(\d+)\)'
inputs={
 'L1CardAlchemy1.java':(('maleMyth','MALE_MYTH_CARDS',11),('femaleMyth','FEMALE_MYTH_CARDS',11),('legendPool','LEGEND_CARDS',23)),
 'L1CardAlchemy2.java':(('pool','LEGEND_CARDS',36),('pool','HERO_CARDS',36)),
}
for fn,arrays in inputs.items():
 old=(DOC/'baseline'/fn).read_text()
 new=(P/fn).read_text()
 for i,(var,field,expected) in enumerate(arrays):
  matches=list(re.finditer(r'Object\[\]\[\] '+var+r'\s*=\s*\{(.*?)\n\s*\};',old,re.S))
  src=re.findall(source_re,matches[i].group(1) if fn=='L1CardAlchemy2.java' else matches[0].group(1))
  dst=re.search(r'private static final ResultCard\[\] '+field+r'\s*=\s*\{(.*?)\n\s*\};',new,re.S)
  assert dst,field
  values=re.findall(dest_re,dst.group(1))
  assert len(src)==len(values)==expected,(field,len(src),len(values))
  assert src==values,('ORDER/WEIGHT/REWARD MISMATCH',field)
  print('PASS',field,'entries',expected,'including duplicates')
# The Myth->Unique selector was deliberately not modified.
assert (DOC/'baseline/L1CardAlchemy3.java').read_bytes()==(P/'L1CardAlchemy3.java').read_bytes()
print('PASS myth->unique synthesis left byte-identical; total reward rows=117')
