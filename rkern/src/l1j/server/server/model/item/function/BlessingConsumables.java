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
/** Extracted BlessingConsumables handlers; effect, consumption and transaction order unchanged. */
final class BlessingConsumables {
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(BlessingConsumables.class.getName());

    private BlessingConsumables() {}

    // Post-commit presentation is best effort; never mask a successful debit.
    private static void notifyHolyWater(L1PcInstance pc) {
        try {
            SC_SkillSound.send(pc, pc.getId(), 190);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Holy water self sound failed", failure);
        }
        try {
            SC_SkillSound.broadcast(pc, pc.getId(), 190);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Holy water broadcast sound failed", failure);
        }
        try {
            SC_ServerMessage.send(pc, 1141);
        } catch (RuntimeException failure) {
            LOG.log(java.util.logging.Level.WARNING, "Holy water notice failed", failure);
        }
    }

    static ItemActionResult handleAinGem(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        if (AinBlessingGemConsumables.useGemCase(pc, item, itemId)) return ItemActionResult.STOP;
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleMaanOrRaid(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        final SpecialConsumableRouter.Result result =
        SpecialConsumableRouter.use(pc, item);

        if (result == SpecialConsumableRouter.Result.RETURN) {
            return ItemActionResult.STOP;
        }

        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleMembershipBuff(ItemActionContext use) {
        L1PcInstance pc = use.player();
        int itemId = use.itemId();
        if (pc.getInventory().DeleteItemInInventory(itemId, 1)) {
            if (pc.getClan() != null)
                pc.getClan().addBlessCount(10000000);
            SC_SystemMessage.send(pc, "축복의 기운 1000 추가되었습니다.");
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleHolyWater(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc.getZoneType() == 1) {
            SC_ServerMessage.send(pc, 79);
            return ItemActionResult.STOP;
        }
        UseDurableConsume(pc, item, () -> {
            try {
                DamagePoison.doInfection(pc, pc, 3000, 5, false, 30000);
            } finally {
                ItemActionCompletion.finishCommittedConsumable(pc, item);
            }
        });
        return ItemActionResult.STOP;
    }

    static ItemActionResult handleHolyWaterOfEva(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc.hasSkillEffect(STATUS_HOLY_WATER_OF_EVA)) {
            SC_ServerMessage.send(pc, 79);
            return ItemActionResult.STOP;
        }
        UseDurableConsume(pc, item, () -> {
            try {
                if (pc.hasSkillEffect(STATUS_HOLY_MITHRIL_POWDER)) {
                    pc.removeSkillEffect(STATUS_HOLY_MITHRIL_POWDER);
                }
                pc.setSkillEffect(STATUS_HOLY_WATER, 900 * 1000);
                notifyHolyWater(pc);
            } finally {
                ItemActionCompletion.finishCommittedConsumable(pc, item);
            }
        });
        return ItemActionResult.STOP;
    }
}
