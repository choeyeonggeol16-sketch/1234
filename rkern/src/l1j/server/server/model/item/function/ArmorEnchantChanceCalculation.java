package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.datatables.AccessoryEnchantInformationTable;
import l1j.server.server.datatables.ArmorEnchantInformationTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
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

/** Exact existing accessory/armor rate calculation and GM chance diagnostics. */
final class ArmorEnchantChanceCalculation {
    private ArmorEnchantChanceCalculation() {}

    static int calculate(L1PcInstance pc, L1ItemInstance targetItem,
                         int armorId, int armortype, int enchant_level, int rnd) {
        int enchant_chance_armor = 0;
        // BEGIN_ORIGINAL_ARMORENCHANTCHANCECALCULATION
                if (armortype >= 8 && armortype <= 12 || armortype == 30 || armortype == 28) {

                    int chance = AccessoryEnchantInformationTable.getInstance().getChance(armorId,
                            enchant_level);

                    if (enchant_level >= 0 && enchant_level < 1) {
                        enchant_chance_armor = Config.악세사리1 + chance;
                    }
                    if (enchant_level >= 1 && enchant_level < 2) {
                        enchant_chance_armor = Config.악세사리2 + chance;
                    }
                    if (enchant_level >= 2 && enchant_level < 3) {
                        enchant_chance_armor = Config.악세사리3 + chance;
                    }
                    if (enchant_level >= 3 && enchant_level < 4) {
                        enchant_chance_armor = Config.악세사리4 + chance;
                    }
                    if (enchant_level >= 4 && enchant_level < 5) {
                        enchant_chance_armor = Config.악세사리5 + chance;
                    }
                    if (enchant_level >= 5 && enchant_level < 6) {
                        enchant_chance_armor = Config.악세사리6 + chance;
                    }
                    if (enchant_level >= 6 && enchant_level < 7) {
                        enchant_chance_armor = Config.악세사리7 + chance;
                    }
                    if (enchant_level >= 7 && enchant_level < 8) {
                        enchant_chance_armor = Config.악세사리8 + chance;
                    }
                    if (enchant_level >= 8 && enchant_level < 9) {
                        enchant_chance_armor = Config.악세사리9 + chance;
                    }
                    if (enchant_level >= 9 && enchant_level < 10) {
                        enchant_chance_armor = Config.악세사리10 + chance;
                    }
                    if (enchant_level >= 10 && enchant_level < 11) {
                        enchant_chance_armor = Config.악세사리11 + chance;
                    }
                    if (enchant_level >= 11 && enchant_level < 12) {
                        enchant_chance_armor = Config.악세사리12 + chance;
                    }
                    if (enchant_level >= 12 && enchant_level < 13) {
                        enchant_chance_armor = Config.악세사리13 + chance;
                    }
                    if (enchant_level >= 13 && enchant_level < 14) {
                        enchant_chance_armor = Config.악세사리14 + chance;
                    }
                    if (enchant_level >= 14 && enchant_level < 15) {
                        enchant_chance_armor = Config.악세사리15 + chance;
                    }

                    if (pc.isGm()) {
                        SC_SystemMessage.send(pc, "\\aA확률 : [\\aG " + enchant_chance_armor + "\\aA ]");
                        SC_SystemMessage.send(pc, "\\aA찬스 : [\\aG " + rnd + " \\aA]");
                        SC_SystemMessage.send(pc, "\\aA추가확률 : [\\aG " + chance + " \\aA]");

                    }

                } else {

                    int chance = ArmorEnchantInformationTable.getInstance().getChance(armorId,
                            enchant_level);
                    if (targetItem.getItem().get_safeenchant() != 0 &&
                            enchant_level >= 4 && enchant_level < 5) {
                        enchant_chance_armor = Config.방어구1 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() != 0 &&
                            enchant_level >= 5 && enchant_level < 8) {
                        enchant_chance_armor = Config.방어구2 + chance ;
                    }
                    if (targetItem.getItem().get_safeenchant() != 0 &&
                            enchant_level >= 6 && enchant_level < 7) {
                        enchant_chance_armor = Config.방어구3 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() != 0 &&
                            enchant_level >= 7 && enchant_level < 8) {
                        enchant_chance_armor = Config.방어구4 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() != 0 &&
                            enchant_level >= 8 && enchant_level < 9) {
                        enchant_chance_armor = Config.방어구5 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() != 0 &&
                            enchant_level >= 9 && enchant_level < 10) {
                        enchant_chance_armor = Config.방어구6 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() != 0 &&
                            enchant_level >= 10 && enchant_level < 11) {
                        enchant_chance_armor = Config.방어구7 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() != 0 &&
                            enchant_level >= 11 && enchant_level < 12) {
                        enchant_chance_armor = Config.방어구8 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() != 0 &&
                            enchant_level >= 12 && enchant_level < 13) {
                        enchant_chance_armor = Config.방어구9 + chance;
                    }

                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 0 && enchant_level < 1) {
                        enchant_chance_armor = Config.안전방어구1 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 1 && enchant_level < 2) {
                        enchant_chance_armor = Config.안전방어구2 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 2 && enchant_level < 3) {
                        enchant_chance_armor = Config.안전방어구3 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 3 && enchant_level < 4) {
                        enchant_chance_armor = Config.안전방어구4 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 4 && enchant_level < 5) {
                        enchant_chance_armor = Config.안전방어구5 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 5 && enchant_level < 6) {
                        enchant_chance_armor = Config.안전방어구6 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 6 && enchant_level < 7) {
                        enchant_chance_armor = Config.안전방어구7 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 7 && enchant_level < 8) {
                        enchant_chance_armor = Config.안전방어구8 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 8 && enchant_level < 9) {
                        enchant_chance_armor = Config.안전방어구9 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 9 && enchant_level < 10) {
                        enchant_chance_armor = Config.안전방어구10 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 10 && enchant_level < 11) {
                        enchant_chance_armor = Config.안전방어구11 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 11 && enchant_level < 12) {
                        enchant_chance_armor = Config.안전방어구12 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 12 && enchant_level < 13) {
                        enchant_chance_armor = Config.안전방어구13 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 13 && enchant_level < 14) {
                        enchant_chance_armor = Config.안전방어구14 + chance;
                    }
                    if (targetItem.getItem().get_safeenchant() == 0 &&
                            enchant_level >= 14 && enchant_level < 15) {
                        enchant_chance_armor = Config.안전방어구15 + chance;
                    }

                    if (pc.isGm()) {
                        SC_SystemMessage.send(pc, "\\aA확률 : [\\aG " + enchant_chance_armor + "\\aA ]");
                        SC_SystemMessage.send(pc, "\\aA찬스 : [\\aG " + rnd + " \\aA]");
                        SC_SystemMessage.send(pc, "\\aA추가확률 : [\\aG " + chance + " \\aA]");

                    }

                }

        // END_ORIGINAL_ARMORENCHANTCHANCECALCULATION
        return enchant_chance_armor;
    }
}
