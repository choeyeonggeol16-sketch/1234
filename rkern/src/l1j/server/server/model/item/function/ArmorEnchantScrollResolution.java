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
final class ArmorEnchantScrollResolution {
    private ArmorEnchantScrollResolution() {}

    private static void resultNotice(L1PcInstance pc, String message) {
        try { SC_SystemMessage.send(pc, message); }
        catch (RuntimeException e) {
            CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Armor enchant result notification failed", e);
        }
    }

    static ItemActionResult apply(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CIOClient client = use.client();
        L1ItemInstance item = use.item();
        L1ItemInstance targetItem = use.targetItem();
        int itemId = use.itemId();
        int safe_enchant = ((L1Armor) targetItem.getItem()).get_safeenchant();
        int armorId = targetItem.getItem().getItemId();
        int armortype = targetItem.getItem().getType();
        int enchant_level = targetItem.getEnchantLevel();
        // BEGIN_ORIGINAL_ARMORENCHANTSCROLLRESOLUTION
            if (itemId == L1ItemId.C_SCROLL_OF_ENCHANT_ARMOR || itemId == L1ItemId.Inadril_T_ScrollC
                    || itemId == L1ItemId.ENCHANT_ARMOR || itemId == L1ItemId.Inadril_T_ScrollB3
                    || itemId == L1ItemId.B_SCROLL_OF_ENCHANT_ARMOR5) {

                pc.getInventory().removeItem(item, 1);
                int rnd = _random.nextInt(100) + 1;
                if (safe_enchant == 0 && rnd <= 30) {
                    FailureEnchant(pc, targetItem, client);
                    return ItemActionResult.STOP;

                } else if (enchant_level < -1) {

                    FailureEnchant(pc, targetItem, client);
                } else {
                    SuccessEnchant(pc, targetItem, client, -1);
                }
            } else if (itemId == 810013) {
                enchant_bless_orim(pc, item, targetItem);
                return ItemActionResult.STOP;
            } else if (itemId == 3000428) {
                enchant_snapper(pc, item, targetItem);
                return ItemActionResult.STOP;
            } else if (itemId == 3000430) {
                enchant_roomtis(pc, item, targetItem);
                return ItemActionResult.STOP;
            } else if (itemId == 3000546) {
                enchant_insignia(pc, item, targetItem);
                return ItemActionResult.STOP;
            } else if (itemId == 3000547) {
                enchant_sentence(pc, item, targetItem);
                return ItemActionResult.STOP;

            } else if (itemId == 4000428) {
                enchant_snapper1(pc, item, targetItem);
                return ItemActionResult.STOP;
            } else if (itemId == 4000430) {
                enchant_roomtis1(pc, item, targetItem);
                return ItemActionResult.STOP;
            } else if (itemId == 4000546) {
                enchant_insignia1(pc, item, targetItem);
                return ItemActionResult.STOP;
            } else if (itemId == 4000547) {
                enchant_sentence1(pc, item, targetItem);
                return ItemActionResult.STOP;

            } else if (itemId == 4100034) {
                enchant_should(pc, item, targetItem);
                return ItemActionResult.STOP;

            } else if (itemId == 98077) {
                if (targetItem != null && targetItem.getItem().getType2() == 2 && ( targetItem.getItem().getItemId() == 123111 ||
                             targetItem.getItem().getItemId() == 123112 ||  targetItem.getItem().getItemId() == 123113) ) {
                    if (enchant_level >= Config.할파스갑옷) {
                        SC_SystemMessage.send(pc, "더 이상 인챈이 불가능합니다.");
                        return ItemActionResult.STOP;
                    }

                    int enchant = enchant_level < 0 ? 0
                            : enchant_level >= Config.할파스갑옷인챈.length ? Config.할파스갑옷인챈.length - 1 : enchant_level;
                    if (MJRnd.isWinning(100, Config.할파스갑옷인챈[enchant])) {
                        ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client,
                                () -> resultNotice(pc, "순간 강렬하게 빛을 내어 인챈트에 성공 하였습니다."));
                        return ItemActionResult.STOP;
                    } else {
                        ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
                            ItemActionCompletion.finishCommittedConsumable(pc, item);
                            resultNotice(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                        });
                        return ItemActionResult.STOP;
                    }
                }
            } else if (itemId == 68077) {
                if (targetItem != null && targetItem.getItem().getType2() == 2) {
                    if (enchant_level >= Config.armorLevel) {
                        SC_SystemMessage.send(pc, "더 이상 인챈이 불가능합니다.");
                        return ItemActionResult.STOP;
                    }
                    if (targetItem.getItem().getType2() == 2 && (targetItem.getItem().getItemId() == 123111 ||
                             targetItem.getItem().getItemId() == 123112 || targetItem.getItem().getItemId() == 123113)) {
                        SC_SystemMessage.send(pc, "할파스 갑옷 종류는 강화 할 수 없습니다.");
                        return ItemActionResult.STOP;
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
                        ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client,
                                () -> resultNotice(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다."));
                        return ItemActionResult.STOP;
                    } else {
                        ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
                            ItemActionCompletion.finishCommittedConsumable(pc, item);
                            resultNotice(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                        });
                        return ItemActionResult.STOP;
                    }

                }
            } else if (itemId == 3000380) {
                if (targetItem != null) {
                    if (enchant_level >= Config.accessoryLevel) {
                        SC_SystemMessage.send(pc, "더 이상 인챈이 불가능합니다.");
                        return ItemActionResult.STOP;
                    }
                    int k3 = CommonUtil.random(100);

                    int enchant = enchant_level < 0 ? 0
                            : enchant_level >= Config.accessory.length ? Config.accessory.length - 1
                                    : enchant_level;

                            int rnd = _random.nextInt(100) + 1;
                            if (rnd <= Config.accessory[enchant]) {
                                ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client, () -> {
                                    resultNotice(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
                                    if (pc.isGm()) resultNotice(pc, "\\fY[확률: " + rnd + "] [+" + enchant
                                            + " 악세 확률범위: 1~" + Config.accessory[enchant] + "] \\f2[성공]");
                                });
                            } else {
                                ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
                                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                                    resultNotice(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                                    if (pc.isGm()) resultNotice(pc, "\\fY[확률: " + rnd + "] [+" + enchant
                                            + " 악세 확률범위: 1~" + Config.accessory[enchant] + "] [실패]");
                                });
                            }
                            return ItemActionResult.STOP;
                }
            } else if (itemId == 4000518) {
                if (targetItem != null) {

                    if (enchant_level >= Config.accessorytest1) {
                        SC_SystemMessage.send(pc, "해당 인챈 이하의 고대의 룬 에만 사용 가능합니다.");
                        return ItemActionResult.STOP;
                    }

                    int enchant = enchant_level < 0 ? 0
                            : enchant_level >= Config.ancient2.length ? Config.ancient2.length - 1
                                    : enchant_level;
                    if (MJRnd.isWinning(100, Config.ancient2[enchant])) {
                        ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client,
                                () -> resultNotice(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다."));
                        return ItemActionResult.STOP;
                    } else {
                        ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
                            ItemActionCompletion.finishCommittedConsumable(pc, item);
                            resultNotice(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                        });
                        return ItemActionResult.STOP;
                    }
                }

            } else if (itemId == 3000518) {
                if (targetItem != null) {

                    if (enchant_level >= Config.accessorytest) {
                        SC_SystemMessage.send(pc, "해당 인챈 이하의 할파스의 룬 에만 사용 가능합니다.");
                        return ItemActionResult.STOP;
                    }

                    int enchant = enchant_level < 0 ? 0
                            : enchant_level >= Config.ancient.length ? Config.ancient.length - 1
                                    : enchant_level;
                    if (MJRnd.isWinning(100, Config.ancient[enchant])) {
                        ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client, () -> {
                            resultNotice(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
                            if (pc.isGm()) resultNotice(pc, targetItem.getName() + " \\f:확률: " + Config.ancient1[enchant]);
                        });
                    } else {
                        ItemReceiptOperations.UseDurableConsume(pc, item, () -> {
                            ItemActionCompletion.finishCommittedConsumable(pc, item);
                            resultNotice(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                            if (pc.isGm()) resultNotice(pc, targetItem.getName() + " \\f:확률: " + Config.ancient1[enchant]);
                        });
                    }
                    return ItemActionResult.STOP;
                }
            } else if (itemId == 3000517) {
                if (targetItem != null) {

                    if (enchant_level >= Config.accessorytest) {
                        SC_SystemMessage.send(pc, "해당 인챈 이하의 할파스의 룬 에만 사용 가능합니다.");
                        return ItemActionResult.STOP;
                    }

                    int enchant = enchant_level < 0 ? 0
                            : enchant_level >= Config.ancient1.length ? Config.ancient1.length - 1
                                    : enchant_level;
                    if (MJRnd.isWinning(100, Config.ancient1[enchant])) {
                        ItemReceiptOperations.UseAtomicEnchantSuccess(pc, item, targetItem, client, () -> {
                            resultNotice(pc, "순간 강렬하게 빛을내어 인챈트에 성공 하였습니다.");
                            if (pc.isGm()) resultNotice(pc, targetItem.getName() + " \\f:확률: " + Config.ancient1[enchant]);
                        });
                        return ItemActionResult.STOP;
                    } else {
                        // Halpha rune failure destroys the scroll AND the rune.
                        // The old two separate removeItem() calls could commit
                        // only one deletion after a database failure. Debit both
                        // exact objects in one existing CacheD receipt instead.
                        final String destroyedName = targetItem.getName();
                        final boolean gm = pc.isGm();
                        final int chance = Config.ancient1[enchant];
                        ItemReceiptOperations.UseDurableConsumeTwoInput(pc, item, targetItem, () -> {
                            // The original scroll may no longer have an inventory row.
                            // Start the item delay only after successful commit.
                            ItemActionCompletion.finishCommittedConsumable(pc, item);
                            try {
                                SC_SystemMessage.send(pc, "한 순간 강렬하게 빛났지만 인챈트에 실패 하였습니다.");
                            } catch (RuntimeException notifyFailure) {
                                CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                        "Halpha rune failure notification failed", notifyFailure);
                            }
                            if (gm) {
                                try {
                                    SC_SystemMessage.send(pc, destroyedName + " \\f:확률: " + chance);
                                } catch (RuntimeException notifyFailure) {
                                    CreatureItemActions.ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                            "Halpha rune GM notification failed", notifyFailure);
                                }
                            }
                        });
                        return ItemActionResult.STOP;
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

                enchant_chance_armor = ArmorEnchantChanceCalculation.calculate(
                        pc, targetItem, armorId, armortype, enchant_level, rnd);

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
        // END_ORIGINAL_ARMORENCHANTSCROLLRESOLUTION
        return ItemActionResult.COMPLETE;
    }
}
