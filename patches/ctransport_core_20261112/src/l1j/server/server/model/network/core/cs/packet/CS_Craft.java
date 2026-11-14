package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.IndunSystem.MiniGame.L1Gambling;
import l1j.server.IndunSystem.MiniGame.L1Gambling3;
import l1j.server.MJCaptchaSystem.Loader.MJCaptchaLoadManager;
import l1j.server.MJSurveySystem.MJSurveyFactory;
import l1j.server.MJSurveySystem.MJSurveySystemLoader;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.Stadium.StadiumManager;
import l1j.server.server.DungeonTimeCommand;
import l1j.server.server.GMCommands;
import l1j.server.server.SkillMsgMute;
import l1j.server.server.UserCommands;
import l1j.server.server.datatables.BuddyTable;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanBuffTable;
import l1j.server.server.datatables.ClanBuffTable.ClanBuff;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.SpamTable;
import l1j.server.server.model.L1Buddy;
import l1j.server.server.model.L1ChatParty;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1ClanRanking;
import l1j.server.server.model.L1ClanRanking.RankData;
import l1j.server.server.model.L1ExcludingList;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.monitor.Logger.ChatType;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.packets.Protocol.Packet.SC_CharStat;
import l1j.server.server.packets.Protocol.Packet.SC_SlotChange;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.model.network.core.sc.packet.SC_NewChat;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Pledge;
import l1j.server.server.model.network.core.sc.packet.SC_RankingClan;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1CharName;
import l1j.server.server.utils.StatContext;

public final class CS_Craft {
    private static final java.util.logging.Logger _log =
            java.util.logging.Logger.getLogger(CS_Craft.class.getName());

	private static final int PACKET_CHAT = 0x02;
	private static final int PACKET_EXCLUDE = 0x1F;
	private static final int PACKET_NEW_STAT = 228;
	private static final int PACKET_SEAL = 0x39;
	private static final int PACKET_CLAN_BUFF = 140;
	private static final int PACKET_CLAN_RANKING = 146;
	private static final int PACKET_EQUIP_SWAP = 33;
	private static final int PACKET_CLAN_BUFF_CHANGE = 0xF9;
	private static final int PACKET_CLAN_BUFF_CHOICE = 0xF8;

	private CS_Craft() {
	}

	public static void handle(CIOClient client, int type, int p1, int p2, int p3, long l0, long l1, String s0, String s1) {
		if (client == null) {
			return;
		}

		final L1PcInstance pc = client.getActiveChar();

		if (type != PACKET_NEW_STAT && pc == null) {
			return;
		}

		switch (type) {
		case PACKET_CLAN_BUFF_CHANGE:
			handleClanBuffChange(pc);
			return;
		case PACKET_CLAN_BUFF_CHOICE:
			handleClanBuffChoice(pc, p1, p2);
			return;
		case PACKET_EQUIP_SWAP:
			handleEquipSwap(pc, p1, p2);
			return;
		case PACKET_CLAN_BUFF:
			handleClanBuff(pc, p1);
			return;
		case PACKET_SEAL:
			handleItemSeal(pc, p1);
			return;
		case PACKET_CLAN_RANKING:
			handleClanRanking(pc);
			return;
		case PACKET_NEW_STAT: {
			StatContext context = null;

			if (p3 >= 0) {
				context = new StatContext(p1, p2);
				final int mask = p3;

				for (int i = 0; i < 6; i++) {
					if ((mask & (1 << i)) == 0) {
						continue;
					}
					final int stat = (int) ((l0 >>> (i << 3)) & 0xFFL);
					context.setStat(i, stat);
				}
			}
			handleNewStat(pc, client, context);
			return;
		}
		case PACKET_EXCLUDE:
			handleExclude(pc, p1, p2, s0);
			return;
		case PACKET_CHAT:
			handleChat(pc, client, p1, s0, s1);
			return;
		default:
			return;
		}
	}

	private static void handleClanBuffChange(L1PcInstance pc) {
        if (pc == null || pc.getClan() == null) return;
        final L1Clan clan = pc.getClan();
        synchronized (clan) {
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, clan.getClanId())) {
            final ClanBuffImage before = new ClanBuffImage(clan), after = new ClanBuffImage(clan);
            if (!tx.debit(40308, 300000)) return;
            L1Clan draft = new L1Clan();
            draft.setBuffFirst(before.first); draft.setBuffSecond(before.second); draft.setBuffThird(before.third);
            after.selection = 0;
            after.first = ClanBuffTable.getRandomBuff(draft); draft.setBuffFirst(after.first);
            after.second = ClanBuffTable.getRandomBuff(draft); draft.setBuffSecond(after.second);
            after.third = ClanBuffTable.getRandomBuff(draft);
            stageClanBuff(tx, clan, before, after);
            tx.commit(() -> {
                after.install(clan);
                l1j.server.server.model.network.core.sc.packet.SC_EinhasadClanBuff.send(pc, pc);
            });
        } catch (RuntimeException failure) {
            _log.log(java.util.logging.Level.WARNING, "Clan buff reroll not admitted", failure);
        }
    
        }
    }

	private static void handleClanBuffChoice(L1PcInstance pc, int buffNum, int buffCheck) {
        if (pc == null || pc.getClan() == null || buffCheck < 1 || buffCheck > 3) return;
        final L1Clan clan = pc.getClan();
        synchronized (clan) {
        final ClanBuff buff = ClanBuffTable.getBuffList(buffNum);
        if (buff == null) return;
        if (buffCheck == 1 && buffNum != clan.getBuffFirst() && buffNum != clan.getBuffSecond()
                && buffNum != clan.getBuffThird()) return;
        if (buffCheck == 2 && !pc.getMap().isEscapable()) {
            SC_SystemMessage.send(pc, "이곳에서는 사용할 수 없습니다.");
            SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, clan.getClanId())) {
            final ClanBuffImage before = new ClanBuffImage(clan), after = new ClanBuffImage(clan);
            int fee = buffCheck == 1 ? 0 : buffCheck == 2 ? 1000 : 500000;
            if (fee > 0 && !tx.debit(40308, fee)) return;
            if (buffCheck != 2) after.selection = buffNum;
            stageClanBuff(tx, clan, before, after);
            tx.commit(() -> {
                after.install(clan);
                if (buffCheck == 2) {
                    SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
                    pc.start_teleport(buff.teleportX, buff.teleportY, (short) buff.teleportM,
                            pc.getHeading(), 169, true, false);
                } else l1j.server.server.model.network.core.sc.packet.SC_EinhasadClanBuff.send(pc, pc);
            });
        } catch (RuntimeException failure) {
            _log.log(java.util.logging.Level.WARNING, "Clan buff choice not admitted", failure);
        }
    
        }
    }

	private static void handleEquipSwap(L1PcInstance pc, int equipType, int slot) {
		if (pc == null || (slot != 0 && slot != 1)
                || (equipType != 16 && equipType != 8)) return;
        if (pc.getTradeID() != 0 || pc.isTradeResolving()) return;
        final L1PcInventory inventory = pc.getInventory();
        if (!inventory.tryBeginEquipmentChange()) {
            SC_SystemMessage.send(pc, "아이템 처리 중입니다. 잠시 후 다시 시도하세요.");
            return;
        }
        try {
            if (equipType == 16) {
                pc.getChangeSlot(slot);
                // Announce only after the admitted swap completes. applySlot
                // updates the selected slot, including an empty/no-op preset.
                SC_SlotChange.sendCreate(pc, SC_SlotChange.SLOT_CHANGE, slot);
            } else {
                pc.addSlotItem(slot, 0, true);
            }
        } finally {
            inventory.endEquipmentChange();
        }
	}

	private static void handleClanBuff(L1PcInstance pc, int buffId) {
        if (pc == null || pc.getClan() == null || buffId < 0 || buffId >= 4) return;
        final L1Clan clan = pc.getClan();
        synchronized (clan) {
        if (!pc.isGm() && !pc.isCrown() && pc.getClanRank() != 9 && pc.getClanRank() != 3) {
            SC_ServerMessage.send(pc, 4648); return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, clan.getClanId())) {
            final ClanBuffImage before = new ClanBuffImage(clan), after = new ClanBuffImage(clan);
            final int fee = before.times[buffId] > 0 ? 10000000 : 300000000;
            if (!pc.isGm() && before.points < fee) { SC_ServerMessage.send(pc, 4620); return; }
            after.bless = buffId + 1;
            after.times[buffId] = before.times[buffId] > 0 ? before.times[buffId] : 172800;
            if (!pc.isGm()) after.points -= fee;
            stageClanBuff(tx, clan, before, after);
            tx.commit(() -> {
                after.install(clan);
                for (L1PcInstance member : clan.getOnlineClanMember()) {
                    if (member == null) continue;
                    final int stamp = member.getSessionStamp();
                    l1j.server.server.model.network.thread.CIOThreadCallback.getInstance()
                            .completeWarehouseMove(member, stamp, () -> {
                        if (member.getClanid() != clan.getClanId()) return;
                        if (before.bless != 0 && member.hasSkillEffect(504 + before.bless)) {
                            member.removeSkillEffect(504 + before.bless);
                            SC_ActionUI2.send(member, 2723 + before.bless, 1, 0, 7231 + before.bless * 2, 0);
                        }
                        SC_Pledge.sendBless(member, clan, after.bless);
                        L1SkillUse.cast(member, buffId + 505, member.getId(), member.getX(), member.getY(),
                                null, after.times[buffId], L1SkillUse.TYPE_GMBUFF);
                    }, failure -> { if (failure != null) _log.fine("Clan member buff notification expired"); });
                }
            });
        } catch (RuntimeException failure) {
            _log.log(java.util.logging.Level.WARNING, "Clan buff activation not admitted", failure);
        }
    
        }
    }

    private static final class ClanBuffImage {
        int selection, first, second, third, bless, points;
        final int[] times;
        ClanBuffImage(L1Clan clan) {
            selection = clan.getEinhasadBlessBuff(); first = clan.getBuffFirst();
            second = clan.getBuffSecond(); third = clan.getBuffThird();
            bless = clan.getBless(); points = clan.getBlessCount(); times = clan.getBuffTime().clone();
        }
        void install(L1Clan clan) {
            clan.setEinhasadBlessBuff(selection); clan.setBuffFirst(first);
            clan.setBuffSecond(second); clan.setBuffThird(third);
            clan.setBless(bless); clan.setBlessCount(points);
            for (int i=0; i<4; i++) clan.setBuffTime(i, times[i]);
        }
    }

    private static void stageClanBuff(L1InventoryTransaction tx, L1Clan clan,
            ClanBuffImage before, ClanBuffImage after) {
        final int clanId = clan.getClanId();
        tx.requireTables("clan_data");
        tx.sql(con -> {
            try (PreparedStatement check = con.prepareStatement(
                    "SELECT EinhasadBlessBuff,Buff_List1,Buff_List2,Buff_List3,bless,bless_count "
                    + "FROM clan_data WHERE clan_id=? FOR UPDATE")) {
                check.setInt(1, clanId);
                try (java.sql.ResultSet rows = check.executeQuery()) {
                    if (!rows.next() || rows.getInt(1)!=before.selection || rows.getInt(2)!=before.first
                            || rows.getInt(3)!=before.second || rows.getInt(4)!=before.third
                            || rows.getInt(5)!=before.bless || rows.getInt(6)!=before.points)
                        throw new SQLException("Clan buff preimage changed");
                }
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE clan_data SET EinhasadBlessBuff=?,"
                    + "Buff_List1=?,Buff_List2=?,Buff_List3=?,bless=?,bless_count=?,"
                    + "attack=?,defence=?,pvpattack=?,pvpdefence=? WHERE clan_id=?")) {
                ps.setInt(1,after.selection);ps.setInt(2,after.first);ps.setInt(3,after.second);
                ps.setInt(4,after.third);ps.setInt(5,after.bless);ps.setInt(6,after.points);
                for(int i=0;i<4;i++)ps.setInt(7+i,after.times[i]);
                ps.setInt(11,clanId);
                if (ps.executeUpdate()>1) throw new SQLException("Duplicate clan identity");
            }
        });
    }

	private static void handleItemSeal(L1PcInstance pc, int sealItemObjId) {
		try {
			// Refuse before touching the target or charging the fee.  Inventory
			// update/save gates run too late to undo a direct setBless call.
			if (pc == null || pc.isItemTransferBusy() || pc.getTradeID() != 0) return;
			final L1ItemInstance targetItem = pc.getInventory().getItem(sealItemObjId);

			if (targetItem == null || targetItem.getItem().getType2() == 0) {
				SC_ServerMessage.send(pc, 79);
				return;
			}

            if (targetItem.isEquipped()) {
                pc.sendPackets("장비를 해제한 후 봉인해 주세요.");
                return;
            }

			if (targetItem.getItem().isEndedTimeMessage()) {
				SC_ServerMessage.send(pc, 79);
				return;
			}

			final int sealFee = 100000;
			if (!pc.getInventory().checkItem(40308, sealFee)) {
				pc.sendPackets("아데나 100,000원이 부족합니다.");
				return;
			}

			if (targetItem.getBless() == 0 || targetItem.getBless() == 1 || targetItem.getBless() == 3) {
				int bless = 0;
				switch (targetItem.getBless()) {
				case 0: bless = 128; break;
				case 1: bless = 129; break;
				case 3: bless = 131; break;
				}

                final int newBless = bless;
                try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                        CacheD.accountOrderKey("seal:" + pc.getId()))) {
                    if (!tx.debit(40308, sealFee)) return;
                    tx.change(targetItem, prepared -> prepared.setBless(newBless));
                    tx.commit(() -> {
                        int status = targetItem.isIdentified() ? 47 : 46;
                        SC_PacketBox.send(pc, SC_PacketBox.ITEM_STATUS, targetItem, status);
                        pc.sendPackets("봉인해제시 .봉인해제신청 명령어를 사용하세요.");
                    });
                }
			} else {
				SC_ServerMessage.send(pc, 79);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void handleClanRanking(L1PcInstance pc) {
		final RankData[] allDatas = L1ClanRanking.getInstance().getRankerDatas();
		SC_RankingClan.send(pc, allDatas);
	}

	private static void handleNewStat(L1PcInstance pc, CIOClient client, StatContext ctx) {
		/*
		 * 원본 그대로.
		 */
		if (pc == null || !ctx.resolve(pc)) {
			return;
		}
		SC_CharStat.sendCreate(client, client, ctx);
	}

	private static void handleExclude(L1PcInstance pc, int type, int subType, String charName) {
		if (pc == null) {
			return;
		}

		final L1ExcludingList exList = SpamTable.getInstance().getExcludeTable(pc.getId());

		if (type == 0) {
			SC_PacketBox.send(pc, SC_PacketBox.SHOW_LIST_EXCLUDE, exList.getExcludeList(0), 0);
			SC_PacketBox.send(pc, SC_PacketBox.SHOW_LIST_EXCLUDE, exList.getExcludeList(1), 1);
			return;
		}

		if (charName == null || charName.isEmpty()) {
			return;
		}

		if (charName.equalsIgnoreCase(pc.getName())) {
			SC_SystemMessage.send(pc, "자기 자신은 차단할 수 없습니다.");
			return;
		}

		if (exList.contains(subType, charName)) {
			delExclude(pc, subType, charName);
			exList.remove(subType, charName);
			SC_PacketBox.send(pc, SC_PacketBox.REM_EXCLUDE, subType, charName);
			return;
		}

		for (L1CharName cn : CharacterTable.getInstance().getCharNameList()) {
			if (charName.equalsIgnoreCase(cn.getName())) {
				exList.add(subType, cn.getName());
				insertExclude(pc, subType, cn.getId(), cn.getName());
				SC_PacketBox.send(pc, SC_PacketBox.ADD_EXCLUDE, subType, charName);
				return;
			}
		}
	}

	private static void handleChat(L1PcInstance pc, CIOClient client, int chatType, String message, String targetName) {
		try {
			if (pc == null) {
				return;
			}

			if (l1j.server.server.model.character.pss.window.PlaySupportWindow.handleLinkCommand(pc, message)) return;

			if (!pc.isGm() && (pc.isGhost() || StadiumManager.getInstance().is_on_stadium(pc.getMapId()))) {
				SC_SystemMessage.send(pc, "현재 채팅을 하실 수 없는 상태입니다.");
				return;
			}

			if (chatType == 1) {
				chatWhisper(pc, chatType, message, targetName);
			} else {
				processChatMessage(pc, chatType, message);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void chatWhisper(L1PcInstance whisperFrom, int chatType, String text, String targetName) {
		if (!whisperFrom.isGm() && Config.보라채팅1 && whisperFrom.getPinkNameTime() > 0) {
			SC_SystemMessage.send(whisperFrom, "현재 상태에서는 귓속말을 할 수 없습니다.");
			return;
		}

		if (targetName == null || targetName.length() > 50 || text == null || text.length() > 45) {
			return;
		}

		if (whisperFrom.hasSkillEffect(L1SkillId.STATUS_CHAT_PROHIBITED)) {
			SC_ServerMessage.send(whisperFrom, 242);
			return;
		}

		if (whisperFrom.getLevel() < Config.WHISPER_CHAT_LEVEL) {
			SC_ServerMessage.send(whisperFrom, 404, String.valueOf(Config.WHISPER_CHAT_LEVEL));
			return;
		}

		final L1PcInstance whisperTo = L1World.getInstance().getPlayer(targetName);

		if (whisperTo == null) {
			SC_ServerMessage.send(whisperFrom, 73, targetName);
			return;
		}

		if (whisperTo.equals(whisperFrom)) {
			return;
		}

		final L1ExcludingList spamList2 = SpamTable.getInstance().getExcludeTable(whisperTo.getId());

		if (spamList2.contains(0, whisperFrom.getName())) {
			SC_ServerMessage.send(whisperFrom, 117, whisperTo.getName());
			return;
		}

		if (!whisperTo.isCanWhisper()) {
			SC_ServerMessage.send(whisperFrom, 205, whisperTo.getName());
			return;
		}

		SC_NewChat.send(whisperFrom, whisperFrom, 3, chatType, text, whisperTo.getName());
		SC_NewChat.send(whisperTo, whisperFrom, 4, chatType, text, whisperFrom.getName());

		LoggerInstance.getInstance().addWhisper(whisperFrom, whisperTo, text);
	}

	private static void processChatMessage(L1PcInstance pc, int chatType, String chatText) {
		if (chatText == null) {
			return;
		}

		MJCaptchaLoadManager.getInstance().do_auth_captcha(pc, chatText);

		if (pc.hasSkillEffect(L1SkillId.SILENCE) || pc.hasSkillEffect(L1SkillId.AREA_OF_SILENCE) || pc.hasSkillEffect(L1SkillId.CONFUSION) || pc.hasSkillEffect(L1SkillId.STATUS_POISON_SILENCE) || pc.hasSkillEffect(1005)) {
			SC_ServerMessage.send(pc, 242);
			return;
		}

		if (pc.getMapId() == 631 && !pc.isGm()) {
			SC_ServerMessage.send(pc, 912);
			return;
		}

		if (pc.isDeathMatch() && !pc.isGm() && !pc.isGhost()) {
			SC_SystemMessage.send(pc, "데스매치 경기 중에는 채팅이 금지됩니다.");
			return;
		}

		if (!pc.isGm() && pc.getMapId() == 5153 && chatType != 0) {
			SC_SystemMessage.send(pc, "프리미엄 배틀존 진행 중에는 일반채팅만 가능합니다.");
			return;
		}

		switch (chatType) {
		case 0: {
			if (!GMCommands.일반채팅 && !pc.isGm() && pc.getAccessLevel() != Config.SUB_GMCODE) {
				if (!chatText.startsWith(".")) {
					SC_SystemMessage.send(pc, "운영자에 의해 현재 채팅이 비활성화되었습니다.");
					return;
				}
			}

			if (chatText.startsWith(".")) {
				if (handleClientCommand(pc, chatText.substring(1).trim())) {
					return;
				}
			}

			if (handleMegaphone(pc, chatText)) {
				return;
			}

			if (handleGambling(pc, chatText)) {
				return;
			}

			if (pc.is_combat_field()) {
				return;
			}

			SC_NewChat.send(pc, pc, 3, chatType, chatText, "");
			final SC_NewChat.ChatData sChatPacket = SC_NewChat.prepare(pc, 4, chatType, chatText, "");

			final L1ExcludingList spamList = SpamTable.getInstance().getExcludeTable(pc.getId());
			if (!spamList.contains(0, pc.getName())) {
				sChatPacket.send(pc);
			}

			for (L1PcInstance listener : L1World.getInstance().getVisiblePlayerFast(pc)) {
				if (listener.getMapId() == 5554 && !listener.isGm() && !pc.isGm()) {
					continue;
				}

				if (!listener.isOutsideChat() && !pc.isGm()) {
					final L1Buddy buddyList = BuddyTable.getInstance().getBuddyTable(listener.getId());
					final L1Party party = listener.getParty();
					final L1ChatParty cParty = listener.getChatParty();

					if (!(buddyList.containsId(pc.getId()) || (listener.getClanid() > 0 && listener.getClanid() == pc.getClanid()) || (party != null && party.isMember(pc)) || (cParty != null && cParty.isMember(pc)))) {
						continue;
					}
				}

				final L1ExcludingList spamList3 = SpamTable.getInstance().getExcludeTable(listener.getId());
				if (!spamList3.contains(0, pc.getName())) {
					sChatPacket.send(listener);
				}
			}

			LoggerInstance.getInstance().addChat(ChatType.Normal, pc, chatText);
			break;
		}

		case 3:
			if (!GMCommands.일반채팅 && !pc.isGm() && pc.getAccessLevel() != Config.SUB_GMCODE && !chatText.startsWith(".")) {
				SC_SystemMessage.send(pc, "운영자에 의해 현재 채팅이 비활성화되었습니다.");
				return;
			}

			if (!GMCommands.전체채팅 && !pc.isGm()) {
				SC_SystemMessage.send(pc, "운영자에 의해 현재 전체채팅이 비활성화되었습니다.");
				return;
			}

			chatWorld(pc, chatType, chatText);
			break;

		case 4: {
			if (pc.getClanid() != 0) {
				final L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null) {
					final SC_NewChat.ChatData packet = SC_NewChat.prepare(pc, 4, chatType, chatText, "");
					LoggerInstance.getInstance().addChat(ChatType.Clan, pc, chatText);

					for (L1PcInstance listener : clan.getOnlineClanMember()) {
						final L1ExcludingList spam = SpamTable.getInstance().getExcludeTable(listener.getId());
						if (!spam.contains(0, pc.getName())) {
							packet.send(listener);
						}
					}
				}
			}
			break;
		}

		case 11:
			if (pc.isInParty()) {
				final SC_NewChat.ChatData packet = SC_NewChat.prepare(pc, 4, chatType, chatText, "");
				LoggerInstance.getInstance().addChat(ChatType.Party, pc, chatText);

				for (L1PcInstance listener : pc.getParty().getMembers()) {
					if (!SpamTable.getInstance().getExcludeTable(listener.getId()).contains(0, pc.getName())) {
						packet.send(listener);
					}
				}
			}
			break;

		case 12:
			chatWorld(pc, 12, chatText);
			break;

		case 13: {
			if (pc.getClanid() != 0) {
				final L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				final int rank = pc.getClanRank();

				if (clan != null && (rank == L1Clan.군주 || rank == L1Clan.수호)) {
					final SC_NewChat.ChatData packet = SC_NewChat.prepare(pc, 4, chatType, chatText, "");
					LoggerInstance.getInstance().addChat(ChatType.Alliance, pc, chatText);

					for (L1PcInstance listener : clan.getOnlineClanMember()) {
						final int listenerRank = listener.getClanRank();
						if (!SpamTable.getInstance().getExcludeTable(listener.getId()).contains(0, pc.getName()) && (listenerRank == L1Clan.군주 || listenerRank == L1Clan.수호)) {
							packet.send(listener);
						}
					}
				}
			}
			break;
		}

		case 14:
			if (pc.isInChatParty()) {
				final SC_NewChat.ChatData packet = SC_NewChat.prepare(pc, 4, chatType, chatText, "");
				LoggerInstance.getInstance().addChat(ChatType.Party, pc, chatText);

				for (L1PcInstance listener : pc.getChatParty().getMembers()) {
					if (!SpamTable.getInstance().getExcludeTable(listener.getId()).contains(0, pc.getName())) {
						packet.send(listener);
					}
				}
			}
			break;

		case 17:
			if (pc.getClanid() != 0) {
				final L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null && pc.isCrown() && pc.getId() == clan.getLeaderId()) {
					final SC_NewChat.ChatData packet = SC_NewChat.prepare(pc, 4, chatType, chatText, "");
					LoggerInstance.getInstance().addChat(ChatType.Guardian, pc, chatText);

					for (L1PcInstance listener : clan.getOnlineClanMember()) {
						if (!SpamTable.getInstance().getExcludeTable(listener.getId()).contains(0, pc.getName())) {
							packet.send(listener);
						}
					}
				}
			}
			break;
		}

		if (!pc.isGm()) {
			pc.checkChatInterval();
		}
	}

	private static boolean handleClientCommand(L1PcInstance pc, String tcmd) {
		if (tcmd.equals("탐") || tcmd.equals("탐포인트")) {
			SC_SystemMessage.send(pc, "보유 탐 포인트: " + pc.getNetConnection().getAccount().getTamPoint() + " 개");
			return true;
		}

		if (tcmd.equals("던전")) {
			DungeonTimeCommand.showDungeonTime(pc);
			return true;
		}

		if (tcmd.equals("마법멘트")) {
			final boolean muted = SkillMsgMute.toggle(pc.getId());
			SC_SystemMessage.send(pc, muted ? "마법 사용불가 알림을 껐습니다." : "마법 사용불가 알림을 켰습니다.");
			return true;
		}

		if (pc.getAccessLevel() == Config.GMCODE || pc.isMonitor()) {
			GMCommands.getInstance().handleCommands(pc, tcmd);
			return true;
		}

		UserCommands.getInstance().handleCommands(pc, tcmd);
		return true;
	}

	private static boolean handleMegaphone(L1PcInstance pc, String chatText) {
		final int temporaryItemObjectId = pc.getTemporaryItemObjectId();

		if (temporaryItemObjectId <= 0) {
			return false;
		}

		final L1Object obj = L1World.getInstance().findObject(temporaryItemObjectId);

		if (obj != null && obj.instanceOf(MJL1Type.L1TYPE_ITEMINSTANCE)) {
			final L1ItemInstance item = (L1ItemInstance) obj;
			final int itemId = item.getItemId();

			if (itemId == 700085 || itemId == 700086) {
				if (MJSurveyFactory.isMegaphoneSpeaking) {
					pc.sendPackets("이미 확성기 메시지가 재생되고 있습니다. 잠시 후 다시 이용해주세요.");
					pc.clearTemporaryItemObjectId();
					return true;
				}

				MJSurveySystemLoader.getInstance().registerSurvey(pc,
						String.format("입력하신 문자가 : \"%s\"입니다. 이대로 전송하시겠습니까?", chatText),
						temporaryItemObjectId,
						MJSurveyFactory.createMegaphoneSurvey(temporaryItemObjectId, chatText, itemId == 700085 ? 20 : 40),
						10000L);

				pc.clearTemporaryItemObjectId();
				return true;
			}
		}

		return false;
	}

	private static boolean handleGambling(L1PcInstance pc, String chatText) {
		if (pc.isGambling() && pc.getGamblingMoney() == 0) {
			new L1Gambling().placeBet(pc, chatText);
			return true;
		}

		if (pc.isGambling3() && pc.getGamblingMoney3() == 0) {
			new L1Gambling3().placeBet(pc, chatText);
			return true;
		}

		if (pc.isGambling()) {
            // No gambling object is needed for an ordinary chat message.
            final L1Gambling gam = new L1Gambling();
			int type = -1;
			if (chatText.startsWith("홀")) type = 1;
			else if (chatText.startsWith("짝")) type = 2;
			else if (chatText.startsWith("1")) type = 3;
			else if (chatText.startsWith("2")) type = 4;
			else if (chatText.startsWith("3")) type = 5;
			else if (chatText.startsWith("4")) type = 6;
			else if (chatText.startsWith("5")) type = 7;
			else if (chatText.startsWith("6")) type = 8;

			if (type != -1) {
				gam.Gambling2(pc, chatText, type);
				return true;
			}
		}

		if (pc.isGambling3()) {
			final L1Gambling3 gam3 = new L1Gambling3();
			int type3 = -1;

			if (chatText.startsWith("오크전사")) type3 = 1;
			else if (chatText.startsWith("스파토이")) type3 = 2;
			else if (chatText.startsWith("멧돼지")) type3 = 3;
			else if (chatText.startsWith("슬라임")) type3 = 4;
			else if (chatText.startsWith("해골")) type3 = 5;
			else if (chatText.startsWith("늑대인간")) type3 = 6;
			else if (chatText.startsWith("버그베어")) type3 = 7;
			else if (chatText.startsWith("장로")) type3 = 8;
			else if (chatText.startsWith("괴물눈")) type3 = 9;

			if (type3 != -1) {
				gam3.Gambling3(pc, chatText, type3);
				return true;
			}
		}

		return false;
	}

	private static void chatWorld(L1PcInstance pc, int chatType, String text) {
		try {
			if (pc.isGm() || pc.getAccessLevel() == 1) {
				sendGmWorld(pc, chatType, text);
				return;
			}

			if (pc.getLevel() < Config.GLOBAL_CHAT_LEVEL) {
				SC_ServerMessage.send(pc, 195, String.valueOf(Config.GLOBAL_CHAT_LEVEL));
				return;
			}

			if (!L1World.getInstance().isWorldChatElabled()) {
				SC_ServerMessage.send(pc, 510);
				return;
			}

			if (pc.get_food() <= 12) {
				SC_ServerMessage.send(pc, 462);
				return;
			}

			SC_PacketBox.send(pc, SC_PacketBox.FOOD, pc.get_food());

			for (L1PcInstance listener : L1World.getInstance().getAllPlayers()) {
				final L1ExcludingList spam = SpamTable.getInstance().getExcludeTable(listener.getId());
				if (spam.contains(0, pc.getName())) {
					continue;
				}

				if (chatType == 12) {
					if (listener.isShowTradeChat()) {
						SC_NewChat.send(listener, pc, 4, chatType, text, "");
					}
				} else if (chatType == 3) {
					if (listener.isShowWorldChat()) {
						SC_NewChat.send(listener, pc, 4, chatType, text, "");
					}
				}
			}

			if (chatType == 3) {
				LoggerInstance.getInstance().addChat(ChatType.Global, pc, text);
			}

		} catch (Exception e) {
		}
	}

	private static void sendGmWorld(L1PcInstance pc, int chatType, String text) {
		if (chatType == 3) {
			SC_SystemMessage.broadcast(text);
			SC_PacketBox.broadcastGreen("\\f= " + text);
			LoggerInstance.getInstance().addChat(ChatType.Global, pc, text);
			return;
		}

		if (chatType == 12) {
			SC_NewChat.broadcastAll(pc, 4, 12, text, "[******] ");
			SC_NewChat.broadcastAll(pc, 4, 3, text, "[******] ");
			SC_PacketBox.broadcastGreen("" + text);
		}
	}

	private static void insertExclude(L1PcInstance pc, int subType, int objId, String name) {
        final int ownerId = pc.getId();
        CacheD.getInstance().submitDbJobOrdered(ownerId, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO character_exclude SET char_id=?, type=?, exclude_id=?, exclude_name=?")) {
                ps.setInt(1, ownerId);
                ps.setInt(2, subType);
                ps.setInt(3, objId);
                ps.setString(4, name);
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException("Character exclude insert failed: " + ownerId, ex);
            }
        });
    }

    private static void delExclude(L1PcInstance pc, int subType, String name) {
        final int ownerId = pc.getId();
        CacheD.getInstance().submitDbJobOrdered(ownerId, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "DELETE FROM character_exclude WHERE char_id=? AND type=? AND exclude_name=?")) {
                ps.setInt(1, ownerId);
                ps.setInt(2, subType);
                ps.setString(3, name);
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new IllegalStateException("Character exclude delete failed: " + ownerId, ex);
            }
        });
    }

}
