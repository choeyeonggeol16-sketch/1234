package l1j.server.server.model.network.core.cs.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import l1j.server.Config;
import l1j.server.server.Account;
import l1j.server.server.task.TaskLane;
import l1j.server.server.model.npc.spawn.boss.BossMonsterSpawnList;
import l1j.server.server.datatables.CFriendsDb;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CFriends;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CReplyChecker;
import l1j.server.server.model.inventory.CTrade;
import l1j.server.server.model.L1HauntedHouse;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.L1PolyMorph;
import l1j.server.server.model.L1Racing;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.combat.statistics.Kda;
import l1j.server.server.model.character.combat.statistics.KdaLoader;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.survey.SurveySystemLoader;
import l1j.server.server.model.item.Card.L1CardAdd;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ClanAttention;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Resurrection;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_Trade;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.model.world.siege.War;
import l1j.server.server.model.world.siege.WarFactory;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.shared.geometry.GamePoint;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.model.npc.spawn.boss.L1Boss;
import l1j.server.server.templates.L1Npc;
public final class CS_Attr {

	public static final int MSGCODE_622_BOSS 				= 1;
	public static final int MSGCODE_622_KDINIT				= 3;
	public static final int MSGCODE_622_Name				= 6;
	public static final int MSGCODE_622_Card				= 7;
	public static final int MSGCODE_622_Card2				= 8;
	public static final int MSGCODE_622_Card3				= 9;
	public static final int MSGCODE_622_Tree				= 10;

	public static final int MSGCODE_NO 			= 0;
	public static final int MSGCODE_YES 			= 1;


	private CS_Attr() {
	}

    private static boolean unavailable(L1PcInstance pc) {
        return pc == null || pc.isItemTransferBusy() || pc.isTradeResolving() || pc.getTradeID() != 0;
    }
    private static void confirmationFailed(L1PcInstance pc, RuntimeException failure) {
        java.util.logging.Logger.getLogger(CS_Attr.class.getName()).log(
                java.util.logging.Level.WARNING, "Confirmation economy preparation failed", failure);
        SC_SystemMessage.send(pc, "처리를 완료하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private static void broadcastGazeAdded(L1Clan clan, String opposingName) {
        final List<String> gaze = clan.getGazeList();
        final var announcement = SC_ClanAttention.nativeMentWriter(true, opposingName);
        final var roster = SC_ClanAttention.nativeListWriter(gaze.size(), gaze);
        try (var announcementFrame = announcement == null ? null
                     : new CAsyncSendConstBuffer(announcement, Integer.MIN_VALUE, Integer.MAX_VALUE);
             var rosterFrame = roster == null ? null
                     : new CAsyncSendConstBuffer(roster, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            for (L1PcInstance member : clan.getOnlineClanMember()) {
                CSharedBroadcast.sendRecipient(announcementFrame, member);
                CSharedBroadcast.sendRecipient(rosterFrame, member);
            }
        }
    }

    /** Cost and persisted K/D image are committed together. Positive PVP events
     * that arrive while SQL is pending remain as deltas after the reset.
     */
    public static void resetKillDeath(L1PcInstance pc, L1ItemInstance item,
            boolean resetKill, boolean resetDeath) {
        if (unavailable(pc) || item == null || (!resetKill && !resetDeath)) return;
        final Kda kda = pc.getKDA();
        if (kda == null || kda.isBot || pc.getInventory().GetByServerID(item.getId()) != item) return;
        final KdaLoader loader = KdaLoader.getInstance();
        try {
            synchronized (loader) {
                // Queue the current authoritative counters before reserving the
                // same existing global lane; no JDBC or commit wait here.
                final int beforeKill, beforeDeath;
                synchronized (kda) {
                    loader.store();
                    beforeKill = kda.kill; beforeDeath = kda.death;
                }
                if (beforeKill < 0 || beforeDeath < 0
                        || ((!resetKill || beforeKill == 0) && (!resetDeath || beforeDeath == 0))) return;
                try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, KdaLoader.orderKey())) {
                    if (!tx.debit(item, 1)) return;
                    tx.requireTables("tb_kda");
                    tx.sql(con -> resetKillDeathRow(con, pc.getId(), beforeKill, beforeDeath, resetKill, resetDeath));
                    tx.commit(() -> {
                        synchronized (kda) {
                            if (pc.getKDA() != kda || kda.kill < beforeKill || kda.death < beforeDeath)
                                throw new IllegalStateException("K/D reset memory preimage changed");
                            if (resetKill) kda.kill -= beforeKill;
                            if (resetDeath) kda.death -= beforeDeath;
                        }
                        loader.storeCommittedReset(kda);
                        SC_SystemMessage.send(pc, "킬/데스 초기화가 완료되었습니다.");
                    });
                }
            }
        } catch (RuntimeException failure) { confirmationFailed(pc, failure); }
    }
    public static void resetKillDeathRow(Connection con, int owner, int beforeKill, int beforeDeath,
            boolean resetKill, boolean resetDeath) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE tb_kda SET killing=?, death=? WHERE objid=? AND killing=? AND death=?")) {
            ps.setInt(1, resetKill ? 0 : beforeKill); ps.setInt(2, resetDeath ? 0 : beforeDeath);
            ps.setInt(3, owner); ps.setInt(4, beforeKill); ps.setInt(5, beforeDeath);
            if (ps.executeUpdate() != 1) throw new SQLException("K/D reset before-image changed: " + owner);
        }
    }
    private static void recoverExperience(L1PcInstance pc, boolean scroll) {
        if (unavailable(pc) || Config.STANDBY_SERVER || pc.getExpRes() != 1) return;
        final int before = pc.getExp(), level = pc.getLevel(), owner = pc.getId();
        final int delta = (int) (ExpTable.getNeedExpNextLevel(level) * (scroll ? 0.05 : 0.025));
        final int target = (int) Math.min(ExpTable.MAX_EXP, (long) before + Math.max(0, delta));
        if (target == before) return;
        if (ExpTable.getLevelByExp(target) != level) {
            SC_SystemMessage.send(pc, "레벨이 변경되는 경험치 복구는 현재 지원하지 않습니다. 비용은 차감되지 않았습니다.");
            return;
        }
        long cost = (long) level * level * (level < 45 ? 50 : 150);
        if (pc.getLawful() >= 0) cost = (long) (cost * 0.7);
        if (cost <= 0 || cost > Integer.MAX_VALUE) return;
        try {
            // Serialize the current character state before the economy receipt;
            // construct the immutable save directly, without pc.save's other I/O.
            CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc));
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                    CacheD.accountOrderKey("experience-recovery:" + owner))) {
                if (!tx.debit(scroll ? 3000049 : L1ItemId.ADENA, scroll ? 1 : (int) cost)) {
                    SC_ServerMessage.send(pc, 189); return;
                }
                tx.requireTables("characters");
                tx.sql(con -> recoverExperienceRow(con, owner, level, before, target));
                tx.commit(() -> {
                    if (pc.getExp() != before || pc.getExpRes() != 1 || pc.getLevel() != level)
                        throw new IllegalStateException("Experience recovery memory preimage changed");
                    pc.setExp(target); pc.setExpRes(0);
                    // Same-level packet/buff refresh only. The old levelUp path
                    // has extra random stats/quest/item mutations outside receipt.
                    pc.onChangeExp();
                });
            }
        } catch (RuntimeException failure) { confirmationFailed(pc, failure); }
    }
    public static void recoverExperienceRow(Connection con, int owner, int level, int before, int target)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE characters SET Exp=?, ExpRes=0 WHERE objid=? AND level=? AND Exp=? AND ExpRes=1")) {
            ps.setInt(1, target); ps.setInt(2, owner); ps.setInt(3, level); ps.setInt(4, before);
            if (ps.executeUpdate() != 1) throw new SQLException("Experience recovery before-image changed: " + owner);
        }
    }

	public static void handle(
			CUserSocket client,
			int attrcode,
			int msgIdx,
			int c,
			String name) {

		if (client == null) return;

		final L1PcInstance pc = client.getActiveChar();
		if (pc == null) return;

		if (attrcode == 622) {
			switch(msgIdx){
			case 2: // Retired dialog ID. Do not consume another pending confirmation.
				return;
			case MSGCODE_622_KDINIT:
                L1ItemInstance resetItem = pc.getKillDeathInitializeItem();
                pc.setKillDeathInitializeItem(null);
                if (c == MSGCODE_YES) resetKillDeath(pc, resetItem, true, true);
                return;
			case MSGCODE_622_Name:
				if (c == MSGCODE_YES) {
					L1ItemInstance item = pc.getNameInstance();
					if (item != null) {
						int[] MALE_LIST = new int[] { 0, 61, 138, 734, 2786, 6658, 6671, 12490 };
						int[] FEMALE_LIST = new int[] { 1, 48, 37, 1186, 2796, 6661, 6650, 12494 };
						if (pc.get_sex() == 0) {
							pc.set_sex(1);
							pc.setClassId(FEMALE_LIST[pc.getType()]);
						} else {
							pc.set_sex(0);
							pc.setClassId(MALE_LIST[pc.getType()]);
						}
						pc.setCurrentSprite(pc.getClassId());
						pc.sendShape(pc.getClassId());
						pc.getInventory().DeleteItemAmount(item, 1);
					}
					TaskLane.SESSION.scheduleRequired(new Runnable(){
						@Override
						public void run(){
							CUserSocket clnt = pc.getNetConnection();
							CS_CharSelect.restartProcess(pc);
                            CS_CommonClick.sendCharAmountAndPacks(clnt);							
						}
					}, 500L);
				}
				pc.setNameInstance(null);
				return;
            // Confirmation packets must use the same durable item/account receipt
            // as direct item use. Never mutate Account or debit inventory here.
            case MSGCODE_622_Card2:
                if (c == MSGCODE_YES) L1CardAdd.legend1(pc);
                else pc.setCollecItem(null);
                return;
            case MSGCODE_622_Card3:
                if (c == MSGCODE_YES) L1CardAdd.myth(pc);
                else pc.setCollecItem(null);
                return;
            case MSGCODE_622_Card:
                if (c == MSGCODE_YES) L1CardAdd.hero1(pc);
                else pc.setCollecItem(null);
                return;
            case MSGCODE_622_Tree:
                if (c == MSGCODE_YES) L1CardAdd.SkillTree(pc);
                else pc.setCollecItem(null);
                return;
			case MSGCODE_622_BOSS: {
				if (c == MSGCODE_NO) {
					if (pc.getLevel() < Config.YN_pclevel) {
						SC_SystemMessage.send(pc, "레벨 " + Config.YN_pclevel + " 부터 이용가능 합니다.");
						return;
					}
					SC_SystemMessage.send(pc, "보스이동 참가를 거절 하셨습니다.");
					pc.setBossYN(0);
				} else if (c == MSGCODE_YES) {
					if (pc.getLevel() < Config.YN_pclevel) {
						SC_SystemMessage.send(pc, "레벨 "+Config.YN_pclevel+" 부터 이용가능 합니다.");
						return;
					}

					L1Boss boss = BossMonsterSpawnList.find(pc.getBossYN());
					if (boss == null)
						SC_SystemMessage.send(pc, "선택 딜레이가 지나 이용이 불가능 합니다(10초)");
					else {
						L1Map m	= L1WorldMap.getInstance().getMap((short)boss.getMap());
						int x = boss.getX();
						int y = boss.getY();
						int cx = 0;
						int cy = 0;
						int current_try = 0;
						int limit_try = 100;
						do{
							cx = x + (GameRandom.isBoolean() ? GameRandom.next(5) : -GameRandom.next(5));
							cy = y + (GameRandom.isBoolean() ? GameRandom.next(5) : -GameRandom.next(5));
						}while(++current_try < limit_try && !GamePoint.isValidPosition(m, cx, cy));
						if(current_try >= limit_try){
							cx = x;
							cy = y;
						}
						pc.start_teleport(cx, cy, (short) boss.getMap(), pc.getHeading(), 169, true, false);
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "해당 보스구역으로 이동 되었습니다.");
					}
				}
				break;
			}
			}

			if(SurveySystemLoader.getInstance().isSurvey(msgIdx)){
				SurveySystemLoader.getInstance().submitSurvey(pc, msgIdx, c == 1);
				return;
			}
		}

		switch (attrcode) {
        case 6008:
            if (msgIdx == 48) {
                l1j.server.server.packets.Protocol.MainServer.Indun.Proto.GludioDungeonPackets.handleInviteResponse(pc,c==MSGCODE_YES);
            }
            return;
		case 180:
			boolean ring = false;
			if (name != null && name.startsWith("maple")) {
				name = name.replace("maple ", "");
			}

			if (name != null && name.equalsIgnoreCase("ranking class polymorph")) {
				if (pc.getRankLevel() < 3)
					return;
				switch (pc.getType()) {
				case 0: name = pc.get_sex() == 0 ? "rangking prince male" : "rangking prince female"; break;
				case 1: name = pc.get_sex() == 0 ? "rangking knight male" : "rangking knight female"; break;
				case 2: name = pc.get_sex() == 0 ? "rangking elf male" : "rangking elf female"; break;
				case 3: name = pc.get_sex() == 0 ? "rangking wizard male" : "rangking wizard female"; break;
				case 4: name = pc.get_sex() == 0 ? "rangking darkelf male" : "rangking darkelf female"; break;
				case 5: name = pc.get_sex() == 0 ? "rangking dragonknight male" : "rangking dragonknight female"; break;
				case 6: name = pc.get_sex() == 0 ? "rangking illusionist male" : "rangking illusionist female"; break;
				case 7: name = pc.get_sex() == 0 ? "rangking warrior male" : "rangking warrior female"; break;
				}
			}

			if (name != null && name.length() > 0) {
				L1PolyMorph poly = PolyTable.getInstance().getTemplate(name);
				if (poly != null || name.equals("")) {
					if (name.equals("")) {
						int spriteId = pc.getCurrentSpriteId();
						if (spriteId == 6034 || spriteId == 6035) {
						} else {
							if (pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
								pc.removeSkillEffect(L1SkillId.POLY_RING_MASTER);
							}
							pc.removeSkillEffect(L1SkillId.SHAPE_CHANGE);
						}
					} else if (poly.getMinLevel() <= pc.getLevel() || pc.isGm()) {
						if (pc.isPolyRingMaster()) {
							ring = true;
						}
						L1PolyMorph.doPoly(pc, poly.getPolyId(), 7200, L1PolyMorph.MORPH_BY_ITEMMAGIC, ring);
					}
				}
			}
			break;
		case 97:
            CReplyChecker.FinishWaitingReplyByReplier(pc, msgIdx, c);
            break;
		case 3348:
			if (c == 1) {
				final L1Object requester = L1World.getInstance().findObject(pc.getTempID());
				pc.setTempID(0);
				if (!(requester instanceof L1PcInstance gazePc)) return;
				L1Clan targetClan = L1World.getInstance().findClan(gazePc.getClanname());
				if (targetClan == null) return;

				L1Clan pcClan = L1World.getInstance().findClan(pc.getClanname());
				if (pcClan == null) return;

				targetClan.addGazelist(pcClan.getClanName());
				pcClan.addGazelist(targetClan.getClanName());

				broadcastGazeAdded(pcClan, targetClan.getClanName());
				broadcastGazeAdded(targetClan, pcClan.getClanName());
			}
			break;
		case 217:
		case 221:
		case 222:
			L1PcInstance enemyLeader = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			if (enemyLeader == null) return;
			pc.setTempID(0);
			String clanName = pc.getClanname();
			String enemyClanName = enemyLeader.getClanname();
			if (c == 0) {
				if (attrcode == 217) {
					SC_ServerMessage.send(enemyLeader, 236, clanName);
				} else if (attrcode == 221 || attrcode == 222) {
					SC_ServerMessage.send(enemyLeader, 237, clanName);
				}
			} else if (c == 1) {
				L1Clan defense = L1World.getInstance().findClan(clanName);
				L1Clan offense = L1World.getInstance().findClan(enemyClanName);
				if(defense == null || offense == null) return;

				if (attrcode == 217) {
					War war = WarFactory.createNormalWar(defense);
					war.register(offense);
				} else{
					War war = offense.getCurrentWar();
					if(war == null) return;
					war.notifySurrender(defense, offense);
					war.dispose();
				}
			}
			break;
		case 4703:
            // The housekeeper has already disabled opening/entering this dungeon
            // during renewal. A forged confirmation must not bypass that policy
            // or debit clan funds (including a cancellation packet).
            SC_SystemMessage.send(pc, "리뉴얼 공사로인해 이용 불가능합니다.");
            break;
        case 252: {
            CTrade trade = pc.GetTrade();
            if (trade != null) {
                trade.Request(pc, msgIdx, c);
            } else if (msgIdx == 0 && c == 1) {
                // Preserve the legacy NPC-only dialog, not a stale player reply.
                int npcId = 0;
                if (pc.getMapId() == 4 && pc.getY() == 32851) {
                    if (pc.getX() == 33507) npcId = 400064;
                    else if (pc.getX() == 33515) npcId = 300027;
                }
                L1Npc npc = npcId == 0 ? null : CObjectDB.GetNPC(npcId);
                if (npc != null) SC_Trade.send(pc, npc.get_name());
            }
            break;
        }
        case 321:
			L1PcInstance resusepc1 = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			if (resusepc1 != null) {
				if (c == 1) {
					SC_SkillSound.send(pc, pc.getId(), '\346');
					SC_SkillSound.broadcast(pc, pc.getId(), '\346');
					pc.resurrect(pc.getMaxHp() / 2);
					pc.setCurrentHp(pc.getMaxHp() / 2);
					TileOccupancy.getInstance().tryOccupyNow(pc.getMapId(), pc.getX(), pc.getY(), pc.getId());
					SC_Resurrection.send(pc, pc, resusepc1, 0);
					SC_Resurrection.broadcast(pc, pc, resusepc1, 0);
					SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
					SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc));
				}
			}
			break;
		case 322:
			L1PcInstance resusepc2 = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			if (resusepc2 != null) {
				if (c == 1) {
					SC_SkillSound.send(pc, pc.getId(), '\346');
					SC_SkillSound.broadcast(pc, pc.getId(), '\346');
					pc.resurrect(pc.getMaxHp());
					pc.setCurrentHp(pc.getMaxHp());
					TileOccupancy.getInstance().tryOccupyNow(pc.getMapId(), pc.getX(), pc.getY(), pc.getId());
					SC_Resurrection.send(pc, pc, resusepc2, 0);
					SC_Resurrection.broadcast(pc, pc, resusepc2, 0);
					SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
					SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc));
					if (pc.getExpRes() == 1 && pc.isGres() && pc.isGresValid()) {
						pc.resExp();
						pc.setExpRes(0);
						pc.setGres(false);
					}
				}
			}
			break;
		case 325:
			pc.setTempID(0);
			break;
		case 512:
			int houseId = pc.getTempID();
			pc.setTempID(0);
			if (name == null || name.isEmpty() || name.length() > 16) {
				SC_ServerMessage.send(pc, 513);
			} else {
				L1Clan ownClan = pc.getClan();
                if (!pc.isCrown() || ownClan == null || ownClan.getLeaderId() != pc.getId()
                        || ownClan.getHouseId() != houseId) {
                    SC_SystemMessage.send(pc, "아지트 이름 변경 권한이 없습니다.");
                } else {
                    l1j.server.server.model.world.residence.L1AuctionTransaction.renameHouse(pc, houseId, name);
                }
			}
			break;
		case 622:
			CFriendsDb buddyTable = CFriendsDb.getInstance();
			CFriends buddyList = buddyTable.GetFriends(pc.getId());
			L1PcInstance target2 = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			String name2 = pc.getName();
			if (c == 0) {
				if (target2 != null) {
					SC_SystemMessage.send(target2, pc.getName() + "님이 친구 요청을 거절하였습니다.");
				} else if (pc.isSpecialBuff()) {
					pc.setSpecialBuff(false);
				}
			} else if (c == 1) {
				if (target2 != null) {
					buddyList.AddFriend(pc.getId(), name2);
					buddyTable.EstablishFriendship(target2.getId(), pc.getId(), name2);
					SC_SystemMessage.send(target2, pc.getName() + "님이 친구 등록 되었습니다.");
					SC_SystemMessage.send(pc, target2.getName() + "님에게 친구 등록이 되었습니다.");
				} else if (pc.getRaidGame()) {
					pc.setRaidGame(false);
				} 
			}
			break;
		case 630:
            CReplyChecker.FinishWaitingReplyByReplier(pc, attrcode, msgIdx, c);
            break;
		case 653:
			if (c == 1) {
				pc.setPartnerId(0);
				pc.save();
			}
			break;
		case 654:
			L1PcInstance partner = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
			pc.setTempID(0);
			if (partner != null) {
				if (c == 0) {
					SC_ServerMessage.send(partner, 656, pc.getName());
				} else if (c == 1) {
					pc.setPartnerId(partner.getId());
					pc.save();
					SC_ServerMessage.send(pc, 790);
					SC_ServerMessage.send(pc, 655, partner.getName());

					partner.setPartnerId(pc.getId());
					partner.save();
					SC_ServerMessage.send(partner, 790);
					SC_ServerMessage.send(partner, 655, pc.getName());
				}
			}
			break;
		case 729:
			if (c == 1) {
				callClan(pc);
			}
			break;
		case 738:
            if (c == MSGCODE_YES) recoverExperience(pc, false);
            break;
        case 2551:
            if (c == MSGCODE_YES) recoverExperience(pc, true);
            break;
		case 951:
		case 953:
		case 954:
            CReplyChecker.FinishWaitingReplyByReplier(pc, attrcode, msgIdx, c);
            break;
		case 1256:
			switch (c) {
			case 0:
				if (L1Racing.getInstance().contains(0, pc) && pc.getMapId() != 5143) {
					L1Racing.getInstance().remove(0, pc);
				} else if (L1HauntedHouse.getInstance().isMember(pc) && pc.getMapId() != 5140) {
					L1HauntedHouse.getInstance().removeMember(pc);
				}
				break;
			case 1:
				if (L1Racing.getInstance().contains(0, pc) && pc.getMapId() != 5143) {
					if (L1Racing.getInstance().getGameStatus() == L1Racing.STATUS_NONE
							|| L1Racing.getInstance().getGameStatus() == L1Racing.STATUS_READY) {
						Random random = new Random(System.nanoTime());
						int locx = 32767 + random.nextInt(2);
						int locy = 32848 + random.nextInt(2);
						
						L1SkillUse.cast(
								pc,
								L1SkillId.CANCELLATION,
								pc.getId(),
								pc.getX(),
								pc.getY(),
								null,
								0,
								L1SkillUse.TYPE_LOGIN);
						
						pc.start_teleport(locx, locy, 5143, 5, 169, true, false);
					}
				} else if (L1HauntedHouse.getInstance().isMember(pc) && pc.getMapId() != 5140) {
					if (L1HauntedHouse.getInstance().getHauntedHouseStatus() == L1HauntedHouse.STATUS_NONE
							|| L1HauntedHouse.getInstance().getHauntedHouseStatus() == L1HauntedHouse.STATUS_READY) {
						if (pc.isInParty()) {
							pc.getParty().leaveMember(pc);
						}
						L1SkillUse.cast(
								pc,
								L1SkillId.CANCELLATION,
								pc.getId(),
								pc.getX(),
								pc.getY(),
								null,
								0,
								L1SkillUse.TYPE_LOGIN);
						
						L1PolyMorph.doPoly(pc, 6284, 600, L1PolyMorph.MORPH_BY_LOGIN, false);
						pc.start_teleport(32722, 32830, 5140, 2, 169, true, false);
					}
				}
				break;
			}
			break;
		case 2923:
			if (c == 0) {
				SC_ChatPacket.sendDirect(pc, pc, "드래곤 포탈 입장이 취소되었습니다.");
			} else if (c == 1) {
				if (pc.DragonPortalLoc[0] != 0) {
					Collection<L1PcInstance> templist = L1World.getInstance().getAllPlayers();
					L1PcInstance[] list = templist.toArray(new L1PcInstance[templist.size()]);
					int count = 0;
					for (L1PcInstance player : list) {
						if (player == null) continue;
						if (player.getMapId() == pc.DragonPortalLoc[2]) {
							count += 1;
						}
					}
					if (count >= 32) {
						SC_ServerMessage.send(pc, 1536);
						return;
					}
					pc.start_teleport(pc.DragonPortalLoc[0], pc.DragonPortalLoc[1], pc.DragonPortalLoc[2], 5, 169, true, false);
				}
			}
			pc.DragonPortalLoc[0] = 0;
			pc.DragonPortalLoc[1] = 0;
			pc.DragonPortalLoc[2] = 0;
			break;
		case 479:
			if(c == 1) {
				try{
					pc.onStat(name);
				}catch(Exception e){
					System.out.println(String.format("%s님이 스텟오버를 시도했습니다.", pc.getName()));
				}
			}
			break;
		default:
			break;
		}
	}


	

	private static void callClan(L1PcInstance pc) {
		L1PcInstance callClanPc = (L1PcInstance) L1World.getInstance().findObject(pc.getTempID());
		pc.setTempID(0);
		if (callClanPc == null) {
			return;
		}
		if (!pc.getMap().isEscapable() && !pc.isGm()) {
			SC_ServerMessage.send(pc, 647);
			pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, false);
			return;
		}
		if (pc.getId() != callClanPc.getCallClanId()) {
			return;
		}

		boolean isInWarArea = false;
		int castleId = L1CastleLocation.getCastleIdByArea(callClanPc);
		if (castleId != 0) {
			isInWarArea = true;
			if (CastleWarBusiness.getInstance().isNowWar(castleId)) {
				isInWarArea = false;
			}
		}
		short mapId = callClanPc.getMapId();
		if (mapId != 0 && mapId != 4 && mapId != 304 || isInWarArea) {
			pc.sendPackets("군주님께서 현재 소환 할 수없는 곳에 계셔서 마법이 실패 하였습니다.");
			callClanPc.sendPackets("군주님의 위치에서는 혈원을 소환 할 수 없습니다.");
			return;
		}

		L1Map map = callClanPc.getMap();
		int callCalnX = callClanPc.getX();
		int callCalnY = callClanPc.getY();
		int locX = 0;
		int locY = 0;
		int heading = 0;
		switch (callClanPc.getCallClanHeading()) {
		case 0: locX = callCalnX; locY = callCalnY - 1; heading = 4; break;
		case 1: locX = callCalnX + 1; locY = callCalnY - 1; heading = 5; break;
		case 2: locX = callCalnX + 1; locY = callCalnY; heading = 6; break;
		case 3: locX = callCalnX + 1; locY = callCalnY + 1; heading = 7; break;
		case 4: locX = callCalnX; locY = callCalnY + 1; heading = 0; break;
		case 5: locX = callCalnX - 1; locY = callCalnY + 1; heading = 1; break;
		case 6: locX = callCalnX - 1; locY = callCalnY; heading = 2; break;
		case 7: locX = callCalnX - 1; locY = callCalnY - 1; heading = 3; break;
		default: break;
		}

		boolean isExistCharacter = false;
        L1Character cha = null;
        for (L1Object object : L1World.getInstance().getClusterGrid().queryNearby(callClanPc, 1)) {
            if (object instanceof L1Character) {
                cha = (L1Character) object;
                if (cha.getX() == locX && cha.getY() == locY && cha.getMapId() == mapId) {
                    isExistCharacter = true;
                    break;
                }
            }
        }

		if (locX == 0 && locY == 0 || !map.isPassable(locX, locY) || isExistCharacter) {
			SC_ServerMessage.send(pc, 627);
			return;
		}
		pc.start_teleport(locX, locY, mapId, heading, 2235, true, false);
	}

}
