package l1j.server.server.model.world.instance.dungeon.time.loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.world.instance.dungeon.time.progress.AccountTimeProgress;
import l1j.server.server.model.world.instance.dungeon.time.progress.CharacterTimeProgress;
import l1j.server.server.model.world.instance.dungeon.time.progress.DungeonTimeProgress;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.world.index.StampIndex;
import l1j.server.server.storage.cached.CTransaction;

public class DungeonTimeProgressLoader {
    private static final ReentrantReadWriteLock RESET = new ReentrantReadWriteLock(true);
    private static volatile long generation;
    private static long appliedBoundary = Long.MIN_VALUE;

    public static long generation() { return generation; }

    public static void load(L1PcInstance pc) {
        applySnapshot(pc, readSnapshot(pc.getId(), pc.getAccountName()));
    }

    public static boolean resetAll(Calendar boundary) {
        RESET.writeLock().lock();
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            CTransaction.requireTransactionalTables(con, "tb_dungeon_time_account_information",
                    "tb_dungeon_time_char_information", "tb_index_stamp");
            con.setAutoCommit(false);
            try {
                long committed = resetIfDue(con, new Timestamp(Math.floorDiv(boundary.getTimeInMillis(), 1000L) * 1000L));
                con.commit();
                if (committed <= appliedBoundary) return false;
                appliedBoundary = committed;
                generation++;
                return true;
            } catch (SQLException | RuntimeException error) {
                try {
                    con.rollback();
                } catch (SQLException rollback) {
                    error.addSuppressed(rollback);
                    try { DatabaseFactory.discardConnection(con); }
                    catch (SQLException discard) { error.addSuppressed(discard); }
                }
                throw error;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Dungeon reset transaction failed", failure);
        } finally {
            RESET.writeLock().unlock();
        }
    }

    private static long resetIfDue(Connection con, Timestamp boundary) throws SQLException {
        final int stamp = StampIndex.SIDX_DUNGEON_TIME_TRUNCATE.to_int();
        try (PreparedStatement insert = con.prepareStatement(
                "INSERT INTO tb_index_stamp (idx) VALUES (?) ON DUPLICATE KEY UPDATE idx=idx")) {
            insert.setInt(1, stamp);
            insert.executeUpdate();
        }
        try (PreparedStatement lock = con.prepareStatement("SELECT stamp FROM tb_index_stamp WHERE idx=? FOR UPDATE")) {
            lock.setInt(1, stamp);
            try (ResultSet rows = lock.executeQuery()) {
                if (!rows.next()) throw new SQLException("Missing dungeon reset stamp");
                Timestamp last = rows.getTimestamp(1);
                if (last != null && !last.before(boundary)) return last.getTime();
            }
        }
        try (var statement = con.createStatement()) {
            statement.executeUpdate("DELETE FROM tb_dungeon_time_account_information");
            statement.executeUpdate("DELETE FROM tb_dungeon_time_char_information");
        }
        try (PreparedStatement update = con.prepareStatement("UPDATE tb_index_stamp SET stamp=? WHERE idx=?")) {
            update.setTimestamp(1, boundary);
            update.setInt(2, stamp);
            update.executeUpdate();
        }
        return boundary.getTime();
    }

    private static void submit(int owner, long expectedGeneration, Runnable write) {
        CacheD.getInstance().submitDbJobOrdered(owner, () -> {
            RESET.readLock().lock();
            try {
                if (generation == expectedGeneration) write.run();
            } finally {
                RESET.readLock().unlock();
            }
        });
    }

    public static List<DungeonTimeProgress<?>> readSnapshot(int characterId, String accountName) {
        ArrayList<DungeonTimeProgress<?>> rows = new ArrayList<>();
        RESET.readLock().lock();
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "select * from tb_dungeon_time_char_information where owner_info=?")) {
                ps.setInt(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows.add(CharacterTimeProgress.newInstance(rs));
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "select * from tb_dungeon_time_account_information where owner_info=?")) {
                ps.setString(1, accountName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) rows.add(AccountTimeProgress.newInstance(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Dungeon DB read failed: " + characterId, e);
        } finally {
            RESET.readLock().unlock();
        }
        return rows;
    }

    public static void applySnapshot(L1PcInstance pc, List<DungeonTimeProgress<?>> rows) {
        if (rows == null) return;
        for (int i = 0, n = rows.size(); i < n; i++) {
            final DungeonTimeProgress<?> progress = rows.get(i);
            if (progress instanceof CharacterTimeProgress) {
                pc.put_dungeon_progress(progress.get_timer_id(), (CharacterTimeProgress) progress);
            } else if (progress instanceof AccountTimeProgress) {
                pc.put_dungeon_progress(progress.get_timer_id(), (AccountTimeProgress) progress);
            }
        }
    }

    public static void delete(L1PcInstance pc) {
        if (pc == null) return;
        final int charId = pc.getId();
        final String account = pc.getAccountName();
        final long epoch = generation;
        submit(charId, epoch, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "delete from tb_dungeon_time_char_information where owner_info=?")) {
                ps.setInt(1, charId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Dungeon char delete failed: " + charId, e);
            }
        });
        submit(CacheD.accountOrderKey(account), epoch, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "delete from tb_dungeon_time_account_information where owner_info=?")) {
                ps.setString(1, account);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Dungeon account delete failed: " + account, e);
            }
        });
    }

    public static void delete(L1PcInstance pc, int timerId, boolean isAccount) {
        if (pc == null) return;
        final int charId = pc.getId();
        final String account = pc.getAccountName();
        submit(isAccount ? CacheD.accountOrderKey(account) : charId, generation, () -> {
            String sql = isAccount
                    ? "delete from tb_dungeon_time_account_information where owner_info=? and timer_id=?"
                    : "delete from tb_dungeon_time_char_information where owner_info=? and timer_id=?";
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                if (isAccount) ps.setString(1, account);
                else ps.setInt(1, charId);
                ps.setInt(2, timerId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Dungeon timer delete failed: " + timerId, e);
            }
        });
    }

    public static void update(L1PcInstance pc) {
        if (pc == null) return;
        final long epoch = generation;
        final Collection<DungeonTimeProgress<?>> chars = pc.get_character_progresses();
        final Collection<DungeonTimeProgress<?>> accounts = pc.get_account_progresses();
        if (chars != null && !chars.isEmpty()) {
            final int n = chars.size();
            final int[] ids = new int[n], remain = new int[n];
            int i = 0;
            for (DungeonTimeProgress<?> pr : chars) {
                if (pr.generation() != epoch) return;
                ids[i] = pr.get_timer_id();
                remain[i++] = pr.get_remain_seconds();
            }
            submitCharacterProgress(pc.getId(), epoch, ids, remain);
        }
        if (accounts != null && !accounts.isEmpty()) {
            final int n = accounts.size();
            final int[] ids = new int[n], remain = new int[n];
            int i = 0;
            for (DungeonTimeProgress<?> pr : accounts) {
                if (pr.generation() != epoch) return;
                ids[i] = pr.get_timer_id();
                remain[i++] = pr.get_remain_seconds();
            }
            submitAccountProgress(pc.getAccountName(), epoch, ids, remain);
        }
    }

    private static void submitCharacterProgress(int charId, long epoch, int[] ids, int[] remain) {
        submit(charId, epoch, () -> {
            final String sql = "insert into tb_dungeon_time_char_information set owner_info=?, timer_id=?, remain_seconds=? "
                    + "on duplicate key update remain_seconds=?";
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < ids.length; i++) {
                    ps.setInt(1, charId);
                    ps.setInt(2, ids[i]);
                    ps.setInt(3, remain[i]);
                    ps.setInt(4, remain[i]);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                throw new IllegalStateException("Dungeon char save failed: " + charId, e);
            }
        });
    }

    private static void submitAccountProgress(String account, long epoch, int[] ids, int[] remain) {
        submit(CacheD.accountOrderKey(account), epoch, () -> {
            final String sql = "insert into tb_dungeon_time_account_information set owner_info=?, timer_id=?, remain_seconds=? "
                    + "on duplicate key update remain_seconds=?";
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                for (int i = 0; i < ids.length; i++) {
                    ps.setString(1, account);
                    ps.setInt(2, ids[i]);
                    ps.setInt(3, remain[i]);
                    ps.setInt(4, remain[i]);
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                throw new IllegalStateException("Dungeon account save failed: " + account, e);
            }
        });
    }

    public static void update(DungeonTimeProgress<?> progress) {
        if (progress instanceof CharacterTimeProgress)
            update((CharacterTimeProgress) progress);
        else if (progress instanceof AccountTimeProgress)
            update((AccountTimeProgress) progress);
    }

    public static void update(CharacterTimeProgress progress) {
        if (progress == null) return;
        submitCharacterProgress(progress.get_owner_info(), progress.generation(),
                new int[]{progress.get_timer_id()}, new int[]{progress.get_remain_seconds()});
    }

    public static void update(AccountTimeProgress progress) {
        if (progress == null) return;
        submitAccountProgress(progress.get_owner_info(), progress.generation(),
                new int[]{progress.get_timer_id()}, new int[]{progress.get_remain_seconds()});
    }
}
