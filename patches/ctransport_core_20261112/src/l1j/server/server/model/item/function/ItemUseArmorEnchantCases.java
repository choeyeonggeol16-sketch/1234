package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.datatables.AccessoryEnchantInformationTable;
import l1j.server.server.datatables.ArmorEnchantInformationTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Armor;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.ItemUseCreature._random;
import static l1j.server.server.model.item.function.ItemUseEnchantments.FailureEnchant;
import static l1j.server.server.model.item.function.ItemUseEnchantments.SuccessEnchant;
import static l1j.server.server.model.item.function.ItemUseEnchantments.enchant_bless_orim;
import static l1j.server.server.model.item.function.ItemUseEnchantments.enchant_insignia;
import static l1j.server.server.model.item.function.ItemUseEnchantments.enchant_insignia1;
import static l1j.server.server.model.item.function.ItemUseEnchantments.enchant_roomtis;
import static l1j.server.server.model.item.function.ItemUseEnchantments.enchant_roomtis1;
import static l1j.server.server.model.item.function.ItemUseEnchantments.enchant_sentence;
import static l1j.server.server.model.item.function.ItemUseEnchantments.enchant_sentence1;
import static l1j.server.server.model.item.function.ItemUseEnchantments.enchant_should;
import static l1j.server.server.model.item.function.ItemUseEnchantments.enchant_snapper;
import static l1j.server.server.model.item.function.ItemUseEnchantments.enchant_snapper1;
import static l1j.server.server.model.item.function.ItemUseTransformation.RandomELevel;

/** Item-specific ArmorEnchant effects; selected once by ItemUseDispatcher. */
final class ItemUseArmorEnchantCases {
    private ItemUseArmorEnchantCases() {}

    static ItemUseResult use(ItemUseContext use) {
        L1PcInstance pc = use.player();
        CIOClient client = use.client();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        {

            if (targetItem == null || targetItem.getItem().getType2() != 2) {
                SC_ServerMessage.send(pc, 79);
                return ItemUseResult.STOP;
            }

            if (pc.getIsChangeItem5() == true ||pc.getIsChangeItem1() == true|| pc.getIsChangeItem2() == true|| pc.getIsChangeItem3() == true|| pc.getIsChangeItem4() == true ||
                    pc.getIsChangeItem6() == true ||pc.getIsChangeItem7() == true|| pc.getIsChangeItem8() == true|| pc.getIsChangeItem9() == true|| pc.getIsChangeItem10() == true ||
                    pc.getIsChangeItem11() == true|| pc.getIsChangeItem12() == true|| pc.getIsChangeItem13() == true|| pc.getIsChangeItem14() == true|| pc.getIsChangeItem15() == true ||
                    pc.getIsChangeItem16() == true|| pc.getIsChangeItem17() == true|| pc.getIsChangeItem18() == true|| pc.getIsChangeItem19() == true|| pc.getIsChangeItem20() == true
                    ) {
                SC_ChatPacket.sendDirect(pc, pc, "\\f3아이템변경 취소로 인해 리스후에 행동이 가능합니다.");
                return ItemUseResult.STOP;
            }

            int fill_itemId = item.getItemId();
            switch (targetItem.getItem().getType()) {
            case 30:
                if (fill_itemId != 3000546 && fill_itemId != 5991 && fill_itemId != L1ItemId.ENCHANT_TEST4  && fill_itemId != L1ItemId.ENCHANT_TEST44) {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
                break;
            case 29:
            case 15:
            case 4:
                if (fill_itemId != 4100034 && fill_itemId != 40074 && fill_itemId != 140074 && fill_itemId != 240074 && fill_itemId != 68077) {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
                break;
            case 28:
                if (fill_itemId != 3000547 && fill_itemId != 3000100  && fill_itemId != 4000547) {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
                break;
            }
            if (targetItem.isEquipped()) {
                SC_ServerMessage.send(pc, 4302);
                return ItemUseResult.STOP;
            }

            int safe_enchant = ((L1Armor) targetItem.getItem()).get_safeenchant();
            if (safe_enchant < 0) {
                SC_ServerMessage.send(pc, 79);
                return ItemUseResult.STOP;
            }

            int armorId = targetItem.getItem().getItemId();
            int armortype = targetItem.getItem().getType();

                if (targetItem.getItem().getItemId() == 808011 && targetItem.getEnchantLevel() >= 5) {
                    SC_SystemMessage.send(pc, "\\fY귀신의 망토는 +5까지만 강화가능합니다.");
                    return ItemUseResult.STOP;
                    }

            if (armorId == 21095) {
                if (itemId == 30147) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (itemId == 30147) {
                if (armorId == 21095) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (armorId == 123111 || armorId == 123112 || armorId == 123113) {
                if (itemId == 98077) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (armorId >= 22215 && armorId <= 22223 || armorId >= 490000 && armorId <= 490008) {
                if (itemId >= 410066 && itemId <= 410068) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (itemId >= 410066 && itemId <= 410068) {
                if (armorId >= 22215 && armorId <= 22223 || armorId >= 490000 && armorId <= 490008) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (itemId == 5991 || itemId == L1ItemId.ENCHANT_TEST4 || itemId == L1ItemId.ENCHANT_TEST44) {
                if (armorId >= 900081 && armorId <= 900084 ||armorId >= 900152 && armorId <= 900154) {
                    if (targetItem.getEnchantLevel() >= Config.휘장류) {
                        SC_ServerMessage.send(pc, 79);
                        return ItemUseResult.STOP;
                    }
                } else {
                        SC_ServerMessage.send(pc, 79);
                        return ItemUseResult.STOP;
                    }

            }
                if (armorId >= 900081 && armorId <= 900084 ||armorId >= 900152 && armorId <= 900154) {
                    if (itemId == 5991 || itemId == L1ItemId.ENCHANT_TEST4 || itemId == L1ItemId.ENCHANT_TEST44) {
                    } else {
                        SC_ServerMessage.send(pc, 79);
                        return ItemUseResult.STOP;
                    }
                }

            if (itemId == 3000100 || itemId == L1ItemId.ENCHANT_TEST5|| itemId == L1ItemId.ENCHANT_TEST55) {
                if (armorId == 900020 || armorId == 900021 || armorId == 900049 || armorId == 900050
                        || armorId == 900051 || armorId == 900124 || armorId == 900125 || armorId == 900126
                        || armorId >= 900093 && armorId <= 900099 || armorId >= 900127 && armorId <= 900130) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (itemId == 3000517 || itemId == L1ItemId.ENCHANT_TEST3) {
                int enchant_level = targetItem.getEnchantLevel();
                if (enchant_level >= Config.accessorytest) {
                    SC_SystemMessage.send(pc, "해당 인챈 이하의 할파스의 룬 에만 사용 가능합니다.");
                    return ItemUseResult.STOP;
                }
            }
            if (itemId == 4000517 || itemId == L1ItemId.ENCHANT_TEST33) {
                int enchant_level = targetItem.getEnchantLevel();
                if (enchant_level >= Config.accessorytest1) {
                    SC_SystemMessage.send(pc, "해당 인챈 이하의 고대의 룬 에만 사용 가능합니다.");
                    return ItemUseResult.STOP;
                }
            }
            if (armorId == 900116) {
                if (itemId == 3000517 || itemId == 3000518) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (armorId >= 423000 && armorId <= 423008) {
                if (itemId == L1ItemId.SCROLL_OF_ENCHANT_FANTASY_ARMOR) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (armorId == 700117) {
                if (itemId == 4000517 || itemId == 4000518) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (itemId == L1ItemId.SCROLL_OF_ENCHANT_FANTASY_ARMOR) {
                if (armorId >= 423000 && armorId <= 423008) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (itemId == 30069) {
                if (armorId >= 22328 && armorId <= 22335) {
                } else {
                    SC_ServerMessage.send(pc, 79);

                    return ItemUseResult.STOP;
                }
            }
            if (armorId >= 22328 && armorId <= 22335) {
                if (itemId == 30069) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (itemId == 210084) {
                if (armorId >= 22034 && armorId <= 22064) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (armorId >= 22034 && armorId <= 22064) {
                if (itemId == 210084) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (itemId == 210068 || itemId == L1ItemId.Pure_white_Scroll || itemId == L1ItemId.Roomtis_Scroll
                    || itemId == L1ItemId.IVORYTOWER_GKFFHDNLS || itemId == 810012 || itemId == 810013  || itemId == 3000380
                    || itemId == 7004 || itemId == L1ItemId.ENCHANT_TEST1 || itemId == L1ItemId.ENCHANT_TEST2 || itemId == L1ItemId.ENCHANT_TEST11 || itemId == L1ItemId.ENCHANT_TEST22) {
                if (armortype >= 8 && armortype <= 12) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (armortype >= 8 && armortype <= 12) {
                if (itemId == 210068 || itemId == L1ItemId.Pure_white_Scroll || itemId == L1ItemId.ENCHANT_TEST1|| itemId == L1ItemId.ENCHANT_TEST11 || itemId == L1ItemId.ENCHANT_TEST22
                        || itemId == 7004 || itemId == L1ItemId.ENCHANT_TEST2
                        || itemId == L1ItemId.Roomtis_Scroll || itemId == 810012 || itemId == 810013
                        || itemId == L1ItemId.IVORYTOWER_GKFFHDNLS || itemId == 3000380) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if ((itemId == L1ItemId.ENCHANT_TEST3 || itemId == 3000517) && armorId != 900116) {
                SC_ServerMessage.send(pc, 79);
                return ItemUseResult.STOP;
            }
            if ((itemId == L1ItemId.ENCHANT_TEST33 || itemId == 4000517 ) && armorId != 700117) {
                SC_ServerMessage.send(pc, 79);
                return ItemUseResult.STOP;
            }

            if (itemId == L1ItemId.IVORYTOWER_GKFFHDNLS) {
                if (armorId == 22367 || armorId == 900024) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (armorId == 22367 || armorId == 900024) {
                if (itemId == L1ItemId.IVORYTOWER_GKFFHDNLS) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (itemId == L1ItemId.Roomtis_Scroll || itemId == L1ItemId.ENCHANT_TEST2 || itemId == L1ItemId.ENCHANT_TEST22) {
                if (armorId >= 22229 && armorId <= 22231 || armorId >= 222337 && armorId <= 222339
                        || armorId == 222340 || armorId == 222341) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (armorId >= 22229 && armorId <= 22231 || armorId >= 222337 && armorId <= 222339
                    || armorId == 222340 || armorId == 222341) {
                if (itemId == L1ItemId.Roomtis_Scroll || itemId == L1ItemId.ENCHANT_TEST2 || itemId == L1ItemId.ENCHANT_TEST22) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (itemId == L1ItemId.Pure_white_Scroll || itemId == L1ItemId.ENCHANT_TEST1 || itemId == L1ItemId.ENCHANT_TEST11) {
                if (armorId >= 22224 && armorId <= 22228 || armorId == 222290 || armorId == 222291
                        || armorId >= 222330 && armorId <= 222336) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (armorId >= 22224 && armorId <= 22228 || armorId == 222290 || armorId == 222291
                    || armorId >= 222330 && armorId <= 222336) {
                if (itemId == L1ItemId.Pure_white_Scroll || itemId == L1ItemId.ENCHANT_TEST1 || itemId == L1ItemId.ENCHANT_TEST11) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (itemId == 7004) {
                if ((armorId >= 22337 && armorId <= 22339 || armorId == 22073)) {
                    if (targetItem.getEnchantLevel() >= 4) {
                        SC_ServerMessage.send(pc, 79);
                        return ItemUseResult.STOP;
                    }
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (itemId == L1ItemId.IVORYTOWER_ARMOR_SCROLL) {
                if (armorId == 20028 || armorId == 20082 || armorId == 20126 || armorId == 20173
                        || armorId >= 22300 && armorId <= 22311 || armorId == 20206 || armorId == 20232
                        || armorId == 20283) {
                    if (targetItem.getEnchantLevel() >= 6) {
                        SC_ServerMessage.send(pc, 79);
                        return ItemUseResult.STOP;
                    }
                } else if (armorId == 22312 || armorId == 321515 || armorId >= 20500 && armorId <= 20505) {
                    if (targetItem.getEnchantLevel() >= 4) {
                        SC_ServerMessage.send(pc, 79);
                        return ItemUseResult.STOP;
                    }
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }

            if (armorId == 20028 || armorId == 20082 || armorId == 20126 || armorId == 20173 || armorId == 20206
                    || armorId == 20232 || armorId == 20283 || (armorId >= 22300 && armorId <= 22312)
                    || (armorId >= 20500 && armorId <= 20505)) {
                if (itemId != L1ItemId.IVORYTOWER_ARMOR_SCROLL) {
                    SC_ServerMessage.send(pc, 79);
                    return ItemUseResult.STOP;
                }
            }
            if (targetItem.getBless() >= 128) {
                SC_ServerMessage.send(pc, 79);
                return ItemUseResult.STOP;
            }

            int enchant_level = targetItem.getEnchantLevel();

            if (armorId >= 22229 && armorId <= 22231 || armorId >= 222337 && armorId <= 222339
                    || armorId >= 222340 && armorId <= 222341) {
                if (enchant_level >= Config.룸티스) {
                    SC_SystemMessage.send(pc, "룸티스귀걸이는 +" + Config.룸티스 + "이상은 인챈할 수 없습니다.");
                    SC_SystemMessage.send(pc, "제한해제시 공지 하겠습니다.");
                    return ItemUseResult.STOP;
                }
            } else if (armorId >= 22224 && armorId <= 22228 || armorId == 222290 || armorId == 222291

                    || armorId >= 222330 && armorId <= 222336) {
                if (enchant_level >= Config.스냅퍼) {
                    SC_SystemMessage.send(pc, "스냅퍼 반지는 +" + Config.스냅퍼 + " 이상은 인챈할 수 없습니다.");
                    SC_SystemMessage.send(pc, "제한해제시 공지 하겠습니다.");
                    return ItemUseResult.STOP;
                }
            } else if (armorId >= 900020 && armorId <= 900021 || armorId >= 900049 && armorId <= 900051
                    || armorId >= 900093 && armorId <= 900099 || armorId >= 900124 && armorId <= 900126
                    || armorId >= 900127 && armorId <= 900130) {
                if (enchant_level >= Config.문장류) {
                    SC_SystemMessage.send(pc, "문장류는 +" + Config.문장류 + " 이상은 인챈할 수 없습니다.");
                    SC_SystemMessage.send(pc, "제한해제시 공지 하겠습니다.");
                    return ItemUseResult.STOP;
                }
            } else if (armortype == 30) {
                if (enchant_level >= Config.휘장류) {
                        SC_SystemMessage.send(pc, "휘장은 +" + Config.휘장류 + " 이상은 인챈할 수 없습니다.");
                        SC_SystemMessage.send(pc, "제한해제시 공지 하겠습니다.");
                        return ItemUseResult.STOP;

                }

            } else if (armortype >= 8 && armortype <= 12) {
                if (!(armorId >= 22229 && armorId <= 22231 || armorId >= 222337 && armorId <= 222339
                        || armorId >= 222340 && armorId <= 222341 || armorId >= 22224 && armorId <= 22228
                        || armorId == 222290 || armorId == 222291 || armorId >= 222330 && armorId <= 222336)) {
                    if (enchant_level >= Config.악세사리) {
                        SC_SystemMessage.send(pc, "악세사리는 +" + Config.악세사리 + " 이상은 인챈할 수 없습니다.");
                        SC_SystemMessage.send(pc, "제한해제시 공지 하겠습니다.");
                        return ItemUseResult.STOP;
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
                        return ItemUseResult.STOP;
                    }
                } else {
                    if (enchant_level >= Config.방어구인첸트) {
                        SC_SystemMessage.send(pc, "방어구는 +" + Config.방어구인첸트 + "이상 강화할 수 없습니다.");
                        return ItemUseResult.STOP;
                    }
                }
            }

            if (itemId == L1ItemId.C_SCROLL_OF_ENCHANT_ARMOR || itemId == L1ItemId.Inadril_T_ScrollC
                    || itemId == L1ItemId.ENCHANT_ARMOR || itemId == L1ItemId.Inadril_T_ScrollB3
                    || itemId == L1ItemId.B_SCROLL_OF_ENCHANT_ARMOR5) {

                pc.getInventory().removeItem(item, 1);
                int rnd = _random.nextInt(100) + 1;
                if (safe_enchant == 0 && rnd <= 30) {
                    FailureEnchant(pc, targetItem, client);
                    return ItemUseResult.STOP;

                } else if (enchant_level < -1) {

                    FailureEnchant(pc, targetItem, client);
                } else {
                    SuccessEnchant(pc, targetItem, client, -1);
                }
            } else if (itemId == 810013) {
                enchant_bless_orim(pc, item, targetItem);
                return ItemUseResult.STOP;
            } else if (itemId == 3000428) {
                enchant_snapper(pc, item, targetItem);
                return ItemUseResult.STOP;
            } else if (itemId == 3000430) {
                enchant_roomtis(pc, item, targetItem);
                return ItemUseResult.STOP;
            } else if (itemId == 3000546) {
                enchant_insignia(pc, item, targetItem);
                return ItemUseResult.STOP;
            } else if (itemId == 3000547) {
                enchant_sentence(pc, item, targetItem);
                return ItemUseResult.STOP;

            } else if (itemId == 4000428) {
                enchant_snapper1(pc, item, targetItem);
                return ItemUseResult.STOP;
            } else if (itemId == 4000430) {
                enchant_roomtis1(pc, item, targetItem);
                return ItemUseResult.STOP;
            } else if (itemId == 4000546) {
                enchant_insignia1(pc, item, targetItem);
                return ItemUseResult.STOP;
            } else if (itemId == 4000547) {
                enchant_sentence1(pc, item, targetItem);
                return ItemUseResult.STOP;

            } else if (itemId == 4100034) {
                enchant_should(pc, item, targetItem);
                return ItemUseResult.STOP;

            } else if (itemId == 98077) {
                if (targetItem != null && targetItem.getItem().getType2() == 2 && ( targetItem.getItem().getItemId() == 123111 ||
                             targetItem.getItem().getItemId() == 123112 ||  targetItem.getItem().getItemId() == 123113) ) {
                    if (enchant_level >= Config.할파스갑옷) {
                        SC_SystemMessage.send(pc, "더 이상 인챈이 불가능합니다.");
                        return ItemUseResult.STOP;
                    }

                    int enchant = enchant_level < 0 ? 0
                            : enchant_level >= Config.할파스갑옷인챈.length ? Config.할파스갑옷인챈.length - 1 : enchant_level;
                    if (MJRnd.isWinning(100, Config.할파스갑옷인챈[enchant])) {
                        SuccessEnchant(pc, targetItem, client, +1);
                        SC_SystemMessage.send(pc, "순간 강렬하게 빛을 내어 인챈트에 성공 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                    } else {
                        SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                    }
                }
            } else if (itemId == 68077) {
                if (targetItem != null && targetItem.getItem().getType2() == 2) {
                    if (enchant_level >= Config.armorLevel) {
                        SC_SystemMessage.send(pc, "더 이상 인챈이 불가능합니다.");
                        return ItemUseResult.STOP;
                    }
                    if (targetItem.getItem().getType2() == 2 && targetItem.getItem().getItemId() == 123111 ||
                             targetItem.getItem().getItemId() == 123112 ||  targetItem.getItem().getItemId() == 123113) {
                        SC_SystemMessage.send(pc, "할파스 갑옷 종류는 강화 할 수 없습니다.");
                        return ItemUseResult.STOP;
                    }

                    int enchant = enchant_level < 0 ? 0
                            : targetItem.getItem().get_safeenchant() == 0
                                    ? enchant_level >= Config.armor1.length ? Config.armor1.length - 1
                                            : enchant_level
                                    : enchant_level >= Config.armor.length ? Config.armor.length - 1
                                            : enchant_level;
                    int dice = targetItem.getItem().get_safeenchant() == 0 ? Config.armor1[enchant]
                            : Config.armor[enchant];
                    if (MJRnd.isWinning(100, dice)) {
                        SuccessEnchant(pc, targetItem, client, +1);
                        SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                    } else {
                        SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                    }

                }
            } else if (itemId == 3000380) {
                if (targetItem != null) {
                    if (enchant_level > Config.accessoryLevel) {
                        SC_SystemMessage.send(pc, "더 이상 인챈이 불가능합니다.");
                        return ItemUseResult.STOP;
                    }
                    int k3 = CommonUtil.random(100);

                    int enchant = enchant_level < 0 ? 0
                            : enchant_level >= Config.accessory.length ? Config.accessory.length - 1
                                    : enchant_level;

                            int rnd = _random.nextInt(100) + 1;
                            if (rnd <= Config.accessory[enchant]) {
                        SuccessEnchant(pc, targetItem, client, +1);
                        SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                        if (pc.isGm()) {
                            SC_SystemMessage.send(pc, "\\fY[확률: "+rnd+"] [+"+enchant+" 악세 확률범위: 1~"+Config.accessory[enchant]+"] \\f2[성공]");
                        }
                    } else {
                        SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                        if (pc.isGm()) {
                            SC_SystemMessage.send(pc, "\\fY[확률: "+rnd+"] [+"+enchant+" 악세 확률범위: 1~"+Config.accessory[enchant]+"] [실패]");
                        }
                    }
                }
            } else if (itemId == 4000518) {
                if (targetItem != null) {

                    if (enchant_level >= Config.accessorytest1) {
                        SC_SystemMessage.send(pc, "해당 인챈 이하의 고대의 룬 에만 사용 가능합니다.");
                        return ItemUseResult.STOP;
                    }

                    int enchant = enchant_level < 0 ? 0
                            : enchant_level >= Config.ancient2.length ? Config.ancient2.length - 1
                                    : enchant_level;
                    if (MJRnd.isWinning(100, Config.ancient2[enchant])) {
                        SuccessEnchant(pc, targetItem, client, +1);
                        SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                    } else {
                        SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                    }
                }

            } else if (itemId == 3000518) {
                if (targetItem != null) {

                    if (enchant_level >= Config.accessorytest) {
                        SC_SystemMessage.send(pc, "해당 인챈 이하의 할파스의 룬 에만 사용 가능합니다.");
                        return ItemUseResult.STOP;
                    }

                    int enchant = enchant_level < 0 ? 0
                            : enchant_level >= Config.ancient.length ? Config.ancient.length - 1
                                    : enchant_level;
                    if (MJRnd.isWinning(100, Config.ancient[enchant])) {
                        SuccessEnchant(pc, targetItem, client, +1);
                        SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                    } else {
                        SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                    }
                    if (pc.isGm()) {
                        SC_SystemMessage.send(pc, targetItem.getName()+" \\f:확률: "+Config.ancient1[enchant]);
                    }
                }
            } else if (itemId == 3000517) {
                if (targetItem != null) {

                    if (enchant_level >= Config.accessorytest) {
                        SC_SystemMessage.send(pc, "해당 인챈 이하의 할파스의 룬 에만 사용 가능합니다.");
                        return ItemUseResult.STOP;
                    }

                    int enchant = enchant_level < 0 ? 0
                            : enchant_level >= Config.ancient1.length ? Config.ancient1.length - 1
                                    : enchant_level;
                    if (MJRnd.isWinning(100, Config.ancient1[enchant])) {
                        SuccessEnchant(pc, targetItem, client, +1);
                        SC_SystemMessage.send(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
                        pc.getInventory().removeItem(item, 1);
                    } else {
                        SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                        pc.getInventory().removeItem(targetItem, 1);
                        pc.getInventory().removeItem(item, 1);
                    }

                    if (pc.isGm()) {
                        SC_SystemMessage.send(pc, targetItem.getName()+" \\f:확률: "+Config.ancient1[enchant]);
                    }
                }
            } else if (enchant_level < safe_enchant) {
                pc.getInventory().removeItem(item, 1);
                SuccessEnchant(pc, targetItem, client, RandomELevel(targetItem, itemId));
            } else {
                pc.getInventory().removeItem(item, 1);

                int enchant_chance_armor = 0;
                int enchant_level_tmp;
                int chance1 = 0;
                int rnd = _random.nextInt(100) + 1;

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

                if (pc._EnchantArmorSuccess == true) {
                    int randomEnchantLevel = 0;
                    randomEnchantLevel = RandomELevel(targetItem, itemId);
                    SuccessEnchant(pc, targetItem, client, randomEnchantLevel);
                    pc._EnchantArmorSuccess = false;
                } else if (rnd < enchant_chance_armor) {
                    int randomEnchantLevel = RandomELevel(targetItem, itemId);

                    if ((enchant_level >= 0 && armorId >= 22000 && armorId <= 22003)
                            || (armorId >= 22254 && armorId <= 22256) || armorId == 22252 || armorId == 20190) {
                        randomEnchantLevel = 1;
                    }

                    if ((enchant_level >= 1 && armorId == 900188 || armorId == 900121 || armorId == 900123 || armorId == 900189)) {
                        randomEnchantLevel = 1;
                    }

                    if ((enchant_level >= 6 && armorId >= 900025 && armorId <= 900028)) {
                        randomEnchantLevel = 1;
                    }

                    if ((enchant_level >= 6 && armorId >= 900011 && armorId <= 900018)) {
                        randomEnchantLevel = 1;
                    }
                    SuccessEnchant(pc, targetItem, client, randomEnchantLevel);
                } else if (enchant_level >= 9 && rnd < (enchant_chance_armor * 2)) {
                    SC_ServerMessage.send(pc, 160, targetItem.getLogName(), "$245", "$248");
                } else if (itemId == 810012) {

                    if (enchant_level == 0) {
                        SuccessEnchant(pc, targetItem, client, 0);
                    } else {
                        SuccessEnchant(pc, targetItem, client, -1);
                    }
                } else if (itemId == 810013) {
                    SC_ServerMessage.send(pc, 4056, targetItem.getLogName());

                } else {
                    FailureEnchant(pc, targetItem, client);
                }
            }
        }
            return ItemUseResult.COMPLETE;
    }
}
