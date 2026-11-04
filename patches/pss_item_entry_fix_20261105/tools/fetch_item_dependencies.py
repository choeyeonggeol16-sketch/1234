#!/usr/bin/env python3
"""Fetch pinned development dependencies over verified TLS; never replace user libraries."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--destination',type=Path,default=Path(os.environ.get('ITEM_DEPS_DIR','/workspace/dependencies/item-processing')));args=p.parse_args()
args.destination.mkdir(parents=True,exist_ok=True)
for row in json.loads((root/'tools/item_dependencies.json').read_text()):
 target=args.destination/row['file']
 if target.is_file():
  if hashlib.sha256(target.read_bytes()).hexdigest()!=row['sha256']:raise SystemExit('Existing dependency differs; preserve and inspect: '+str(target))
  print('VERIFIED',target.name);continue
 with tempfile.NamedTemporaryFile(dir=args.destination,suffix='.part',delete=False) as f:temporary=Path(f.name)
 try:
  subprocess.run(['curl','-fsS','--connect-timeout','15','--max-time','120','-o',str(temporary),row['url']],check=True)
  if hashlib.sha256(temporary.read_bytes()).hexdigest()!=row['sha256']:raise SystemExit('Checksum mismatch: '+target.name)
  temporary.replace(target);print('INSTALLED',target.name)
 finally:temporary.unlink(missing_ok=True)
