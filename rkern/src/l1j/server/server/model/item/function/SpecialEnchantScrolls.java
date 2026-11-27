package l1j.server.server.model.item.function;



import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;



import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;


import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;


import static l1j.server.server.model.item.function.EnchantmentItems.*;
import static l1j.server.server.model.item.function.PotionEffects.*;
import static l1j.server.server.model.item.function.TransformationItems.*;
import static l1j.server.server.model.item.function.SpellBookItems.*;
import static l1j.server.server.model.item.function.TowerTeleportAmulets.*;
import static l1j.server.server.model.item.function.PetWithdrawalItems.*;
import static l1j.server.server.model.item.function.NearbyItemTargetQueries.*;
import static l1j.server.server.model.item.function.ResolventItemFacade.*;
import static l1j.server.server.model.item.function.SupportSummonItems.*;
import static l1j.server.server.model.item.function.FurniturePlacementItems.*;
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

public final class SpecialEnchantScrolls {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private SpecialEnchantScrolls() {}
	public static void 악세인챈(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        AccessoryBlessingScrollEffects.악세인챈(pc, scroll, target);
    }

public static void 악세인챈100(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        AccessoryBlessingScrollEffects.악세인챈100(pc, scroll, target);
    }
public static void 집행진화주문서(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        WeaponEvolutionScrollEffects.집행진화주문서(pc, scroll, target);
    }
public static void 방어구축복100(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        ArmorBlessingScrollEffects.방어구축복100(pc, scroll, target);
    }
public static void 방어구축복(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        ArmorBlessingScrollEffects.방어구축복(pc, scroll, target);
    }
	public static void 무기축복(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        WeaponBlessingScrollEffects.무기축복(pc, scroll, target);
    }
	public static void 무기축복100(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        WeaponBlessingScrollEffects.무기축복100(pc, scroll, target);
    }
public static boolean isOwnedScrollTarget(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
    return pc != null && scroll != null && target != null && scroll != target
            && scroll.getItem() != null
            && target.getItem() != null
            && scroll.getCount() > 0 && target.getCount() > 0
            && pc.getInventory().GetByServerID(scroll.getId()) == scroll
            && pc.getInventory().GetByServerID(target.getId()) == target;
}

public static boolean isAccessoryEnchantTarget(L1ItemInstance target) {
    if (target == null || target.getItem() == null || target.getItem().getType2() != 2) return false;
    final int type = target.getItem().getType();
    return type == 9 || type == 11 || type == 8;
}

public static int qualityBonus(int roll) {
    if (roll >= 30) return 15;
    if (roll >= 20) return 30;
    if (roll >= 10) return 60;
    if (roll >= 5) return 80;
    return -1;
}

public static void 옵션부여(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        ItemOptionScrollEffects.옵션부여(pc, scroll, target);
    }

public static void 품질부여주문서(L1PcInstance pc, L1ItemInstance scroll, L1ItemInstance target) {
        ItemOptionScrollEffects.품질부여주문서(pc, scroll, target);
    }

}
