from pathlib import Path
import shutil,sys
root=Path(__file__).resolve().parents[2]
base=root/'docs/item_domain_phase38_48_20261118/typed'
target=Path(sys.argv[1]).resolve();target.mkdir(parents=True,exist_ok=True)
for name in ('l1j/server/server/model/L1InventoryTransaction.java','l1j/server/server/model/Instance/L1ItemInstance.java'):
 x=base/name;y=target/name;y.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(x,y)
# Test stub only: this existing production method is used in the successful 403 packet.
p=target/'l1j/server/server/model/Instance/L1ItemInstance.java';s=p.read_text()
assert 'public ItemDefinition getItem()' in s
s=s.replace('public ItemDefinition getItem()', 'public String getLogName(){return "i"+id;} public ItemDefinition getItem()')
p.write_text(s)
prod=(root/'src/l1j/server/server/model/network/core/cs/packet/CS_NpcAction.java').read_text()
model=(base/'QuestLogic.java').read_text()
def extract(name):
 a=prod.index('private static void '+name+'('); b=prod.index('{',a); level=0
 for pos in range(b,len(prod)):
  if prod[pos]=='{':level+=1
  elif prod[pos]=='}':
   level-=1
   if level==0:return '    '+prod[a:pos+1]+'\n'
 raise RuntimeError(name)
model=model.replace('public final class QuestLogic {','public final class QuestLogic {\n    private static final java.util.Random _random = new java.util.Random(2121);',1)
model=model.replace('\n}\nclass NpcTemplate','\n'+extract('dragonEyeExchange')+extract('dragonEyeUpgrade')+'}\nclass NpcTemplate',1)
model+='\nclass L1ItemId { static final int ADENA=40308; }\n'
(target/'QuestLogic.java').write_text(model)
checks=(
'410162, 32, 100000, 410034, "sherme10", false',
'410163, 32, 100000, 410032, "sherme10", false',
'410164, 32, 100000, 410035, "sherme10", false',
'410165, 32, 100000, 410033, "sherme10", false',
'dragonEyeExchange(pc, (L1NpcInstance) obj, materialId, 1, 100000, rewardId, "sherme1", true)',
'410034, 410032, 410036, 40',
'410036, 410033, 410037, 30',
'410037, 410035, 410038, 20',
)
for val in checks:assert val in prod,val
assert 'if (s.equalsIgnoreCase("K"))' in prod
print('PHASE60-62 source extractor and 11 route mappings PASS')
