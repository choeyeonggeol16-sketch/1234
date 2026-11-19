#!/usr/bin/env python3
"""Static regression for receipt-tail call sites; not a substitute for full compile."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
p=root/'src/l1j/server/server/model/item/function'
expected='QuestRepairScrollUse ExperienceRecoveryConsumables CombatBuffConsumables BlessingConsumables UnlistedMagicScrolls WorldDestinationItems QuestEarringCollection QuestVoucherExchange GemConversionItems QuestTreasureFragmentCombine QuestSummoningFragmentCombine DollPotentialEnhancementScrolls QuestAncientBookCombine FishingUtilityItems'.split()
checks=0
for cls in expected:
 s=(p/f'{cls}.java').read_text('utf8')
 assert 'ItemActionCompletion.finishCommittedConsumable(pc,' in s,cls
 checks+=1
 assert 'return ItemActionResult.STOP;' in s,cls
 checks+=1
 if cls in ['QuestEarringCollection','QuestVoucherExchange','GemConversionItems','QuestTreasureFragmentCombine','QuestSummoningFragmentCombine','DollPotentialEnhancementScrolls']:
  assert 'return ItemActionResult.COMPLETE;' not in s,cls
  checks+=1
# These formerly returned COMPLETE immediately after a receipt submission.
for cls in ['QuestRepairScrollUse','ExperienceRecoveryConsumables','QuestAncientBookCombine','WorldDestinationItems','UnlistedMagicScrolls']:
 s=(p/f'{cls}.java').read_text('utf8')
 assert 'return ItemActionResult.COMPLETE;' not in s,cls
 checks+=1
# The fishing handler must retain synchronous fishing entry's COMPLETE but not use
# the same completion for the asynchronous reel charge path.
s=(p/'FishingUtilityItems.java').read_text('utf8')
assert s.count('return ItemActionResult.COMPLETE;')==1
checks+=1
# The original 138 pet/amulete/wand enhancements must remain.
assert 'if (l1pet == null' in (p/'PetWithdrawalItems.java').read_text('utf8');checks+=1
assert 'private static final java.util.logging.Logger LOG' in (p/'EnchantmentOutcomeEffects.java').read_text('utf8');checks+=1
print('PHASE139_142_SOURCE_GATES='+str(checks)+' PASS')
