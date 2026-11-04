#!/usr/bin/env python3
"""Compile the complete source tree with verified dependencies and Java 21 preview APIs."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--dependencies',type=Path,default=Path(os.environ.get('ITEM_DEPS_DIR','/workspace/dependencies/item-processing')));p.add_argument('--output',type=Path,default=root/'build/item-processing');args=p.parse_args()
rows=json.loads((root/'tools/item_dependencies.json').read_text())
jars=[]
for row in rows:
 jar=args.dependencies/row['file']
 if not jar.is_file() or hashlib.sha256(jar.read_bytes()).hexdigest()!=row['sha256']:
  raise SystemExit('Dependency absent/checksum mismatch: '+str(jar)+'; run tools/fetch_item_dependencies.py')
 jars.append(str(jar.resolve()))
args.output.mkdir(parents=True,exist_ok=True)
files=sorted((root/'src').rglob('*.java'))
def quote(value):return '"'+str(value).replace('\\','/').replace('"','\\"')+'"'
arguments=['--enable-preview','-source','21','-target','21','-encoding','UTF-8','-proc:none','-classpath',os.pathsep.join(jars),'-d',str((args.output/'classes').resolve())]+[str(f) for f in files]
argfile=args.output/'javac-arguments.txt';argfile.write_text('\n'.join(quote(x) for x in arguments)+'\n')
print('Compiling',len(files),'Java sources',flush=True)
result=subprocess.run(['java','-Xmx2g','-m','jdk.compiler/com.sun.tools.javac.Main','@'+str(argfile.resolve())])
if result.returncode:raise SystemExit(result.returncode)
print('PASS full Java type compilation:',len(files),'sources; Java 21 --enable-preview required at runtime too')
