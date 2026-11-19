package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.rowset.CachedRowSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Warehouse.ElfWarehouse;
import l1j.server.server.model.Warehouse.PrivateWarehouse;
import l1j.server.server.model.Warehouse.SpecialWarehouse;
import l1j.server.server.model.Warehouse.SupplementaryService;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CharacterStorage;
import l1j.server.server.storage.mysql.MySqlCharacterStorage;
import l1j.server.server.templates.L1CharName;

public class CharacterTable {
	private CharacterStorage _charStorage;

	private static CharacterTable _instance;

	private static final Logger _log = Logger.getLogger(CharacterTable.class.getName());

	private final Map<String, L1CharName> _charNameList = new ConcurrentHashMap<>();
	
	private final TIntObjectMap<L1CharName> _charIdMap = new TIntObjectHashMap<>();

	private CharacterTable() {
		_charStorage = new MySqlCharacterStorage();
	}

	public static CharacterTable getInstance() {
		if (_instance == null) {
			_instance = new CharacterTable();
		}
		return _instance;
	}

	public void storeNewCharacter(L1PcInstance pc) throws Exception {
        pc.lockUserState();
        try {
			_charStorage.createCharacter(pc);
			if (!_charNameList.containsKey(pc.getName())) {
				L1CharName cn = new L1CharName();
				cn.setName(pc.getName());
				cn.setId(pc.getId());
				_charNameList.put(pc.getName(), cn);
				synchronized (_charIdMap) {
					_charIdMap.put(pc.getId(), cn);
				}
			}
			_log.finest("storeNewCharacter");
        } finally { pc.unlockUserState(); }
	}

    /** Publish identity indexes only after the complete creation receipt commits. */
    public void registerCreatedCharacter(L1PcInstance pc) {
        L1CharName cn = new L1CharName(); cn.setName(pc.getName()); cn.setId(pc.getId());
        _charNameList.put(pc.getName(), cn);
        synchronized (_charIdMap) { _charIdMap.put(pc.getId(), cn); }
    }

	public void updateCharacterAccount(L1PcInstance pc) throws Exception {
		synchronized (pc) {
			_charStorage.updateAccountName(pc);
			_log.finest("updateCharacterAccount");
		}
	}

	public void storeCharacter(L1PcInstance pc) throws Exception {
        // Snapshot capture takes this same lock and later reads synchronized
        // getters. Never hold the PC monitor before waiting for this lock.
        pc.lockUserState();
        try {
            if (pc.rst != null && pc.getReturnStat() != 0) return;
			_charStorage.storeCharacter(pc);
			String name = pc.getName();
			if (!_charNameList.containsKey(name)) {
				L1CharName cn = new L1CharName();
				cn.setName(name);
				cn.setId(pc.getId());
				_charNameList.put(name, cn);
				synchronized (_charIdMap) {
					_charIdMap.put(pc.getId(), cn);
				}
			}
			_log.finest("storeCharacter: " + pc.getName());
        } finally { pc.unlockUserState(); }
	}

	public void deleteCharacter(String accountName, String charName) throws Exception {
        _charStorage.deleteCharacter(accountName, charName);
        L1CharName cn = _charNameList.remove(charName);
        if (cn != null) {
            synchronized (_charIdMap) {
                _charIdMap.remove(cn.getId());
            }
        }
        _log.finest("deleteCharacter");
    }

	public boolean isContainNameList(String name) {
		return _charNameList.containsKey(name);
	}

	public L1CharName getCharNameById(int id) {
		synchronized (_charIdMap) {
			return _charIdMap.get(id);
		}
	}
	
	public L1CharName getCharName(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		return _charNameList.get(name);
	}

	public L1PcInstance restoreCharacter(String charName) throws Exception {
		return _charStorage.loadCharacter(charName);
	}

    public L1PcInstance loadCharacter(String charName) throws Exception {
        final L1PcInstance pc = restoreCharacter(charName);
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
        if (loadedItems == null) pc.getInventory().loadItems();
        else pc.getInventory().loadItems(loadedItems);
		PrivateWarehouse warehouse = WarehouseManager.getInstance().getPrivateWarehouse(pc.getAccountName());
		warehouse.loadItems();
		ElfWarehouse elfwarehouse = WarehouseManager.getInstance().getElfWarehouse(pc.getAccountName());
		elfwarehouse.loadItems();
		pc.getDwarfForPackageInventory().loadItems();
		SupplementaryService supplementaryservice = WarehouseManager.getInstance().getSupplementaryService(pc.getAccountName());
		supplementaryservice.loadItems();
		SpecialWarehouse specialwarehose = WarehouseManager.getInstance().getSpecialWarehouse(pc.getName());
		specialwarehose.loadItems();
	}

    public void restoreInventory(L1PcInstance pc, List<L1ItemInstance> loadedItems,
                                 CachedRowSet[] warehouseRows) {
        if (warehouseRows == null || warehouseRows.length != 5) {
            throw new IllegalStateException("Missing warehouse snapshots: " + pc.getName());
        }
        try {
            // Fail before applying any inventory if a detached SQL snapshot is missing.
            for (CachedRowSet row : warehouseRows) {
                if (row == null) throw new IllegalStateException("Incomplete warehouse snapshots: " + pc.getName());
            }
            if (loadedItems == null) pc.getInventory().loadItems();
            else pc.getInventory().loadItems(loadedItems);
            WarehouseManager manager = WarehouseManager.getInstance();
            manager.getPrivateWarehouse(pc.getAccountName()).loadItemsFromSnapshot(warehouseRows[0]);
            manager.getElfWarehouse(pc.getAccountName()).loadItemsFromSnapshot(warehouseRows[1]);
            pc.getDwarfForPackageInventory().loadItemsFromSnapshot(warehouseRows[2]);
            manager.getSupplementaryService(pc.getAccountName()).loadItemsFromSnapshot(warehouseRows[3]);
            manager.getSpecialWarehouse(pc.getName()).loadItemsFromSnapshot(warehouseRows[4]);
        } catch (SQLException e) {
            throw new IllegalStateException("Warehouse restore failed: " + pc.getName(), e);
        } finally {
            for (CachedRowSet row : warehouseRows) {
                if (row != null) try { row.close(); } catch (SQLException ignored) {}
            }
        }
    }

    public void loadAllCharName() {
        final String sql = "SELECT objid, char_name FROM characters";
        // Construct both indexes outside the published maps. A JDBC failure
        // halfway through a result set must not destroy the known roster.
        final Map<String, L1CharName> byName = new java.util.HashMap<>();
        final TIntObjectMap<L1CharName> byId = new TIntObjectHashMap<>();
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                final int id = rs.getInt("objid");
                final String name = rs.getString("char_name");
                if (id <= 0 || name == null || name.isEmpty()
                        || byName.containsKey(name) || byId.containsKey(id)) {
                    throw new SQLException("Invalid or duplicate character name/ID in DB");
                }
                final L1CharName cn = new L1CharName();
                cn.setName(name);
                cn.setId(id);
                byName.put(name, cn);
                byId.put(id, cn);
            }
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "loadAllCharName failed; refusing incomplete index", e);
            throw new IllegalStateException("Character name index load failed", e);
        }
        synchronized (_charIdMap) {
            _charNameList.clear();
            _charNameList.putAll(byName);
            _charIdMap.clear();
            byId.forEachEntry((id, cn) -> { _charIdMap.put(id, cn); return true; });
        }
        _log.config("캐릭터 이름 및 ID 목록 " + byName.size() + "건 로드 완료");
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
		return _charNameList.values().toArray(new L1CharName[0]);
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

	public void CharacterAccountCheck(L1PcInstance pc, String charName) {
		String sql1 = "SELECT login, password FROM accounts WHERE ip = (SELECT ip FROM accounts WHERE login = (SELECT account_name FROM characters WHERE char_name = ?))";
		String sql2 = "SELECT char_name, level, highlevel, clanname, onlinestatus FROM characters WHERE account_name = ?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm1 = con.prepareStatement(sql1)) {
			pstm1.setString(1, charName);

			try (ResultSet loginRs = pstm1.executeQuery()) {
				SC_SystemMessage.send(pc, "-------------------------------------------------");
				while (loginRs.next()) {
					String login = loginRs.getString("login");
					SC_SystemMessage.send(pc, "\\fYAccounts : " + login + ", PassWord : " + loginRs.getString("password"));
					
					try (PreparedStatement pstm2 = con.prepareStatement(sql2)) {
						pstm2.setString(1, login);
						try (ResultSet characterRs = pstm2.executeQuery()) {
							while (characterRs.next()) {
								String onlineStatus = characterRs.getInt("onlinestatus") == 0 ? "" : "(접속중)";
								SC_SystemMessage.send(pc, "* " + characterRs.getString("char_name") + " (Lv:"
										+ characterRs.getInt("level") + ") (HLv:" + characterRs.getInt("highlevel") + ") " + "(혈맹:"
										+ characterRs.getString("clanname") + ") " + "\\fY" + onlineStatus);
							}
						}
					}
				}
				SC_SystemMessage.send(pc, "-------------------------------------------------");
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	public void CharacterAccountCheck1(L1PcInstance pc, String charName) {
		String sql1 = "SELECT login, password, ip FROM accounts WHERE login = (SELECT account_name FROM characters WHERE char_name = ?)";
		String sql2 = "SELECT char_name, level, highlevel, clanname, onlinestatus FROM characters WHERE account_name = ?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm1 = con.prepareStatement(sql1)) {
			pstm1.setString(1, charName);

			try (ResultSet loginRs = pstm1.executeQuery()) {
				SC_SystemMessage.send(pc, "\\aD-------------------------------------------------");
				while (loginRs.next()) {
					String login = loginRs.getString("login");
					SC_SystemMessage.send(pc, "\\aH계정: " + login + ", 비번: " + loginRs.getString("password") + ", IP: " + loginRs.getString("ip"));
					
					try (PreparedStatement pstm2 = con.prepareStatement(sql2)) {
						pstm2.setString(1, login);
						try (ResultSet characterRs = pstm2.executeQuery()) {
							while (characterRs.next()) {
								String onlineStatus = characterRs.getInt("onlinestatus") == 0 ? "" : "(접속중)";
								SC_SystemMessage.send(pc, "* " + characterRs.getString("char_name") + " (Lv:" + characterRs.getInt("level") + ") (HLv:" + characterRs.getInt("highlevel") + ") " + "(혈맹:" + characterRs.getString("clanname") + ") " + "\\aG" + onlineStatus);
							}
						}
					}
				}
				SC_SystemMessage.send(pc, "\\aD-------------------------------------------------");
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
}
