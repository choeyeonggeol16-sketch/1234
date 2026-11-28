package l1j.server.server.model.item.function;

import l1j.server.server.model.character.skill.acquire.CSkillBook;

import static l1j.server.server.model.character.skill.L1SkillId.EARTH_BIND;
import static l1j.server.server.model.character.skill.L1SkillId.ICE_LANCE;
import static l1j.server.server.model.character.skill.L1SkillId.SHOCK_STUN;
import static l1j.server.server.model.item.function.BossSummonWandItems.*;
import static l1j.server.server.model.item.function.ChargedRewardBoxItems.*;
import static l1j.server.server.model.item.function.CombatAbilityItems.*;
import static l1j.server.server.model.item.function.CreatureItemActions.*;
import static l1j.server.server.model.item.function.DollEffectItems.*;
import static l1j.server.server.model.item.function.DurabilityRepairItems.*;
import static l1j.server.server.model.item.function.ElixirStatTransactions.*;
import static l1j.server.server.model.item.function.EnchantedBagTransactions.*;
import static l1j.server.server.model.item.function.EnchantmentItems.*;
import static l1j.server.server.model.item.function.EquipmentItemActions.*;
import static l1j.server.server.model.item.function.FurniturePlacementItems.*;
import static l1j.server.server.model.item.function.ImmuneBladeSkillItems.*;
import static l1j.server.server.model.item.function.InventorySlotExpansionItems.*;
import static l1j.server.server.model.item.function.ItemReceiptOperations.*;
import static l1j.server.server.model.item.function.LawfulPotionTransactions.*;
import static l1j.server.server.model.item.function.NearbyItemTargetQueries.*;
import static l1j.server.server.model.item.function.PetVoucherTransactions.*;
import static l1j.server.server.model.item.function.PetWithdrawalItems.*;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.ReceiptCraftingItems.*;
import static l1j.server.server.model.item.function.ResolventItemFacade.*;
import static l1j.server.server.model.item.function.RevivalScrollCraftingItems.*;
import static l1j.server.server.model.item.function.SealedEquipmentRewards.*;
import static l1j.server.server.model.item.function.SpecialEnchantScrolls.*;
import static l1j.server.server.model.item.function.SpellBookItems.*;
import static l1j.server.server.model.item.function.SupportSummonItems.*;
import static l1j.server.server.model.item.function.TowerCharmCraftingItems.*;
import static l1j.server.server.model.item.function.TowerTeleportAmulets.*;
import static l1j.server.server.model.item.function.TransformationItems.*;

import l1j.server.server.model.character.skill.CSkillDB;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Skills;

public final class WarriorSkillBookItems {
    private WarriorSkillBookItems() {}
	public static void 전사스킬(L1PcInstance pc, L1ItemInstance item, boolean ispassibe) {
		final CSkillDB table = CSkillDB.getInstance();
		L1Skills skill = table.acquireDB().findWarriorBook(item.getItemId());
		// Missing skill definitions must not destroy the corresponding book.
		if (skill == null) {
			SC_SystemMessage.send(pc, "습득할 수 있는 스킬을 찾지 못했습니다.");
			return;
		}
        int skillLevel = skill.getSkillLevel();
        int id = skill.getId();
        if (skillLevel < 1 || skillLevel > 29) {
            SC_SystemMessage.send(pc, "습득할 수 있는 스킬 레벨이 아닙니다.");
            return;
        }
        final int skillId = skill.getSkillId();
        if (pc.HaveSkill(skillId)) {
            SC_SystemMessage.send(pc, "이미 배운 마법입니다.");
            return;
        }
        int[] levels = new int[29];
        levels[skillLevel - 1] = id;
        CSkillBook.commitLearning(pc, item, skill, levels, 224);
	}
}
