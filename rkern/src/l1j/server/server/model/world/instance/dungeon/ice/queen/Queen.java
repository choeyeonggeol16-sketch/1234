package l1j.server.server.model.world.instance.dungeon.ice.queen;

import java.util.ArrayList;
import java.util.Random;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.world.instance.CInstanceEvents;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.dungeon.ice.CIceCaveSpawn;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

public final class Queen {

	private final CInstantZone<Queen> zone;
	private final int _map;

	private static Random _random = new Random(System.nanoTime());


	private boolean FirstRoom = false;
	private boolean SecondRoom = false;
	private boolean ThirdRoom = false;
	private boolean FourthRoom = false;
	private boolean BossRoom = false;
	private boolean End = false;

	private int Time = 3600;

    Queen(CInstantZone<Queen> zone) {
        this.zone = zone;
        _map = zone.mapId();
        zone.setExit(34068, 32311, 4);
    }

	int[] _MonsterList = { 5080, 5081, 5082, 5083, 5084, 5085 };

    void play() {
        if (CInstanceEvents.isStopping() || Thread.currentThread().isInterrupted()) return;
        FirstRoom = true;
        SpawnMonster();
        while (!End && !CInstanceEvents.isStopping() && !Thread.currentThread().isInterrupted()) {
            Check();
            if (End) break;
            if (FirstRoom) First();
            else if (SecondRoom) Second();
            else if (ThirdRoom) Third();
            else if (FourthRoom) Fourth();
            else if (BossRoom) Boss();

            if (!CIceCaveSpawn.pause(1000L)) break;
        }
    }


	private void Check() {
		if (Time > 0) {
			Time--;
		}
		if (Time <= 0) {
			End();
		} else if (Time <= 3590) {
			CheckPc();
		}
		if (Time % 60 == 0) {
			int min = Time / 60;
			l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenObjectPlayersBestEffort(
                CWorld.getInstance().getVisibleObjects(_map).values(),
                SC_PacketBox.nativeGreenWriter(min + "분 후에 마을로 강제 이동 됩니다."));
		}
	}

	private void First() {
		if (_list1.size() > 0) {
			for (int i = _list1.size() - 1; i >= 0; i--) {
				L1NpcInstance npc = _list1.get(i);
				if (npc.getCurrentHp() <= 0) {
					_list1.remove(i);
				}
			}
		} else {
			openDoor(5147); // 첫번째 문 개방.
			FirstRoom = false;
			SecondRoom = true;
		}
	}

	private void Second() {
		if (_list2.size() > 0) {
			for (int i = _list2.size() - 1; i >= 0; i--) {
				L1NpcInstance npc = _list2.get(i);
				if (npc.getCurrentHp() <= 0) {
					_list2.remove(i);
				}
			}
		} else {
			openDoor(5148); // 두번째 문 개방.
			SecondRoom = false;
			ThirdRoom = true;
		}
	}

	private void Third() {
		if (_list3.size() > 0) {
			for (int i = _list3.size() - 1; i >= 0; i--) {
				L1NpcInstance npc = _list3.get(i);
				if (npc.getCurrentHp() <= 0) {
					_list3.remove(i);
				}
			}
		} else {
			openDoor(5149); // 세번째 문 개방.
			ThirdRoom = false;
			FourthRoom = true;
		}
	}

	private void Fourth() {
		if (_list4.size() > 0) {
			for (int i = _list4.size() - 1; i >= 0; i--) {
				L1NpcInstance npc = _list4.get(i);
				if (npc.getCurrentHp() <= 0) {
					_list4.remove(i);
				}
			}
		} else {
			openDoor(5150); // 네번째 문 개방.
			FourthRoom = false;
			BossRoom = true;
		}
	}

	private void Boss() {
		if (_list5.size() > 0) {
			for (int i = _list5.size() - 1; i >= 0; i--) {
				L1NpcInstance npc = _list5.get(i);
				if (npc.getCurrentHp() <= 0) {
					_list5.remove(i);
				}
			}
		} else {
			l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenObjectPlayersBestEffort(
                CWorld.getInstance().getVisibleObjects(_map).values(),
                SC_PacketBox.nativeGreenWriter("벽 뒤에 있는 스빈을 만나십시오."));
			openDoor(5151); // 다섯번째 문 개방.
			BossRoom = false;
		}
	}

    void finish() {
        try {
            CIceCaveSpawn.finish(zone.map(), 34068, 32311);
        } finally {
            for (int room = 0; room <= 5; room++) roomList(room).clear();
        }
    }


    private void CheckPc() {
        if (CWorld.getInstance().getVisiblePlayers(_map).isEmpty()) End();
    }

    private void openDoor(int doorId) {
        for (L1NpcInstance npc : _list0) {
            if (npc instanceof L1DoorInstance door && npc.getNpcId() == doorId
                    && door.getOpenStatus() == ActionCodes.ACTION_Close) {
                door.open();
                return;
            }
        }
    }

	private void End() {
		End = true;
	}

	private final ArrayList<L1NpcInstance> _list0 = new ArrayList<L1NpcInstance>();
	private final ArrayList<L1NpcInstance> _list1 = new ArrayList<L1NpcInstance>();
	private final ArrayList<L1NpcInstance> _list2 = new ArrayList<L1NpcInstance>();
	private final ArrayList<L1NpcInstance> _list3 = new ArrayList<L1NpcInstance>();
	private final ArrayList<L1NpcInstance> _list4 = new ArrayList<L1NpcInstance>();
	private final ArrayList<L1NpcInstance> _list5 = new ArrayList<L1NpcInstance>();

    private ArrayList<L1NpcInstance> roomList(int room) {
        return switch (room) {
            case 0 -> _list0;
            case 1 -> _list1;
            case 2 -> _list2;
            case 3 -> _list3;
            case 4 -> _list4;
            case 5 -> _list5;
            default -> throw new IllegalArgumentException("Invalid ice-cave room " + room);
        };
    }


	private void SpawnMonster() {
		// NPC스폰
		spawn(32734, 32802, (short) _map, 4, 5086, 1, 0); // 상아탑 첩보원
		spawn(32860, 32920, (short) _map, 6, 5087, 1, 0); // 스빈

		// 문짝 스폰
		spawn(32784, 32818, (short) _map, 0, 5147, 1, 0);
		spawn(32852, 32806, (short) _map, 0, 5148, 1, 0);
		spawn(32822, 32855, (short) _map, 0, 5149, 1, 0);
		spawn(32762, 32916, (short) _map, 0, 5150, 1, 0);
		spawn(32852, 32920, (short) _map, 0, 5151, 1, 0);

		// 1번방 뿌리기
		for (int i = 0; i < 15; i++) {
			spawn(32765, 32818, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 12, 1);
		}
		spawn(32749, 32800, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 0, 1);
		spawn(32749, 32817, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 0, 1);
		spawn(32766, 32833, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 0, 1);
		spawn(32772, 32802, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 0, 1);

		// 2번 방 뿌리기 2였는데 1로 고쳤죠 메소드를 따라가보면
		spawn(32813, 32805, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 0, 2);
		spawn(32819, 32807, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 0, 2);
		spawn(32819, 32805, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 0, 2);
		for (int i = 0; i < 15; i++) {
			spawn(32833, 32806, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 13, 2);
		}

		// 3번 방 뿌리기
		spawn(32859, 32832, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 0, 3);
		spawn(32857, 32831, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 0, 3);
		spawn(32855, 32834, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 0, 3);
		for (int i = 0; i < 15; i++) {
			spawn(32850, 32853, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 10, 3);
		}

		// 4번 방 뿌리기
		for (int i = 0; i < 15; i++) {
			spawn(32767, 32892, (short) _map, 0, _MonsterList[_random.nextInt(100) % _MonsterList.length], 12, 4);
		}

		// 5번 방 뿌리기 ( 보스 몬스터 추가 : 아이스 여왕 )
		for (int i = 0; i < 15; i++) {
			spawn(32767, 32892, (short) _map, 6, _MonsterList[_random.nextInt(100) % _MonsterList.length], 12, 5);
		}
		spawn(32845, 32920, (short) _map, 6, 45609, 0, 5); // 아이스여왕
	}

    private void spawn(int x, int y, short mapId, int heading, int npcId, int radius, int room) {
        final L1NpcInstance npc = CIceCaveSpawn.spawn(x, y, mapId, heading, npcId, radius);
        roomList(room).add(npc);
    }
}
