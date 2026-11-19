#!/usr/bin/env python3
"""Re-run old production potions + transformations tests with updated logger mock."""
from pathlib import Path
import shutil, subprocess, tempfile, os
root=Path(os.environ['SOURCE_ROOT']) if os.environ.get('SOURCE_ROOT') else Path(__file__).resolve().parents[2]
doc=root/'docs/item_multi_phase118_122_20261118/tests'
prod=root/'src/l1j/server/server/model/item/function'
with tempfile.TemporaryDirectory(prefix='rkern122_126_') as t:
 dest=Path(t)
 shutil.copytree(doc/'typed', dest/'fixture')
 function=dest/'fixture/l1j/server/server/model/item/function'
 p=function/'CreatureItemActions.java'
 s=p.read_text('utf-8')
 assert s.count('public class CreatureItemActions {')==1
 s=s.replace('public class CreatureItemActions {','''public class CreatureItemActions {
 public static final java.util.logging.Logger ITEM_USE2_LOG=java.util.logging.Logger.getLogger("item-mock");''')
 p.write_text(s,'utf-8')
 for name in ['MovementPotionConsumables','ManaProtectionPotionConsumables',
              'MovementPotionEffects','ManaPotionEffects','ProtectivePotionEffects',
              'TransformationScrollItems','ItemActionCompletion']:
  shutil.copyfile(prod/(name+'.java'),function/(name+'.java'))
 files=list((dest/'fixture').rglob('*.java'))
 classes=dest/'classes';classes.mkdir()
 p=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*map(str,files)],text=True,capture_output=True)
 if p.returncode: print(p.stderr[-10000:]);raise SystemExit(p.returncode)
 for name in ['TestDurablePotions','TestPolyReceipts']:
  p=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.'+name],text=True,capture_output=True)
  print(p.stdout,end='')
  if p.returncode: print(p.stderr[-5000:]);raise SystemExit(p.returncode)
