package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.function.Consumer;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.storage.cached.CTransaction;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ManagerInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.ClanWarehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.storage.CacheD;

public class ClanTable {
	private static final Logger _log = Logger.getLogger(ClanTable.class.getName());
	private static volatile ClanTable _instance;

	// [최적화] Trove4j Primitive Int Map 적용 (Integer 박싱 비용 소멸)
	private final TIntObjectMap<L1Clan> _clans = new TIntObjectHashMap<L1Clan>(64);
//	private final TIntObjectMap<L1Clan> _redknights = new TIntObjectHashMap<L1Clan>(8);
	
	public static final String CLAN_TUTORIAL_ANN = "자동탈퇴레벨: "+Config.신규혈맹보호레벨;
	public static final int CLAN_TUTORIAL_EMB = Config.신규혈맹엠블런;
	
	public static ClanTable getInstance() {
        ClanTable instance = _instance;
        if (instance != null) return instance;
        synchronized (ClanTable.class) {
            instance = _instance;
            if (instance == null) _instance = instance = new ClanTable();
        }
        return instance;
	}

	
	private ClanTable() {
		this(true);
	}

	// A reload candidate must never alter the live world before all SQL reads
	// (including clan warehouse snapshots) have completed successfully.
	private ClanTable(boolean publish) {
		loadClansAndMembers();
		if (publish) {
			// Startup owns warehouse initialization. A live reload must not
			// reinstall DB snapshots into already active CacheD warehouse owners:
			// a failed later warehouse SELECT would otherwise partially overwrite
			// old inventories even though the staged clan roster is discarded.
			loadClanWarehouses();
			publishClans();
		}
	}

	private void publishClans() {
		for (L1Clan clan : _clans.valueCollection()) {
			if (clan != null) L1World.getInstance().storeClan(clan);
		}
	}

	/**
	 * 혈맹 데이터와 캐릭터(혈맹원)조회를 통합하거나 효율적으로 처리하여 N+1 쿼리 부하 최소화
	 */
	private void loadClansAndMembers() {
		String clanSql = "SELECT * FROM clan_data ORDER BY clan_id";
		String memberSql = "SELECT char_name, ClanRank, level, notes, objid, Type, ClanID FROM characters WHERE ClanID > 0";

		try (Connection con = DatabaseFactory.getInstance().getConnection()) {
			
			//  혈맹 데이터 로드
			try (PreparedStatement pstm = con.prepareStatement(clanSql);
				 ResultSet rs = pstm.executeQuery()) {
				
				while (rs.next()) {
					L1Clan clan = new L1Clan();
					int clan_id = rs.getInt(1);
					int castle_id = rs.getInt(5);
					clan.setClanId(clan_id);
					clan.setClanName(rs.getString(2));
					clan.setLeaderId(rs.getInt(3));
					clan.setLeaderName(rs.getString(4));
					clan.setCastleId(castle_id);
					clan.setHouseId(rs.getInt(6));
					clan.setAlliance(rs.getInt(7));
					clan.setClanBirthDay(rs.getTimestamp(8));
					
					String botStr = rs.getString(9);
					clan.setBot(botStr != null && botStr.equalsIgnoreCase("true"));
					clan.setBotStyle(rs.getInt(10));
					clan.setBotLevel(rs.getInt(11));
					
					clan.setOnlineMaxUser(rs.getInt(12));
					clan.setAnnouncement(rs.getString(13));
					clan.setEmblemId(rs.getInt(14));
					clan.setEmblemStatus(rs.getInt(15));
					clan.setBless(rs.getInt("bless"));
					clan.setBlessCount(rs.getInt("bless_count"));
					clan.setBuffTime(rs.getInt("attack"), rs.getInt("defence"), rs.getInt("pvpattack"), rs.getInt("pvpdefence"));
					clan.setUnderDungeon(rs.getInt("under_dungeon"));
					clan.setRankTime(rs.getInt("ranktime"));
					clan.setRankDate(rs.getTimestamp("rankdate"));
					
					clan.setJoinSetting(rs.getInt("join_setting"));
					clan.setJoinType(rs.getInt("join_type"));
					clan.setJoinPassword(rs.getString("join_password"));
					clan.setWarPoint(rs.getInt("War_point"));
					
					clan.setEinhasadBlessBuff(rs.getInt("EinhasadBlessBuff"));
					clan.setBuffFirst(rs.getInt("Buff_List1"));
					clan.setBuffSecond(rs.getInt("Buff_List2"));
					clan.setBuffThird(rs.getInt("Buff_List3"));
					
					_clans.put(clan_id, clan);
				}
			}

			//  혈맹원 데이터 일괄 로드
			try (PreparedStatement pstm = con.prepareStatement(memberSql);
				 ResultSet rs = pstm.executeQuery()) {
				
				while (rs.next()) {
					int clanId = rs.getInt("ClanID");
					L1Clan clan = _clans.get(clanId);
					if (clan != null) {
						String name = rs.getString("char_name");
						int rank = rs.getInt("ClanRank");
						int level = rs.getInt("level");
						String notes = rs.getString("notes");
						int memberId = rs.getInt("objid");
						int type = rs.getInt("Type");
						clan.addLoadedClanMember(name, rank, level, notes, memberId, type);
					}
				}
			}

			_log.config("혈맹 데이터 " + _clans.size() + "건 로드 완료");

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ClanTable loadClansAndMembers Error", e);
            throw new IllegalStateException("Clan roster bootstrap incomplete", e);
		}
	}

	private void loadClanWarehouses() {
		for (L1Clan clan : _clans.valueCollection()) {
			if (clan != null) {
				ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(clan.getClanName());
				if (clanWarehouse != null) {
					clanWarehouse.loadItems();
				}
			}
		}
	}

	public L1Clan createTutorialClan(){ 
		L1ManagerInstance manager = L1ManagerInstance.getInstance();
		
		if(manager.getClanid() != 0){
			System.out.println("이미 생성된 신규 혈맹이 있습니다.");
			return null;
		}
		
		L1Clan clan = L1World.getInstance().findClan(Config.신규혈맹이름);
		if(clan != null){
			manager.setClanid(clan.getClanId());
			manager.setClanname(clan.getClanName());
			manager.setClanRank(L1Clan.군주);
			clan.addClanMember(manager.getName(), manager.getClanRank(), manager.getLevel(), "", manager.getId(), manager.getType(), manager.getOnlineStatus(), manager);
			return clan;
		}
		
		clan = new L1Clan();
		clan.setClanId(IdFactory.getInstance().nextId());
		clan.setClanName(Config.신규혈맹이름);
		clan.setLeaderId(manager.getId());
		clan.setLeaderName("메티스");
		clan.setCastleId(0);
		clan.setHouseId(0);
		clan.setAlliance(0);
		clan.setClanBirthDay(new Timestamp(System.currentTimeMillis()));
		clan.setAnnouncement(CLAN_TUTORIAL_ANN);
		clan.setEmblemId(CLAN_TUTORIAL_EMB);
		clan.setEmblemStatus(1);
		clan.setBless(0);
		clan.setBlessCount(0);
		clan.setBuffTime(0, 0, 0, 0);
		clan.setJoinSetting(1);
		clan.setJoinType(1);
		clan.setBot(false);
		
		String sql = "INSERT INTO clan_data SET clan_id=?, clan_name=?, leader_id=?, leader_name=?, hascastle=?, hashouse=?, alliance=?, clan_birthday=?, max_online_user=?, announcement=?, emblem_id=?, emblem_status=?,clan_exp=?,bless=?,bless_count=?,attack=?,defence=?,pvpattack=?,pvpdefence=?,join_setting=?,join_type=?,EinhasadBlessBuff=?,Buff_List1=?,Buff_List2=?,Buff_List3=?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, clan.getClanId());
			pstm.setString(2, clan.getClanName());
			pstm.setInt(3, clan.getLeaderId());
			pstm.setString(4, clan.getLeaderName());
			pstm.setInt(5, clan.getCastleId());
			pstm.setInt(6, clan.getHouseId());
			pstm.setInt(7, clan.getAlliance());
			pstm.setTimestamp(8, clan.getClanBirthDay());
			pstm.setInt(9, clan.getOnlineMaxUser());
			pstm.setString(10, "");
			pstm.setInt(11, 0);
			pstm.setInt(12, 0);
			pstm.setInt(13, 0);
			pstm.setInt(14, 0);
			pstm.setInt(15, 0);
			pstm.setInt(16, 0);
			pstm.setInt(17, 0);
			pstm.setInt(18, 0);
			pstm.setInt(19, 0);
			pstm.setInt(20, clan.getJoinSetting());
			pstm.setInt(21, clan.getJoinType());
			pstm.setInt(22, clan.getEinhasadBlessBuff());
			pstm.setInt(23, clan.getBuffFirst());
			pstm.setInt(24, clan.getBuffSecond());
			pstm.setInt(25, clan.getBuffThird());
			if (pstm.executeUpdate() != 1)
                throw new SQLException("Clan creation did not insert exactly one row: " + clan.getClanName());
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ClanTable createTutorialClan Error", e);
            // A failed or uncertain INSERT must never publish a memory-only clan.
            return null;
		}

		L1World.getInstance().storeClan(clan);
		_clans.put(clan.getClanId(), clan);

		manager.setClanid(clan.getClanId());
		manager.setClanname(clan.getClanName());
		manager.setClanRank(L1Clan.군주);
		clan.addClanMember(manager.getName(), manager.getClanRank(), manager.getLevel(), "", manager.getId(), manager.getType(), manager.getOnlineStatus(), manager);
		return clan;
	}
	
    /** Paid clan creation: the leader row, clan row and 10k adena debit share
     * one CacheD receipt. The CIO owner publishes the clan after COMMIT only.
     */
    public void createClanPaid(L1PcInstance player, String clanName, Consumer<L1Clan> committed) {
        if (player == null || clanName == null || clanName.isEmpty() || player.getClanid() != 0
                || !player.isCrown() || player.isGhost()) return;
        if (L1World.getInstance().findClan(clanName) != null) return;
        final L1Clan clan = new L1Clan();
        clan.setClanId(IdFactory.getInstance().nextId());
        clan.setClanName(clanName);
        clan.setLeaderId(player.getId());
        clan.setLeaderName(player.getName());
        clan.setCastleId(0);
        clan.setHouseId(0);
        clan.setAlliance(0);
        clan.setClanBirthDay(new Timestamp(System.currentTimeMillis()));
        clan.setAnnouncement("");
        clan.setEmblemId(0);
        clan.setEmblemStatus(0);
        clan.setBless(0);
        clan.setBlessCount(0);
        clan.setBuffTime(0, 0, 0, 0);
        clan.setJoinSetting(1);
        clan.setJoinType(1);
        clan.setBot(false);
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(player,
                CacheD.accountOrderKey("$clan-create"))) {
            if (!tx.debit(40308, 10000)) return;
            tx.requireTables("clan_data", "characters");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT ClanID FROM characters WHERE objid=? FOR UPDATE")) {
                    ps.setInt(1, player.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || rs.getInt(1) != 0)
                            throw new SQLException("Clan founder already joined a clan");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT clan_id FROM clan_data WHERE clan_name=? FOR UPDATE")) {
                    ps.setString(1, clanName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) throw new SQLException("Clan name already exists");
                    }
                }
                insertClanRow(con, clan);
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE characters SET ClanID=?,Clanname=?,ClanRank=? "
                        + "WHERE objid=? AND ClanID=0")) {
                    ps.setInt(1, clan.getClanId());
                    ps.setString(2, clanName);
                    ps.setInt(3, L1Clan.군주);
                    ps.setInt(4, player.getId());
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Founder clan before-image changed");
                }
            });
            tx.onFailure(() -> l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.send(
                    player, "혈맹 창설을 완료하지 못했습니다. 다시 시도해 주세요."));
            tx.commitAfterInventoryRelease(() -> {
                if (ClanTable.getInstance() != this || player.getClanid() != 0
                        || L1World.getInstance().findClan(clanName) != null)
                    throw new IllegalStateException("Clan creation world preimage changed after COMMIT");
                L1World.getInstance().storeClan(clan);
                _clans.put(clan.getClanId(), clan);
                player.setClanid(clan.getClanId());
                player.setClanname(clanName);
                player.setClanRank(L1Clan.군주);
                clan.addClanMember(player.getName(), player.getClanRank(), player.getLevel(), "",
                        player.getId(), player.getType(), player.getOnlineStatus(), player);
                if (committed != null) committed.accept(clan);
            });
        } catch (RuntimeException e) {
            _log.log(Level.WARNING, "Clan creation receipt not admitted: " + clanName, e);
            l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.send(
                    player, "혈맹 창설을 시작하지 못했습니다.");
        }
    }

    /** Caller owns the JDBC transaction; NEVER commit a partial clan insert. */
    private static void insertClanRow(Connection con, L1Clan clan) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO clan_data SET clan_id=?, clan_name=?, leader_id=?, leader_name=?, "
                + "hascastle=?, hashouse=?, alliance=?, clan_birthday=?, max_online_user=?, "
                + "announcement=?, emblem_id=?, emblem_status=?, clan_exp=?, bless=?, bless_count=?, "
                + "attack=?, defence=?, pvpattack=?, pvpdefence=?, join_setting=?, join_type=?, bot=?, "
                + "EinhasadBlessBuff=?, Buff_List1=?, Buff_List2=?, Buff_List3=?")) {
            ps.setInt(1, clan.getClanId());
            ps.setString(2, clan.getClanName());
            ps.setInt(3, clan.getLeaderId());
            ps.setString(4, clan.getLeaderName());
            ps.setInt(5, clan.getCastleId());
            ps.setInt(6, clan.getHouseId());
            ps.setInt(7, clan.getAlliance());
            ps.setTimestamp(8, clan.getClanBirthDay());
            ps.setInt(9, clan.getOnlineMaxUser());
            ps.setString(10, clan.getAnnouncement());
            ps.setInt(11, clan.getEmblemId());
            ps.setInt(12, clan.getEmblemStatus());
            ps.setInt(13, 0);
            ps.setInt(14, clan.getBless());
            ps.setInt(15, clan.getBlessCount());
            ps.setInt(16, 0);
            ps.setInt(17, 0);
            ps.setInt(18, 0);
            ps.setInt(19, 0);
            ps.setInt(20, clan.getJoinSetting());
            ps.setInt(21, clan.getJoinType());
            ps.setBoolean(22, clan.isBot());
            ps.setInt(23, clan.getEinhasadBlessBuff());
            ps.setInt(24, clan.getBuffFirst());
            ps.setInt(25, clan.getBuffSecond());
            ps.setInt(26, clan.getBuffThird());
            if (ps.executeUpdate() != 1) throw new SQLException("Clan insert did not affect one row");
        }
    }

	public L1Clan createClan(L1PcInstance player, String clan_name, boolean isBot) {
		for (L1Clan oldClans : _clans.valueCollection()) {
			if (oldClans != null && oldClans.getClanName().equalsIgnoreCase(clan_name)) {
				return null;
			}
		}
		Timestamp time = new Timestamp(System.currentTimeMillis());
		L1Clan clan = new L1Clan();
		clan.setClanId(IdFactory.getInstance().nextId());
		clan.setClanName(clan_name);
		clan.setLeaderId(player.getId());
		clan.setLeaderName(player.getName());
		clan.setCastleId(0);
		clan.setHouseId(0);
		clan.setAlliance(0);
		clan.setClanBirthDay(time);
		clan.setAnnouncement("");
		clan.setEmblemId(0);
		clan.setEmblemStatus(0);
		clan.setBless(0);
		clan.setBlessCount(0);
		clan.setBuffTime(0, 0, 0, 0);
		clan.setJoinSetting(1);
		clan.setJoinType(1);
		clan.setBot(isBot);
		
		String sql = "INSERT INTO clan_data SET clan_id=?, clan_name=?, leader_id=?, leader_name=?, hascastle=?, hashouse=?, alliance=?, clan_birthday=?, max_online_user=?, announcement=?, emblem_id=?, emblem_status=?,clan_exp=?,bless=?,bless_count=?,attack=?,defence=?,pvpattack=?,pvpdefence=?,join_setting=?,join_type=?,bot=?,EinhasadBlessBuff=?,Buff_List1=?,Buff_List2=?,Buff_List3=?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, clan.getClanId());
			pstm.setString(2, clan.getClanName());
			pstm.setInt(3, clan.getLeaderId());
			pstm.setString(4, clan.getLeaderName());
			pstm.setInt(5, clan.getCastleId());
			pstm.setInt(6, clan.getHouseId());
			pstm.setInt(7, clan.getAlliance());
			pstm.setTimestamp(8, clan.getClanBirthDay());
			pstm.setInt(9, clan.getOnlineMaxUser());
			pstm.setString(10, "");
			pstm.setInt(11, 0);
			pstm.setInt(12, 0);
			pstm.setInt(13, 0);
			pstm.setInt(14, 0);
			pstm.setInt(15, 0);
			pstm.setInt(16, 0);
			pstm.setInt(17, 0);
			pstm.setInt(18, 0);
			pstm.setInt(19, 0);
			pstm.setInt(20, clan.getJoinSetting());
			pstm.setInt(21, clan.getJoinType());
			pstm.setBoolean(22, clan.isBot());
			pstm.setInt(23, clan.getEinhasadBlessBuff());
			pstm.setInt(24, clan.getBuffFirst());
			pstm.setInt(25, clan.getBuffSecond());
			pstm.setInt(26, clan.getBuffThird());
			if (pstm.executeUpdate() != 1)
                throw new SQLException("Clan creation did not insert exactly one row: " + clan.getClanName());
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ClanTable createClan Error", e);
            // A failed or uncertain INSERT must never publish a memory-only clan.
            return null;
		}

		L1World.getInstance().storeClan(clan);
		_clans.put(clan.getClanId(), clan);

		player.setClanid(clan.getClanId());
		player.setClanname(clan.getClanName());
		player.setClanRank(L1Clan.군주);
		clan.addClanMember(player.getName(), player.getClanRank(), player.getLevel(), "", player.getId(), player.getType(), player.getOnlineStatus(), player);
		try {
			player.save();
		} catch (Exception e) {
			_log.log(Level.SEVERE, "ClanTable player save Error", e);
		}
		return clan;
	}
	
	public L1Clan createClan(L1PcInstance player, String clan_name) {
		return createClan(player, clan_name, false);
	}

	/** Additive, guarded update shared by item receipts and independent clan rewards. */
	private static void applyWarPointSql(Connection con, int clanId, String clanName, int delta)
			throws SQLException {
		if (delta == 0 || clanId <= 0 || clanName == null)
			throw new SQLException("Invalid clan point mutation");
		final long lower = Math.max(0L, -(long) delta);
		final long upper = Math.min((long) Integer.MAX_VALUE,
				(long) Integer.MAX_VALUE - delta);
		try (PreparedStatement ps = con.prepareStatement(
				"UPDATE clan_data SET War_point=War_point+? "
				+ "WHERE clan_id=? AND clan_name=? AND War_point>=? AND War_point<=?")) {
			ps.setInt(1, delta);
			ps.setInt(2, clanId);
			ps.setString(3, clanName);
			ps.setLong(4, lower);
			ps.setLong(5, upper);
			if (ps.executeUpdate() != 1)
				throw new SQLException("Clan points changed, insufficient, or out of bounds: " + clanId);
		}
	}

	/** Include points in the same durable receipt as item consumption and rewards. */
	public static void stageWarPointDelta(L1InventoryTransaction tx, L1Clan clan, int delta) {
		if (clan == null || clan.isWarPointUncertain() || delta == 0)
			throw new IllegalStateException("Clan point owner unavailable or uncertain");
		final int id = clan.getClanId();
		final String name = clan.getClanName();
		tx.requireTables("clan_data");
		tx.sql(con -> applyWarPointSql(con, id, name, delta));
	}

	/** Non-item credits/debits: existing CacheD ordered worker and durable receipt.
	 * No game-thread JDBC, no pre-commit memory mutation and no retry after uncertain COMMIT.
	 * The result callback must publish packets only, never mutate game-owner state.
	 */
	public static boolean changeWarPoint(L1Clan clan, int delta, Consumer<Boolean> result) {
		if (clan == null || clan.getClanId() <= 0 || clan.isWarPointUncertain() || delta == 0) return false;
		final int clanId = clan.getClanId();
		final String clanName = clan.getClanName();
		final int ownerKey = CacheD.accountOrderKey("war-point:" + clanId);
		final String receiptId = UUID.randomUUID().toString();
		try {
			CacheD.getInstance().submitDbJobOrdered(ownerKey, () -> {
				try {
					CTransaction.persistEconomy(receiptId, ownerKey, clanId,
							new CTransaction.Mutation[] {
								con -> CTransaction.requireTransactionalTables(con, "clan_data"),
								con -> applyWarPointSql(con, clanId, clanName, delta)
							});
					clan.addWarPointCommitted(delta);
					if (result != null) notifyWarPointResult(result, true);
				} catch (SQLException | RuntimeException failure) {
					for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
						if (cause instanceof CTransaction.OutcomeUnknownException) {
							clan.markWarPointUncertain();
							break;
						}
					}
					_log.log(Level.SEVERE, "Clan point settlement failed or unresolved: " + clanId, failure);
					if (result != null) notifyWarPointResult(result, false);
					throw new IllegalStateException("Clan point settlement failed", failure);
				}
			});
			return true;
		} catch (RuntimeException rejected) {
			_log.log(Level.SEVERE, "Clan point settlement not admitted: " + clanId, rejected);
			if (result != null) notifyWarPointResult(result, false);
			return false;
		}
	}

	private static void notifyWarPointResult(Consumer<Boolean> action, boolean committed) {
		try { action.accept(committed); }
		catch (RuntimeException failure) {
			_log.log(Level.WARNING, "Clan point notification failed", failure);
		}
	}
	
    public static void updateOnlineUser(L1Clan clan) {
        final int clanId = clan.getClanId();
        final String name = clan.getClanName();
        try {
            CacheD.getInstance().submitDbJobOrdered(clanId, () -> {
                if (L1World.getInstance().getClan(clanId) != clan) return;
                final long counts = clan.GetMemberCounts();
                final String sql = "UPDATE clan_data SET total_m=?,current_m=? WHERE clan_id=? AND clan_name=?";
                try (Connection con = DatabaseFactory.getInstance().getConnection();
                     PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, (int)(counts >>> 32));
                    ps.setInt(2, (int)counts);
                    ps.setInt(3, clanId);
                    ps.setString(4, name);
                    ps.executeUpdate();
                } catch (Exception failure) {
                    _log.log(Level.SEVERE, "Clan member count update failed: " + clanId, failure);
                }
            });
        } catch (RuntimeException failure) {
            // A count is derived metadata; it must never abort a committed
            // roster publication. A later membership/session update refreshes it.
            _log.log(Level.WARNING, "Clan member count admission failed: " + clanId, failure);
        }
    }


	public void updateClan(L1Clan clan) {
        updateClan(clan,null);
    }

    public void updateClan(L1Clan clan,Runnable afterCommit) {
        final int owner=clan.getClanId();
        final Object[] values = new Object[]{clan.getClanId(),clan.getLeaderId(),clan.getLeaderName(),clan.getCastleId(),clan.getAlliance(),clan.getClanBirthDay(),clan.getBotStyle(),clan.getBotLevel(),clan.getOnlineMaxUser(),clan.getAnnouncement(),clan.getEmblemId(),clan.getEmblemStatus(),clan.getJoinSetting(),clan.getJoinType(),clan.getClanMemberList().size(),clan.getCurrentOnlineMemebers(),clan.getClanName()};
        final String sql="UPDATE clan_data SET clan_id=?, leader_id=?, leader_name=?, hascastle=?, alliance=?, clan_birthday=?, bot_style=?, bot_level=?, max_online_user=?, announcement=?, emblem_id=?, emblem_status=?, join_setting=?, join_type=?, total_m=?, current_m=? WHERE clan_name=?";
        CacheD.getInstance().submitDbJobOrdered(owner,()->{
            try(Connection con=DatabaseFactory.getInstance().getConnection();PreparedStatement ps=con.prepareStatement(sql)) {
                for(int index=0;index<values.length;index++)ps.setObject(index+1,values[index]);
                final int rows=ps.executeUpdate();
                if(rows>1)throw new SQLException("Duplicate clan metadata row");
                if(rows==1 && afterCommit!=null)afterCommit.run();
            } catch(SQLException failure){throw new IllegalStateException("Clan metadata persistence",failure);}
        });
    }

	public void persistInitialBuffs(L1Clan clan) {
        final int id=clan.getClanId(),a=clan.getBuffFirst(),b=clan.getBuffSecond(),c=clan.getBuffThird();
        CacheD.getInstance().submitDbJobOrdered(id,()->{
            try(Connection con=DatabaseFactory.getInstance().getConnection();PreparedStatement ps=con.prepareStatement(
                    "UPDATE clan_data SET Buff_List1=?,Buff_List2=?,Buff_List3=? WHERE clan_id=? "
                    +"AND EinhasadBlessBuff=0 AND Buff_List1=0 AND Buff_List2=0 AND Buff_List3=0")) {
                ps.setInt(1,a);ps.setInt(2,b);ps.setInt(3,c);ps.setInt(4,id);ps.executeUpdate();
            }catch(SQLException failure){throw new IllegalStateException("Initial clan buffs",failure);}
        });
    }

    public void createClanBot(L1PcInstance player, String clan_name, int style) {
		for (L1Clan oldClans : _clans.valueCollection()) {
			if (oldClans != null && oldClans.getClanName().equalsIgnoreCase(clan_name))
				return;
		}

		L1Clan clan = new L1Clan();
		clan.setClanId(IdFactory.getInstance().nextId());
		clan.setClanName(clan_name);
		clan.setLeaderId(player.getId());
		clan.setLeaderName(player.getName());
		clan.setCastleId(0);
		clan.setHouseId(0);
		clan.setBot(true);
		clan.setBotStyle(style);


		String sql = "INSERT INTO clan_data SET clan_id=?, clan_name=?, leader_id=?, leader_name=?, hascastle=?, hashouse=?, bot=?, bot_style=?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			
			pstm.setInt(1, clan.getClanId());
			pstm.setString(2, clan.getClanName());
			pstm.setInt(3, clan.getLeaderId());
			pstm.setString(4, clan.getLeaderName());
			pstm.setInt(5, clan.getCastleId());
			pstm.setInt(6, clan.getHouseId());
			pstm.setString(7, "true");
			pstm.setInt(8, style);
			if (pstm.executeUpdate() != 1)
                throw new SQLException("Clan creation did not insert exactly one row: " + clan.getClanName());
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ClanTable createClanBot Error", e);
            return;
		}

		L1World.getInstance().storeClan(clan);
		_clans.put(clan.getClanId(), clan);
        player.setClanid(clan.getClanId());
        player.setClanname(clan.getClanName());
	}

    /**
     * Disband a clan as one durable change. Member rows and clan_data must
     * never commit separately: a failed clan DELETE must leave the member
     * association intact for retry/recovery.
     */
    public void deleteClanWithMembers(L1Clan clan) {
        if (clan == null) return;
        // A disband must not silently destroy an occupied/pending pledge vault.
        // A matching DB row is also checked under the same SQL transaction.
        final ClanWarehouse vault = WarehouseManager.getInstance().getClanWarehouse(clan.getClanName());
        if (vault == null) throw new IllegalStateException("Clan warehouse unavailable");
        final int lane = vault.transferOrderKey();
        if (vault.getWarehouseUsingChar() != 0 || vault.getSize() != 0
                || CacheD.getInstance().hasPendingOwnerApply(lane)
                || !CacheD.getInstance().flushDbOwner(lane, 0L))
            throw new IllegalStateException("Clan warehouse occupied or has outstanding DB work");
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            con.setAutoCommit(false);
            boolean committed = false;
            boolean rolledBack = false;
            try {
                applyDeletePledgeSql(con, clan, null);
                con.commit();
                committed = true;
            } catch (SQLException | RuntimeException failure) {
                try { con.rollback(); rolledBack = true; }
                catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            } finally {
                // Never reset auto-commit after a failed rollback: doing so
                // could commit an uncertain partial transaction on a pooled connection.
                if (committed || rolledBack) {
                    try { con.setAutoCommit(true); }
                    catch (SQLException resetFailure) {
                        if (!committed) throw resetFailure;
                        // COMMIT already succeeded; reporting a disband failure here
                        // would leave a durable deletion with a live world clan.
                        _log.log(Level.WARNING, "Committed clan disband: connection reset failed", resetFailure);
                    }
                }
            }
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "ClanTable deleteClanWithMembers Error", e);
            throw new IllegalStateException("Clan disband not committed: " + clan.getClanName(), e);
        }
        forgetCommittedClanMerge(clan);
    }

    /** Stage the membership and pledge deletion inside one existing CacheD receipt. */
    public static void stageDeletePledge(CacheD.WarehouseMove tx, L1Clan clan, int[] expectedIds) {
        tx.stageWarehouseSql(con -> applyDeletePledgeSql(con, clan, expectedIds));
    }

    private static void applyDeletePledgeSql(Connection con, L1Clan clan, int[] expectedIds)
            throws SQLException {
        // MyISAM or a missing table cannot provide the advertised
        // all-or-nothing disband semantics. Refuse before any mutation.
        CTransaction.requireTransactionalTables(con,
                "characters", "clan_data", "clan_warehouse");
        try (PreparedStatement current = con.prepareStatement(
                "SELECT clan_name,leader_id,hashouse,hascastle,alliance "
                + "FROM clan_data WHERE clan_id=? FOR UPDATE")) {
            current.setInt(1, clan.getClanId());
            try (ResultSet rs = current.executeQuery()) {
                if (!rs.next() || !clan.getClanName().equals(rs.getString(1))
                        || clan.getLeaderId() != rs.getInt(2)
                        || rs.getInt(3) != 0 || rs.getInt(4) != 0 || rs.getInt(5) != 0)
                    throw new SQLException("Clan disband DB ownership/precondition changed");
            }
        }
        try (PreparedStatement stored = con.prepareStatement(
                "SELECT id FROM clan_warehouse WHERE clan_name=? LIMIT 1 FOR UPDATE")) {
            stored.setString(1, clan.getClanName());
            try (ResultSet rs = stored.executeQuery()) {
                if (rs.next()) throw new SQLException("Clan disband vault not empty");
            }
        }
        // A concurrent admission/merge must not introduce a member whose
        // CacheD save lane was not reserved by the dismissal.
        if (expectedIds != null) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT objid FROM characters WHERE ClanID=? ORDER BY objid FOR UPDATE")) {
                ps.setInt(1, clan.getClanId());
                try (ResultSet rs = ps.executeQuery()) {
                    int index = 0;
                    while (rs.next()) {
                        if (index >= expectedIds.length || rs.getInt(1) != expectedIds[index++])
                            throw new SQLException("Pledge dismissal roster changed");
                    }
                    if (index != expectedIds.length)
                        throw new SQLException("Pledge dismissal roster changed");
                }
            }
        }
        try (PreparedStatement members = con.prepareStatement(
                "UPDATE characters SET ClanID=0, Clanname='', ClanRank=0, Title='', notes='' WHERE ClanID=?")) {
            members.setInt(1, clan.getClanId());
            members.executeUpdate();
        }
        try (PreparedStatement delete = con.prepareStatement(
                "DELETE FROM clan_data WHERE clan_id=? AND clan_name=? AND leader_id=? "
                + "AND hashouse=0 AND hascastle=0 AND alliance=0")) {
            delete.setInt(1, clan.getClanId());
            delete.setString(2, clan.getClanName());
            delete.setInt(3, clan.getLeaderId());
            if (delete.executeUpdate() != 1)
                throw new SQLException("Clan DELETE did not affect exactly one row: " + clan.getClanName());
        }
    }

    /** All member owner replays succeeded; the DB row is already gone. */
    public void forgetCommittedClanDisband(L1Clan clan, ClanWarehouse vault) {
        if (_clans.get(clan.getClanId()) != clan)
            throw new IllegalStateException("Stale committed pledge dismissal");
        L1World.getInstance().removeClan(clan);
        _clans.remove(clan.getClanId());
        WarehouseManager.getInstance().retireDisbandedClanWarehouse(clan.getClanName(), vault);
    }

    public void deleteClan(L1Clan clan){
        if (clan == null) return;
        String sql = "DELETE FROM clan_data WHERE clan_id = ? AND clan_name = ?";
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(sql)) {
            pstm.setInt(1, clan.getClanId());
            pstm.setString(2, clan.getClanName());
            if (pstm.executeUpdate() != 1)
                throw new SQLException("Clan DELETE did not affect exactly one row: " + clan.getClanName());
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "ClanTable deleteClan Error", e);
            throw new IllegalStateException("Clan deletion not committed: " + clan.getClanName(), e);
        }
        finishClanDeletion(clan);
    }

    private void finishClanDeletion(L1Clan clan) {
        ClanWarehouse clanWarehouse = WarehouseManager.getInstance().getClanWarehouse(clan.getClanName());
        if (clanWarehouse != null) {
            clanWarehouse.clearItems();
            clanWarehouse.deleteAllItems();
        }
        L1World.getInstance().removeClan(clan);
        _clans.remove(clan.getClanId());
    }

    /** Memory-only retirement for a source pledge already removed in the
     * same durable CacheD transaction as its complete member transfer.
     * Never delete its warehouse rows again outside that transaction.
     */
    public void forgetCommittedClanMerge(L1Clan clan) {
        if (clan == null || _clans.get(clan.getClanId()) != clan) {
            throw new IllegalStateException("Stale source pledge after merge commit");
        }
        L1World.getInstance().removeClan(clan);
        _clans.remove(clan.getClanId());
        try {
            WarehouseManager.getInstance().delClanWarehouse(clan.getClanName());
        } catch (IllegalStateException failure) {
            // The SQL receipt is durable; refusing the world removal here would
            // resurrect an already-deleted pledge. Log deferred cache eviction.
            _log.log(Level.WARNING, "Committed pledge warehouse cache not yet retired", failure);
        }
    }

	public void deleteClan(String clan_name) {
		L1Clan clan = L1World.getInstance().findClan(clan_name);
		if (clan != null) {
			deleteClan(clan);
		}
	}

	public L1Clan getTemplate(int clan_id) {
		return _clans.get(clan_id);
	}
	
	public L1Clan getRedKnight(int castleId){
		return null; //_redknights.get(castleId);
	}

	public static synchronized void reload() {
		// Do not erase active clans on a failed or partial database reload.
		// The staged table never publishes until its roster and warehouses load.
		ClanTable candidate = new ClanTable(false);
		ClanTable oldInstance = _instance;
		L1World.getInstance().clearClan();
		candidate.publishClans();
		_instance = candidate;
		if (oldInstance != null) oldInstance._clans.clear();
	}

	public L1Clan find(String clan_name) {
		for (L1Clan clan : _clans.valueCollection()) {
			if (clan != null && clan.getClanName().equalsIgnoreCase(clan_name))
				return clan;
		}
		return null;
	}
	
	public L1Clan findCastleClan(int castleId){
		for (L1Clan clan : _clans.valueCollection()) {
			if (clan != null && clan.getCastleId() == castleId)
				return clan;
		}
		return null;
	}

	public void updateUnderDungeon(int clanid, int type) {
        try {
            updateUnderDungeonOrThrow(clanid, type);
        } catch (SQLException e) {
            _log.log(Level.SEVERE, "ClanTable updateUnderDungeon Error", e);
        }
    }

    /** Daily reset must not publish a memory-only success if SQL failed. */
    public void updateUnderDungeonOrThrow(int clanid, int type) throws SQLException {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement(
                     "UPDATE clan_data SET under_dungeon = ? WHERE clan_id = ?")) {
            pstm.setInt(1, type);
            pstm.setInt(2, clanid);
            pstm.executeUpdate();
        }
    }

	public void updateRankDate(int clanid, Timestamp time) {
		try (Connection con = DatabaseFactory.getInstance().getConnection(); 
			 PreparedStatement pstm = con.prepareStatement("UPDATE clan_data SET rankdate = ? WHERE clan_id = ?")) {
			pstm.setTimestamp(1, time);
			pstm.setInt(2, clanid);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ClanTable updateRankDate Error", e);
		}
	}


	public void updateRankTime(int clanid, int time) {
		try (Connection con = DatabaseFactory.getInstance().getConnection(); 
			 PreparedStatement pstm = con.prepareStatement("UPDATE clan_data SET ranktime = ? WHERE clan_id = ?")) {
			pstm.setInt(1, time);
			pstm.setInt(2, clanid);
			pstm.execute();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ClanTable updateRankTime Error", e);
		}
	}

	public void updateBlessCount(int clanid, int count) {
        CacheD.getInstance().submitDbJobOrdered(clanid,()->{
		try (Connection con = DatabaseFactory.getInstance().getConnection();
				PreparedStatement pstm = con.prepareStatement("UPDATE clan_data SET bless_count = ? WHERE clan_id = ?")) {
			pstm.setInt(1, count);
			pstm.setInt(2, clanid);
			pstm.execute();
		} catch (SQLException e) {
			throw new IllegalStateException("Clan points save",e);
		}
	
        });
    }

	public void updateBless(int clanid, int bless) {
        CacheD.getInstance().submitDbJobOrdered(clanid,()->{
		try (Connection con = DatabaseFactory.getInstance().getConnection(); 
			 PreparedStatement pstm = con.prepareStatement("UPDATE clan_data SET bless = ? WHERE clan_id = ?")) {
			pstm.setInt(1, bless);
			pstm.setInt(2, clanid);
			pstm.execute();
		} catch (SQLException e) {
			throw new IllegalStateException("Clan blessing save",e);
		}
	
        });
    }

	public void updateBuffTime(int a, int b, int c, int d, int clanid) {
        CacheD.getInstance().submitDbJobOrdered(clanid,()->{
		try (Connection con = DatabaseFactory.getInstance().getConnection();
				PreparedStatement pstm = con.prepareStatement("UPDATE clan_data SET attack = ?, defence = ?, pvpattack = ?, pvpdefence = ? WHERE clan_id = ?")) {
			pstm.setInt(1, a);
			pstm.setInt(2, b);
			pstm.setInt(3, c);
			pstm.setInt(4, d);
			pstm.setInt(5, clanid);
			pstm.execute();
		} catch (SQLException e) {
			throw new IllegalStateException("Clan buff time save",e);
		}
	
        });
    }

	public void updateClanPassword(L1Clan clan) {
		String sql = "UPDATE clan_data SET join_password = ? WHERE clan_name = ?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {
			pstm.setString(1, clan.getJoinPassword());
			pstm.setString(2, clan.getClanName());
			pstm.executeUpdate();
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ClanTable updateClanPassword Error", e);
		}
	}
}