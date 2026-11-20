package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.EnchantmentItems.SuccessEnchant;

/** Weapon enchant pipeline: original Phase 10 branch, with unchanged decision order. */
final class WeaponEnchantLevelLimits {
    private WeaponEnchantLevelLimits() {}

    static ItemActionResult validate(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CUserSocket client = use.client();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        int safe_enchant = targetItem.getItem().get_safeenchant();
        int weaponId = targetItem.getItem().getItemId();
        // BEGIN_ORIGINAL_WEAPONENCHANTLEVELLIMITS
                            int enchant_level = targetItem.getEnchantLevel();

                            if (enchant_level >= Config.무기인첸트 && (!(itemId >= 210064 && itemId <= 210067 || itemId >= 310064 && itemId <= 310068|| itemId >= 560030 &&  itemId <= 560033))) {
                                SC_SystemMessage.send(pc, "무기는 +" + Config.무기인첸트 + "이상 강화할 수 없습니다.");
                                return ItemActionResult.STOP;
                            }
                            if (safe_enchant == 0) {
                                if (enchant_level >= Config.무기고급인첸트 && (!(itemId >= 210064 && itemId <= 210067|| itemId >= 310064 && itemId <= 310068 || itemId >= 560030 &&  itemId <= 560033))){
                                    if (itemId == L1ItemId.C_SCROLL_OF_ENCHANT_WEAPON || itemId == L1ItemId.ENCHANT_WEAPONA) {
                                        pc.getInventory().removeItem(item, 1);
                                        SuccessEnchant(pc, targetItem, client, -1);
                                    } else {
                                        SC_SystemMessage.send(pc, "무기는 +" + Config.무기고급인첸트 + "이상 강화할 수 없습니다.");
                                    }
                                    return ItemActionResult.STOP;
                                }
                            }
        // END_ORIGINAL_WEAPONENCHANTLEVELLIMITS
        return ItemActionResult.COMPLETE;
    }
}
