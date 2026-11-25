package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.CObjectDB;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.model.Instance.L1CataInstance;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_CloseList;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_SellHouse;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.model.world.siege.War;
import l1j.server.server.templates.L1House;
import l1j.server.server.templates.L1Npc;

final class NpcCastleActions {
    private NpcCastleActions() {}

    static boolean sendTaxScreen(L1PcInstance pc, int objId, String page, int castleId) {
        if (pc == null) return false;
        final CUserSocket socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket;
        if (connection == null || !connection.isClientConnected()) return false;
        final String value = Integer.toString(CastleWarBusiness.getInstance().getTaxRate(castleId));
        final long size = 24L + 4L * (page.length() + value.length());
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int)size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HYPERTEXT);
            buffer.writeD(objId);
            buffer.writeS(page);
            buffer.writeH(1);
            buffer.writeH(1);
            buffer.writeS(value);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

	static String sellHouse(L1PcInstance pc, int objectId, int npcId) {
		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
		if (clan == null) {
			return "";
		}
		int houseId = clan.getHouseId();
		if (houseId == 0) {
			return "";
		}
		L1House house = HouseTable.getInstance().getHouseTable(houseId);
		if (house == null) return "";
		int keeperId = house.getKeeperId();
		if (npcId != keeperId) {
			return "";
		}
		if (!pc.isCrown()) {
			SC_ServerMessage.send(pc, 518);
			return "";
		}
		if (pc.getId() != clan.getLeaderId()) {
			SC_ServerMessage.send(pc, 518);
			return "";
		}
		if (house.isOnSale()) {
			return "agonsale";
		}

		SC_SellHouse.send(pc, objectId, String.valueOf(houseId));
		return null;
	}

	static void openCloseDoor(L1PcInstance pc, L1NpcInstance npc, String s) {
		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
		if (clan != null) {
			int houseId = clan.getHouseId();
			if (houseId != 0) {
				L1House house = HouseTable.getInstance().getHouseTable(houseId);
				int keeperId = house.getKeeperId();
				if (npc.getNpcTemplate().get_npcId() == keeperId) {
					L1DoorInstance door1 = null;
					L1DoorInstance door2 = null;
					L1DoorInstance door3 = null;
					L1DoorInstance door4 = null;
					List<L1DoorInstance> doors = DoorSpawnTable.getInstance().getDoorList();
					for (L1DoorInstance door : doors) {
						if (door.getKeeperId() == keeperId) {
							if (door1 == null) {
								door1 = door;
								continue;
							}
							if (door2 == null) {
								door2 = door;
								continue;
							}
							if (door3 == null) {
								door3 = door;
								continue;
							}
							if (door4 == null) {
								door4 = door;
								break;
							}
						}
					}
					doors = null;
					if (door1 != null) {
						if (s.equalsIgnoreCase("open")) {
							door1.open();
						} else if (s.equalsIgnoreCase("close")) {
							door1.close();
						}
					}
					if (door2 != null) {
						if (s.equalsIgnoreCase("open")) {
							door2.open();
						} else if (s.equalsIgnoreCase("close")) {
							door2.close();
						}
					}
					if (door3 != null) {
						if (s.equalsIgnoreCase("open")) {
							door3.open();
						} else if (s.equalsIgnoreCase("close")) {
							door3.close();
						}
					}
					if (door4 != null) {
						if (s.equalsIgnoreCase("open")) {
							door4.open();
						} else if (s.equalsIgnoreCase("close")) {
							door4.close();
						}
					}
				}
			}
		}
	}

	static void openCloseGate(L1PcInstance pc, int keeperId, boolean isOpen) {
		boolean isNowWar = false;
		int pcCastleId = 0;
		if (pc.getClanid() != 0) {
			L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
			if (clan != null) {
				pcCastleId = clan.getCastleId();
			}
		}
		if (pcCastleId == 0) { // KKK 혈맹이 없거나, 성혈이 아닌경우
			return;
		}
		if (keeperId == 70656 || keeperId == 70549 || keeperId == 70985) {
			if (isExistDefenseClan(L1CastleLocation.KENT_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.KENT_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.KENT_CASTLE_ID);
		} else if (keeperId == 70600) { // OT
			if (isExistDefenseClan(L1CastleLocation.OT_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.OT_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.OT_CASTLE_ID);
		} else if (keeperId == 70778 || keeperId == 70987 || keeperId == 70687) {
			if (isExistDefenseClan(L1CastleLocation.WW_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.WW_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.WW_CASTLE_ID);
		} else if (keeperId == 70817 || keeperId == 70800 || keeperId == 70988 || keeperId == 70990 || keeperId == 70989
				|| keeperId == 70991) {
			if (isExistDefenseClan(L1CastleLocation.GIRAN_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.GIRAN_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.GIRAN_CASTLE_ID);

		} else if (keeperId == 70863 || keeperId == 70992 || keeperId == 70862) {
			if (isExistDefenseClan(L1CastleLocation.HEINE_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.HEINE_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.HEINE_CASTLE_ID);
		} else if (keeperId == 70995 || keeperId == 70994 || keeperId == 70993) {
			if (isExistDefenseClan(L1CastleLocation.DOWA_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.DOWA_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.DOWA_CASTLE_ID);
		} else if (keeperId == 70996) {
			if (isExistDefenseClan(L1CastleLocation.ADEN_CASTLE_ID)) {
				if (pcCastleId != L1CastleLocation.ADEN_CASTLE_ID) {
					return;
				}
			}
			isNowWar = CastleWarBusiness.getInstance().isNowWar(L1CastleLocation.ADEN_CASTLE_ID);
		}
		List<L1DoorInstance> doors = DoorSpawnTable.getInstance().getDoorList();
		
		for (L1DoorInstance door : doors) {
			if (door.getKeeperId() == keeperId) {
				if (isNowWar && door.getMaxHp() > 1) {
				} else {
					if (isOpen) {
						door.open();
					} else {
						door.close();
					}
				}
			}
		}
		doors = null;
	}

	static boolean isExistDefenseClan(int castleId) {
		boolean isExistDefenseClan = false;
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (castleId == clan.getCastleId()) {
				isExistDefenseClan = true;
				break;
			}
		}
		return isExistDefenseClan;
	}

	static void expelOtherClan(L1PcInstance clanPc, int keeperId) {

	    if (clanPc == null) {
	        return;
	    }

	    int houseId = 0;

	    for (L1House house : HouseTable.getInstance().getHouseTableList()) {
	        if (house == null) {
	            continue;
	        }
	        if (house.getKeeperId() == keeperId) {
	            houseId = house.getHouseId();
	            break;
	        }
	    }

	    if (houseId == 0) {
	        return;
	    }
	    final int[] loc =
	            L1HouseLocation.getHouseTeleportLoc(
	                    houseId,
	                    0);

	    if (loc == null || loc.length < 3) {
	        return;
	    }

	    final int clanId =
	            clanPc.getClanid();

	    for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {

	        if (pc == null) {
	            continue;
	        }

	        if (pc.getClanid() == clanId) {
	            continue;
	        }

	        if (!L1HouseLocation.isInHouseLoc(
	                houseId,
	                pc.getX(),
	                pc.getY(),
	                pc.getMapId())) {
	            continue;
	        }

	        pc.start_teleport(
	                loc[0],
	                loc[1],
	                loc[2],
	                pc.getHeading(),
	                169,
	                true,
	                false);
	    }
	}

	static void repairGate(L1PcInstance pc) {
		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
		if (clan != null) {
			int castleId = clan.getCastleId();
			if (castleId != 0) {
				if (!CastleWarBusiness.getInstance().isNowWar(castleId)) {
					List<L1DoorInstance> doors = DoorSpawnTable.getInstance().getDoorList();
					for (L1DoorInstance door : doors) {
						if (L1CastleLocation.checkInWarArea(castleId, door)) {
							door.repairGate();
						}
					}
					doors = null;
					SC_ServerMessage.send(pc, 990);
				} else {
					SC_ServerMessage.send(pc, 991);
				}
			}
		}
	}

	static void payFee(L1PcInstance pc, L1NpcInstance npc) {
		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
		if (clan == null || clan.getHouseId() <= 0) return;
		L1House house = HouseTable.getInstance().getHouseTable(clan.getHouseId());
		if (house == null || npc.getNpcTemplate().get_npcId() != house.getKeeperId()) return;
		Calendar deadline = house.getTaxDeadline();
		if (deadline == null) return;
		Calendar checkCal = Calendar.getInstance(TimeZone.getTimeZone(Config.TIME_ZONE));
		checkCal.add(Calendar.DATE, 3);
		checkCal.set(Calendar.MINUTE, 0);
		checkCal.set(Calendar.SECOND, 0);
		if (deadline.after(checkCal)) {
			SC_SystemMessage.send(pc, "더 이상 납부할 세금이 없습니다.");
			return;
		}
		l1j.server.server.model.L1AuctionTransaction.payTax(pc, house);
	}

	static String[] makeHouseTaxStrings(L1PcInstance pc, L1NpcInstance npc) {
		String name = npc.getNpcTemplate().get_name();
		String[] result;
		result = new String[] { name, "2000", "1", "1", "00" };
		L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
		if (clan != null) {
			int houseId = clan.getHouseId();
			if (houseId != 0) {
				L1House house = HouseTable.getInstance().getHouseTable(houseId);
				int keeperId = house.getKeeperId();
				if (npc.getNpcTemplate().get_npcId() == keeperId) {
					Calendar cal = house.getTaxDeadline();
					int month = cal.get(Calendar.MONTH) + 1;
					int day = cal.get(Calendar.DATE);
					int hour = cal.get(Calendar.HOUR_OF_DAY);
					result = new String[] { name, "2000", String.valueOf(month), String.valueOf(day),
							String.valueOf(hour) };
				}
			}
		}
		return result;
	}

	static String[] makeWarTimeStrings(int castleId) {		
		Calendar warTime = CastleWarBusiness.getInstance().get(castleId).nextCal();
		int year = warTime.get(Calendar.YEAR);
		int month = warTime.get(Calendar.MONTH) + 1;
		int day = warTime.get(Calendar.DATE);
		int hour = warTime.get(Calendar.HOUR_OF_DAY);
		int minute = warTime.get(Calendar.MINUTE);
		String[] result;
		if (castleId == L1CastleLocation.OT_CASTLE_ID) {
			result = new String[] { String.valueOf(year), String.valueOf(month), String.valueOf(day),
					String.valueOf(hour), String.valueOf(minute) };
		} else {
			result = new String[] { "", String.valueOf(year), String.valueOf(month), String.valueOf(day),
					String.valueOf(hour), String.valueOf(minute) };
		}
		return result;
	}

    /** Tax replies do not terminate subsequent legacy dispatch. */
    static void sendTaxScreens(L1PcInstance pc, L1Object obj, String s) {
            // Castle tax NPC screens: serialize the hypertext reply directly.
            if (s.equalsIgnoreCase("andyn3")) {
                sendTaxScreen(pc, obj.getId(), "andyn3", 1);
            } else if (s.equalsIgnoreCase("vola3")) {
                sendTaxScreen(pc, obj.getId(), "vola3", 3);
            } else if (s.equalsIgnoreCase("s_merchant3")) {
                sendTaxScreen(pc, obj.getId(), "s_merchant3", 4);
            } else if (s.equalsIgnoreCase("vergil3")) {
                sendTaxScreen(pc, obj.getId(), "vergil3", 4);
            } else if (s.equalsIgnoreCase("shivan3")) {
                sendTaxScreen(pc, obj.getId(), "shivan3", 5);
            } else if (s.equalsIgnoreCase("ralf6")) {
                sendTaxScreen(pc, obj.getId(), "ralf6", 6);
            } else if (s.equalsIgnoreCase("defman3")) {
                sendTaxScreen(pc, obj.getId(), "defman3", 6);
            } else if (s.equalsIgnoreCase("fega3")) {
                sendTaxScreen(pc, obj.getId(), "fega3", 6);
            }

    }


    static String catapult(L1PcInstance pc, L1Object obj, String s, String htmlid) {
				if (s.equalsIgnoreCase("0-5") // 외성문 방향으로 발사!
						|| s.equalsIgnoreCase("0-6") // 내성문 방향으로 발사!
						|| s.equalsIgnoreCase("0-7") // 수호탑 방향으로 발사!
						|| s.equalsIgnoreCase("1-16") // 외성문 방향으로 침묵포탄 발사!
						|| s.equalsIgnoreCase("1-17") // 내성문 앞쪽으로 침묵포탄 발사!
						|| s.equalsIgnoreCase("1-18") // 내성문 좌측으로 침묵포탄 발사!
						|| s.equalsIgnoreCase("1-19") // 내성문 우측으로 침묵포탄 발사!
						|| s.equalsIgnoreCase("1-20") // 수호탑 방향으로 침묵포탄 발사!
						// 수성
						|| s.equalsIgnoreCase("0-9") // 외성문 방향으로 발사!
				) {
					int locx = 0;
					int locy = 0;
					int gfxid = 0;
					int castleid = 0;
					int npcId = ((L1NpcInstance) obj).getNpcTemplate().get_npcId();
					if (s.equalsIgnoreCase("0-5")) { // 외성문 방향으로 발사!
						switch (npcId) {
						case 7000086: // 5시 방향 공성 오크요새 공성측
							locx = 32795;
							locy = 32315;
							gfxid = 12197; // 우측
							castleid = 2;
							break;
						case 7000082: // 5시 방향 공성 기란성 공성측
							locx = 33632;
							locy = 32731;
							gfxid = 12197; // 우측
							castleid = 4;
							break;
						case 7000084: // 7시 방향 공성 켄트성 공성측
							locx = 33114;
							locy = 32771;
							gfxid = 12193; // 좌측
							castleid = 1;
							break;
						}
					} else if (s.equalsIgnoreCase("0-6")) { // 내성문 방향으로 발사!
						switch (npcId) {
						case 7000086: // 11시 방향 공성 오크요새 공성측
							locx = 32798;
							locy = 32268;
							gfxid = 12197; // 우측
							castleid = 2;
							break;
						case 7000082: // 11시 방향 공성 기란성 공성측
							locx = 33632;
							locy = 32664;
							gfxid = 12197; // 우측
							castleid = 4;
							break;
						case 7000084: // 2시 방향 공성 켄트성 공성측
							locx = 33171;
							locy = 32763;
							gfxid = 12197; // 좌측
							castleid = 1;
							break;
						}
					} else if (s.equalsIgnoreCase("0-7")) { // 수호탑 방향으로 발사!
						switch (npcId) {
						case 7000086: // 11시 방향 공성 오크요새 공성측
							locx = 32798;
							locy = 32285;
							gfxid = 12197; // 우측
							castleid = 2;
							break;
						case 7000082: // 11시 방향 공성 기란성 공성측
							locx = 33631;
							locy = 32678;
							gfxid = 12197; // 우측
							castleid = 4;
							break;
						case 7000084: // 2시 방향 공성 켄트성 공성측
							locx = 33168;
							locy = 32779;
							gfxid = 12197; // 좌측
							castleid = 1;
							break;
						}
					} else if (s.equalsIgnoreCase("0-9")) { // 외성문 방향으로 발사!
						int pcCastleId = 0;
						if (pc.getClanid() != 0) {
							L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
							if (clan != null) {
								pcCastleId = clan.getCastleId();
							}
						}
						switch (npcId) {
						case 7000087: // 11시 방향 공성 오크요새 수성측
							if (isExistDefenseClan(L1CastleLocation.OT_CASTLE_ID)) {
								if (pcCastleId != L1CastleLocation.OT_CASTLE_ID) {
									SC_ServerMessage.send(pc, 3682);
									// 투석기 사용: 실패(성을 수호하는 성혈 군주만 사용 가능)
									return htmlid;
								}
							}
							locx = 32794;
							locy = 32320;
							gfxid = 12193; // 우측
							castleid = 2;
							break;
						case 7000083: // 11시 방향 공성 기란성 수성측
							if (isExistDefenseClan(L1CastleLocation.GIRAN_CASTLE_ID)) {
								if (pcCastleId != L1CastleLocation.GIRAN_CASTLE_ID) {
									SC_ServerMessage.send(pc, 3682);
									// 투석기 사용: 실패(성을 수호하는 성혈 군주만 사용 가능)
									return htmlid;
								}
							}
							locx = 33631;
							locy = 32738;
							gfxid = 12193; // 우측
							castleid = 4;
							break;
						case 7000085: // 2시 방향 공성 켄트성 수성측
							if (isExistDefenseClan(L1CastleLocation.KENT_CASTLE_ID)) {
								if (pcCastleId != L1CastleLocation.KENT_CASTLE_ID) {
									SC_ServerMessage.send(pc, 3682);
									// 투석기 사용: 실패(성을 수호하는 성혈 군주만 사용 가능)
									return htmlid;
								}
							}
							locx = 33107;
							locy = 32770;
							gfxid = 12197; // 우측
							castleid = 1;
							break;
						}

						/*
						 * <a action="1-16">외성문 방향으로 침묵포탄 발사!</a><br> <a action="1-17">내성문 앞쪽으로 침묵포탄
						 * 발사!</a><br> <a action="1-18">내성문 좌측으로 침묵포탄 발사!</a><br> <a action="1-19">내성문
						 * 우측으로 침묵포탄 발사!</a><br> <a action="1-20">수호탑 방향으로 침묵포탄 발사!</a><br><br> } else
						 * if (s.equalsIgnoreCase("0-9")) { //외성문 방향으로 침묵포탄 발사!
						 */

					} else {
						SC_SystemMessage.send(pc, "침묵포탄은 사용 불가능 합니다.");
						return htmlid;
					}

					boolean isNowWar = castleid != 0 && CastleWarBusiness.getInstance().isNowWar(castleid);
					if (!isNowWar) {
						SC_ServerMessage.send(pc, 3683);
						// 투석기 사용: 실패(공성 시간에만 사용 가능)
						return htmlid;
					}
					//
					boolean inWar = War.isNowWar(pc.getClan());
					if (!(pc.isCrown() && inWar && isNowWar)) {
						SC_ServerMessage.send(pc, 3681);
						// 투석기 사용: 실패(전쟁을 선포한 군주만 사용 가능)
						return htmlid;
					}
					if (pc.getlastShellUseTime() + 10000L > System.currentTimeMillis()) {
						SC_ServerMessage.send(pc, 3680);
						// 투석기 사용: 실패(재장전 시간 필요)
						return htmlid;
					}

					if (obj != null) {
						if (obj instanceof L1CataInstance) {
							L1CataInstance npc = (L1CataInstance) obj;
							if (pc.getInventory().consumeItem(30124, 1)) {
								SC_DoActionGFX.broadcast(
										npc,
										ActionCodes.ACTION_Attack);
								long packet = SC_EffectLocation.pack(locx, locy, gfxid);
								SC_EffectLocation.sendPacked(pc, packet);
								SC_EffectLocation.broadcastWidePacked(pc, packet);
								getShellDmg(locx, locy);
								// 침묵포탄(locx, locy); // 침묵포탄 테스트
								pc.updatelastShellUseTime();
							} else {
								SC_ServerMessage.send(pc, 337, "$16785");
							}
						}
					}
				}
        return htmlid;
    }

	private static void getShellDmg(int locx, int locy) {
		L1PcInstance targetPc = null;
		L1NpcInstance targetNpc = null;
		L1EffectInstance effect = L1EffectSpawn.getInstance().spawnEffect(81154, 1 * 1000, locx, locy, (short) 4);
		if (effect == null) return;
		for (L1Object object : L1World.getInstance().getVisibleObjects(effect, 3)) {
			if (object == null) {
				continue;
			}
			if (!(object instanceof L1Character)) {
				continue;
			}
			if (object.getId() == effect.getId()) {
				continue;
			}

			if (object instanceof L1PcInstance) {
				targetPc = (L1PcInstance) object;
				SC_DoActionGFX.send(
						targetPc.getNetConnection(),
						targetPc.getId(),
						ActionCodes.ACTION_Damage);
				SC_DoActionGFX.broadcast(
						targetPc,
						ActionCodes.ACTION_Damage);
				targetPc.receiveDamage(targetPc, 100, 3);
			} else if (object instanceof L1SummonInstance || object instanceof L1PetInstance) {
				targetNpc = (L1NpcInstance) object;
				SC_DoActionGFX.broadcast(
						targetNpc,
						ActionCodes.ACTION_Damage);
				targetNpc.receiveDamage(targetNpc, (int) 100);
			}
		}
	}

	static String redKnight(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("j")) {// 가입
					SC_ServerMessage.send(pc, 3854); // 52레벨 이상만 붉은
																// 기사단에 가입이
																// 가능합니다.

					/*
					 * if (pc.getLevel() < 52) { SC_ServerMessage.send(pc, 3854); // 52레벨
					 * 이상만 붉은 기사단에 가입이 가능합니다. return htmlid; }
					 * 
					 * if (pc.getRedKnightClanId() == 0) { CastleWarBusiness business =
					 * CastleWarBusiness.getInstance(); int castleId =
					 * business.isNowReady(L1CastleLocation.GIRAN_CASTLE_ID) ?
					 * L1CastleLocation.GIRAN_CASTLE_ID :
					 * business.isNowReady(L1CastleLocation.OT_CASTLE_ID) ?
					 * L1CastleLocation.OT_CASTLE_ID :
					 * business.isNowReady(L1CastleLocation.KENT_CASTLE_ID) ?
					 * L1CastleLocation.KENT_CASTLE_ID : 0; if(castleId == 0){ pc.sendPackets(new
					 * SC_SystemMessage("공성: 가입 실패 (가입 시간 지남)" )); return htmlid; }
					 * 
					 * pc.setRedKnightClanId(castleId); pc.start_teleport(pc.getX(), pc.getY(),
					 * pc.getMapId(), pc.getHeading(), 169, false, false); pc.sendPackets(new
					 * SC_ServerMessage(3764)); // 공성: 붉은 기사단 가입 성공 } else pc.sendPackets(new
					 * SC_ServerMessage(3765)); // 공성: 가입 실패 (가입 상태)
					 */
					return htmlid;
				} else if (s.equalsIgnoreCase("d")) {// 탈퇴
					if (pc.getRedKnightClanId() == 0) {
						SC_SystemMessage.send(pc, "가입되어 있지 않습니다.");
						return htmlid;
					}

					pc.setRedKnightClanId(0);
					SC_SystemMessage.send(pc, "붉은 기사단을 탈퇴하였습니다.");
					pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, false);
					return htmlid;
				}

				/** 카이저 */
		return htmlid;
	}


    /** Validate admission and template before collecting the mercenary fee. */
    static boolean hireMercenary(L1PcInstance pc, L1NpcInstance npc, boolean kent) {
        final int rank = pc.getClanRank();
        if (rank != L1Clan.군주 && rank != L1Clan.수호 && rank != L1Clan.부군주
                && rank != L1Clan.CLAN_RANK_LEAGUE_GUARDIAN) {
            SC_SystemMessage.send(pc, "용병 구입은 수호기사 이상만 가능합니다.");
            return false;
        }
        if (!pc.getPetList().isEmpty()) {
            SC_SystemMessage.send(pc, "이미 무언가를 끌고다니시면 이용 불가합니다.");
            return false;
        }
        final int castleId = L1CastleLocation.getCastleIdByArea(npc);
        if (castleId > 0 && CastleWarBusiness.getInstance().isNowWar(castleId)) {
            SC_SystemMessage.send(pc, "해당성의 공성전이 진행중에는 이용 불가능합니다.");
            return false;
        }
        final L1Npc template = CObjectDB.LoadNPC().getTemplate(kent ? 7320031 : 7320000);
        if (template == null) {
            SC_SystemMessage.send(pc, "용병을 고용할 수 없습니다.");
            return false;
        }
        if (!pc.getInventory().consumeItem(40308, 300000)) {
            SC_SystemMessage.send(pc, "용병 구입은 아데나 30만 입니다.");
            return false;
        }
        final L1SummonInstance summon = new L1SummonInstance(template, pc);
        summon.setPetcost(6);
        SC_CloseList.send(pc, pc.getId());
        SC_ChatPacket.sendDirect(pc, pc, kent ? "켄트성 용병이 고용 되었습니다. 1시간 동안 이용가능합니다"
                : "용병이 고용 되었습니다. 1시간 동안 이용가능합니다", 1);
        return true;
    }
}
