package l1j.server.server.Controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import I1j.server.server.model.Thread.NpcDeleteTimer;
import l1j.server.Templates.L1EventNpcSpawn;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.EventNpcSpawnTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_SystemMessage;

public class EventNpcSpawnCotroller implements Runnable {
	public static final int SLEEP_TIME = 60000;

	private static List<L1EventNpcSpawn> npc_list = new ArrayList<L1EventNpcSpawn>();

	private static EventNpcSpawnCotroller _instance;

	public static EventNpcSpawnCotroller getInstance() {
		if (_instance == null) {
			_instance = new EventNpcSpawnCotroller();
		}
		return _instance;
	}

	@SuppressWarnings("deprecation")
	private void spawn_check() {
		try {
			List<L1EventNpcSpawn> list = EventNpcSpawnTable.getInstance().getList();
			if (list.size() > 0) {
				long time = System.currentTimeMillis();
				Date date = new Date(time);
				int hour = date.getHours();
				int min = date.getMinutes(); 
				for (L1EventNpcSpawn b : list) {
					try {   
						if ((b.isSpawnTime(hour, min, time)) && (!isSpawn(b))) {  
							npc_spawn(b, b.getSpawnLoc()[0], b.getSpawnLoc()[1], (short) b.getSpawnLoc()[2],b.getNpcId(), 0, b.getKeepTime() * 1000, b.getMent());
							npc_list.add(b);  
							try {
								GeneralThreadPool.getInstance().schedule(new Runnable() {
									public void run() {
										EventNpcSpawnCotroller.eventEnd(b); 
									}
								}, b.getKeepTime() * 1000);
							} catch (Exception e1) {
								e1.getStackTrace();
							}
						}

					} catch (Exception e) {
						e.getStackTrace();
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static boolean isSpawn(L1EventNpcSpawn b) {
		if (npc_list == null) {
			return false;
		}
		for (L1EventNpcSpawn mi : npc_list) { 
			if (mi == b) {
				return true;
			}
		}
		return false;
	}
	public void run() {
		try {
			spawn_check(); 
		} catch (Exception e) {
			e.getStackTrace();
		}
	}

	public static void npc_spawn(L1EventNpcSpawn b, int x, int y, short map, int npcId, int randomRange,int timeMillisToDelete, String ment) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(map);
			if (randomRange == 0) {
				npc.getLocation().set(x, y, map);
				npc.getLocation().forward(5);
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(x + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					npc.setY(y + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					if ((npc.getMap().isInMap(npc.getLocation())) && (npc.getMap().isPassable(npc.getLocation()))) {
						break;
					}
					Thread.sleep(1L);
				} while (tryCount < 50);

				if (tryCount >= 50) {
					npc.getLocation().set(x, y, map);
					npc.getLocation().forward(5);
				}
			}

			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(5);

			L1World.getInstance().storeObject(npc);
			L1World.getInstance().addVisibleObject(npc);

			L1World.getInstance().broadcastPacketToAll(new S_SystemMessage(ment));
			L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, ment));

			npc.getLight().turnOnOffLight();
			npc.startChat(0);
			if (0 < timeMillisToDelete) {
				NpcDeleteTimer timer = new NpcDeleteTimer(npc, timeMillisToDelete);
				timer.begin();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void eventEnd(L1EventNpcSpawn b) {
		if (b == null) {
			return;
		}
		for (int i = 0; i < b.getExitMap().length; i++) {
			for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
				if (pc == null)
					continue;
				if (pc.getMapId() == b.getExitMap()[i]) { 
					pc.teleport( b.getExitLoc()[0], b.getExitLoc()[1], (short) b.getExitLoc()[2], pc.getHeading(), 169, false, false);
					pc.sendPackets(new S_SystemMessage(b.getEventName() + " 가(이) 종료되었습니다."));
				}
			}
		}
		npc_list.remove(b);
	}
}