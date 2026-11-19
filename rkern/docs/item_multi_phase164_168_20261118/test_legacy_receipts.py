#!/usr/bin/env python3
"""Previous receipt/exchange regression on production source with new unrelated enchant helper excluded.
The enchant helper itself is exercised with typed mocks by test_atomic_enchant_receipt.py.
"""
import tempfile,subprocess,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve()
s=(root/'docs/item_multi_phase155_158_20261118/check_receipts.py').read_text()
needle=" receipt=(base/'ItemReceiptOperations.java').read_text(encoding='utf8')"
assert needle in s
transform='''
 # The historical fixture has no enchant-target or CIOClient stubs. Only its
 # old exchange/consume methods are compiled here; the new method has its own
 # separate dedicated production-method typed test.
 name='    public static void UseAtomicEnchantSuccess('
 a=receipt.index(name); b=receipt.index('{',a);depth=0
 for k in range(b,len(receipt)):
  if receipt[k]=='{':depth+=1
  elif receipt[k]=='}':
   depth-=1
   if depth==0:
    receipt=receipt[:a]+receipt[k+1:];break
'''
s=s.replace(needle,needle+transform,1)
with tempfile.NamedTemporaryFile('w',suffix='.py',encoding='utf8',delete=False) as f:
 f.write(s);p=Path(f.name)
try:
 c=subprocess.run([sys.executable,str(p),str(root/'src')],capture_output=True,text=True,timeout=65)
 if c.returncode:raise RuntimeError(c.stdout+c.stderr)
 print(c.stdout.strip())
finally:p.unlink()
