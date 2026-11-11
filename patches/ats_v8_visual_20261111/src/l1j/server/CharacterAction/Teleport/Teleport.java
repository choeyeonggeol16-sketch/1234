package l1j.server.CharacterAction.Teleport;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import l1j.server.DungeonTimer.DungeonTimeInformation;
import l1j.server.DungeonTimer.Loader.DungeonTimeInformationLoader;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_CharVisualUpdate;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_MapID;
import l1j.server.server.serverpackets.S_Party;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_WorldObject;

/**
 * 텔레포트 새로 구현 
 * @author 
 *
 */
public class Teleport {

	private static class LazyHolder {
		private static final Teleport instance = new Teleport();
	}
	public static Teleport getInstance() {
		return LazyHolder.instance;
	}
	private static Random _random = new Random(System.nanoTime());

	/**
	 * 텔레포트 정보를 받아 텔레포트를 시켜준다.
	 * @param pc
	 */
	public void Teleportation(L1PcInstance pc) {
		try {
			
			int x = pc.getTeleportX();
			int y = pc.getTeleportY();
			short mapId = pc.getTeleportMapId();
			int head = pc.getTeleportHeading();

			L1Map map = L1WorldMap.getInstance().getMap(mapId);

			try {
				if (!map.isInMap(x, y) && !pc.isGm()) {
					x = pc.getX();
					y = pc.getY();
					mapId = pc.getMapId();
				}
			} catch (Exception e) {
				x = pc.getX();
				y = pc.getY();
				mapId = pc.getMapId();
			}
			
			List<L1PcInstance> list = pc.getKnownPlayers();
			for (L1PcInstance target : list) {
				target.sendPackets(S_RemoveObject.get(pc.getId()));
			}

			L1World.getInstance().moveVisibleObject(pc, mapId);
			pc.setLocation(x, y, mapId);
			pc.setHeading(head);
			pc.sendPackets(new S_MapID(pc.getMap().getBaseMapId(), pc.getMap().isUnderwater()));


			if (!pc.isGmInvis()) {
				pc.broadcastPacket(S_WorldObject.out(pc));
			}
			
			pc.sendPackets(S_WorldObject.in(pc));
			
			pc.removeAllKnownObjects();
			pc.sendVisualEffectAtTeleport();
			pc.updateObject();
			pc.sendPackets(S_CharVisualUpdate.get(pc));

            if(pc.hasSkillEffect(SkillId.메디테이션)) {
            	pc.removeSkillEffect(SkillId.메디테이션);
            }
        
			HashSet<L1PcInstance> subjects = new HashSet<L1PcInstance>();
			subjects.add(pc);

			if (!pc.isGhost() && pc.getMap().isTakePets()) {
				for (L1NpcInstance petNpc : pc.getPetList().values()) {
					L1Location loc = pc.getLocation().randomLocation(3, false);
					int nx = loc.getX();
					int ny = loc.getY();
					if (pc.getMapId() == 5125 || pc.getMapId() == 5131 || pc.getMapId() == 5132 || pc.getMapId() == 5133
							|| pc.getMapId() == 5134 || pc.getMapId() == 781 || pc.getMapId() == 782) {
						nx = 32799 + _random.nextInt(5) - 3;
						ny = 32864 + _random.nextInt(5) - 3;
					} 
					teleport(petNpc, nx, ny, pc.getMapId(), pc.getHeading());
					if (petNpc instanceof SummonInstance) {
						SummonInstance summon = (SummonInstance) petNpc;
						pc.sendPackets(S_WorldObject.summon(summon, pc));
					} else if (petNpc instanceof PetInstance) {
						PetInstance pet = (PetInstance) petNpc;
						pc.sendPackets(S_WorldObject.pet(pet, pc));
					}
					for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(petNpc)) {
						visiblePc.removeKnownObject(petNpc);
						subjects.add(visiblePc);
					}

				}

				DollInstance doll = pc.getMagicDoll();
				if (doll != null) {
					L1Location loc = pc.getLocation().randomLocation(3, false);
					int nx = loc.getX();
					int ny = loc.getY();
					teleport(doll, nx, ny, pc.getMapId(), pc.getHeading());
					pc.sendPackets(S_WorldObject.doll(doll, pc));
					for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(doll)) {
						visiblePc.removeKnownObject(doll);
						subjects.add(visiblePc);
					} 
				} 
			} else {
				// 애완동물을 월드 MAP상으로부터 지운다 
				PetInstance pet = null;
				SummonInstance summon = null;
				for (L1NpcInstance petObject : pc.getPetList().values()) { 
					if (petObject instanceof PetInstance) {
						pet = (PetInstance) petObject;
						pet.dropItem();
						pc.getPetList().remove(pet.getId());
						pet.deleteMe();
					}
					if (petObject instanceof SummonInstance) {
						summon = (SummonInstance) petObject;
						for (L1PcInstance visiblePc : L1World.getInstance().getVisiblePlayer(summon)) {
							visiblePc.sendPackets(S_WorldObject.summon(summon, visiblePc));
						}
					}
				} 
				// 마법인형을 월드 맵상으로부터 지운다
				DollInstance doll = pc.getMagicDoll();
				if (doll != null) {
					doll.deleteDoll(); 
				}
			}

			for (L1PcInstance updatePc : subjects) {
				updatePc.updateObject();
			}

			// 피바 갱신
			if (pc.isInParty()) {
				L1PcInstance member[] = pc.getParty().getMembers();
				if (pc.getParty() != null) {
					for (int i = 0, a = member.length; i < a; i++) {
						member[i].sendPackets(new S_Party(0x6e, member[i]));
					}
				}
			}
		
			
			pc.setTeleport(false);
		
//			DungeonTimeInformation dtInfo = DungeonTimeInformationLoader.getInstance().from_map_id(pc.getMapId());
//			if(dtInfo != null) {
//				pc.send_dungeon_progress(dtInfo);
//			}
			
		
			
			if(pc.isDead()) {
				pc.sendPackets(S_DoActionGFX.get(pc.getId(), ActionCodes.ACTION_Die));
			}			
		} catch (Exception e) {	
		}
	}

	/**
	 * 엔피시 텔레포트를 실행한다.
	 * @param npc
	 * @param x
	 * @param y
	 * @param map
	 * @param head
	 */
	public static void teleport(L1NpcInstance npc, int x, int y, short map, int head) {
		L1World.getInstance().moveVisibleObject(npc, map, x, y);

		L1WorldMap.getInstance().getMap(npc.getMapId()).setPassable(npc.getX(), npc.getY(), true);

		npc.setX(x);
		npc.setY(y);
		npc.setMap(map);
		npc.setHeading(head);
		npc.allTargetClear();

		L1WorldMap.getInstance().getMap(npc.getMapId()).setPassable(npc.getX(), npc.getY(), false);
	}
	/**
	 * 몬스터 지엠 용 리콜 
	 * @param cha
	 * @param target
	 * @param distance
	 * @param check
	 */
	public static void teleportToTargetFront(L1Character cha, L1Character target, int distance, boolean check) {
		int locX = target.getX();
		int locY = target.getY();
		int heading = target.getHeading();
		L1Map map = target.getMap();
		short mapId = target.getMapId();

		switch (heading) {
		case 1:
			locX += distance;
			locY -= distance;
			break;
		case 2:
			locX += distance;
			break;
		case 3:
			locX += distance;
			locY += distance;
			break;
		case 4:
			locY += distance;
			break;
		case 5:
			locX -= distance;
			locY += distance;
			break;
		case 6:
			locX -= distance;
			break;
		case 7:
			locX -= distance;
			locY -= distance;
			break;
		case 0:
			locY -= distance;
			break;
		default:
			break;
		}

		if (map.isPassable(locX, locY)) {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				pc.teleport(locX, locY, mapId, cha.getHeading(), 169, true, check);
			} else if (cha instanceof L1NpcInstance) {
			}
		}
	}

	/**
	 * 몬스터 스킬용 
	 * @param target
	 * @param distance
	 * @return
	 */
	public static L1Location 소환텔레포트(L1Character target, int distance) {
		L1Location loc = new L1Location();
		int locX = target.getX();
		int locY = target.getY();
		int heading = target.getHeading();
		loc.setMap(target.getMapId());
		switch (heading) {
		case 1:
			locX += distance;
			locY -= distance;
			break;
		case 2:
			locX += distance;
			break;
		case 3:
			locX += distance;
			locY += distance;
			break;
		case 4:
			locY += distance;
			break;
		case 5:
			locX -= distance;
			locY += distance;
			break;
		case 6:
			locX -= distance;
			break;
		case 7:
			locX -= distance;
			locY -= distance;
			break;
		case 0:
			locY -= distance;
			break;
		}
		loc.setX(locX);
		loc.setY(locY);
		return loc;
	}
	/**
	 * 콜클랜 전용
	 * @param pc
	 * @param effectable
	 */
	public static void Collteleport(L1PcInstance pc, L1Location loc, int head, boolean effectable) {
		pc.teleport(loc.getX(), loc.getY(), (short) loc.getMapId(), head, 2281, effectable, false);
	}
	/**
	 * 랜덤 텔레포트 용 
	 * @param pc
	 * @param effectable
	 */
	public static void randomTeleport(L1PcInstance pc, boolean effectable) {
		L1Location newLocation = pc.getLocation().randomLocation(200, true);
		int newX = newLocation.getX();
		int newY = newLocation.getY();
		int newHeading = pc.getHeading();
		short mapId = (short) newLocation.getMapId();
		pc.teleport(newX, newY, mapId, newHeading, 169, effectable, false);
	}
}
