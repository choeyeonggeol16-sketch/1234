/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.utils;


import java.util.Calendar;
import java.util.Date;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import I1j.server.server.model.Thread.NpcDeleteTimer;


public class SystemUtil {
	/**
	 * 시스템이 이용중의 heap 사이즈를 메가바이트 단위로 돌려준다.<br>
	 * 이 값에 스택의 사이즈는 포함되지 않는다.
	 * 
	 * @return 이용중의 heap 사이즈
	 */
	public static long getUsedMemoryMB() {
		return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024L / 1024L;
	}

	static private Date date = new Date(0);
	static private String[] weekDay = { "일", "월", "화", "수", "목", "금", "토" };

	/**
	 * 시간에 해당하는 요일을 리턴.
	 * 
	 * @param time
	 * @return
	 */
	static public String getYoil(long time) {
		date.setTime(time);
		Calendar c = Calendar.getInstance();
		c.setTime(date);
		return weekDay[c.get(Calendar.DAY_OF_WEEK) - 1];
	}
	
	/** 엔피씨를 스폰한다
	 * @param x @param y @param map @param npcId @param randomRange
	 * @param timeMillisToDelete @param movemap (이동시킬 맵을 설정한다 - 안타레이드) */
	public static void spawn(int x, int y, short map, int npcId, int randomRange, int timeMillisToDelete) {
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
			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			
			L1World.getInstance().storeObject(npc);
			L1World.getInstance().addVisibleObject(npc); 

			npc.getLight().turnOnOffLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			if (0 < timeMillisToDelete) {
				NpcDeleteTimer timer = new NpcDeleteTimer(npc, timeMillisToDelete);				
				timer.begin();
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
				npc.setHeading(0);

				if (npcId == 45545 || npcId == 45516 || npcId == 45529) {
					MonsterInstance mon = (MonsterInstance) npc;
					mon.setMovementDistance(15);
				}

				L1World.getInstance().storeObject(npc);
				L1World.getInstance().addVisibleObject(npc);

				npc.getLight().turnOnOffLight();
				npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
				if (0 < timeMillisToDelete) {
					NpcDeleteTimer timer = new NpcDeleteTimer(npc, timeMillisToDelete);
					timer.begin();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return npc;
	}

}
