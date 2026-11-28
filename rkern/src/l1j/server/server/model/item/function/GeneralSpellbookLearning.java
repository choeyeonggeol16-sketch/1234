package l1j.server.server.model.item.function;

import l1j.server.server.model.character.skill.acquire.CSkillBook;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.character.skill.acquire.CSkillAcquireDB.Book;

final class GeneralSpellbookLearning {
    private GeneralSpellbookLearning() {}

    static void SpellBook(L1PcInstance pc, L1ItemInstance item, boolean isLawful) {
        CSkillBook.learn(pc, item, Book.GENERAL, isLawful ? 224 : 231);
    }
}
