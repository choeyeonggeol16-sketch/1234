package l1j.server.server.model.item.teleport;


import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.MJDungeonTimer.DungeonTimeInformation;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.MJExpAmpSystem.MJExpAmplifierLoader;
import l1j.server.MJInstanceSystem.MJInstanceSpace;
import l1j.server.MJRaidSystem.MJRaidSpace;
import l1j.server.MJCompanion.Instance.MJCompanionTeleportHandler;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1HauntedHouse;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Instance.L1SupportInstance;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.packets.Protocol.Packet.SC_MAP_ID;
import l1j.server.server.packets.Protocol.Packet.SC_WORLD_PUT_OBJECT_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.model.network.core.sc.packet.SC_PinkName;
import l1j.server.server.model.network.core.sc.packet.SC_Liquor;

public class L1Teleport {

	private static final Logger LOG = Logger.getLogger(L1Teleport.class.getName());

	private static final L1Teleport instance = new L1Teleport();

	public static L1Teleport getInstance() {
		return instance;
	}

	private static final ThreadLocal<ArrayList<L1PcInstance>> TL_SUBJECTS =
			ThreadLocal.withInitial(() -> new ArrayList<L1PcInstance>(32));

	public void doTeleport(L1PcInstance pc, int x, int y, int mapid) {
		if (pc == null) {
			return;
		}
		teleportInternal(pc, x, y, mapid);
	}

	public void doTeleportation(L1PcInstance pc) {
		if (pc == null || !pc.get_teleport()) return;

		if (pc.isDead() && !MJRaidSpace.getInstance().isInInstance(pc) && !MJInstanceSpace.isInInstance(pc)) {
			return;
		}
		teleportInternal(pc, pc.get_teleport_x(), pc.get_teleport_y(), pc.get_teleport_map());
	}

	private void teleportInternal(L1PcInstance pc, int x, int y, int mapid) {
		final int oldMapId = pc.getMapId();
		final int oldX = pc.getX();
		final int oldY = pc.getY();
		L1Map map = L1WorldMap.getInstance().getMap((short) mapid);

		// 위치 검증 
		if (map == null || (!map.isInMap(x, y) && !pc.isGm())) {
			x = pc.getX();
			y = pc.getY();
			mapid = oldMapId;
		}
		
		L1Clan clan = pc.getClan();
		if (clan != null) {
			clan.deleteClanRetrieveUser(pc.getId());
		}

		final ArrayList<L1PcInstance> subjects = TL_SUBJECTS.get();
		subjects.clear();
		addSubject(subjects, pc);

		try {
	        clearPcFromOldViewers(pc);

			/*
			 * Owner teleport:
			 * 같은 맵 랜덤 텔레포트라도 펫/서먼/컴패니언이 기존 몹의
			 * target/hate를 들고 있으면 새 위치에서 그 몹을 다시 찾아간다.
			 *
			 * 실제 좌표 이동 전에 기존 전투 상태를 먼저 끊는다.
			 */
			clearOwnedCombatTargets(pc);
	          
			// 물리적 좌표 이동 및 패킷 동기화 시작
			performPhysicalMove(pc, x, y, mapid);

			// UI 및 기본 필수 상태값 갱신
			updateEssentialState(pc);

			// 가시 범위(Known Objects) 처리
			refreshVisibility(pc, oldMapId, oldX, oldY);

			// 버프 및 특수 효과 동기화
			syncBuffsAndEffects(pc);

			// 동행물(펫/인형/서포트) 통합 처리
			processCompanions(pc, mapid, subjects);

			/*
			 * 텔레포트 중간에 owner AI가 한 번 깨어난 경우까지 정리.
			 * tpEndFinish() 전에 한 번 더 비워서 이전 사냥 타겟이
			 * 텔레포트 완료 뒤 살아나는 것을 막는다.
			 */
			clearOwnedCombatTargets(pc);

			// 가시 범위 내 모든 대상 강제 업데이트
			for (int i = 0, size = subjects.size(); i < size; i++) {
				final L1PcInstance updatePc = subjects.get(i);
				updatePc.onWorldMove(
						updatePc.getMapId(),
						updatePc.getX(),
						updatePc.getY());
			}

			// 맵별 컨텐츠 부가 로직 처리 
			processMapContentLogic(pc, oldMapId, mapid);

			MJCompanionTeleportHandler.on_teleported(pc, x, y, mapid);

		} catch (Exception e) {
			LOG.log(
					Level.SEVERE,
					"Teleport failed. pcId=" + pc.getId()
							+ ", from=" + oldMapId + ":" + oldX + "," + oldY
							+ ", to=" + mapid + ":" + x + "," + y,
					e);
		} finally {
			// 최종 언락 및 메모리 정리 
			pc.tpEndFinish();
			subjects.clear();
		}
	}

	private void performPhysicalMove(L1PcInstance pc, int x, int y, int mapid) {
		
		final int oldX = pc.getX();
		final int oldY = pc.getY();
		final short oldMapId = pc.getMapId();
		
		final L1World world = L1World.getInstance();

	    world.relocateVisibleObject(pc, x, y, mapid);
	    pc.setHeading(pc.getHeading());
		SC_MAP_ID.sendCreate(pc, pc.getMap().getBaseMapId(), pc.getMap().isUnderwater());
	    
	 
//	    System.out.println("[TELE DEBUG] 목적지: x=" + x + " y=" + y + " mapid=" + mapid 
//	    	    + " | 실제상태: pc.getX()=" + pc.getX() + " pc.getMapId()=" + pc.getMapId()
//	    	    + " | getMap().getId()=" + pc.getMap().getId() 
//	    	    + " | getBaseMapId()=" + pc.getMap().getBaseMapId());
	    
		TileOccupancy.getInstance().teleportRelaxed(
				oldMapId,
				oldX,
				oldY,
				mapid,
				x,
				y,
				pc.getId());
		MJExpAmplifierLoader.getInstance().set(pc);
	}

	private void updateEssentialState(L1PcInstance pc) {
		// 안전지대 UI 
		boolean isSafety = pc.getZoneType() != 0;
		if (pc.getSafetyZone() != isSafety) {
			SC_ActionUI.send(pc, SC_ActionUI.SAFETYZONE, isSafety);
			pc.setSafetyZone(isSafety);
		}

		if (pc.isReserveGhost()) pc.endGhost();

		// 낚시 종료 처리
		if (pc.getMapId() == 5490) {
			pc.setFishing(false);
			SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
			FishingTimeController.getInstance().removeMember(pc);
		}
		
		updateRulerIcon(pc);
	}

	private void refreshVisibility(
			L1PcInstance pc,
			int oldMap,
			int oldX,
			int oldY) {

		if (pc.getMapId() != 631) {
		    SC_WorldPutObject.broadcastPc(pc);
		}

		SC_WorldPutObject.put(
		        pc.getNetConnection(),
		        pc);

		pc.removeAllKnownObjects();
		pc.onWorldMove(oldMap, oldX, oldY);
		CIOThreadCallback.getInstance().clearPcState(pc);
		
		if (pc.isInParty()) {
			pc.getParty().refreshPartyMemberStatus(pc);
		}

		pc.sendVisualEffectAtTeleport();
		SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
	}

	private void syncBuffsAndEffects(L1PcInstance pc) {
		if (!pc.hasSkillEffect(L1SkillId.비욘드)) {
			pc.killSkillEffectTimer(L1SkillId.MEDITATION);
		}
		pc.setCallClanId(0);

		if (pc.isPinkName()) {
			SC_PinkName.send(pc, pc.getId(), pc.getPinkNameTime());
		}

		if (pc.hasSkillEffect(L1SkillId.STATUS_DRAGON_PEARL)) {
			int sec = pc.getSkillEffectTimeSec(L1SkillId.STATUS_DRAGON_PEARL);
			SC_Liquor.send(pc, pc.getId(), 8);
			SC_PacketBox.send(pc, SC_PacketBox.DRAGON_PEARL, 8, sec / 4);
			pc.setPearl(1);
		}

		if (pc.isGm() && pc.hasSkillEffect(L1SkillId.INVISIBILITY)) {
			SC_RemoveObject.broadcast(pc);
		}
		
		// 주변 핑크네임 유저 확인
		L1Party party = pc.getParty();
		for (L1PcInstance visible : L1World.getInstance().getVisiblePlayerFast(pc)) {
			if (visible.isPinkName()) {
				SC_PinkName.send(pc, visible.getId(), visible.getPinkNameTime());
			}
			if (party != null && party.isMember(visible)) {
				party.refreshPartyMemberStatus(visible);
			}
		}
	}

	private void clearOwnedCombatTargets(final L1PcInstance pc) {
		if (pc == null) {
			return;
		}

		/*
		 * Pet / Summon은 petList 공동 소유.
		 * allTargetClear()로 target뿐 아니라 hate/dropHate와
		 * 현재 추격/action 상태까지 함께 끊는다.
		 */
		if (!pc.getPetList().isEmpty()) {
			for (L1NpcInstance npc : pc.getPetList().values()) {
				if (npc == null) {
					continue;
				}

				if (npc instanceof L1PetInstance
						|| npc instanceof L1SummonInstance) {
					npc.allTargetClear();
				}
			}
		}

		/*
		 * Companion은 petList와 별도 보관.
		 */
		final L1CompanionInstance companion =
				pc.get_companion();

		if (companion != null) {
			companion.allTargetClear();
		}
	}

	private void processCompanions(L1PcInstance pc, int mapid, ArrayList<L1PcInstance> subjects) {
		boolean canTakePets = !pc.isGhost() && pc.getMap().isTakePets() && isPetAllowedMap(pc.getMapId());

		// 1. 펫 & 소환수 
		if (!pc.getPetList().isEmpty()) {
			for (L1NpcInstance petNpc : pc.getPetList().values()) {
				if (canTakePets) {
					moveFollower(pc, petNpc, mapid, subjects);
					if (petNpc instanceof L1SummonInstance) {
						SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance(pc, (L1SummonInstance) petNpc, pc);
					} else if (petNpc instanceof L1PetInstance) {
					    pc.broadcastPacket(SC_WORLD_PUT_OBJECT_NOTI.newInstance(petNpc).encode());
					}
				} else {
					removePetGracefully(pc, petNpc);
				}
			}
		}

		// 2. 컴패니언
		final L1CompanionInstance companion =
				pc.get_companion();

		if (companion != null) {
			companion.allTargetClear();

			if (canTakePets) {
				moveFollower(
						pc,
						companion,
						mapid,
						subjects);

				companion.onPerceive(pc);
			}
		}

		// 3. 마법인형
		if (!pc.getDollList().isEmpty()) {
			for (L1DollInstance doll : pc.getDollList().values()) {
				if (canTakePets) {
					moveFollower(pc, doll, mapid, subjects);
					SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance(pc, doll);
				} else {
					doll.deleteDoll(false);
				}
			}
		}

		// 4. 서포트
		if (!pc.getSupportList().isEmpty()) {
			for (L1SupportInstance support : pc.getSupportList().values()) {
				if (canTakePets) {
					moveFollower(pc, support, mapid, subjects);
					l1j.server.server.model.network.core.sc.packet.SC_SupportPack.send(pc, support);
				} else {
					support.deleteSupport();
				}
			}
		}
	}

	private void moveFollower(L1PcInstance owner, L1NpcInstance follower, int mapid, ArrayList<L1PcInstance> subjects) {
		L1Location loc = owner.getLocation().randomLocation(3, false);
		int nx = loc.getX();
		int ny = loc.getY();

		if (isFixedCoordMap(owner.getMapId())) {
			nx = 32799 + CommonUtil.random(5) - 3;
			ny = 32864 + CommonUtil.random(5) - 3;
		}

		teleport(follower, nx, ny, (short) mapid, owner.getHeading());

		final L1World world = L1World.getInstance();
		for (L1PcInstance visible : world.getVisiblePlayerFast(follower)) {
			visible.removeKnownObject(follower);
			addSubject(subjects, visible);
		}
	}

	private static void addSubject(
			ArrayList<L1PcInstance> subjects,
			L1PcInstance pc) {

		if (pc == null) {
			return;
		}

		for (int i = 0, size = subjects.size(); i < size; i++) {
			final L1PcInstance current = subjects.get(i);

			if (current == pc || pc.equals(current)) {
				return;
			}
		}

		subjects.add(pc);
	}

	private void removePetGracefully(L1PcInstance owner, L1NpcInstance petNpc) {
		if (petNpc instanceof L1PetInstance) {
			L1PetInstance pet = (L1PetInstance) petNpc;
			pet.dropItem();
			owner.getPetList().remove(pet.getId());
			pet.deleteMe();
		} else if (petNpc instanceof L1SummonInstance) {
			L1SummonInstance summon = (L1SummonInstance) petNpc;
			for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayerFast(summon)) {
				SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance(visiblePc, summon, visiblePc);
			}
		}
	}

	private void processMapContentLogic(L1PcInstance pc, int oldMapId, int newMapId) {
		// 인스턴스 던전 밖으로 나갈 때 아이템 삭제
		if (!isIndunMap(pc.getMap().getBaseMapId())) {
			cleanupIndunItems(pc);
		}
		
		// 공포의 집 버프 해제
		if ((oldMapId == 5140 || oldMapId == 5143) && oldMapId != newMapId) {
			L1HauntedHouse.getInstance().clearBuff(pc);
		}
		
		if (pc.getAutoHunt() && oldMapId != newMapId) {
            if (newMapId == 4) {
                if (oldMapId != 4) { 
                    pc.setAutoHuntMapId(oldMapId);
                }
            } else {
                pc.setAutoHuntMapId(newMapId);
            }
        }

		// 특정 퀘스트/던전 상태 업데이트
		updateQuestState(pc, oldMapId, newMapId);

		// 공성 관련 위치 이동 처리
		MJCastleWarBusiness.move(pc);

		// 개인 상점 찾기 HTML 복구
		if (pc.getFindMerchantId() != 0) {
			SC_NPCTalkReturn.send(pc, pc.getFindMerchantId(), "usershop");
			pc.setFindMerchantId(0);
		}

		// 던전 타이머 정보 전송
		if (oldMapId != newMapId && !pc.noPlayerCK && !pc.noPlayerck2) {
			DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_map_id(newMapId);
			if (dtInfo != null) pc.send_dungeon_progress(dtInfo);
		}
	}

	private void updateQuestState(L1PcInstance pc, int mapid, int newMapId) {
		if (pc.isInFantasy && !isFantasyMap(mapid)) {
			pc.getInventory().consumeItem(810006);
			pc.getInventory().consumeItem(810007);
			pc.isInFantasy = false;
		}
		if (pc.isInValakas && !isIndunMap(pc.getMap().getBaseMapId())) {
			pc.getInventory().consumeItem(203003);
			pc.isInValakas = false;
		}
		if (pc.isInValakasBoss && mapid != 2600) {
			pc.isInValakasBoss = false;
		}
		
		if(!isGahoMap(mapid)) {			
			if(pc.getInventory().checkEquipped(900022)) {
				SC_PacketBox.send(pc, SC_PacketBox.UNLIMITED_ICON1, 484, false);
			}
		}
		
		if (!isForbiddenItemMap(mapid)) {
			pc.getInventory().consumeItem(30055);
			pc.getInventory().consumeItem(30056);
		}
	}

	// --- 편의 및 가독성을 위한 Helper 메서드들 ---
	
	private boolean isRulerMap(int mid) {
		return mid == 495 || mid == 479 || mid == 461 || mid == 453 || mid == 452 || 
			   mid == 475 || mid == 492 || mid == 471 || mid == 462 || 
			   (mid >= 15410 && mid <= 15440);
	}

	private boolean isPetAllowedMap(int mid) {
		return mid != 5153 && mid != 5140 && mid != 781 && mid != 782;
	}

	private boolean isFixedCoordMap(int mid) {
		return mid == 5125 || (mid >= 5131 && mid <= 5134) || mid == 781 || mid == 782;
	}

	private boolean isIndunMap(int mid) {
		return mid == 1936 || mid == 2600 || mid == 2699;
	}

	private boolean isFantasyMap(int mid) {
		return mid >= 1936 && mid <= 2035;
	}

	private boolean isGahoMap(int mid) {
		return mid >= 1708 && mid <= 1712;
	}
	
	private boolean isForbiddenItemMap(int mid) {
		return (mid >= 2101 && mid <= 2201) || (mid >= 12152 && mid <= 12252);
	}

	private void updateRulerIcon(L1PcInstance pc) {
		if (pc.getInventory().checkItem(900111)) {
			if (isRulerMap(pc.getMapId())) {
				SC_InventoryIcon.iconNewUnLimit(pc, L1SkillId.TELEPORT_RULER, 5119, true);
			} else {
				SC_InventoryIcon.icoEnd(pc, L1SkillId.TELEPORT_RULER);
			}
		}
	}

	private void cleanupIndunItems(L1PcInstance pc) {
		pc.getInventory().consumeItem(203003);
		pc.getInventory().consumeItem(810006);
		pc.getInventory().consumeItem(810007);
	}
	
	

	public void teleport(L1NpcInstance npc, int x, int y, short map, int head) {
		
		final int oldX = npc.getX();
		final int oldY = npc.getY();
		final int oldMapId = npc.getMapId();
		
		final L1World world = L1World.getInstance();

	    world.relocateVisibleObject(npc, x, y, map);
	    npc.setHeading(head);
        NpcEventFactory.onNpcWorldEvent(npc);
	    npc.teleportNpcOcc(oldMapId, oldX, oldY, map, x, y);
		
	}

	public L1Location 소환텔레포트(L1Character target, int distance) {
		L1Location loc = new L1Location();
		int lx = target.getX(), ly = target.getY(), h = target.getHeading();
		loc.setMap(target.getMapId());
		switch (h) {
			case 1: lx += distance; ly -= distance; break;
			case 2: lx += distance; break;
			case 3: lx += distance; ly += distance; break;
			case 4: ly += distance; break;
			case 5: lx -= distance; ly += distance; break;
			case 6: lx -= distance; break;
			case 7: lx -= distance; ly -= distance; break;
			case 0: ly -= distance; break;
		}
		loc.setX(lx); loc.setY(ly);
		return loc;
	}

	public void teleportToTargetFront(L1Character cha, L1Character target, int distance, boolean check) {
		L1Location loc = 소환텔레포트(target, distance);
		if (target.getMap().isPassable(loc.getX(), loc.getY())) {
			if (cha instanceof L1PcInstance) {
				((L1PcInstance) cha).start_teleport(loc.getX(), loc.getY(), (short)loc.getMapId(), cha.getHeading(), 169, true, check);
			}
		}
	}

	public void randomTeleport(L1PcInstance pc, boolean effect) {
		L1Location loc = pc.getLocation().randomLocation(200, true);
		pc.start_teleport(loc.getX(), loc.getY(), (short) loc.getMapId(), pc.getHeading(), 169, effect, false);
	}
	
    private static void clearPcFromOldViewers(L1PcInstance pc) {
    	try {
    		final L1World world = L1World.getInstance();

    		for (L1PcInstance v : world.getVisiblePlayerFast(pc)) {
    			if (v == null || v == pc) {
    				continue;
    			}

    			v.removeKnownObject(pc);
    			SC_RemoveObject.send(v.getNetConnection(), pc);
    		}

        } catch (Exception e) {
        	LOG.log(
        			Level.WARNING,
        			"Old-viewer cleanup failed. pcId=" + pc.getId(),
        			e);
        }
    }
}
