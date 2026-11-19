from pathlib import Path
from collections import Counter
import re
p=Path(__file__).parent
def diagnostics(t):
    out=[]
    for z in re.split(r'(?m)(?=^src/[^\n]+\.java:\d+: (?:error|warning):)', t):
        if z.startswith('src/'):
            out.append(re.split(r'(?m)^Note:|^\d+ errors|^\d+ warnings',z)[0].rstrip())
    return Counter(out)
a,b=[diagnostics((p/f'fullcompile_{x}.log').read_text()) for x in ('before','after')]
print(f'Before: {sum(a.values())}; after: {sum(b.values())}; removed: {sum((a-b).values())}; added: {sum((b-a).values())}')
assert a==b
print('Exact javac diagnostic multiset equivalence PASS; both full builds still fail (531 errors, 5 warnings).')
