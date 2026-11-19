package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.cooking.L1Cooking;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.templates.L1EtcItem;
import static l1j.server.server.model.item.function.CreatureItemActions.cancelAbsoluteBarrier;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;
import static l1j.server.server.model.item.function.WarriorSkillBookItems.전사스킬;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook1;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook3;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook4;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook5;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook6;
import static l1j.server.server.model.item.function.SpellBookItems.useElfSpellBook;
import static l1j.server.server.model.item.function.SpellBookItems.useSpellBook;
import static l1j.server.server.model.item.function.FurniturePlacementItems.useFurnitureItem;

/** Default-path item domain, extracted without changing action branch bodies. */
final class UnlistedMagicScrolls {
    private UnlistedMagicScrolls() {}

    static ItemActionResult castSkillScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CIOClient client = use.client();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int itemObjectId = use.itemObjectId();
        int skillId = use.skillId();
        int spellTargetId = use.spellTargetId();
        int spellTargetX = use.spellX();
        int spellTargetY = use.spellY();


            int skillid = itemId - 40858;

            if(pc.isSkillDelay()) {
                SC_ServerMessage.send(pc, 281);
                pc.teleportunLock(skillId);
                return ItemActionResult.STOP;
            }

            int useType = item.getItem().getUseType();
            if (useType == 30 || useType == 0) {
                spellTargetId = pc.getId();
            } else {
                if (spellTargetId == pc.getId() || spellTargetId <= 0) {
                    SC_ServerMessage.send(pc, 281);
                    return ItemActionResult.STOP;
                }
            }
            final int spellTarget = spellTargetId;
            final int spellX = spellTargetX;
            final int spellY = spellTargetY;
            UseDurableConsume(pc, item, () -> {
                try {
                    cancelAbsoluteBarrier(pc);
                    L1SkillUse.cast(pc, skillid, spellTarget, spellX, spellY, null, 0, L1SkillUse.TYPE_SPELLSC);
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, item);
                }
            });

                return ItemActionResult.STOP;
    }
}
