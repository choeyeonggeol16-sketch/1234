package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.cooking.L1Cooking;
import l1j.server.server.model.network.core.session.CUserSession;
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
final class WorldDestinationItems {
    private WorldDestinationItems() {}

    static ItemActionResult coordinateScroll(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CUserSession client = use.client();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int itemObjectId = use.itemObjectId();
        int skillId = use.skillId();
        int spellTargetId = use.spellTargetId();
        int spellTargetX = use.spellX();
        int spellTargetY = use.spellY();

            int locX = ((L1EtcItem) item.getItem()).get_locx();
            int locY = ((L1EtcItem) item.getItem()).get_locy();
            short mapId = ((L1EtcItem) item.getItem()).get_mapid();
            if (locX != 0 && locY != 0) {

                if (pc.hasSkillEffect(L1SkillId.이터디스) || pc.hasSkillEffect(L1SkillId.버닝샷)  ) {
                    SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
                    return ItemActionResult.STOP;
                }

                if (pc.getMap().isEscapable() || pc.isGm()) {
                    UseDurableConsume(pc, item, () -> {
                        try {
                            pc.start_teleport(locX, locY, mapId, pc.getHeading(), 169, true, pc.getAutoHunt());
                            cancelAbsoluteBarrier(pc);
                        } finally {
                            ItemActionCompletion.finishCommittedConsumable(pc, item);
                        }
                    });
                    return ItemActionResult.STOP;
                } else {
                    SC_ServerMessage.send(pc, 647);
                    cancelAbsoluteBarrier(pc);
                }
            } else {
                if (item.getCount() < 1) {
                    SC_ServerMessage.send(pc, 329, item.getLogName());
                }
            }
                return ItemActionResult.STOP;
    }
    /** Coordinate-targeted administrator wand (42501); ownership is not authorization. */
    static ItemActionResult handlePointTeleport(ItemActionContext use) {
        L1PcInstance pc = use.player();
        if (!pc.isGm()) return ItemActionResult.STOP;
        pc.start_teleport(use.spellX(), use.spellY(), pc.getMapId(), pc.getHeading(),
                169, false, pc.getAutoHunt());
        return ItemActionResult.COMPLETE;
    }

}
