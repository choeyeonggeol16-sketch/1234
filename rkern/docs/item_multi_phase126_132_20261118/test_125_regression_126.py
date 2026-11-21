#!/usr/bin/env python3
"""Old skillbook and world receipt regressions with updated postcommit-tail mock."""
from pathlib import Path
import os,zipfile,tempfile,subprocess,shutil
root=Path(os.environ['SOURCE_ROOT']) if os.environ.get('SOURCE_ROOT') else Path(__file__).resolve().parents[2]
old=root/'docs/item_multi_phase123_125_20261118'
prod=root/'src/l1j/server/server/model/item/function'
with tempfile.TemporaryDirectory(prefix='rkern125_126_') as td:
 dest=Path(td)
 with zipfile.ZipFile(old/'fixtures.zip') as z:z.extractall(dest)
 skill=dest/'skills';world=dest/'misc'
 for name in ['SkillBookLearning','GeneralSpellbookLearning']:
  shutil.copyfile(prod/(name+'.java'),skill/'l1j/server/server/model/item/function'/f'{name}.java')
 w=(prod/'WarriorSkillBookItems.java').read_text('utf-8');start=w.index('public static void 전사스킬(');p=w.index('{',start);n=1;q=p+1
 while n:
  if w[q]=='{':n+=1
  if w[q]=='}':n-=1
  q+=1
 head='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
public final class WarriorSkillBookItems {\n'''
 (skill/'l1j/server/server/model/item/function/WarriorSkillBookItems.java').write_text(head+w[start:q]+'\n}\n','utf-8')
 imports='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.*;
import l1j.server.server.model.network.core.sc.packet.*;
import l1j.server.server.storage.CacheD;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.character.combat.arena.stadium.StadiumManager;
import static l1j.server.server.model.item.function.CreatureItemActions.cancelAbsoluteBarrier;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.TransformationItems.polyAction;
'''
 for name in ['ResurrectionConsumables','TargetPolymorphWands']:
  s=(prod/(name+'.java')).read_text('utf-8')
  (world/'l1j/server/server/model/item/function'/f'{name}.java').write_text(imports+s[s.index('final class '+name):],'utf-8')
 (world/'l1j/server/server/model/item/function/ItemActionCompletion.java').write_text('''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class ItemActionCompletion {public static int count;
 public static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance i){count++;}}
''','utf-8')
 for folder,main in [(skill,'TestBooks'),(world,'TestWorldEffects')]:
  java=list(folder.rglob('*.java'));classes=dest/(main+'classes');classes.mkdir()
  p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*map(str,java)],capture_output=True,text=True)
  if p.returncode:print(p.stderr[-10000:]);raise SystemExit(p.returncode)
  p=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.'+main],capture_output=True,text=True)
  if p.returncode:print(p.stdout[-1000:],p.stderr[-10000:]);raise SystemExit(p.returncode)
  for line in p.stdout.splitlines():
   if 'TESTS_PASSED' in line: print(line)
