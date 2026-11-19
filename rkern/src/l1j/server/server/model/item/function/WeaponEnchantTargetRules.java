package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

/** Weapon enchant pipeline: original Phase 10 branch, with unchanged decision order. */
final class WeaponEnchantTargetRules {
    private WeaponEnchantTargetRules() {}

    static ItemActionResult validate(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CIOClient client = use.client();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        // BEGIN_ORIGINAL_WEAPONENCHANTTARGETRULES
                            if (pc.getIsChangeItem5() == true ||pc.getIsChangeItem1() == true|| pc.getIsChangeItem2() == true|| pc.getIsChangeItem3() == true|| pc.getIsChangeItem4() == true ||
                                    pc.getIsChangeItem6() == true ||pc.getIsChangeItem7() == true|| pc.getIsChangeItem8() == true|| pc.getIsChangeItem9() == true|| pc.getIsChangeItem10() == true ||
                                    pc.getIsChangeItem11() == true|| pc.getIsChangeItem12() == true|| pc.getIsChangeItem13() == true|| pc.getIsChangeItem14() == true|| pc.getIsChangeItem15() == true ||
                                    pc.getIsChangeItem16() == true|| pc.getIsChangeItem17() == true|| pc.getIsChangeItem18() == true|| pc.getIsChangeItem19() == true|| pc.getIsChangeItem20() == true
                                    ) {
                                SC_ChatPacket.sendDirect(pc, pc, "\\f3아이템변경 취소로 인해 리스후에 행동이 가능합니다.");
                                return ItemActionResult.STOP;
                            }
                            if (targetItem == null || targetItem.isEquipped()) {
                                SC_ServerMessage.send(pc, 4302);
                                return ItemActionResult.STOP;
                            }
                            if (targetItem == null || targetItem.getItem().getType2() != 1) {
                                SC_ServerMessage.send(pc, 79);
                                return ItemActionResult.STOP;
                            }

                            int safe_enchant = targetItem.getItem().get_safeenchant();
                            if (safe_enchant < 0) {
                                SC_ServerMessage.send(pc, 79);
                                return ItemActionResult.STOP;
                            }
                            int weaponId = targetItem.getItem().getItemId();
                            if (weaponId >= 246 && weaponId <= 249) {
                                if (itemId == L1ItemId.SCROLL_OF_ENCHANT_QUEST_WEAPON) {
                                } else {
                                    SC_ServerMessage.send(pc, 79);
                                    return ItemActionResult.STOP;
                                }
                            }
                            if (itemId == L1ItemId.SCROLL_OF_ENCHANT_QUEST_WEAPON) {

                                if (weaponId >= 246 && weaponId <= 249) {
                                } else {
                                    SC_ServerMessage.send(pc, 79);

                                    return ItemActionResult.STOP;
                                }
                            }

                            if (weaponId >= 307 && weaponId <= 314) {
                                if (itemId == 30146) {
                                } else {
                                    SC_ServerMessage.send(pc, 79);

                                    return ItemActionResult.STOP;
                                }
                            }
                            if (itemId == 30146) {
                                if (weaponId >= 307 && weaponId <= 314) {
                                } else {
                                    SC_ServerMessage.send(pc, 79);

                                    return ItemActionResult.STOP;
                                }
                            }

                            if (weaponId >= 413000 && weaponId <= 413007) {
                                if (itemId == L1ItemId.SCROLL_OF_ENCHANT_FANTASY_WEAPON) {
                                } else {
                                    SC_ServerMessage.send(pc, 79);

                                    return ItemActionResult.STOP;
                                }
                            }
                            if (itemId == L1ItemId.SCROLL_OF_ENCHANT_FANTASY_WEAPON) {
                                if (weaponId >= 413000 && weaponId <= 413007) {

                                } else {
                                    SC_ServerMessage.send(pc, 79);

                                    return ItemActionResult.STOP;
                                }
                            }
                            switch (itemId) {
                            case 140087:
                            case 40087:
                            case 240087:
                                if (weaponId == 315 || weaponId == 316 || weaponId == 317 || weaponId == 318 || weaponId == 319
                                        || weaponId == 320 || weaponId == 1104 || weaponId == 7000136 || weaponId == 7000213

        ) {

                                    return ItemActionResult.STOP;
                                }
                            }

                            if (weaponId == 7 || weaponId == 35 || weaponId == 48 || weaponId == 73 || weaponId == 105
                                    || weaponId == 120  || weaponId == 156 || weaponId == 174
                                    || weaponId == 9003 || weaponId == 175 || weaponId == 224 || weaponId == 203012
                                    || weaponId == 7000222) {
                                if (itemId != L1ItemId.IVORYTOWER_WEAPON_SCROLL) {
                                    SC_ServerMessage.send(pc, 79);
                                    return ItemActionResult.STOP;
                                }
                            }
                            if (targetItem.getBless() >= 128 && (!(itemId >= 210064 && itemId <= 210067 || itemId >= 310064 && itemId <= 310068
                                    || itemId >= 560030 && itemId <= 560033 || itemId == 810003 || itemId == 4100148))) {
                                SC_ServerMessage.send(pc, 79);
                                return ItemActionResult.STOP;
                            }

                            if (itemId == 210064 && targetItem.getAttrEnchantLevel() != 0
                                    && targetItem.getAttrEnchantLevel() != 11
                                    && targetItem.getAttrEnchantLevel() != 12
                                    && targetItem.getAttrEnchantLevel() != 13
                                    && targetItem.getAttrEnchantLevel() != 14
                                    && targetItem.getAttrEnchantLevel() != 15) {
                                SC_ServerMessage.send(pc, 1294);
                                return ItemActionResult.STOP;
                            }
                            if (itemId == 210065 && targetItem.getAttrEnchantLevel() != 0
                                    && targetItem.getAttrEnchantLevel() != 16
                                    && targetItem.getAttrEnchantLevel() != 17
                                    && targetItem.getAttrEnchantLevel() != 18
                                    && targetItem.getAttrEnchantLevel() != 19
                                    && targetItem.getAttrEnchantLevel() != 20) {
                                SC_ServerMessage.send(pc, 1294);
                                return ItemActionResult.STOP;
                            }
                            if (itemId == 210066 && targetItem.getAttrEnchantLevel() != 0
                                    && targetItem.getAttrEnchantLevel() != 6 && targetItem.getAttrEnchantLevel() != 7
                                    && targetItem.getAttrEnchantLevel() != 8 && targetItem.getAttrEnchantLevel() != 9
                                    && targetItem.getAttrEnchantLevel() != 10) {
                                SC_ServerMessage.send(pc, 1294);
                                return ItemActionResult.STOP;
                            }
                            if (itemId == 210067 && targetItem.getAttrEnchantLevel() != 0
                                    && targetItem.getAttrEnchantLevel() != 1 && targetItem.getAttrEnchantLevel() != 2
                                    && targetItem.getAttrEnchantLevel() != 3 && targetItem.getAttrEnchantLevel() != 4
                                    && targetItem.getAttrEnchantLevel() != 5) {
                                SC_ServerMessage.send(pc, 1294);
                                return ItemActionResult.STOP;
                            }
        // END_ORIGINAL_WEAPONENCHANTTARGETRULES
        return ItemActionResult.COMPLETE;
    }
}
