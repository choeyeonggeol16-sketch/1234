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

import l1j.server.server.datatables.CObjectDB;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.datatables.UBSpawnTable;
import l1j.server.server.datatables.UBTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.IntRange;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.L1GroundInventory;

// Referenced classes of package l1j.server.server.model:
// L1UltimateBattle

public class L1UltimateBattle {
	private int _locX;

	private int _locY;

	private L1Location _location;

	private short _mapId;

	private int _locX1;

	private int _locY1;

	private int _locX2;

	private int _locY2;

	private int _ubId;

	private int _pattern;

	private volatile boolean _isNowUb;

	private volatile boolean _active;

	private int _minLevel;

	private int _maxLevel;

	private int _maxPlayer;

	private boolean _enterRoyal;

	private boolean _enterKnight;

	private boolean _enterMage;

	private boolean _enterElf;

	private boolean _enterDarkelf;

	private boolean _enterDragonknight;

	private boolean _enterBlackwizard;

	private boolean _enter전사;

	private boolean _enterMale;

	private boolean _enterFemale;

	private boolean _usePot;

	private int _hpr;

	private int _mpr;

	// private static Random random = new Random(System.nanoTime());
	private static int BEFORE_MINUTE = 5;

	private CUltimateBattleTimer battleTimer;

	private Set<Integer> _managers = new HashSet<Integer>();

	private SortedSet<Integer> _ubTimes = new TreeSet<Integer>();

	private static final Logger _log = Logger.getLogger(L1UltimateBattle.class.getName());

	private static final L1PcInstance[] NO_MEMBERS = new L1PcInstance[0];
	private final CopyOnWriteArrayList<L1PcInstance> _members = new CopyOnWriteArrayList<>();

	private void sendRoundMessage(int curRound) {
		switch (_ubId) {
		case 1:
		case 2:
		case 3:
		case 5:
			if (curRound == 1) {
				sendMessage("콜롯세움 관리인: 제 1 군 투입!"); // 스타트
			} else if (curRound == 2) {
				sendMessage("콜롯세움 관리인: 제 2 군 투입!"); // 스타트
			} else if (curRound == 3) {
				sendMessage("콜롯세움 관리인: 제 3 군 투입!"); // 스타트
			} else if (curRound == 4) {
				sendMessage("콜롯세움 관리인: 최종전 개시! 제한 시간은 5분 입니다"); // 스타트
			}
			break;
		case 4:
			if (curRound == 1) {
				sendMessage("콜롯세움 관리인: 제 1 군 투입!"); // 스타트
			} else if (curRound == 2) {
				sendMessage("콜롯세움 관리인: 제 2 군 투입!"); // 스타트
			} else if (curRound == 3) {
				sendMessage("콜롯세움 관리인: 최종전 개시! 제한 시간은 5분 입니다"); // 스타트
			}
			break;
		}
	}

	private void spawnSupplies(int curRound) {//바닥아이템 드랍관련
		switch (_ubId) {
		case 1:
		case 2:
			if (curRound == 1) {
				spawnGroundItem(L1ItemId.ADENA, 1000, 60);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 3, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 5, 20);
				spawnGroundItem(L1ItemId.POTION_OF_GREATER_HEALING, 3, 20);
				spawnGroundItem(40317, 1, 5);
				spawnGroundItem(40079, 1, 10);
				//spawnGroundItem(3000156, 1, 5);
				sendMessage("콜롯세움 관리인: 제 1 군의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 1분 후에 제 2 군의 투입이 시작됩니다.");
			} else if (curRound == 2) {
				spawnGroundItem(L1ItemId.ADENA, 5000, 50);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 7, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 10, 20);
				spawnGroundItem(L1ItemId.POTION_OF_GREATER_HEALING, 5, 20);
				spawnGroundItem(40317, 1, 7);
				spawnGroundItem(40093, 1, 10);
				spawnGroundItem(40079, 1, 10);
				//spawnGroundItem(3000156, 1, 5);
				sendMessage("콜롯세움 관리인: 제 2 군의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 2분 후에 제 3 군의 투입이 시작됩니다.");
			} else if (curRound == 3) {
				spawnGroundItem(L1ItemId.ADENA, 10000, 30);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 7, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 20, 20);
				spawnGroundItem(L1ItemId.POTION_OF_GREATER_HEALING, 10, 10);
				spawnGroundItem(40317, 1, 10);
				spawnGroundItem(40094, 1, 10);
				spawnGroundItem(40079, 1, 10);
				//spawnGroundItem(3000156, 1, 5);
				sendMessage("콜롯세움 관리인: 제 3 군까지의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 6분 후에 최종전이 시작됩니다.");
			}
			break;
		case 3:
			if (curRound == 1) {
				spawnGroundItem(L1ItemId.ADENA, 1000, 60);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 3, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 5, 20);
				spawnGroundItem(L1ItemId.POTION_OF_GREATER_HEALING, 3, 20);
				spawnGroundItem(40317, 1, 5);
				spawnGroundItem(40079, 1, 10);
//				spawnGroundItem(3000156, 1, 10);
				sendMessage("콜롯세움 관리인: 제 1 군의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 1분 후에 제 2 군의 투입이 시작됩니다.");
			} else if (curRound == 2) {
				spawnGroundItem(L1ItemId.ADENA, 3000, 50);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 5, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 10, 20);
				spawnGroundItem(L1ItemId.POTION_OF_GREATER_HEALING, 5, 20);
				spawnGroundItem(40317, 1, 7);
				spawnGroundItem(40093, 1, 10);
//				spawnGroundItem(3000156, 1, 10);
				sendMessage("콜롯세움 관리인: 제 2 군의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 2분 후에 제 3 군의 투입이 시작됩니다.");
			} else if (curRound == 3) {
				spawnGroundItem(L1ItemId.ADENA, 5000, 30);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 10, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 15, 20);
				spawnGroundItem(L1ItemId.POTION_OF_GREATER_HEALING, 7, 10);
				spawnGroundItem(40317, 1, 10);
				spawnGroundItem(40094, 1, 10);
//				spawnGroundItem(3000156, 1, 10);
				sendMessage("콜롯세움 관리인: 제 3 군까지의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 6분 후에 최종전이 시작됩니다.");
			}
			break;
		case 4:
			if (curRound == 1) {
				spawnGroundItem(L1ItemId.ADENA, 200, 60);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 3, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 5, 20);
				spawnGroundItem(40317, 1, 5);
				spawnGroundItem(40079, 1, 10);
				//////spawnGroundItem(3000156, 1, 1);
//				spawnGroundItem(3000156, 1, 15);
				sendMessage("콜롯세움 관리인: 제 1 군의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 2분 후에 제 2 군의 투입이 시작됩니다.");
			} else if (curRound == 2) {
				spawnGroundItem(L1ItemId.ADENA, 500, 50);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 7, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 12, 20);
				spawnGroundItem(L1ItemId.POTION_OF_GREATER_HEALING, 5, 20);
				spawnGroundItem(40317, 1, 7);
				spawnGroundItem(40093, 1, 10);
//				spawnGroundItem(3000156, 1, 15);
				sendMessage("콜롯세움 관리인: 제 2 군까지의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 6분 후에 최종전이 시작됩니다.");
			}
			break;
		case 5:
			if (curRound == 1) {
				spawnGroundItem(L1ItemId.ADENA, 1000, 60);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 3, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 5, 20);
				spawnGroundItem(L1ItemId.POTION_OF_GREATER_HEALING, 3, 20);
				spawnGroundItem(40317, 1, 5);
				spawnGroundItem(40079, 1, 10);
//				spawnGroundItem(3000156, 1, 20);
				sendMessage("콜롯세움 관리인: 제 1 군의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 1분 후에 제 2 군의 투입이 시작됩니다.");
			} else if (curRound == 2) {
				spawnGroundItem(L1ItemId.ADENA, 5000, 50);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 7, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 10, 20);
				spawnGroundItem(L1ItemId.POTION_OF_GREATER_HEALING, 5, 20);
				spawnGroundItem(40317, 1, 7);
				spawnGroundItem(40093, 1, 10);
				spawnGroundItem(40079, 1, 10);
//				spawnGroundItem(3000156, 1, 20);
				sendMessage("콜롯세움 관리인: 제 2 군의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 2분 후에 제 3 군의 투입이 시작됩니다.");
			} else if (curRound == 3) {
				spawnGroundItem(L1ItemId.ADENA, 10000, 30);
				spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 7, 20);
				spawnGroundItem(L1ItemId.POTION_OF_EXTRA_HEALING, 20, 20);
				spawnGroundItem(L1ItemId.POTION_OF_GREATER_HEALING, 10, 10);
				spawnGroundItem(40317, 1, 10);
				spawnGroundItem(40094, 1, 10);
				spawnGroundItem(40079, 1, 10);
//				spawnGroundItem(3000156, 1, 20);
				sendMessage("콜롯세움 관리인: 제 3 군까지의 투입이 완료되었습니다.");
				sendMessage("콜롯세움 관리인: 6분 후에 최종전이 시작됩니다.");
			}
			break;
		}
	}

	private void removeRetiredMembers() {
		L1PcInstance[] temp = getMembersArray();
		for (int i = 0; i < temp.length; i++) {
			if (temp[i].getMapId() != _mapId) {
				removeMember(temp[i]);
			}
		}
	}

	private void sendMessage(String msg) {
		try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
       SC_SystemMessage.nativeWriter(msg),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc : getMembersArray()) {
			l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc);
		}
}
	}

	private void spawnGroundItem(int itemId, int stackCount, int count) {
		L1Item temp = CObjectDB.GetItem(itemId);
		if (temp == null) {
			return;
		}
		L1Location loc = null;
		L1ItemInstance item = null;
		L1GroundInventory ground = null;
		for (int i = 0; i < count; i++) {
			loc = _location
					.randomLocation((getLocX2() - getLocX1()) / 2, false);
			if (temp.isStackable()) {
				item = CObjectDB.CreateItem(itemId);
				item.setEnchantLevel(0);
				item.setCount(stackCount);
				ground = L1World.getInstance().getInventory(loc.getX(),
						loc.getY(), _mapId);
				try {
					if (ground.CheckAddable(item, stackCount) == CInventory.OK) {
						ground.CreateOrAddItem(item);
					}
				} finally {
					// A failed ground insert or stack merge cannot retain a
					// world-registered, unowned temporary item.
					if (ground.GetByServerID(item.getId()) != item) {
						L1World.getInstance().removeObject(item);
					}
				}
			} else {
				item = null;
				for (int createCount = 0; createCount < stackCount; createCount++) {
					item = CObjectDB.CreateItem(itemId);
					item.setEnchantLevel(0);
					ground = L1World.getInstance().getInventory(loc.getX(),
							loc.getY(), _mapId);
					try {
						if (ground.CheckAddable(item, stackCount) == CInventory.OK) {
							ground.CreateOrAddItem(item);
						}
					} finally {
						// A failed ground insert or stack merge cannot retain a
						// world-registered, unowned temporary item.
						if (ground.GetByServerID(item.getId()) != item) {
							L1World.getInstance().removeObject(item);
						}
					}
				}
			}
		}
	}

	private void clearColosseum() {
		L1MonsterInstance mob = null;
		CInventory inventory = null;
		for (Object obj : L1World.getInstance().getVisibleObjects(_mapId)
				.values()) {
			if (obj instanceof L1MonsterInstance) {
				mob = (L1MonsterInstance) obj;
				if (!mob.isDead()) {
					mob.setDead(true);
					mob.setStatus(ActionCodes.ACTION_Die);
					mob.setCurrentHp(0);
					mob.deleteMe();

				}
			} else if (obj instanceof CInventory) {
				inventory = (CInventory) obj;
				inventory.clearItems();
			}
		}
	}

	public L1UltimateBattle() {
	}

    private enum Phase { OPEN, WARNING, COUNTDOWN, FIRST_ROUND, SPAWN, NEXT_ROUND }

    private static final int[] ROUND_WAIT_SECONDS = {70, 120, 360, 180};
    private static final int[] UB4_WAIT_SECONDS = {120, 360, 180};

    private final class CUltimateBattleTimer implements Runnable {
        private final int[] roundWaits = _ubId == 4 ? UB4_WAIT_SECONDS : ROUND_WAIT_SECONDS;
        private Phase phase = Phase.OPEN;
        private int countdown = 5;
        private int round = 1;
        private int spawnIndex;
        private ArrayList<L1UbSpawn> spawns;
        private CServerTimer deadline;

        private void AddTimer(long delayMs) {
            deadline = CServerTimer.once(this, delayMs);
            if (deadline == null) {
                stop();
                _log.warning("Ultimate battle timer admission rejected for arena " + _ubId);
            }
        }

        @Override
        public void run() {
            synchronized (L1UltimateBattle.this) {
                if (battleTimer != this) return;
                try {
                    TimerExpired();
                } catch (RuntimeException failure) {
                    stop();
                    _log.log(Level.WARNING, "Ultimate battle failed for arena " + _ubId, failure);
                }
            }
        }

        private void TimerExpired() {
            switch (phase) {
                case OPEN -> {
                    removeRetiredMembers();
                    sendMessage("콜롯세움 관리인: 이제 곧 몬스터들이 등장할 것입니다. 건투를 빕니다.");
                    phase = Phase.WARNING;
                    AddTimer(5_000L);
                }
                case WARNING -> {
                    sendMessage("콜롯세움 관리인: 10초뒤에 경기를 시작 합니다.");
                    phase = Phase.COUNTDOWN;
                    AddTimer(5_000L);
                }
                case COUNTDOWN -> {
                    sendMessage("콜롯세움 관리인: " + countdown-- + " !!");
                    if (countdown == 0) phase = Phase.FIRST_ROUND;
                    AddTimer(1_000L);
                }
                case FIRST_ROUND -> {
                    removeRetiredMembers();
                    setNowUb(true);
                    startRound();
                }
                case SPAWN -> spawnNext();
                case NEXT_ROUND -> {
                    removeRetiredMembers();
                    if (++round <= roundWaits.length) startRound();
                    else finish();
                }
            }
        }

        private void startRound() {
            sendRoundMessage(round);
            spawns = UBSpawnTable.getInstance().getPattern(_ubId, _pattern).getSpawnList(round);
            spawnIndex = 0;
            phase = Phase.SPAWN;
            spawnNext();
        }

        private void spawnNext() {
            while (spawnIndex < spawns.size()) {
                final L1UbSpawn spawn = spawns.get(spawnIndex++);
                if (getMembersCount() > 0) spawn.spawnAll();
                final long delay = spawn.getSpawnDelay() * 1_000L;
                if (delay > 0L) {
                    AddTimer(delay);
                    return;
                }
            }
            spawns = null;
            if (getMembersCount() > 0) spawnSupplies(round);
            for (L1PcInstance pc : getMembersArray()) {
                UBTable.getInstance().writeUbScore(getUbId(), pc);
            }
            phase = Phase.NEXT_ROUND;
            AddTimer(roundWaits[round - 1] * 1_000L);
        }

        private void finish() {
            for (L1PcInstance pc : getMembersArray()) {
                final int[] loc = Getback.GetBack_Location(pc, true);
                pc.start_teleport(loc[0], loc[1], loc[2], 5, 169, true, false);
                removeMember(pc);
            }
            clearColosseum();
            stop();
        }
    }

    public synchronized void start() {
        if (battleTimer != null) return;
        if (getUbId() == 1) {
            L1World.getInstance().broadcastServerMessage(
                    "5분 후 기란 마을의 무한대전이 시작됩니다. 지금부터 입장가능합니다.");
        }
        _pattern = 1;
        setActive(true);
        battleTimer = new CUltimateBattleTimer();
        battleTimer.AddTimer((BEFORE_MINUTE * 60L - 15L) * 1_000L);
    }

    public synchronized void stop() {
        final CUltimateBattleTimer current = battleTimer;
        battleTimer = null;
        if (current != null && current.deadline != null) current.deadline.cancel();
        setActive(false);
        setNowUb(false);
    }

	public void addMember(L1PcInstance pc) {
		_members.addIfAbsent(pc);
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
		return _members.toArray(NO_MEMBERS);
	}

	public int getMembersCount() {
		return _members.size();
	}

	private void setNowUb(boolean i) {
		_isNowUb = i;
	}

	public boolean isNowUb() {
		return _isNowUb;
	}

	public int getUbId() {
		return _ubId;
	}

	public void setUbId(int id) {
		_ubId = id;
	}

	public short getMapId() {
		return _mapId;
	}

	public void setMapId(short mapId) {
		this._mapId = mapId;
	}

	public int getMinLevel() {
		return _minLevel;
	}

	public void setMinLevel(int level) {
		_minLevel = level;
	}

	public int getMaxLevel() {
		return _maxLevel;
	}

	public void setMaxLevel(int level) {
		_maxLevel = level;
	}

	public int getMaxPlayer() {
		return _maxPlayer;
	}

	public void setMaxPlayer(int count) {
		_maxPlayer = count;
	}

	public void setEnterRoyal(boolean enterRoyal) {
		this._enterRoyal = enterRoyal;
	}

	public void setEnterKnight(boolean enterKnight) {
		this._enterKnight = enterKnight;
	}

	public void setEnterMage(boolean enterMage) {
		this._enterMage = enterMage;
	}

	public void setEnterElf(boolean enterElf) {
		this._enterElf = enterElf;
	}

	public void setEnterDarkelf(boolean enterDarkelf) {
		this._enterDarkelf = enterDarkelf;
	}

	public void setEnterDragonknight(boolean enterDragonknight) {
		this._enterDragonknight = enterDragonknight;
	}

	public void setEnterBlackwizard(boolean enterBlackwizard) {
		this._enterBlackwizard = enterBlackwizard;
	}

	public void setEnter전사(boolean enter전사) {
		this._enter전사 = enter전사;
	}

	public void setEnterMale(boolean enterMale) {
		this._enterMale = enterMale;
	}

	public void setEnterFemale(boolean enterFemale) {
		this._enterFemale = enterFemale;
	}

	public boolean canUsePot() {
		return _usePot;
	}

	public void setUsePot(boolean usePot) {
		this._usePot = usePot;
	}

	public int getHpr() {
		return _hpr;
	}

	public void setHpr(int hpr) {
		this._hpr = hpr;
	}

	public int getMpr() {
		return _mpr;
	}

	public void setMpr(int mpr) {
		this._mpr = mpr;
	}

	public int getLocX1() {
		return _locX1;
	}

	public void setLocX1(int locX1) {
		this._locX1 = locX1;
	}

	public int getLocY1() {
		return _locY1;
	}

	public void setLocY1(int locY1) {
		this._locY1 = locY1;
	}

	public int getLocX2() {
		return _locX2;
	}

	public void setLocX2(int locX2) {
		this._locX2 = locX2;
	}

	public int getLocY2() {
		return _locY2;
	}

	public void setLocY2(int locY2) {
		this._locY2 = locY2;
	}

	public void resetLoc() {
		_locX = (_locX2 + _locX1) / 2;
		_locY = (_locY2 + _locY1) / 2;
		_location = new L1Location(_locX, _locY, _mapId);
	}

	public L1Location getLocation() {
		return _location;
	}

	public void addManager(int npcId) {
		_managers.add(npcId);
	}

	public boolean containsManager(int npcId) {
		return _managers.contains(npcId);
	}

	public void addUbTime(int time) {
		_ubTimes.add(time);
	}

	public String getNextUbTime() {
		return intToTimeFormat(nextUbTime());
	}

	private int nextUbTime() {
		SimpleDateFormat sdf = new SimpleDateFormat("HHmm");
		int nowTime = Integer.valueOf(sdf.format(getRealTime().getTime()));
		SortedSet<Integer> tailSet = _ubTimes.tailSet(nowTime);
		if (tailSet.isEmpty()) {
			tailSet = _ubTimes;
		}
		return tailSet.first();
	}

	private static String intToTimeFormat(int n) {
		return n / 100 + ":" + n % 100 / 10 + "" + n % 10;
	}

	private static Calendar getRealTime() {
		TimeZone _tz = TimeZone.getTimeZone(Config.TIME_ZONE);
		Calendar cal = Calendar.getInstance(_tz);
		return cal;
	}

	public boolean checkUbTime() {
		SimpleDateFormat sdf = new SimpleDateFormat("HHmm");
		Calendar realTime = getRealTime();
		realTime.add(Calendar.MINUTE, BEFORE_MINUTE);
		int nowTime = Integer.valueOf(sdf.format(realTime.getTime()));
		return _ubTimes.contains(nowTime);
	}

	private void setActive(boolean f) {
		_active = f;
	}

	public boolean isActive() {
		return _active;
	}

	public boolean canPcEnter(L1PcInstance pc) {
		_log.log(Level.FINE, "pcname=" + pc.getName() + " ubid=" + _ubId
				+ " minlvl=" + _minLevel + " maxlvl=" + _maxLevel);
		if (!IntRange.includes(pc.getLevel(), _minLevel, _maxLevel)) {
			return false;
		}

		if (!((pc.isCrown() && _enterRoyal) 
	|| (pc.isKnight() && _enterKnight)
	|| (pc.isWizard() && _enterMage)
	|| (pc.isElf() && _enterElf)
	|| (pc.isDarkelf() && _enterDarkelf)
	|| (pc.isDragonknight() && _enterDragonknight) 
	|| (pc.isBlackwizard() && _enterBlackwizard)
	|| (pc.isWarrior() && _enter전사))) {
			return false;
		}

		return true;
	}

	private String[] _ubInfo;

	public String[] makeUbInfoStrings() {
		if (_ubInfo != null) {
			return _ubInfo;
		}
		String nextUbTime = getNextUbTime();
		StringBuilder classesBuff = new StringBuilder();
		if (_enterBlackwizard) {
			classesBuff.append("환술사 ");
		}
		if (_enterDragonknight) {
			classesBuff.append("용기사 ");
		}
		if (_enterDarkelf) {
			classesBuff.append("다크엘프 ");
		}
		if (_enterMage) {
			classesBuff.append("마법사 ");
		}
		if (_enterElf) {
			classesBuff.append("요정 ");
		}
		if (_enterKnight) {
			classesBuff.append("기사 ");
		}
		if (_enterRoyal) {
			classesBuff.append("군주 ");
		}
		if (_enter전사) {
			classesBuff.append("전사 ");
		}
		String classes = classesBuff.toString().trim();

		StringBuilder sexBuff = new StringBuilder();
		if (_enterMale) {
			sexBuff.append("남자 ");
		}
		if (_enterFemale) {
			sexBuff.append("여자 ");
		}
		String sex = sexBuff.toString().trim();
		String loLevel = String.valueOf(_minLevel);
		String hiLevel = String.valueOf(_maxLevel);
		String teleport = _location.getMap().isEscapable() ? "가능" : "불가능";
		String res = _location.getMap().isUseResurrection() ? "가능" : "불가능";
		String pot = "가능";
		String hpr = String.valueOf(_hpr);
		String mpr = String.valueOf(_mpr);
		String summon = _location.getMap().isTakePets() ? "가능" : "불가능";
		String summon2 = _location.getMap().isRecallPets() ? "가능" : "불가능";
		_ubInfo = new String[] { nextUbTime, classes, sex, loLevel, hiLevel,
				teleport, res, pot, hpr, mpr, summon, summon2 };
		return _ubInfo;
	}
}
