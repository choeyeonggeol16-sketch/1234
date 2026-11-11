package l1j.server.server.Controller;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import I1j.server.server.model.Thread.NpcDeleteTimer;
import l1j.server.Template.LORnd;
import l1j.server.Templates.L1Boss;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.BossMonsterSpawnList;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.utils.CommonUtil;
import manager.SJManager;

public class BossController implements Runnable {

	private static BossController instance;
	private static final ConcurrentHashMap<Integer, Integer> _bossesId = new ConcurrentHashMap<Integer, Integer>(256);

	public static BossController getInstance() {
		if (instance == null) {
			instance = new BossController();
			GeneralThreadPool.getInstance().execute(instance);
		}
		return instance;
	}

	@SuppressWarnings("deprecation")
	private void spawn_check() {
		try {
			List<L1Boss> list = BossMonsterSpawnList.getList();
			if (list.size() > 0) {
				long time = System.currentTimeMillis();
				Date date = new Date(time);
				int hour = date.getHours();
				int min = date.getMinutes();
				for (L1Boss b : list) {
					try {
						// 객체 생성.
						if (b.isSpawnTime(hour, min, time) && isSpwanRate(b.getSpawnRate())) { 
							Integer id = _bossesId.get(b.getNpcId());
							if (id != null) {
								L1Object obj = L1World.getInstance().findObject(id);
								if (obj != null) {
									L1Character c = (L1Character) obj;
									if (!c.isDead()) {
										continue;
									}
								}
							}
							if (b.getnonespawntime() != 0 && LORnd.isWinning(100, b.getnonespawntime())) {
								return;
							}
							boss_spawn(b, b.getX(), b.getY(), (short) b.getMap(), b.getNpcId(),b.getRndLoc(), 3600000, b.isMent(), b.isYn());
							//_bossesId.put(boss.getNpcId(), boss.getId());
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 컨트롤러가 1분마다 한번씩 호출됨.
	 */
	@Override
	public void run() {
		try {
			spawn_check();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			GeneralThreadPool.getInstance().schedule(this, 60000L);
		}
	}
	
	public static MonsterInstance boss_spawn(L1Boss b, int x, int y, short map, int npcId, int randomRange,int timeMillisToDelete, boolean ment, boolean ynment) {
		L1NpcInstance npc = null;
		try {
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
			npc.setHeading(5);

			L1World.getInstance().storeObject(npc);
			L1World.getInstance().addVisibleObject(npc);


			if (ment) {
				L1World.getInstance().broadcastPacketToAll(new S_SystemMessage(b.getMentMessage()));
				L1World.getInstance().broadcastPacketToAll(new S_PacketBox(S_PacketBox.GREEN_MESSAGE, b.getMentMessage()));
			}

			if ((ynment)) {
				for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
			        if ((!pc.isPrivateShop()) && (pc.isBossNotify()) && (pc.getMap().isEscapable())) {

//			        	if ((pc.getAI() != null) && (pc.getAI().getBotType() == MJBotType.HUNT)) {
//			              MJBotBossNotifier ntf = MJBotBossNotifierLoader.getInstance().get(npc.getNpcId());
//			              MJBotAI ai = pc.getAI();
//			              if (((!(ai instanceof MJBotMovableAI)) || (((MJBotMovableAI)ai).getWarCastle() == -1)) && 
//			                
//			                (ntf != null) && (ai.getBrain().toRand(100 - ntf.aggro) < ai.getBrain().getHormon())) {
//			                ai.teleport(npc.getX(), npc.getY(), npc.getMapId());
//			              }
			           // } else {
							pc.setBossYN(npc.getNpcId());
							if (b.getYnMessage() == null || b.getYnMessage().equals("")) {
								//pc.sendPackets(new S_Message_YN(C_Attr.MSGCODE_6008_BOSS, 6008, npc.getName() + "가 스폰되었습니다. 레이드하러 가볼까요?"));
							}else {
								//pc.sendPackets(new S_Message_YN(C_Attr.MSGCODE_6008_BOSS, 6008, b.getYnMessage()));
							}
			            //}
			        }
			        	

				}
				try {
					GeneralThreadPool.getInstance().schedule(new Runnable() {
						@Override
						public void run() {
							for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
								if (pc.getBossYN() != 0) {
									pc.setBossYN(0);
								}
							}
						}
					}, 15000);// 보스스폰대기시간
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			if (0 < timeMillisToDelete) {
				NpcDeleteTimer timer = new NpcDeleteTimer(npc, timeMillisToDelete);
				timer.begin();
			}

			if (SJManager.start) {
				SJManager.getInstance().BossAppend(npc.getName());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return npc instanceof MonsterInstance ? (MonsterInstance) npc : null;
	}
	
	/**
	 * 스폰 확률을 계산한다.
	 * @param b
	 */
	public boolean isSpwanRate(int i) {
		try {
			int spawn_chance = CommonUtil.random(1000000);
			//System.out.println("보스스폰확률 : " + spawn_chance + " / " + i);
			if (spawn_chance < i) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	
}