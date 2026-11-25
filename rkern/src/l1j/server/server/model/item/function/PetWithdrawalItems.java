package l1j.server.server.model.item.function;



import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SupportInstance;



import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.types.Point;




import static l1j.server.server.model.item.function.EnchantmentItems.*;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.TransformationItems.*;
import static l1j.server.server.model.item.function.SpellBookItems.*;
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

public final class PetWithdrawalItems {
    private PetWithdrawalItems() {}

	public static boolean canWithdrawPet(L1PcInstance pc, int itemObjectId) {
		if (!pc.getMap().isTakePets()) {
			SC_ServerMessage.send(pc, 563); // \f1 여기에서는 사용할 수 없습니다.
			return false;
		}

		if (pc.getMapId() == 781 || pc.getMapId() == 782) {
			SC_ServerMessage.send(pc, 563); // \f1 여기에서는 사용할 수 없습니다.
			return false;
		}

		if (pc.isInWarArea()) {
			SC_ServerMessage.send(pc, 563); // \f1 여기에서는 사용할 수 없습니다.
			return false;
		}

		int petCost = 0;
		Object[] petList = pc.getPetList().values().toArray();
		for (Object pet : petList) {
			if (pet instanceof L1PetInstance) {
				if (((L1PetInstance) pet).getItemObjId() == itemObjectId) {
					return false;
				}
			}

			if (pet instanceof L1PetInstance) {
				petCost += ((L1NpcInstance) pet).getPetcost();
			}
		}
		int charisma = pc.getAbility().getTotalCha();
		if (pc.isCrown()) { // CROWN
			charisma += 6;
		} else if (pc.isElf()) { // ELF
			charisma += 12;
		} else if (pc.isWizard()) { // WIZ
			charisma += 6;
		} else if (pc.isDarkelf()) { // DE
			charisma += 6;
		} else if (pc.isDragonknight()) { // 용기사
			charisma += 6;
		} else if (pc.isBlackwizard()) { // 환술사
			charisma += 6;
		}

		charisma -= petCost;
		int petCount = charisma / 6;
		if (petCount <= 0) {
			SC_ServerMessage.send(pc, 489); // 물러가려고 하는 애완동물이 너무 많습니다.
			return false;
		}

		L1Pet l1pet = PetTable.getInstance().getTemplate(itemObjectId);
        L1ItemInstance amulet = pc.getInventory().GetByServerID(itemObjectId);
        if (l1pet == null || amulet == null
                || (amulet.getItemId() != 40314 && amulet.getItemId() != 40316)) return false;
        return CObjectDB.LoadNPC().getTemplate(l1pet.get_npcid()) != null;
	}

	public static boolean withdrawPet(L1PcInstance pc, int itemObjectId) {
        // Revalidate after the consumable receipt commits: map, active pets,
        // ownership and the NPC template may have changed while awaiting CacheD.
        if (!canWithdrawPet(pc, itemObjectId)) return false;
        L1Pet l1pet = PetTable.getInstance().getTemplate(itemObjectId);
        L1Npc npcTemp = CObjectDB.LoadNPC().getTemplate(l1pet.get_npcid());
        if (npcTemp == null) return false;
        L1PetInstance pet = new L1PetInstance(npcTemp, pc, l1pet);
        pet.setPetcost(6);
        return true;
	}
}
