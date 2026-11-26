package l1j.server.server.model;

import l1j.server.server.model.quest.CQuestSystem;
import l1j.server.server.model.network.core.sc.packet.SC_ClanName;


import l1j.server.server.model.network.core.sc.packet.SC_CharTitle;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.inventory.warehouse.ClanWarehouse;
import l1j.server.server.model.inventory.warehouse.WarehouseManager;

import java.util.concurrent.RejectedExecutionException;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.utils.ServerMessage;
import l1j.server.server.model.inventory.L1InventoryTransaction;

public class L1ClanJoin {

	private static final Logger _log = Logger.getLogger(L1ClanJoin.class.getName());
	private static volatile L1ClanJoin _instance;

	public static L1ClanJoin getInstance() {
		if (_instance == null) {
			synchronized (L1ClanJoin.class) {
				if (_instance == null) {
					_instance = new L1ClanJoin();
				}
			}
		}
		return _instance;
	}

	private L1ClanJoin() { }
	
    public void tutorialJoin(L1Clan clan, L1PcInstance pc) {
        if (clan != null && pc != null)
            joinClanAtomic(clan, pc, Integer.MAX_VALUE, true, null);
    }

    /** A single-character admission is committed before the world/packets
     * change. Use the clan resource FIFO and the character's existing CacheD
     * owner; admission is not a durable success acknowledgement.
     */
    private static int dbClanCapacity(Connection con, int leaderId) throws SQLException {
        if (Config.MAX_CLAN_MEMBER > 0) return Config.MAX_CLAN_MEMBER;
        int level, charisma;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT level, Cha FROM characters WHERE objid=? FOR UPDATE")) {
            ps.setInt(1, leaderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Clan leader record missing");
                level = rs.getInt(1);
                charisma = rs.getInt(2);
            }
        }
        boolean quest45;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT quest_step FROM character_quests WHERE char_id=? AND quest_id=? FOR UPDATE")) {
            ps.setInt(1, leaderId);
            ps.setInt(2, CQuestSystem.QUEST_LEVEL45);
            try (ResultSet rs = ps.executeQuery()) {
                quest45 = rs.next() && rs.getInt(1) == 0xff;
            }
        }
        if (charisma <= 0) throw new SQLException("Clan leader charisma invalid");
        return charisma * (quest45 ? 3 : 1) * (level >= 50 ? 3 : 2);
    }

    /** CacheD worker preflight: read primitive IDs only, fail closed on SQL
     * error or missing character. Never allocate a list of boxed/string IDs. */
    public static boolean CheckAccountPledge(String account, int characterId,
            int sourceClanId, int targetClanId) throws SQLException {
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            return CheckAccountPledge(con, account, characterId, sourceClanId, targetClanId, false);
        }
    }

    static boolean CheckAccountPledge(Connection con, String account, int characterId,
            int sourceClanId, int targetClanId, boolean lock) throws SQLException {
        if (account == null || account.isEmpty() || characterId <= 0 || targetClanId <= 0)
            return false;
        final String sql = lock
                ? "SELECT objid,ClanID FROM characters WHERE account_name=? ORDER BY objid FOR UPDATE"
                : "SELECT objid,ClanID FROM characters WHERE account_name=?";
        boolean found = false;
        boolean compatible = true;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, account);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final int id = rs.getInt(1), clanId = rs.getInt(2);
                    if (id == characterId) {
                        if (found || clanId != sourceClanId) compatible = false;
                        found = true;
                    }
                    if (clanId != 0 && clanId != targetClanId) compatible = false;
                }
            }
        }
        return found && compatible;
    }

    private boolean joinClanAtomic(L1Clan clan, L1PcInstance joinPc, int capacity,
                                   boolean tutorial, Consumer<Boolean> result) {
        return joinClanAtomic(clan, joinPc, capacity, tutorial, result, null);
    }

    private boolean joinClanAtomic(L1Clan clan, L1PcInstance joinPc, int capacity,
                                   boolean tutorial, Consumer<Boolean> result, String account) {
        if (clan == null || clan.IsDismissed() || joinPc == null || joinPc.getClanid() != 0) return false;
        final int clanId = clan.getClanId(), characterId = joinPc.getId();
        final String clanName = clan.getClanName();
        final int rank = L1Clan.수련;
        if (capacity > 0 && capacity <= clan.getClanMemberList().size()) return false;
        final int clanLane = CacheD.accountOrderKey("clan-join:", clanId);
        try (L1InventoryTransaction tx = account == null
                ? L1InventoryTransaction.begin(joinPc, clanLane)
                : L1InventoryTransaction.begin(joinPc, clanLane,
                        new int[]{CacheD.accountOrderKey(account)})) {
            tx.requireTables("clan_data", "characters", "character_quests");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT clan_name FROM clan_data WHERE clan_id=? FOR UPDATE")) {
                    ps.setInt(1, clanId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || !clanName.equals(rs.getString(1)))
                            throw new SQLException("Clan no longer exists");
                    }
                }
                if (account != null && !CheckAccountPledge(con, account, characterId, 0, clanId, true))
                    throw new SQLException("Account pledge restriction changed before COMMIT");
                // The world roster is a fast preliminary bound; the DB row
                // count under the same receipt owns the final capacity test.
                final int allowed = capacity > 0 ? capacity : dbClanCapacity(con, clan.getLeaderId());
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT objid FROM characters WHERE ClanID=? FOR UPDATE")) {
                    ps.setInt(1, clanId);
                    int members = 0;
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (++members >= allowed)
                                throw new SQLException("Clan capacity exhausted");
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE characters SET ClanID=?,Clanname=?,ClanRank=?,notes='',Title=? "
                        + "WHERE objid=? AND ClanID=0")) {
                    ps.setInt(1, clanId);
                    ps.setString(2, clanName);
                    ps.setInt(3, rank);
                    ps.setString(4, tutorial ? Config.GAME_SERVER_NAME : "");
                    ps.setInt(5, characterId);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Clan admission character preimage changed");
                }
            });
            tx.onFailure(() -> {
                SC_SystemMessage.send(joinPc, "혈맹 가입을 완료하지 못했습니다.");
                if (result != null) result.accept(false);
            });
            tx.commitAfterInventoryRelease(() -> {
                if (CWorld.getInstance().getClan(clanId) != clan || joinPc.getClanid() != 0)
                    throw new IllegalStateException("Stale clan admission after COMMIT");
                // Commit the live state before encoding or sending any packet.
                joinPc.setClanid(clanId);
                joinPc.setClanname(clanName);
                joinPc.setClanRank(rank);
                joinPc.setClanMemberNotes("");
                joinPc.setTitle(tutorial ? Config.GAME_SERVER_NAME : "");
                clan.addClanMember(joinPc.getName(), rank, joinPc.getLevel(), "",
                        characterId, joinPc.getType(), joinPc.getOnlineStatus(), joinPc);
                try {
                    // Preserve the old notice recipients (exclude the new member).
                    try (var notice = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                            SC_ServerMessage.nativeWriter(94, joinPc.getName()), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                        for (L1PcInstance other : clan.getOnlineClanMember()) {
                            if (other != joinPc)
                                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(notice, other);
                        }
                    }
                    SC_CharTitle.send(joinPc, joinPc.getId(), joinPc.getTitle());
                    SC_CharTitle.broadcast(joinPc, joinPc.getId(), joinPc.getTitle());
                if (tutorial) SC_SystemMessage.send(joinPc, "중립혈맹은 PK에 대미지 제한을 받습니다.");
                SC_PacketBox.send(joinPc, SC_PacketBox.MSG_RANK_CHANGED, rank, joinPc.getName());
                SC_ServerMessage.send(joinPc, 95, clanName);
                SC_ClanName.send(joinPc, joinPc, clan.getEmblemId(), rank);
                SC_ReturnedStat.sendEmblem(joinPc, characterId, clanId);
                SC_PacketBox.send(joinPc, SC_PacketBox.PLEDGE_EMBLEM_STATUS, clan.getEmblemStatus());
                SC_ActionUI.send(joinPc, clanName, rank);
                for (L1PcInstance other : clan.getOnlineClanMember()) {
                    SC_ReturnedStat.sendEmblem(other, characterId, clan.getEmblemId());
                    SC_ReturnedStat.broadcastEmblem(other, other.getId(), clan.getEmblemId());
                }
                } catch (RuntimeException failure) {
                    _log.log(Level.WARNING, "Committed pledge join notification failed: " + characterId, failure);
                }
                if (!tutorial) giveClanBless(clan, joinPc);
                if (result != null) result.accept(true);
            });
            return true;
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Clan admission not admitted id=" + characterId, failure);
            SC_SystemMessage.send(joinPc, "혈맹 가입 요청을 시작하지 못했습니다.");
            return false;
        }
    }

    /** Transfer an entire source pledge in ONE durable receipt. A failed SQL
     * operation leaves both rosters intact; no partial per-member save occurs.
     * The source warehouse must be unused and empty: a merge must NEVER delete
     * a clan warehouse holding items or race a pending warehouse transfer.
     */
    private boolean mergeClanAtomic(L1Clan target, L1Clan source, L1PcInstance sourceLeader,
                                    int capacity, Consumer<Boolean> result) {
        return mergeClanAtomic(target, source, sourceLeader, capacity, result, null);
    }

    private boolean mergeClanAtomic(L1Clan target, L1Clan source, L1PcInstance sourceLeader,
                                    int capacity, Consumer<Boolean> result, String account) {
        if (target == null || source == null || target == source || sourceLeader == null
                || target.IsDismissed() || source.IsDismissed()
                || sourceLeader.getClanid() != source.getClanId()
                || source.getLeaderId() != sourceLeader.getId() || !sourceLeader.isCrown()
                || source.getHouseId() != 0 || source.getCastleId() != 0
                || source.getCurrentWar() != null || source.getAlliance() != 0
                || (capacity > 0 && target.getClanMemberList().size()
                        + source.getClanMemberList().size() > capacity))
            return false;
        final int sourceId = source.getClanId();
        final int targetId = target.getClanId();
        final String sourceName = source.getClanName(), targetName = target.getClanName();
        final List<L1Clan.ClanMember> roster = new ArrayList<>(source.getClanMemberList());
        final int targetCount = target.getClanMemberList().size();
        if (roster.isEmpty()) return false;
        final Set<Integer> sourceIds = new HashSet<>(roster.size() * 2);
        for (L1Clan.ClanMember member : roster)
            if (member == null || member.memberId <= 0 || !sourceIds.add(member.memberId)) return false;
        if (!sourceIds.contains(sourceLeader.getId())) return false;
        // The caller owns only its own CIO shard. Do not mutate another
        // connected player's clan fields from this shard. Offline members are
        // transferred in SQL and will load the destination on next login.
        for (L1Clan.ClanMember member : roster) {
            final L1PcInstance active = CWorld.getInstance().getPlayer(member.name);
            if (active != null && active != sourceLeader) {
                SC_SystemMessage.send(sourceLeader, "혈맹 합병 전에 다른 혈맹원은 접속을 종료해야 합니다.");
                return false;
            }
            // A delayed offline snapshot could otherwise write ClanID back to
            // the deleted source after this transaction commits. Nonblocking
            // failure is safer than waiting for DB on the game-owner thread.
            if (active == null && !CacheD.getInstance().flushCharacter(member.name, 0L)) {
                SC_SystemMessage.send(sourceLeader, "혈맹원 저장 작업이 완료된 후 다시 시도해 주세요.");
                return false;
            }
        }

        final ClanWarehouse warehouse = WarehouseManager.getInstance().getClanWarehouse(sourceName);
        synchronized (warehouse) {
            final CacheD cache = CacheD.getInstance();
            final int warehouseLane = warehouse.transferOrderKey();
            if (warehouse.getWarehouseUsingChar() != 0 || warehouse.getSize() != 0
                    || cache.hasPendingOwnerApply(warehouseLane)
                    || !cache.flushDbOwner(warehouseLane, 0L)) {
                SC_SystemMessage.send(sourceLeader, "혈맹 창고를 비우고 이용을 종료한 후 다시 시도해 주세요.");
                return false;
            }
            try (L1InventoryTransaction tx = account == null
                    ? L1InventoryTransaction.begin(sourceLeader, warehouseLane)
                    : L1InventoryTransaction.begin(sourceLeader, warehouseLane,
                            new int[]{CacheD.accountOrderKey(account)})) {
                tx.requireTables("characters", "clan_data", "clan_warehouse", "character_quests");
                tx.sql(con -> {
                    // Lock both rows in a stable order. DB, not a stale roster,
                    // decides whether either clan was replaced or gained a house.
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT clan_id,clan_name,leader_id,hashouse,hascastle,alliance "
                            + "FROM clan_data WHERE clan_id IN (?,?) ORDER BY clan_id FOR UPDATE")) {
                        ps.setInt(1, sourceId); ps.setInt(2, targetId);
                        boolean sourceFound = false, targetFound = false;
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                int id = rs.getInt(1);
                                if (id == sourceId) {
                                    if (sourceFound || !sourceName.equals(rs.getString(2))
                                            || rs.getInt(3) != sourceLeader.getId()
                                            || rs.getInt(4) != 0 || rs.getInt(5) != 0
                                            || rs.getInt(6) != 0)
                                        throw new SQLException("Source pledge no longer eligible for merge");
                                    sourceFound = true;
                                } else if (id == targetId) {
                                    if (targetFound || !targetName.equals(rs.getString(2)))
                                        throw new SQLException("Target pledge changed during merge");
                                    targetFound = true;
                                }
                            }
                        }
                        if (!sourceFound || !targetFound) throw new SQLException("Pledge row missing");
                    }
                    if (account != null && !CheckAccountPledge(con, account,
                            sourceLeader.getId(), sourceId, targetId, true))
                        throw new SQLException("Account pledge restriction changed before merge COMMIT");
                    // Do not drop the former pledge's items. Lock a matching row
                    // (when present) and require an empty warehouse at COMMIT.
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT id FROM clan_warehouse WHERE clan_name=? LIMIT 1 FOR UPDATE")) {
                        ps.setString(1, sourceName);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) throw new SQLException("Source pledge warehouse is not empty");
                        }
                    }
                    // Detect online/offline membership changes instead of moving a
                    // different DB roster than the one being replayed in the world.
                    int oldRows = 0, newRows = 0;
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT objid,ClanID FROM characters WHERE ClanID IN (?,?) "
                            + "ORDER BY objid FOR UPDATE")) {
                        ps.setInt(1, sourceId); ps.setInt(2, targetId);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                int id = rs.getInt(1), clanId = rs.getInt(2);
                                if (clanId == sourceId) {
                                    if (!sourceIds.contains(id))
                                        throw new SQLException("Source pledge membership changed");
                                    oldRows++;
                                } else if (clanId == targetId) newRows++;
                            }
                        }
                    }
                    final int allowed = capacity > 0 ? capacity : dbClanCapacity(con, target.getLeaderId());
                    if (oldRows != roster.size() || newRows != targetCount
                            || oldRows + newRows > allowed)
                        throw new SQLException("Pledge roster/capacity changed during merge");
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE characters SET ClanID=?,Clanname=?,ClanRank=?,notes='',Title='' WHERE ClanID=?")) {
                        ps.setInt(1, targetId);ps.setString(2, targetName);
                        ps.setInt(3, L1Clan.수련);ps.setInt(4, sourceId);
                        if (ps.executeUpdate() != roster.size())
                            throw new SQLException("Partial pledge member transfer");
                    }
                    try (PreparedStatement ps = con.prepareStatement(
                            "DELETE FROM clan_data WHERE clan_id=? AND clan_name=? AND leader_id=? "
                            + "AND hashouse=0 AND hascastle=0 AND alliance=0")) {
                        ps.setInt(1, sourceId); ps.setString(2, sourceName);
                        ps.setInt(3, sourceLeader.getId());
                        if (ps.executeUpdate() != 1) throw new SQLException("Source pledge DELETE failed");
                    }
                });
                tx.onFailure(() -> {
                    SC_SystemMessage.send(sourceLeader, "혈맹 합병을 완료하지 못했습니다. 다시 확인해 주세요.");
                    if (result != null) result.accept(false);
                });
                tx.commitAfterInventoryRelease(() -> {
                    // This runs on the existing CIO owner, only AFTER the
                    // durable commit. No offline character is restored/saved.
                    if (CWorld.getInstance().getClan(sourceId) != source
                            || CWorld.getInstance().getClan(targetId) != target)
                        throw new IllegalStateException("Stale clan merge replay after COMMIT");
                    // A concurrent login after admission cannot be applied
                    // safely by this shard. Fail closed and quarantine the
                    // durable owner rather than replay another owner's state.
                    for (L1Clan.ClanMember member : roster) {
                        final L1PcInstance live = CWorld.getInstance().getPlayer(member.name);
                        if (live != null && live != sourceLeader)
                            throw new IllegalStateException("Member logged in during committed clan merge: " + member.memberId);
                    }
                    for (L1Clan.ClanMember member : roster) {
                        L1PcInstance online = CWorld.getInstance().getPlayer(member.name);
                        if (online != null && online.getId() == member.memberId
                                && online.getClanid() == sourceId) {
                            online.setClanid(targetId);
                            online.setClanname(targetName);
                            online.setClanRank(L1Clan.수련);
                            online.setClanMemberNotes("");
                            online.setTitle("");
                        } else online = null;
                        target.addClanMember(member.name, L1Clan.수련, member.level, "",
                                member.memberId, member.type,
                                online == null ? 0 : online.getOnlineStatus(), online);
                        if (online != null) {
                            try {
                            SC_PacketBox.send(online, SC_PacketBox.MSG_RANK_CHANGED,
                                    L1Clan.수련, online.getName());
                            SC_ServerMessage.send(online, 95, targetName);
                            SC_ClanName.send(online, online, target.getEmblemId(), L1Clan.수련);
                            SC_ReturnedStat.sendEmblem(online, online.getId(), targetId);
                            SC_PacketBox.send(online, SC_PacketBox.PLEDGE_EMBLEM_STATUS,
                                    target.getEmblemStatus());
                            SC_ActionUI.send(online, targetName, L1Clan.수련);
                            } catch (RuntimeException failure) {
                                _log.log(Level.WARNING, "Committed pledge merge notification failed: " + member.memberId, failure);
                            }
                            giveClanBless(target, online);
                        }
                    }
                    // SQL has already deleted the old row. Do not issue a
                    // second asynchronous deletion of the former warehouse.
                    ClanTable.getInstance().forgetCommittedClanMerge(source);
                    if (!new File("emblem/" + sourceId).delete())
                        _log.fine("Old clan emblem absent or not removable: " + sourceId);
                    if (result != null) result.accept(true);
                });
                return true;
            } catch (RuntimeException failure) {
                _log.log(Level.WARNING, "Clan merge could not be admitted: " + sourceId, failure);
                SC_SystemMessage.send(sourceLeader, "혈맹 합병을 시작하지 못했습니다.");
                return false;
            }
        }
    }

    public boolean ClanJoin(L1Clan clan, L1PcInstance joinPc) throws Exception {
        return ClanJoin(clan, joinPc, null);
    }

    public boolean ClanJoin(L1Clan clan, L1PcInstance joinPc, Consumer<Boolean> result) throws Exception {
        return ClanJoin(clan, joinPc, result, null);
    }

    public boolean ClanJoinChecked(L1Clan clan, L1PcInstance joinPc, String account) throws Exception {
        if (account == null || account.isEmpty() || joinPc == null
                || !account.equals(joinPc.getAccountName())) return false;
        return ClanJoin(clan, joinPc, null, account);
    }

    private boolean ClanJoin(L1Clan clan, L1PcInstance joinPc, Consumer<Boolean> result,
            String account) throws Exception {
        if (clan == null || joinPc == null) return false;
        // Leader level and completed quest are resolved with the member SQL
        // under one CacheD receipt, never with two JDBC round trips on CIO.
        final L1Clan prior = joinPc.getClan();
        if (prior != null) {
            if (prior == clan) return false;
            return mergeClanAtomic(clan, prior, joinPc, -1, result, account);
        }
        return joinClanAtomic(clan, joinPc, -1, false, result, account);
    }

	private void giveClanBless(L1Clan clan, L1PcInstance joinPc) {
		try {
			if (clan == null || joinPc == null) {
				return;
			}
			int bless = clan.getBless();
			if (bless != 0 && !joinPc.hasSkillEffect(504 + bless)) {
				int time = clan.getBuffTime()[bless - 1];
				if (time <= 0) {
					time = 172800;
				}
				L1SkillUse.cast(
						joinPc,
						504 + bless,
						joinPc.getId(), joinPc.getX(),
						joinPc.getY(), null, time, L1SkillUse.TYPE_LOGIN);
				
			}
		} catch (Exception e) {
		}
	}

    public boolean ClanJoin(L1PcInstance pc, L1PcInstance joinPc) {
        return ClanJoin(pc, joinPc, null);
    }

    public boolean ClanJoin(L1PcInstance pc, L1PcInstance joinPc, Consumer<Boolean> result) {
        return ClanJoin(pc, joinPc, result, null);
    }

    public boolean ClanJoinChecked(L1PcInstance pc, L1PcInstance joinPc, String account) {
        if (account == null || account.isEmpty() || joinPc == null
                || !account.equals(joinPc.getAccountName())) return false;
        return ClanJoin(pc, joinPc, null, account);
    }

    private boolean ClanJoin(L1PcInstance pc, L1PcInstance joinPc, Consumer<Boolean> result,
            String account) {
        if (pc == null || joinPc == null) return false;
        int clan_id = pc.getClanid();
		String clanName = pc.getClanname();
		L1Clan clan = CWorld.getInstance().getClan(clan_id);
		if (clan != null) {
			int maxMember = 0;
			int charisma = 0;
			if (pc.getId() != clan.getLeaderId())
				charisma = pc.GetTotalStatCha();
			else
				charisma = pc.GetStatCha();

			boolean lv45quest = pc.getQuest().isEnd(CQuestSystem.QUEST_LEVEL45);
			if (pc.getLevel() >= 50) {
				maxMember = lv45quest ? charisma * 9 : charisma * 3;
			} else {
				maxMember = lv45quest ? charisma * 6 : charisma * 2;
			}
			if (Config.MAX_CLAN_MEMBER > 0) {
				maxMember = Config.MAX_CLAN_MEMBER;
			}

            if (joinPc.getClanid() == 0) {
                if (maxMember <= clan.getClanMemberList().size()) {
                    SC_ServerMessage.send(joinPc, 188, pc.getName());
                    return false;
                }
                return joinClanAtomic(clan, joinPc, maxMember, false, result, account);
			} else {
				if (Config.CLAN_ALLIANCE) {
					return changeClan(pc, joinPc, maxMember, result, account);
				} else {
					SC_ServerMessage.send(joinPc, 89);
					return false;
				}
			}
		} else {
			return false;
		}
	}

    private boolean changeClan(L1PcInstance pc, L1PcInstance joinPc, int maxMember, Consumer<Boolean> result, String account) {
        int clanId = pc.getClanid();
        L1Clan clan = CWorld.getInstance().getClan(clanId);
        int oldClanId = joinPc.getClanid();
        L1Clan oldClan = CWorld.getInstance().getClan(oldClanId);
        if (clan == null || oldClan == null || clan == oldClan || oldClanId == clanId) return false;
        if (joinPc.isCrown() && joinPc.getId() == oldClan.getLeaderId()) {
            return mergeClanAtomic(clan, oldClan, joinPc, maxMember, result, account);
        }
        return false;
    }

	public int getOfflineClanLeaderCha(int member) {
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement("SELECT Cha FROM characters WHERE objid=?")) {
			pstm.setInt(1, member);
			try (ResultSet rs = pstm.executeQuery()) {
				if (!rs.next()) return 0;
				return rs.getInt("Cha");
			}
		} catch (SQLException e) {
			_log.warning("could not check existing charname:" + e.getMessage());
		}
		return 0;
	}
	
	public LeaderInfo getOfflineClanLeaderInfo(int objid) {
		try (Connection con = DatabaseFactory.getInstance().getConnection()) {
			LeaderInfo info = new LeaderInfo();
			
			try (PreparedStatement pstm = con.prepareStatement("SELECT char_name, level, Cha FROM characters WHERE objid=?")) {
				pstm.setInt(1, objid);
				try (ResultSet rs = pstm.executeQuery()) {
					if (!rs.next()) return null;
					info.name = rs.getString("char_name");
					info.cha = rs.getInt("Cha");
					info.lvl = rs.getInt("level");
				}
			}
			
			try (PreparedStatement pstm = con.prepareStatement("SELECT * FROM character_quests WHERE char_id=? AND quest_id=?")) {
				pstm.setInt(1, objid);
				pstm.setInt(2, CQuestSystem.QUEST_LEVEL45);
				try (ResultSet rs = pstm.executeQuery()) {
					info.is45Quest = rs.next() && (rs.getInt("quest_step") == 0xff);
				}
			}
			return info;
		} catch (SQLException e) {
			_log.warning("could not check existing charname:" + e.getMessage());
		}
		return null;
	}
	
	class LeaderInfo {
		public String name;
		public int lvl;
		public int cha;
		public boolean is45Quest;
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(128);
			sb.append("NAME : ").append(name).append("\n");
			sb.append("LEVEL : ").append(lvl).append("\n");
			sb.append("CHARISMA : ").append(cha).append("\n");
			sb.append("QUESTSTATE : ").append(is45Quest).append("\n");
			return sb.toString();
		}
	}

    // Pledge lifecycle operations share the existing domain executor.
    public static void LeavePledge(
            CUserSocket client) {

        if (client == null) {
            return;
        }

        final L1PcInstance player =
                client.getActiveChar();

        if (player == null
                || player.isGhost()) {

            return;
        }

        final int clanId =
                player.getClanid();

        if (clanId == 0) {
            return;
        }

        final L1Clan clan =
                CWorld
                        .getInstance()
                        .getClan(clanId);

        if (clan == null) {
            return;
        }

        try {

            /*
             * 혈맹 군주:
             * 혈맹 자체 해산.
             */
            if (player.isCrown()
                    && player.getId()
                            == clan.getLeaderId()) {

                DisbandPledge(
                        clan,
                        player);

                return;
            }

            /*
             * 일반 혈맹원:
             * 본인만 탈퇴.
             */
            LeaveMember(
                    clan,
                    player);

        } catch (Exception e) {

            _log.log(
                    Level.SEVERE,
                    "CS_LeavePledge 실행 중 예외 발생",
                    e);
        }
    }

    private static void DisbandPledge(
            L1Clan clan,
            L1PcInstance player)
            throws Exception {

        final String playerName =
                player.getName();

        final String clanName =
                player.getClanname();

        /*
         * 혈맹 무인 로봇 존재 시 해산 불가.
         */
        if (player.hasSkillEffect(
                L1SkillId.무인혈맹)) {

            SC_PacketBox.send(player, SC_PacketBox.GREEN_MESSAGE, "혈맹무인로봇을 제거 후 혈맹해산이 가능합니다.");

            player.sendPackets(
                    "\\f3혈맹무인로봇을 제거 후 혈맹해산이 가능합니다.");

            return;
        }

        /*
         * 성/아지트 보유 상태.
         */
        if (clan.getCastleId() > 0
                || clan.getHouseId() > 0) {

            SC_ServerMessage.send(player, ServerMessage.HAVING_NEST_OF_CLAN);

            return;
        }

        /*
         * 전쟁 중.
         */
        if (clan.getCurrentWar() != null) {

            SC_ServerMessage.send(player, ServerMessage.CANNOT_BREAK_CLAN);

            return;
        }

        /*
         * 동맹 존재.
         */
        if (clan.getAlliance() > 0) {

            SC_ServerMessage.send(player, ServerMessage.CANNOT_BREAK_CLAN_HAVING_FRIENDS);

            return;
        }

        DeletePledge(clan, player, playerName, clanName);
    }

    /** Reserve one dismissal and all roster/vault lanes. No game-owner JDBC. */
    private static void DeletePledge(L1Clan clan, L1PcInstance player,
            String playerName, String clanName) {
        final int clanId = clan.getClanId(), leaderId = player.getId();
        final CWorld world = CWorld.getInstance();
        final ClanTable table = ClanTable.getInstance();
        final CUserSocket leaderSocket = player.getNetConnection();
        final int leaderStamp = player.getSessionStamp();
        synchronized (clan) {
            if (world.getClan(clanId) != clan || player.getClanid() != clanId
                    || player.isLogoutStarted() || !player.isCrown()
                    || leaderId != clan.getLeaderId() || clan.getHouseId() != 0
                    || clan.getCastleId() != 0 || clan.getAlliance() != 0
                    || clan.getCurrentWar() != null || !clan.TryBeginDismiss()) return;
            boolean submitted = false;
            try {
                final ClanWarehouse vault = WarehouseManager.getInstance().getClanWarehouse(clanName);
                if (vault == null) throw new IllegalStateException("Pledge vault unavailable");
                synchronized (vault) {
                    if (vault.getWarehouseUsingChar() != 0 || vault.getSize() != 0)
                        throw new RejectedExecutionException("Pledge vault occupied");
                    final L1Clan.ClanMember[] roster =
                            clan.getClanMemberList().toArray(new L1Clan.ClanMember[0]);
                    final int n = roster.length;
                    if (n == 0) throw new IllegalStateException("Empty pledge roster");
                    final int[] ids = new int[n], shards = new int[n], stamps = new int[n];
                    final String[] names = new String[n];
                    final boolean[] online = new boolean[n];
                    final L1PcInstance[] live = new L1PcInstance[n];
                    final int[] owners = new int[n]; // n-1 member IDs + pledge lane
                    int ownerCount = 0;
                    boolean foundLeader = false;
                    final CIOThreadCallback cio = CIOThreadCallback.getInstance();
                    for (int i = 0; i < n; i++) {
                        final L1Clan.ClanMember member = roster[i];
                        final int id = member.memberId;
                        if (id <= 0) throw new IllegalStateException("Invalid pledge member ID");
                        ids[i] = id;
                        names[i] = member.name;
                        if (id == leaderId) foundLeader = true;
                        else owners[ownerCount++] = id;
                        if (world.findObject(id) instanceof L1PcInstance pc) {
                            final CUserSocket socket = pc.getNetConnection();
                            if (pc.getClanid() != clanId || pc.isLogoutStarted()
                                    || socket == null || !socket.isGameSessionReady()
                                    || world.getPlayer(pc.getName()) != pc)
                                throw new RejectedExecutionException("Pledge member session changing");
                            live[i] = pc;
                            online[i] = true;
                            stamps[i] = pc.getSessionStamp();
                            shards[i] = cio.ownerShardIndex(pc);
                            if (shards[i] < 0)
                                throw new RejectedExecutionException("Pledge member owner unavailable");
                        }
                    }
                    if (!foundLeader || ownerCount != n - 1)
                        throw new IllegalStateException("Pledge leader missing from roster");
                    owners[ownerCount] = CacheD.accountOrderKey("clan-join:", clanId);
                    final int[] sqlIds = ids.clone();
                    java.util.Arrays.sort(sqlIds);
                    for (int i = 1; i < n; i++)
                        if (sqlIds[i] == sqlIds[i - 1])
                            throw new IllegalStateException("Duplicate pledge member ID");
                    try (CacheD.WarehouseMove tx = CacheD.beginPledgeDismissMove(
                            leaderId, vault.transferOrderKey(), owners, ids, names, online)) {
                        // A member can disconnect after the roster was captured.
                        // Once the lanes are reserved, later logouts wait for this receipt.
                        for (int i = 0; i < live.length; i++) {
                            final L1PcInstance pc = live[i];
                            if (pc == null) continue;
                            final CUserSocket socket = pc.getNetConnection();
                            if (pc.isLogoutStarted() || pc.isItemTransferBusy() || pc.getSessionStamp() != stamps[i]
                                    || socket == null || !socket.isGameSessionReady()
                                    || socket.getActiveChar() != pc)
                                throw new RejectedExecutionException("Pledge member went offline before reservation");
                        }
                        ClanTable.stageDeletePledge(tx, clan, sqlIds);
                        tx.onError(failure -> {
                            // A confirmed SQL rejection can reopen the pledge.
                            // Unknown COMMIT or replay failures must stay fenced.
                            if (failure instanceof SQLException
                                    && !(failure instanceof l1j.server.server.storage.cached.CTransaction.OutcomeUnknownException))
                                clan.CancelDismiss();
                            _log.log(Level.SEVERE, "Pledge dismissal persistence failed: " + clanId, failure);
                            cio.completeSession(leaderSocket, () -> {
                                if (player.getSessionStamp() == leaderStamp && !player.isLogoutStarted())
                                    SC_SystemMessage.send(player, "혈맹 해산을 완료하지 못했습니다.");
                            });
                        });
                        tx.commit(() -> ReplyDeletePledge(tx, world, table, clan, vault,
                                roster, live, shards, stamps, playerName, clanName));
                        submitted = true;
                    }
                }
            } catch (RuntimeException failure) {
                if (!submitted) clan.CancelDismiss();
                _log.log(Level.WARNING, "Pledge dismissal not admitted: " + clanId, failure);
                SC_SystemMessage.send(player, "혈맹원 저장 작업과 혈맹 창고 상태를 확인한 후 다시 시도해 주세요.");
            }
        }
    }

    /** DB COMMIT is confirmed; clear each live PC on its existing owner.
     * Keep all reserved lanes until every owner and the world reply finish. */
    private static void ReplyDeletePledge(CacheD.WarehouseMove tx, CWorld world,
            ClanTable table, L1Clan clan, ClanWarehouse vault,
            L1Clan.ClanMember[] roster, L1PcInstance[] live, int[] shards, int[] stamps,
            String playerName, String clanName) {
        final CIOThreadCallback cio = CIOThreadCallback.getInstance();
        final int clanId = clan.getClanId();
        int count = 1; // dispatch sentinel prevents early finalization
        for (L1PcInstance pc : live) if (pc != null) count++;
        final java.util.concurrent.atomic.AtomicInteger remaining =
                new java.util.concurrent.atomic.AtomicInteger(count);
        final java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();
        final Consumer<Throwable> done = failure -> {
            if (failure != null) error.compareAndSet(null, failure);
            if (remaining.decrementAndGet() != 0) return;
            final Throwable first = error.get();
            if (first != null) {
                _log.log(Level.SEVERE, "Committed pledge dismissal replay failed: " + clanId, first);
                tx.finishOwnerApply(first);
                return;
            }
            cio.completeWorldMutation(world, () -> {
                if (ClanTable.getInstance() != table || world.getClan(clanId) != clan)
                    throw new IllegalStateException("Stale pledge dismissal world reply");
                for (L1Clan.ClanMember member : roster) clan.RemoveDbMember(member.memberId);
                table.forgetCommittedClanDisband(clan, vault);
                clan.CommitDismiss();
                try {
                    if (!new File("emblem/" + clanId).delete())
                        _log.fine("Pledge emblem absent or not removable: " + clanId);
                } catch (RuntimeException notification) {
                    _log.log(Level.WARNING, "Pledge emblem cleanup failed: " + clanId, notification);
                }
            }, tx::finishOwnerApply);
        };
        for (int i = 0; i < live.length; i++) {
            final L1PcInstance pc = live[i];
            if (pc == null) continue;
            final int stamp = stamps[i];
            cio.completeOnShard(shards[i], () -> {
                if (world != CWorld.getInstance() || pc.getSessionStamp() != stamp)
                    throw new IllegalStateException("Stale pledge member dismissal reply");
                pc.lockUserState();
                try {
                    if (pc.getClanid() == clanId) pc.ClearCommittedClanData();
                    else if (pc.getClanid() != 0)
                        throw new IllegalStateException("Pledge member changed after COMMIT");
                } finally { pc.unlockUserState(); }
                if (pc.isLogoutStarted() || world.findObject(pc.getId()) != pc) return;
                try {
                    if (!pc.getName().equalsIgnoreCase(playerName))
                        SC_ServerMessage.send(pc, 269, playerName, clanName, null);
                    pc.NotifyClanDataCleared();
                } catch (RuntimeException notification) {
                    _log.log(Level.WARNING, "Pledge dismissal notification failed: " + pc.getId(), notification);
                }
            }, failure -> {
                try {
                    if (failure != null && pc.getSessionStamp() == stamp) {
                        final CUserSocket socket = pc.getNetConnection();
                        if (socket != null) socket.close();
                    }
                } finally { done.accept(failure); }
            });
        }
        done.accept(null);
    }

    /** No packet, buff or membership change is published before SQL COMMIT. */
    public static void LeaveMember(L1Clan clan, L1PcInstance player) {
        LeaveMember(clan, player, null);
    }

    public static void LeaveMember(L1Clan clan, L1PcInstance player, Runnable afterCommitted) {
        if (clan == null || clan.IsDismissed() || player == null || player.getClanid() != clan.getClanId()
                || player.getId() == clan.getLeaderId()) return;
        final int oldClanId = clan.getClanId();
        final String playerName = player.getName();
        final String clanName = clan.getClanName();
        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                l1j.server.server.model.inventory.L1InventoryTransaction.begin(player,
                        l1j.server.server.storage.CacheD.accountOrderKey("$clan-membership"))) {
            tx.requireTables("characters");
            tx.sql(con -> {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE characters SET ClanID=0,Clanname='',ClanRank=0,Title='',notes='' "
                        + "WHERE objid=? AND ClanID=?")) {
                    ps.setInt(1, player.getId());
                    ps.setInt(2, oldClanId);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("Clan member leave before-image changed");
                }
            });
            tx.onFailure(() ->
                    l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.send(
                            player, "혈맹 탈퇴를 완료하지 못했습니다."));
            tx.commitAfterInventoryRelease(() -> {
                if (player.getClanid() != oldClanId || player.getId() == clan.getLeaderId()
                        || CWorld.getInstance().getClan(oldClanId) != clan)
                    throw new IllegalStateException("Clan leave memory preimage changed after COMMIT");
                // Make the membership state authoritative before any observable effect.
                player.ClearCommittedClanData();
                clan.removeClanMember(player.getId());
                if (player.isClanBuff()) {
                    player.setClanBuff(false);
                    try {
                        player.killSkillEffectTimer(L1SkillId.CLANBUFF_YES);
                        SC_PacketBox.send(player, SC_PacketBox.UNLIMITED_ICON1, 450, false);
                    } catch (RuntimeException failure) {
                        _log.log(Level.WARNING, "Committed pledge leave buff cleanup failed: " + player.getId(), failure);
                    }
                }
                try {
                    player.NotifyClanDataCleared();
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(
                            clan.getOnlineClanMember(), SC_ServerMessage.nativeWriter(178, playerName, clanName, null));
                } catch (RuntimeException failure) {
                    _log.log(Level.WARNING, "Committed pledge leave notification failed: " + player.getId(), failure);
                }
                if (afterCommitted != null) afterCommitted.run();
            });
        } catch (RuntimeException error) {
            _log.log(Level.WARNING, "Clan leave receipt not admitted: " + playerName, error);
            l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.send(
                    player, "혈맹 탈퇴를 시작하지 못했습니다.");
        }
    }

    public static void BanMember(
            CUserSocket client,
            String targetName) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        if (targetName == null
                || targetName.isEmpty()) {

            return;
        }

        /*
         * 군주 본인 추방 방지.
         */
        if (targetName.equalsIgnoreCase(
                pc.getName())) {

            return;
        }

        /*
         * 원본은 clanId lookup이 아니라
         * clanName 기반 findClan().
         * semantics 그대로 유지.
         */
        final L1Clan clan =
                CWorld
                        .getInstance()
                        .findClan(
                                pc.getClanname());

        if (clan == null || pc.getClanid() != clan.getClanId()) {
            return;
        }

        /*
         * 군주 권한 검증.
         */
        if (!pc.isCrown()
                || pc.getId()
                        != clan.getLeaderId()) {

            SC_ServerMessage.send(pc, 518);

            return;
        }

        final int castleId =
                clan.getCastleId();

        /*
         * 공성 중 추방 불가.
         */
        if (castleId != 0
                && CastleWarBusiness
                        .getInstance()
                        .isNowWar(castleId)) {

            SC_ServerMessage.send(pc, 439);

            return;
        }

        /*
         * 먼저 온라인 캐릭터 검색.
         */
        final L1PcInstance target =
                CWorld
                        .getInstance()
                        .getPlayer(
                                targetName);

        if (target != null) {

            BanOnlineMember(
                    pc,
                    clan,
                    target,
                    targetName);

            return;
        }

        /*
         * 오프라인 혈맹원 ID 기반 추방.
         */
        BanOfflineMember(
                client,
                pc,
                clan,
                targetName);
    }

    private static boolean CanBanMember(L1PcInstance leader, L1Clan clan,
            L1PcInstance target, int leaderStamp, int targetStamp) {
        if (clan.IsDismissed() || leader == null || target == null || leader.isLogoutStarted() || target.isLogoutStarted()
                || leader.getSessionStamp() != leaderStamp || target.getSessionStamp() != targetStamp
                || leader.isGhost() || !leader.isCrown()
                || leader.getClanid() != clan.getClanId() || leader.getId() != clan.getLeaderId()
                || target.getClanid() != clan.getClanId() || target.getId() == clan.getLeaderId()
                || clan.GetDbMember(target.getId()) == null
                || CWorld.getInstance().getClan(clan.getClanId()) != clan
                || CWorld.getInstance().findObject(leader.getId()) != leader
                || CWorld.getInstance().findObject(target.getId()) != target) return false;
        int castle = clan.getCastleId();
        return castle == 0 || !CastleWarBusiness.getInstance().isNowWar(castle);
    }

    private static void BanOnlineMember(L1PcInstance pc, L1Clan clan,
            L1PcInstance target, String targetName) {
        final int leaderStamp = pc.getSessionStamp(), targetStamp = target.getSessionStamp();
        final int clanId = clan.getClanId(), targetId = target.getId(), leaderId = pc.getId();
        final String clanName = clan.getClanName();
        if (!CanBanMember(pc, clan, target, leaderStamp, targetStamp)) {
            SC_ServerMessage.send(pc, 109, targetName);
            return;
        }
        final CUserSocket targetSocket = target.getNetConnection();
        CIOThreadCallback.getInstance().completeSession(targetSocket, () -> {
            if (!CanBanMember(pc, clan, target, leaderStamp, targetStamp)) return;
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(target,
                    CacheD.accountOrderKey("clan-join:", clanId))) {
                tx.requireTables("characters", "clan_data");
                tx.sql(con -> {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT leader_id FROM clan_data WHERE clan_id=? AND clan_name=? FOR UPDATE")) {
                        ps.setInt(1, clanId);
                        ps.setString(2, clanName);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next() || rs.getInt(1) != leaderId || leaderId == targetId)
                                throw new SQLException("Pledge ban authority changed");
                        }
                    }
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE characters SET ClanID=0,Clanname='',ClanRank=0,Title='',notes='' "
                            + "WHERE objid=? AND ClanID=?")) {
                        ps.setInt(1, targetId);
                        ps.setInt(2, clanId);
                        if (ps.executeUpdate() != 1)
                            throw new SQLException("Pledge ban before-image changed");
                    }
                });
                tx.onFailure(() -> {
                    if (pc.getSessionStamp() == leaderStamp && !pc.isLogoutStarted())
                        SC_SystemMessage.send(pc, "혈맹원 추방 저장에 실패했습니다.");
                });
                tx.commitAfterInventoryRelease(() -> {
                    if (target.getClanid() != clanId
                            || CWorld.getInstance().getClan(clanId) != clan)
                        throw new IllegalStateException("Stale committed pledge ban");
                    target.ClearCommittedClanData();
                    clan.removeClanMember(targetId);
                    try {
                        target.NotifyClanDataCleared();
                        SC_ServerMessage.send(target, 238, clanName);
                    } catch (RuntimeException failure) {
                        _log.log(Level.WARNING, "Committed pledge ban notification failed: " + targetId, failure);
                    }
                    if (pc.getSessionStamp() == leaderStamp && !pc.isLogoutStarted()
                            && pc.getClanid() == clanId) {
                        try {
                            SC_PacketBox.send(pc, pc, SC_PacketBox.PLEDGE_REFRESH_MINUS);
                            SC_ServerMessage.send(pc, 240, target.getName());
                        } catch (RuntimeException failure) {
                            _log.log(Level.WARNING, "Committed pledge ban leader notification failed: " + leaderId, failure);
                        }
                    }
                });
            } catch (RuntimeException failure) {
                _log.log(Level.WARNING, "Pledge ban receipt admission failed: " + targetId, failure);
                if (pc.getSessionStamp() == leaderStamp && !pc.isLogoutStarted())
                    SC_SystemMessage.send(pc, "혈맹원 추방을 시작하지 못했습니다.");
            }
        }, failure -> {
            if (failure != null && pc.getSessionStamp() == leaderStamp && !pc.isLogoutStarted()) {
                SC_SystemMessage.send(pc, "혈맹원 추방 대상의 접속 상태가 변경되었습니다.");
            }
        });
    }

    /** CPledgeDb::OustPledgeMember: persist by database ID, not a restored PC. */
    private static void BanOfflineMember(
            CUserSocket client, L1PcInstance pc, L1Clan clan, String targetName) {
        final l1j.server.server.templates.L1CharName known =
                CharacterTable.getInstance().getCharName(targetName);
        if (known == null || known.getId() <= 0) {
            SC_ServerMessage.send(pc, 109, targetName);
            return;
        }
        final int targetId = known.getId(), clanId = clan.getClanId();
        final int leaderId = pc.getId(), leaderStamp = pc.getSessionStamp();
        final String clanName = clan.getClanName();
        L1PcInstance online = null;
        try {
            // Login publication uses the same short pledge monitor. No JDBC or
            // waiting for owner work while holding it.
            synchronized (clan) {
                if (clan.IsDismissed() || client.getActiveChar() != pc || pc.isLogoutStarted() || pc.isGhost()
                        || !pc.isCrown() || pc.getClanid() != clanId
                        || leaderId != clan.getLeaderId()
                        || CWorld.getInstance().getClan(clanId) != clan) return;
                final int castle = clan.getCastleId();
                if (castle != 0 && CastleWarBusiness.getInstance().isNowWar(castle)) {
                    SC_ServerMessage.send(pc, 439);
                    return;
                }
                final L1Clan.ClanMember member = clan.GetDbMember(targetId);
                if (targetId == leaderId || member == null) {
                    SC_ServerMessage.send(pc, 109, targetName);
                    return;
                }
                if (CWorld.getInstance().findObject(targetId) instanceof L1PcInstance live) {
                    online = live;
                } else {
                    final int lane = CacheD.accountOrderKey("clan-join:", clanId);
                    try (CacheD.WarehouseMove tx =
                            CacheD.beginOfflinePledgeMove(targetId, lane, member.name)) {
                        tx.stageWarehouseSql(con -> {
                            l1j.server.server.storage.cached.CTransaction.requireTransactionalTables(
                                    con, "characters", "clan_data");
                            try (PreparedStatement ps = con.prepareStatement(
                                    "SELECT leader_id FROM clan_data WHERE clan_id=? AND clan_name=? FOR UPDATE")) {
                                ps.setInt(1, clanId);
                                ps.setString(2, clanName);
                                try (ResultSet rs = ps.executeQuery()) {
                                    if (!rs.next() || rs.getInt(1) != leaderId || leaderId == targetId)
                                        throw new SQLException("Pledge oust authority changed");
                                }
                            }
                            try (PreparedStatement ps = con.prepareStatement(
                                    "UPDATE characters SET ClanID=0,Clanname='',ClanRank=0,Title='',notes='' "
                                    + "WHERE objid=? AND ClanID=?")) {
                                ps.setInt(1, targetId);
                                ps.setInt(2, clanId);
                                if (ps.executeUpdate() != 1)
                                    throw new SQLException("Pledge oust before-image changed");
                            }
                        });
                        tx.onError(failure -> {
                            _log.log(Level.WARNING, "Offline pledge oust failed: " + targetId, failure);
                            CIOThreadCallback.getInstance().completeSession(client, () -> {
                                if (client.getActiveChar() == pc && pc.getSessionStamp() == leaderStamp
                                        && !pc.isLogoutStarted())
                                    SC_SystemMessage.send(pc, "혈맹원 추방 저장에 실패했습니다.");
                            });
                        });
                        tx.commit(() -> {
                            // The CacheD character lane stays fenced until the
                            // committed roster is visible, even if the leader logs out.
                            Throwable replayFailure = null;
                            try {
                                if (CWorld.getInstance().getClan(clanId) != clan
                                        || clan.GetDbMember(targetId) != member)
                                    throw new IllegalStateException("Stale committed pledge oust");
                                clan.removeClanMember(targetId);
                            } catch (Throwable failure) {
                                replayFailure = failure;
                                _log.log(Level.SEVERE, "Committed pledge oust replay failed: " + targetId, failure);
                            } finally {
                                tx.finishOwnerApply(replayFailure);
                            }
                            if (replayFailure != null) return;
                            CIOThreadCallback.getInstance().completeSession(client, () -> {
                                if (client.getActiveChar() == pc && pc.getSessionStamp() == leaderStamp
                                        && !pc.isLogoutStarted() && pc.getClanid() == clanId) {
                                    SC_PacketBox.send(pc, pc, SC_PacketBox.PLEDGE_REFRESH_MINUS);
                                    SC_ServerMessage.send(pc, 240, member.name);
                                }
                            });
                        });
                    }
                    return;
                }
            }
        } catch (RuntimeException rejected) {
            _log.log(Level.WARNING, "Offline pledge oust not admitted: " + targetId, rejected);
            SC_SystemMessage.send(pc, "혈맹원 추방 처리가 지연되고 있습니다. 다시 시도해 주세요.");
            return;
        }
        BanOnlineMember(pc, clan, online, targetName);
    }
}
