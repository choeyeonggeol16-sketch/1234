package l1j.server.server.model.item.function;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;

/** Quest-bookmark capacity extension, committed with the input token. */
final class QuestProgressItems {
    private static final Logger LOG = Logger.getLogger(QuestProgressItems.class.getName());
    private QuestProgressItems() {}

    static ItemActionResult handleMarkReward(ItemActionContext use) {
        L1PcInstance pc = use.player();
        L1ItemInstance item = use.item();
        if (pc == null || item == null || item.getItemId() != 700022
                || pc.getInventory().getItem(item.getId()) != item) return ItemActionResult.COMPLETE;
        final int before = pc.getMark_count();
        if (before >= 100) {
            SC_ServerMessage.send(pc, 2930);
            return ItemActionResult.COMPLETE;
        }
        final int next = before + 10;
        final int characterId = pc.getId();
        try {
            // Persist any previously queued character/item state before the
            // preimage-checked receipt. No JDBC executes on the CIO owner.
            CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc));
            if (item.getRecordingColumns() != 0)
                pc.getInventory().saveItem(item, CUserInventory.COL_SAVE_ALL);
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
                if (!tx.debit(item, 1)) return ItemActionResult.COMPLETE;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (PreparedStatement update = con.prepareStatement(
                            "UPDATE characters SET Mark_Count=? WHERE objid=? AND Mark_Count=?")) {
                        update.setInt(1, next);
                        update.setInt(2, characterId);
                        update.setInt(3, before);
                        if (update.executeUpdate() != 1)
                            throw new SQLException("Bookmark capacity preimage changed: " + characterId);
                    }
                });
                tx.onFailure(() -> {
                    try {
                        SC_SystemMessage.send(pc, "책갈피 확장 저장에 실패했습니다.");
                    } catch (RuntimeException noticeFailure) {
                        LOG.log(Level.WARNING, "Bookmark expansion failure notice failed", noticeFailure);
                    }
                });
                tx.commitAfterInventoryRelease(() -> {
                    if (pc.getMark_count() != before)
                        throw new IllegalStateException("Bookmark capacity changed before receipt apply: " + characterId);
                    pc.setMark_count(next);
                    // The column and input debit have already committed; a later
                    // character snapshot or client packet is only follow-up work.
                    try {
                        pc.save();
                    } catch (RuntimeException saveFailure) {
                        LOG.log(Level.WARNING, "Committed bookmark expansion: snapshot admission failed", saveFailure);
                    }
                    try {
                        SC_PacketBox.send(pc, SC_PacketBox.BOOKMARK_SIZE_PLUS_10, next);
                    } catch (RuntimeException noticeFailure) {
                        LOG.log(Level.WARNING, "Committed bookmark expansion: client notice failed", noticeFailure);
                    }
                });
            }
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Bookmark expansion prepare failed: " + characterId, failure);
            try {
                SC_SystemMessage.send(pc, "책갈피 확장을 처리하지 못했습니다.");
            } catch (RuntimeException noticeFailure) {
                LOG.log(Level.WARNING, "Bookmark expansion prepare notice failed", noticeFailure);
            }
        }
        return ItemActionResult.COMPLETE;
    }
}
