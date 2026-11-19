#!/usr/bin/env python3
from pathlib import Path
import re, tempfile, subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE=Path(__file__).resolve().parent/'baseline'
NEW=ROOT/'src/l1j/server/server/model/item/function'
PAT=re.compile(r'String\.format\("((?:[^"\\]|\\.)*?)%d((?:[^"\\]|\\.)*?)",\s*([A-Za-z_]\w*)\)')
rows=[]
for p in NEW.glob('*.java'):
 oldpath=BASE/(p.name+'.baseline')
 if not oldpath.is_file():continue
 old=oldpath.read_text('utf8')
 for m in PAT.finditer(old):
  prefix,suffix,var=m.groups()
  if '%' in prefix or '%' in suffix:continue
  replaced=f'"{prefix}" + {var} + "{suffix}"'
  if replaced in p.read_text():rows.append((prefix,suffix))
assert len(rows)==22,len(rows)
statements=[]
for idx,(a,b) in enumerate(rows):
 old=f'String.format("{a}%d{b}", n)'
 new=f'"{a}" + n + "{b}"'
 statements.append(f'if(!({old}).equals({new})) throw new AssertionError("{idx}/"+n);')
source='''import java.util.*;
public class Formatting19 {public static void main(String[] args) {
int n=0; int trials=0;
Locale[] loc={Locale.KOREAN,Locale.US,Locale.JAPAN,Locale.ROOT};
int[] values={Integer.MIN_VALUE,-99999,-100,-10,-1,0,1,7,9,10,60,100,125,2147483647};
for(Locale locale:loc){Locale.setDefault(locale);for(int v:values){n=v;
'''+ '\n'.join(statements)+'''\n trials += 22;}}
System.out.println("PASS formatted messages: "+trials+" exact Korean/US/Japan/ROOT string comparisons");
}}'''
with tempfile.TemporaryDirectory(prefix='format19_') as d:
 p=Path(d)/'Formatting19.java';p.write_text(source,encoding='utf8')
 for cmd in (['javac','-encoding','UTF-8','--release','21',str(p)],['java','-cp',d,'Formatting19']):
  x=subprocess.run(cmd,capture_output=True,text=True)
  if x.returncode:raise RuntimeError(x.stdout+x.stderr)
 print(x.stdout.strip())
