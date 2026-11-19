package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.datatables.WeaponEnchantInformationTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** Exact original weapon enchant chance and GM diagnostics; no additional RNG calls. */
final class WeaponEnchantChanceCalculation {
    private WeaponEnchantChanceCalculation() {}

    static int calculate(L1PcInstance pc, L1ItemInstance targetItem,
                         int weaponId, int enchant_level, int rnd) {
        // BEGIN_ORIGINAL_WEAPONENCHANTCHANCECALCULATION
                                int enchant_chance_wepon = 0;

                                int chance = WeaponEnchantInformationTable.getInstance().getChance(weaponId,
                                        enchant_level);
                                if (targetItem.getItem().get_safeenchant() != 0 &&
                                        enchant_level >= 6 && enchant_level < 7) {
                                    enchant_chance_wepon = Config.무기1 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() != 0 &&
                                        enchant_level >= 7 && enchant_level < 8) {
                                    enchant_chance_wepon = Config.무기2 + chance ;
                                }
                                if (targetItem.getItem().get_safeenchant() != 0 &&
                                        enchant_level >= 8 && enchant_level < 9) {
                                enchant_chance_wepon = Config.무기3 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() != 0 &&
                                        enchant_level >= 9 && enchant_level < 10) {
                                    enchant_chance_wepon = Config.무기4 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() != 0 &&
                                        enchant_level >= 10 && enchant_level < 11) {
                                    enchant_chance_wepon = Config.무기5 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() != 0 &&
                                        enchant_level >= 11 && enchant_level < 12) {
                                    enchant_chance_wepon = Config.무기6 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() != 0 &&
                                        enchant_level >= 12 && enchant_level < 13) {
                                    enchant_chance_wepon = Config.무기7 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() != 0 &&
                                        enchant_level >= 13 && enchant_level < 14) {
                                    enchant_chance_wepon = Config.무기8 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() != 0 &&
                                        enchant_level >= 14 && enchant_level < 15) {
                                    enchant_chance_wepon = Config.무기9 + chance;
                                }

                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 0 && enchant_level < 1) {
                                    enchant_chance_wepon = Config.안전무기1 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 1 && enchant_level < 2) {
                                enchant_chance_wepon = Config.안전무기2 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 2 && enchant_level < 3) {
                                    enchant_chance_wepon = Config.안전무기3 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 3 && enchant_level < 4) {
                                    enchant_chance_wepon = Config.안전무기4 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 4 && enchant_level < 5) {
                                    enchant_chance_wepon = Config.안전무기5 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 5 && enchant_level < 6) {
                                    enchant_chance_wepon = Config.안전무기6 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 6 && enchant_level < 7) {
                                    enchant_chance_wepon = Config.안전무기7 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 7 && enchant_level < 8) {
                                    enchant_chance_wepon = Config.안전무기8 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 8 && enchant_level < 9) {
                                    enchant_chance_wepon = Config.안전무기9 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 9 && enchant_level < 10) {
                                    enchant_chance_wepon = Config.안전무기10 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 10 && enchant_level < 11) {
                                    enchant_chance_wepon = Config.안전무기11 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 11 && enchant_level < 12) {
                                    enchant_chance_wepon = Config.안전무기12 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 12 && enchant_level < 13) {
                                    enchant_chance_wepon = Config.안전무기13 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 13 && enchant_level < 14) {
                                    enchant_chance_wepon = Config.안전무기14 + chance;
                                }
                                if (targetItem.getItem().get_safeenchant() == 0 &&
                                        enchant_level >= 14 && enchant_level < 15) {
                                    enchant_chance_wepon = Config.안전무기15 + chance;
                                }

                                if (pc.isGm()) {
                                    SC_SystemMessage.send(pc, "\\aA확률 : [\\aG " + enchant_chance_wepon + "\\aA ]");
                                    SC_SystemMessage.send(pc, "\\aA찬스 : [\\aG " + rnd + " \\aA]");
                                    SC_SystemMessage.send(pc, "\\aA추가확률 : [\\aG " + chance + " \\aA]");

                                }

        // END_ORIGINAL_WEAPONENCHANTCHANCECALCULATION
        return enchant_chance_wepon;
    }
}
