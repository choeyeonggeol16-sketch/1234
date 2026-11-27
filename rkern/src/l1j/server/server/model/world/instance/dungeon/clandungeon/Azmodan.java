package l1j.server.server.model.world.instance.dungeon.clandungeon;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ActionCodes;
import l1j.server.server.model.world.instance.CInstanceEvents;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.shared.GameRandom;

public final class Azmodan implements Runnable {
    private static final Logger LOG = Logger.getLogger(Azmodan.class.getName());
    private final CInstantZone<Azmodan> zone;
    private final int clanId;
    private final int _map;
    private boolean began;
    private boolean returnRequested;



	private boolean Running = false;

	public boolean Start = false;
	private boolean FirstLevel = false;
	private boolean SecondLevel = false;
	private boolean ThirdLevel = false;
	private boolean End = false;

	private int Time = 3600;
	
    Azmodan(CInstantZone<Azmodan> zone, int clanId) {
        this.zone = zone;
        this.clanId = clanId;
        this._map = zone.mapId();
    }

    int clanId() { return clanId; }
    boolean began() { return began; }

	int[] _MonsterList1 = { 14901, 14902 };
	int[] _MonsterList2 = { 14900, 14901, 14902, 14903 };
	int[] _MonsterList3 = { 14901, 14902, 14904 };

	@Override
	public void run() {
        if (CInstanceEvents.isStopping() || Thread.currentThread().isInterrupted()) return;
		began = true;
		Running = true;
		FirstLevel = true;
		Time = 3600;
		
		SpawnMonster();
		while (Running && !CInstanceEvents.isStopping() && !Thread.currentThread().isInterrupted()) {
			try {
				Check();
				if (End) {
					break;
				} else if (FirstLevel) {
					First();
				} else if (SecondLevel) {
					Second();
				} else if (ThirdLevel) {
					Third();
				} 
			} catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Clan dungeon stage failed for map " + _map, failure);
                break;
			} finally {
				if (!CInstanceEvents.isStopping() && !Thread.currentThread().isInterrupted()) {
					try { Thread.sleep(1000L); }
					catch (InterruptedException stopping) { Thread.currentThread().interrupt(); }
				}
			}
		}
	}
	
	private void Check() {
		if (Time > 0) {
			Time--;
		}
		if (Time <= 0) {
			End();
		} else if (Time <= 3590) {
			CheckClanId();
		} 
		if (Time % 600 == 0) {
			int min = Time / 60;
			CSharedBroadcast.sendWrittenObjectPlayersBestEffort(
                    CWorld.getInstance().getMapObjects(_map),
                    SC_PacketBox.nativeGreenWriter(min+"분뒤에 던전은 종료됩니다."));
		}
	}

	private void First() {
		if (_list1.size() > 0) {
			for (int i = _list1.size()-1; i >= 0; i--) {
				L1NpcInstance npc = _list1.get(i);
				if (npc.getNpcId() == 14905 && npc.getCurrentHp() <= 0) {
					remove(npc, 1);
					openDoor(14911);
					FirstLevel = false;
					SecondLevel = true;	
				}
			}
		} 
	}

	private void Second() {
		if (_list2.size() > 0) {
			for (int i = _list2.size()-1; i >= 0; i--) {
				L1NpcInstance npc = _list2.get(i);
				if (npc.getNpcId() == 14906 && npc.getCurrentHp() <= 0) {				
					remove(npc, 2);
					openDoor(14912); // 두번째 문 개방.
					SecondLevel = false;
					ThirdLevel = true;
				}
			}
		} 		
	}

	private void Third() {
		if (_list3.size() > 0) {
			for (int i = _list3.size()-1; i >= 0; i--) {
				L1NpcInstance npc = _list3.get(i);
				if (npc.getNpcId() == 14907 && npc.getCurrentHp() <= 0) {				
					remove(npc, 3);					
					ThirdLevel = false;				
				}
			}
		}		
	}

    void cleanup() {
        if (!began) return;
        // Storage cleanup must still run if the room's wait was interrupted.
        Thread.interrupted();
        Running = false;
        if (!returnRequested) returnPlayers();
		for (L1Object ob : CWorld.getInstance().snapshotZoneOccupants(zone.map())) {
			if (ob == null || ob.getMap() != zone.map())
				continue;
            try {
                if (ob instanceof L1ItemInstance item) {
                    CInventory groundInventory = CWorld.getInstance().findGroundInventory(item.getX(), item.getY(), item.getMapId());
                    if (groundInventory != null && groundInventory.getMap() == zone.map()) groundInventory.DeleteItemAmount(item);
                } else if (ob instanceof L1NpcInstance npc && !CInstantZoneManager.isPlayerCompanion(npc)) {
                    npc.deleteMe();
                }
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Clan dungeon object cleanup failed for map " + _map, failure);
            }
		}
        _list0.clear();
        _list1.clear();
        _list2.clear();
        _list3.clear();
        final L1Clan clan = CWorld.getInstance().getClan(clanId);
        if (clan != null) {
            clan.setUnderDungeon(2);
            clan.setUnderMapid(0);
            ClanTable.getInstance().updateUnderDungeon(clanId, 2);
        }
	}

	private void openDoor(int doorId) {
		L1DoorInstance door = null;
		for (L1Object object : CWorld.getInstance().getMapObjects(_map)) {
			if (object instanceof L1DoorInstance) {
				door = (L1DoorInstance) object;
				if (door.getNpcTemplate().get_npcId() == doorId) {
					if (door.getOpenStatus() == ActionCodes.ACTION_Close) {
						door.open();
					}
				}
			}
		}
	}
	
    private void CheckClanId() {
        final L1Clan clan = CWorld.getInstance().getClan(clanId);
        if (clan == null) return;
        for (L1Object object : CWorld.getInstance().getMapObjects(_map)) {
            if (!(object instanceof L1NpcInstance npc) || npc.getMap() != zone.map()
                    || npc.getNpcId() != 14907 || !npc.isDead()) continue;
            final int elapsed = 3600 - Time;
            if (clan.getRankTime() == 0 || clan.getRankTime() > elapsed) {
                clan.setRankTime(elapsed);
                ClanTable.getInstance().updateRankTime(clanId, elapsed);
                final Timestamp now = new Timestamp(System.currentTimeMillis());
                clan.setRankDate(now);
                ClanTable.getInstance().updateRankDate(clanId, now);
                final String message = String.format("기록을 %d분 %d초 로 갱신하였습니다.", elapsed / 60, elapsed % 60);
                CSharedBroadcast.sendWrittenArrayBestEffort(clan.getOnlineClanMember(),
                        SC_SystemMessage.nativeWriter(message));
            } else if (clan.getRankTime() < elapsed) {
                CSharedBroadcast.sendWrittenArrayBestEffort(clan.getOnlineClanMember(),
                        SC_SystemMessage.nativeWriter("기존의 기록을 갱신하는데 실패하였습니다."));
            }
            End();
            return;
        }
    }

	private void End() {
		try {
			CSharedBroadcast.sendWrittenObjectPlayersBestEffort(
                    CWorld.getInstance().getMapObjects(_map),
                    SC_PacketBox.nativeGreenWriter("30초후 지하통로가 닫힙니다."));
			Thread.sleep(30000);
			returnPlayers();
		} catch (InterruptedException stopping) {
			Thread.currentThread().interrupt();
		}
		End = true;
	}

    private void returnPlayers() {
        returnRequested = true;
        for (L1Object obj : CWorld.getInstance().snapshotZoneOccupants(zone.map())) {
            if (obj instanceof L1PcInstance pc && pc.getMap() == zone.map()) {
                pc.start_teleport(33426, 32811, 4, 5, 169, true, false);
            }
        }
    }

	private final ArrayList<L1NpcInstance> _list0 = new ArrayList<L1NpcInstance>();
	private final ArrayList<L1NpcInstance> _list1 = new ArrayList<L1NpcInstance>();
	private final ArrayList<L1NpcInstance> _list2 = new ArrayList<L1NpcInstance>();
	private final ArrayList<L1NpcInstance> _list3 = new ArrayList<L1NpcInstance>();	

	public void add(L1NpcInstance npc, int type) {
		switch (type) {
		case 0:
			if (npc == null || _list0.contains(npc)) {
				return;
			}
			_list0.add(npc);
			break;
		case 1:
			if (npc == null || _list1.contains(npc)) {
				return;
			}
			_list1.add(npc);
			break;
		case 2:
			if (npc == null || _list2.contains(npc)) {
				return;
			}
			_list2.add(npc);
			break;
		case 3:
			if (npc == null || _list3.contains(npc)) {
				return;
			}
			_list3.add(npc);
			break;	
		}
	}

	private void remove(L1NpcInstance npc, int type) {
		switch (type) {
		case 0:
			if (npc == null || !_list0.contains(npc)) {
				return;
			}
			_list0.remove(npc);
			break;
		case 1:
			if (npc == null || !_list1.contains(npc)) {
				return;
			}
			_list1.remove(npc);
			break;
		case 2:
			if (npc == null || !_list2.contains(npc)) {
				return;
			}
			_list2.remove(npc);
			break;
		case 3:
			if (npc == null || !_list3.contains(npc)) {
				return;
			}
			_list3.remove(npc);
			break;	
		}
	}

	private void SpawnMonster() {	
		// 문짝 스폰
		spawn(32869, 32936, (short) _map, 0, 14911, 1, 0); //1층문
		spawn(32809, 33066, (short) _map, 0, 14912, 1, 0); //2층문	

		// 지하1층
		spawn(32933, 32874, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32933, 32871, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32934, 32879, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32928, 32881, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32925, 32887, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32917, 32885, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32914, 32888, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32909, 32883, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32901, 32884, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32891, 32881, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32891, 32873, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32893, 32863, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32888, 32860, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32917, 32885, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32879, 32863, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32869, 32877, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32872, 32885, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32867, 32890, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32873, 32893, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32873, 32900, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32881, 32899, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32917, 32885, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32891, 32902, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32885, 32910, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32893, 32907, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32917, 32885, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32898, 32911, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32900, 32915, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32906, 32910, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32917, 32885, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32915, 32908, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32917, 32885, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32923, 32909, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32926, 32915, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32926, 32922, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32915, 32928, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32903, 32925, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32899, 32933, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32889, 32930, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32881, 32930, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32877, 32925, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32917, 32885, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32875, 32933, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32873, 32923, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32861, 32931, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32911, 32887, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32897, 32878, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32889, 32858, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32870, 32870, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		spawn(32866, 32921, (short) _map, 0, _MonsterList1[GameRandom.nextInt(_MonsterList1.length)], 0, 1);
		
		spawn(32868, 32925, (short) _map, 6, 14905, 0, 1); // 탐욕의 아비쉬
		
		// 지하 2층
		spawn(32866, 32982, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32863, 32985, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32872, 32985, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32868, 32984, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32872, 32996, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32868, 32990, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32862, 33005, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32870, 32998, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32844, 33006, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32865, 32997, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32863, 33003, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32831, 33001, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32858, 33002, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32819, 32990, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32843, 33005, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32840, 33000, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32814, 33007, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32831, 32999, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32812, 33016, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32834, 32993, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32831, 32989, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32824, 32996, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32811, 33027, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32819, 32996, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32810, 32994, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32805, 32999, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32828, 33032, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32806, 33006, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32804, 33016, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32813, 33019, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32815, 33027, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32825, 33027, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32827, 33030, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32836, 33030, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32842, 33033, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32855, 33028, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32847, 33033, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32853, 33041, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32859, 33032, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32862, 33049, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32866, 33032, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32855, 33053, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32857, 33041, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32839, 33055, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32862, 33045, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32851, 33054, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32840, 33052, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32834, 33058, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32827, 33056, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32821, 33053, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32813, 33060, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32805, 33059, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32811, 33049, (short) _map, 0, _MonsterList2[GameRandom.nextInt(_MonsterList2.length)], 0, 2);
		spawn(32812, 33048, (short) _map, 5, 14903, 0, 2); //독꽃
		spawn(32808, 33049, (short) _map, 5, 14903, 0, 2); //독꽃
		spawn(32805, 33051, (short) _map, 5, 14903, 0, 2); //독꽃
		spawn(32804, 33055, (short) _map, 5, 14903, 0, 2); //독꽃
		spawn(32804, 33055, (short) _map, 5, 14903, 0, 2); //독꽃
		spawn(32805, 33060, (short) _map, 5, 14903, 0, 2); //독꽃
		spawn(32812, 33054, (short) _map, 5, 14906, 0, 2); // 탐욕의 삼지바
		
		// 지하 3층
		spawn(32795, 33113, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32798, 33108, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32798, 33112, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32800, 33116, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32803, 33115, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32803, 32120, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32808, 33116, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32808, 33120, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32805, 33129, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32803, 33127, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32802, 33136, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32811, 33133, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32805, 33139, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32798, 33147, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32796, 33142, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32812, 33145, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32808, 33147, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32802, 33156, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32812, 33152, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32809, 33160, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32809, 33168, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32820, 33157, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32828, 33160, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32819, 33161, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32831, 33159, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32812, 33177, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32806, 33180, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32817, 33182, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32827, 33173, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32838, 33169, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32848, 33158, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32844, 33181, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32837, 33179, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32835, 33170, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32847, 33163, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32861, 33157, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32863, 33165, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32853, 33183, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32854, 33183, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32862, 33180, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32858, 33172, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32859, 33161, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32854, 33148, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32862, 33131, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32858, 33121, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32867, 33114, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32855, 33114, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32854, 33108, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32845, 33117, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32840, 33114, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32864, 33109, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32835, 33108, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32852, 33110, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32857, 33125, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32865, 33129, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32862, 33135, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32852, 33118, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32827, 33112, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32847, 33106, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32836, 33109, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32824, 33104, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32817, 33101, (short) _map, 0, _MonsterList3[GameRandom.nextInt(_MonsterList3.length)], 0, 3);
		spawn(32866, 33173, (short) _map, 5, 14907, 0, 3); // 탐욕의 아즈모단
	}

	private L1NpcInstance spawn(int x, int y, short MapId, int Heading, int npcId, int randomRange, int roomnumber) {
		if (Thread.currentThread().isInterrupted()) return null;
		L1NpcInstance npc = null;
		try { 
			npc = CObjectDB.CreateNPC(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(MapId);
			if (randomRange == 0) {
				npc.getLocation().set(x, y, MapId);
				npc.getLocation().forward(Heading);
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(x + GameRandom.spawnOffset(randomRange));
					npc.setY(y + GameRandom.spawnOffset(randomRange));
					if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
						break;
					}
				} while (tryCount < 50);
				if (tryCount >= 50) {
					npc.getLocation().forward(Heading);
				}
			}
			if (npc instanceof L1DoorInstance) {		
				if (npc.getNpcId() == 14911) {
					((L1DoorInstance) npc).setLeftEdgeLocation(32868);
					((L1DoorInstance) npc).setRightEdgeLocation(32870);
					((L1DoorInstance) npc).setDirection(0);
				} else if (npc.getNpcId() == 14912) {
					((L1DoorInstance) npc).setLeftEdgeLocation(32808);
					((L1DoorInstance) npc).setRightEdgeLocation(32810);
					((L1DoorInstance) npc).setDirection(0);			
				}
			}
			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(Heading);

			CWorld.getInstance().storeAndEnterWorld(npc);

			add(npc, roomnumber);
			npc.refreshLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); 

		} catch (Exception e) {
			e.printStackTrace();
		}
		return npc;
	}
}
