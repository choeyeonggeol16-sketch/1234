package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.datatables.AccessoryEnchantInformationTable;
import l1j.server.server.datatables.ArmorEnchantInformationTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CUserSession;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Armor;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.CreatureItemActions._random;
import static l1j.server.server.model.item.function.EnchantmentItems.FailureEnchant;
import static l1j.server.server.model.item.function.EnchantmentItems.SuccessEnchant;
import static l1j.server.server.model.item.function.EnchantmentItems.enchant_bless_orim;
import static l1j.server.server.model.item.function.EnchantmentItems.enchant_insignia;
import static l1j.server.server.model.item.function.EnchantmentItems.enchant_insignia1;
import static l1j.server.server.model.item.function.EnchantmentItems.enchant_roomtis;
import static l1j.server.server.model.item.function.EnchantmentItems.enchant_roomtis1;
import static l1j.server.server.model.item.function.EnchantmentItems.enchant_sentence;
import static l1j.server.server.model.item.function.EnchantmentItems.enchant_sentence1;
import static l1j.server.server.model.item.function.EnchantmentItems.enchant_should;
import static l1j.server.server.model.item.function.EnchantmentItems.enchant_snapper;
import static l1j.server.server.model.item.function.EnchantmentItems.enchant_snapper1;
import static l1j.server.server.model.item.function.TransformationItems.RandomELevel;

/** Armor enchant domain: extracted unchanged from the Phase 9 item handler. */
final class ArmorEnchantLevelLimits {
    private ArmorEnchantLevelLimits() {}

    static ItemActionResult validate(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CUserSession client = use.client();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        int safe_enchant = ((L1Armor) targetItem.getItem()).get_safeenchant();
        int armorId = targetItem.getItem().getItemId();
        int armortype = targetItem.getItem().getType();
        // BEGIN_ORIGINAL_ARMORENCHANTLEVELLIMITS
            int enchant_level = targetItem.getEnchantLevel();

            if (armorId >= 22229 && armorId <= 22231 || armorId >= 222337 && armorId <= 222339
                    || armorId >= 222340 && armorId <= 222341) {
                if (enchant_level >= Config.룸티스) {
                    SC_SystemMessage.send(pc, "룸티스귀걸이는 +" + Config.룸티스 + "이상은 인챈할 수 없습니다.");
                    SC_SystemMessage.send(pc, "제한해제시 공지 하겠습니다.");
                    return ItemActionResult.STOP;
                }
            } else if (armorId >= 22224 && armorId <= 22228 || armorId == 222290 || armorId == 222291

                    || armorId >= 222330 && armorId <= 222336) {
                if (enchant_level >= Config.스냅퍼) {
                    SC_SystemMessage.send(pc, "스냅퍼 반지는 +" + Config.스냅퍼 + " 이상은 인챈할 수 없습니다.");
                    SC_SystemMessage.send(pc, "제한해제시 공지 하겠습니다.");
                    return ItemActionResult.STOP;
                }
            } else if (armorId >= 900020 && armorId <= 900021 || armorId >= 900049 && armorId <= 900051
                    || armorId >= 900093 && armorId <= 900099 || armorId >= 900124 && armorId <= 900126
                    || armorId >= 900127 && armorId <= 900130) {
                if (enchant_level >= Config.문장류) {
                    SC_SystemMessage.send(pc, "문장류는 +" + Config.문장류 + " 이상은 인챈할 수 없습니다.");
                    SC_SystemMessage.send(pc, "제한해제시 공지 하겠습니다.");
                    return ItemActionResult.STOP;
                }
            } else if (armortype == 30) {
                if (enchant_level >= Config.휘장류) {
                        SC_SystemMessage.send(pc, "휘장은 +" + Config.휘장류 + " 이상은 인챈할 수 없습니다.");
                        SC_SystemMessage.send(pc, "제한해제시 공지 하겠습니다.");
                        return ItemActionResult.STOP;

                }

            } else if (armortype >= 8 && armortype <= 12) {
                if (!(armorId >= 22229 && armorId <= 22231 || armorId >= 222337 && armorId <= 222339
                        || armorId >= 222340 && armorId <= 222341 || armorId >= 22224 && armorId <= 22228
                        || armorId == 222290 || armorId == 222291 || armorId >= 222330 && armorId <= 222336)) {
                    if (enchant_level >= Config.악세사리) {
                        SC_SystemMessage.send(pc, "악세사리는 +" + Config.악세사리 + " 이상은 인챈할 수 없습니다.");
                        SC_SystemMessage.send(pc, "제한해제시 공지 하겠습니다.");
                        return ItemActionResult.STOP;
                    }
                }
            } else {
                if (safe_enchant == 0) {
                    if (enchant_level >= Config.방어구고급인첸트) {
                        if (itemId == L1ItemId.C_SCROLL_OF_ENCHANT_ARMOR || itemId == L1ItemId.ENCHANT_ARMOR) {

                            pc.getInventory().removeItem(item, 1);
                            SuccessEnchant(pc, targetItem, client, -1);

                        } else {
                            SC_SystemMessage.send(pc, "방어구는 +" + Config.방어구고급인첸트 + "이상 강화할 수 없습니다.");
                        }
                        return ItemActionResult.STOP;
                    }
                } else {
                    if (enchant_level >= Config.방어구인첸트) {
                        SC_SystemMessage.send(pc, "방어구는 +" + Config.방어구인첸트 + "이상 강화할 수 없습니다.");
                        return ItemActionResult.STOP;
                    }
                }
            }
        // END_ORIGINAL_ARMORENCHANTLEVELLIMITS
        return ItemActionResult.COMPLETE;
    }
}
