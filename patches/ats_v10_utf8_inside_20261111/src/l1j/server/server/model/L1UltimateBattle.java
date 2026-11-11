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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Templates.L1Item;
import l1j.server.configure.Config;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.UBSpawnTable;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.utils.IntRange;

// Referenced classes of package l1j.server.server.model:
// L1UltimateBattle

public class L1UltimateBattle {
	private int _locX;
	private int _locY;
	private L1Location _location; // 중심점
	private short _mapId;
	private int _locX1;
	private int _locY1;
	private int _locX2;
	private int _locY2;

	private int _ubId;
	private int _pattern;
	private boolean _isNowUb;
	private boolean _active; // UB입장 가능~경기 종료까지 true

	private int _minLevel;
	private int _maxLevel;
	private int _maxPlayer;

	private boolean _enterRoyal;
	private boolean _enterKnight;
	private boolean _enterMage;
	private boolean _enterElf;
	private boolean _enterDarkelf;
	private boolean _enterMale;
	private boolean _enterFemale;
	private boolean _usePot;
	private int _hpr;
	private int _mpr;

	private static int BEFORE_MINUTE = 5; // 5분전으로부터 입장 개시

	private Set<Integer> _managers = new HashSet<Integer>();
	private SortedSet<Integer> _ubTimes = new TreeSet<Integer>();

	private static final Logger _log = Logger.getLogger(L1UltimateBattle.class
			.getName());

	private final ArrayList<L1PcInstance> _members = new ArrayList<L1PcInstance>();

	/**
	 * 라운드 개시시의 메세지를 송신한다.
	 * 
	 * @param curRound
	 *            개시하는 라운드
	 */
	private void sendRoundMessage(int curRound) {
		// XXX - 이 ID는 잘못되어 있다
		final int MSGID_ROUND_TABLE[] = { 893, 894, 895, 896 };

		sendMessage(MSGID_ROUND_TABLE[curRound - 1], "");
	}

	/**
	 * 일부등의 보급 아이템을 출현시킨다.
	 * 
	 * @param curRound
	 *            현재의 라운드
	 */
	private void spawnSupplies(int curRound) {
		if (curRound == 1) {
			spawnGroundItem(ItemId.아데나, 30000, 60);
	//		spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 3, 20);
			spawnGroundItem(ItemId.고급_체력_회복제, 5, 20);
			spawnGroundItem(ItemId.강력_체력_회복제, 3, 20);
			spawnGroundItem(40317, 1, 5); // 숫돌
			spawnGroundItem(42079, 1, 20); // 귀환 스크
			spawnGroundItem(40727, 1, 20); // 녹색 양말
		} else if (curRound == 2) {
			spawnGroundItem(ItemId.아데나, 50000, 50);
	//		spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 5, 20);
			spawnGroundItem(ItemId.고급_체력_회복제, 10, 20);
			spawnGroundItem(ItemId.강력_체력_회복제, 5, 20);
			spawnGroundItem(40317, 1, 7); // 숫돌
			spawnGroundItem(40093, 1, 10); // 브란크스크(Lv4)
			spawnGroundItem(42079, 1, 5); // 귀환 스크
			spawnGroundItem(40727, 2, 20); // 녹색 양말
		} else if (curRound == 3) {
			spawnGroundItem(ItemId.아데나, 100000, 30);
	//		spawnGroundItem(L1ItemId.POTION_OF_CURE_POISON, 7, 20);
			spawnGroundItem(ItemId.고급_체력_회복제, 20, 20);
			spawnGroundItem(ItemId.강력_체력_회복제, 10, 20);
			spawnGroundItem(40317, 1, 10); // 숫돌
			spawnGroundItem(40727, 3, 20); // 녹색 양말
		}
	}

	/**
	 * 콜롯세움으로부터 나온 멤버를 멤버 리스트로부터 삭제한다.
	 */
	private void removeRetiredMembers() {
		L1PcInstance[] temp = getMembersArray();
		for (int i = 0; i < temp.length; i++) {
			if (temp[i].getMapId() != _mapId) {
				removeMember(temp[i]);
			}
		}
	}

	/**
	 * UB에 참가하고 있는 플레이어에 메세지(S_ServerMessage)를 송신한다.
	 * 
	 * @param type
	 *            메세지 타입
	 * @param msg
	 *            송신하는 메세지
	 */
	private void sendMessage(int type, String msg) {
		for (L1PcInstance pc : getMembersArray()) {
			pc.sendPackets(new S_ServerMessage(type, msg));
		}
	}

	/**
	 * 콜롯세움상에 아이템을 출현시킨다.
	 * 
	 * @param itemId
	 *            출현시키는 아이템의 아이템 ID
	 * @param stackCount
	 *            아이템의 스택수
	 * @param count
	 *            출현시키는 수
	 */
	private void spawnGroundItem(int itemId, int stackCount, int count) {
		L1Item temp = ItemTable.getInstance().getTemplate(itemId);
		if (temp == null) {
			return;
		}

		for (int i = 0; i < count; i++) {
			L1Location loc = _location.randomLocation(
					(getLocX2() - getLocX1()) / 2, false);
			if (temp.isStackable()) {
				ItemInstance item = ItemTable.getInstance()
						.createItem(itemId);
				item.setEnchantLevel(0);
				item.setCount(stackCount);
				L1GroundInventory ground = L1World.getInstance().getInventory(
						loc.getX(), loc.getY(), _mapId);
				if (ground.checkAddItem(item, stackCount) == L1Inventory.OK) {
					ground.storeItem(item);
				}
			} else {
				ItemInstance item = null;
				for (int createCount = 0; createCount < stackCount; createCount++) {
					item = ItemTable.getInstance().createItem(itemId);
					item.setEnchantLevel(0);
					L1GroundInventory ground = L1World.getInstance()
							.getInventory(loc.getX(), loc.getY(), _mapId);
					if (ground.checkAddItem(item, stackCount) == L1Inventory.OK) {
						ground.storeItem(item);
					}
				}
			}
		}
	}

	/**
	 * 콜롯세움상의 아이템과 monster를 모두 삭제한다.
	 */
	private void clearColosseum() {
		for (Object obj : L1World.getInstance().getVisibleObjects(_mapId)
				.values()) {
			if (obj instanceof MonsterInstance) // monster 삭제
			{
				MonsterInstance mob = (MonsterInstance) obj;
				if (!mob.isDead()) {
					mob.setDead(true);
					mob.setCurrentHpDirect(0);
					mob.deleteMe();

				}
			} else if (obj instanceof L1Inventory) // 아이템 삭제
			{
				L1Inventory inventory = (L1Inventory) obj;
				inventory.clearItems();
			}
		}
	}

	/**
	 * constructor　 　.
	 */
	public L1UltimateBattle() {
	}

	class UbThread implements Runnable {
		/**
		 * 경기 개시까지를 카운트다운 한다.
		 * 
		 * @throws InterruptedException
		 */
		private void countDown() throws InterruptedException {
			// XXX - 이 ID는 잘못되어 있다
			final int MSGID_COUNT = 637;
			final int MSGID_START = 632;

			for (int loop = 0; loop < BEFORE_MINUTE * 60 - 10; loop++) { // 개시 10초전까지 기다린다
				Thread.sleep(1000);
// removeRetiredMembers();
			}
			removeRetiredMembers();

			sendMessage(MSGID_COUNT, "10"); // 10초전

			Thread.sleep(5000);
			sendMessage(MSGID_COUNT, "5"); // 5초전

			Thread.sleep(1000);
			sendMessage(MSGID_COUNT, "4"); // 4초전

			Thread.sleep(1000);
			sendMessage(MSGID_COUNT, "3"); // 3초전

			Thread.sleep(1000);
			sendMessage(MSGID_COUNT, "2"); // 2초전

			Thread.sleep(1000);
			sendMessage(MSGID_COUNT, "1"); // 1초전

			Thread.sleep(1000);
			sendMessage(MSGID_START, "무한대전 몬스터"); // 스타트
			removeRetiredMembers();
		}

		/**
		 * 모든 monster가 출현한 후, 다음의 라운드가 시작될 때까지의 시간을 대기한다.
		 * 
		 * @param curRound
		 *            현재의 라운드
		 * @throws InterruptedException
		 */
		private void waitForNextRound(int curRound) throws InterruptedException {
			final int WAIT_TIME_TABLE[] = { 6, 6, 2, 18 };

			int wait = WAIT_TIME_TABLE[curRound - 1];
			for (int i = 0; i < wait; i++) {
				Thread.sleep(10000);
// removeRetiredMembers();
			}
			removeRetiredMembers();
		}

		/**
		 * thread 프로시저.
		 */
		@Override
		public void run() {
			try {
				setActive(true);
				countDown();
				setNowUb(true);
				for (int round = 1; round <= 4; round++) {
					sendRoundMessage(round);

					L1UbPattern pattern = UBSpawnTable.getInstance()
							.getPattern(_ubId, _pattern);

					ArrayList<L1UbSpawn> spawnList = pattern
							.getSpawnList(round);

					for (L1UbSpawn spawn : spawnList) {
						if (getMembersCount() > 0) {
							spawn.spawnAll();
						}

						Thread.sleep(spawn.getSpawnDelay() * 1000);
// removeRetiredMembers();
					}

					if (getMembersCount() > 0) {
						spawnSupplies(round);
					}

					waitForNextRound(round);
				}

				for (L1PcInstance pc : getMembersArray()) // 콜롯세움내에 있는 PC를 밖에 낸다
				{
					Random random = new Random();
					int rndx = random.nextInt(4);
					int rndy = random.nextInt(4);
					int locx = 33503 + rndx;
					int locy = 32764 + rndy;
					short mapid = 4;
					pc.teleport(locx, locy, mapid, pc.getHeading(), 169, true, false); 
					removeMember(pc);
				}
				clearColosseum();
				setActive(false);
				setNowUb(false);
			} catch (Exception e) {
				_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
		}
	}

	/**
	 * 얼티메이트 배틀을 개시한다.
	 * 
	 * @param ubId
	 *            개시하는 얼티메이트 배틀의 ID
	 */
	public void start() {
		int patternsMax = UBSpawnTable.getInstance().getMaxPattern(_ubId);
		Random random = new Random();
		_pattern = random.nextInt(patternsMax) + 1; // 출현 패턴을 결정한다

		UbThread ub = new UbThread();
		GeneralThreadPool.getInstance().execute(ub);
	}

	/**
	 * 플레이어를 참가 멤버 리스트에 추가한다.
	 * 
	 * @param pc
	 *            새롭게 참가하는 플레이어
	 */
	public void addMember(L1PcInstance pc) {
		if (!_members.contains(pc)) {
			_members.add(pc);
		}
	}

	/**
	 * 플레이어를 참가 멤버 리스트로부터 삭제한다.
	 * 
	 * @param pc
	 *            삭제하는 플레이어
	 */
	public void removeMember(L1PcInstance pc) {
		_members.remove(pc);
	}

	/**
	 * 참가 멤버 리스트를 클리어 한다.
	 */
	public void clearMembers() {
		_members.clear();
	}

	/**
	 * 플레이어가, 참가 멤버인지를 돌려준다.
	 * 
	 * @param pc
	 *            조사하는 플레이어
	 * @return 참가 멤버이면 true, 그렇지 않으면 false.
	 */
	public boolean isMember(L1PcInstance pc) {
		return _members.contains(pc);
	}

	/**
	 * 참가 멤버의 배열을 작성해, 돌려준다.
	 * 
	 * @return 참가 멤버의 배열
	 */
	public L1PcInstance[] getMembersArray() {
		return _members.toArray(new L1PcInstance[_members.size()]);
	}

	/**
	 * 참가 멤버수를 돌려준다.
	 * 
	 * @return 참가 멤버수
	 */
	public int getMembersCount() {
		return _members.size();
	}

	/**
	 * UB중인지를 설정한다.
	 * 
	 * @param i
	 *            true/false
	 */
	private void setNowUb(boolean i) {
		_isNowUb = i;
	}

	/**
	 * UB중인지를 돌려준다.
	 * 
	 * @return UB중이면 true, 그렇지 않으면 false.
	 */
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

	// set 된 locx1~locy2로부터 중심점을 요구한다.
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

	/**
	 * @return UB입장 가능~경기 종료까지는 true, 그 이외는 false를 돌려준다.
	 */
	public boolean isActive() {
		return _active;
	}

	/**
	 * UB에 참가 가능한가, 레벨, 클래스를 체크한다.
	 * 
	 * @param pc
	 *            UB에 참가할 수 있는지 체크하는 PC
	 * @return 참가 할 수 있는 경우는 true, 할 수 없는 경우는 false
	 */
	public boolean canPcEnter(L1PcInstance pc) {
		_log.log(Level.FINE, "pcname=" + pc.getName() + " ubid=" + _ubId
				+ " minlvl=" + _minLevel + " maxlvl=" + _maxLevel);
		// 참가 가능한 레벨인가
		if (!IntRange.includes(pc.getLevel(), _minLevel, _maxLevel)) {
			return false;
		}

		// 참가 가능한 클래스인가
		if (!((pc.isCrown() && _enterRoyal) || (pc.isKnight() && _enterKnight)
				|| (pc.isWizard() && _enterMage) || (pc.isElf() && _enterElf) || (pc
				.isDarkelf() && _enterDarkelf))) {
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
		// 클래스
		StringBuilder classesBuff = new StringBuilder();
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
		String classes = classesBuff.toString().trim();
		// 성별
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
