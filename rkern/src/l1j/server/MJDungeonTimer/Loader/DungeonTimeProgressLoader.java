package l1j.server.MJDungeonTimer.Loader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import l1j.server.DatabaseFactory;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import l1j.server.MJDungeonTimer.Progress.AccountTimeProgress;
import l1j.server.MJDungeonTimer.Progress.CharacterTimeProgress;
import l1j.server.MJDungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.BatchHandler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.storage.CacheD;

public class DungeonTimeProgressLoader{
	public static void load(L1PcInstance pc){
		Selector.exec("select * from tb_dungeon_time_char_information where owner_info=?", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, pc.getId());
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					CharacterTimeProgress progress = CharacterTimeProgress.newInstance(rs);
					pc.put_dungeon_progress(progress.get_timer_id(), progress);
				}
			}
		});
		Selector.exec("select * from tb_dungeon_time_account_information where owner_info=?", new SelectorHandler(){
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setString(1, pc.getAccountName());
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				while(rs.next()){
					AccountTimeProgress progress = AccountTimeProgress.newInstance(rs);
					pc.put_dungeon_progress(progress.get_timer_id(), progress);
				}
			}
		});
	}
	
    public static List<DungeonTimeProgress<?>> readSnapshot(int characterId, String accountName) {
        ArrayList<DungeonTimeProgress<?>> rows = new ArrayList<>();
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
        CacheD.getInstance().submitDbJobOrdered(charId, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "delete from tb_dungeon_time_char_information where owner_info=?")) {
                ps.setInt(1, charId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Dungeon char delete failed: " + charId, e);
            }
        });
        CacheD.getInstance().submitDbJobOrdered(account, () -> {
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
        CacheD.getInstance().submitDbJobOrdered(
                isAccount ? CacheD.accountOrderKey(account) : charId, () -> {
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
        final Collection<DungeonTimeProgress<?>> chars = pc.get_character_progresses();
        final Collection<DungeonTimeProgress<?>> accounts = pc.get_account_progresses();
        if (chars != null && !chars.isEmpty()) {
            final int n = chars.size();
            final int[] ids = new int[n], remain = new int[n];
            int i = 0;
            for (DungeonTimeProgress<?> pr : chars) {
                ids[i] = pr.get_timer_id();
                remain[i++] = pr.get_remain_seconds();
            }
            submitCharacterProgress(pc.getId(), ids, remain);
        }
        if (accounts != null && !accounts.isEmpty()) {
            final int n = accounts.size();
            final int[] ids = new int[n], remain = new int[n];
            int i = 0;
            for (DungeonTimeProgress<?> pr : accounts) {
                ids[i] = pr.get_timer_id();
                remain[i++] = pr.get_remain_seconds();
            }
            submitAccountProgress(pc.getAccountName(), ids, remain);
        }
    }

    private static void submitCharacterProgress(int charId, int[] ids, int[] remain) {
        CacheD.getInstance().submitDbJobOrdered(charId, () -> {
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

    private static void submitAccountProgress(String account, int[] ids, int[] remain) {
        CacheD.getInstance().submitDbJobOrdered(account, () -> {
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
        submitCharacterProgress(progress.get_owner_info(),
                new int[]{progress.get_timer_id()}, new int[]{progress.get_remain_seconds()});
    }

    public static void update(AccountTimeProgress progress) {
        if (progress == null) return;
        submitAccountProgress(progress.get_owner_info(),
                new int[]{progress.get_timer_id()}, new int[]{progress.get_remain_seconds()});
    }
}
