package l1j.server.server.model.item.function;

import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.SkillCheck;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.storage.CacheD;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Skills;

/** One learning flow; book families supply only their existing differences. */
final class SkillBookLearning {
    enum Kind {
        DARK_ELF(97, 241, "흑정령의 수정 (", 231, 29, true),
        ELF(129, 179, "정령의 수정 (", 224, -1, false),
        KNIGHT(87, 96, "기술서 (", 224, -1, false),
        PRINCE(113, 123, "마법서 (", 224, -1, false),
        DRAGON_KNIGHT(181, 199, "용기사의 서판(", 224, -1, false),
        ILLUSIONIST(201, 224, "기억의 수정(", 224, 28, false);

        final int first, last, sound, level29Slot;
        final String prefix;
        final boolean skipMissing;

        Kind(int first, int last, String prefix, int sound, int level29Slot, boolean skipMissing) {
            this.first=first; this.last=last; this.prefix=prefix; this.sound=sound;
            this.level29Slot=level29Slot; this.skipMissing=skipMissing;
        }
    }

    private static final Logger LOG = Logger.getLogger(SkillBookLearning.class.getName());
    private SkillBookLearning() {}

    /** Book debit and mastery row share the same CacheD receipt. No skill is
     * published to the player until the receipt commits. */
    static void commitLearning(L1PcInstance pc, L1ItemInstance book, int skillId,
                               String skillName, int[] levels, int sound) {
        if (pc == null || book == null || skillId <= 0 || skillName == null
                || pc.isSkillMastery(skillId)
                || pc.getInventory().getItem(book.getId()) != book) return;
        final int playerId = pc.getId();
        try {
            if (book.getRecordingColumns() != 0)
                pc.getInventory().saveItem(book, L1PcInventory.COL_SAVE_ALL);
            try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                    CacheD.accountOrderKey("item-use:", playerId))) {
            if (!tx.debit(book, 1) || pc.isSkillMastery(skillId)) return;
            tx.requireTables("characters", "character_skills");
            tx.sql(con -> {
                // The character row serializes competing skill learning sessions.
                try (PreparedStatement lock = con.prepareStatement(
                        "SELECT objid FROM characters WHERE objid=? FOR UPDATE")) {
                    lock.setInt(1, playerId);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Missing skill owner " + playerId);
                    }
                }
                try (PreparedStatement check = con.prepareStatement(
                        "SELECT skill_id FROM character_skills WHERE char_obj_id=? AND skill_id=? FOR UPDATE")) {
                    check.setInt(1, playerId);
                    check.setInt(2, skillId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) throw new SQLException("Skill already learned: " + skillId);
                    }
                }
                try (PreparedStatement insert = con.prepareStatement(
                        "INSERT INTO character_skills (char_obj_id, skill_id, skill_name, is_active, activetimeleft)"
                        + " VALUES(?,?,?,0,0)")) {
                    insert.setInt(1, playerId);
                    insert.setInt(2, skillId);
                    insert.setString(3, skillName);
                    if (insert.executeUpdate() != 1)
                        throw new SQLException("Skill persistence did not insert a row: " + skillId);
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "스킬 습득을 저장하지 못했습니다. 스킬북을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                // Publish the economic effect before any optional client packets.
                pc.setSkillMastery(skillId);
                SkillCheck.getInstance().AddSkill(playerId, skillId);
                try { SC_AddSkill.send(pc, levels); }
                catch (RuntimeException ex) { LOG.log(Level.WARNING, "Skill list packet failed after commit", ex); }
                try {
                    final long packed = SC_SkillSound.pack(playerId, sound);
                    try { SC_SkillSound.sendPacked(pc, packed); }
                    catch (RuntimeException ex) { LOG.log(Level.WARNING, "Skill sound packet failed after commit", ex); }
                    try { SC_SkillSound.broadcastPacked(pc, packed); }
                    catch (RuntimeException ex) { LOG.log(Level.WARNING, "Skill sound broadcast failed after commit", ex); }
                } catch (RuntimeException ex) {
                    LOG.log(Level.WARNING, "Skill sound preparation failed after commit", ex);
                }
            });
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Skill book receipt prepare failed: " + playerId + "/" + skillId, ex);
            SC_SystemMessage.send(pc, "스킬북 사용을 처리하지 못했습니다.");
        }
    }

    static void learn(L1PcInstance pc, L1ItemInstance item, Kind kind) {
        String skillName = "";
        int skillId = 0;
        int[] levels = new int[30];
        SkillsTable table = SkillsTable.getInstance();
        final String bookName = item.getItem().getName();
        for (int id=kind.first; id<=kind.last; id++) {
            L1Skills skill = table.getTemplate(id);
            if (skill == null) {
                if (kind.skipMissing) System.out.println("누락된 스킬 아이디 : " + id);
                continue;
            }
            if (!matches(bookName, kind.prefix, skill.getName())) continue;
            if (kind == Kind.ELF && !pc.isGm() && skill.getAttr()!=0 && pc.getElfAttr()!=skill.getAttr()) {
                int attr = pc.getElfAttr();
                if (attr==0 || attr==1 || attr==2 || attr==4 || attr==8) {
                    SC_ServerMessage.send(pc, 79);
                    return;
                }
            }
            int level = skill.getSkillLevel();
            skillName = skill.getName();
            skillId = skill.getSkillId();
            if (level>=1 && level<=28) levels[level-1] = skill.getId();
            else if (level==29 && kind.level29Slot>=0) levels[kind.level29Slot] = skill.getId();
        }
        // No matching template: do not send an empty skill packet or destroy the book.
        if (skillId == 0) {
            SC_SystemMessage.send(pc, "습득할 수 있는 스킬을 찾지 못했습니다.");
            return;
        }
        if (pc.isSkillMastery(skillId)) {
            SC_SystemMessage.send(pc, "이미 배운 마법입니다.");
            return;
        }
        commitLearning(pc, item, skillId, skillName, levels, kind.sound);
    }

    /** Equivalent to equalsIgnoreCase(prefix + name + ")"), without building it. */
    static boolean matches(String book, String prefix, String name) {
        if (book == null || prefix == null || name == null) return false;
        int prefixLength=prefix.length(), nameLength=name.length();
        return book.length()==prefixLength+nameLength+1
            && book.charAt(book.length()-1)==')'
            && book.regionMatches(true, 0, prefix, 0, prefixLength)
            && book.regionMatches(true, prefixLength, name, 0, nameLength);
    }
}
