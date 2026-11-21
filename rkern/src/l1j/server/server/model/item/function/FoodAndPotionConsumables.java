package l1j.server.server.model.item.function;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.sc.packet.SC_CurseBlind;
import l1j.server.server.model.network.core.sc.packet.SC_Karma;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.character.effect.poison.DamagePoison;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FLOATING_EYE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_MITHRIL_POWDER;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_WATER;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_HOLY_WATER_OF_EVA;
import static l1j.server.server.model.item.function.CombatAbilityItems.블레이즈스턴;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.PcRoomPassTransactions.피씨방코인;
import static l1j.server.server.model.item.function.PotionEffects.useCashScroll;
import static l1j.server.server.model.item.function.PotionEffects.usePotionCase;

/** Item-specific Consumables effects; selected once by ItemActionDispatcher. */
/** Extracted FoodAndPotionConsumables handlers; effect, consumption and transaction order unchanged. */
final class FoodAndPotionConsumables {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(FoodAndPotionConsumables.class.getName());

    private FoodAndPotionConsumables() {}

    // The inventory debit has already committed before optional packets are sent.
    private static void sendFoodNotices(L1PcInstance pc, String nameId,
                                         boolean blindNotice, boolean foodNotice) {
        if (blindNotice) {
            try {
                SC_CurseBlind.send(pc, 2);
            } catch (RuntimeException failure) {
                LOG.log(java.util.logging.Level.WARNING, "Committed floating-eye food: blind notice failed", failure);
            }
            // Preserve the old client ordering: blindness notice precedes reveal sync.
            // The reveal path only publishes AOI presentation, not inventory state.
            try {
                pc.showFloatingEyeToInvis();
            } catch (RuntimeException failure) {
                LOG.log(java.util.logging.Level.WARNING, "Committed floating-eye food: reveal sync failed", failure);
            }
        }
        if (foodNotice) {
            try {
                SC_PacketBox.send(pc, SC_PacketBox.FOOD, pc.get_food());
            } catch (RuntimeException failure) {
                LOG.log(java.util.logging.Level.WARNING, "Committed food: hunger packet failed", failure);
            }
            try {
                SC_ServerMessage.send(pc, 76, nameId);
            } catch (RuntimeException failure) {
                LOG.log(java.util.logging.Level.WARNING, "Committed food: item notice failed", failure);
            }
        }
    }

    private static void sendKarmaNotices(L1PcInstance pc) {
        try {
            SC_Karma.send(pc);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Committed karma potion: karma packet failed", failure);
        }
        try {
            SC_SystemMessage.send(pc, pc.getName() + "님의 우호도가 향상되었습니다.");
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Committed karma potion: notice failed", failure);
        }
    }

    static ItemActionResult handleCashScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        if (itemId == 30063) {
            if (!(pc.getMapId() >= 1005 && pc.getMapId() <= 1022
                    || pc.getMapId() > 6000 && pc.getMapId() < 6999)) {
                SC_ServerMessage.send(pc, 1891);

                return ItemActionResult.STOP;
            }
        }
        UseDurableConsume(pc, item, () -> {
            try {
                useCashScroll(pc, itemId);
            } finally {
                ItemActionCompletion.finishCommittedConsumable(pc, item);
            }
        });
        return ItemActionResult.STOP;
    }

    static ItemActionResult handlePotion(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        if (PotionEffects.usePotionCase(pc, item, itemId)) return ItemActionResult.STOP;
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleFood(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        // Capture immutable template data before the receipt can remove the input stack.
        final int foodVolume = item.getItem().getFoodVolume() / 10;
        final String nameId = item.getItem().getNameId();
        final boolean floatingEye = itemId == 40057;
        final boolean extraFood = itemId == 210039 || itemId == 30085;
        UseDurableConsume(pc, item, () -> {
            try {
                boolean blindNotice = false;
                if (floatingEye) {
                    pc.setSkillEffect(STATUS_FLOATING_EYE, 0);
                    blindNotice = pc.isBlind();
                }
                final boolean foodNotice = pc.get_food() < 225;
                if (foodNotice) {
                    pc.set_food(pc.get_food() + 10);
                    if (extraFood) pc.set_food(pc.get_food() + 90);
                    pc.add_food(foodVolume <= 0 ? 5 : foodVolume);
                }
                // The committed gameplay state cannot be skipped by any UI/AOI failure.
                sendFoodNotices(pc, nameId, blindNotice, foodNotice);
            } finally {
                ItemActionCompletion.finishCommittedConsumable(pc, item);
            }
        });
        return ItemActionResult.STOP;
    }

    static ItemActionResult handleKarmaPotion(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc.getKarma() >= -10000000) {
            final int karmaDelta = (int) (-15000 * Config.RATE_KARMA);
            UseDurableConsume(pc, item, () -> {
                try {
                    pc.addKarma(karmaDelta);
                    sendKarmaNotices(pc);
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });
            return ItemActionResult.STOP;
        } else {
            SC_ServerMessage.send(pc, 79);
        }
        return ItemActionResult.COMPLETE;
    }
}
