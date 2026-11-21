#!/usr/bin/env python3
"""Java 21 source-method tests using deterministic transaction and network doubles.
Tests production bodies, not a complete server build or live MySQL.
"""
from pathlib import Path
import zipfile, tempfile, subprocess, shutil, os
here=Path(__file__).resolve().parent
root=here.parents[1]
prod=root/'src/l1j/server/server/model/item/function'
with tempfile.TemporaryDirectory(prefix='rkern123_') as temporary:
    dest=Path(temporary)
    with zipfile.ZipFile(here/'fixtures.zip') as z:z.extractall(dest)
    s=dest/'skills';w=dest/'misc'
    for name in ['SkillBookLearning','GeneralSpellbookLearning']:
        target=s/'l1j/server/server/model/item/function'/f'{name}.java'
        shutil.copyfile(prod/f'{name}.java',target)
    # Warrior imports are only external namespace clutter. Compile the verbatim
    # production method with minimal imports and original signature/body.
    warrior=(prod/'WarriorSkillBookItems.java').read_text('utf-8')
    start=warrior.index('public static void 전사스킬(')
    brace=warrior.index('{',start); n=1; end=brace+1
    while n:
        if warrior[end]=='{':n+=1
        elif warrior[end]=='}':n-=1
        end+=1
    m=warrior[start:end]
    (s/'l1j/server/server/model/item/function/WarriorSkillBookItems.java').write_text('package l1j.server.server.model.item.function;\nimport l1j.server.server.model.Instance.L1ItemInstance;\nimport l1j.server.server.model.Instance.L1PcInstance;\nimport l1j.server.server.datatables.SkillsTable;\nimport l1j.server.server.templates.L1Skills;\nimport l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;\npublic final class WarriorSkillBookItems {\n'+m+'\n}\n',encoding='utf-8')
    for name in ['ResurrectionConsumables','TargetPolymorphWands']:
        full=(prod/f'{name}.java').read_text('utf-8')
        body=full[full.index('final class '+name):]
        imports='package l1j.server.server.model.item.function;\nimport l1j.server.server.model.Instance.*;\nimport l1j.server.server.model.*;\nimport l1j.server.server.model.network.core.sc.packet.*;\nimport l1j.server.server.storage.CacheD;\nimport l1j.server.server.ActionCodes;\nimport l1j.server.server.model.character.combat.arena.stadium.StadiumManager;\nimport static l1j.server.server.model.item.function.CreatureItemActions.cancelAbsoluteBarrier;\nimport static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;\nimport static l1j.server.server.model.item.function.TransformationItems.polyAction;\n'
        (w/'l1j/server/server/model/item/function'/f'{name}.java').write_text(imports+body,encoding='utf-8')
    for folder,main in [(s,'TestBooks'),(w,'TestWorldEffects')]:
        java=list(folder.rglob('*.java'))
        classes=folder/'classes';classes.mkdir()
        cmd=['javac','--release','21','-encoding','UTF-8','-d',str(classes)]+list(map(str,java))
        subprocess.run(cmd,check=True,capture_output=True,text=True)
        result=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.'+main],check=True,capture_output=True,text=True)
        # The missing-template and injected-exception traces are expected.
        for line in result.stdout.splitlines():
            if line.startswith('SKILL_RECEIPT_TESTS_PASSED=') or line.startswith('WORLD_RECEIPT_TESTS_PASSED='):
                print(line)
