package l1j.server.server.model.item.function;


import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.model.network.core.session.CIOClient;


import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Skills;




import static l1j.server.server.model.item.function.EnchantmentItems.*;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.TransformationItems.*;
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

public final class SpellBookItems {
    private SpellBookItems() {}
	public static void useSpellBook(L1PcInstance pc, L1ItemInstance item, int itemId) {
        GeneralSpellbookConsumable.useSpellBook(pc, item, itemId);
    }

	public static void useElfSpellBook(L1PcInstance pc, L1ItemInstance item, int itemId) {
        ElfSpellbookConsumable.useElfSpellBook(pc, item, itemId);
    }
	

	public static void SpellBook(L1PcInstance pc, L1ItemInstance item, boolean isLawful) {
        GeneralSpellbookLearning.SpellBook(pc, item, isLawful);
    }

	public static void SpellBook1(L1PcInstance pc, L1ItemInstance l1iteminstance, CIOClient clientthread) {
        SkillBookLearning.learn(pc, l1iteminstance, SkillBookLearning.Kind.DARK_ELF);
    }

	public static void SpellBook2(L1PcInstance pc, L1ItemInstance l1iteminstance) {
        SkillBookLearning.learn(pc, l1iteminstance, SkillBookLearning.Kind.ELF);
    }

	public static void SpellBook3(L1PcInstance pc, L1ItemInstance l1iteminstance, CIOClient clientthread) {
        SkillBookLearning.learn(pc, l1iteminstance, SkillBookLearning.Kind.KNIGHT);
    }

	public static void SpellBook4(L1PcInstance pc, L1ItemInstance l1iteminstance, CIOClient clientthread) {
        SkillBookLearning.learn(pc, l1iteminstance, SkillBookLearning.Kind.PRINCE);
    }

	public static void SpellBook5(L1PcInstance pc, L1ItemInstance l1iteminstance, CIOClient clientthread) {
        SkillBookLearning.learn(pc, l1iteminstance, SkillBookLearning.Kind.DRAGON_KNIGHT);
    }

	public static void SpellBook6(L1PcInstance pc, L1ItemInstance l1iteminstance, CIOClient clientthread) {
        SkillBookLearning.learn(pc, l1iteminstance, SkillBookLearning.Kind.ILLUSIONIST);
    }

}
