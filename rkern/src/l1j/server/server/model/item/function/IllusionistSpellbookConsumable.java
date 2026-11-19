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

/** Dedicated item-use handler extracted from a mixed domain. */
final class IllusionistSpellbookConsumable {
    private IllusionistSpellbookConsumable() {}

    static ItemActionResult blackWizardBooks(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CIOClient client = use.client();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int itemObjectId = use.itemObjectId();
        int skillId = use.skillId();
        int spellTargetId = use.spellTargetId();
        int spellTargetX = use.spellX();
        int spellTargetY = use.spellY();

            if (pc.isBlackwizard() || pc.isGm()) {
                if (itemId >= 210000 && itemId <= 210004 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (((itemId >= 210005 && itemId <= 210007) ||  (itemId == 210009)) && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (((itemId >= 210010 && itemId <= 210012) ||  (itemId == 210014))&& pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (itemId >= 210015 && itemId <= 210019 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (itemId == 4100109 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (itemId == 3000096 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (itemId == 5618 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else if (itemId == 5617 && pc.getLevel() >= 1) {
                    SpellBook6(pc, item, client);
                } else {
                    SC_ServerMessage.send(pc, 312);
                }
            } else {
                SC_ServerMessage.send(pc, 79);
            }
                return ItemActionResult.COMPLETE;
    }
}
