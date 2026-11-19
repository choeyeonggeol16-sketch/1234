package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1Object;
import l1j.server.server.datatables.KeyTable;
import l1j.server.IndunSystem.ValakasRoom.ValakasRoomSystem;
import l1j.server.IndunSystem.ValakasRoom.ValakasReadyStart;
import l1j.server.IndunSystem.Training.BossTrainingSystem;
import l1j.server.IndunSystem.Orim.OrimController;
import l1j.server.IndunSystem.FanstasyIsland.FantasyIslandSystem;

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

import l1j.server.IndunSystem.BossRaid.BossRaid;

import l1j.server.IndunSystem.BossRaid.BossRaidSystem;

import l1j.server.IndunSystem.BossRaid2.Queen1;

import l1j.server.IndunSystem.BossRaid2.QueenSystem2;

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


    static String bossTraining(L1PcInstance pc, String s, String htmlid) {
		if (s.equalsIgnoreCase("1")) { // 대여
			int countActiveMaps = BossTrainingSystem.getInstance().countRaidPotal();
			if (pc.getInventory().checkItem(80500)) {
				htmlid = "bosskey6";
				// 이미 훈련소 열쇠를 가지고 계신 것 같군요.
				// 많은 분들께서 이용하실 수 있도록 훈련소는 한 사람 당 하나 씩만 대여해 드리고 있습니다.
			} else if (countActiveMaps >= 99) {
				htmlid = "bosskey3";
				// 죄송합니다.
				// 지금은 모든 훈련소에서 훈련이 진행 중 입니다.
			} else {
				htmlid = "bosskey4";
			}
		} else if (s.length() == 1 && s.charAt(0) >= '2' && s.charAt(0) <= '4') {
			if (!pc.getInventory().checkItem(80500)) { // 액션 조작 방지
                if (BossTrainingSystem.getInstance().countRaidPotal() >= 99) return "bosskey3";
				L1ItemInstance item = null;
				int count = 0;
				if (s.equalsIgnoreCase("2")) { // 4개
					count = 4;
				} else if (s.equalsIgnoreCase("3")) { // 8개
					count = 8;
				} else if (s.equalsIgnoreCase("4")) { // 16개
					count = 16;
				}
				if (pc.getInventory().consumeItem(40308, count * 300)) {
					int id = BossTrainingSystem.getInstance().blankMapId();
					BossTrainingSystem.getInstance().startRaid(pc, id);
					for (int i = 0; i < count; i++) {
						item = pc.getInventory().storeItem(80500, 1);
                                    if (item == null) {
                                        SC_SystemMessage.send(pc, "훈련소 열쇠를 모두 지급하지 못했습니다.");
                                        return htmlid;
                                    }
						item.setKeyId(id);
						if (KeyTable.checkey(item)) {
							KeyTable.DeleteKey(item);
							KeyTable.StoreKey(item);
						} else {
							KeyTable.StoreKey(item);
						}
					}
					htmlid = "bosskey7";
					// 같이 훈련을 받으실 분들에게 열쇠를 나누어 주신 다음 저에게 보여주시면 훈련소로 안내해
					// 드리겠습니다.
					// 훈련소의 대여시간은 최대 4시간이며, 훈련 중이라 해도 대여 시간이 종료되면 다음 사람을
					// 위해 훈련소 사용이 중지됩니다.
					// 훈련용 몬스터를 소환하실 때에는 항상 훈련소의 남은 사용 시간을 확인하시기 바랍니다.
				} else {
					htmlid = "bosskey5";
					// 죄송하지만, 사용료를 지불하지 않으시면 훈련소를 빌려드릴 수 없습니다.
					// 아덴 왕국의 지원금만으로 이 많은 훈련소를 관리하는 것이 쉬운 일은 아니라서요.
				}
			} else {
				htmlid = "bosskey6";
				// 이미 훈련소 열쇠를 가지고 계신 것 같군요.
				// 많은 분들께서 이용하실 수 있도록 훈련소는 한 사람 당 하나 씩만 대여해 드리고 있습니다.
			}
		} else if (s.equalsIgnoreCase("6")) { // 입장
			int countActiveMaps = BossTrainingSystem.getInstance().countRaidPotal();
			if (countActiveMaps < 100) {
				L1ItemInstance item = pc.getInventory().findItemId(80500);
				if (item != null) {
					int id = item.getKeyId();
					// L1Teleport.teleport(pc, 32901, 32814, (short) id,
					// 0, true);
					pc.start_teleport(32901, 32814, id, pc.getHeading(), 169, true, false);
				} else {
					htmlid = "bosskey2";
					// 훈련소 열쇠를 가지고 있지 않으신 것 같네요.
					// 먼저 훈련소를 대여하신 후에 사용하실 수 있습니다.
				}
			} else {
				htmlid = "bosskey3";
				// 죄송합니다.
				// 지금은 모든 훈련소에서 훈련이 진행 중 입니다.
			}
		}
        return htmlid;
    }

    static String valakasReady(L1PcInstance pc, String s, String htmlid) {
		if (s.equalsIgnoreCase("a")) {
			if (pc.getLevel() >= 60) {
				if (ValakasReadyStart.getInstance().countReadyRaid() >= 49) {
                        SC_SystemMessage.send(pc, "인스턴스 던전을 더 이상 생성할수 없습니다.");
                        return htmlid;
                    }
                    if (pc.getInventory().consumeItem(820001, 1)) {// 냉한의기운
					ValakasReadyStart.getInstance().startReady(pc);
				} else {
					htmlid = "eldnas1";
				}
			} else {
				htmlid = "eldnas3";
			}
		}
		// 진데스나이트
        return htmlid;
    }

    static String valakasRaid(L1PcInstance pc, String s, String htmlid) {
		if (s.equalsIgnoreCase("enter")) {
			if (pc.getLevel() >= 60) {
				if (!pc.getInventory().checkItem(203003, 1)) {// 데스나이트의
                    if (ValakasRoomSystem.getInstance().countStartRaid() >= 49) {
                        SC_SystemMessage.send(pc, "인스턴스 던전을 더 이상 생성할수 없습니다.");
                        return htmlid;
                    }
					// 불검:진
					if (pc.getInventory().storeItem(203003, 1) == null) return htmlid;// 데스나이트의
					// 불검:진
					ValakasRoomSystem.getInstance().startRaid(pc);
				}
			} else {
				htmlid = "fd_death2";
			}
		}
		/** 깃털마을 피아르 **/
        return htmlid;
    }

    static String orim(L1PcInstance pc, L1Object obj, String s, String htmlid) {
		L1ItemInstance item = null;
		L1NpcInstance npc = (L1NpcInstance) obj;
		if (s.equalsIgnoreCase("b")) { // 오림님의 이야기를 듣고 싶어요
			if (!OrimController.getInstance().getInDunOpen()) {
				if ((pc.isInParty()) && (pc.getParty().isLeader(pc))) {
					boolean isInMap = true; // 우선 맵에 있는걸로 선언 후
					for (L1PcInstance player : pc.getParty().getMembers()) {
						if (player.getMapId() != 0) {
							isInMap = false;
							break;
						} else if (!player.getInventory().checkItem(410096, 1)) {
							SC_SystemMessage.send(pc, "파티원의 누군가가 시공의 구슬이 없습니다.");
							SC_SystemMessage.send(player, "파티원의 누군가가 시공의 구슬이 없습니다.");
							return htmlid;
						}
					}
					if (pc.getParty().getNumOfMembers() > 1 && isInMap) {
						pc.getParty().getLeader().getName();
						OrimController Indun = OrimController.getInstance();
						Indun.start();
						L1Party party = pc.getParty();
						L1PcInstance[] players = party.getMembers();
						SC_PacketBox.broadcastGreen(pc.getParty().getLeader().getName() + " 님이 동료들과 함께 해상던전으로 떠났습니다.");
						L1World.getInstance().broadcastServerMessage(
								"\\aD" + pc.getParty().getLeader().getName() + " 님이 동료들과 함께 해상던전으로 떠났습니다.");
						for (L1PcInstance pc1 : players) {
							Indun.addPlayMember(pc1);
							pc1.getInventory().consumeItem(410096, 1);
							// L1Teleport.teleport(pc1, 32796, 32801,
							// (short)9101, pc1.getHeading(), true);
							pc1.start_teleport(32796, 32801, 9101, pc1.getHeading(), 169, true, false);
						}
					} else {
						// htmlid = "id0_1";
						// htmlid = "id0_3";
						SC_NpcChatPacket.sendNpc(pc, npc, "3명~5명의 파티원으로 구성되어야합니다.", 0);
					}
				} else
					htmlid = "id0_2";
			} else {
				SC_NpcChatPacket.sendNpc(pc, npc, "이미 해상던전으로 선발대가 출발했다네. 잠시후 다시오게.", 0);
				htmlid = "";
			}
		}
		if (s.equalsIgnoreCase("c")) { // 항아리 지급
			if (!pc.getInventory().checkItem(410095, 1)) {
				item = pc.getInventory().storeItem(410095, 1);
				if (item != null) SC_ServerMessage.send(pc, 143, "$7918", item.getName());
			} else {
				htmlid = "j_html03";
			}
		} else if (s.equalsIgnoreCase("a")) { // 비밀 연구실 텔
			return NpcTravelCommands.orimLaboratory(pc, obj.getId(), htmlid);
                } else if (s.equalsIgnoreCase("d")) { // 일기장 복원
                    return NpcExchangeCommands.diary(pc, obj.getId(), false);
                } else if (s.equalsIgnoreCase("e")) {
                    return NpcExchangeCommands.diary(pc, obj.getId(), true);
                }
        return htmlid;
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
