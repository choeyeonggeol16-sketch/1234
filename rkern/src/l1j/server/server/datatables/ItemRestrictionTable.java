package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import l1j.server.DatabaseFactory;

/** The persisted item-ID list for one inventory policy. */
final class ItemRestrictionTable {
    private final TIntSet itemIds = new TIntHashSet();
    private final Logger logger;
    private final String label;
    private final String selectSql;
    private final String insertSql;
    private final String deleteSql;

    ItemRestrictionTable(String table, String label, Logger logger) {
        this.logger = logger;
        this.label = label;
        selectSql = "SELECT item_id FROM " + table;
        insertSql = "INSERT INTO " + table + " SET item_id=?";
        deleteSql = "DELETE FROM " + table + " WHERE item_id=?";
        reload();
    }

    synchronized void reload() {
        final TIntSet replacement = new TIntHashSet();
        try (Connection connection = DatabaseFactory.getInstance().getConnection();
                PreparedStatement statement = connection.prepareStatement(selectSql);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) replacement.add(rows.getInt("item_id"));
        } catch (SQLException failure) {
            logger.log(Level.SEVERE, "loadIdList error", failure);
            return;
        }
        synchronized (itemIds) {
            itemIds.clear();
            itemIds.addAll(replacement);
        }
        logger.config(label + " 아이템 목록 " + replacement.size() + "건 로드 완료");
    }

    void storeId(int itemId) {
        update(itemId, true);
    }

    void deleteId(int itemId) {
        update(itemId, false);
    }

    private synchronized void update(int itemId, boolean add) {
        synchronized (itemIds) {
            if (itemIds.contains(itemId) == add) return;
        }

        try (Connection connection = DatabaseFactory.getInstance().getConnection();
                PreparedStatement statement = connection.prepareStatement(add ? insertSql : deleteSql)) {
            statement.setInt(1, itemId);
            statement.execute();
            synchronized (itemIds) {
                if (add) itemIds.add(itemId);
                else itemIds.remove(itemId);
            }
        } catch (SQLException failure) {
            String action = add ? "storeId" : "deleteId";
            logger.log(Level.SEVERE, action + " error [ItemID: " + itemId + "]", failure);
        }
    }

    int[] getIdList() {
        synchronized (itemIds) {
            return itemIds.toArray();
        }
    }

    boolean contains(int itemId) {
        synchronized (itemIds) {
            return itemIds.contains(itemId);
        }
    }
}
