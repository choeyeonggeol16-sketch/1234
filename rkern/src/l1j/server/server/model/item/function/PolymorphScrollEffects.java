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

/** Original usePolyScroll item use implementation. */
final class PolymorphScrollEffects {
    private PolymorphScrollEffects() {}

	public static boolean usePolyScroll(L1PcInstance pc, int item_id, String s) {
//		 System.out.println(s);//변신 액션찾을때
		int time = 0;
		boolean ring = false;
		switch (item_id) {
		case 40088:
		case 40096:
			
			time = 1800;
			break;
		case 900075:
			//ring = true;
			time = 3600;
			break;
			
		case 140088:
			time = 2100;
			break;
		case 210112:
			time = 3600;
			break;
		case 40008:
			time = 7200;
			break;
		case 140008:
			time = 7200;
			break;
		default:
			return false;
		}
		if (s.startsWith("maple")) {
			String aa = s;
			String bb = aa.replace("maple ", "");
			s = bb;
		}
		if (s.equalsIgnoreCase("ranking class polymorph")) {
            s = RankingClassPolymorphNames.select(pc, s);
        }
		if (s.equalsIgnoreCase("myth class polymorph")) {
            s = MythicClassPolymorphNames.select(pc, s);
        }
		if (s.equalsIgnoreCase("ranking class polymorph1")) {
            s = AlternateRankingPolymorphNames.select(pc, s);
        }
		if (s.startsWith("rangking ")) {
			if (!RankUserLoader.getInstance().isRankPoly(pc))
				return false;
		}
		if (s.startsWith("maple")) {
			if (!pc.isPolyRingMaster())
				return false;
		}
		if (s.equalsIgnoreCase("girtas")) {
			if (pc.getAccount().getInfo_kir_poly() != 1){
				pc.sendPackets("코드 :" + pc.getAccount().getInfo_kir_poly());
				return false;
			}
		}
		L1PolyMorph poly = PolyTable.getInstance().getTemplate(s);
		if (pc.isGm()) {
		      SC_SystemMessage.send(pc, "\\f:변신이름 >> " + s);
		    }
		
		if (poly != null || s.equals("")) {
			if (s.equals("")) {
				int spriteId = pc.getCurrentSpriteId();
				if (spriteId == 6034 || spriteId == 6035) {
					return true;
				} else {
					pc.removeSkillEffect(SHAPE_CHANGE);
					if (pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
						pc.removeSkillEffect(L1SkillId.POLY_RING_MASTER);
					}
				//	L1NeigeBugCheck.shapechange(pc);
					return true;
				}
			} else if (poly.getMinLevel() <= pc.getLevel() || pc.isGm()) {
				if (((item_id == 40008 || item_id == 140008 || item_id == 40410 ) && pc.isPolyRingMaster()) || item_id == 900075 ) {
					ring = true;
				}
				L1PolyMorph.doPoly(pc, poly.getPolyId(), time, L1PolyMorph.MORPH_BY_ITEMMAGIC, ring);
			///.	L1NeigeBugCheck.shapechange(pc);
				try
		        {
		          if (pc.isGm()) {
		            SC_SystemMessage.send(pc, "\\f:변신번호  >> " + pc.getCurrentSpriteId());
		          }
		        }
		        catch (Exception localException) {}
				
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}
}
