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

/** Retains the original default-path predicate order; dispatches to domain-specific items. */
final class UnlistedItemRouter {
    private UnlistedItemRouter() {}

    static ItemActionResult use(ItemActionContext use) {
        L1PcInstance pc = use.player();
        CIOClient client = use.client();
        L1ItemInstance item = use.item();
        int itemId = use.itemId();
        int itemObjectId = use.itemObjectId();
        int skillId = use.skillId();
        int spellTargetId = use.spellTargetId();
        int spellTargetX = use.spellX();
        int spellTargetY = use.spellY();
        if (itemId >= 40136 && itemId <= 40161 || itemId == 410027) return CelebrationSoundItems.fireworks(use);
        else if ((itemId >= 40859 && itemId <= 40898) && itemId != 40863) return UnlistedMagicScrolls.castSkillScroll(use);
        else if (itemId >= 41357 && itemId <= 41382) return CelebrationSoundItems.alphabetSound(use);
        else if ((itemId >= 41277 && itemId <= 41292) || (itemId >= 49049 && itemId <= 49064) || (itemId >= 210048 && itemId <= 210063)
                || (itemId >= 30051 && itemId <= 30054) || (itemId >= 4100156 && itemId <= 4100159) || (itemId >= 3000129 && itemId <= 3000130)
                || (itemId >= 42650 && itemId <= 42653)) return CookingAndFurnitureItems.cooking(use);
        else if (itemId >= 41383 && itemId <= 41400) return CookingAndFurnitureItems.furniture(use);
        else if (itemId > 40169 && itemId <= 40223 || itemId >= 45000 && itemId <= 45022 || itemId == 40225
                || itemId == 3000095) return WizardSpellbookConsumable.wizardBooks(use);
        else if (itemId == 40226  ||itemId == 40227  ||itemId == 40229  ||itemId == 40230
                || itemId == 5560 || itemId == 3000090 || itemId == 3000089) return RoyalSpellbookConsumable.royalBooks(use);
        else if ((itemId >= 40232 && itemId <= 40264) || (itemId >= 41149 && itemId <= 41153)
                || itemId == 3000091 || (itemId >= 3000511 && itemId <= 3000513) || itemId == 4100295) return ElfClassSpellbookRoute.elfBooks(use);
        else if (itemId > 40264 && itemId < 40280 || itemId == 5559 || itemId == 4100103
                || itemId == 4100104 || itemId == 4100105) return DarkElfSpellbookConsumable.darkElfBooks(use);
        else if (itemId >= 40164 && itemId <= 40166 || itemId == 41148
                || itemId == 3000092 || itemId == 4100101 || itemId == 4100102) return KnightSpellbookConsumable.knightBooks(use);
        else if ((itemId >= 210020 && itemId <= 210034) || itemId == 4100106) return DragonKnightSpellbookConsumable.dragonKnightBooks(use);
        else if ((itemId >= 210000 && itemId <= 210019) || itemId == 3000096 || itemId == 4100109 || itemId == 5618 || itemId == 5617) return IllusionistSpellbookConsumable.blackWizardBooks(use);
        else if (itemId >= 210123 && itemId <= 210132 || itemId == 3000094 || itemId == 210121 ) return WarriorSpellbookConsumable.warriorBooks(use);
        else return WorldDestinationItems.coordinateScroll(use);
    }
}
