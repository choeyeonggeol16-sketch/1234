from pathlib import Path
import sys,shutil
root=Path(__file__).resolve().parents[2]
base=root/'docs/item_domain_phase38_48_20261118/typed';out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
for name in ('l1j/server/server/model/L1InventoryTransaction.java','l1j/server/server/model/Instance/L1ItemInstance.java'):
 a=base/name;b=out/name;b.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(a,b)
p=out/'l1j/server/server/model/Instance/L1ItemInstance.java';s=p.read_text();s=s.replace('public ItemDefinition getItem()', 'public String getLogName(){return "i"+id;} public ItemDefinition getItem()');p.write_text(s)
prod=(root/'src/l1j/server/server/model/network/core/cs/packet/CS_NpcAction.java').read_text()
a=prod.index('private static void npcSingleMaterialExchange(');b=prod.index('{',a);depth=0
for i in range(b,len(prod)):
 if prod[i]=='{':depth+=1
 elif prod[i]=='}':
  depth-=1
  if depth==0:break
m=prod[a:i+1]
model=(base/'QuestLogic.java').read_text()
model=model.replace('\n}\nclass NpcTemplate','\n    '+m+'\n}\nclass NpcTemplate',1)
model+='\nclass L1ItemId { static final int ADENA=40308,DRUGA_POKET=410039; }\n'
(out/'QuestLogic.java').write_text(model)
for val in ('14000000','1400001','1400002','1400003','L1ItemId.ADENA, 10000000, rewardId, "veil8", "veil4", null','41921, 45, 410055, null, "suschef5", "$8538"'):
 assert val in prod,val
print('PHASE63-64 real-production method and five route mappings PASS')
