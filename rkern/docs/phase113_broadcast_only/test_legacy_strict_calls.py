#!/usr/bin/env python3
from pathlib import Path
import re
root=Path(__file__).resolve().parents[2]/'src'
strict=('sendWrittenRecipients','sendWrittenArray','sendWrittenObjectPlayers',
        'sendWrittenSelfAndVisible','sendWrittenNestedVisible','sendWrittenMapTeam')
bad=[]
for p in root.rglob('*.java'):
    if p.name=='CSharedBroadcast.java':continue
    code=p.read_text()
    for name in strict:
        pattern=r'CSharedBroadcast\.'+name+r'\s*\('
        if re.search(pattern,code):bad.append((str(p.relative_to(root)),name))
assert not bad, f'Legacy strict visual fanouts still called: {bad}'
print('113 broadcast entrypoint audit PASS: zero strict visual/notice fanout call sites')
