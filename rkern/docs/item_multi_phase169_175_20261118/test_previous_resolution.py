#!/usr/bin/env python3
"""Adapt earlier 168 fixture to the newly added method's ownership-check dependency.
Production source is unchanged; fixture gets one missing stub class."""
from pathlib import Path
import tempfile,subprocess,sys
project=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parents[2]
base=project/'docs/item_multi_phase164_168_20261118/test_atomic_enchant_resolution.py'
s=base.read_text()
needle='    modified=original.replace(marker,stubcalls+marker,1)'
assert needle in s
addon='''    stubcalls += "\\n add(base+'SpecialEnchantScrolls.java',"+repr('''+repr('''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public class SpecialEnchantScrolls {
 static boolean isOwnedScrollTarget(L1PcInstance p,L1ItemInstance s,L1ItemInstance t){
 return p!=null && s!=null && t!=null && s!=t && s.count>0 && t.count>0;
 }}''')+''')+")\\n"
'''
s=s.replace(needle,addon+needle)
with tempfile.NamedTemporaryFile('w',suffix='.py',delete=False,encoding='utf8') as f:
 f.write(s);p=Path(f.name)
try:
 c=subprocess.run([sys.executable,str(p),str(project)],capture_output=True,text=True,timeout=90)
 if c.returncode:raise SystemExit(c.stdout+c.stderr)
 print(c.stdout.strip())
finally:p.unlink()
