#!/usr/bin/env python3
"""Production-guard extraction, Java 21 execution and preserved previous regressions.
No MySQL / packet protocol server is mocked as a full integration test.
"""
from pathlib import Path
import re, subprocess, sys, tempfile
W=Path(__file__).resolve().parents[2]
SRC=W/'src/l1j/server/server'
sets={
 'NeigeCard.java':(['rnlpolyfir','rnlpolyph','rnlpolysh'], ['rnlpolych','rnalce']),
 'NeigeCard2.java':(['rnllfir','rnllpl','rnllsh'],['rnllch','rnllsce']),
 'NeigeCard3.java':(['rnmyfir','rnmypl','rnmysh'],['rnmych','rnmysce']),
}
checks=0
with tempfile.TemporaryDirectory(prefix='item105_') as d:
 root=Path(d)
 java=['public class CardSelection105 {','  static final class Pc {boolean busy; boolean isItemTransferBusy(){return busy;}}']
 for name,(exacts,prefixes) in sets.items():
  s=(SRC/name).read_text(encoding='utf-8')
  match=re.search(r'if \(pc\.isItemTransferBusy\(\) && s2 != null\s*&& \((.*?)\)\) return true;',s,re.S)
  assert match is not None, name
  expr=match.group(1)
  assert set(re.findall(r's2\.equals\("([^"]+)"\)',expr))==set(exacts),name
  assert set(re.findall(r's2\.startsWith\("([^"]+)"\)',expr))==set(prefixes),name
  # A new guard must not match incidental morph or UI pages.
  code='if (pc.isItemTransferBusy() && s2 != null && ('+expr+')) return true;'
  m={'NeigeCard.java':'hero','NeigeCard2.java':'legend','NeigeCard3.java':'myth'}[name]
  java.append(f'  static boolean {m}(Pc pc,String s2) {{ {code} return false; }}')
  # Existing action cases are the exact validation population, including non-synthesis actions.
  cases=re.findall(r'\bcase "([^"]+)":',s)
  assert len(cases)>16
  guarded=[v for v in cases if v in exacts or any(v.startswith(p) for p in prefixes)]
  assert len(guarded)>=18,(name,len(guarded))
  print(name,'actions',len(cases),'synthesis_controls',len(guarded))
  for val in cases:
   exp=val in exacts or any(val.startswith(p) for p in prefixes)
   quoted='"'+val+'"'
   java.append(f'  static {{ for(int busy=0;busy<=1;busy++) verify("{m}",{quoted},busy==1,{str(exp).lower()}); }}')
   checks+=2
 # Java static test assertions for busy, idle, NULL, accidental overlapping prefix
 java.append('''  static int checked;
  static void verify(String kind, String action, boolean busy, boolean expected) {
    Pc pc=new Pc();pc.busy=busy;
    boolean blocked=switch(kind){case "hero"->hero(pc,action);case "legend"->legend(pc,action);default->myth(pc,action);};
    checked++;
    if(blocked!=(busy&&expected))throw new AssertionError(kind+":"+action+" busy="+busy);
  }
  public static void main(String[] ignored) {
    Pc pc=new Pc();pc.busy=true;
    if(hero(pc,null)||legend(pc,null)||myth(pc,null))throw new AssertionError("null guard");
    if(hero(pc,"rnlpolyshm00")||legend(pc,"rnlll01")||myth(pc,"rnmyl02"))throw new AssertionError("read/morph blocked");
    if(!hero(pc,"rnalce77")||!legend(pc,"rnllsce555")||!myth(pc,"rnmysce777"))throw new AssertionError("unexpected suffix handling");
    System.out.println("PASS 105 Java actual production guard / "+checked+" conditions");
  }
}''')
 (root/'CardSelection105.java').write_text('\n'.join(java))
 r=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(root),str(root/'CardSelection105.java')],capture_output=True,text=True)
 assert r.returncode==0,r.stderr
 r=subprocess.run(['java','-cp',str(root),'CardSelection105'],capture_output=True,text=True)
 assert r.returncode==0,r.stderr+'\n'+r.stdout
 print(r.stdout.strip())
 # The independent central NPC dispatch path can reset the same selected fields.
 action=(SRC/'model/npc/action/ActionControlHandler.java').read_text(encoding='utf-8')
 for k in ['neifirl','neifirmy']:
  assert re.search(rf'_actionMap\.put\("{k}", pc -> \{{ if \(pc\.isItemTransferBusy\(\)\) return true; resetCardInfo\(pc\);',action),k
  checks+=1
 assert re.search(r'void resetCardInfo\(L1PcInstance pc\)\s*\{(.*?)\}', action, re.S)
 reset_block=re.search(r'void resetCardInfo\(L1PcInstance pc\)\s*\{(.*?)\}', action, re.S).group(1)
 assert all(reset_block.count(f'pc.getAccount().setInfo_card_{part}(0);')==1 for part in 'abcd')
 print('PASS 105 central NPC entrypoints guarded: 2')
# Prior exact production registration + answer regression suite.
r=subprocess.run([sys.executable,str(W/'docs/item_only_phase104_20261118/test_104.py')],capture_output=True,text=True,timeout=180)
print('104 regression:', r.stdout[-1250:])
assert r.returncode==0,r.stderr[-2500:]+r.stdout[-2500:]
print('PASS 105 control checks:',checks)
