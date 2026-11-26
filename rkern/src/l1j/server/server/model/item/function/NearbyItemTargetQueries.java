package l1j.server.server.model.item.function;



import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.model.world.residence.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SupportInstance;



import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
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

public final class NearbyItemTargetQueries {
    private NearbyItemTargetQueries() {}

	public static ArrayList<L1NpcInstance> get어드밴스1(L1Object object, int radius) {
		int map = object.getMapId();
		Point pt = object.getLocation();
		Collection<L1NpcInstance> pc1 = L1World.getInstance().getAllNpc();
		ArrayList<L1NpcInstance> result = null;
		if(radius < 0)
			result = new ArrayList<L1NpcInstance>(19 * 4);
		else if(radius == 0)
			result = new ArrayList<L1NpcInstance>(2);
		else
			result = new ArrayList<L1NpcInstance>(radius * 4);
		for (L1NpcInstance element : pc1) {
			if ((element == null) || (element.equals(object)) || (map != element.getMapId()))
				continue;
			if (radius == -1) {
				if (pt.isInScreen(element.getLocation()))
					result.add(element);
			} else if (radius == 0) {
				if (pt.isSamePoint(element.getLocation())) {
					result.add(element);
				}
			} else if (pt.getTileLineDistance(element.getLocation()) <= radius) {
				result.add(element);
			
			}
		}

		return result;
	}

	public static ArrayList<L1PcInstance> get어드밴스(L1PcInstance pc, L1Object object, int radius) {
		int map = object.getMapId();
		Point pt = object.getLocation();
		Collection<L1PcInstance> pc1 = L1World.getInstance().getAllPlayers();
		ArrayList<L1PcInstance> result = null;
		if(radius < 0)
			result = new ArrayList<L1PcInstance>(19 * 4);
		else if(radius == 0)
			result = new ArrayList<L1PcInstance>(2);
		else
			result = new ArrayList<L1PcInstance>(radius * 4);
		for (L1PcInstance element : pc1) {
			if ((element == null) || (element.equals(object)) || (map != element.getMapId()))
				continue;
			if (radius == -1) {
				if (pt.isInScreen(element.getLocation()))
					result.add(element);
			} else if (radius == 0) {
				if (pt.isSamePoint(element.getLocation())) {
					result.add(element);
				}
			} else {
                final int distance = pt.getTileLineDistance(element.getLocation());
                if (distance <= radius) {
                    result.add(element);
                } else if (distance > radius) {
                    SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fY거리가 멀어서 사용할 수 없습니다."); 
                    SC_SystemMessage.send(pc, "\\fY거리가 멀어서 사용할 수 없습니다.");
                }
            }
		}

		return result;
	}
}
