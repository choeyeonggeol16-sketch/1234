from pathlib import Path
import sys,shutil
root=Path(__file__).resolve().parents[2]
base=root/'docs/item_domain_phase38_48_20261118/typed';out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
for file in ('l1j/server/server/model/L1InventoryTransaction.java','l1j/server/server/model/Instance/L1ItemInstance.java'):
 a=base/file;b=out/file;b.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(a,b)
prod=(root/'src/l1j/server/server/model/network/core/cs/packet/CS_NpcAction.java').read_text()
a=prod.index('private static void npcPaidFloorTeleport(');b=prod.index('{',a);level=0
for j in range(b,len(prod)):
 if prod[j]=='{':level+=1
 elif prod[j]=='}':
  level-=1
  if level==0:break
m=prod[a:j+1]
model=(base/'QuestLogic.java').read_text().replace('\n}\nclass NpcTemplate','\n    '+m+'\n}\nclass NpcTemplate',1)
model+='\nclass L1ItemId { static final int ADENA=40308; }\n'
(out/'QuestLogic.java').write_text(model)
for val in ('40104 + floor','116 + floor * 10','floor >= 0 && floor <= 8','npcPaidFloorTeleport(pc, (L1NpcInstance) obj'):
 assert val in prod,val
print('PHASE65 production source and floor-route assertions PASS')
