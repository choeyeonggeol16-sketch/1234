from pathlib import Path
import tempfile,shutil,subprocess,zipfile,os
root=Path(os.environ['SOURCE_ROOT']) if os.environ.get('SOURCE_ROOT') else Path(__file__).resolve().parents[2]
base=root/'docs/item_multi_phase123_125_20261118/fixtures.zip'
src=root/'src/l1j/server/server/model/item/function'
with tempfile.TemporaryDirectory(prefix="rkern126_world_") as td:
 out=Path(td)
 with zipfile.ZipFile(base) as fixture: fixture.extractall(out)
 shutil.move(str(out/'misc'), str(out/'fixture'))
 d=out/'fixture'/'l1j/server/server/model/item/function'
 prefix='''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.*;
import l1j.server.server.model.network.core.sc.packet.*;
import l1j.server.server.storage.CacheD;
import l1j.server.server.ActionCodes;
import l1j.server.Stadium.StadiumManager;
import static l1j.server.server.model.item.function.CreatureItemActions.cancelAbsoluteBarrier;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.TransformationItems.polyAction;
'''
 for name in ['ResurrectionConsumables','TargetPolymorphWands']:
  s=(src/(name+'.java')).read_text('utf-8')
  body=s[s.index('final class '+name):]
  (d/(name+'.java')).write_text(prefix+body,'utf-8')
 (d/'ItemActionCompletion.java').write_text('''package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.*;
public final class ItemActionCompletion { public static int count;
 public static void finishCommittedConsumable(L1PcInstance p,L1ItemInstance i){count++;}
}
''','utf-8')
 # Add dynamic tests to existing full test harness, before final print.
 h=d/'TestWorldEffects.java';s=h.read_text('utf-8')
 before='  System.out.println("WORLD_RECEIPT_TESTS_PASSED="+cases);'
 assert s.count(before)==1
 extra='''
  reset();ItemActionCompletion.count=0;item=new L1ItemInstance(111,40089,0);pc=p(item);
  target=new L1PcInstance();target.id=188;target.hp=0;target.dead=true;L1World.INSTANCE.target=target;
  ok(ResurrectionConsumables.handleTeleportCompanions(context(40089,pc,item,188))==ItemActionResult.STOP,"resurrection async STOP");
  ok(ItemActionCompletion.count==0,"resurrection no precommit tail");
  ItemReceiptOperations.pending.run();ok(ItemActionCompletion.count==1,"resurrection one postcommit tail");
  reset();ItemActionCompletion.count=0;item=new L1ItemInstance(112,40008,2);pc=p(item);L1World.INSTANCE.target=pc;
  ok(TargetPolymorphWands.handleResurrectionScroll(context(40008,pc,item,pc.id))==ItemActionResult.STOP,"wand async STOP");
  ok(ItemActionCompletion.count==0,"wand no precommit tail");CDB.pending.run();
  ok(ItemActionCompletion.count==1,"wand one postcommit tail");
  reset();ItemActionCompletion.count=0;item=new L1ItemInstance(113,40410,0);pc=p(item);L1World.INSTANCE.target=pc;
  ok(TargetPolymorphWands.handleResurrectionScroll(context(40410,pc,item,pc.id))==ItemActionResult.STOP,"normal scroll async STOP");
  ok(ItemActionCompletion.count==0,"normal scroll no precommit tail");ItemReceiptOperations.pending.run();
  ok(ItemActionCompletion.count==1,"normal scroll one postcommit tail");
  reset();ItemActionCompletion.count=0;item=new L1ItemInstance(114,40089,0);pc=p(item);
  target=new L1PcInstance();target.id=189;target.hp=0;target.dead=true;L1World.INSTANCE.target=target;
  ResurrectionConsumables.handleTeleportCompanions(context(40089,pc,item,189));
  ItemReceiptOperations.pending=null;ok(ItemActionCompletion.count==0,"rollback no tail");
'''
 h.write_text(s.replace(before,extra+'\n'+before),'utf-8')
 classes=out/'classes';classes.mkdir()
 files=list((out/'fixture').rglob('*.java'))
 r=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*map(str,files)],capture_output=True,text=True)
 if r.returncode:
  print(r.stderr[:8000]);raise RuntimeError('typed compile failed')
 r=subprocess.run(['java','-cp',str(classes),'l1j.server.server.model.item.function.TestWorldEffects'],capture_output=True,text=True)
 print(r.stdout.strip())
 if r.returncode:
  print(r.stderr[:5000]);raise RuntimeError('typed test failed')
