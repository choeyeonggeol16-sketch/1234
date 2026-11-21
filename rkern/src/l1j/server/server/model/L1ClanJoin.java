package l1j.server.server.model;
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
import l1j.server.server.model.Warehouse.ClanWarehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;

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
            ps.setInt(2, L1Quest.QUEST_LEVEL45);
            try (ResultSet rs = ps.executeQuery()) {
                quest45 = rs.next() && rs.getInt(1) == 0xff;
            }
        }
        if (charisma <= 0) throw new SQLException("Clan leader charisma invalid");
        return charisma * (quest45 ? 3 : 1) * (level >= 50 ? 3 : 2);
    }

    private boolean joinClanAtomic(L1Clan clan, L1PcInstance joinPc, int capacity,
                                   boolean tutorial, Consumer<Boolean> result) {
        if (clan == null || joinPc == null || joinPc.getClanid() != 0) return false;
        final int clanId = clan.getClanId(), characterId = joinPc.getId();
        final String clanName = clan.getClanName();
        final int rank = L1Clan.수련;
        if (capacity > 0 && capacity <= clan.getClanMemberList().size()) return false;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(joinPc,
                CacheD.accountOrderKey("clan-join:" + clanId))) {
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
                if (L1World.getInstance().getClan(clanId) != clan || joinPc.getClanid() != 0)
                    throw new IllegalStateException("Stale clan admission after COMMIT");
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(clan.getOnlineClanMember(),
                        SC_ServerMessage.nativeWriter(94,joinPc.getName()));
                joinPc.setClanid(clanId);
                joinPc.setClanname(clanName);
                joinPc.setClanRank(rank);
                joinPc.setClanMemberNotes("");
                joinPc.setTitle(tutorial ? Config.GAME_SERVER_NAME : "");
                SC_CharTitle.send(joinPc, joinPc.getId(), joinPc.getTitle());
                SC_CharTitle.broadcast(joinPc, joinPc.getId(), joinPc.getTitle());
                clan.addClanMember(joinPc.getName(), rank, joinPc.getLevel(), "",
                        characterId, joinPc.getType(), joinPc.getOnlineStatus(), joinPc);
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
                if (!tutorial) {
                    giveClanBless(clan, joinPc);
                }
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
        if (target == null || source == null || target == source || sourceLeader == null
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
            final L1PcInstance active = L1World.getInstance().getPlayer(member.name);
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
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(sourceLeader, warehouseLane)) {
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
                    if (L1World.getInstance().getClan(sourceId) != source
                            || L1World.getInstance().getClan(targetId) != target)
                        throw new IllegalStateException("Stale clan merge replay after COMMIT");
                    // A concurrent login after admission cannot be applied
                    // safely by this shard. Fail closed and quarantine the
                    // durable owner rather than replay another owner's state.
                    for (L1Clan.ClanMember member : roster) {
                        final L1PcInstance live = L1World.getInstance().getPlayer(member.name);
                        if (live != null && live != sourceLeader)
                            throw new IllegalStateException("Member logged in during committed clan merge: " + member.memberId);
                    }
                    for (L1Clan.ClanMember member : roster) {
                        L1PcInstance online = L1World.getInstance().getPlayer(member.name);
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
                            SC_PacketBox.send(online, SC_PacketBox.MSG_RANK_CHANGED,
                                    L1Clan.수련, online.getName());
                            SC_ServerMessage.send(online, 95, targetName);
                            SC_ClanName.send(online, online, target.getEmblemId(), L1Clan.수련);
                            SC_ReturnedStat.sendEmblem(online, online.getId(), targetId);
                            SC_PacketBox.send(online, SC_PacketBox.PLEDGE_EMBLEM_STATUS,
                                    target.getEmblemStatus());
                            SC_ActionUI.send(online, targetName, L1Clan.수련);
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
        if (clan == null || joinPc == null) return false;
        // Leader level and completed quest are resolved with the member SQL
        // under one CacheD receipt, never with two JDBC round trips on CIO.
        final L1Clan prior = joinPc.getClan();
        if (prior != null) {
            if (prior == clan) return false;
            return mergeClanAtomic(clan, prior, joinPc, -1, result);
        }
        return joinClanAtomic(clan, joinPc, -1, false, result);
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
        if (pc == null || joinPc == null) return false;
        int clan_id = pc.getClanid();
		String clanName = pc.getClanname();
		L1Clan clan = L1World.getInstance().getClan(clan_id);
		if (clan != null) {
			int maxMember = 0;
			int charisma = 0;
			if (pc.getId() != clan.getLeaderId())
				charisma = pc.getAbility().getTotalCha();
			else
				charisma = pc.getAbility().getCha();

			boolean lv45quest = pc.getQuest().isEnd(L1Quest.QUEST_LEVEL45);
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
                return joinClanAtomic(clan, joinPc, maxMember, false, result);
			} else {
				if (Config.CLAN_ALLIANCE) {
					return changeClan(pc, joinPc, maxMember, result);
				} else {
					SC_ServerMessage.send(joinPc, 89);
					return false;
				}
			}
		} else {
			return false;
		}
	}

    private boolean changeClan(L1PcInstance pc, L1PcInstance joinPc, int maxMember, Consumer<Boolean> result) {
        int clanId = pc.getClanid();
        L1Clan clan = L1World.getInstance().getClan(clanId);
        int oldClanId = joinPc.getClanid();
        L1Clan oldClan = L1World.getInstance().getClan(oldClanId);
        if (clan == null || oldClan == null || clan == oldClan || oldClanId == clanId) return false;
        if (joinPc.isCrown() && joinPc.getId() == oldClan.getLeaderId()) {
            return mergeClanAtomic(clan, oldClan, joinPc, maxMember, result);
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
				pstm.setInt(2, L1Quest.QUEST_LEVEL45);
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
}