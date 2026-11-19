#!/usr/bin/env python3
"""Guard phase 22-30 exact modified production region & removed passthrough entrypoints.
This is not an integrated full-server compile; old source baselines are embedded in docs.
"""
from pathlib import Path
import re,hashlib
D=Path(__file__).resolve().parent
ROOT=D.parents[1]
FUNC=ROOT/'src/l1j/server/server/model/item/function'
BASE=D/'baseline'
changed=['EnchantmentOutcomeEffects.java',
 'ItemActionDispatcher.java','SupplementalCraftRouter.java','SupplementalRewardRouter.java']
removed=['ClassGiftPackageUse','AdministratorGiftPackageUse','CharacterSlotVoucherItems',
 'EquipmentSlotVoucherItems','RankingRewardItems','ItemCurrencyExchanges','TransformationPotionItems']
for n in removed:
 assert not (FUNC/(n+'.java')).exists(),n
# Validate all original -> optimized code changes strictly, including the complete switch arms.
rules={
 'SupplementalCraftRouter.java': [('CharacterSlotVoucherItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1)', 'CharacterSlotReceipt.useCharacterSlotExpansionAtomic(client, pc, l1iteminstance)'),
   ('RankingRewardItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1)','RankBusiness.getInstance().onExpendiantItem(pc, l1iteminstance)')],
 'SupplementalRewardRouter.java': [('EquipmentSlotVoucherItems.use(pc, l1iteminstance, itemId, l1iteminstance1)','EquipmentSlotUnlockReceipt.useSlotUnlockAtomic(pc, l1iteminstance)')],
 'ItemActionDispatcher.java': [('ClassGiftPackageUse.handleClassPackage(use);','{ ClassPackageRewardReceipt.useClassPackage(pc, item); yield ItemActionResult.STOP; }'),
  ('AdministratorGiftPackageUse.handleOpenAdminPackage(use);','{ AdminItemPackages.open(pc, item); yield ItemActionResult.STOP; }'),
  ('ItemCurrencyExchanges.handleConvertAdena(use);','{ AdenaCurrencyConversionReceipt.convertAdena(pc, item); yield ItemActionResult.STOP; }'),
  ('ItemCurrencyExchanges.handleExchangeAdenaVoucher(use);','{ SpecialConsumableRouter.use(pc, item); yield ItemActionResult.STOP; }'),
  ('TransformationPotionItems.handlePolyPotion(use);','{ usePolyPotion(pc, itemId); pc.getInventory().removeItem(item, 1); yield ItemActionResult.COMPLETE; }'),
  ('        if(use.item().getItem().getType2()==1) {','        final int type2 = use.item().getItem().getType2();\n        if(type2==1) {'),
  ('if(use.item().getItem().getType2()==2)', 'if(type2==2)'),
  ('if(use.item().getItem().getType2()!=0)', 'if(type2!=0)')],
}
for filename in changed:
 src=(BASE/filename).read_text('utf8')
 if filename=='EnchantmentOutcomeEffects.java':
  for var,expected in [('s1',8),('nameId',2)]:
   old=f'new StringBuilder().append(pm).append(item.getEnchantLevel()).append(" ").append({var}).toString()'
   assert src.count(old)==expected,(filename,old)
   src=src.replace(old,f'pm + item.getEnchantLevel() + " " + {var}')
 else:
  for old,new in rules[filename]:
   assert src.count(old)==1,(filename,old,src.count(old))
   src=src.replace(old,new)
 assert src == (FUNC/filename).read_text('utf8'),filename

# Registry contract: the unchanged portions of the original class are byte-identical.
name='ItemEffectRegistry.java';old=(BASE/name).read_text('utf8');new=(ROOT/'src/l1j/server/server/model/item'/name).read_text('utf8')
start='        void buildIndex() {'; end='        /** 지연 초기화'
a0=old.index(start);a1=old.index(end);b0=new.index(start);b1=new.index(end)
assert old[:a0]==new[:b0] and old[a1:]==new[b1:],name
assert 'List.copyOf(universal)' in new and 'if ((specializedMask & (1 << c)) == 0)' in new

production=ROOT/'src'
for n in removed:
 for p in production.rglob('*.java'):
  assert n not in p.read_text('utf8',errors='replace'),(n,p)
assert sum(1 for _ in production.rglob('*.java'))==1926
print('PASS phase30 audited exact source-delta guard: 5 changed, 7 removed; locale-formatting restored')
