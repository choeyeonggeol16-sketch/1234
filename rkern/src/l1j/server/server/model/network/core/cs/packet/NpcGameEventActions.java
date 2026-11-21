package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1Object;
import l1j.server.server.datatables.KeyTable;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.instance.dungeon.valakasroom.ValakasRoomSystem;
import l1j.server.server.model.world.instance.dungeon.valakasroom.ValakasReady;
import l1j.server.server.model.world.instance.dungeon.valakasroom.ValakasStart;
import l1j.server.server.model.world.instance.dungeon.valakasroom.ValakasReadyStart;
import l1j.server.server.model.world.instance.dungeon.training.BossTraining;
import l1j.server.server.model.world.instance.dungeon.training.BossTrainingSystem;
import l1j.server.server.model.world.instance.dungeon.orim.OrimController;
import l1j.server.server.model.world.instance.dungeon.fanstasyisland.FantasyIslandSystem;

import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.datatables.UBTable;
import l1j.server.server.model.L1DeathMatch;
import l1j.server.server.model.L1HauntedHouse;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1PetMatch;
import l1j.server.server.model.L1Racing;
import l1j.server.server.model.L1UltimateBattle;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import java.util.Random;
import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.utils.TYUtil;

import l1j.server.server.model.world.instance.dungeon.bossraid.BossRaid;

import l1j.server.server.model.world.instance.dungeon.bossraid.BossRaidSystem;

import l1j.server.server.model.world.instance.dungeon.bossraid2.Queen1;

import l1j.server.server.model.world.instance.dungeon.bossraid2.QueenSystem2;

final class NpcGameEventActions {
    private NpcGameEventActions() {}

	static String enterUb(L1PcInstance pc, int npcId) {
		L1UltimateBattle ub = UBTable.getInstance().getUbForNpcId(npcId);
		if (ub == null || !ub.isActive() || !ub.canPcEnter(pc)) {
			return "colos2";
		}
		if (ub.isNowUb()) {
			return "colos1";
		}
		if (ub.getMembersCount() >= ub.getMaxPlayer()) {
			return "colos4";
		}

		ub.addMember(pc);
		L1Location loc = ub.getLocation().randomLocation(10, false);
		pc.start_teleport(loc.getX(), loc.getY(), ub.getMapId(), pc.getHeading(), 169, true, false);
		return "";
	}

	static String enterHauntedHouse(L1PcInstance pc) {
		if (L1HauntedHouse.getInstance().getHauntedHouseStatus() == L1HauntedHouse.STATUS_PLAYING) {
			SC_ServerMessage.send(pc, 1182);
			return "";
		}
		if (L1HauntedHouse.getInstance().getHauntedHouseStatus() == L1HauntedHouse.STATUS_CLEANUP) {
			SC_ServerMessage.send(pc, 1183);
			return "";
		}
		if (L1HauntedHouse.getInstance().getMembersCount() >= 10) {
			SC_ServerMessage.send(pc, 1184);
			return "";
		}

		if (pc.getLevel() < 30) {
			SC_ServerMessage.send(pc, 1273, "30", "99");
			return "";
		}

		if (!L1HauntedHouse.getInstance().isMember(pc)) {
			if (!pc.getInventory().checkItem(L1ItemId.ADENA, 1000)) {
				SC_ServerMessage.send(pc, 189);
				return "";
			}

			pc.getInventory().removeItem(L1ItemId.ADENA, 1000);

			L1HauntedHouse.getInstance().addMember(pc);
		}

		if (L1DeathMatch.getInstance().isMember(pc)) {
			L1DeathMatch.getInstance().removeMember(pc);
		}

		if (L1Racing.getInstance().contains(0, pc)) {
			L1Racing.getInstance().remove(0, pc);
		}

		return "";
	}

	static String enterDeathMatch(L1PcInstance pc) {
		if (L1DeathMatch.getInstance().getDeathMatchStatus() == L1DeathMatch.STATUS_PLAYING) {
			SC_ServerMessage.send(pc, 1182);
			return "";
		}
		if (L1DeathMatch.getInstance().getMembersCount() >= 20) {
			SC_SystemMessage.send(pc, "이미 데스매치가 포화 상태라네.");
			return "";
		}
		if (pc.getLevel() < 52) {
			SC_ServerMessage.send(pc, 1273, "52", "99");
			return "";
		}

		L1DeathMatch.getInstance().addMember(pc);
		if (L1HauntedHouse.getInstance().isMember(pc)) {
			L1HauntedHouse.getInstance().removeMember(pc);
		}
		return "";
	}

    static String enterPetMatch(L1PcInstance pc, String objectId) {
        final int amuletId;
        try {
            amuletId = Integer.parseInt(objectId);
        } catch (NumberFormatException invalid) {
            SC_ServerMessage.send(pc, 1182);
            return "";
        }
        if (!pc.getPetList().isEmpty()) {
            SC_ServerMessage.send(pc, 1187);
            return "";
        }
        if (amuletId <= 0 || !L1PetMatch.getInstance().enterPetMatch(pc, amuletId)) {
            SC_ServerMessage.send(pc, 1182);
        }
        return "";
    }

	static String[] makeUbInfoStrings(int npcId) {
		L1UltimateBattle ub = UBTable.getInstance().getUbForNpcId(npcId);
		return ub.makeUbInfoStrings();
	}

	static String enterPe(L1PcInstance pc, int npcId) {
		if (L1Racing.getInstance().getGameStatus() == L1HauntedHouse.STATUS_PLAYING) {
			SC_ServerMessage.send(pc, 1182);
			return "";
		}

		if (L1Racing.getInstance().getGameStatus() == L1HauntedHouse.STATUS_CLEANUP) {
			SC_ServerMessage.send(pc, 1182);
			return "";
		}
		if (L1Racing.getInstance().getMembersCount() >= 8) {
			SC_ServerMessage.send(pc, 1184);
			return "";
		}

		if (pc.getLevel() < 30) {
			SC_ServerMessage.send(pc, 1273, "30", "99");
			return "";
		}

		if (!L1Racing.getInstance().isMember(pc)) {
			if (!pc.getInventory().checkItem(L1ItemId.ADENA, 1000)) {
				SC_ServerMessage.send(pc, 189);
				return "";
			}

			pc.getInventory().removeItem(L1ItemId.ADENA, 1000);

			L1Racing.getInstance().add(0, pc);
		}

		if (L1DeathMatch.getInstance().isMember(pc)) {
			L1DeathMatch.getInstance().removeMember(pc);
		}

		if (L1HauntedHouse.getInstance().isMember(pc)) {
			L1Racing.getInstance().remove(0, pc);
		}

		return "";
	}

    static void comaCheck(L1PcInstance pc, int type, int objid) {
        if (type != 3 && type != 5) return;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 7000021)) {
            int remaining = type;
            for (int id = 3000022; id <= 3000026 && remaining > 0; id++) {
                if (!pc.getInventory().checkItem(id, 1)) continue;
                if (!tx.debit(id, 1)) {
                    SC_NPCTalkReturn.send(pc, objid, "coma3");
                    return;
                }
                remaining--;
            }
            if (remaining != 0) {
                SC_NPCTalkReturn.send(pc, objid, "coma3");
                return;
            }
            commitComa(tx, pc, type, objid, "coma3");
        } catch (RuntimeException failure) {
            SC_NPCTalkReturn.send(pc, objid, "coma3");
        }
    }

    static void giveComaBuff(L1PcInstance pc, int objid) {
        final int death = pc.getDeathMatchPiece(), ghost = pc.getGhostHousePiece();
        final int race = pc.getPetRacePiece(), pet = pc.getPetMatchPiece();
        final int battle = pc.getUltimateBattlePiece();
        final long sum = (long) death + ghost + race + pet + battle;
        // Reset the submitted selection synchronously; a delayed receipt must
        // never erase the next menu's selection.
        resetPiece(pc);
        if (death < 0 || ghost < 0 || race < 0 || pet < 0 || battle < 0 || sum < 3 || sum == 4) {
            SC_NPCTalkReturn.send(pc, objid, "coma3_3");
            return;
        }
        if (sum > 5) {
            SC_NPCTalkReturn.send(pc, objid, "coma3_1");
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 7000021)) {
            if (!debitComaPiece(tx, 3000022, death) || !debitComaPiece(tx, 3000023, ghost)
                    || !debitComaPiece(tx, 3000024, race) || !debitComaPiece(tx, 3000025, pet)
                    || !debitComaPiece(tx, 3000026, battle)) {
                SC_NPCTalkReturn.send(pc, objid, "coma3_2");
                return;
            }
            commitComa(tx, pc, (int) sum, objid, "coma3_2");
        } catch (RuntimeException failure) {
            SC_NPCTalkReturn.send(pc, objid, "coma3_2");
        }
    }

    private static boolean debitComaPiece(L1InventoryTransaction tx, int itemId, int count) {
        return count == 0 || tx.debit(itemId, count);
    }

    private static void commitComa(L1InventoryTransaction tx, L1PcInstance pc, int type, int objid, String failurePage) {
        tx.onFailure(() -> SC_NPCTalkReturn.send(pc, objid, failurePage));
        tx.commitAfterInventoryRelease(() -> {
            applyComa(pc, type);
            SC_NPCTalkReturn.send(pc, objid, "");
        });
    }

    private static void applyComa(L1PcInstance pc, int type) {
        final boolean large = type == 5;
        final int skill = large ? L1SkillId.COMA_B : L1SkillId.COMA_A;
        pc.removeSkillEffect(large ? L1SkillId.COMA_A : L1SkillId.COMA_B);
        if (!pc.hasSkillEffect(skill)) {
            if (large) pc.getAbility().addSp(1);
            pc.getAbility().addAddedCon(large ? 3 : 1);
            pc.getAbility().addAddedDex(5);
            pc.getAbility().addAddedStr(5);
            pc.addHitRate(large ? 5 : 3);
            pc.getAC().addAc(large ? -8 : -3);
            SC_OwnCharStatus.send(pc);
            if (large) SC_SPMR.send(pc);
        }
        final int sound = large ? 7383 : 7382;
        SC_SkillSound.send(pc, pc.getId(), sound);
        SC_SkillSound.broadcast(pc, pc.getId(), sound);
        pc.setSkillEffect(skill, large ? 7200 * 1000 : 3600 * 1000);
    }

	static void resetPiece(L1PcInstance pc) {
		pc.setDeathMatchPiece(0);
		pc.setGhostHousePiece(0);
		pc.setPetRacePiece(0);
		pc.setPetMatchPiece(0);
		pc.setUltimateBattlePiece(0);
	}

	static void selectComa(L1PcInstance pc, int objid) {
		String[] htmldata = new String[] { String.valueOf(pc.getDeathMatchPiece()),
				String.valueOf(pc.getGhostHousePiece()), String.valueOf(pc.getPetRacePiece()),
				String.valueOf(pc.getPetMatchPiece()), String.valueOf(pc.getUltimateBattlePiece()) };
		SC_NPCTalkReturn.send(pc, objid, "coma5", htmldata);
	}

    // Preserve legacy ordered independent matches, including ambiguous A/a etc.
    private static final String[] BOSS_COMMANDS = {"A", "B", "C", "D", "E", "F", "G", "H", "a", "b", "c", "L", "M", "N", "O", "P", "Q", "S", "T", "U", "V", "W", "X", "Y", "Z", "I", "J", "K", "VV", "d"};
    private static final int[] BOSS_KEYS = {80466, 80467, 80450, 80451, 80452, 80453, 80454, 80455, 80456, 80457, 80458, 80459, 80460, 80461, 80462, 80463, 80464, 80465, 80468, 80469, 80470, 80471, 80472, 80473, 80474, 80475, 80476, 80477, 80479, 80478};
    private static final int[] BOSS_IDS = {5020, 7310175, 45829, 81201, 45955, 45959, 45956, 45546, 7123, 45583, 7000093, 45795, 45676, 45863, 7310015, 7310021, 45952, 45601, 7310148, 7310154, 7310028, 7310034, 7310041, 7310046, 7310051, 7310056, 7310061, 7310066, 8500129, 45600};
    private static final String[] BOSS_MESSAGES = {
        "'광풍의샤스키'가 중앙에 소환되었습니다.",
        "'망자의 교도관'이 중앙에 소환되었습니다.",
        "'발바도스'가 중앙에 소환되었습니다.",
        "'아이스데몬'이 중앙에 소환되었습니다.",
        "'케이나'가 중앙에 소환되었습니다.",
        "'이데아'가 중앙에 소환되었습니다.",
        "'비아타스'가 중앙에 소환되었습니다.",
        "'도플갱어보스'가 중앙에 소환되었습니다.",
        "'아크모'가 중앙에 소환되었습니다.",
        "'베레스'가 중앙에 소환되었습니다.",
        "'제로스'가 중앙에 소환되었습니다.",
        "'스피리드'가 중앙에 소환되었습니다.",
        "'헬바인'이 중앙에 소환되었습니다.",
        "'라이아'가 중앙에 소환되었습니다.",
        "'왜곡의 제니스퀸'가 중앙에 소환되었습니다.",
        "'불신의 시어'가 중앙에 소환되었습니다.",
        "'거대수호개미'가 중앙에 소환되었습니다.",
        "'데스나이트'가 중앙에 소환되었습니다.",
        "'공포의 린드비오르'가 중앙에 소환되었습니다.",
        "'공포의 안타라스'가 중앙에 소환되었습니다.",
        "'공포의 뱀파이어'가 중앙에 소환되었습니다.",
        "'죽음의 좀비 로드'가 중앙에 소환되었습니다.",
        "'지옥의 쿠거'가 중앙에 소환되었습니다.",
        "'불사의 머미 로드'가 중앙에 소환되었습니다.",
        "'냉혹한 아이리스'가 중앙에 소환되었습니다.",
        "'어둠의 나이트 발드'가 중앙에 소환되었습니다.",
        "'불멸의 리치'가 중앙에 소환되었습니다.",
        "'오만한 우그누스'가 중앙에 소환되었습니다.",
        "'사신 그림 리퍼'가 중앙에 소환되었습니다.",
        "'흑기사 대장 커츠'가 중앙에 소환되었습니다."
    };

    static String bossKey(L1PcInstance pc, L1NpcInstance npc, String command, String tail, Random random) {
        if ("AA".equalsIgnoreCase(command)) {
            final int[] bosses;
            try {
                final String config = Config.mobboss;
                if (config == null || config.isBlank()) return "bosskey10";
                bosses = TYUtil.GetIntTokenizeIntArray(config);
            } catch (IllegalArgumentException e) {
                return "bosskey10";
            }
            if (bosses.length == 0) return "bosskey10";
            // Validate every possible random destination before consuming the key.
            for (int id : bosses) if (NpcTable.getInstance().getTemplate(id) == null) return "bosskey10";
            if (!pc.getInventory().consumeItem(80479, 1)) return "bosskey10";
            // Keep the existing 0..99 modulo selection and shared RNG order.
            spawn(32878, 32816, (short) pc.getMapId(), 0, bosses[random.nextInt(100) % bosses.length], 0);
            SC_NpcChatPacket.sendNpc(pc, npc, "어떤 몬스터가 나왔을까요??", 0);
            return tail;
        }
        for (int i = 0; i < BOSS_COMMANDS.length; i++) {
            if (!BOSS_COMMANDS[i].equalsIgnoreCase(command)) continue;
            if (NpcTable.getInstance().getTemplate(BOSS_IDS[i]) == null
                    || !pc.getInventory().consumeItem(BOSS_KEYS[i], 1)) {
                tail = "bosskey10";
                continue;
            }
            L1SpawnUtil.spawn2(32878, 32816, (short) pc.getMapId(), BOSS_IDS[i], 0, 3600 * 1000, 0);
            SC_NpcChatPacket.sendNpc(pc, npc, BOSS_MESSAGES[i], 0);
        }
        return tail;
    }

	private static void spawn(int x, int y, short MapId, int Heading, int npcId, int randomRange) {
		try {// 타입이란부분에 월래2였는데 얼음여왕은 1을 죠 타입을따라가보면
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(MapId);
			if (randomRange == 0) {
				npc.getLocation().set(x, y, MapId);
				npc.getLocation().forward(Heading);
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(x + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					npc.setY(y + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
						break;
					}
					Thread.sleep(1);
				} while (tryCount < 50);
				if (tryCount >= 50) {
					npc.getLocation().forward(Heading);
				}
			}

			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(Heading);

			L1World.getInstance().storeAndEnterWorld(npc);


			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static String fantasyIsland(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("enter")) {
						FantasyIslandSystem.getInstance().startRaid(pc);	
						}

				/** 하버트 **/
		return htmlid;
	}


    static String bossTraining(L1PcInstance pc, L1NpcInstance npc, String s, String htmlid) {
        final BossTrainingSystem training = BossTrainingSystem.getInstance();
        if (s.equalsIgnoreCase("1")) {
            if (pc.getInventory().checkItem(80500)) return "bosskey6";
            return training.countRaidPotal() >= 99 ? "bosskey3" : "bosskey4";
        }
        if (s.length() == 1 && s.charAt(0) >= '2' && s.charAt(0) <= '4') {
            if (pc.getInventory().checkItem(80500)) return "bosskey6";
            if (training.countRaidPotal() >= 99) return "bosskey3";
            return rentTrainingRoom(pc, npc, training, 4 << (s.charAt(0) - '2'), htmlid);
        }
        if (s.equalsIgnoreCase("6")) {
            L1ItemInstance key = pc.getInventory().findItemId(80500);
            if (key == null || !training.isActive(key)) return "bosskey2";
            pc.start_teleport(32901, 32814, key.getKeyId(), pc.getHeading(), 169, true, false);
        }
        return htmlid;
    }

    private static String rentTrainingRoom(L1PcInstance pc, L1NpcInstance npc,
            BossTrainingSystem training, int count, String htmlid) {
        final int npcId = npc.getId();
        final var client = pc.getNetConnection();
        final int dialogueVersion = client == null ? 0 : client.npcDialogueVersion();
        final long dialogueTarget = L1World.getInstance().smartIdOf(npc);
        BossTraining reserved = null;
        boolean submitted = false;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npc.getNpcId())) {
            if (pc.getInventory().checkItem(80500)) return "bosskey6";
            if (!tx.debit(40308, count * 300)) return "bosskey5";
            final BossTraining room = training.reserveRaid();
            if (room == null) return "bosskey3";
            reserved = room;
            final int[] keyIds = new int[count];
            for (int i = 0; i < count; i++) {
                L1ItemInstance key = tx.grant(80500, 1);
                if (key == null || key.getItem().isStackable()) {
                    sendTrainingKeyFailure(pc);
                    return htmlid;
                }
                key.setKeyId(room.mapId());
                keyIds[i] = key.getId();
            }
            tx.requireTables("item_key_boss");
            tx.sql(con -> KeyTable.storeTrainingKeys(con, room.mapId(), keyIds));
            tx.onDurableCommit(() -> room.activate(keyIds));
            tx.onTerminalFailure(() -> {
                if (!room.isActive()) training.cancel(room);
            });
            tx.onFailure(() -> sendTrainingKeyFailure(pc));
            tx.commitAfterInventoryRelease(() -> {
                if (HtmlCmdHandler.canReplyToDialogue(pc, dialogueTarget, dialogueVersion))
                    SC_NPCTalkReturn.send(pc, npcId, "bosskey7");
            });
            submitted = true;
            return null;
        } catch (RuntimeException failure) {
            sendTrainingKeyFailure(pc);
            return htmlid;
        } finally {
            if (!submitted && reserved != null) training.cancel(reserved);
        }
    }

    private static void sendTrainingKeyFailure(L1PcInstance pc) {
        SC_SystemMessage.send(pc, "훈련소 열쇠를 모두 지급하지 못했습니다.");
    }

    static String valakasReady(L1PcInstance pc, int templateId, String command, String htmlid) {
        if (!command.equalsIgnoreCase("a")) return htmlid;
        if (pc.getLevel() < 60) return "eldnas3";
        final ValakasReadyStart rooms = ValakasReadyStart.getInstance();
        if (rooms.countReadyRaid() >= 49) {
            sendValakasLimit(pc);
            return htmlid;
        }
        ValakasReady reserved = null;
        boolean submitted = false;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, templateId)) {
            if (!tx.debit(820001, 1)) return "eldnas1";
            final ValakasReady room = rooms.reserve(pc);
            if (room == null) {
                sendValakasLimit(pc);
                return htmlid;
            }
            reserved = room;
            tx.onTerminalFailure(() -> rooms.cancel(room));
            tx.onFailure(() -> sendValakasFailure(pc));
            tx.commitAfterInventoryRelease(() -> rooms.enter(room, pc));
            submitted = true;
        } catch (RuntimeException failure) {
            sendValakasFailure(pc);
        } finally {
            if (!submitted && reserved != null) rooms.cancel(reserved);
        }
        return htmlid;
    }

    static String valakasRaid(L1PcInstance pc, int templateId, String command, String htmlid) {
        if (!command.equalsIgnoreCase("enter")) return htmlid;
        if (pc.getLevel() < 60) return "fd_death2";
        if (pc.getInventory().checkItem(203003, 1)) return htmlid;
        final ValakasRoomSystem rooms = ValakasRoomSystem.getInstance();
        if (rooms.countStartRaid() >= 49) {
            sendValakasLimit(pc);
            return htmlid;
        }
        ValakasStart reserved = null;
        boolean submitted = false;
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, templateId)) {
            if (pc.getInventory().checkItem(203003, 1) || tx.grant(203003, 1) == null) return htmlid;
            final ValakasStart room = rooms.reserve(pc);
            if (room == null) {
                sendValakasLimit(pc);
                return htmlid;
            }
            reserved = room;
            tx.onTerminalFailure(() -> rooms.cancel(room));
            tx.onFailure(() -> sendValakasFailure(pc));
            tx.commitAfterInventoryRelease(() -> rooms.enter(room, pc));
            submitted = true;
        } catch (RuntimeException failure) {
            sendValakasFailure(pc);
        } finally {
            if (!submitted && reserved != null) rooms.cancel(reserved);
        }
        return htmlid;
    }

    private static void sendValakasLimit(L1PcInstance pc) {
        SC_SystemMessage.send(pc, "인스턴스 던전을 더 이상 생성할수 없습니다.");
    }

    private static void sendValakasFailure(L1PcInstance pc) {
        SC_SystemMessage.send(pc, "발라카스 인스턴스 입장을 완료하지 못했습니다.");
    }

    static String orim(L1PcInstance pc, L1Object obj, String s, String htmlid) {
        if (s.equalsIgnoreCase("b")) return enterOrim(pc, (L1NpcInstance) obj, htmlid);
        if (s.equalsIgnoreCase("c")) {
            if (pc.getInventory().checkItem(410095, 1)) return "j_html03";
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, ((L1NpcInstance) obj).getNpcId())) {
                if (pc.getInventory().checkItem(410095, 1)) return "j_html03";
                L1ItemInstance jar = tx.grant(410095, 1);
                if (jar == null) return htmlid;
                String jarName = jar.getName();
                tx.commitAfterInventoryRelease(() -> SC_ServerMessage.send(pc, 143, "$7918", jarName));
            } catch (RuntimeException failure) {
                SC_SystemMessage.send(pc, "항아리를 지급하지 못했습니다.");
            }
        } else if (s.equalsIgnoreCase("a")) {
            return NpcTravelCommands.orimLaboratory(pc, obj.getId(), htmlid);
        } else if (s.equalsIgnoreCase("d")) {
            return NpcExchangeCommands.diary(pc, obj.getId(), false);
        } else if (s.equalsIgnoreCase("e")) {
            return NpcExchangeCommands.diary(pc, obj.getId(), true);
        }
        return htmlid;
    }

    private static String enterOrim(L1PcInstance leader, L1NpcInstance npc, String htmlid) {
        if (OrimController.getInstance().getInDunOpen()) return orimBusy(leader, npc);
        final L1Party party = leader.getParty();
        if (party == null || !party.isLeader(leader)) return "id0_2";
        final L1PcInstance[] members = party.getMembers();
        for (L1PcInstance member : members) {
            if (member == null || member.getMapId() != 0) break;
            if (!member.getInventory().checkItem(410096, 1)) {
                orimMissingOrb(leader, member);
                return htmlid;
            }
        }
        if (!orimPartyCurrent(leader, party, members)) {
            SC_NpcChatPacket.sendNpc(leader, npc, "3명~5명의 파티원으로 구성되어야합니다.", 0);
            return htmlid;
        }

        final OrimController room = OrimController.reserveAdmission();
        if (room == null) return orimBusy(leader, npc);
        boolean submitted = false;
        try (L1InventoryTransaction tx = members.length == 2
                ? L1InventoryTransaction.begin(leader, members[0] == leader ? members[1] : members[0])
                : L1InventoryTransaction.beginParty(leader, members)) {
            for (L1PcInstance member : members) {
                if (!tx.debit(member, 410096, 1)) {
                    orimMissingOrb(leader, member);
                    return htmlid;
                }
            }
            tx.sql(con -> {
                if (!orimPartyCurrent(leader, party, members))
                    throw new java.sql.SQLException("Orim party changed before the admission debit");
            });
            tx.onTerminalFailure(room::cancelAdmission);
            tx.onFailure(() -> SC_SystemMessage.send(leader, "해상던전 입장에 실패했습니다."));
            tx.commit(() -> {
                room.startAdmission(members);
                String departure = leader.getName() + " 님이 동료들과 함께 해상던전으로 떠났습니다.";
                SC_PacketBox.broadcastGreen(departure);
                L1World.getInstance().broadcastServerMessage("\\aD" + departure);
                for (L1PcInstance member : members) {
                    CIOThreadCallback.getInstance().completeSession(member.getNetConnection(), () -> {
                        if (member.getParty() == party && member.getMapId() == 0)
                            member.start_teleport(32796, 32801, 9101, member.getHeading(), 169, true, false);
                    });
                }
            });
            submitted = true;
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(leader, "해상던전 입장에 실패했습니다.");
        } finally {
            if (!submitted) room.cancelAdmission();
        }
        return htmlid;
    }

    private static boolean orimPartyCurrent(L1PcInstance leader, L1Party party, L1PcInstance[] members) {
        if (members.length < 2 || leader.getParty() != party || !party.isLeader(leader)
                || party.getNumOfMembers() != members.length) return false;
        for (L1PcInstance member : members) {
            if (member == null || member.getParty() != party || member.getMapId() != 0
                    || !party.isMember(member)) return false;
            var client = member.getNetConnection();
            if (client == null || !client.isConnected() || client.getActiveChar() != member) return false;
        }
        return true;
    }

    private static String orimBusy(L1PcInstance pc, L1NpcInstance npc) {
        SC_NpcChatPacket.sendNpc(pc, npc, "이미 해상던전으로 선발대가 출발했다네. 잠시후 다시오게.", 0);
        return "";
    }

    private static void orimMissingOrb(L1PcInstance leader, L1PcInstance member) {
        String message = "파티원의 누군가가 시공의 구슬이 없습니다.";
        SC_SystemMessage.send(leader, message);
        SC_SystemMessage.send(member, message);
    }

    /** Existing single-request raid admission. Global reservation is owned by the raid system. */
    static boolean enterClanRaid(L1PcInstance pc, String s) {
        if (!s.equalsIgnoreCase("1")) return true;
        if (Queen1.getqeensgo()) {
            SC_SystemMessage.send(pc, "현재 혈맹 레이드가 진행중입니다. 잠시 후에 도전해주시기 바랍니다.");
            return false;
        }
        if (pc.getClanid() == 0) {
            SC_SystemMessage.send(pc, "혈맹 가입자만 진행 가능합니다.");
            return false;
        }
        if (!pc.isInParty()) {
            SC_SystemMessage.send(pc, "파티중이 아닙니다. 2인 파티이상 출입 가능합니다.");
            return true;
        }
        if (!pc.getParty().isLeader(pc)) {
            SC_SystemMessage.send(pc, "파티장만 신청 가능합니다.");
            return true;
        }
        if (!pc.getInventory().checkItem(7005254, 1)
                || !pc.getInventory().consumeItem(7005254, 1)) {
            SC_SystemMessage.send(pc, "보스 레이드 입장권(1)이 부족합니다.");
            return true;
        }
        Queen1.setqeensgo(true);
        QueenSystem2.getInstance().startQueen(pc);
        return true;
    }

    static boolean enterBossRaid(L1PcInstance pc) {
        if (BossRaid.getBossGo()) {
            SC_SystemMessage.send(pc, "현재 보스 레이드가 진행중입니다. 잠시 후에 도전해주시기 바랍니다.");
            return false;
        }
        if (!pc.isInParty()) {
            SC_SystemMessage.send(pc, "파티중이 아닙니다. 2인 파티이상 출입 가능합니다.");
            return true;
        }
        if (!pc.getParty().isLeader(pc)) {
            SC_SystemMessage.send(pc, "파티장만 신청 가능합니다.");
            return true;
        }
        if (!pc.getInventory().hasItemDirect(7005254)
                || !pc.getInventory().consumeItem(7005254, 1)) {
            SC_SystemMessage.send(pc, "보스 레이드 입장권(1)이 부족합니다.");
            return true;
        }
        BossRaid.setBossGo(true);
        BossRaidSystem.getInstance().startBossRaid(pc);
        return true;
    }
}
