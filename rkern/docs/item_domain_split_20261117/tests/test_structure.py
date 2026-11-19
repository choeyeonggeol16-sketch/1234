from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[3]; audit=Path(__file__).resolve().parents[1]
pkg=root/'src/l1j/server/server/model/item/function'; utils=root/'src/l1j/server/server/utils'
assert not any((pkg/(n+'.java')).exists() for n in ('additem','additem2','additemnew'))
assert not (utils/'ItemUseUtil.java').exists()
assert all((pkg/(n+'.java')).exists() for n in ('ClassRewardPackages','LevelAndEquipmentPackages','StarterSupportPackages','RuneRewardPackages','EquipmentStarterPackages','LevelMilestonePackages','QuestDialogueItems','PackageRewardStaging','RecruitStarterKit','SpecialEnchantmentItems','DurableBuffScrollItems'))
text=(utils/'SupplementalItemIds.java').read_text(encoding='utf8')
body=text.split('private static final int[] ITEM_IDS = {',1)[1].split('};',1)[0]
body=re.sub(r'//[^\n]+','',body)
a=[x.strip() for x in body.split(',') if x.strip()]
b=(audit/'ORIGINAL_SUPPLEMENTAL_IDS.txt').read_text(encoding='utf8').splitlines()
assert a==b,('supplemental ID regression',len(a),len(b))
s=(pkg/'ItemActionDispatcher.java').read_text(encoding='utf8')
labels=[]
for item in re.findall(r'(?ms)^[ \t]*case[ \t]+(.+?)[ \t]*->',s):labels.extend(x.strip() for x in item.split(','))
old=(audit/'ORIGINAL_DISPATCH_LABELS.txt').read_text(encoding='utf8').splitlines()
assert sorted(labels)==old, ('dispatcher item IDs changed',len(labels),len(old))
assert 'QuestDialogueItems.send(pc, itemId)' in s
assert 'ClassRewardPackages.clickItem' in s and 'LevelAndEquipmentPackages.clickItem' in s
assert 'ItemUseUtil' not in (pkg/'ItemActionService.java').read_text(encoding='utf8')
print('PASS exact 203-case-group labels (flattened), supplemental ID index, domain wiring')
