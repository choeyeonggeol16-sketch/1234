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
/** Extracted CombatBuffConsumables handlers; effect, consumption and transaction order unchanged. */
final class CombatBuffConsumables {
    private CombatBuffConsumables() {}

    static ItemActionResult handleCastGmBuff(ItemActionContext use) {
        L1PcInstance pc = use.player();
        int itemId = use.itemId();
        {
            int buffId = itemId - 34243;
            L1SkillUse.cast(pc, buffId, pc.getId(), pc.getX(), pc.getY(), null, 0,
                    L1SkillUse.TYPE_GMBUFF);
        }
            return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleDragonPearl(ItemActionContext use) {
        L1PcInstance pc = use.player();
        if (pc.hasSkillEffect(L1SkillId.잠재력진주)) {
            SC_SystemMessage.send(pc, "3단 가속 효과가 이미 적용 중입니다.");
            return ItemActionResult.STOP;
        }

        SC_SystemMessage.send(pc, "3단 가속 효과가 이미 적용 중입니다.");
        return ItemActionResult.COMPLETE;
    }

    static ItemActionResult handleComaCoin(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        {
            UseDurableConsume(pc, item, () -> {
                try {
                    L1SkillUse.cast(pc, 50007, pc.getId(), pc.getX(), pc.getY(),
                            null, 0, L1SkillUse.TYPE_SPELLSC);
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });
            return ItemActionResult.STOP;
        }
    }

    static ItemActionResult handleBlessWeaponScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc.hasSkillEffect(L1SkillId.BLESS_WEAPON)) {
            SC_SystemMessage.send(pc, "블레스웨폰 스킬이 적용중입니다.");
            return ItemActionResult.STOP;
        }
        UseDurableConsume(pc, item, () -> {
            try {
                if (pc.hasSkillEffect(L1SkillId.ENCHANT_WEAPON)) {
                    pc.removeSkillEffect(L1SkillId.ENCHANT_WEAPON);
                }
                L1SkillUse.cast(pc, L1SkillId.ENCHANT_WEAPON, pc.getId(), pc.getX(), pc.getY(),
                        null, 0, L1SkillUse.TYPE_GMBUFF);
            } finally {
                ItemActionCompletion.finishCommittedConsumable(pc, item);
            }
        });
        return ItemActionResult.STOP;
    }

    static ItemActionResult handleDragonSkill(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        int spellTargetId = use.spellTargetId();
        if(Config.Neige_Myth_Skill == 2){
        if(pc.getCurrentSpriteId() == 19082 || pc.getCurrentSpriteId() == 18942
            || pc.getCurrentSpriteId() == 21140 || pc.getCurrentSpriteId() == 19514){
        블레이즈스턴(pc, spellTargetId, item);
        }else{
            pc.sendPackets("사용할 수 있는 조건이 아닙니다.");

        }
        }
        return ItemActionResult.COMPLETE;
    }
}
