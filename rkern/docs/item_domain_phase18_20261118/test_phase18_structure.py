#!/usr/bin/env python3
from pathlib import Path
import re
D=Path(__file__).resolve().parent
F=D.parents[1]/'src/l1j/server/server/model/item/function'
B=D/'baseline'
removed=[f'Level{i}PolymorphForm' for i in (30,40,52,55,60,65,70,75,80)]+[f'Level{i}AchievementReward' for i in (82,84,86,88,89,90)]
for n in removed:
    assert not (F/(n+'.java')).exists(),n
    assert all(n not in p.read_text('utf8') for p in F.glob('*.java')),('dangling',n)
for lev in (30,40,52,55,60,65,70,75,80):
    old=(B/(f'Level{lev}PolymorphForm.java')).read_text('utf8')
    m=old[old.index('    static int select('):old.rfind('\n}')].rstrip()
    m=m.replace('    static int select(',f'    private static int selectLevel{lev}(',1)
    assert m in (F/'LevelPolymorphScrollEffects.java').read_text('utf8'),lev
for lev in (82,84,86,88,89,90):
    old=(B/(f'Level{lev}AchievementReward.java')).read_text('utf8')
    m=old[old.index('    static void stage('):old.rfind('\n}')].rstrip()
    m=m.replace('    static void stage(',f'    private static void stageLevel{lev}(',1)
    assert m in (F/'LevelMilestonePackages.java').read_text('utf8'),lev
s=(F/'Telbookitem.java').read_text('utf8')
assert 'find_item_ids' not in s and 'new Random()' not in s
assert '830022 + pc.getMapId() - 101' in s and '830022 + pc.getMapId() - 12852' in s
assert 'ThreadLocalRandom random = ThreadLocalRandom.current()' in s
s=(F/'IdentificationAndScrollItems.java').read_text('utf8')
assert 'StringBuffer sb = new StringBuffer()' not in s
print('PASS Phase18 exact 15 original method bodies relocated, no stale references, known allocation sites removed')
