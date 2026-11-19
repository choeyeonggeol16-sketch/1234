#!/usr/bin/env python3
# Copy actual production class bodies with imports remapped to deliberately narrow typed test stubs.
from pathlib import Path
import sys
root, dest=map(Path,sys.argv[1:3])
package=dest/'l1j/server/server/model/item/function'
package.mkdir(parents=True,exist_ok=True)
selected='QuestEarringCollection QuestVoucherExchange GemConversionItems QuestTreasureFragmentCombine QuestSummoningFragmentCombine DollPotentialEnhancementScrolls ExperienceRecoveryConsumables QuestRepairScrollUse'.split()
for name in selected:
 src=root/'src/l1j/server/server/model/item/function'/f'{name}.java'
 lines=[line for line in src.read_text().splitlines(True) if not line.startswith('import ')]
 code=''.join(lines).replace('package l1j.server.server.model.item.function;', '''package l1j.server.server.model.item.function;
import static l1j.server.server.model.item.function.ItemReceiptOperations.*;
import static l1j.server.server.model.item.function.DurabilityRepairItems.UseDurableRepair;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.isOwnedScrollTarget;
import static l1j.server.server.model.item.function.CreatureItemActions.*;''')
 (package/f'{name}.java').write_text(code)
for f in ('Stubs.java','Regression.java'):
 (package/f).write_bytes((root/'docs/item_multi_phase139_142_20261118'/f).read_bytes())
