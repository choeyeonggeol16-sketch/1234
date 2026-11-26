package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.SHAPE_CHANGE;
import java.util.Random;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.inventory.L1PolyMorph;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.rank.loader.RankUserLoader;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Skills;
import static l1j.server.server.model.item.function.EnchantmentItems.*;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.SpellBookItems.*;
import static l1j.server.server.model.item.function.TowerTeleportAmulets.*;
import static l1j.server.server.model.item.function.PetWithdrawalItems.*;
import static l1j.server.server.model.item.function.NearbyItemTargetQueries.*;
import static l1j.server.server.model.item.function.ResolventItemFacade.*;
import static l1j.server.server.model.item.function.SupportSummonItems.*;
import static l1j.server.server.model.item.function.FurniturePlacementItems.*;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.*;
import static l1j.server.server.model.item.function.WandInspectionItems.*;
import static l1j.server.server.model.item.function.CombatSkillItemEffects.*;
import static l1j.server.server.model.item.function.DogFightEnhancementItems.*;
import static l1j.server.server.model.item.function.NpcWandAndSpawnItems.*;
import static l1j.server.server.model.item.function.WarriorSkillBookItems.*;
import static l1j.server.server.model.item.function.PcRoomPassTransactions.*;
import static l1j.server.server.model.item.function.CashBuffTransactions.*;
import static l1j.server.server.model.item.function.EquipmentItemActions.*;
import static l1j.server.server.model.item.function.ChargedRewardBoxItems.*;
import static l1j.server.server.model.item.function.DurabilityRepairItems.*;
import static l1j.server.server.model.item.function.ItemReceiptOperations.*;
import static l1j.server.server.model.item.function.EnchantedBagTransactions.*;
import static l1j.server.server.model.item.function.LawfulPotionTransactions.*;
import static l1j.server.server.model.item.function.InventorySlotExpansionItems.*;
import static l1j.server.server.model.item.function.ReceiptCraftingItems.*;
import static l1j.server.server.model.item.function.RevivalScrollCraftingItems.*;
import static l1j.server.server.model.item.function.TowerCharmCraftingItems.*;
import static l1j.server.server.model.item.function.ImmuneBladeSkillItems.*;
import static l1j.server.server.model.item.function.BossSummonWandItems.*;
import static l1j.server.server.model.item.function.PetVoucherTransactions.*;
import static l1j.server.server.model.item.function.ElixirStatTransactions.*;
import static l1j.server.server.model.item.function.SealedEquipmentRewards.*;
import static l1j.server.server.model.item.function.CombatAbilityItems.*;
import static l1j.server.server.model.item.function.DollEffectItems.*;
import static l1j.server.server.model.item.function.CreatureItemActions.*;

/** Item-use behavior extracted without changing invocation order. */
final class MythicClassPolymorphNames {
    private MythicClassPolymorphNames() {}



    static String select(L1PcInstance pc, String s) {
			if (pc.isCrown()) {
				if (pc.get_sex() == 0)
					s = "royal male r";
				else
					s = "royal female r";
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0)
					s = "knight male r";
				else
					s = "knight female r";
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0)
					if(pc.getCurrentSpriteId() != 21070){
					s = "elf male r";
					}else{
						s = "elf old male";
					}
				else
					if(pc.getCurrentSpriteId() != 21066){
					s = "elf female r";
					}else{
						s = "elf old female";
					}
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0)
					s = "wizard male r";
				else
					s = "wizard female r";
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0)
					s = "darkelf male r";
				else
					s = "darkelf female r";
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0)
					s = "dragon r";
				else
					s = "dragon r";
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0)
					s = "dopel male";
				else
					s = "dopel female";
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0)
					s = "my warrior";
				else
					s = "my warrior";
			}
		
        return s;
    }
}
