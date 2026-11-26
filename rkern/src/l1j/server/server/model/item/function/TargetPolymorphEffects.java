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

import static l1j.server.server.model.item.function.TransformationItems.usePolyScroll;
/** Original polyAction item use implementation. */
final class TargetPolymorphEffects {
    private TargetPolymorphEffects() {}
    // Fixed maple-wand transformation pool; the RNG call sequence is unchanged.
    private static final int[] POLYMORPH_IDS = { 29, 979, 1037, 1039, 3860, 3861, 3862, 3863, 3864, 3865, 3904, 3906, 95, 146, 2374, 2376,
				2377, 2378, 3866, 3867, 3868, 3869, 3870, 3871, 3872, 3873, 3874, 3875, 3876, 3882, 3883, 3884, 3885,
				11358, 11396, 11397, 12225, 12226, 11399, 11398, 12227 };
    private static final Random _random = CreatureItemActions._random;

	public static void polyAction(L1PcInstance attacker, L1Character cha, int itemId, String s) {
		boolean isSameClan = false;
		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			if (pc.getClanid() != 0 && attacker.getClanid() == pc.getClanid()) {
				isSameClan = true;
			}

			if (attacker.getId() != pc.getId() && pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
				// 대상이 변신 지배 반지로 변신했을경우
				SC_SkillSound.send(pc, pc.getId(), 15846);
				SC_SkillSound.broadcast(pc, pc.getId(), 15846);
				SC_ServerMessage.send(attacker, 280);
				return;
			}
		}
		if (cha instanceof L1MonsterInstance) {
			return;
		}
		if (attacker.getId() != cha.getId() && !isSameClan) {
			int probability = 3 * (attacker.getLevel() - cha.getLevel()) - cha.GetMDefendBySkill();
			int rnd = _random.nextInt(100) + 1;
			if (rnd > probability) {
				SC_ServerMessage.send(attacker, 280);
				return;
			}
		}
		int pid = _random.nextInt(POLYMORPH_IDS.length);
		int polyId = POLYMORPH_IDS[pid];

		if (cha instanceof L1PcInstance) {
			L1PcInstance pc = (L1PcInstance) cha;
			if (pc.getInventory().IsEquippedByClassID(20281) || pc.isPolyRingMaster()) {
				if (usePolyScroll(pc, itemId, s)) {
				} else {
					SC_ServerMessage.send(pc, 181);
				}
			} else {
				L1Skills skillTemp = SkillsTable.getInstance().getTemplate(SHAPE_CHANGE);
				L1PolyMorph.doPoly(pc, polyId, skillTemp.getBuffDuration(), L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
				if (attacker.getId() != pc.getId()) {
					SC_ServerMessage.send(pc, 241, attacker.getName()); // 누구가
					// 당신을
					// 변신시켰습니다.
				}
			}
		} else if (cha instanceof L1MonsterInstance) {
			L1MonsterInstance mob = (L1MonsterInstance) cha;
			if (mob.getLevel() < 50) {
				int npcId = mob.getNpcTemplate().get_npcId();
				if (npcId != 45338 && npcId != 45370 && npcId != 45456 && npcId != 45464 && npcId != 45473
						&& npcId != 45488 && npcId != 45497 && npcId != 45516 && npcId != 45529 && npcId != 45458) {
					L1Skills skillTemp = SkillsTable.getInstance().getTemplate(SHAPE_CHANGE);
					L1PolyMorph.doPoly(mob, polyId, skillTemp.getBuffDuration(), L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
				}
			}
		}
	}
}
