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
package l1j.server.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.types.Point;

public class L1Location extends Point {
	
	private static Random _random = new Random();
	protected L1Map _map = L1Map.newNull();

	public L1Location() {
		super();
	}

	public L1Location(L1Location loc) {
		this(loc._x, loc._y, loc._map);
	}

	public L1Location(int x, int y, int mapId) {
		super(x, y);
		setMap(mapId);
	}

	public L1Location(int x, int y, L1Map map) {
		super(x, y);
		_map = map;
	}

	public L1Location(Point pt, int mapId) {
		super(pt);
		setMap(mapId);
	}

	public L1Location(Point pt, L1Map map) {
		super(pt);
		_map = map;
	}

	public void set(L1Location loc) {
		_map = loc._map;
		_x = loc._x;
		_y = loc._y;
	}

	public void set(int x, int y, int mapId) {
		set(x, y);
		setMap(mapId);
	}

	public void set(int x, int y, L1Map map) {
		set(x, y);
		_map = map;
	}

	public void set(Point pt, int mapId) {
		set(pt);
		setMap(mapId);
	}

	public void set(Point pt, L1Map map) {
		set(pt);
		_map = map;
	}

	public L1Map getMap() {
		return _map;
	}

	public int getMapId() {
		return _map.getId();
	}

	public void setMap(L1Map map) {
		_map = map;
	}

	public void setMap(int mapId) {
		_map = L1WorldMap.getInstance().getMap((short) mapId);
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof L1Location)) {
			return false;
		}
		L1Location loc = (L1Location) obj;
		return (this.getMap() == loc.getMap()) && (this.getX() == loc.getX())
				&& (this.getY() == loc.getY());
	}

	@Override
	public L1Location clone() {
		return new L1Location(this);
	}

	@Override
	public int hashCode() {
		return 7 * _map.getId() + super.hashCode();
	}

	/**
	 * 이 Location에 대한, 이동 가능한 랜덤 범위의 Location를 돌려준다.
	 * 랜덤 텔레포트의 경우는, 성에리어, 아지트내의 Location는 반환되지 않는다.
	 * 
	 * @param max
	 *            랜덤 범위의 최대치
	 * @param isRandomTeleport
	 *            랜덤 텔레포트인가
	 * @return 새로운 Location
	 */
	public L1Location randomLocation(int max, boolean isRandomTeleport) {
		return randomLocation(0, max, isRandomTeleport);
	}

	/**
	 * 이 Location에 대한, 이동 가능한 랜덤 범위의 Location를 돌려준다.
	 * 랜덤 텔레포트의 경우는, 성에리어, 아지트내의 Location는 반환되지 않는다.
	 * 
	 * @param min
	 *            랜덤 범위의 최소치(0으로 자신의 좌표를 포함한다)
	 * @param max
	 *            랜덤 범위의 최대치
	 * @param isRandomTeleport
	 *            랜덤 텔레포트인가
	 * @return 새로운 Location
	 */
	public L1Location randomLocation(int min, int max, boolean isRandomTeleport) {
		return L1Location.randomLocation(this, min, max, isRandomTeleport);
	}
	
	private static L1Location randomNearLocation(L1Location baseLocation, List<L1Location> locations) {
		L1Location nextCandidate = baseLocation;

		for (int xOffset = -1; xOffset <= 1; ++xOffset) {
			for (int yOffset = -1; yOffset <= 1; ++yOffset) {
				if (xOffset == 0 && yOffset == 0) {
					continue;
				}

				L1Location location = new L1Location(baseLocation);
				location._x += xOffset;
				location._y += yOffset;

				boolean find = false;

				for (L1Location existLoc : locations) {
					if (existLoc._x == location._x && existLoc._y == location._y) {
						find = true;
						break;
					}
				}

				if (find) {
					continue;
				}

				L1Map map = location.getMap();
				short heading = (short) baseLocation.getHeading(location);
				if (map.isUserPassable(baseLocation.getX(), baseLocation.getY(), heading)) {
					if (L1World.getInstance().getVisiblePoint(location, 0).size() > 0) {
						Random rnd = new Random();
						if (rnd.nextInt(100) < 15) {
							return location;
						}
					} else {
						return location;
					}
					nextCandidate = location;
				}

				locations.add(location);
			}
		}

		if (nextCandidate._x == baseLocation._x && nextCandidate._y == baseLocation._y) {
			return nextCandidate;
		}

		return randomNearLocation(nextCandidate, locations);
	}
	
	public static L1Location randomNearLocation(L1Location baseLocation) {
		L1Map map = baseLocation.getMap();
		if (map.isUserPassable(baseLocation)) {
			if (L1World.getInstance().getVisiblePoint(baseLocation, 0).size() > 0) {
				Random rnd = new Random();
				if (rnd.nextInt(100) < 15) {
					return baseLocation;
				}
			} else {
				return baseLocation;
			}
		}

		List<L1Location> locations = new ArrayList<L1Location>();
		locations.add(baseLocation);
		return randomNearLocation(baseLocation, locations);
	}

	/**
	 * 인수의 Location에 대해서, 이동 가능한 랜덤 범위의 Location를 돌려준다.
	 * 랜덤 텔레포트의 경우는, 성에리어, 아지트내의 Location는 반환되지 않는다.
	 * 
	 * @param baseLocation
	 *            랜덤 범위의 바탕으로 되는 Location
	 * @param min
	 *            랜덤 범위의 최소치(0으로 자신의 좌표를 포함한다)
	 * @param max
	 *            랜덤 범위의 최대치
	 * @param isRandomTeleport
	 *            랜덤 텔레포트인가
	 * @return 새로운 Location
	 */
	public static L1Location randomLocation(L1Location baseLocation, int min, int max, boolean isRandomTeleport) {
		if (min > max) {
			throw new IllegalArgumentException("min > max가 되는 인수는 무효");
		}
		if (max <= 0) {
			return new L1Location(baseLocation);
		}
		if (min < 0) {
			min = 0;
		}

		L1Location newLocation = new L1Location();
		int newX = 0;
		int newY = 0;
		int locX = baseLocation.getX();
		int locY = baseLocation.getY();
		short mapId = (short) baseLocation.getMapId();
		L1Map map = baseLocation.getMap();

		newLocation.setMap(map);

		int locX1 = locX - max;
		int locX2 = locX + max;
		int locY1 = locY - max;
		int locY2 = locY + max;

		int mapX1 = map.getX();
		int mapX2 = mapX1 + map.getWidth();
		int mapY1 = map.getY();
		int mapY2 = mapY1 + map.getHeight();

		if (locX1 < mapX1) {
			locX1 = mapX1;
		}
		if (locX2 > mapX2) {
			locX2 = mapX2;
		}
		if (locY1 < mapY1) {
			locY1 = mapY1;
		}
		if (locY2 > mapY2) {
			locY2 = mapY2;
		}

		int diffX = locX2 - locX1;
		int diffY = locY2 - locY1;

		int trial = 0;
		int amax = (int) Math.pow(1 + (max * 2), 2);
		int amin = (min == 0) ? 0 : (int) Math.pow(1 + ((min - 1) * 2), 2);
		int trialLimit = 40 * amax / (amax - amin);

		while (true) {
			if (trial >= trialLimit) {
				newLocation.set(locX, locY);
				break;
			}
			trial++;
			Random rnd = L1Location._random;
			try {
				if (rnd != null) {
					newX = locX1 + L1Location._random.nextInt(diffX + 1);
					newY = locY1 + L1Location._random.nextInt(diffY + 1);
				} else {
					newX = locX1;
					newY = locY1;
				}
			} catch (Exception e) {
			}
			newLocation.set(newX, newY);
			if (baseLocation.getTileLineDistance(newLocation) < min) {
				continue;

			}
			if (isRandomTeleport) {
				if (L1CastleLocation.checkInAllWarArea(newX, newY, mapId)) {
					continue;
				}

				if (L1HouseLocation.isInHouse(newX, newY, mapId)) {
					continue;
				}

				/** 샌드웜 지역 **/
				if (newX >= 32704 && newX <= 32835 && newY >= 33110
						&& newY <= 33234 && mapId == 4)
					continue;
				// 버경장
				if ((newX >= 33472 && newX <= 33536)
						&& (newY >= 32838 && newY <= 32876) && mapId == 4)
					continue;
				// 화둥 위
				if ((newX >= 33472 && newX <= 33792) && newY <= 32191
						&& mapId == 4)
					continue;

				if ((newX >= 33464 && newX <= 33531)
						&& (newY >= 33168 && newY <= 33248) && mapId == 4)
					continue;

				if (mapId == 400
						&& ((newX >= 32703 && newX <= 32874 && newY >= 32908 && newY <= 33029) || (newX >= 32729
								&& newX <= 32813 && newY >= 32822 && newY <= 32927))) {
					continue;
				}

				if (mapId == 4
						&& ((newX >= 33331 && newX <= 33341 && newY >= 32430 && newY <= 32441)
								|| (newX >= 33258 && newX <= 33267
										&& newY >= 32396 && newY <= 32407)
								|| (newX >= 33388 && newX <= 33397
										&& newY >= 32339 && newY <= 32350) || (newX >= 33443
								&& newX <= 33483 && newY >= 32315 && newY <= 32357))) {
					continue;
				}
				
				/** 용의 계곡 던전 1층 **/
				if (mapId == 30  && !(newX >= 32718 && newX <= 32818 && newY >= 32718 && newY <= 32818)){
					continue;
				}
				/** 상아탑 7층 **/
				if (mapId == 285 && (newX >= 32841 && newX <= 32957 && newY >= 32706 && newY <= 32825)){
					continue;
				}
				if (mapId == 15883 && !(newX >= 32736 && newX <= 32924 && newY >= 32732 && newY <= 32928))
					continue;
			}
			if (map.isInMap(newX, newY) && map.isPassable(newX, newY)) {
				break;
			}
		}
		return newLocation;
	}
}
