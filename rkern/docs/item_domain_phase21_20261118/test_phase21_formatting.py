#!/usr/bin/env python3
"""Assert every changed protection-scroll formatter is exactly a one-number string template.
Compile and run all 19 original templates with Korean/US/Japanese/ROOT integer cases.
"""
from pathlib import Path
import subprocess, tempfile, os, re
D=Path(__file__).resolve().parent
P=D.parents[1]/'src/l1j/server/server/model/item/function'
NAMES=['EmblemProtectionScrolls.java','InsigniaProtectionScrolls.java','OrimProtectionScrolls.java','RoomtisProtectionScrolls.java','ShoulderProtectionScrolls.java','SnapperProtectionScrolls.java']
pattern=r'String\.format\("([^"%]*)%d([^"%]*)",\s*([^)]+)\)'
formats=[]
for name in NAMES:
 original=(D/'baseline'/name).read_text('utf8')
 changed=(P/name).read_text('utf8')
 matches=list(re.finditer(pattern,original))
 assert matches,(name,'no matches')
 assert changed.count('String.format(')==0,(name,'old String.format left')
 substitute=original
 for m in matches[::-1]:
  l,r,arg=m.groups()
  assert arg.strip()=='3' or arg.strip().startswith('Config.'),(name,arg)
  repl=f'"{l}" + ({arg}) + "{r}"'
  substitute=substitute[:m.start()]+repl+substitute[m.end():]
  formats.append(f'{l}%d{r}')
 assert substitute==changed,name
assert len(formats)==19,len(formats)
texts=','.join('"'+s.replace('\\','\\\\').replace('"','\\"')+'"' for s in formats)
java=f'''import java.util.*;
public class FormatCases {{
 public static void main(String[] args) {{
 String[] fmt = {{{texts}}};
 int[] nums={{-1000,-1,0,1,2,3,4,5,10,99,1234,Integer.MAX_VALUE}};
 Locale[] locales={{Locale.KOREA,Locale.US,Locale.JAPAN,Locale.ROOT}};
 int count=0;
 for(Locale loc:locales){{Locale.setDefault(loc);
 for(String format:fmt)for(int i:nums){{
 int offset=format.indexOf("%d");
 String before=String.format(format,i);
 String after=format.substring(0,offset)+i+format.substring(offset+2);
 if(!before.equals(after))throw new AssertionError(loc+": "+before+" != "+after);
 count++;
 }}
 }}
 System.out.println("protection_scroll_templates="+fmt.length+" comparisons="+count);
 }}
}}
'''
with tempfile.TemporaryDirectory(prefix='rk21fmt_') as td:
 root=Path(td)
 (root/'FormatCases.java').write_text(java,encoding='utf8')
 env=dict(os.environ, LANG='C.UTF-8',LC_ALL='C.UTF-8')
 c=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',td,str(root/'FormatCases.java')],capture_output=True,text=True,env=env)
 assert c.returncode==0,c.stderr
 r=subprocess.run(['java','-cp',td,'FormatCases'],capture_output=True,text=True,env=env)
 assert r.returncode==0,r.stderr
 print('PASS 19 original+modified source replacements identical,',r.stdout.strip())
