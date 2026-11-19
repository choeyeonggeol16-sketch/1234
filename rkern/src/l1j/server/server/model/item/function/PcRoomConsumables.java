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
import l1j.server.server.model.poison.L1DamagePoison;
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
/** Extracted PcRoomConsumables handlers; effect, consumption and transaction order unchanged. */
final class PcRoomConsumables {
    private PcRoomConsumables() {}

    static ItemActionResult handlePcRoomBuff(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        if (pc.PC방_버프) {
            SC_SystemMessage.send(pc, "이미 PC방버프가 적용 되어있습니다.");
            return ItemActionResult.STOP;
        } else {
            피씨방코인(pc, itemId, item, 7);
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handlePcRoomGift(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        if (pc.PC방_버프) {
            SC_SystemMessage.send(pc, "이미 PC방버프가 적용 되어있습니다.");
            return ItemActionResult.STOP;
        } else {
            피씨방코인(pc, itemId, item, 3);
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleGrowthPcRoomBuff(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        if (pc.PC방_버프) {
            SC_SystemMessage.send(pc, "이미 PC방버프가 적용 되어있습니다.");
            return ItemActionResult.STOP;
        } else {
            피씨방코인(pc, itemId, item, 30);
        }
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleSupportBuff(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        if (pc.getLevel() <= 55) {
            SC_SystemMessage.send(pc, "레벨 56 이상만 사용가능 합니다.");
            return ItemActionResult.STOP;
        }
        if (pc.PC방_버프) {
            SC_SystemMessage.send(pc, "이미 PC방버프가 적용 되어있습니다.");
            return ItemActionResult.STOP;
        } else {
            피씨방코인(pc, itemId, item, 1);
        }
        return ItemActionResult.COMPLETE;
    }
}
