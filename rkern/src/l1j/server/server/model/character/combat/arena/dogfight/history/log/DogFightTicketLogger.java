package l1j.server.server.model.character.combat.arena.dogfight.history.log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;

public final class DogFightTicketLogger {
    private static final Logger LOG = Logger.getLogger(DogFightTicketLogger.class.getName());
    private static final String SELL_SQL = "INSERT INTO dogfight_log_selling (action_date,character_object_id,character_name,item_id,item_name,count,price,total_price) VALUES (?,?,?,?,?,?,?,?)";
    private static final String BUY_SQL = "INSERT INTO dogfight_log_buying (action_date,character_object_id,character_name,item_id,item_name,count,price,total_price) VALUES (?,?,?,?,?,?,?,?)";

    private static final class Entry {
        final String date, name, itemName;
        final int charId, itemId, count, price, total;

        Entry(DogFightTicketLogInfo v) {
            date = v.get_action_date();
            charId = v.get_character_object_id();
            name = v.get_character_name();
            itemId = v.get_item_id();
            itemName = v.get_item_name();
            count = v.get_count();
            price = v.get_price();
            total = v.get_total_price();
        }
    }

    public static void do_update(ArrayList<DogFightTicketLogInfo> entries, boolean selling) {
        if (entries == null || entries.isEmpty()) return;
        Entry[] snapshot = new Entry[entries.size()];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = new Entry(entries.get(i));
        }
        final int characterId = snapshot[0].charId;
        CacheD.getInstance().submitDbJobOrdered(characterId, () -> store(snapshot, selling));
    }

    private static void store(Entry[] entries, boolean selling) {
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(selling ? SELL_SQL : BUY_SQL)) {
                for (Entry v : entries) {
                    ps.setString(1, v.date);
                    ps.setInt(2, v.charId);
                    ps.setString(3, v.name);
                    ps.setInt(4, v.itemId);
                    ps.setString(5, v.itemName);
                    ps.setInt(6, v.count);
                    ps.setInt(7, v.price);
                    ps.setInt(8, v.total);
                    ps.addBatch();
                }
                ps.executeBatch();
                con.commit();
            } catch (SQLException ex) {
                try { con.rollback(); } catch (SQLException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        } catch (SQLException ex) {
            LOG.log(Level.SEVERE, "Dogfight ticket audit save failed: character=" + entries[0].charId, ex);
            throw new IllegalStateException("Dogfight ticket audit save failed", ex);
        }
    }
}
