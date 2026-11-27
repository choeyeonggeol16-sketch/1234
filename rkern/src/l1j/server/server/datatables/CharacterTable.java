package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.inventory.warehouse.ElfWarehouse;
import l1j.server.server.model.inventory.warehouse.PrivateWarehouse;
import l1j.server.server.model.inventory.warehouse.SpecialWarehouse;
import l1j.server.server.model.inventory.warehouse.SupplementaryService;
import l1j.server.server.model.inventory.warehouse.WarehouseManager;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CharacterStorage;
import l1j.server.server.storage.mysql.MySqlCharacterStorage;
import l1j.server.server.templates.L1CharName;

public class CharacterTable {
	private CharacterStorage _charStorage;

	private static final Logger _log = Logger.getLogger(CharacterTable.class.getName());
	private static final class Holder {
		private static final CharacterTable INSTANCE = new CharacterTable();
	}
	private final Object nameLock = new Object();
	private Names names = new Names();
	private long nameRevision;

	// Most folded keys have one row. A list is needed only for legacy collisions.
	private static final class Names {
		final TIntObjectMap<L1CharName> ids = new TIntObjectHashMap<>();
		final TreeMap<String, Object> folded = new TreeMap<>(
				Comparator.comparingInt(String::length).thenComparing(String.CASE_INSENSITIVE_ORDER));

		void add(L1CharName value) {
			final L1CharName byId = ids.get(value.getId());
			if (byId != null) remove(byId);
			ids.put(value.getId(), value);
			final String key = value.getName();
			final Object previous = folded.get(key);
			if (previous == null) folded.put(key, value);
			else if (previous instanceof L1CharName first) {
				final ArrayList<L1CharName> group = new ArrayList<>(2);
				group.add(first);
				group.add(value);
				folded.put(key, group);
			} else group(previous).add(value);
		}

		void remove(L1CharName value) {
			final String key = value.getName();
			ids.remove(value.getId());
			final Object group = folded.get(key);
			if (group instanceof L1CharName) folded.remove(key);
			else {
				final var rows = group(group);
				rows.remove(value);
				folded.remove(key);
				if (rows.size() == 1) folded.put(rows.getFirst().getName(), rows.getFirst());
				else if (!rows.isEmpty()) folded.put(rows.getFirst().getName(), rows);
			}
		}

		L1CharName exact(String key) {
			Object value = folded.get(key);
			if (value == null) return null;
			if (value instanceof L1CharName name) return name.getName().equals(key) ? name : null;
			L1CharName match = null;
			for (L1CharName row : group(value)) {
				if (!row.getName().equals(key)) continue;
				if (match != null) return null;
				match = row;
			}
			return match;
		}

		@SuppressWarnings("unchecked")
		private static ArrayList<L1CharName> group(Object value) {
			return (ArrayList<L1CharName>) value;
		}
	}

	private CharacterTable() { _charStorage = new MySqlCharacterStorage(); }

	public static CharacterTable getInstance() { return Holder.INSTANCE; }

	public void storeNewCharacter(L1PcInstance pc) throws Exception {
		pc.lockUserState();
		try {
			_charStorage.createCharacter(pc);
			registerCreatedCharacter(pc);
			_log.finest("storeNewCharacter");
		} finally { pc.unlockUserState(); }
	}

	/** Publish identity indexes only after the complete creation receipt commits. */
	public void registerCreatedCharacter(L1PcInstance pc) {
		publishName(pc.getId(), pc.getName());
	}

	private void publishName(int id, String name) {
		synchronized (nameLock) {
			names.add(new L1CharName(id, name));
			nameRevision++;
		}
	}

	public boolean renameCharacter(int id, String oldName, String newName) throws SQLException {
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement ps = con.prepareStatement(
					 "UPDATE characters AS src LEFT JOIN characters AS existing "
					 + "ON existing.char_name=? AND existing.objid<>src.objid "
					 + "SET src.char_name=? WHERE src.objid=? AND BINARY src.char_name=? AND existing.objid IS NULL")) {
			ps.setString(1, newName);
			ps.setString(2, newName);
			ps.setInt(3, id);
			ps.setString(4, oldName);
			if (ps.executeUpdate() != 1) return false;
		}
		publishName(id, newName);
		return true;
	}

	public void updateCharacterAccount(L1PcInstance pc) throws Exception {
		synchronized (pc) {
			_charStorage.updateAccountName(pc);
			_log.finest("updateCharacterAccount");
		}
	}

	public boolean storeCharacter(L1PcInstance pc) throws Exception {
        // Snapshot capture takes this same lock and later reads synchronized
        // getters. Never hold the PC monitor before waiting for this lock.
        pc.lockUserState();
        try {
            if (pc.rst != null && pc.getReturnStat() != 0) return true;
            return _charStorage.storeCharacter(pc);
        } finally { pc.unlockUserState(); }
	}

	public void deleteCharacter(String accountName, String charName) throws Exception {
        _charStorage.deleteCharacter(accountName, charName);
        synchronized (nameLock) {
            L1CharName cn = names.exact(charName);
            if (cn != null) names.remove(cn);
            nameRevision++;
        }
        _log.finest("deleteCharacter");
    }

	public boolean isContainNameList(String name) {
		return getCharName(name) != null;
	}

	public L1CharName getCharNameById(int id) {
		synchronized (nameLock) { return names.ids.get(id); }
	}

	public L1CharName getCharName(String name) {
		if (name == null || name.isEmpty()) return null;
		synchronized (nameLock) { return names.exact(name); }
	}

	/** Includes offline characters. A folded key shared by multiple IDs is ambiguous. */
	public L1CharName getUniqueCharNameIgnoringCase(String name) {
		if (name == null || name.isEmpty()) return null;
		synchronized (nameLock) {
			Object value = names.folded.get(name);
			return value instanceof L1CharName cn && name.equalsIgnoreCase(cn.getName()) ? cn : null;
		}
	}

	public L1PcInstance restoreCharacter(String charName) throws Exception {
		return _charStorage.loadCharacter(charName);
	}

    public L1PcInstance loadCharacter(String charName) throws Exception {
        return loadCharacter(charName, null);
    }

    public L1PcInstance loadCharacter(String charName, CharacterStorage.LoginVitals vitals) throws Exception {
        final L1PcInstance pc = _charStorage.loadCharacter(charName, vitals);
        if (pc == null) return null;
        try {
            final L1Map map = L1WorldMap.getInstance().getMap(pc.getMapId());
            if (map == null || !map.isInMap(pc.getX(), pc.getY())) {
                pc.setX(33087);
                pc.setY(33396);
                pc.setMap((short) 4);
            }
            _log.finest("loadCharacter: " + pc.getName());
            return pc;
        } catch (Exception e) {
            _log.log(Level.SEVERE, "loadCharacter error: " + charName, e);
            throw e; // Never publish a partially initialized character on failure.
        }
    }

	public static void clearOnlineStatus() {
		String sql = "UPDATE characters SET OnlineStatus = 0";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "clearOnlineStatus error", e);
		}
	}

    public static void updateOnlineStatus(L1PcInstance pc) {
        if (pc == null) return;
        final int charId = pc.getId();
        CacheD.getInstance().submitDbJobOrdered(charId, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "UPDATE characters SET OnlineStatus = 1, lastLoginTime = NOW() WHERE objid = ?")) {
                ps.setInt(1, charId);
                ps.executeUpdate();
            } catch (SQLException e) {
                _log.log(Level.SEVERE, "updateOnlineStatus error: " + charId, e);
                throw new IllegalStateException("Online status DB save failed: " + charId, e);
            }
        });
    }

    public void restoreInventory(L1PcInstance pc) {
        restoreInventory(pc, null);
    }

    public void restoreInventory(L1PcInstance pc, List<L1ItemInstance> loadedItems) {
        final long warehouseLease = pc.beginWarehouseCacheLease();
        final WarehouseManager manager = WarehouseManager.getInstance();
        try {
            if (loadedItems == null) pc.getInventory().loadItems();
            else pc.getInventory().loadItems(loadedItems);
            manager.bindPrivateWarehouse(pc.getAccountName(), warehouseLease).loadItems();
            manager.bindElfWarehouse(pc.getAccountName(), warehouseLease).loadItems();
            pc.getDwarfForPackageInventory().loadItems();
            manager.bindSupplementaryService(pc.getAccountName(), warehouseLease).loadItems();
            manager.bindSpecialWarehouse(pc.getName(), warehouseLease).loadItems();
        } finally {
            if (pc.isLogoutStarted())
                manager.releaseSessionWarehouses(pc.getAccountName(), pc.getName(), warehouseLease);
        }
    }

    public List<L1ItemInstance> restoreInventoryFromSnapshots(L1PcInstance pc, List<L1ItemInstance> loadedItems,
                                  List<List<L1ItemInstance>> warehouseItems) {
        if (loadedItems == null) {
            throw new IllegalStateException("Missing character inventory snapshot: " + pc.getName());
        }
        final ArrayList<L1ItemInstance> loadedEquipment = new ArrayList<>(16);
        if (warehouseItems == null || warehouseItems.size() != 5) {
            throw new IllegalStateException("Missing warehouse snapshots: " + pc.getName());
        }
        long warehouseLease = 0L;
        try {
            for (List<L1ItemInstance> items : warehouseItems) {
                if (items == null) throw new IllegalStateException("Incomplete warehouse snapshots: " + pc.getName());
            }
            warehouseLease = pc.beginWarehouseCacheLease();
            pc.getInventory().loadItems(loadedItems, loadedEquipment);
            WarehouseManager manager = WarehouseManager.getInstance();
            manager.bindPrivateWarehouse(pc.getAccountName(), warehouseLease).installLoadedSnapshot(warehouseItems.get(0));
            manager.bindElfWarehouse(pc.getAccountName(), warehouseLease).installLoadedSnapshot(warehouseItems.get(1));
            pc.getDwarfForPackageInventory().installLoadedSnapshot(warehouseItems.get(2));
            manager.bindSupplementaryService(pc.getAccountName(), warehouseLease).installLoadedSnapshot(warehouseItems.get(3));
            manager.bindSpecialWarehouse(pc.getName(), warehouseLease).installLoadedSnapshot(warehouseItems.get(4));
            return loadedEquipment;
        } catch (SQLException e) {
            throw new IllegalStateException("Warehouse restore failed: " + pc.getName(), e);
        } finally {
            if (warehouseLease != 0L && pc.isLogoutStarted())
                WarehouseManager.getInstance().releaseSessionWarehouses(
                        pc.getAccountName(), pc.getName(), warehouseLease);
        }
    }

    public void loadAllCharName() {
        final String sql = "SELECT objid, char_name FROM characters";
        final long revision;
        synchronized (nameLock) { revision = nameRevision; }
        final Names loaded = new Names();
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("objid");
                String name = rs.getString("char_name");
                if (id <= 0 || name == null || name.isEmpty()
                        || loaded.exact(name) != null || loaded.ids.containsKey(id))
                    throw new SQLException("Invalid or duplicate character name/ID in DB");
                loaded.add(new L1CharName(id, name));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Character name index load failed", e);
        }
        synchronized (nameLock) {
            if (nameRevision != revision)
                throw new IllegalStateException("Character names changed during reload");
            names = loaded;
            nameRevision++;
        }
        _log.config("캐릭터 이름 및 ID 목록 " + loaded.ids.size() + "건 로드 완료");
    }

	public int PcLevelInDB(int pcid) {
		int result = 0;
		String sql = "SELECT level FROM characters WHERE objid = ?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, pcid);
			
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					result = rs.getInt(1);
				}
			}
		} catch (SQLException e) {
			_log.warning("could not check existing charname: " + e.getMessage());
		}
		return result;
	}

	public L1CharName[] getCharNameList() {
		synchronized (nameLock) { return names.ids.valueCollection().toArray(L1CharName[]::new); }
	}

	public void updateLoc(int castleid, int a, int b, int c, int d, int f) {
		int[] loc = L1CastleLocation.getGetBackLoc(castleid);
		String sql = "UPDATE characters SET LocX=?, LocY=?, MapID=? WHERE OnlineStatus=0 AND (MapID=? OR MapID=? OR MapID=? OR MapID=? OR MapID=?)";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setInt(1, loc[0]);
			pstm.setInt(2, loc[1]);
			pstm.setInt(3, loc[2]);
			pstm.setInt(4, a);
			pstm.setInt(5, b);
			pstm.setInt(6, c);
			pstm.setInt(7, d);
			pstm.setInt(8, f);
			pstm.executeUpdate();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "updateLoc error", e);
		}
	}

	/**
	 * [SQL 최적화] 기존 2번 왕복하던 쿼리를 서브쿼리 활용 단일 쿼리로 통합
	 */
	public ArrayList<String> CharacterClanCheck(String charName) {
		ArrayList<String> clanIdList = new ArrayList<>();
		String sql = "SELECT clanid FROM characters WHERE account_name = (SELECT account_name FROM characters WHERE char_name = ?)";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, charName);
			
			try (ResultSet rs = pstm.executeQuery()) {
				while (rs.next()) {
					clanIdList.add(rs.getString("clanid"));
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "CharacterClanCheck error", e);
		}

		return clanIdList;
	}

    private static final int ACCOUNT_CHECK_BATCH_SIZE = 64;

    private record AccountCheckRow(String login, String ip) {}

    public void CharacterAccountCheck(L1PcInstance pc, String charName) {
        checkAccounts(pc, charName, true);
    }

    public void CharacterAccountCheck1(L1PcInstance pc, String charName) {
        checkAccounts(pc, charName, false);
    }

    private void checkAccounts(L1PcInstance pc, String charName, boolean sameIp) {
        final String sql = sameIp
                ? "SELECT login FROM accounts WHERE ip=(SELECT ip FROM accounts WHERE login="
                        + "(SELECT account_name FROM characters WHERE char_name=?))"
                : "SELECT login,ip FROM accounts WHERE login=(SELECT account_name FROM characters WHERE char_name=?)";
        final String separator = (sameIp ? "" : "\\aD") + "-------------------------------------------------";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, charName);
            try (ResultSet rows = ps.executeQuery()) {
                SC_SystemMessage.send(pc, separator);
                final ArrayList<AccountCheckRow> batch = new ArrayList<>(ACCOUNT_CHECK_BATCH_SIZE);
                while (rows.next()) {
                    batch.add(new AccountCheckRow(rows.getString("login"), sameIp ? null : rows.getString("ip")));
                    if (batch.size() == ACCOUNT_CHECK_BATCH_SIZE) {
                        sendAccountBatch(pc, con, batch, sameIp);
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) sendAccountBatch(pc, con, batch, sameIp);
                SC_SystemMessage.send(pc, separator);
            }
        } catch (SQLException failure) {
            _log.log(Level.SEVERE, "Character account lookup failed", failure);
        }
    }

    private void sendAccountBatch(L1PcInstance pc, Connection con, ArrayList<AccountCheckRow> batch,
                                  boolean sameIp) throws SQLException {
        // Bind each original login as before. Net uses different charsets for
        // accounts.login and characters.account_name; a direct join can lose
        // the character account index. The ordinal also retains empty accounts.
        final StringBuilder sql = new StringBuilder(batch.size() * 130);
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) sql.append(" UNION ALL ");
            sql.append("SELECT ").append(i).append(" AS lookup_order, objid, char_name, level, ")
                    .append("highlevel, clanname, onlinestatus FROM characters WHERE account_name=?");
        }
        sql.append(" ORDER BY lookup_order, char_name, objid");
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < batch.size(); i++) ps.setString(i + 1, batch.get(i).login());
            try (ResultSet rows = ps.executeQuery()) {
                boolean hasRow = rows.next();
                for (int i = 0; i < batch.size(); i++) {
                    final AccountCheckRow account = batch.get(i);
                    SC_SystemMessage.send(pc, sameIp
                            ? "\\fYAccounts : " + account.login()
                            : "\\aH계정: " + account.login() + ", IP: " + account.ip());
                    while (hasRow && rows.getInt("lookup_order") == i) {
                        final String onlineStatus = rows.getInt("onlinestatus") == 0 ? "" : "(접속중)";
                        SC_SystemMessage.send(pc, "* " + rows.getString("char_name") + " (Lv:"
                                + rows.getInt("level") + ") (HLv:" + rows.getInt("highlevel")
                                + ") (혈맹:" + rows.getString("clanname") + ") "
                                + (sameIp ? "\\fY" : "\\aG") + onlineStatus);
                        hasRow = rows.next();
                    }
                }
            }
        }
    }
}
