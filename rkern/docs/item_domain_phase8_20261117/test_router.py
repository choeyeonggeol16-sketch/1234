#!/usr/bin/env python3
"""Compile the actual Java 21 AdvancedAbilityItems dispatcher with typed delegating stubs."""
from pathlib import Path
import re,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[2]
F=ROOT/'src/l1j/server/server/model/item/function'
s=(F/'AdvancedAbilityItems.java').read_text('utf8')
entries=[]
for m in re.finditer(r'(?m)^\s*case (.+?) -> ([\w]+)\.(\w+)\((.+?)\);\s*$',s):
    labels=[int(x.strip()) for x in m.group(1).split(',')]
    for id in labels:entries.append((id,m.group(2),m.group(3)))
assert len(entries)==len({x[0] for x in entries}), 'duplicate item ID'
classes={name for _,name,_ in entries}
with tempfile.TemporaryDirectory(prefix='rkern_phase8_router_') as d:
    d=Path(d)
    srcdir=d/'l1j/server/server/model/item/function';srcdir.mkdir(parents=True)
    for p,n in [('l1j/server/server/model/Instance','L1PcInstance'),('l1j/server/server/model/Instance','L1ItemInstance')]:
        folder=d/p;folder.mkdir(parents=True,exist_ok=True)
        (folder/(n+'.java')).write_text(f'package {p.replace("/", ".")}; public final class {n} '+'{}\n','utf8')
    args={
     'PassiveSkillBooks':'L1PcInstance pc,L1ItemInstance item,int id',
     'SkillLearningItems':'L1PcInstance pc,L1ItemInstance item,int id',
     'DurableSkillItems':'L1PcInstance pc,L1ItemInstance item',
     'ImmuneBladeSkillItems':'L1PcInstance pc',
    }
    for name in classes:
        method='UseImmuneBlade' if name=='ImmuneBladeSkillItems' else 'usePotentialScroll' if name=='DurableSkillItems' else 'use'
        sig=args.get(name,'L1PcInstance pc')
        (srcdir/(name+'.java')).write_text('package l1j.server.server.model.item.function;\nimport l1j.server.server.model.Instance.*;\n'
            +f'final class {name} '+'{ '+f'static void {method}({sig}) '+'{ Trace.last="'+name+'"; } }\n','utf8')
    (srcdir/'Trace.java').write_text('package l1j.server.server.model.item.function; final class Trace { static String last; }','utf8')
    lines=['package l1j.server.server.model.item.function;',
      'import l1j.server.server.model.Instance.*;',
      'public final class TestRouter { public static void main(String[] a) {',
      'L1PcInstance pc=new L1PcInstance(); L1ItemInstance it=new L1ItemInstance();']
    for id,cls,_ in entries:
        lines.append(f'Trace.last=null; AdvancedAbilityItems.use(pc,it,{id}); if(!"{cls}".equals(Trace.last)) throw new AssertionError("{id}:"+Trace.last);')
    for id in [0,1,-1,2147483647,70703,70742,880801]:
        if id not in {e[0] for e in entries}:
            lines.append(f'Trace.last=null;AdvancedAbilityItems.use(pc,it,{id}); if(Trace.last!=null) throw new AssertionError("unknown {id}");')
    lines+=['System.out.println("PASS phase8 Java21 typed AdvancedAbilityItems dispatch: '+str(len(entries))+' original item IDs and unknown IDs");','}}']
    (srcdir/'TestRouter.java').write_text('\n'.join(lines)+'\n','utf8')
    javac=['javac','-encoding','UTF-8','-d',str(d),str(F/'AdvancedAbilityItems.java'),*[str(p) for p in d.rglob('*.java')]]
    r=subprocess.run(javac,text=True,capture_output=True)
    assert r.returncode==0,r.stdout+r.stderr
    r=subprocess.run(['java','-cp',str(d),'l1j.server.server.model.item.function.TestRouter'],text=True,capture_output=True)
    assert r.returncode==0,r.stdout+r.stderr
    print(r.stdout.strip())
