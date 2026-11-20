package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.cooking.L1Cooking;
import l1j.server.server.model.network.core.connection.CUserSocket;
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

/** Dedicated item-use handler extracted from a mixed domain. */
final class KnightSpellbookConsumable {
    private KnightSpellbookConsumable() {}

    static ItemActionResult knightBooks(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CUserSocket client = use.client();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int itemObjectId = use.itemObjectId();
        int skillId = use.skillId();
        int spellTargetId = use.spellTargetId();
        int spellTargetX = use.spellX();
        int spellTargetY = use.spellY();

            if (pc.isKnight() || pc.isGm()) {
                if (itemId == 40165 && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);

                } else if (itemId == 40164 && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);
                } else if ((itemId == 40166) && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);

                } else if (itemId == 4100101 && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);
                } else if (itemId == 41148 && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);
                } else if (itemId == 4100102 || itemId == 3000092 && pc.getLevel() >= 1) {
                    SpellBook3(pc, item, client);
                } else {
                    SC_ServerMessage.send(pc, 312);
                }
            } else {
                SC_ServerMessage.send(pc, 79);
            }
                return ItemActionResult.COMPLETE;
    }
}
