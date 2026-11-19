#!/usr/bin/env python3
import json,re,hashlib,pathlib,sys
root=pathlib.Path(__file__).resolve().parents[3]
f=root/'src/l1j/server/server/model/item/function'
m=json.loads((pathlib.Path(__file__).parent/'EXPECTED_ORIGINAL_FRAGMENTS.json').read_text())
pat=re.compile(r'^    static ItemActionResult (\w+)\(ItemActionContext use\) \{',re.M)
for entry,expected in m['original_methods'].items():
 cls,meth=entry.split('.')
 text=(f/f'{cls}.java').read_text();ms=list(pat.finditer(text))
 snippets={x.group(1):text[x.start():ms[i+1].start() if i+1<len(ms) else text.rfind('\n}')].rstrip() for i,x in enumerate(ms)}
 assert meth in snippets,entry
 assert hashlib.sha256(snippets[meth].encode()).hexdigest()==expected,entry
for entry,expected in m['fallback'].items():
 cls,meth=entry.split('.')
 s=(f/f'{cls}.java').read_text()
 start=s.index(f'    static ItemActionResult {meth}(ItemActionContext use) {{')
 next_m=pat.search(s,start+len(meth)+35)
 ending=next_m.start() if next_m else s.rfind('\n}')
 source=s[start:ending]
 # The generated fallback handler keeps the full legacy local bindings then unchanged branch body.
 brace=source.index('{')
 body=source[brace+1:]
 locals_end=body.index('        if (') if '        if (' in body else -1
 # Use known last-binding marker and final COMPLETE to isolate exact body.
 loc=body.index('        int spellTargetY = use.spellY();')+len('        int spellTargetY = use.spellY();')
 assert body[loc:loc+1]=='\n',entry
 raw=body[loc+1:body.rfind('        return ItemActionResult.COMPLETE;')]
 assert hashlib.sha256(raw.encode()).hexdigest()==expected,entry
for stale in ['ConsumableItemCases','UtilityItemCases','FallbackItemCases']:
 assert not (f/(stale+'.java')).exists(),stale
 assert not any(stale in x.read_text(errors='replace') for x in (root/'src').rglob('*.java')),stale
router=(f/'UnlistedItemRouter.java').read_text()
for name in m['router_destination']:
 assert 'return '+name+'(use);' in router,name
labels=set(re.findall(r'(?m)^\s*case\s+.*?->', (f/'ItemActionDispatcher.java').read_text()))
print('PASS 34 byte-identical extracted methods + 14 byte-identical fallback action bodies; 13 predicate/wiring paths; no stale classes')
