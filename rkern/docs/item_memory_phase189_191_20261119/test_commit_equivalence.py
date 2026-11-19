#!/usr/bin/env python3
"""Java 21 typed execution of actual phase188 vs new commitInternal under SQL and replay failures."""
from pathlib import Path
import subprocess, tempfile
base = (Path(__file__).resolve().parent/'baseline_188_L1InventoryTransaction.java').read_text('utf-8')
new = (Path(__file__).resolve().parents[2]/'src/l1j/server/server/model/L1InventoryTransaction.java').read_text('utf-8')
def extract(s, sig):
 a=s.index(sig);b=s.index('{',a);d=1;i=b+1
 while d:
  if s[i]=='{':d+=1
  elif s[i]=='}':d-=1
  i+=1
 return s[a:i].replace('private void commitInternal','void commitInternal',1)
source=(Path(__file__).resolve().parent.parent/'item_memory_phase185_188_20261119/test_commit_singleton.py').read_text('utf-8')
java=source[source.index("java='''")+len("java='''"):source.rindex("'''\njava=java.replace")]
java=java.replace('static final class Legacy extends Base { %OLD% }','static final class Legacy extends Base { Object commitEntries; %OLD% }')
java=java.replace('if(this instanceof Optimized optimized)optimized.entries=count==1?entries[0]:entries;',
'''if(this instanceof Optimized optimized)optimized.entries=count==1?entries[0]:entries;
      if(this instanceof Legacy legacy)legacy.commitEntries=count==1?entries[0]:entries;''')
old=extract(base,'    private void commitInternal(Runnable afterCommitted, boolean releaseBeforeEffects)')
new=extract(new,'    private void commitInternal(Runnable afterCommitted, boolean releaseBeforeEffects)')
import re
old=re.sub(r'\bentries\b','commitEntries',old)
java=java.replace('%OLD%',old).replace('%NEW%',new)
# Modify expected stage counters comparing 188 to current; same.
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'CommitPathCheck.java';p.write_text(java)
 a=subprocess.run(['javac','--release','21',str(p)],capture_output=True,text=True)
 if a.returncode:print(a.stderr);raise SystemExit(a.returncode)
 b=subprocess.run(['java','-ea','-cp',d,'CommitPathCheck'],capture_output=True,text=True,timeout=60)
 print(b.stdout,b.stderr);raise SystemExit(b.returncode)
