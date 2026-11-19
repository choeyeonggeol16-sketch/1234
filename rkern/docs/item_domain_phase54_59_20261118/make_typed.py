from pathlib import Path
import shutil,sys
project=Path(__file__).resolve().parents[2]
base=project/'docs/item_domain_phase38_48_20261118/typed'
out=Path(sys.argv[1]).resolve();out.mkdir(parents=True,exist_ok=True)
for name in ('l1j/server/server/model/L1InventoryTransaction.java','l1j/server/server/model/Instance/L1ItemInstance.java'):
 p=base/name;q=out/name;q.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,q)
prod=(project/'src/l1j/server/server/model/network/core/cs/packet/CS_NpcAction.java').read_text(encoding='utf-8')
model=(base/'QuestLogic.java').read_text(encoding='utf-8')
def extract(name):
 a=prod.index('private static void '+name+'(');b=prod.index('{',a);balance=0
 for i in range(b,len(prod)):
  if prod[i]=='{':balance+=1
  elif prod[i]=='}':
   balance-=1
   if balance==0:return '    '+prod[a:i+1]+'\n'
 raise ValueError('unterminated method '+name)
model=model.replace('public final class QuestLogic {','public final class QuestLogic {\n    private static final java.util.Random _random = new java.util.Random(1234);',1)
model=model.replace('\n}\nclass NpcTemplate','\n'+extract('lukeinChestReceipt')+extract('keplishaHoroscopeReceipt')+'}\nclass NpcTemplate',1)
model=model.replace('QUEST_SIMIZZ=27;','QUEST_SIMIZZ=27,QUEST_LUKEIN1=23,QUEST_TBOX1=24,QUEST_TBOX2=25,QUEST_TBOX3=26,QUEST_KEPLISHA=35;',1)
model+='\nclass L1ItemId { static final int ADENA=40308; }\n'
(out/'QuestLogic.java').write_text(model,encoding='utf-8')
checks=[
 '1, 2, 3, L1Quest.QUEST_TBOX1, 40702',
 'npcId - 71062, 5, 6, L1Quest.QUEST_TBOX2, 40702',
 'npcId - 71062, 11, 1, L1Quest.QUEST_TBOX3, 40716',
 'L1Quest.QUEST_LUKEIN1, 11, L1Quest.QUEST_END,',
 '40716, 1, 0, 20269, 0, "lukein0", "lukein0"',
 'questGrantOnce(pc, (L1NpcInstance) obj, L1Quest.QUEST_KEPLISHA,',
 'keplishaHoroscopeReceipt(pc, (L1NpcInstance) obj);',
 'final int variant = _random.nextInt(3);',
 'final int detail = _random.nextInt(100) + 1;'
]
for check in checks:assert check in prod, 'missing dispatcher/behavior: '+check
for old in ('new int[] { 1, 2, 3 }','new int[] { 1, 2, 3, 4, 5, 6 }',
            'pc.getQuest().set_end(L1Quest.QUEST_TBOX1)',
            'pc.getQuest().set_end(L1Quest.QUEST_TBOX2)',
            'pc.getQuest().set_end(L1Quest.QUEST_TBOX3)'):
 assert old not in prod, 'old premature quest mutation: '+old
print('PHASE54-59 source extraction/route assertions PASS')
