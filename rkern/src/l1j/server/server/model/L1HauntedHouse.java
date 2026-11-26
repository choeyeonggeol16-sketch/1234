package l1j.server.server.model;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import java.util.ArrayList;
import java.util.Random;

import l1j.server.server.model.world.instance.CInstanceWork;
import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1FieldObjectInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.model.inventory.CInventory;

public class L1HauntedHouse implements Runnable {
    /** Optional game notices must not terminate the timed game-state transition. */
    private static final java.util.logging.Logger GAME_NOTICE_LOG =
            java.util.logging.Logger.getLogger(L1HauntedHouse.class.getName());

    private static l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer
    openGameBroadcast(l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter writer) {
        if (writer == null) return null;
        try {
            return new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    writer, Integer.MIN_VALUE, Integer.MAX_VALUE);
        } catch (RuntimeException failure) {
            GAME_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Optional game notice source unavailable", failure);
            return null;
        }
    }

    private static void sendGameNotice(
            l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer shared,
            L1PcInstance pc) {
        if (shared == null || pc == null) return;
        try {
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared, pc);
        } catch (RuntimeException failure) {
            GAME_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Optional game notice SEND failed", failure);
        }
    }

    private static void closeGameBroadcast(
            l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer shared) {
        if (shared == null) return;
        try {
            shared.close();
        } catch (RuntimeException failure) {
            GAME_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Optional game notice source release failed", failure);
        }
    }

	public static final int STATUS_NONE = 0;
	public static final int STATUS_READY = 1;
	public static final int STATUS_PLAYING = 2;
	public static final int STATUS_CLEANUP = 3;

	public static final int EXECUTE_STATUS_NONE = 0;
	public static final int EXECUTE_STATUS_PREPARE = 1;
	public static final int EXECUTE_STATUS_READY = 2;
	public static final int EXECUTE_STATUS_PROGRESS = 3;
	public static final int EXECUTE_STATUS_FINALIZE = 4;

	private final ArrayList<L1PcInstance> _members = new ArrayList<L1PcInstance>();

	private int _hauntedHouseStatus = STATUS_NONE;
	private int _executeStatus = EXECUTE_STATUS_NONE;
	private int _count = 0;

	private L1NpcInstance _guide = null;
	private L1FieldObjectInstance _fire = null;

	private static L1HauntedHouse _instance;

	public static L1HauntedHouse getInstance() {
		if (_instance == null) {
			_instance = new L1HauntedHouse();
		}
		return _instance;
	}

	@Override
	public void run() {
		try {
			switch (_executeStatus) {
			case EXECUTE_STATUS_NONE: {
				if (getHauntedHouseStatus() == STATUS_READY) {
					_executeStatus = EXECUTE_STATUS_PREPARE;
					CInstanceWork.scheduleRequired(this, 60000L);
				} else {
					CInstanceWork.scheduleRequired(this, 1000L);
				}
			}
				break;

			case EXECUTE_STATUS_PREPARE: {
				removeRetiredMembers();

				if (readyHauntedHouse()) {
					_count = 10;

					_executeStatus = EXECUTE_STATUS_READY;
				} else {
					_executeStatus = EXECUTE_STATUS_NONE;
				}

				CInstanceWork.scheduleRequired(this, 1000L);
			}
				break;

			case EXECUTE_STATUS_READY: {
				if (countDown()) {
					removeRetiredMembers();
					startHauntedHouse();

					_count = 60 * 5;

					_executeStatus = EXECUTE_STATUS_PROGRESS;
				}

				CInstanceWork.scheduleRequired(this, 1000L);
			}
				break;

			case EXECUTE_STATUS_PROGRESS: {

				if (getHauntedHouseStatus() == STATUS_CLEANUP) {
					if (endCountDown()) {
						_executeStatus = EXECUTE_STATUS_FINALIZE;
						CInstanceWork.scheduleRequired(this, 5 * 60 * 000L);
					} else {
						CInstanceWork.scheduleRequired(this, 1000L);
					}
				} else {
					if (--_count == 0) {
						if (_count % 10 == 0) // 얜 10초마다 한번씩
						{
							removeRetiredMembers();
						}

						endHauntedHouse();

					}
					CInstanceWork.scheduleRequired(this, 1000L);
				}
			}
				break;

			case EXECUTE_STATUS_FINALIZE: {
				_executeStatus = EXECUTE_STATUS_NONE;
				setHauntedHouseStatus(STATUS_NONE);

				CInstanceWork.scheduleRequired(this, 1000L);
			}
				break;
			}
		} catch (Exception e) {
		}
	}

	public void addMember(L1PcInstance pc) {
		if (!_members.contains(pc)) {
			_members.add(pc);
			// %d번째 순번으로 입장 예약되었습니다.
			SC_ServerMessage.send(pc, 1253, Integer.toString(getMembersCount()));

			// if(getMembersCount() > 0){
			if (getMembersCount() > 1) {
				if (getHauntedHouseStatus() == STATUS_NONE) {
					setHauntedHouseStatus(STATUS_READY);
				}

				final var shared = openGameBroadcast(SC_MessageYN.nativeWriter(1256,""));
				try {
for (L1PcInstance player : getMembersArray()) {
					// 입장하시겠습니까? (Y/N)
					if (player.getMap().getId() != 5140)
						sendGameNotice(shared,player);
				}
} finally { closeGameBroadcast(shared); }
			}
			// 이미 있다면..
		} else {
			// 이미 입장 예약이 되어있습니다.
			SC_ServerMessage.send(pc, 1254);
		}

	}

	public void clearBuff(L1PcInstance pc) {
		
		
		L1SkillUse.cast(pc, L1SkillId.CANCELLATION, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_LOGIN);


		int classId = pc.getClassId();
		pc.setCurrentSprite(classId);
		pc.sendShape(classId);
		L1ItemInstance weapon = pc.getWeapon();
		if (weapon != null) {
			final long charVisual = SC_CharVisualUpdate.pack(pc);
			SC_CharVisualUpdate.sendPacked(pc, charVisual);
			SC_CharVisualUpdate.broadcastPacked(pc, charVisual);
		}
	}

	private boolean readyHauntedHouse() {
		// if( getMembersCount() < 1 )
		if (getMembersCount() < 2) {
			final var shared = openGameBroadcast(SC_ServerMessage.nativeWriter(1264));
			try {
            for (L1PcInstance pc : getMembersArray()) {
				if (pc.getMapId() == 5140) {
					// 경기 최소 인원이 2명이 만족하지 않아 경기를 강제 종료 합니다. 1000 아데나를 돌려 드렸습니다.
					sendGameNotice(shared,pc);
					pc.getInventory().CreateOrAddItem(40308, 1000); // 1000 아데나 지급

//					L1Teleport.teleport(pc, 32624, 32813, (short) 4, 5, true);
					pc.start_teleport(32624, 32813, 4, pc.getHeading(), 169, true, false);
				}
				removeMember(pc);

				setHauntedHouseStatus(STATUS_NONE);
			}
        } finally { closeGameBroadcast(shared); }
            // Also reset when the booking list was already empty.
            setHauntedHouseStatus(STATUS_NONE);
			return false;
		}

		setHauntedHouseStatus(STATUS_PLAYING);

		try {
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(getMembersArray(),SC_ServerMessage.nativeWriter(1257));
        } catch (RuntimeException failure) {
            GAME_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Haunted House start notice failed", failure);
        }

		return true;
	}

	public void removeRetiredMembers() {
		for (L1PcInstance pc : getMembersArray()) {
			if (pc.getMapId() != 5140) {
				removeMember(pc);
			}
		}

		for (L1PcInstance pc : CWorld.getInstance().getAllPlayers3()) {
			if (pc.getMapId() == 5140 && !isMember(pc)) {
//				L1Teleport.teleport(pc, 32624, 32813, (short) 4, 5, true);
				pc.start_teleport(32624, 32813, 4, pc.getHeading(), 169, true, false);
			}
		}
	}

	private void broadcast(String msg) {
        final var shared = openGameBroadcast(SC_SystemMessage.nativeWriter(msg));
        try {
            for(L1PcInstance pc:getMembersArray()){
                if(pc.getMapId()==5140)
                    sendGameNotice(shared,pc);
            }
        } finally { closeGameBroadcast(shared); }
    }

	private void npcBroadcast(String msg) {
		if (_guide == null) {
			for (Object obj : CWorld.getInstance().getVisibleObjects(4).values()) {
				if (obj instanceof L1NpcInstance) {
					L1NpcInstance n = (L1NpcInstance) obj;
					if (n.getNpcTemplate().get_npcId() == 80085) {
						_guide = n;
						break;
					}
				}
			}
		}

		if (_guide == null) {
			return;
		}

		final l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter wire;
        try { wire = SC_NpcChatPacket.nativeNpcWriter(_guide,msg,2); }
        catch (RuntimeException failure) {
            GAME_NOTICE_LOG.log(java.util.logging.Level.WARNING,
                    "Haunted House NPC notice encoding failed", failure);
            return;
        }
        if(wire==null)return;
        final var shared = openGameBroadcast(wire);
        try {
            for (L1PcInstance pc : getMembersArray()) {
                if (pc.getMapId() == 5140) {
                    sendGameNotice(shared,pc);
                }
            }
        } finally { closeGameBroadcast(shared); }
	}

	private boolean countDown() {
		if (_count == 0) {
			return true;
		}

		broadcast(_count + "초후 게임이 시작됩니다.");

		--_count;

		return false;

	}

	private boolean endCountDown() {
		if (_count == 0) {
			kickAllPlayers();
			closeDoor();

			return true;
		}

		broadcast(_count + "초후 유령의 집 밖으로 이동합니다.");

		--_count;

		return false;

	}

	private void startHauntedHouse() {
		openDoor();

		if (_fire == null) {
			spawnFire();
		}

	}

	private void openDoor() {
		L1DoorInstance door = null;
		for (L1Object object : CWorld.getInstance().getObject()) {
			if (object instanceof L1DoorInstance) {
				door = (L1DoorInstance) object;
				// 첫번째 문이라면 연다..
				if (door.getMapId() == 5140 && door.getDoorId() == 3001) {
					door.open();
				}
			}
		}
	}

	private void closeDoor() {
		L1DoorInstance door = null;
		for (L1Object object : CWorld.getInstance().getObject()) {
			if (object instanceof L1DoorInstance) {
				door = (L1DoorInstance) object;
				// 첫번째 문이라면 닫는다..
				if (door.getMapId() == 5140 && door.getDoorId() == 3001) {
					door.close();
				}
			}
		}
	}

	public void spawnFire() {
		try {
			L1Npc l1npc = CObjectDB.GetNPC(81171);
			if (l1npc != null) {
				try {
					L1NpcInstance npc = CObjectDB.CreateNPC(l1npc);

					npc.setId(IdFactory.getInstance().nextId());
					npc.setMap((short) 5140);
					npc.setX(32871);
					npc.setY(32830);
					npc.setHomeX(32871);
					npc.setHomeY(32830);
					npc.setHeading(0);

					CWorld.getInstance().storeAndEnterWorld(npc);
					L1Object object = CWorld.getInstance().findObject(npc.getId());
					_fire = (L1FieldObjectInstance) object;
					_fire.onNpcAI();
					_fire.refreshLight();
					_fire.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE);
				} catch (Exception e) {
					// e.printStackTrace();
				}
			}
		} catch (Exception exception) {
		}
	}

	private void despawnFire() {
		if (_fire != null) {
			_fire.deleteMe();
			_fire = null;
		}
	}

	private void kickAllPlayers() {
		for (L1PcInstance pc : getMembersArray()) {
			if (pc.getMapId() == 5140) {
//				L1Teleport.teleport(pc, 32624, 32813, (short) 4, 5, true);
				pc.start_teleport(32624, 32813, 4, pc.getHeading(), 169, true, false);
			}
		}

		clearMembers();
	}

	public void endHauntedHouse() {
		setHauntedHouseStatus(STATUS_CLEANUP);

		npcBroadcast("시간이 다 되었군. 다음에 또 보세나.");

		_count = 5;
	}

	public void endHauntedHouse(L1PcInstance pc) {
		setHauntedHouseStatus(STATUS_CLEANUP);
		SC_SystemMessage.send(pc, "영혼의 불꽃을 파괴했습니다.");

		final L1ItemInstance item = CObjectDB.CreateItem(41308);

		if (item != null) {
			try {
				if (pc.getInventory().CheckAddable(item, 1) == CInventory.OK) {
					item.setCount(1);
					if (pc.getInventory().CreateOrAddItem(item) != null) {
						SC_ServerMessage.send(pc, 403, item.getLogName());

						Random random = new Random(System.nanoTime()); // 펫레이싱
						if (random.nextInt() < 33) {
							// Reuse the actual grant for the one-item notice.
							L1ItemInstance bonus = pc.getInventory().CreateOrAddItem(3000023, 1);
							if (bonus != null) {
								SC_ServerMessage.send(pc, 403, bonus.getNumberedName(1));
							}
						}
					}
				}
			} finally {
				// ItemTable pre-registers this object. Stack merge/rejection/exception
				// must not retain a world index entry for the unowned image.
				if (pc.getInventory().GetByServerID(item.getId()) != item) {
					CWorld.getInstance().removeObject(item);
				}
			}
		}

		despawnFire();

		npcBroadcast("예상보다 일찍 도착했군.");

		_count = 5;
	}

	public void removeMember(L1PcInstance pc) {
		_members.remove(pc);
	}

	public void clearMembers() {
		_members.clear();
	}

	public boolean isMember(L1PcInstance pc) {
		return _members.contains(pc);
	}

	public L1PcInstance[] getMembersArray() {
		return _members.toArray(new L1PcInstance[_members.size()]);
	}

	public int getMembersCount() {
		return _members.size();
	}

	private void setHauntedHouseStatus(int i) {
		_hauntedHouseStatus = i;
	}

	public int getHauntedHouseStatus() {
		return _hauntedHouseStatus;
	}
}