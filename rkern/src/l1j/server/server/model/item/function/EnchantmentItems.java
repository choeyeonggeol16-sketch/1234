package l1j.server.server.model.item.function;


import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import java.util.Random;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.monitor.LoggerInstance;


import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;


import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.TransformationItems.*;
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

public final class EnchantmentItems {
    private static final Random _random = CreatureItemActions._random;
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private EnchantmentItems() {}
	public static void AttrEnchant(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance weapon) {
		AttributeEnchantScrollEffects.AttrEnchant(pc, scroll, weapon);
	}

	public static void enchant_insignia(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		InsigniaProtectionScrolls.enchant_insignia(pc, scroll, target);
	}

	public static void enchant_should(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		ShoulderProtectionScrolls.enchant_should(pc, scroll, target);
	}

	public static void enchant_sentence(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		EmblemProtectionScrolls.enchant_sentence(pc, scroll, target);
	}

	public static void enchant_roomtis(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		RoomtisProtectionScrolls.enchant_roomtis(pc, scroll, target);
	}

	public static void enchant_snapper(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		SnapperProtectionScrolls.enchant_snapper(pc, scroll, target);
	}

	
	public static void enchant_insignia1(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		InsigniaProtectionScrolls.enchant_insignia1(pc, scroll, target);
	}

	

	public static void enchant_sentence1(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		EmblemProtectionScrolls.enchant_sentence1(pc, scroll, target);
	}

	public static void enchant_roomtis1(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		RoomtisProtectionScrolls.enchant_roomtis1(pc, scroll, target);
	}

	public static void enchant_snapper1(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		SnapperProtectionScrolls.enchant_snapper1(pc, scroll, target);
	}

	
	
	
	public static void enchant_bless_orim(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
		OrimProtectionScrolls.enchant_bless_orim(pc, scroll, target);
	}

	public static void SuccessEnchant(L1PcInstance pc, L1ItemInstance item, CUserSocket client, int i) {
        EnchantmentOutcomeEffects.SuccessEnchant(pc, item, client, i);
    }

	public static void FailureEnchant(L1PcInstance pc, L1ItemInstance item, CUserSocket client) {
        EnchantmentOutcomeEffects.FailureEnchant(pc, item, client);
    }

}
