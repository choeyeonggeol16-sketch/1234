package l1j.server.server.model.character.skill.acquire;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.CSkillDB;
import l1j.server.server.model.character.skill.acquire.CSkillAcquireDB.Book;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.CSkillAcquireStorage;
import l1j.server.server.templates.L1Skills;

public final class CSkillBook {
    private static final Logger LOG = Logger.getLogger(CSkillBook.class.getName());

    private CSkillBook() {}

    public static void commitLearning(L1PcInstance pc, L1ItemInstance book, L1Skills skill, int[] levels, int sound) {
        if (pc.HaveSkill(skill.getSkillId()) || pc.getInventory().GetByServerID(book.getId()) != book) return;
        int playerId = pc.getId();
        try {
            if (book.getRecordingColumns() != 0)
                pc.getInventory().saveItem(book, CUserInventory.COL_SAVE_ALL);
            try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                    CacheD.accountOrderKey("item-use:", playerId))) {
                if (pc.HaveSkill(skill.getSkillId()) || !tx.debit(book, 1)) return;
                tx.requireTables("character_skills");
                tx.sql(con -> CSkillAcquireStorage.insert(con, playerId, skill));
                tx.onFailure(() -> SC_SystemMessage.send(pc,
                        "스킬 습득을 저장하지 못했습니다. 스킬북을 확인해 주세요."));
                tx.commit(() -> {
                    pc.AcquireSkill(skill.getSkillId());
                    try { SC_AddSkill.send(pc, levels); }
                    catch (RuntimeException ex) { LOG.log(Level.WARNING, "Skill list packet failed after commit", ex); }
                    final long packed = SC_SkillSound.pack(playerId, sound);
                    try { SC_SkillSound.sendPacked(pc, packed); }
                    catch (RuntimeException ex) { LOG.log(Level.WARNING, "Skill sound packet failed after commit", ex); }
                    try { SC_SkillSound.broadcastPacked(pc, packed); }
                    catch (RuntimeException ex) { LOG.log(Level.WARNING, "Skill sound broadcast failed after commit", ex); }
                });
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Skill book receipt prepare failed: " + playerId + "/" + skill.getSkillId(), ex);
            SC_SystemMessage.send(pc, "스킬북 사용을 처리하지 못했습니다.");
        }
    }

    public static void learn(L1PcInstance pc, L1ItemInstance item, Book book) {
        learn(pc, item, book, book.sound);
    }

    public static void learn(L1PcInstance pc, L1ItemInstance item, Book book, int sound) {
        List<L1Skills> matches = CSkillDB.getInstance().acquireDB().findBook(book, item.getItem());
        if (matches.isEmpty()) {
            SC_SystemMessage.send(pc, "습득할 수 있는 스킬을 찾지 못했습니다.");
            return;
        }
        L1Skills learned = matches.getLast();
        if (book == Book.ELF && !canLearnElfBook(pc, matches)) return;
        if (pc.HaveSkill(learned.getSkillId())) {
            SC_SystemMessage.send(pc, "이미 배운 마법입니다.");
            return;
        }
        int[] levels = new int[30];
        for (L1Skills skill : matches) {
            int level = skill.getSkillLevel();
            if (level >= 1 && level <= 28) levels[level - 1] = skill.getId();
            else if (level == 29 && book.level29Slot >= 0) levels[book.level29Slot] = skill.getId();
        }
        commitLearning(pc, item, learned, levels, sound);
    }

    private static boolean canLearnElfBook(L1PcInstance pc, List<L1Skills> matches) {
        if (pc.isGm()) return true;
        int attr = pc.getElfAttr();
        if (attr != 0 && attr != 1 && attr != 2 && attr != 4 && attr != 8) return true;
        for (L1Skills skill : matches) {
            if (skill.getAttr() != 0 && attr != skill.getAttr()) {
                SC_ServerMessage.send(pc, 79);
                return false;
            }
        }
        return true;
    }
}
