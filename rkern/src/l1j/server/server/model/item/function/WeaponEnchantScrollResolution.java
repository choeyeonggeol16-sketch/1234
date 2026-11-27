package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.EnchantmentItems.AttrEnchant;
import static l1j.server.server.model.item.function.TransformationItems.RandomELevel;

/** Weapon enchant pipeline: original Phase 10 branch, with unchanged decision order. */
final class WeaponEnchantScrollResolution {
    private WeaponEnchantScrollResolution() {}

    // Successful durable mutations must not be reclassified because of a
    // disconnected client's result packet. Only packet calls are caught here.
    private static void resultNotice(L1PcInstance pc, String message) {
        try { SC_SystemMessage.send(pc, message); }
        catch (RuntimeException e) {
            CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Weapon enchant result notification failed", e);
        }
    }
    private static void resultNotice(L1PcInstance pc, int messageId) {
        try { SC_ServerMessage.send(pc, messageId); }
        catch (RuntimeException e) {
            CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Weapon enchant result notification failed", e);
        }
    }

    static ItemActionResult apply(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CUserSocket client = use.client();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        int safe_enchant = targetItem.getItem().get_safeenchant();
        int weaponId = targetItem.getItem().getItemId();
        int enchant_level = targetItem.getEnchantLevel();
        // BEGIN_ORIGINAL_WEAPONENCHANTSCROLLRESOLUTION
                            if (itemId == L1ItemId.C_SCROLL_OF_ENCHANT_WEAPON || itemId == L1ItemId.ENCHANT_WEAPONA) {
                                final int roll = GameRandom.nextInt(100) + 1;
                                if ((safe_enchant == 0 && roll <= 30) || enchant_level < -6) {
                                    ItemReceiptOperations.useAtomicEnchantFailure(pc, item, targetItem, null);
                                } else {
                                    ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client, -1, null);
                                }
                                return ItemActionResult.STOP;
                            } else if (itemId >= 210064 && itemId <= 210067) {
                                AttrEnchant(pc, item, targetItem);
                                return ItemActionResult.STOP;
                            } else if ((itemId >= 310064 && itemId <= 310068) || (itemId >= 560030 && itemId <= 560033)) {
                                AttributeEnchantScrolls.apply(pc, item, targetItem);
                                return ItemActionResult.STOP;
                            } else if (itemId == 68076) {
                                if (targetItem != null && targetItem.getItem().getType2() == 1) {
                                    int max_enchantLevel = targetItem.getItem().get_safeenchant() == 0
                                            ? Config.rareweaponLevel : Config.weaponLevel;

                                    if (enchant_level >= max_enchantLevel) {
                                        SC_SystemMessage.send(pc, "더 이상 인챈이 불가능합니다.");
                                        return ItemActionResult.STOP;
                                    }

                                    int enchant = enchant_level < 0 ? 0
                                            : targetItem.getItem().get_safeenchant() == 0
                                                    ? enchant_level >= Config.rareweapon.length ? Config.rareweapon.length - 1
                                                            : enchant_level
                                                    : enchant_level >= Config.weapon.length ? Config.weapon.length - 1
                                                            : enchant_level;
                                    int dice = targetItem.getItem().get_safeenchant() == 0 ? Config.rareweapon[enchant]
                                            : Config.weapon[enchant];

                                    int rnd = GameRandom.nextInt(100) + 1;
                                    if (rnd <= dice) {
                                        ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client, () -> {
                                            resultNotice(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
                                            if (pc.isGm()) {
                                                if (targetItem.getItem().get_safeenchant() == 0) {
                                                    resultNotice(pc, "\\fY[안전인챈: 0] [확률: " + rnd + "] [+" + enchant + " 무기 확률범위: 1~" + dice + "] \\f2[성공]");
                                                } else {
                                                    resultNotice(pc, "\\fY[안전인챈: 6] [확률: " + rnd + "] [+" + enchant + " 무기 확률범위: 1~" + dice + "] \\f2[성공]");
                                                }
                                            }
                                        });
                                    } else {
                                        ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
                                            ItemActionCompletion.finishCommittedConsumable(pc, item);
                                            resultNotice(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                                            if (pc.isGm()) {
                                                if (targetItem.getItem().get_safeenchant() == 0) {
                                                    resultNotice(pc, "\\fY[안전인챈: 0] [확률: " + rnd + "] [+" + enchant + " 무기 확률범위: 1~" + dice + "] [실패]");
                                                } else {
                                                    resultNotice(pc, "\\fY[안전인챈: 6] [확률: " + rnd + "] [+" + enchant + " 무기 확률범위: 1~" + dice + "] [실패]");
                                                }
                                            }
                                        });
                                    }
                                    return ItemActionResult.STOP;

                                }
                            } else if (itemId == 724) {
                                if (targetItem != null && (weaponId == 315 || weaponId == 316 || weaponId == 317
                                        || weaponId == 318 || weaponId == 319 || weaponId == 320 || weaponId == 1104
                                        || weaponId == 7000136 || weaponId == 7000213 || weaponId == 259 || weaponId == 260
                                        || weaponId == 261 || weaponId == 262 || weaponId == 263 || weaponId == 264
                                        || weaponId == 265 || weaponId == 266 || weaponId == 267)) {
                                    if (enchant_level > 10) {
                                        SC_SystemMessage.send(pc, "더 이상 주 인챈트가 불가능 합니다.");
                                        return ItemActionResult.STOP;
                                    }
                                    int k3 = CommonUtil.random(100);

                                    if (k3 >= 0 && k3 <= Config.Hero_weapon) {
                                        ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client,
                                                () -> resultNotice(pc, "강렬하게 빛을 내어 인챈트 성공 하였습니다."));
                                    } else {
                                        ItemReceiptOperations.useAtomicEnchantFailure(pc, item, targetItem,
                                                () -> resultNotice(pc, "강렬하게 빛났지만 인챈트에 실패 하였습니다."));
                                    }
                                    return ItemActionResult.STOP;
                                }

                            } else if (itemId == 810003) {
                                if (!(targetItem.getItem().getMaterial() == 9
                                        || targetItem.getItem().getMaterial() == 18)) {
                                    if (enchant_level == 9) {
                                        int rnd = GameRandom.nextInt(100) + 1;
                                        if (!SpecialEnchantScrolls.isOwnedScrollTarget(pc, item, targetItem))
                                            return ItemActionResult.STOP;
                                        if (rnd <= Config.Master_Enchant) {
                                            ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client, null);
                                        } else {
                                            ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
                                                ItemActionCompletion.finishCommittedConsumable(pc, item);
                                                resultNotice(pc, 1310);
                                            });
                                        }
                                        return ItemActionResult.STOP;
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
                                        int rnd = GameRandom.nextInt(100) + 1;
                                        if (!SpecialEnchantScrolls.isOwnedScrollTarget(pc, item, targetItem))
                                            return ItemActionResult.STOP;
                                        if (rnd <= 100) {
                                            ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client, null);
                                        }
                                        return ItemActionResult.STOP;
                                    } else {
                                        SC_SystemMessage.send(pc, "무기인챈트 +8 이상에서만 사용이 가능합니다.");

                                    }
                                } else {
                                    SC_ServerMessage.send(pc, 1294);

                                }
                            } else {
                                if (enchant_level < safe_enchant) {
                                    ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client,
                                            RandomELevel(targetItem, itemId), null);
                                } else {
                                    final int roll = GameRandom.nextInt(100) + 1;
                                    final int chance = WeaponEnchantChanceCalculation.calculate(
                                            pc, targetItem, weaponId, enchant_level, roll);
                                    final boolean forced = pc._EnchantWeaponSuccess;
                                    if (forced || roll < chance) {
                                        ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client,
                                                RandomELevel(targetItem, itemId),
                                                forced ? () -> pc._EnchantWeaponSuccess = false : null);
                                    } else {
                                        ItemReceiptOperations.useAtomicEnchantFailure(pc, item, targetItem, null);
                                    }
                                }
                                return ItemActionResult.STOP;
                            }
        // END_ORIGINAL_WEAPONENCHANTSCROLLRESOLUTION
        return ItemActionResult.COMPLETE;
    }
}
