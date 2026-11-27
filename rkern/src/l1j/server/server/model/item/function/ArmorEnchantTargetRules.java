package l1j.server.server.model.item.function;

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

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Armor;

/** Armor enchant domain: extracted unchanged from the Phase 9 item handler. */
final class ArmorEnchantTargetRules {
    private ArmorEnchantTargetRules() {}

    static ItemActionResult validate(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CUserSocket client = use.client();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        // BEGIN_ORIGINAL_ARMORENCHANTTARGETRULES
            if (targetItem == null || targetItem.getItem().getType2() != 2) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }

            if (pc.getIsChangeItem5() == true ||pc.getIsChangeItem1() == true|| pc.getIsChangeItem2() == true|| pc.getIsChangeItem3() == true|| pc.getIsChangeItem4() == true ||
                    pc.getIsChangeItem6() == true ||pc.getIsChangeItem7() == true|| pc.getIsChangeItem8() == true|| pc.getIsChangeItem9() == true|| pc.getIsChangeItem10() == true ||
                    pc.getIsChangeItem11() == true|| pc.getIsChangeItem12() == true|| pc.getIsChangeItem13() == true|| pc.getIsChangeItem14() == true|| pc.getIsChangeItem15() == true ||
                    pc.getIsChangeItem16() == true|| pc.getIsChangeItem17() == true|| pc.getIsChangeItem18() == true|| pc.getIsChangeItem19() == true|| pc.getIsChangeItem20() == true
                    ) {
                SC_ChatPacket.sendDirect(pc, pc, "\\f3아이템변경 취소로 인해 리스후에 행동이 가능합니다.");
                return ItemActionResult.STOP;
            }

            int fill_itemId = item.getItemId();
            switch (targetItem.getItem().getType()) {
            case 30:
                if (fill_itemId != 3000546 && fill_itemId != 5991 && fill_itemId != L1ItemId.ENCHANT_TEST4  && fill_itemId != L1ItemId.ENCHANT_TEST44) {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
                break;
            case 29:
            case 15:
            case 4:
                if (fill_itemId != 4100034 && fill_itemId != 40074 && fill_itemId != 140074 && fill_itemId != 240074 && fill_itemId != 68077) {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
                break;
            case 28:
                if (fill_itemId != 3000547 && fill_itemId != 3000100  && fill_itemId != 4000547) {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
                break;
            }
            if (targetItem.isEquipped()) {
                SC_ServerMessage.send(pc, 4302);
                return ItemActionResult.STOP;
            }

            int safe_enchant = ((L1Armor) targetItem.getItem()).get_safeenchant();
            if (safe_enchant < 0) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }

            int armorId = targetItem.getItem().getItemId();
            int armortype = targetItem.getItem().getType();

                if (targetItem.getItem().getItemId() == 808011 && targetItem.getEnchantLevel() >= 5) {
                    SC_SystemMessage.send(pc, "\\fY귀신의 망토는 +5까지만 강화가능합니다.");
                    return ItemActionResult.STOP;
                    }

            if (armorId == 21095) {
                if (itemId == 30147) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (itemId == 30147) {
                if (armorId == 21095) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (armorId == 123111 || armorId == 123112 || armorId == 123113) {
                if (itemId == 98077) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (armorId >= 22215 && armorId <= 22223 || armorId >= 490000 && armorId <= 490008) {
                if (itemId >= 410066 && itemId <= 410068) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (itemId >= 410066 && itemId <= 410068) {
                if (armorId >= 22215 && armorId <= 22223 || armorId >= 490000 && armorId <= 490008) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (itemId == 5991 || itemId == L1ItemId.ENCHANT_TEST4 || itemId == L1ItemId.ENCHANT_TEST44) {
                if (armorId >= 900081 && armorId <= 900084 ||armorId >= 900152 && armorId <= 900154) {
                    if (targetItem.getEnchantLevel() >= Config.휘장류) {
                        SC_ServerMessage.send(pc, 79);
                        return ItemActionResult.STOP;
                    }
                } else {
                        SC_ServerMessage.send(pc, 79);
                        return ItemActionResult.STOP;
                    }

            }
                if (armorId >= 900081 && armorId <= 900084 ||armorId >= 900152 && armorId <= 900154) {
                    if (itemId == 5991 || itemId == L1ItemId.ENCHANT_TEST4 || itemId == L1ItemId.ENCHANT_TEST44) {
                    } else {
                        SC_ServerMessage.send(pc, 79);
                        return ItemActionResult.STOP;
                    }
                }

            if (itemId == 3000100 || itemId == L1ItemId.ENCHANT_TEST5|| itemId == L1ItemId.ENCHANT_TEST55) {
                if (armorId == 900020 || armorId == 900021 || armorId == 900049 || armorId == 900050
                        || armorId == 900051 || armorId == 900124 || armorId == 900125 || armorId == 900126
                        || armorId >= 900093 && armorId <= 900099 || armorId >= 900127 && armorId <= 900130) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (itemId == 3000517 || itemId == L1ItemId.ENCHANT_TEST3) {
                int enchant_level = targetItem.getEnchantLevel();
                if (enchant_level >= Config.accessorytest) {
                    SC_SystemMessage.send(pc, "해당 인챈 이하의 할파스의 룬 에만 사용 가능합니다.");
                    return ItemActionResult.STOP;
                }
            }
            if (itemId == 4000517 || itemId == L1ItemId.ENCHANT_TEST33) {
                int enchant_level = targetItem.getEnchantLevel();
                if (enchant_level >= Config.accessorytest1) {
                    SC_SystemMessage.send(pc, "해당 인챈 이하의 고대의 룬 에만 사용 가능합니다.");
                    return ItemActionResult.STOP;
                }
            }
            if (armorId == 900116) {
                if (itemId == 3000517 || itemId == 3000518) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (armorId >= 423000 && armorId <= 423008) {
                if (itemId == L1ItemId.SCROLL_OF_ENCHANT_FANTASY_ARMOR) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (armorId == 700117) {
                if (itemId == 4000517 || itemId == 4000518) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (itemId == L1ItemId.SCROLL_OF_ENCHANT_FANTASY_ARMOR) {
                if (armorId >= 423000 && armorId <= 423008) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (itemId == 30069) {
                if (armorId >= 22328 && armorId <= 22335) {
                } else {
                    SC_ServerMessage.send(pc, 79);

                    return ItemActionResult.STOP;
                }
            }
            if (armorId >= 22328 && armorId <= 22335) {
                if (itemId == 30069) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (itemId == 210084) {
                if (armorId >= 22034 && armorId <= 22064) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (armorId >= 22034 && armorId <= 22064) {
                if (itemId == 210084) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (itemId == 210068 || itemId == L1ItemId.Pure_white_Scroll || itemId == L1ItemId.Roomtis_Scroll
                    || itemId == L1ItemId.IVORYTOWER_GKFFHDNLS || itemId == 810012 || itemId == 810013  || itemId == 3000380
                    || itemId == 7004 || itemId == L1ItemId.ENCHANT_TEST1 || itemId == L1ItemId.ENCHANT_TEST2 || itemId == L1ItemId.ENCHANT_TEST11 || itemId == L1ItemId.ENCHANT_TEST22) {
                if (armortype >= 8 && armortype <= 12) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (armortype >= 8 && armortype <= 12) {
                if (itemId == 210068 || itemId == L1ItemId.Pure_white_Scroll || itemId == L1ItemId.ENCHANT_TEST1|| itemId == L1ItemId.ENCHANT_TEST11 || itemId == L1ItemId.ENCHANT_TEST22
                        || itemId == 7004 || itemId == L1ItemId.ENCHANT_TEST2
                        || itemId == L1ItemId.Roomtis_Scroll || itemId == 810012 || itemId == 810013
                        || itemId == L1ItemId.IVORYTOWER_GKFFHDNLS || itemId == 3000380) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if ((itemId == L1ItemId.ENCHANT_TEST3 || itemId == 3000517) && armorId != 900116) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }
            if ((itemId == L1ItemId.ENCHANT_TEST33 || itemId == 4000517 ) && armorId != 700117) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }

            if (itemId == L1ItemId.IVORYTOWER_GKFFHDNLS) {
                if (armorId == 22367 || armorId == 900024) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (armorId == 22367 || armorId == 900024) {
                if (itemId == L1ItemId.IVORYTOWER_GKFFHDNLS) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (itemId == L1ItemId.Roomtis_Scroll || itemId == L1ItemId.ENCHANT_TEST2 || itemId == L1ItemId.ENCHANT_TEST22) {
                if (armorId >= 22229 && armorId <= 22231 || armorId >= 222337 && armorId <= 222339
                        || armorId == 222340 || armorId == 222341) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (armorId >= 22229 && armorId <= 22231 || armorId >= 222337 && armorId <= 222339
                    || armorId == 222340 || armorId == 222341) {
                if (itemId == L1ItemId.Roomtis_Scroll || itemId == L1ItemId.ENCHANT_TEST2 || itemId == L1ItemId.ENCHANT_TEST22) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (itemId == L1ItemId.Pure_white_Scroll || itemId == L1ItemId.ENCHANT_TEST1 || itemId == L1ItemId.ENCHANT_TEST11) {
                if (armorId >= 22224 && armorId <= 22228 || armorId == 222290 || armorId == 222291
                        || armorId >= 222330 && armorId <= 222336) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (armorId >= 22224 && armorId <= 22228 || armorId == 222290 || armorId == 222291
                    || armorId >= 222330 && armorId <= 222336) {
                if (itemId == L1ItemId.Pure_white_Scroll || itemId == L1ItemId.ENCHANT_TEST1 || itemId == L1ItemId.ENCHANT_TEST11) {
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (itemId == 7004) {
                if ((armorId >= 22337 && armorId <= 22339 || armorId == 22073)) {
                    if (targetItem.getEnchantLevel() >= 4) {
                        SC_ServerMessage.send(pc, 79);
                        return ItemActionResult.STOP;
                    }
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (itemId == L1ItemId.IVORYTOWER_ARMOR_SCROLL) {
                if (armorId == 20028 || armorId == 20082 || armorId == 20126 || armorId == 20173
                        || armorId >= 22300 && armorId <= 22311 || armorId == 20206 || armorId == 20232
                        || armorId == 20283) {
                    if (targetItem.getEnchantLevel() >= 6) {
                        SC_ServerMessage.send(pc, 79);
                        return ItemActionResult.STOP;
                    }
                } else if (armorId == 22312 || armorId == 321515 || armorId >= 20500 && armorId <= 20505) {
                    if (targetItem.getEnchantLevel() >= 4) {
                        SC_ServerMessage.send(pc, 79);
                        return ItemActionResult.STOP;
                    }
                } else {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }

            if (armorId == 20028 || armorId == 20082 || armorId == 20126 || armorId == 20173 || armorId == 20206
                    || armorId == 20232 || armorId == 20283 || (armorId >= 22300 && armorId <= 22312)
                    || (armorId >= 20500 && armorId <= 20505)) {
                if (itemId != L1ItemId.IVORYTOWER_ARMOR_SCROLL) {
                    SC_ServerMessage.send(pc, 79);
                    return ItemActionResult.STOP;
                }
            }
            if (targetItem.getBless() >= 128) {
                SC_ServerMessage.send(pc, 79);
                return ItemActionResult.STOP;
            }
        // END_ORIGINAL_ARMORENCHANTTARGETRULES
        return ItemActionResult.COMPLETE;
    }
}
