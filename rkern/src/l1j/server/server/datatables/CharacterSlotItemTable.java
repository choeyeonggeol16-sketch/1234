package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.storage.CacheD;

public class CharacterSlotItemTable {

    private static final Logger _log = Logger.getLogger(CharacterSlotItemTable.class.getName());
    private static CharacterSlotItemTable _instance;
    private static final int[] EMPTY_SLOTS = new int[0];

    public static CharacterSlotItemTable getInstance() {
        if (_instance == null) {
            _instance = new CharacterSlotItemTable();
        }
        return _instance;
    }

    public int[] loadCharSlots(int charId) {
        final String sql = "SELECT slot_number, item_objid FROM character_slot_items "
                + "WHERE char_id = ? AND slot_number IN (0, 1) ORDER BY slot_number";
        int[] slots = EMPTY_SLOTS;
        int count = 0;

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql)) {

            pstm.setInt(1, charId);

            try (ResultSet rs = pstm.executeQuery()) {
                while (rs.next()) {
                    if (count == slots.length) {
                        slots = Arrays.copyOf(slots, count == 0 ? 16 : count << 1);
                    }
                    slots[count++] = rs.getInt("slot_number");
                    slots[count++] = rs.getInt("item_objid");
                }
            }
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "CharacterSlotItemTable loadCharSlots error: " + charId, e);
            throw new IllegalStateException("Character slot load failed: " + charId, e);
        }
        return count == slots.length ? slots : Arrays.copyOf(slots, count);
    }

    public void applyCharSlots(L1PcInstance pc, int[] slots) {
        if (pc == null || slots == null) return;
        for (int i = 0; i + 1 < slots.length; i += 2) {
            pc.addSlotItem(slots[i], slots[i + 1], false);
        }
    }

    public void selectCharSlot(L1PcInstance pc, int slotNum) {
        String sql = "SELECT * FROM character_slot_items WHERE char_id = ? AND slot_number = ?";

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql)) {

            pstm.setInt(1, pc.getId());
            pstm.setInt(2, slotNum);

            try (ResultSet rs = pstm.executeQuery()) {
                while (rs.next()) {
                    pc.addSlotItem(slotNum, rs.getInt("item_objid"), false);
                }
            }
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "CharacterSlotItemTable selectCharSlot error", e);
        }
    }

    public void updateCharSlotItems(L1PcInstance pc) {
        if (pc == null) return;
        final int charId = pc.getId();
        final List<Integer> slot0 = pc.getSlotItems(0);
        final List<Integer> slot1 = pc.getSlotItems(1);
        final int n0 = slot0 == null ? 0 : slot0.size();
        final int n1 = slot1 == null ? 0 : slot1.size();
        final int[] ids = new int[n0 + n1];
        for (int i = 0; i < n0; i++) ids[i] = slot0.get(i);
        for (int i = 0; i < n1; i++) ids[n0 + i] = slot1.get(i);

        CacheD.getInstance().submitDbJobRetryOrdered(charId, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection()) {
                con.setAutoCommit(false);
                try {
                    try (PreparedStatement delete = con.prepareStatement(
                            "DELETE FROM character_slot_items WHERE char_id = ?")) {
                        delete.setInt(1, charId);
                        delete.executeUpdate();
                    }
                    if (ids.length != 0) {
                        try (PreparedStatement insert = con.prepareStatement(
                                "INSERT INTO character_slot_items SET item_objid = ?, char_id = ?, slot_number = ?")) {
                            for (int i = 0; i < ids.length; i++) {
                                insert.setInt(1, ids[i]);
                                insert.setInt(2, charId);
                                insert.setInt(3, i < n0 ? 0 : 1);
                                insert.addBatch();
                            }
                            insert.executeBatch();
                        }
                    }
                    con.commit();
                } catch (SQLException | RuntimeException e) {
                    try { con.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                    throw e;
                }
            } catch (SQLException e) {
                _log.log(Level.SEVERE, "CharacterSlotItemTable updateCharSlotItems error: " + charId, e);
                throw new IllegalStateException("Character slots DB save failed: " + charId, e);
            }
        });
    }
}
