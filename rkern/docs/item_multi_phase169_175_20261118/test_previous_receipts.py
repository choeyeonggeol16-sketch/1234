#!/usr/bin/env python3
"""Earlier receipt/exchange regression, excluding independently tested enchant methods
whose fixture does not model enchant target types."""
from pathlib import Path
import subprocess,tempfile,sys
project=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[2]
src=(project/'docs/item_multi_phase155_158_20261118/check_receipts.py').read_text()
needle=" receipt=(base/'ItemReceiptOperations.java').read_text(encoding='utf8')"
assert needle in src
inject='''
 # Remove newer enchant-specific helpers from this historical receipt fixture;
 # they are compiled and exercised in the dedicated 168 and 169 tests.
 for name in ('    public static void UseAtomicEnchantSuccess(',
              '    public static void UseProtectionEnchant(',
              '    private static void protectionNotice('):
  a=receipt.index(name);b=receipt.index('{',a);depth=0
  for k in range(b,len(receipt)):
   if receipt[k]=='{':depth+=1
   elif receipt[k]=='}':
    depth-=1
    if depth==0:
     receipt=receipt[:a]+receipt[k+1:];break
'''
src=src.replace(needle,needle+inject,1)
with tempfile.NamedTemporaryFile('w',suffix='.py',encoding='utf8',delete=False) as f:
 f.write(src);p=Path(f.name)
try:
 run=subprocess.run([sys.executable,str(p),str(project/'src')],capture_output=True,text=True,timeout=60)
 if run.returncode: raise SystemExit(run.stdout+run.stderr)
 print(run.stdout.strip())
finally:p.unlink()
