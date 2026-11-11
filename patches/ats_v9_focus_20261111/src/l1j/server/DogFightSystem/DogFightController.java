package l1j.server.DogFightSystem;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javolution.util.FastTable;
import l1j.server.L1DatabaseFactory;
import l1j.server.ActionCodes.EActionCodes;
import l1j.server.DogFightSystem.DogFightHistory.DogFight;
import l1j.server.Templates.L1ShopItem;
import l1j.server.server.ActionCodes;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.shop.Shop;
import l1j.server.server.serverpackets.S_AttackPacket;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_NpcChatPacket;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;

public class DogFightController implements Runnable {
	private static DogFightController _instance;
	public static final int EXECUTE_STATUS_NONE = 0;
	public static final int EXECUTE_STATUS_PREPARE = 1;
	public static final int EXECUTE_STATUS_READY = 2;
	public static final int EXECUTE_STATUS_STANDBY = 3;
	public static final int EXECUTE_STATUS_PROGRESS = 4;
	public static final int EXECUTE_STATUS_FINALIZE = 5;

	public static boolean racing_im = false;
	
	private int _executeStatus = EXECUTE_STATUS_NONE;

	public int _FightCount = 0;
	public int _dogfightState = 2;

	public int _ticketSellRemainTime;
	public int _FightWatingTime;
	public int _currentBroadcastFighter;
	public int _watingtime;
	public int _winner = -1;

	L1NpcInstance[] _npc = new L1NpcInstance[3];

	public int[] _ticketCount = new int[3];
	public int[] _ticketId = new int[3];
	private static DecimalFormat _df = new DecimalFormat("#.#");

	public boolean _complete = false;

	List<L1ShopItem> _purchasingList = new ArrayList<L1ShopItem>();
	public DogFightInstance[] _dogfight = new DogFightInstance[2];

	/** 투견 추가 **/
	private final HashMap<Integer, DogFightTicket> _ticket_info = new HashMap<Integer, DogFightTicket>();
	
	public HashMap<Integer, DogFightTicket> getAllTemplates() {
		return _ticket_info;
	}

	public int[] Number = { 1, 2, 3 }; //홀짝 
	
	// 티켓 초기화
	public int[] ticket = { 0, 0, 0 };
	// 승률 초기화
	public double[] _winRate = { 0, 0 };
	// 상태 초기화
	public String[] _FightCondition = { "좋음", "좋음" };

	public static DogFightController getInstance() {
		if (_instance == null) {
			_instance = new DogFightController();
		}
		return _instance;
	}
	
	
	public void run() {
		try {
			switch (_executeStatus) {
			case EXECUTE_STATUS_NONE: {
				if (checkStartFight()) {
					initFightGame();
					_executeStatus = EXECUTE_STATUS_PREPARE;
					GeneralThreadPool.getInstance().schedule(this, 10 * 1000L);
				} else {
					GeneralThreadPool.getInstance().schedule(this, 1000L); // 1초
				}
			}
				break;
			case EXECUTE_STATUS_PREPARE: {
				startSellTicket();
				_executeStatus = EXECUTE_STATUS_READY;
				GeneralThreadPool.getInstance().schedule(this, 1000L);
			}
				break;
			case EXECUTE_STATUS_READY: {
				long remainTime = checkTicketSellTime();
				if (remainTime > 0) {
					GeneralThreadPool.getInstance().schedule(this, remainTime);
				} else {
					_executeStatus = EXECUTE_STATUS_STANDBY;
					GeneralThreadPool.getInstance().schedule(this, 1000L);
				}
			}
				break;
			case EXECUTE_STATUS_STANDBY: {
				if (checkWatingTime()) {
					initShopNpc();
					startFight();
					_executeStatus = EXECUTE_STATUS_PROGRESS;
				}
				GeneralThreadPool.getInstance().schedule(this, 1000L);
			}
				break;

			case EXECUTE_STATUS_PROGRESS: {
				long time = 1000L;
				if (broadcastBettingRate()) {
					if (_winner == 2) {
						_complete = true;
						time = 7000L;
					}
					
					if (_dogfight[0] != null && _dogfight[1] != null && (_winner == 0 || _winner == 1)) {
						Broadcaster.broadcastPacket(_dogfight[_winner], S_SkillSound.get(_dogfight[_winner].getId(), _dogfight[_winner].getWinEffect()));
						Broadcaster.broadcastPacket(_dogfight[_winner], S_DoActionGFX.get(_dogfight[_winner].getId(), ActionCodes.ACTION_Aggress));
						_complete = true;
						time = 7000L;
					}
					
					if (_complete) {
						if (_winner != 2) {
							goalIn(_winner);
							WinnerMent("제 " + _FightCount + " 회 승자는 '" + _dogfight[_winner].getNameId() + "' 입니다.");
						} else {
							drawIn();
							WinnerMent("제 " + _FightCount + " 회는 무승부 입니다.");
						}
						
						_executeStatus = EXECUTE_STATUS_FINALIZE;
					}
					
					if ((_dogfight[0].isDead() || _dogfight[1].isDead()) && !_complete) {
						if (_dogfight[1].getCurrentHp() == 0 && _dogfight[0].getCurrentHp() > 0) {
							_winner = 0;
							_complete = true;
						} else if (_dogfight[0].getCurrentHp() == 0 && _dogfight[1].getCurrentHp() > 0) {
							_winner = 1;
							_complete = true;
						} else if (_dogfight[0].getCurrentHp() == 0 && _dogfight[1].getCurrentHp() == 0) {
							_winner = 2;
							_complete = true;
						}
					}
				}
				GeneralThreadPool.getInstance().schedule(this, time);
			}
				break;
			case EXECUTE_STATUS_FINALIZE: {
				for (int i = 0; i < 2; i++) {
					cleardog(i);
				}
				if (racing_im) {
					_instance = null;
					racing_im = false;
					return;
				}
				_FightCount = _FightCount + 1;
				setdogState(2);
				broadcastNpc("다음경기를 준비하는데 " + 1 + "분이 소요됩니다");
				_executeStatus = EXECUTE_STATUS_NONE;
				GeneralThreadPool.getInstance().schedule(this, 1000L);
			}
				break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			broad_cast_betting_rate();
		}
	}
	
	private void condition() {
		Random _rnd = new Random(System.nanoTime());
		for (int i = 0; i < 2; i++) {
			int dogState = _rnd.nextInt(3);

			switch (dogState) {
			case 0:
				_FightCondition[i] = "좋음";
				break;
			case 1:
				_FightCondition[i] = "보통";
				break;
			case 2:
				_FightCondition[i] = "나쁨";
				break;
			default:
				_FightCondition[i] = "보통";
				break;
			}
		}
	}
	
	public boolean checkStartFight() {
		long currentTime = System.currentTimeMillis();
		long _nextFightTime = System.currentTimeMillis() + 60 * 1000;
		int FIGHT_INTERVAL = 3 * 60 * 1000;
		if (_nextFightTime < currentTime) {
			_nextFightTime = currentTime + FIGHT_INTERVAL;
			return true;
		}
		return true;
	}

	public void initNpc() {
		L1NpcInstance n = null;
		for (Object obj : L1World.getInstance().getVisibleObjects(DogFightSystemSettings.DogfightInfo.MapId).values()) {
			if (obj instanceof L1NpcInstance) {
				n = (L1NpcInstance) obj;
				if (n.getNpcTemplate().get_npcId() == DogFightSystemSettings.DogfightInfo.Ticket_Shop_Npcid) { //투견 티켓 상인
					_npc[0] = n;
				} 
				if (n.getNpcTemplate().get_npcId() == DogFightSystemSettings.DogfightInfo.DogFight_Helper_Npcid) { //투견 도우미
					_npc[1] = n;
				} 
				if (n.getNpcTemplate().get_npcId() == DogFightSystemSettings.DogfightInfo.DogFight_Chart_Npcid) { //분포 도우미
					_npc[2] = n;
				} 
			}
		}
	}
	
	public void initShopNpc() {
		List<L1ShopItem> sellingList = new ArrayList<L1ShopItem>();

		Shop shop = new Shop(DogFightSystemSettings.DogfightInfo.Ticket_Shop_Npcid, sellingList, _purchasingList);
		ShopTable.getInstance().addShop(DogFightSystemSettings.DogfightInfo.Ticket_Shop_Npcid, shop);
	}
	
	private FastTable<L1NpcInstance> list = new FastTable<L1NpcInstance>();

	public Iterator<L1NpcInstance> get_Fight_iter() {
		return list.iterator();
	}

	public void initWinRate() {
		//DogFight dogfight = null;
		Random rnd = new Random(System.nanoTime());
		for (int i = 0; i < 2; i++) {
			//dogfight = DogFightHistory.getInstance().getTemplate(_dogfight[i].get_num());
			//double rate = (double) dogfight.getWinCount() * 100.0 / (double) (dogfight.getWinCount() + dogfight.getLoseCount());
			double rate = (rnd.nextInt(20) + 200) * 0.1;
			_winRate[i] = Double.parseDouble(_df.format(rate));
		}
	}
	
	private void loadDog() {
		ArrayList<DogFightCharacters> char_info = DogFightCharacters.character_info();
		List<L1PcInstance> players = null;
		list.clear(); // -- 시작전에 초기화.
		int Start_X[] = { DogFightSystemSettings.DogfightInfo.Left_X, DogFightSystemSettings.DogfightInfo.Right_X };
		int Start_Y[] = { DogFightSystemSettings.DogfightInfo.Left_Y, DogFightSystemSettings.DogfightInfo.Right_Y };
		
		Collections.shuffle(char_info);
		for (int m = 0; m < 2; ++m) {
			try {
				DogFightCharacters cha = char_info.get(m);
				
				String name = null;
				String nameid = null;
				int heading = 0;
				if (m == 0) {
					name = cha.get_char_name();
					nameid = "[\\fB홀\\f>]# "+cha.get_char_name();
					heading = DogFightSystemSettings.DogfightInfo.Left_H;
				} else {
					name = cha.get_char_name();
					nameid = "[\\f3짝\\f>]# "+cha.get_char_name();
					heading = DogFightSystemSettings.DogfightInfo.Right_H;
				}
				
				_dogfight[m] = DogFightInstance.newInstance(cha.get_char_gfx(), name, nameid, cha.get_max_hp(), cha.get_char_num(), Start_X[m], Start_Y[m],
						DogFightSystemSettings.DogfightInfo.MapId, cha.get_min_dmg(), cha.get_max_dmg(), cha.get_skill_percent(),
						cha.get_skill_min_dmg(), cha.get_skill_max_dmg(), cha.get_attacker_effect(), cha.get_win_effect(), heading);
				
				L1World.getInstance().storeObject(_dogfight[m]);
				L1World.getInstance().addVisibleObject(_dogfight[m]);
				players = L1World.getInstance().getVisiblePlayer(_dogfight[m]);
				for (L1PcInstance member : players) {
					if (member != null) {
						member.updateObject();
						member.sendPackets(S_SkillSound.get(_dogfight[m].getId(), 11660));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void broadcastNpc(String msg) {
		if (_npc[0] != null) {
			_npc[0].broadcastPacket(new S_NpcChatPacket(_npc[0], msg, 2));
		}
	}
	
	public void broadcastNpc1(String msg) {
		if (_npc[1] != null) {
			_npc[1].broadcastPacket(new S_NpcChatPacket(_npc[1], "\\f3" + msg, 4));
		}
	}
	
	public void WinnerMent(String msg) {
		if (_npc[0] != null) {
			_npc[0].broadcastPacket(new S_NpcChatPacket(_npc[0], msg, 2));
		}
		if (_npc[1] != null) {
			_npc[1].broadcastPacket(new S_NpcChatPacket(_npc[1], msg, 4));
		}
	}
	
	public void startSellTicket() {
		LoadNpcShopList();
		broadcastNpc("투견 티켓 판매를 시작하였습니다.");
		this.setdogState(0);
		_ticketSellRemainTime = DogFightSystemSettings.DogfightInfo.DogFight_Time;
	}

	public long checkTicketSellTime() {
		if (_ticketSellRemainTime > 0) {
			if (_ticketSellRemainTime == 120) {
				broadcastNpc("[투견]경기 시작 2분전!");
			}
			if (_ticketSellRemainTime == 60) {
				broadcastNpc("[투견]경기 시작 1분전!");
			}
			_ticketSellRemainTime--;
			return 1000;
		}
		
		if (_ticketSellRemainTime == 0) {
			_ticketSellRemainTime = 0;
			broadcastNpc("10초 후 투견 티켓 판매를 마감합니다.");
			_FightWatingTime = 10;
		}
		return 0;
	}
	
	private boolean checkWatingTime() {		
		if (_FightWatingTime > 0) {
			broadcastNpc1(_FightWatingTime + "초");
			--_FightWatingTime;

			return false;
		} else {
			setdogState(1);
		}
		return true;
	}
	
	public int getdogState() {
		return this._dogfightState;
	}
	
	public void setdogState(int state) {
		this._dogfightState = state;
	}
		
	public void LoadNpcShopList() {
		try {
			List<L1ShopItem> sellingList = new ArrayList<L1ShopItem>();

			for (int i = 0; i < 3; i++) {
				ticket[i] = DogFigthSystemLoader.getInstance().getLastTicketNumber() + GetIssuedTicket() + 1;
				if (i == 0)
					SaveFight(ticket[i], "투견 티켓  " + _dogfight[i].getNameId() + "-" + (i + 1));
				else if (i == 1)
					SaveFight(ticket[i], "투견 티켓  " + _dogfight[i].getNameId() + "-" + (i + 1));
				else if (i == 2)
					SaveFight(ticket[i], "투견 티켓  [무]# 무승부 -" + (i + 1));
				
				L1ShopItem item = new L1ShopItem(ticket[i], 500, 1);// 판매 리스트
				sellingList.add(item);
				this._ticketId[i] = ticket[i];
			}
			
			for (int i = 0; i < 3; i++) {
				L1ShopItem item1 = new L1ShopItem(ticket[i], 25000000, 50000);// 판매 리스트
				sellingList.add(item1);
			}
			
			for (int i = 0; i < 3; i++) {
				L1ShopItem item1 = new L1ShopItem(ticket[i], 50000000, 100000);// 판매 리스트
				sellingList.add(item1);
			}

			Shop shop = new Shop(DogFightSystemSettings.DogfightInfo.Ticket_Shop_Npcid, sellingList, _purchasingList);
			ShopTable.getInstance().addShop(DogFightSystemSettings.DogfightInfo.Ticket_Shop_Npcid, shop);
			DogFigthSystemLoader.getInstance().setLastTicketNumber(ticket[ticket.length - 1]);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int GetIssuedTicket() {
		return _ticket_info.size();
	}
	
	private void SaveFight(int i, String j) {
		DogFightTicket etcItem = new DogFightTicket();
		etcItem.setItemDescId(1);
		etcItem.setType2(0);
		etcItem.setItemId(i);
		etcItem.setName(j);
		etcItem.setNameId(j);
		etcItem.setType(12);
		etcItem.setType1(12);
		etcItem.setMaterial(5);
		etcItem.setWeight(0);
		etcItem.set_price(500);
		etcItem.setGfxId(143);
		etcItem.setGroundGfxId(151);
		etcItem.setMinLevel(0);
		etcItem.setMaxLevel(0);
		etcItem.setBless(1);
		etcItem.setTradable(false);
		etcItem.setDmgSmall(0);
		etcItem.setDmgLarge(0);
		etcItem.set_stackable(true);
		AddTicket(etcItem);
	}
	
	public void AddTicket(DogFightTicket fight) {
		_ticket_info.put(Integer.valueOf(fight.getItemId()), fight);
		ItemTable.getInstance().getAllTemplates().put(fight.getItemId(), fight);
	}
	
	private void StartGame() {
		RunFight fight = new RunFight(0);
		RunFight fight1 = new RunFight(1);

		GeneralThreadPool.getInstance().schedule(fight, 1000);
		GeneralThreadPool.getInstance().schedule(fight1, 1000);
	}
	
	public class RunFight implements Runnable {
		private int _status = 0;

		private int _dogId;
		private Random _rndGen = new Random(System.nanoTime());

		public RunFight(int dogId) {
			_dogId = dogId;
		}

		@Override
		public void run() {
			try {
				if (_complete) {
					return;
				}
				switch (_status) {
				case 0: {
					DogFightInstance target = null;
					// 정상
					int damage = _rndGen.nextInt(_dogfight[_dogId].getMaxDmg()) + _dogfight[_dogId].getMinDmg();
					int skill_per = _dogfight[_dogId].getSkillPercent();
					int skill_dmg = _rndGen.nextInt(_dogfight[_dogId].getSkillMaxDmg()) + _dogfight[_dogId].getSkillMinDmg();
					
					// 무승부 테스트
					/*int damage = 10;
					int skill_per = 0;
					int skill_dmg = 10;*/
					if (_dogId == 0) {
						target = _dogfight[1];
					} else {
						target = _dogfight[0];
					}

					if (target.getCurrentHp() > 0 && !_dogfight[_dogId].isDead()) {
						int newhp = 0;
						if (_rndGen.nextInt(100) < skill_per || _dogfight[_dogId].getSkillUse()) {
							_dogfight[_dogId].broadcastPacket(S_DoActionGFX.get(_dogfight[_dogId].getId(), ActionCodes.ACTION_Attack));
							target.broadcastPacket(S_SkillSound.get(target.getId(), target.getSkillEffect()));
							GeneralThreadPool.getInstance().schedule(this, _dogfight[_dogId].getCurrentSpriteInterval(EActionCodes.attack));
							newhp = target.getCurrentHp() - skill_dmg;
							target.setCurrentHp(newhp);
							
							if (_dogfight[_dogId].getSkillUse())
								_dogfight[_dogId].setSkillUse(false);
						} else {
							_dogfight[_dogId].broadcastPacket(S_DoActionGFX.get(_dogfight[_dogId].getId(), ActionCodes.ACTION_Attack));
							target.broadcastPacket(S_AttackPacket.get(target, target.getId(), ActionCodes.ACTION_Damage));
							newhp = target.getCurrentHp() - damage;
							target.setCurrentHp(newhp);
							GeneralThreadPool.getInstance().schedule(this, _dogfight[_dogId].getCurrentSpriteInterval(EActionCodes.attack));
						}

						if (newhp <= 0) {
							if (!_dogfight[_dogId].isDead()) {
								Broadcaster.broadcastPacket(target, S_DoActionGFX.get(target.getId(), ActionCodes.ACTION_Die), true);
								newhp = 0;
								target.NpcDie();
								target.setCurrentHp(newhp);
								GeneralThreadPool.getInstance().schedule(this, _dogfight[_dogId].getCurrentSpriteInterval(EActionCodes.death));
							}
						}
					}
				}
				break;
				case 1:
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void delete() throws Exception {
		for (int i = 0; i < 2; ++i) {
			_dogfight[i].deleteMe();
		}
	}
	
	private void cleardog(int dogid) {
		try {
			GeneralThreadPool.getInstance().schedule(new deletedog(_dogfight[dogid]), 1000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	class deletedog implements Runnable {
		L1NpcInstance deldog;

		public deletedog(L1NpcInstance _dog) {
			deldog = _dog;
		}

		@Override
		public void run() {
			L1NpcInstance dog = deldog;
			dog.deleteMe();
		}
	}
	
	public void drawIn() {		
		synchronized (this) {
			SetWinFightTicketPrice(ticket[2]);
			SetLoseFightTicketPrice(ticket[0]);
			SetLoseFightTicketPrice(ticket[1]);
		}
		_complete = true;
	}
	
	public void goalIn(int i) {		
		synchronized (this) {
			if (i == 0) {
				SetWinFightTicketPrice(ticket[i]);
				SetLoseFightTicketPrice(ticket[1]);
				SetLoseFightTicketPrice(ticket[2]);
				AddWinCount(i);
				AddLoseCount(1);
			} else if (i == 1) {
				SetWinFightTicketPrice(ticket[i]);
				SetLoseFightTicketPrice(ticket[0]);
				SetLoseFightTicketPrice(ticket[2]);
				AddWinCount(i);
				AddLoseCount(0);
			}
		}
		
		_complete = true;
	}
	
	public void initFightGame() {
		try {
			_complete = false;
			_winner = -1;

			broadcastNpc("잠시후 투견 경기가 시작됩니다.");
			// 토탈 판매 장수 초기화
			initTicketCount();
			// 투견 초기화
			initNpc();
			// 상점 Npc초기화
			initShopNpc();
			// 투견 컨디션
			condition();
			// 투견 초기화 및 로딩
			loadDog();
			// 승률 초기화
			initWinRate();
			
		} catch (Exception e) {
		}
	}
	
	public int getTotalTicketCount() {
		int total = 0;
		for (int row = 0; row < 3; row++) {
			total += _ticketCount[row];
		}
		return total;
	}
	
	public void initTicketCount() {
		for (int row = 0; row < 3; row++) {
			_ticketCount[row] = 0;
		}
	}
	
	public void SetWinFightTicketPrice(int id) {
		L1ShopItem newItem = new L1ShopItem(id, (int) ((500 * DogFightSystemSettings.DogfightInfo.Rate)), 1);
		_purchasingList.add(newItem);
		initShopNpc();
	}
	
	public void AddWinCount(int j) {
		DogFight Fighter = DogFightHistory.getInstance().getTemplate(_dogfight[j].get_num());
		Fighter.setWinCount(Fighter.getWinCount() + 1);
		Fighter.setLoseCount(Fighter.getLoseCount());
		SaveAllFighter(Fighter, _dogfight[j].get_num());
	}
	
	public void SaveAllFighter(DogFight Fighter, int num) {
		java.sql.Connection con = null;
		PreparedStatement statement = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			statement = con.prepareStatement("UPDATE gamble_dogfight_history SET WinCount=?, LoseCount=? WHERE Num=" + num);
			statement.setInt(1, Fighter.getWinCount());
			statement.setInt(2, Fighter.getLoseCount());
			statement.execute();
		} catch (SQLException e) {
			System.out.println("[::::::] SaveAllFighter 메소드 에러 발생");
		} finally {
			if (statement != null) {
				try {
					statement.close();
				} catch (Exception e) {
				}
			}
			;
			if (con != null) {
				try {
					con.close();
				} catch (Exception e) {
				}
			}
			;
		}
	}
	
	public void SetLoseFightTicketPrice(int id) {
		L1ShopItem newItem = new L1ShopItem(id, 0, 1);// 승리표 판매 리스트 // 투견표 매입
		_purchasingList.add(newItem);
		initShopNpc();
	}
	
	public void AddLoseCount(int j) {
		DogFight Fighter = DogFightHistory.getInstance().getTemplate(_dogfight[j].get_num());
		Fighter.setWinCount(Fighter.getWinCount());
		Fighter.setLoseCount(Fighter.getLoseCount() + 1);
		SaveAllFighter(Fighter, _dogfight[j].get_num());
	}
	
	private void startFight() {
		broadcastNpc("경기 시작!");
		broadcastNpc1("경기 시작!");
	    _npc[1].broadcastPacket(S_DoActionGFX.get(_npc[1].getId(), 67));	
		StartGame();

		_currentBroadcastFighter = 0;
		_watingtime = 0;
	}
	
	private boolean broadcastBettingRate() {
		double one = 0;
		double two = 0;
		double three = 0;
		int total = getTotalTicketCount();
		long onet = _ticketCount[0];
		long twot = _ticketCount[1];
		long threet = _ticketCount[2];
		
		if (onet != 0) {
			one = ((double) onet / (double) total) * 100;
		}
		
		if (twot != 0) {
			two = ((double) twot / (double) total) * 100;
		}
		
		if (threet != 0) {
			three = ((double) threet / (double) total) * 100;
		}
		
		if (_watingtime == 1) {
			if (DogFightSystemSettings.DogfightInfo.Auto_Controll) {
				if (two > one) {
					_dogfight[0].addSkillPercent(DogFightSystemSettings.DogfightInfo.Auto_Controll_Plus_Skill_Percent);
					_dogfight[1].addSkillPercent(DogFightSystemSettings.DogfightInfo.Auto_Controll_Minus_Skill_Percent);
				} else if (one > two) {
					_dogfight[1].addSkillPercent(DogFightSystemSettings.DogfightInfo.Auto_Controll_Plus_Skill_Percent);
					_dogfight[0].addSkillPercent(DogFightSystemSettings.DogfightInfo.Auto_Controll_Minus_Skill_Percent);
				}
			}
		}
		
		if (_watingtime == 4)
			return true;
			
		if (_watingtime == 3) {
			String str = makeMoneyType((long)_ticketCount[0] * 500); 
			String strs = makeMoneyType((long)_ticketCount[1] * 500); 
			String strt = makeMoneyType((long)_ticketCount[2] * 500); 

			L1World.getInstance().castPacketGm(new S_SystemMessage("제 " + _FightCount + "회 분포도 홀 : "+Double.parseDouble(_df.format(one))+"% 무 : "+Double.parseDouble(_df.format(three))+"% 짝 : "+Double.parseDouble(_df.format(two))+"% 입니다."));
			L1World.getInstance().castPacketGm(new S_SystemMessage("제 " + _FightCount + "회 투견 [홀] 구입 총금액: " + str));
			L1World.getInstance().castPacketGm(new S_SystemMessage("제 " + _FightCount + "회 투견 [짝] 구입 총금액: " + strs));
			L1World.getInstance().castPacketGm(new S_SystemMessage("제 " + _FightCount + "회 투견 [무] 구입 총금액: " + strt));

		}
		
		++_watingtime;
		
		if (_currentBroadcastFighter == 3) {
			return true;
		}

		if (_currentBroadcastFighter == 0) {
			broadcastNpc("배팅 배율을 발표하겠습니다.");
		}

		if (_currentBroadcastFighter < 2)
			broadcastNpc(_dogfight[_currentBroadcastFighter].getNameId() + ": " + DogFightSystemSettings.DogfightInfo.Rate + " 배");
		else if (_currentBroadcastFighter == 2)
			broadcastNpc("무승부" + ": " + DogFightSystemSettings.DogfightInfo.Rate + " 배");
		
		++_currentBroadcastFighter;

		return false;
	}
	
	public static String makeMoneyType(double dblMoneyString) {
		String moneyString = Double.valueOf(dblMoneyString).toString();

		String format = "###,###";
		DecimalFormat df = new DecimalFormat(format);
		DecimalFormatSymbols dfs = new DecimalFormatSymbols();

		dfs.setGroupingSeparator(',');// 구분자를 ,로
		df.setGroupingSize(3);// 3자리 단위마다 구분자처리 한다.
		df.setDecimalFormatSymbols(dfs);

		return (df.format(Double.parseDouble(moneyString))).toString();
	}
	
	public int _betting_rate_time;
	
	public void broad_cast_betting_rate() {
		double one = 0;
		double two = 0;
		double three = 0;
		int total = getTotalTicketCount();
		long onet = _ticketCount[0];
		long twot = _ticketCount[1];
		long threet = _ticketCount[2];
		
		if (onet != 0) {
			one = ((double) onet / (double) total) * 100;
		}
		
		if (twot != 0) {
			two = ((double) twot / (double) total) * 100;
		}
		
		if (threet != 0) {
			three = ((double) threet / (double) total) * 100;
		}

		if (_betting_rate_time++ >= 1) { // 출력 주기 초단위
			if (_npc[2] != null) {
				_npc[2].broadcastPacket(new S_NpcChatPacket(_npc[2], "홀["+Double.parseDouble(_df.format(one))+"%] 무["+Double.parseDouble(_df.format(three))+"%] 짝["+Double.parseDouble(_df.format(two))+"%]", 4));
			}
			_betting_rate_time = 0;
		}
	}
	
}
