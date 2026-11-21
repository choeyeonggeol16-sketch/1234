package l1j.server.server.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.world.instance.dungeon.orim.OrimController;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1FieldObjectInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;

public class L1SpawnUtil {
	private static Logger _log = Logger.getLogger(L1SpawnUtil.class.getName());

	public static L1NpcInstance spawnAndGet(L1PcInstance pc, int npcId, int randomRange, int timeMillisToDelete) {
		L1NpcInstance npc = null;
		try {
			npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(pc.getMapId());
			if (randomRange == 0) {
				npc.getLocation(). set(pc.getLocation());
				npc.getLocation(). forward(pc.getHeading());
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(pc.getX() + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					npc.setY(pc.getY() + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					if (npc.getMap(). isInMap(npc.getLocation()) && npc.getMap(). isPassable(npc.getLocation())) {
						break;
					}
				} while (tryCount < 50);

				if (tryCount >= 50) {
					npc.getLocation(). set(pc.getLocation());
					npc.getLocation(). forward(pc.getHeading());
				}
			}

			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(pc.getHeading());

			L1World.getInstance().storeAndEnterWorld(npc);
			
			if (npcId == 181164) { //기르타스 큰화염
			    SC_WorldPutObject.broadcastNpc(npc);
			    SC_DoActionGFX.broadcast(npc, 4);
			    npc.setStatus(4); 
			    SC_WorldPutObject.broadcastNpc(npc);
			    SC_DoActionGFX.broadcast(npc, 0);
			    npc.setStatus(0);
			    SC_WorldPutObject.broadcastNpc(npc);
			    SC_DoActionGFX.broadcast(npc, 8);
			    npc.setStatus(8);
			}
			npc.getNpcTemplate().doBornNpc(npc);
			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			if (0 < timeMillisToDelete) {
				npc.startTimedDelete(timeMillisToDelete);  
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return npc;
	}
	
	public static L1NpcInstance spawn(L1PcInstance pc, int npcId, int randomRange, int timeMillisToDelete, int d) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(pc.getMapId());
			if (randomRange == 0) {
				npc.getLocation().set(pc.getLocation());
				npc.getLocation().forward(pc.getHeading());
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(pc.getX() + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					npc.setY(pc.getY() + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
						break;
					}
				} while (tryCount < 50);

				if (tryCount >= 50) {
					npc.getLocation().set(pc.getLocation());
					npc.getLocation().forward(pc.getHeading());
				}
			}

			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(pc.getHeading());

			L1World.getInstance().storeAndEnterWorld(npc);

			if (npcId == 181164) { // 기르타스 큰화염
				SC_WorldPutObject.broadcastNpc(npc);
				SC_DoActionGFX.broadcast(npc, 4);
				npc.setStatus(4); 
				SC_WorldPutObject.broadcastNpc(npc);
				SC_DoActionGFX.broadcast(npc, 0);
				npc.setStatus(0);
				SC_WorldPutObject.broadcastNpc(npc);
				SC_DoActionGFX.broadcast(npc, 8);
				npc.setStatus(8);
			}
			npc.getNpcTemplate().doBornNpc(npc);
			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			if (0 < timeMillisToDelete) {
				npc.startTimedDelete(timeMillisToDelete);  
			}
			return npc; 
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void spawn(L1PcInstance pc, int npcId, int randomRange, int timeMillisToDelete) {
		try {
			L1NpcInstance npc = NpcTable.getInstance(). newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(pc.getMapId());
			if (randomRange == 0) {
				npc.getLocation(). set(pc.getLocation());
				npc.getLocation(). forward(pc.getHeading());
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(pc.getX() + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					npc.setY(pc.getY() + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					if (npc.getMap(). isInMap(npc.getLocation()) && npc.getMap(). isPassable(npc.getLocation())) {
						break;
					}
				} while (tryCount < 50);

				if (tryCount >= 50) {
					npc.getLocation(). set(pc.getLocation());
					npc.getLocation(). forward(pc.getHeading());
				}
			}

			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(pc.getHeading());

			L1World.getInstance().storeAndEnterWorld(npc);
			
			if (npcId == 181164) { //기르타스 큰화염
			    SC_WorldPutObject.broadcastNpc(npc);
			    SC_DoActionGFX.broadcast(npc, 4);
			    npc.setStatus(4); 
			    SC_WorldPutObject.broadcastNpc(npc);
			    SC_DoActionGFX.broadcast(npc, 0);
			    npc.setStatus(0);
			    SC_WorldPutObject.broadcastNpc(npc);
			    SC_DoActionGFX.broadcast(npc, 8);
			    npc.setStatus(8);
			}
			npc.getNpcTemplate().doBornNpc(npc);
			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			if (0 < timeMillisToDelete) {
				npc.startTimedDelete(timeMillisToDelete);  
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void spawngmcmd(L1PcInstance pc, int npcId) {
		try {
			L1NpcInstance npc = NpcTable.getInstance(). newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(pc.getMapId());
			npc.getLocation(). set(pc.getLocation());
			//npc.getLocation(). forward(pc.getHeading());
			
			npc.setHomeX(pc.getX());
			npc.setHomeY(pc.getY());
			npc.setHeading(pc.getHeading());

			L1World.getInstance().storeAndEnterWorld(npc);
			
			

			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** 엔피씨를 스폰한다
	 * @param x @param y @param map @param npcId @param randomRange
	 * @param timeMillisToDelete @param movemap (이동시킬 맵을 설정한다 - 안타레이드) */
	public static void spawn2(int x, int y, short map, int npcId, int randomRange, int timeMillisToDelete, int movemap) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(map);
			if (randomRange == 0) {
				npc.getLocation().set(x,y,map);
				npc.getLocation().forward(5);
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(x + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					npc.setY(y + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					if (npc.getMap(). isInMap(npc.getLocation()) && npc.getMap(). isPassable(npc.getLocation())) {
						break;
					}
					Thread.sleep(1);
				} while (tryCount < 50);

				if (tryCount >= 50) {
					npc.getLocation().set(x,y,map);
					npc.getLocation().forward(5);
				}
			}

			if (npc.getNpcId() == 900007 || npc.getNpcId() == 900015 || npc.getNpcId() == 900036 || npc.getNpcId() == 900219) {
				try (var sharedAction = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
				        SC_DoActionGFX.nativeWriter(npc.getId(), ActionCodes.ACTION_AxeWalk), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
				    for (L1PcInstance _pc : L1World.getInstance().getVisiblePlayer(npc)) {
				        npc.onPerceive(_pc);
				        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(sharedAction, _pc);
				    }
				} catch (RuntimeException visualFailure) {
                java.util.logging.Logger.getLogger(L1SpawnUtil.class.getName()).log(
                        java.util.logging.Level.WARNING, "NPC spawn/action visual send failed", visualFailure);
            }
			}
			/*
			if (npc.getNpcId() == 460000365) {
				for (L1PcInstance _pc : L1World.getInstance().getVisiblePlayer(npc)) {
					npc.onPerceive(_pc);
					SC_DoActionGFX.send(_pc.getNetConnection(), npc.getId(), 95);
				}
			}
			*/
				npc.setHomeX(npc.getX());
				npc.setHomeY(npc.getY());
				npc.setHeading(5);
			
			
			if(npcId == 900007 || npcId == 900008 || npcId == 900036 || npcId == 900037 || npcId == 900219 || npcId == 5101 || npcId == 5102){ // 이동할 맵 셋팅
				L1FieldObjectInstance fobj = (L1FieldObjectInstance)npc;
				fobj.setMoveMapId(movemap);
			}

			L1World.getInstance().storeAndEnterWorld(npc);
			if (npc.getNpcId() == 460000365) {
			npc.getNpcTemplate().doBornNpc(npc);
			}
			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			if (0 < timeMillisToDelete) {
				npc.startTimedDelete(timeMillisToDelete);  
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void spawn3(int x, int y, short MapId, int Heading, int npcId, int randomRange, boolean isUsePainwand,
			int timeMillisToDelete) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(MapId);
			if (randomRange == 0) {
				npc.getLocation().forward(Heading);
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
					npc.getLocation().forward(Heading);
				}
			}

			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(Heading);
			L1World.getInstance().storeAndEnterWorld(npc);

			npc.getLight().turnOnOffLight();
			npc.startChat(0);
			if (timeMillisToDelete > 0) {
				npc.startTimedDelete(timeMillisToDelete);  
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "L1SpawnUtil[]Error6", e);
		}
	}
	
	public static L1MonsterInstance spawnfieldboss(int x, int y, short map, int npcId, int randomRange, int timeMillisToDelete, int movemap) {
		L1NpcInstance npc = null;
		try {
			npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(map);
			if (randomRange == 0) {
				npc.getLocation().set(x,y,map);
				npc.getLocation().forward(5);
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(x + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					npc.setY(y + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					if (npc.getMap(). isInMap(npc.getLocation()) && npc.getMap(). isPassable(npc.getLocation())) {
						break;
					}
					Thread.sleep(1);
				} while (tryCount < 50);

				if (tryCount >= 50) {
					npc.getLocation().set(x,y,map);
					npc.getLocation().forward(5);
				}
			}

			if (npc.getNpcId() == 900007 || npc.getNpcId() == 900015 || npc.getNpcId() == 900036 || npc.getNpcId() == 900219) {
				try (var sharedAction = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
				        SC_DoActionGFX.nativeWriter(npc.getId(), ActionCodes.ACTION_AxeWalk), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
				    for (L1PcInstance _pc : L1World.getInstance().getVisiblePlayer(npc)) {
				        npc.onPerceive(_pc);
				        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(sharedAction, _pc);
				    }
				} catch (RuntimeException visualFailure) {
                java.util.logging.Logger.getLogger(L1SpawnUtil.class.getName()).log(
                        java.util.logging.Level.WARNING, "NPC spawn/action visual send failed", visualFailure);
            }
			}
			if (npc.getNpcId() == 460000365) {
				try (var sharedAction = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
				        SC_DoActionGFX.nativeWriter(npc.getId(), 95), Integer.MIN_VALUE, Integer.MAX_VALUE)) {
				    for (L1PcInstance _pc : L1World.getInstance().getVisiblePlayer(npc)) {
				        npc.onPerceive(_pc);
				        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipientBestEffort(sharedAction, _pc);
				    }
				} catch (RuntimeException visualFailure) {
                java.util.logging.Logger.getLogger(L1SpawnUtil.class.getName()).log(
                        java.util.logging.Level.WARNING, "NPC spawn/action visual send failed", visualFailure);
            }
			}
			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(5);
			
			if(npcId == 900007 || npcId == 900008 || npcId == 900036 || npcId == 900037 || npcId == 900219 || npcId == 5101 || npcId == 5102){ // 이동할 맵 셋팅
				L1FieldObjectInstance fobj = (L1FieldObjectInstance)npc;
				fobj.setMoveMapId(movemap);
			}

			L1World.getInstance().storeAndEnterWorld(npc);
			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			if (0 < timeMillisToDelete) {
				npc.startTimedDelete(timeMillisToDelete);  
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return npc instanceof L1MonsterInstance ? (L1MonsterInstance)npc : null;
	}
	
	/** 이 벤 트 커 멘 드 **/
	public static L1NpcInstance Gmspawn(int npcId, int x, int y, short mapid,
			int heading, int timeMinToDelete) {
		L1NpcInstance npc = null;
		try {
			npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(mapid);
			npc.setX(x);
			npc.setY(y);
			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(heading);

			L1World.getInstance().storeAndEnterWorld(npc);

			if (0 < timeMinToDelete) {
				npc.startTimedDelete(timeMinToDelete);  
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return npc;
	}
	
	public static void spawn10(int x, int y, short map, int heading,
			int npcId, int timeMillisToDelete, L1Clan clan) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());					
			npc.setMap(map);
			npc.getLocation().set(x, y, map);
			npc.getLocation().forward(heading);
			
			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(heading);
			if (npc instanceof L1MerchantInstance) {
				L1MerchantInstance mer = (L1MerchantInstance) npc;
				mer.setNameId(clan.getClanName());
				mer.setClanid(clan.getClanId());
				mer.setClanname(clan.getClanName());
			}

			L1World.getInstance().storeAndEnterWorld(npc);

			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			if (0 < timeMillisToDelete) {
				npc.startTimedDelete(timeMillisToDelete);  
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	public static void spawn5(int x, int y, short MapId, int Heading, int npcId, int randomRange, boolean isUsePainwand) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(MapId);
			if (randomRange == 0) {
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
			npc.setHeading(npc.getHeading());
			L1World.getInstance().storeAndEnterWorld(npc);

			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시

			if (npc.getMapId() == 9101) {
				if (npcId == 91240) {
					if (Heading == 4)
						OrimController.getInstance().setShell1(npc);
					else
						OrimController.getInstance().setShell2(npc);
				} else if ((npcId != 91222) && (npcId != 91233) && (npcId != 91235) && (npcId != 91243)) {
					OrimController.getInstance().addMonList(npc);
				}
			}

		} catch (Exception e) {
			_log.log(Level.SEVERE, "L1SpawnUtil[]Error3", e);
		}
	}
	
	public static void spawn(L1NpcInstance pc, int npcId, int randomRange) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(pc.getMapId());

			if (randomRange == 0) {
				npc.getLocation().set(pc.getLocation());
				npc.getLocation().forward(pc.getHeading());
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(pc.getX() + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					npc.setY(pc.getY() + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					if (npc.getMap(). isInMap(npc.getLocation()) && npc.getMap(). isPassable(npc.getLocation())) {
						break;
					}
					Thread.sleep(1);
				} while (tryCount < 50);

				if (tryCount >= 50) {
					npc.getLocation().set(pc.getLocation());
					npc.getLocation().forward(pc.getHeading());
				}
			}
			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(pc.getHeading());
			
			L1World.getInstance().storeAndEnterWorld(npc);
			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//타임밀리
	public static void spawn6(int x, int y, short map, int npcId, int randomRange, int timeMillisToDelete, int movemap) {
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
					if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
						break;
					}
					Thread.sleep(1);
				} while (tryCount < 50);

				if (tryCount >= 50) {
					npc.getLocation().set(x, y, map);
					npc.getLocation().forward(5);
				}
			}

			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(4);

			L1World.getInstance().storeAndEnterWorld(npc);

			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			if (0 < timeMillisToDelete) {
				npc.startTimedDelete(timeMillisToDelete);  
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	public static L1NpcInstance spawnCount(int x, int y, short map, int npcId, int randomRange, int timeMillisToDelete, int count) {
		L1NpcInstance npc = null;
		try {
			for (int i = 0; i < count; i++) {
				npc = NpcTable.getInstance().newNpcInstance(npcId);
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
						if (npc.getMap().isInMap(npc.getLocation()) && npc.getMap().isPassable(npc.getLocation())) {
							break;
						}
						Thread.sleep(1);
					} while (tryCount < 50);

					if (tryCount >= 50) {
						npc.getLocation().set(x, y, map);
						npc.getLocation().forward(5);
					}
				}

				npc.setHomeX(npc.getX());
				npc.setHomeY(npc.getY());
				npc.setHeading(6);

				if (npcId == 45545 || npcId == 45516 || npcId == 45529 ) {
					L1MonsterInstance mon = (L1MonsterInstance) npc;
					mon.setMovementDistance(15);
				}

				L1World.getInstance().storeAndEnterWorld(npc);

				npc.getLight().turnOnOffLight();
				npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
				if (0 < timeMillisToDelete) {
					npc.startTimedDelete(timeMillisToDelete);  
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return npc;
	}

	
	public static void spawn(L1NpcInstance pc, int npcId, int randomRange, int timeMillisToDelete) {
		try {
			L1NpcInstance npc = NpcTable.getInstance().newNpcInstance(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(pc.getMapId());

			if (randomRange == 0) {
				npc.getLocation().set(pc.getLocation());
				npc.getLocation().forward(pc.getHeading());
			} else {
				int tryCount = 0;
				do {
					tryCount++;
					npc.setX(pc.getX() + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					npc.setY(pc.getY() + (int) (Math.random() * randomRange) - (int) (Math.random() * randomRange));
					if (npc.getMap(). isInMap(npc.getLocation()) && npc.getMap(). isPassable(npc.getLocation())) {
						break;
					}
					Thread.sleep(1);
				} while (tryCount < 50);

				if (tryCount >= 50) {
					npc.getLocation().set(pc.getLocation());
					npc.getLocation().forward(pc.getHeading());
				}
			}
			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(pc.getHeading());
			
			L1World.getInstance().storeAndEnterWorld(npc);
			
			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			
			if (0 < timeMillisToDelete) {
				npc.startTimedDelete(timeMillisToDelete);  
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	

	public static void spawn(L1PcInstance pc, int i, int j, int k, boolean b) {
		// TODO Auto-generated method stub
		
	}
}