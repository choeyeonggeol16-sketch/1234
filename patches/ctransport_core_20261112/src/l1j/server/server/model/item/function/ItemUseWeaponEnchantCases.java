package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.datatables.WeaponEnchantInformationTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.ItemUseCreature._random;
import static l1j.server.server.model.item.function.ItemUseEnchantments.AttrEnchant;
import static l1j.server.server.model.item.function.ItemUseEnchantments.FailureEnchant;
import static l1j.server.server.model.item.function.ItemUseEnchantments.SuccessEnchant;
import static l1j.server.server.model.item.function.ItemUseTransformation.RandomELevel;

/** Item-specific WeaponEnchant effects; selected once by ItemUseDispatcher. */
final class ItemUseWeaponEnchantCases {
    private ItemUseWeaponEnchantCases() {}

    static ItemUseResult use(ItemUseContext use) {
        L1PcInstance pc = use.player();
        CIOClient client = use.client();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        {
                            if (pc.getIsChangeItem5() == true ||pc.getIsChangeItem1() == true|| pc.getIsChangeItem2() == true|| pc.getIsChangeItem3() == true|| pc.getIsChangeItem4() == true ||
                                    pc.getIsChangeItem6() == true ||pc.getIsChangeItem7() == true|| pc.getIsChangeItem8() == true|| pc.getIsChangeItem9() == true|| pc.getIsChangeItem10() == true ||
                                    pc.getIsChangeItem11() == true|| pc.getIsChangeItem12() == true|| pc.getIsChangeItem13() == true|| pc.getIsChangeItem14() == true|| pc.getIsChangeItem15() == true ||
                                    pc.getIsChangeItem16() == true|| pc.getIsChangeItem17() == true|| pc.getIsChangeItem18() == true|| pc.getIsChangeItem19() == true|| pc.getIsChangeItem20() == true
                                    ) {
                                SC_ChatPacket.sendDirect(pc, pc, "\\f3아이템변경 취소로 인해 리스후에 행동이 가능합니다.");
                                return ItemUseResult.STOP;
                            }
                            if (targetItem == null || targetItem.isEquipped()) {
                                SC_ServerMessage.send(pc, 4302);
                                return ItemUseResult.STOP;
                            }
                            if (targetItem == null || targetItem.getItem().getType2() != 1) {
                                SC_ServerMessage.send(pc, 79);
                                return ItemUseResult.STOP;
                            }

                            int safe_enchant = targetItem.getItem().get_safeenchant();
                            if (safe_enchant < 0) {
                                SC_ServerMessage.send(pc, 79);
                                return ItemUseResult.STOP;
                            }
                            int weaponId = targetItem.getItem().getItemId();
                            if (weaponId >= 246 && weaponId <= 249) {
                                if (itemId == L1ItemId.SCROLL_OF_ENCHANT_QUEST_WEAPON) {
                                } else {
                                    SC_ServerMessage.send(pc, 79);
                                    return ItemUseResult.STOP;
                                }
                            }
                            if (itemId == L1ItemId.SCROLL_OF_ENCHANT_QUEST_WEAPON) {

                                if (weaponId >= 246 && weaponId <= 249) {
                                } else {
                                    SC_ServerMessage.send(pc, 79);

                                    return ItemUseResult.STOP;
                                }
                            }

                            if (weaponId >= 307 && weaponId <= 314) {
                                if (itemId == 30146) {
                                } else {
                                    SC_ServerMessage.send(pc, 79);

                                    return ItemUseResult.STOP;
                                }
                            }
                            if (itemId == 30146) {
                                if (weaponId >= 307 && weaponId <= 314) {
                                } else {
                                    SC_ServerMessage.send(pc, 79);

                                    return ItemUseResult.STOP;
                                }
                            }

                            if (weaponId >= 413000 && weaponId <= 413007) {
                                if (itemId == L1ItemId.SCROLL_OF_ENCHANT_FANTASY_WEAPON) {
                                } else {
                                    SC_ServerMessage.send(pc, 79);

                                    return ItemUseResult.STOP;
                                }
                            }
                            if (itemId == L1ItemId.SCROLL_OF_ENCHANT_FANTASY_WEAPON) {
                                if (weaponId >= 413000 && weaponId <= 413007) {

                                } else {
                                    SC_ServerMessage.send(pc, 79);

                                    return ItemUseResult.STOP;
                                }
                            }
                            switch (itemId) {
                            case 140087:
                            case 40087:
                            case 240087:
                                if (weaponId == 315 || weaponId == 316 || weaponId == 317 || weaponId == 318 || weaponId == 319
                                        || weaponId == 320 || weaponId == 1104 || weaponId == 7000136 || weaponId == 7000213

        ) {

                                    return ItemUseResult.STOP;
                                }
                            }

                            if (weaponId == 7 || weaponId == 35 || weaponId == 48 || weaponId == 73 || weaponId == 105
                                    || weaponId == 120  || weaponId == 156 || weaponId == 174
                                    || weaponId == 9003 || weaponId == 175 || weaponId == 224 || weaponId == 203012
                                    || weaponId == 7000222) {
                                if (itemId != L1ItemId.IVORYTOWER_WEAPON_SCROLL) {
                                    SC_ServerMessage.send(pc, 79);
                                    return ItemUseResult.STOP;
                                }
                            }
                            if (targetItem.getBless() >= 128 && (!(itemId >= 210064 && itemId <= 210067 || itemId >= 310064 && itemId <= 310068
                                    || itemId >= 560030 && itemId <= 560033 || itemId == 810003 || itemId == 4100148))) {
                                SC_ServerMessage.send(pc, 79);
                                return ItemUseResult.STOP;
                            }

                            if (itemId == 210064 && targetItem.getAttrEnchantLevel() != 0
                                    && targetItem.getAttrEnchantLevel() != 11
                                    && targetItem.getAttrEnchantLevel() != 12
                                    && targetItem.getAttrEnchantLevel() != 13
                                    && targetItem.getAttrEnchantLevel() != 14
                                    && targetItem.getAttrEnchantLevel() != 15) {
                                SC_ServerMessage.send(pc, 1294);
                                return ItemUseResult.STOP;
                            }
                            if (itemId == 210065 && targetItem.getAttrEnchantLevel() != 0
                                    && targetItem.getAttrEnchantLevel() != 16
                                    && targetItem.getAttrEnchantLevel() != 17
                                    && targetItem.getAttrEnchantLevel() != 18
                                    && targetItem.getAttrEnchantLevel() != 19
                                    && targetItem.getAttrEnchantLevel() != 20) {
                                SC_ServerMessage.send(pc, 1294);
                                return ItemUseResult.STOP;
                            }
                            if (itemId == 210066 && targetItem.getAttrEnchantLevel() != 0
                                    && targetItem.getAttrEnchantLevel() != 6 && targetItem.getAttrEnchantLevel() != 7
                                    && targetItem.getAttrEnchantLevel() != 8 && targetItem.getAttrEnchantLevel() != 9
                                    && targetItem.getAttrEnchantLevel() != 10) {
                                SC_ServerMessage.send(pc, 1294);
                                return ItemUseResult.STOP;
                            }
                            if (itemId == 210067 && targetItem.getAttrEnchantLevel() != 0
                                    && targetItem.getAttrEnchantLevel() != 1 && targetItem.getAttrEnchantLevel() != 2
                                    && targetItem.getAttrEnchantLevel() != 3 && targetItem.getAttrEnchantLevel() != 4
                                    && targetItem.getAttrEnchantLevel() != 5) {
                                SC_ServerMessage.send(pc, 1294);
                                return ItemUseResult.STOP;
                            }
                            int enchant_level = targetItem.getEnchantLevel();

                            if (enchant_level >= Config.무기인첸트 && (!(itemId >= 210064 && itemId <= 210067 || itemId >= 310064 && itemId <= 310068|| itemId >= 560030 &&  itemId <= 560033))) {
                                SC_SystemMessage.send(pc, "무기는 +" + Config.무기인첸트 + "이상 강화할 수 없습니다.");
                                return ItemUseResult.STOP;
                            }
                            if (safe_enchant == 0) {
                                if (enchant_level >= Config.무기고급인첸트 && (!(itemId >= 210064 && itemId <= 210067|| itemId >= 310064 && itemId <= 310068 || itemId >= 560030 &&  itemId <= 560033))){
                                    if (itemId == L1ItemId.C_SCROLL_OF_ENCHANT_WEAPON || itemId == L1ItemId.ENCHANT_WEAPONA) {
                                        pc.getInventory().removeItem(item, 1);
                                        SuccessEnchant(pc, targetItem, client, -1);
                                    } else {
                                        SC_SystemMessage.send(pc, "무기는 +" + Config.무기고급인첸트 + "이상 강화할 수 없습니다.");
                                    }
                                    return ItemUseResult.STOP;
                                }
                            }
                            if (itemId == L1ItemId.C_SCROLL_OF_ENCHANT_WEAPON || itemId == L1ItemId.ENCHANT_WEAPONA) {
                                pc.getInventory().removeItem(item, 1);
                                int rnd = _random.nextInt(100) + 1;
                                if (safe_enchant == 0 && rnd <= 30) {
                                    FailureEnchant(pc, targetItem, client);
                                    return ItemUseResult.STOP;
                                }
                                if (enchant_level < -6) {
                                    FailureEnchant(pc, targetItem, client);
                                } else {
                                    SuccessEnchant(pc, targetItem, client, -1);
                                }

                            } else if (itemId >= 210064 && itemId <= 210067) {
                                AttrEnchant(pc, item, targetItem);

                            } else if (itemId >= 310064 && itemId <= 310068) {
                                ItemUseAttributeScroll.apply(pc, item, targetItem);

                            } else if (itemId >= 560030 && itemId <= 560033) {
                                ItemUseAttributeScroll.apply(pc, item, targetItem);

                            } else if (itemId == 68076) {
                                if (targetItem != null && targetItem.getItem().getType2() == 1) {
                                    int max_enchantLevel = targetItem.getItem().get_safeenchant() == 0
                                            ? Config.rareweaponLevel : Config.weaponLevel;

                                    if (enchant_level >= max_enchantLevel) {
                                        SC_SystemMessage.send(pc, "더 이상 인챈이 불가능합니다.");
                                        return ItemUseResult.STOP;
                                    }

                                    int enchant = enchant_level < 0 ? 0
                                            : targetItem.getItem().get_safeenchant() == 0
                                                    ? enchant_level >= Config.rareweapon.length ? Config.rareweapon.length - 1
                                                            : enchant_level
                                                    : enchant_level >= Config.weapon.length ? Config.weapon.length - 1
                                                            : enchant_level;
                                    int dice = targetItem.getItem().get_safeenchant() == 0 ? Config.rareweapon[enchant]
                                            : Config.weapon[enchant];

                                    int rnd = _random.nextInt(100) + 1;
                                    if (rnd <= dice) {
                                        SuccessEnchant(pc, targetItem, client, +1);
                                        SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
                                        pc.getInventory().removeItem(item, 1);
                                        if (pc.isGm()) {
                                            if (targetItem.getItem().get_safeenchant() == 0) {
                                            SC_SystemMessage.send(pc, "\\fY[안전인챈: 0] [확률: "+rnd+"] [+"+enchant+" 무기 확률범위: 1~"+dice+"] \\f2[성공]");
                                            } else {
                                                SC_SystemMessage.send(pc, "\\fY[안전인챈: 6] [확률: "+rnd+"] [+"+enchant+" 무기 확률범위: 1~"+dice+"] \\f2[성공]");
                                            }
                                        }
                                    } else {
                                        SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                                        pc.getInventory().removeItem(item, 1);
                                        if (pc.isGm()) {
                                            if (targetItem.getItem().get_safeenchant() == 0) {
                                            SC_SystemMessage.send(pc, "\\fY[안전인챈: 0] [확률: "+rnd+"] [+"+enchant+" 무기 확률범위: 1~"+dice+"] [실패]");
                                            } else {
                                                SC_SystemMessage.send(pc, "\\fY[안전인챈: 6] [확률: "+rnd+"] [+"+enchant+" 무기 확률범위: 1~"+dice+"] [실패]");
                                            }
                                        }
                                    }

                                }
                            } else if (itemId == 724) {
                                if (targetItem != null && weaponId == 315 || weaponId == 316 || weaponId == 317
                                        || weaponId == 318 || weaponId == 319 || weaponId == 320 || weaponId == 1104
                                        || weaponId == 7000136 || weaponId == 7000213 || weaponId == 259 || weaponId == 260
                                        || weaponId == 261 || weaponId == 262 || weaponId == 263 || weaponId == 264
                                        || weaponId == 265 || weaponId == 266 || weaponId == 267) {
                                    if (enchant_level > 10) {
                                        SC_SystemMessage.send(pc, "더 이상 주 인챈트가 불가능 합니다.");
                                        return ItemUseResult.STOP;
                                    }
                                    int k3 = CommonUtil.random(100);

                                    if (k3 >= 0 && k3 <= Config.Hero_weapon) {

                                        if (Config.ENCHANT_MAX_FAIL && enchant_level >= 11) {
                                            SC_SystemMessage.send(pc, "강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                                            pc.getInventory().removeItem(item, 1);
                                        } else {
                                            SuccessEnchant(pc, targetItem, client, +1);
                                            SC_SystemMessage.send(pc, "강렬하게 빛을 내어 인챈트 성공 하였습니다.");
                                            pc.getInventory().removeItem(item, 1);
                                        }
                                    } else {
                                        SC_SystemMessage.send(pc, "강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                                        pc.getInventory().removeItem(item, 1);
                                        FailureEnchant(pc, targetItem, client);
                                    }
                                }

                            } else if (itemId == 810003) {
                                if (!(targetItem.getItem().getMaterial() == 9
                                        || targetItem.getItem().getMaterial() == 18)) {
                                    if (enchant_level == 9) {
                                        int rnd = _random.nextInt(100) + 1;
                                        if (rnd <= Config.Master_Enchant) {
                                            SuccessEnchant(pc, targetItem, client, 1);
                                        } else {
                                            SC_ServerMessage.send(pc, 1310);

                                        }
                                        pc.getInventory().removeItem(item, 1);
                                    } else {
                                        SC_SystemMessage.send(pc, "무기인챈트 +9 이하에서만 사용이 가능합니다.");

                                    }
                                } else {
                                    SC_ServerMessage.send(pc, 1294);

                                }
                            } else if (itemId == 4100148) {
                                if (!(targetItem.getItem().getMaterial() == 9
                                        || targetItem.getItem().getMaterial() == 18)) {
                                    if (enchant_level >= 8) {
                                        int rnd = _random.nextInt(100) + 1;
                                        if (rnd <= 100) {
                                            SuccessEnchant(pc, targetItem, client, 1);
                                        }
                                        pc.getInventory().removeItem(item, 1);
                                    } else {
                                        SC_SystemMessage.send(pc, "무기인챈트 +8 이상에서만 사용이 가능합니다.");

                                    }
                                } else {
                                    SC_ServerMessage.send(pc, 1294);

                                }
                            } else if (enchant_level < safe_enchant) {
                                pc.getInventory().removeItem(item, 1);
                                SuccessEnchant(pc, targetItem, client, RandomELevel(targetItem, itemId));
                            } else {
                                pc.getInventory().removeItem(item, 1);
                                int rnd = _random.nextInt(100) + 1;
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

                                if (pc._EnchantWeaponSuccess == true) {
                                    int randomEnchantLevel = 0;

                                        randomEnchantLevel = RandomELevel(targetItem, itemId);

                                    SuccessEnchant(pc, targetItem, client, randomEnchantLevel);
                                    pc._EnchantWeaponSuccess = false;
                                } else if (rnd < enchant_chance_wepon) {
                                    int randomEnchantLevel = 0;

                                        randomEnchantLevel = RandomELevel(targetItem, itemId);

                                    SuccessEnchant(pc, targetItem, client, randomEnchantLevel);
                                } else {
                                    FailureEnchant(pc, targetItem, client);
                                }
                            }
                        }
                            return ItemUseResult.COMPLETE;
    }
}
