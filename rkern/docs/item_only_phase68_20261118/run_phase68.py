#!/usr/bin/env python3
"""Run baseline-vs-current typed production-method tests on Java 21."""
from pathlib import Path
import shutil, subprocess, tempfile
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
REAL=ROOT/'src/l1j/server/server'
def run(cmd):
    subprocess.run(cmd,check=True)
def generate(srcfile,dst,name):
    code=srcfile.read_text('utf-8')
    original=srcfile.stem
    if original == 'ConsignmentItemActions':
        code=code.replace('ConsignmentItemActions',name)
    else:
        code=code.replace(original,name)
    dst.parent.mkdir(parents=True,exist_ok=True)
    dst.write_text(code,'utf-8')
with tempfile.TemporaryDirectory(prefix='rkern_item_p68_') as tmp:
    p=Path(tmp)
    aggregate=p/'aggregate'
    shutil.copytree(HERE/'fixture_aggregate',aggregate)
    for short,rel,old,new in [
        ('ClanRaidBossSelection.java','model/item/function','ClanRaidBossSelectionBaseline','ClanRaidBossSelectionCandidate'),
        ('ItemEffectRegistry.java','model/item','ItemEffectRegistryBaseline','ItemEffectRegistryCandidate'),
    ]:
        base=aggregate/'l1j/server/server'/rel
        generate(HERE/'baseline'/short,base/(old+'.java'),old)
        generate(REAL/rel/short,base/(new+'.java'),new)
    classes=p/'agg_classes';classes.mkdir()
    run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),
         *[str(x) for x in aggregate.rglob('*.java')]])
    for main in ['l1j.server.server.model.item.function.CompareBoss',
                 'l1j.server.server.model.item.CompareRegistry',
                 'l1j.server.server.model.item.MeasureRegistry']:
        run(['java','-cp',str(classes),main])
    consignment=p/'consignment'
    shutil.copytree(HERE/'fixture_consignment',consignment)
    rel='model/item/function'
    dst=consignment/'l1j/server/server'/rel
    generate(HERE/'baseline/ConsignmentItemActions.java',dst/'BaselineConsignmentItemActions.java','BaselineConsignmentItemActions')
    generate(REAL/rel/'ConsignmentItemActions.java',dst/'CandidateConsignmentItemActions.java','CandidateConsignmentItemActions')
    cclasses=p/'consignment_classes';cclasses.mkdir()
    run(['javac','--release','21','-encoding','UTF-8','-d',str(cclasses),
         *[str(x) for x in consignment.rglob('*.java')]])
    run(['java','-cp',str(cclasses),'l1j.server.server.model.item.function.CompareConsignment'])
print('Phase68 typed/behavior comparisons: PASS')
