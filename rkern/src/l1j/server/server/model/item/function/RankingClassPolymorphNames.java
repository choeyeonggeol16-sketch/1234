package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.SHAPE_CHANGE;
import java.util.Random;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.model.character.skill.CSkillDB;
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
final class RankingClassPolymorphNames {
    private RankingClassPolymorphNames() {}



    static String select(L1PcInstance pc, String s) {
			if (pc.isCrown()) {
				if (pc.get_sex() == 0)
					s = "rangking prince male";
				else
					s = "rangking prince female";
			} else if (pc.isKnight()) {
				if (pc.get_sex() == 0)
					s = "rangking knight male";
				else
					s = "rangking knight female";
			} else if (pc.isElf()) {
				if (pc.get_sex() == 0)
					s = "rangking elf male";
				else
					s = "rangking elf female";
			} else if (pc.isWizard()) {
				if (pc.get_sex() == 0)
					s = "rangking wizard male";
				else
					s = "rangking wizard female";
			} else if (pc.isDarkelf()) {
				if (pc.get_sex() == 0)
					s = "rangking darkelf male";
				else
					s = "rangking darkelf female";
			} else if (pc.isDragonknight()) {
				if (pc.get_sex() == 0)
					s = "rangking dragonknight male";
				else
					s = "rangking dragonknight female";
			} else if (pc.isBlackwizard()) {
				if (pc.get_sex() == 0)
					s = "rangking illusionist male";
				else
					s = "rangking illusionist female";
			} else if (pc.isWarrior()) {
				if (pc.get_sex() == 0)
					s = "rangking warrior male";
				else
					s = "rangking warrior female";
			}
		
        return s;
    }
}
