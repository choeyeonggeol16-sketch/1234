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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import l1j.server.Templates.L1Npc;
import l1j.server.configure.Config;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.types.Point;

public class L1Spawn {
	private final L1Npc _template;

	private int _id; 
	private String _location;
	private int _maximumCount;
	private int _npcid;
	private int _groupId;
	private int _locx;
	private int _locy;
	private int _randomx;
	private int _randomy;
	private int _locx1;
	private int _locy1;
	private int _locx2;
	private int _locy2;
	private int _heading;
	private int _minRespawnDelay;
	private int _maxRespawnDelay;
	private final Constructor<?> _constructor;
	private short _mapid;
	private boolean _respaenScreen;
	private int _movementDistance;
	private boolean _rest;
	private int _spawnType;
	private int _delayInterval;
	private HashMap<Integer, Point> _homePoint = null;

	private static Random _random = new Random(System.nanoTime());

	private String _name;

	private class SpawnTask implements Runnable {
		private int _spawnNumber;
		private int _objectId;

		private SpawnTask(int spawnNumber, int objectId) {
			_spawnNumber = spawnNumber;
			_objectId = objectId;
		}

		@Override
		public void run() {		
			doSpawn(_spawnNumber, _objectId);

		}
	}

	public L1Spawn(L1Npc mobTemplate) throws SecurityException, ClassNotFoundException {
		_template = mobTemplate;
		String implementationName = _template.getImpl();
		_constructor = Class.forName("l1j.server.server.model.Instance." + implementationName + "Instance").getConstructors()[0];
	}

	public String getName() {
		return _name;
	}
	public void setName(String name) {
		_name = name;
	}
	public short getMapId() {
		return _mapid;
	}
	public void setMapId(short _mapid) {
		this._mapid = _mapid;
	}
	public boolean isRespawnScreen() {
		return _respaenScreen;
	}
	public void setRespawnScreen(boolean flag) {
		_respaenScreen = flag;
	}
	public int getMovementDistance() {
		return _movementDistance;
	}
	public void setMovementDistance(int i) {
		_movementDistance = i;
	}
	public int getAmount() {
		return _maximumCount;
	}
	public int getGroupId() {
		return _groupId;
	}
	public int getId() {
		return _id;
	}
	public String getLocation() {
		return _location;
	}
	public int getLocX() {
		return _locx;
	}
	public int getLocY() {
		return _locy;
	}
	public int getNpcId() {
		return _npcid;
	}
	public int getHeading() {
		return _heading;
	}
	public int getRandomx() {
		return _randomx;
	}
	public int getRandomy() {
		return _randomy;
	}
	public int getLocX1() {
		return _locx1;
	}
	public int getLocY1() {
		return _locy1;
	}
	public int getLocX2() {
		return _locx2;
	}
	public int getLocY2() {
		return _locy2;
	}
	public int getMinRespawnDelay() {
		return _minRespawnDelay;
	}
	public int getMaxRespawnDelay() {
		return _maxRespawnDelay;
	}
	public void setAmount(int amount) {
		_maximumCount = amount;
	}
	public void setId(int id) {
		_id = id;
	}
	public void setGroupId(int i) {
		_groupId = i;
	}
	public void setLocation(String location) {
		_location = location;
	}
	public void setLocX(int locx) {
		_locx = locx;
	}
	public void setLocY(int locy) {
		_locy = locy;
	}
	public void setNpcid(int npcid) {
		_npcid = npcid;
	}
	public void setHeading(int i) {
		if (i < 8 && i >= 0) {
			_heading = i;
		} else {
			_heading = 0;
		}		
	}
	public void setRandomx(int randomx) {
		_randomx = randomx;
	}
	public void setRandomy(int randomy) {
		_randomy = randomy;
	}
	public void setLocX1(int locx1) {
		_locx1 = locx1;
	}
	public void setLocY1(int locy1) {
		_locy1 = locy1;
	}
	public void setLocX2(int locx2) {
		_locx2 = locx2;
	}
	public void setLocY2(int locy2) {
		_locy2 = locy2;
	}
	public void setMinRespawnDelay(int i) {
		_minRespawnDelay = i;
	}
	public void setMaxRespawnDelay(int i) {
		_maxRespawnDelay = i;
	}

	private int calcRespawnDelay() {
		int respawnDelay = _minRespawnDelay * 1000;
		if (_delayInterval > 0) {
			respawnDelay += _random.nextInt(_delayInterval) * 1000;
		}
		return respawnDelay;
	}

	public void executeSpawnTask(int spawnNumber, int objectId) {
		SpawnTask task = new SpawnTask(spawnNumber, objectId);
		GeneralThreadPool.getInstance().schedule(task, calcRespawnDelay());
	}

	private boolean _initSpawn = false;

	private boolean _spawnHomePoint;

	public void init() {
		_delayInterval = _maxRespawnDelay - _minRespawnDelay;
		_initSpawn = true;
		if (Config.SPAWN_HOME_POINT && Config.SPAWN_HOME_POINT_COUNT <= getAmount() && Config.SPAWN_HOME_POINT_DELAY >= getMinRespawnDelay()
				&& isAreaSpawn()) {
			_spawnHomePoint = true;
			_homePoint = new HashMap<Integer, Point>();
		}

		int spawnNum = 0;
		while (spawnNum < _maximumCount) {
			doSpawn(++spawnNum);
		}
		_initSpawn = false;
	}

	protected void doSpawn(int spawnNumber) {
		doSpawn(spawnNumber, 0);
	}

	protected void doSpawn(int spawnNumber, int objectId) {
		L1NpcInstance mob = null;
		try {
			Object parameters[] = { _template };

			int newlocx = getLocX();
			int newlocy = getLocY();
			int tryCount = 0;

			mob = (L1NpcInstance) _constructor.newInstance(parameters);
			if (objectId == 0) {
				mob.setId(IdFactory.getInstance().nextId());
			} else {
				mob.setId(objectId);
			}

			if (0 <= getHeading() && getHeading() <= 7) {
				mob.setHeading(getHeading());
			} else {
				mob.setHeading(0);
			}

			mob.setMap(getMapId());
			mob.setMovementDistance(getMovementDistance());
			mob.setRest(isRest());
			ArrayList<L1PcInstance> players = null;
			L1PcInstance pc = null;
			L1Location loc = null;
			Point pt = null;
			while (tryCount <= 50) {
				switch (getSpawnType()) {
				case SPAWN_TYPE_PC_AROUND:
					if (!_initSpawn) {
						players = new ArrayList<L1PcInstance>();
						for (L1PcInstance _pc : L1World.getInstance().getAllPlayers()) {
							if (getMapId() == _pc.getMapId()) {
								players.add(_pc);
							}
						}
						// if (players.size() > 0) {
						if (!players.isEmpty()) {
							pc = players.get(_random.nextInt(players.size()));
							loc = pc.getLocation().randomLocation(PC_AROUND_DISTANCE, false);
							newlocx = loc.getX();
							newlocy = loc.getY();
							players.clear();
							break;
						}
					}
				default:
					if (isAreaSpawn()) {
						if (!_initSpawn && _spawnHomePoint) {
							pt = _homePoint.get(spawnNumber);
							loc = new L1Location(pt, getMapId()).randomLocation(Config.SPAWN_HOME_POINT_RANGE, false);
							if (loc == null) { // -- 로그 기록
								LoggerInstance.getInstance().addError("L1Spawn.java: loc = null  ' " + this.getNpcId()
										+ " ' / ' " + this.getName() + " ' ");
								break;
							}
							newlocx = loc.getX();
							newlocy = loc.getY();
						} else {
							int rangeX = getLocX2() - getLocX1();
							int rangeY = getLocY2() - getLocY1();
							newlocx = _random.nextInt(rangeX) + getLocX1();
							newlocy = _random.nextInt(rangeY) + getLocY1();
						}
						if (tryCount > 49) {
							newlocx = getLocX();
							newlocy = getLocY();
						}
					} else if (isRandomSpawn()) {
						newlocx = (getLocX() + ((int) (Math.random() * getRandomx()) - (int) (Math.random() * getRandomx())));
						newlocy = (getLocY() + ((int) (Math.random() * getRandomy()) - (int) (Math.random() * getRandomy())));
					} else {
						newlocx = getLocX();
						newlocy = getLocY();
					}
				}
				mob.setX(newlocx);
				mob.setHomeX(newlocx);
				mob.setY(newlocy);
				mob.setHomeY(newlocy);

				if (mob.getMap().isInMap(mob.getLocation()) && mob.getMap().isPassable(mob.getLocation())) {
					if (mob instanceof MonsterInstance) {
						if (isRespawnScreen()) {
							break;
						}
						MonsterInstance mobtemp = (MonsterInstance) mob;
						if (L1World.getInstance().getVisiblePlayer(mobtemp).size() == 0) {
							break;
						}
						SpawnTask task = new SpawnTask(spawnNumber, mob.getId());
						GeneralThreadPool.getInstance().schedule(task, 3000L);
						return;
					}
				}
				tryCount++;
			}
			if (mob instanceof MonsterInstance) {
				((MonsterInstance) mob).initHide();
			}

			mob.setSpawn(this);
			mob.setRespawn(true);
			mob.setSpawnNumber(spawnNumber);
			if (_initSpawn && _spawnHomePoint) {
				pt = new Point(mob.getX(), mob.getY());
				_homePoint.put(spawnNumber, pt);
			}

			L1World.getInstance().storeObject(mob);
			L1World.getInstance().addVisibleObject(mob);
			
			if (mob instanceof MonsterInstance) {
				MonsterInstance mobtemp = (MonsterInstance) mob;
				if (!_initSpawn && mobtemp.getHiddenStatus() == 0) {
					mobtemp.onNpcAI();
				}
			}
			if (getGroupId() != 0) {
				L1MobGroupSpawn.getInstance().doSpawn(mob, getGroupId(), isRespawnScreen(), _initSpawn);
			}
			mob.getLight().turnOnOffLight();
			mob.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE);
		} catch (Exception e) {
			System.out.println(String.format("스폰아이디 : %d, 엔피씨아이디 : %d", _id, _npcid));
			new Throwable().printStackTrace(); 
			System.out.print(String.format("spawn_number : %d, error_imple : %s, npcid : %d ", spawnNumber, _template.getImpl(), _template.get_npcId()));
		}
	}

	public void setRest(boolean flag) {
		_rest = flag;
	}

	public boolean isRest() {
		return _rest;
	}

	private static final int SPAWN_TYPE_PC_AROUND = 1;

	private static final int PC_AROUND_DISTANCE = 20;

	private int getSpawnType() {
		return _spawnType;
	}

	public void setSpawnType(int type) {
		_spawnType = type;
	}

	private boolean isAreaSpawn() {
		return getLocX1() != 0 && getLocY1() != 0 && getLocX2() != 0 && getLocY2() != 0;
	}

	private boolean isRandomSpawn() {
		return getRandomx() != 0 || getRandomy() != 0;
	}
}
