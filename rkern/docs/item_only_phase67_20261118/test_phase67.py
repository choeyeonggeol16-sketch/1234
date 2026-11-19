#!/usr/bin/env python3
"""Compile original 66 and 67 production methods against identical minimal Java21 game stubs."""
from pathlib import Path
import subprocess, tempfile, shutil, sys
here=Path(__file__).resolve().parent
root=here.parent.parent
names={'L1CardMyPast':'Past','L1CardMythList':'Myth','L1CardLegendList':'Legend'}
with tempfile.TemporaryDirectory(prefix='rkern_p67_') as t:
    tmp=Path(t)
    shutil.copytree(here/'fixtures',tmp/'source')
    for origin,suffix in names.items():
        for old in (True,False):
            s=(here/'baseline'/f'{origin}.java').read_text() if old else (root/'src/l1j/server/server/model/item/Card'/f'{origin}.java').read_text()
            renamed=('Old' if old else 'New')+suffix
            signature=f'public class {origin} {{'
            assert s.count(signature)==1, signature
            s=s.replace(signature,f'public class {renamed} {{')
            # Test-only deterministic RNG; production retains Collections.shuffle(List).
            if origin=='L1CardMyPast':
                assert s.count('Collections.shuffle(eligibleCards);')==1
                s=s.replace('Collections.shuffle(eligibleCards);','Collections.shuffle(eligibleCards, new java.util.Random(Trace.seed));')
            dst=tmp/'source/l1j/server/server/model/item/Card'/f'{renamed}.java'
            dst.write_text(s)
    sources=list((tmp/'source').rglob('*.java'))
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(tmp/'classes'),*[str(p) for p in sources]],check=True)
    subprocess.run(['java','-cp',str(tmp/'classes'),'l1j.server.server.model.item.Card.RunPhase67'],check=True)
    if '--bench' in sys.argv:
        subprocess.run(['java','-Xms256m','-Xmx256m','-cp',str(tmp/'classes'),
                        'l1j.server.server.model.item.Card.BenchPhase67'],check=True)
