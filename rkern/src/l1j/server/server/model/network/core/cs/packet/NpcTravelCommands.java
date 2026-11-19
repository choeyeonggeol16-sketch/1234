package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.L1InventoryTransaction;
import java.util.Random;
import l1j.server.server.Controller.잊섬Controller;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

final class NpcTravelCommands {
    private NpcTravelCommands() {}

    static boolean isPcRoomTeleporter(int npcId) {
        return switch (npcId) {
            case 50024, 9000, 50082, 50054, 50056, 50020, 50036, 5069, 7320051, 50066, 50039, 50051, 50046, 50079, 3000005, 3100005 -> true;
            default -> false;
        };
    }

    static String pcRoomTeleport(L1PcInstance pc, int npcId, String command, String htmlid) {
        if (!command.equalsIgnoreCase("T_pcbang")) return htmlid;
        if (!pc.PC방_버프) {
            SC_SystemMessage.send(pc, "PC방 이용권을 사용중에만 사용 가능한 행동입니다.");
            return htmlid;
        }
        final int itemId = npcId == 9000 ? 140100 : 40308;
        final int count = npcId == 9000 ? 1 : 1000;
        // Keep existing admission check; do not teleport on a failed debit.
        if (pc.getInventory().checkItem(itemId, count) && pc.getInventory().consumeItem(itemId, count)) {
            pc.start_teleport(32769, 32837, 622, pc.getHeading(), 169, true, false);
        } else {
            SC_SystemMessage.send(pc, npcId == 9000 ? "축복받은 순간이동 주문서가 필요합니다." : "1,000 아데나가 필요합니다.");
        }
        return htmlid;
    }


	static String waterDragonDungeon(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("1")) {
					if (pc.getLevel() >= 75 & pc.getLevel() <= 80) {
						pc.start_teleport(32774, 32805, 814, pc.getHeading(), 169, true, false);
						htmlid = "";
					} else {
						htmlid = "newbieddw2";
					}
				}
		return htmlid;
	}

	static String peter(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("1")) {
					pc.start_teleport(32770, 32826, 75, pc.getHeading(), 169, true, false);
					htmlid = "";
				}
				if (s.equalsIgnoreCase("2")) {
					pc.start_teleport(32772, 32823, 76, pc.getHeading(), 169, true, false);
					htmlid = "";
				}
				if (s.equalsIgnoreCase("3")) {
					pc.start_teleport(32762, 32839, 77, pc.getHeading(), 169, true, false);
					htmlid = "";
				}
		return htmlid;
	}

	static String jeep(L1PcInstance pc, String s, String htmlid, Random _random) {
																						// 리뉴얼
				if (s.equals("teleportURL")) {// 액션코드
					if (pc.getInventory().checkItem(40308, 7777)
                            && pc.getInventory().consumeItem(40308, 7777)) {
						int rx = _random.nextInt(7);
						int ux = 33435 + rx;
						int uy = 32812 + rx;
						pc.start_teleport(ux, uy, 4, pc.getHeading(), 169, true, false);
					}
				} else {
					SC_SystemMessage.send(pc, "시스템: 아데나(7,000원)이 부족합니다.");
				}
		return htmlid;
	}

	static String piar(L1PcInstance pc, String s, String htmlid, Random _random) {
				if (s.equalsIgnoreCase("a")) {
					if (!pc.PC방_버프) {
						SC_SystemMessage.send(pc, "PC방 이용권을 사용중에만 사용 가능한 행동입니다.");
						htmlid = "pc_tell2";
						return htmlid;
					}
					if (pc.getMap().isEscapable() || pc.isGm()) {
						int rx = _random.nextInt(7);
						int ux = 32768 + rx;
						int uy = 32834 + rx; // 상아탑
						pc.start_teleport(ux, uy, 622, pc.getHeading(), 169, true, false);
					}
				}
		return htmlid;
	}

	static String antCave(L1PcInstance pc, String s, String htmlid) {
				int locx = 0, locy = 0, map = 0;
				if (s.equalsIgnoreCase("b")) {// 1번동굴
					locx = 32783;
					locy = 32751;
					map = 43;
				} else if (s.equalsIgnoreCase("c")) {// 2번동굴
					locx = 32798;
					locy = 32754;
					map = 44;
				} else if (s.equalsIgnoreCase("d")) {// 3번동굴
					locx = 32776;
					locy = 32731;
					map = 45;
				} else if (s.equalsIgnoreCase("e")) {// 4번동굴
					locx = 32787;
					locy = 32795;
					map = 46;
				} else if (s.equalsIgnoreCase("f")) {// 5번동굴
					locx = 32796;
					locy = 32745;
					map = 47;
				} else if (s.equalsIgnoreCase("g")) {// 6번동굴
					locx = 32768;
					locy = 32805;
					map = 50;
				}
				if (locx == 0 || locy == 0) return htmlid;
				if (pc.getInventory().checkItem(40308, 500)
                        && pc.getInventory().consumeItem(40308, 500)) {
					pc.start_teleport(locx, locy, map, pc.getHeading(), 169, true, false);
				} else {
					htmlid = "cave2";
				}
		return htmlid;
	}

	static String dragonHerald(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("1")) {
					if (pc.getLevel() >= 56) {
						pc.start_teleport(32770, 32759, 30, pc.getHeading(), 169, true, false);
						htmlid = "";
					} else {
						htmlid = "dvdgate2";
					}
				}
				/** 클라우디아 라라 **/
		return htmlid;
	}

	static String lala(L1PcInstance pc, String s, String htmlid) {
				if (pc.getLevel() >= 1 & pc.getLevel() <= 55) {
					htmlid = "tel_lala1";
					pc.start_teleport(32646, 32865, 7783, pc.getHeading(), 169, true, false);
				}
				/** 전사단의 영혼 신규영입 이벤트 **/
		return htmlid;
	}

	static String gunterTravel(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().checkItem(447011, 1)) {
					} else {
						if (pc.getLevel() <= 90) {// 해당레벨이하일경우
							SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "군터: 퀘스트를 먼저 진행 하시오..");
							htmlid = "archgunter2";
							return htmlid;
						}
						if (pc.getLevel() >= 90) {// 해당레벨이상일경우
							htmlid = "archgunter1";
							SC_SystemMessage.send(pc, "군터: 퀘스트를 먼저 진행 하시오..");
							pc.start_teleport(32646, 32865, 7783, pc.getHeading(), 169, true, false);
						}
					}
				}
				/** 클라우디아 훈련교관 테온 **/
		return htmlid;
	}

	static String tamShop(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("teleport tamshop")) {
					pc.start_teleport(33964, 32953, 4, pc.getHeading(), 169, true, false);
				}
		return htmlid;
	}

	static String townTravel(L1PcInstance pc, String s, String htmlid) {
				int locx = 0, locy = 0, mapid = 0;
				if (s.equalsIgnoreCase("b")) {//
					locx = 33442;
					locy = 32797;
					mapid = 4;
				} else if (s.equalsIgnoreCase("C")) {//
					locx = 34056;
					locy = 32279;
					mapid = 4;
				} else if (s.equalsIgnoreCase("D")) {// 발라 둥지
					locx = 33705;
					locy = 32504;
					mapid = 4;
				} else if (s.equalsIgnoreCase("E")) {//
					locx = 33614;
					locy = 33253;
					mapid = 4;
				} else if (s.equalsIgnoreCase("F")) {//
					locx = 33050;
					locy = 32780;
					mapid = 4;
				} else if (s.equalsIgnoreCase("G")) {//
					locx = 32631;
					locy = 32770;
					mapid = 4;
				} else if (s.equalsIgnoreCase("H")) {//
					locx = 33080;
					locy = 33392;
					mapid = 4;
				} else if (s.equalsIgnoreCase("I")) {//
					locx = 32617;
					locy = 33201;
					mapid = 4;
				} else if (s.equalsIgnoreCase("J")) {// 오크 숲
					locx = 32741;
					locy = 32450;
					mapid = 4;
				} else if (s.equalsIgnoreCase("K")) {//
					locx = 32581;
					locy = 32940;
					mapid = 0;
				} else if (s.equalsIgnoreCase("L")) {//
					locx = 33958;
					locy = 33364;
					mapid = 4;
				} else if (s.equalsIgnoreCase("N")) {//
					locx = 32800;
					locy = 32927;
					mapid = 800;
				} else if (s.equalsIgnoreCase("V")) {// 데포류즈앞
					locx = 32595;
					locy = 33163;
					mapid = 4;
				}
				if (locx == 0 || locy == 0) return htmlid;
				if (pc.getInventory().checkItem(40308, 100)
                        && pc.getInventory().consumeItem(40308, 100)) {
					pc.start_teleport(locx, locy, mapid, pc.getHeading(), 169, true, false);
					htmlid = "";
				} else {
					htmlid = "pctel2";
				}
				// 리키 수련의텔레포트
		return htmlid;
	}

	static String ricky(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("a")) {// 숨겨진계곡
					if (pc.getLevel() >= 1 & pc.getLevel() <= 45) {
						pc.start_teleport(32684, 32851, 2005, pc.getHeading(), 169, true, false);
					} else {
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\fQ리키: \\f3[Lv.45]\\fQ이하만 입장 허용 레벨입니다.");
					}
				} else if (s.equalsIgnoreCase("b")) {// 기란마을
					pc.start_teleport(33436, 32799, 4, pc.getHeading(), 169, true, false);
				} else if (s.equalsIgnoreCase("c")) {// 라우풀신전
					if (pc.getLevel() >= 10 & pc.getLevel() <= 29) {
						pc.start_teleport(33184, 33449, 4, pc.getHeading(), 169, true, false);
					} else {
						SC_ChatPacket.sendDirect(pc, pc, "은기사 필드 이동 가능레벨 10 ~ 29");
					}
				} else if (s.equalsIgnoreCase("d")) {// 카오틱신전
					if (pc.getLevel() >= 10 & pc.getLevel() <= 29) {
						pc.start_teleport(33066, 33218, 4, pc.getHeading(), 169, true, false);
					} else {
						SC_ChatPacket.sendDirect(pc, pc, "은기사 필드 이동 가능레벨 10 ~ 29");
					}
				} else if (s.equalsIgnoreCase("f")) {// 수련던전
					if (pc.getLevel() >= 10 & pc.getLevel() < 20) {
						// L1Teleport.teleport(pc, 32801, 32806, (short) 25,
						// pc.getHeading(), true);
						pc.start_teleport(32801, 32806, 25, pc.getHeading(), 169, true, false);
					} else if (pc.getLevel() >= 20 & pc.getLevel() < 30) {
						// L1Teleport.teleport(pc, 32806, 32746, (short) 26,
						// pc.getHeading(), true);
						pc.start_teleport(32806, 32746, 26, pc.getHeading(), 169, true, false);
					} else if (pc.getLevel() >= 30 & pc.getLevel() < 40) {
						// L1Teleport.teleport(pc, 32808, 32766, (short) 27,
						// pc.getHeading(), true);
						pc.start_teleport(32808, 32766, 27, pc.getHeading(), 169, true, false);
					} else if (pc.getLevel() >= 40 & pc.getLevel() < 44) {
						// L1Teleport.teleport(pc, 32796, 32799, (short) 28,
						// pc.getHeading(), true);
						pc.start_teleport(32796, 32799, 28, pc.getHeading(), 169, true, false);
					} else {
						SC_ChatPacket.sendDirect(pc, pc, "수련 던전 이동 가능레벨 10 ~ 44");
					}
				} else if (s.equalsIgnoreCase("e")) {// 폭풍던전 불신 Lv 45~51
					if (pc.getLevel() >= 45 & pc.getLevel() <= 51) {
						// L1Teleport.teleport(pc, 32807, 32789, (short) 2010,
						// pc.getHeading(), true);
						pc.start_teleport(32807, 32789, 2010, pc.getHeading(), 169, true, false);
					} else {
						SC_ChatPacket.sendDirect(pc, pc, "폭풍 수련 던전 이동 가능레벨 45 ~ 51");
					}
				}
		return htmlid;
	}

	static String gardenNotice(L1PcInstance pc, String s, String htmlid, Random _random) {
				if (pc.getLevel() <= 99) {
					SC_SystemMessage.send(pc, "수상한 하늘정원(PC) 에서 정령의 오브를 통해 이동하세요.");
					return htmlid;
				}
				/*
				 * if (s.equalsIgnoreCase("D_abyss")) { int rx = _random.nextInt(3); int ry =
				 * _random.nextInt(3); int ux = 32855 + rx; int uy = 32859 + ry;
				 * pc.start_teleport(ux, uy, 623, pc.getHeading(), 169, true, false); }
				 */
				/** 지구르 천상 업데이트 **/
		return htmlid;
	}

	static String zigur(L1PcInstance pc, String s, String htmlid, Random _random) {
				if (pc.getLevel() < 75) {
					if (s.equalsIgnoreCase("A")) {
						int rx = _random.nextInt(3);
						int ry = _random.nextInt(3);
						int ux = 32855 + rx;
						int uy = 32859 + ry;
						pc.start_teleport(ux, uy, 623, pc.getHeading(), 169, true, false);
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "잠시후 수상한 천상의 계곡 으로 이동됩니다.");
					}
				} else {
					SC_SystemMessage.send(pc, "레벨 74 까지 입장 가능합니다.");
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "레벨 74 까지 입장 가능합니다. 그후 기란 감옥 으로 입장하세요.");
				}
				/** 스타크 공성장 이동 **/
		return htmlid;
	}

	static String siegeTravel(L1PcInstance pc, String s, String htmlid, Random _random) {
				if (s.equalsIgnoreCase("a")) { // 입장
					int rx = _random.nextInt(5);
					int ry = _random.nextInt(5);
					int ux = 33630 + rx;
					int uy = 32763 + ry;
					pc.start_teleport(ux, uy, 4, pc.getHeading(), 169, true, false);
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "기란 공성장으로 이동 됩니다.");
				} else if (s.equalsIgnoreCase("b")) {// 기란마을
					int rx = _random.nextInt(5);
					int ry = _random.nextInt(5);
					int ux = 33093 + rx;
					int uy = 32769 + ry;
					pc.start_teleport(ux, uy, 4, pc.getHeading(), 169, true, false);
					SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "켄트 공성장으로 이동 됩니다.");
				}
		return htmlid;
	}

	static String unicornTemple(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("a")) { // 입장
					if (pc.getInventory().checkItem(810000, 1)
                            && pc.getInventory().consumeItem(810000, 1)) {
						pc.start_teleport(32800, 32798, 1935, pc.getHeading(), 169, true, false);
					} else {
						SC_SystemMessage.send(pc, "입장을 위해 유니콘의 사원 열쇠가 필요합니다.");
					}
				} else if (s.equalsIgnoreCase("b")) {// 기란마을
					pc.start_teleport(33436, 32799, 4, pc.getHeading(), 169, true, false);
				}
		return htmlid;
	}

	static String forgottenIsland(L1PcInstance pc, String s, String htmlid, Random _random) {
				/*
				 * if (pc.getLevel() <= Config.잊섬입장레벨) { pc.sendPackets(new
				 * SC_SystemMessage("레벨(80) 이상만 입장이 가능 합니다.")); pc.sendPackets(new
				 * RPacketBox(84,"레벨(80) 이상만 입장이 가능 합니다.")); htmlid = "fg_isval_fl1"; return
				 * htmlid; }
				 */
				if (s.equalsIgnoreCase("a")) {
					if (잊섬Controller.getInstance().get잊섬Start() == true) {
						int i13 = 32786 + _random.nextInt(2);
						int k19 = 32761 + _random.nextInt(2);
						pc.start_teleport(i13, k19, 1710, pc.getHeading(), 169, true, false);
						SC_SystemMessage.send(pc, "잊혀진 섬 열린 시간부터 '1'시간 동안 입장이 가능합니다.");
					} else {
						SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "잊혀진 섬 시간을 확인하시길 바랍니다.");
						SC_SystemMessage.send(pc, "잊혀진 섬 시간을 확인하시길 바랍니다.");
						htmlid = "fg_isval_cl";
					}
				}
		return htmlid;
	}

	static String aldran(L1PcInstance pc, String s, String htmlid) {
				if (s.equals("a")) {
					if (pc.getInventory().checkItem(41207, 1)) {
						// L1Teleport.teleport(pc, 32674, 32871, (short) 550, 0,
						// true);
						pc.start_teleport(32674, 32871, 550, pc.getHeading(), 169, true, false);
						htmlid = "";
					} else {
						htmlid = "aldran9";
					}
				} else if (s.equals("b")) {
					if (pc.getInventory().checkItem(41207, 1)) {
						// L1Teleport.teleport(pc, 32778, 33009, (short) 550, 0,
						// true);
						pc.start_teleport(32778, 33009, 550, pc.getHeading(), 169, true, false);
						htmlid = "";
					} else {
						htmlid = "aldran9";
					}
				} else if (s.equals("c")) {
					if (pc.getInventory().checkItem(41207, 1)) {
						// L1Teleport.teleport(pc, 32471, 32766, (short) 550, 0,
						// true);
						pc.start_teleport(32471, 32766, 550, pc.getHeading(), 169, true, false);
						htmlid = "";
					} else {
						htmlid = "aldran9";
					}
				} else if (s.equals("d")) {
					if (pc.getInventory().checkItem(41207, 1)) {
						// L1Teleport.teleport(pc, 32511, 32998, (short) 550, 0,
						// true);
						pc.start_teleport(32511, 32998, 550, pc.getHeading(), 169, true, false);
						htmlid = "";
					} else {
						htmlid = "aldran9";
					}
				} else if (s.equals("e")) {
					if (pc.getInventory().checkItem(41207, 1)) {
						// L1Teleport.teleport(pc, 32998, 33028, (short) 558, 0,
						// true);
						pc.start_teleport(32998, 33028, 558, pc.getHeading(), 169, true, false);

						htmlid = "";
					} else {
						htmlid = "aldran9";
					}
				}

				/** 투석기 **/
		return htmlid;
	}


    static String pcRoom50015(L1PcInstance pc, String s, String htmlid) {
		if (s.equalsIgnoreCase("T_pcbang")) {
			if (!pc.PC방_버프) {
				SC_SystemMessage.send(pc, "PC방 이용권을 사용중에만 사용 가능한 행동입니다.");
				return htmlid;
			}
			if (pc.getInventory().checkItem(40308, 1000)
                            && pc.getInventory().consumeItem(40308, 1000)) {
				pc.start_teleport(32769, 32837, 622, pc.getHeading(), 169, true, false);
			} else {
				SC_SystemMessage.send(pc, "1,000 아데나가 필요합니다.");
			}
		}
        return htmlid;
    }

    static String orimLaboratory(L1PcInstance pc, int npcObjectId, String htmlid) {
        if (!pc.getInventory().checkItem(410096, 1) || !pc.getInventory().checkItem(40308, 10000)) return "j_html02";
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, 900135)) {
            if (!NpcExchangeCommands.debitLegacy(tx, pc, 410096, 1)
                    || !NpcExchangeCommands.debitLegacy(tx, pc, 40308, 10000)) return "j_html02";
            tx.onFailure(() -> SC_NPCTalkReturn.send(pc, npcObjectId, "j_html02"));
            tx.commitAfterInventoryRelease(() -> {
                pc.start_teleport(32744, 32860, 9100, pc.getHeading(), 169, true, false);
                SC_NPCTalkReturn.send(pc, npcObjectId, "");
            });
            return null;
        } catch (RuntimeException failure) {
            return "j_html02";
        }
    }


    static String 카너스(L1PcInstance pc, String s) {
		String htmlid = "";
		if (s.equals("a")) { // 기르타스 전방으로 이동
			pc.start_teleport(32855, 32862, 537, pc.getHeading(), 169, true, false);
		} else if (s.equals("b")) { // 전초기지로 이동
			pc.start_teleport(32804, 32864, 537, pc.getHeading(), 169, true, false);
		} else if (s.equals("d")) { // 전투 지역을 확인(5000아데나)
			if (pc.getInventory().checkItem(40308, 5000)) {
				pc.getInventory().consumeItem(40308, 5000);
				SC_SystemMessage.send(pc, "아데나는 감사하지만, 지금은 사용 할 수 없습니다.");
				htmlid = "";
			} else {
				htmlid = "canus3";
			}
		}
		return htmlid;
	}

    static String talkToDimensionDoor(L1PcInstance pc, L1NpcInstance npc, String s) {
		String htmlid = "";
		int protectionId = 0;
		int sealId = 0;
		int locX = 0;
		int locY = 0;
		short mapId = 0;
		if (npc.getNpcTemplate().get_npcId() == 80059) {
			protectionId = 40909;
			sealId = 40913;
			locX = 32773;
			locY = 32835;
			mapId = 607;
		} else if (npc.getNpcTemplate().get_npcId() == 80060) {
			protectionId = 40912;
			sealId = 40916;
			locX = 32757;
			locY = 32842;
			mapId = 606;
		} else if (npc.getNpcTemplate().get_npcId() == 80061) {
			protectionId = 40910;
			sealId = 40914;
			locX = 32830;
			locY = 32822;
			mapId = 604;
		} else if (npc.getNpcTemplate().get_npcId() == 80062) {
			protectionId = 40911;
			sealId = 40915;
			locX = 32835;
			locY = 32822;
			mapId = 605;
		}

		if (s.equalsIgnoreCase("a")) {
			// L1Teleport.teleport(pc, locX, locY, mapId, 5, true);
			pc.start_teleport(locX, locY, mapId, pc.getHeading(), 169, true, false);
			htmlid = "";
		} else if (s.equalsIgnoreCase("b")) {
			L1ItemInstance item = pc.getInventory().storeItem(protectionId, 1);
			if (item != null) {
				SC_ServerMessage.send(pc, 143, npc.getNpcTemplate().get_name(), item.getLogName());
			}
			htmlid = "";
		} else if (s.equalsIgnoreCase("c")) {
			htmlid = "wpass07";
		} else if (s.equalsIgnoreCase("d")) {
			if (pc.getInventory().checkItem(sealId)) {
				L1ItemInstance item = pc.getInventory().findItemId(sealId);
				pc.getInventory().consumeItem(sealId, item.getCount());
			}
		} else if (s.equalsIgnoreCase("e")) {
			htmlid = "";
		} else if (s.equalsIgnoreCase("f")) {
			if (pc.getInventory().checkItem(protectionId)) {
				pc.getInventory().consumeItem(protectionId, 1);
			}
			if (pc.getInventory().checkItem(sealId)) {
				L1ItemInstance item = pc.getInventory().findItemId(sealId);
				pc.getInventory().consumeItem(sealId, item.getCount());
			}
			htmlid = "";
		}
		return htmlid;
	}

    static String hyosue(L1PcInstance pc, String s, String htmlid) {
        if (!s.equalsIgnoreCase("1")) return htmlid;
        if (!pc.getInventory().checkItem(40308, 10000)
                || !pc.getInventory().consumeItem(40308, 10000)) return "hyosue1";
        pc.start_teleport(32789, 33186, 4, 5, 169, true, false);
        return "";
    }
    // Fixed destination data, never exposed or mutated by a request.
    private static final int[][] GHOST_LOC = {
						{ 32642, 34249, 32878, 33114, 32734, 32736, 32737, 32769, 33931, 33051, 32870, 33972, 33427, 33594, 33446, 32741 },
						{ 32956, 33453, 32653, 32939, 32831, 32814, 32684, 32800, 33347, 32340, 33254, 33363, 32814, 33244, 32757, 32284, },
						{ 0, 4, 4, 4, 278, 62, 63, 77, 4, 4, 4, 4, 4, 4, 4, 4, } };

    static void esmeralda(L1PcInstance pc, String command) {
        if (!"journey".equalsIgnoreCase(command)) return;
        int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(GHOST_LOC[0].length);
        pc.beginGhost(GHOST_LOC[0][index], GHOST_LOC[1][index], (short) GHOST_LOC[2][index], true, 8);
    }

    private static final int[] TOWER_X = {32800,32800,32800,32800,32796,32720,32720,32724,32722,32731};
    private static final int[] TOWER_Y = {32800,32800,32800,32800,32796,32821,32821,32822,32827,32856};

    static boolean towerGate(L1PcInstance pc, int objectId, String action) {
        if (action.equalsIgnoreCase("1")) {
            pc.start_teleport(33966,33253,4,5,169,true,false);
            return true;
        }
        final int floor = action.length() == 1 ? Character.toLowerCase(Character.toUpperCase(action.charAt(0))) - 'a' : -1;
        if (floor < 0 || floor >= TOWER_X.length) return false;
        paidTowerTravel(pc, 6000015, objectId, 41158, (floor+1)*10, 0,
                TOWER_X[floor], TOWER_Y[floor], 110+floor*10, 5, null);
        return true;
    }

    static void towerUpperFloor(L1PcInstance pc, int objectId, int npcId) {
        final int floor = npcId - 900090;
        if (floor < 0 || floor > 8) return;
        final int x = floor < 4 ? 32754 : floor == 4 ? 32635 : floor == 8 ? 32732 : 32637;
        final int y = floor < 4 ? 32747 : floor == 4 ? 32790 : floor == 8 ? 32857 : 32791;
        paidTowerTravel(pc, npcId, objectId, 40104+floor, 2, 300,
                x, y, pc.getMapId()+4, pc.getHeading(), "omantel2");
    }

    private static void paidTowerTravel(L1PcInstance pc, int npcId, int objectId,
            int token, int count, int adena, int x, int y, int map, int heading, String failurePage) {
        if (!pc.getInventory().checkItem(token,count) || (adena != 0 && !pc.getInventory().checkItem(40308,adena))) {
            towerFailure(pc,objectId,failurePage);
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,npcId)) {
            if ((adena != 0 && !NpcExchangeCommands.debitLegacy(tx,pc,40308,adena))
                    || !NpcExchangeCommands.debitRecipe(tx,pc,token,count)) {
                towerFailure(pc,objectId,failurePage);
                return;
            }
            tx.onFailure(() -> towerFailure(pc,objectId,failurePage));
            tx.commitAfterInventoryRelease(() -> pc.start_teleport(x,y,map,heading,169,true,false));
        } catch (RuntimeException failure) {
            towerFailure(pc,objectId,failurePage);
        }
    }

    private static void towerFailure(L1PcInstance pc, int objectId, String page) {
        if (page != null) SC_NPCTalkReturn.send(pc,objectId,page);
        else SC_SystemMessage.send(pc,"이동에 필요한 아이템이 부족하거나 거래를 완료할 수 없습니다.");
    }

}
