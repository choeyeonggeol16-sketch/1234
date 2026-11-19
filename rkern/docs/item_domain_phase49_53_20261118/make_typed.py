from pathlib import Path
import re, shutil, sys
project=Path(sys.argv[1]).resolve()
base=project/'docs/item_domain_phase38_48_20261118/typed'
root=Path(sys.argv[2]).resolve()
root.mkdir(parents=True,exist_ok=True)
for f in [base/'l1j/server/server/model/L1InventoryTransaction.java',base/'l1j/server/server/model/Instance/L1ItemInstance.java']:
    tgt=root/f.relative_to(base);tgt.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(f,tgt)
prod=(project/'src/l1j/server/server/model/network/core/cs/packet/CS_NpcAction.java').read_text()
code=(base/'QuestLogic.java').read_text()
def method(name):
    a=prod.index(f'private static void {name}(')
    c=prod.index('{',a)
    level=0
    for i in range(c,len(prod)):
        if prod[i]=='{':level+=1
        elif prod[i]=='}':
            level-=1
            if level==0: return '    '+prod[a:i+1]+'\n'
    raise RuntimeError(name)
methods='\n'.join(method(n) for n in ['npcItemExchange','npcQuestDebit','level15ItemReceipt'])
code=code.replace('\n}\nclass NpcTemplate', '\n'+methods+'}\nclass NpcTemplate',1)
code=code.replace('static final int QUEST_LIZARD=34,QUEST_END=255,QUEST_CRYSTAL=33,QUEST_SIMIZZ=27;',
                 'static final int QUEST_LIZARD=34,QUEST_END=255,QUEST_CRYSTAL=33,QUEST_SIMIZZ=27,QUEST_CADMUS=31,QUEST_KAMYLA=32,QUEST_LEVEL15=1;')
(root/'QuestLogic.java').write_text(code)
# Fail fast if routing rules do not agree with extracted methods.
for snippet in [
    'npcItemExchange(pc, (L1NpcInstance) obj, materialId, rewardId, page, "my4")',
    'step, 2, 40647, 3, "cadmus6", "cadmus5"',
    '2, 3, 40644, 1, "kamyla10", "kamyla10"',
    '3, 4, 40630, 1, "kamyla13", "kamyla13"',
    'level15ItemReceipt(pc, (L1NpcInstance) obj, false, s.equalsIgnoreCase("a"))',
    'level15ItemReceipt(pc, (L1NpcInstance) obj, true, s.equalsIgnoreCase("a"))',
]:
    assert snippet in prod, f'route missing {snippet}'
print('Extracted 3 real production helper methods + 6 dispatcher checks.')
