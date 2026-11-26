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

/** Original usePolyPotion item use implementation. */
final class PolymorphPotionEffects {
    private PolymorphPotionEffects() {}

	public static void usePolyPotion(L1PcInstance pc, int itemId) {
		int polyId = 0;
		if ((pc.getLevel() >= 83)) {
			pc.sendPackets("해당 아이템은 82 레벨까지만 사용 가능합니다.");
			return;
		}
		if (itemId == 41143) {
			polyId = 6086;
		} else if (itemId == 41144) {
			polyId = 6087;
		} else if (itemId == 41145) {
			polyId = 6088;
		} else if (itemId == 30057) {
			polyId = 10429;
		} else if (itemId == 30058) {
			polyId = 10431;
		} else if (itemId == 30059) {
			polyId = 10430;
		} else if (itemId == 8000) {
			polyId = 12792;
		} else if (itemId == 8001) { // 7580랜스마스터변신
			polyId = 12237;
		} else if (itemId == 8002) { // 82진데스나이트-완전빠름
			polyId = 12015;
		} else if (itemId == 42655) { // 신규영웅주문서.
			if (pc.isKnight() || pc.isDragonknight() || pc.isWarrior() || pc.isCrown()) {
				polyId = 12283;
			} else if (pc.isDarkelf()) {
				polyId = 12280;
			} else if (pc.isWizard()) {
				polyId = 12295;
			} else if (pc.isBlackwizard()) {
				polyId = 12286;
			} else if (pc.isElf()) {
				polyId = 12314;
			}
		} else if (itemId == 4100290) { // 진 변신 주문서
			if (pc.isKnight() || pc.isWarrior() || pc.isCrown()) {
				polyId = 17541;
			} else if (pc.isDragonknight()) {
				polyId = 17549;
			} else if (pc.isDarkelf()) {
				polyId = 17531;
			} else if (pc.isWizard()) {
				polyId = 17515;
			} else if (pc.isBlackwizard()) {
				polyId = 12286;
			} else if (pc.isElf()) {
				polyId = 17535;
			}
		} else if (itemId == 4100284) {// 진 데스나이트 변신 주문서
			polyId = 17541;
		} else if (itemId == 4100285) {// 진 쉐도우 변신 주문서
			polyId = 17531;
		} else if (itemId == 4100286) {// 진 랜스마스터 변신 주문서
			polyId = 17545;
		} else if (itemId == 4100287) {// 진 바포메트 변신 주문서
			polyId = 17515;
		} else if (itemId == 4100288) {// 진 경비병(활) 변신 주문서
			polyId = 17535;
		} else if (itemId == 4100289) {// 진 경비병(창) 변신 주문서
			polyId = 17549;
		} else if (itemId == 4200100) {// 도펠 주문서
			polyId = 20524;
		} else if (itemId == 4200200) {// 도펠 주문서
			polyId = 18255;
		} else if (itemId == 4200300) {// 도펠 주문서
			polyId = 18260;
		} else if (itemId == 4200400) {// 도펠 주문서
			polyId = 14491;
		} else if (itemId == 4200500) {// 도펠 주문서
			polyId = 19267;
		} else if (itemId == 5922901) {// 도펠 주문서
					polyId = 18402;
			
	}
		L1PolyMorph.doPoly(pc, polyId, 1800, L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
	//	L1NeigeBugCheck.shapechange(pc);
	}
}
