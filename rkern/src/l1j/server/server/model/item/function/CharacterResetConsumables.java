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
/** Extracted CharacterResetConsumables handlers; effect, consumption and transaction order unchanged. */
final class CharacterResetConsumables {
    private CharacterResetConsumables() {}

    static ItemActionResult handleLevelBuff(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc.getLevel() >= 50) {
            SC_MessageYN.send(pc, CS_Attr.MSGCODE_622_KDINIT, 622, "당신의 킬/데스를 초기화 하시겠습니까?");
            pc.setKillDeathInitializeItem(item);
        } else
            pc.sendPackets("레벨 50 이하는 사용이 불가능 합니다.");
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleFoodBuff(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc.getLevel() >= 50) {
            SC_MessageYN.send(pc, CS_Attr.MSGCODE_622_Name, 622, "당신의 성별을 전환 하시겠습니까?");
            pc.setNameInstance(item);
        } else
            pc.sendPackets("레벨 50 이하는 사용이 불가능 합니다.");
        return ItemActionResult.COMPLETE;
    }
}
