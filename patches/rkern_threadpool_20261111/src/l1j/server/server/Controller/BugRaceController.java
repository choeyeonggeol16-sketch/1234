package l1j.server.server.Controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;
import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastTable;
import l1j.server.DatabaseFactory;
import l1j.server.MJTemplate.MJPropertyReader;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Handler.FullSelectorHandler;
import l1j.server.server.ActionCodes;
import l1j.server.server.ThreadPool;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.RaceTable;
import l1j.server.server.datatables.ShopTable;
import l1j.server.server.model.L1IntRegistry;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.MJMessengerInstance;
import l1j.server.server.model.shop.L1Shop;
import l1j.server.server.model.network.core.sc.packet.SC_AttackPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1RaceTicket;
import l1j.server.server.templates.L1Racer;
import l1j.server.server.templates.L1ShopItem;
import l1j.server.server.storage.CacheD;

public class BugRaceController implements Runnable, TimeListener {
	private static final Logger LOG = Logger.getLogger(BugRaceController.class.getName());
	private static BugRaceController _instance;
    private final AtomicBoolean tickInFlight = new AtomicBoolean();
    private volatile boolean clockRegistered;
    private volatile long nextRunAtMs;
    private volatile int clockEpoch;
    // Published as a single snapshot. Rejected racer timers are retried by
    // the already registered RealTimeClock tick, never by a new worker.
    private volatile RunBug[] activeRunners = new RunBug[5];

    // RealTimeClock is already started before the race controller in GameServer.
    // The clock only dispatches: game, SQL and packet work remains off its thread.
    public synchronized void start() {
        if (clockRegistered) return;
        clockRegistered = true;
        ++clockEpoch;
        nextRunAtMs = 0L;
        RealTimeClock.getInstance().addListener(this, Calendar.SECOND);
        onSecondChanged(null);
    }

    public synchronized void stop() {
        if (!clockRegistered) return;
        clockRegistered = false;
        ++clockEpoch;
        activeRunners = new RunBug[5];
        RealTimeClock.getInstance().removeListener(this, Calendar.SECOND);
    }

    @Override public void onSecondChanged(BaseTime time) {
        if (!clockRegistered) return;
        // Do not run movement on the clock: only re-admit an already existing
        // runner whose previous one-shot schedule was rejected.
        final long now = System.currentTimeMillis();
        final RunBug[] runners = activeRunners;
        for (int i = 0; i < runners.length; i++) {
            if (runners[i] != null) runners[i].retryIfRejected(now);
        }
        if (System.currentTimeMillis() < nextRunAtMs
                || !tickInFlight.compareAndSet(false, true)) return;
        final int epoch = clockEpoch;
        try {
            ThreadPool.getInstance().execute(() -> {
                try {
                    if (clockRegistered && clockEpoch == epoch) run();
                } finally {
                    tickInFlight.set(false);
                }
            });
        } catch (RejectedExecutionException rejected) {
            tickInFlight.set(false);
            // The next second signal retries; no game logic runs on the clock.
            LOG.log(Level.WARNING, "BugRace tick admission rejected", rejected);
        }
    }

    @Override public void onMonthChanged(BaseTime time) { }
    @Override public void onDayChanged(BaseTime time) { }
    @Override public void onHourChanged(BaseTime time) { }
    @Override public void onMinuteChanged(BaseTime time) { }


	public static int BUG_BASIC_SPEED;
	public static boolean IS_RATE_REALTIME_UPDATE;
	public static double BUG_MIN_RATE;
	public static double[] BUG_MAX_RATES;
	public static boolean IS_AUTO_TUMBLE;
	public static int MAX_TUMBLE_PROBABILITY;
	public static void load_config(){
		MJPropertyReader reader = null;
		try{
			reader = new MJPropertyReader("./config/bug_race.properties");
			BUG_BASIC_SPEED = reader.readInt("BugBasicSpeed", "250");
			IS_RATE_REALTIME_UPDATE = reader.readBoolean("IsRateRealTimeUpdate", "true");
			BUG_MIN_RATE = reader.readDouble("BugMinRate", "1.2");
			BUG_MAX_RATES = reader.readDoubleArray("BugMaxRates", "10,9,8,7,6", "\\,");
			IS_AUTO_TUMBLE = reader.readBoolean("IsAutoTumble", "true");
			MAX_TUMBLE_PROBABILITY = reader.readInt("MaxTumbleProbability", "100");
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			if(reader != null)
				reader.dispose();
		}
	}
	
	
	//TODO 버경시간 조정 1이면 1분을 뜻함
	private static int RACE_INTERVAL = 1 * 60 * 1000;

	public static final int EXECUTE_STATUS_NONE = 0;
	public static final int EXECUTE_STATUS_PREPARE = 1;
	public static final int EXECUTE_STATUS_READY = 2;
	public static final int EXECUTE_STATUS_STANDBY = 3;
	public static final int EXECUTE_STATUS_PROGRESS = 4;
	public static final int EXECUTE_STATUS_FINALIZE = 5;

	private int _executeStatus = EXECUTE_STATUS_NONE;

	public int _raceCount = 0;
	long _nextRaceTime = System.currentTimeMillis() + 60 * 1000;
	public int _bugRaceState = 2;

	public int _ticketSellRemainTime;
	public int _raceWatingTime;
	public int _currentBroadcastRacer;

	L1NpcInstance[] _npc = new L1NpcInstance[3];
	MJMessengerInstance _box_npc;
	
	public int[] _ticketCount = new int[5];
	private static Random _rnd = new Random(System.nanoTime());
	private static DecimalFormat _df = new DecimalFormat("#.#");

	public int _ranking = 0;
	public boolean _complete = false;

	List<L1ShopItem> _purchasingList = new CopyOnWriteArrayList<L1ShopItem>();
	public L1NpcInstance[] _littleBugBear = new L1NpcInstance[5];

	int Lucky = 0;
	private static Random rnd = new Random(System.nanoTime());

	/** 버경 추가 **/
	private final HashMap<Integer, L1RaceTicket> _race = new HashMap<Integer, L1RaceTicket>(32);

	/*
	 * 동적 버경 티켓 ID는 _race.size()로 만들면 안 된다.
	 * 임시 패키지 티켓을 정리한 뒤 size가 줄어들면 ID가 재사용될 수 있기 때문.
	 * 별도 증가 시퀀스로만 발급한다.
	 */
	private static final int TICKET_ID_BASE = 8000000;
	private int _nextTicketId = TICKET_ID_BASE + 1;

	/*
	 * packCount > 1 티켓은 상점 표시/구매 변환용 임시 SKU다.
	 * 실제 지급은 converter_itemid(기본 티켓) 기준이므로
	 * 경기 종료 후 템플릿을 제거해도 과거 기본 티켓 환전에는 영향이 없다.
	 */
	private final ArrayList<Integer> _temporaryTicketIds = new ArrayList<Integer>(16);
	//private L1Item _allTemplates[] = new L1Item[9000000];
	//private HashMap<Integer, L1Item> _allTemplates = new HashMap<Integer, L1Item>(32);
	
	public HashMap<Integer, L1RaceTicket> getAllTemplates() {
		return _race;
	}

	private int Start_X[] = { 33522, 33520, 33518, 33516, 33514 };
	private int Start_Y[] = { 32861, 32863, 32865, 32867, 32869 };

	private static final ArrayList<BugStruct> _bugs;
	static{
		
		//TODO 버경
		_bugs = new ArrayList<BugStruct>(32);
		_bugs.add(new BugStruct(1, 3478, "배크두"));
		_bugs.add(new BugStruct(2, 3479, "투투"));
		_bugs.add(new BugStruct(3, 3480, "버기"));
		_bugs.add(new BugStruct(4, 3481, "얼루"));
		_bugs.add(new BugStruct(5, 3497, "제프리"));
		_bugs.add(new BugStruct(6, 3498, "카이"));
		_bugs.add(new BugStruct(7, 3499, "아돌프"));
		_bugs.add(new BugStruct(8, 3500, "오버풋"));
		_bugs.add(new BugStruct(9, 3501, "럼프스"));
		_bugs.add(new BugStruct(10, 3502, "부카"));
		_bugs.add(new BugStruct(11, 3503, "그룩"));
		_bugs.add(new BugStruct(12, 3504, "쿤드라"));
		_bugs.add(new BugStruct(13, 3505, "쿠마토"));
		_bugs.add(new BugStruct(14, 3506, "두렉"));
		_bugs.add(new BugStruct(15, 3507, "그로돈"));
		_bugs.add(new BugStruct(16, 3508, "퀘니버"));
		_bugs.add(new BugStruct(17, 3509, "플루토"));
		_bugs.add(new BugStruct(18, 3510, "두리바"));
		_bugs.add(new BugStruct(19, 3511, "버루얼"));
		_bugs.add(new BugStruct(20, 3512, "일레자"));
	}

	public static int[] _time = new int[5];
	public static String _first = null;

	public int[] _ticket = { 0, 0, 0, 0, 0 };
	
	private final ConcurrentHashMap<Integer, BugTicketInfo> m_tickets = new ConcurrentHashMap<Integer, BugTicketInfo>(32);
	
	// 승률 초기화
	public double[] _winRate = { 0, 0, 0, 0, 0 };
	public double[] _winViewRate = { 0, 0, 0, 0, 0 };
	// 상태 초기화
	public String[] _bugCondition = { "매우좋음", "좋음", "나쁨", "매우나쁨", "보통" };
	// 배율 초기화
	public double _ration[] = { 0, 0, 0, 0, 0 };

	public boolean[] _is_downs = new boolean[]{false, false, false, false, false};
	
	public int _round;
	
	public static BugRaceController getInstance() {
		if (_instance == null) {
			_instance = new BugRaceController();
		}
		return _instance;
	}
	
	private BugRaceController(){
		load_config();
		_round = 0;
		Selector.exec("select max(round) as r_nd from bug_history", new FullSelectorHandler(){
			@Override
			public void result(ResultSet rs) throws Exception {
				if(rs.next())
					_round = rs.getInt("r_nd");
			}
		});
	}
	
	public static class BugTicketInfo{
		public int racerId;
		public int itemId;
		public int packCount;
		public int converter_itemid;
	}
	
	public BugTicketInfo find_ticket_info(int itemid){
		return m_tickets.get(itemid);
	}

	@Override
	public void run() {
        // Callbacks can be queued before stop(); no state mutation after stop.
        if (!clockRegistered) return;
        long nextDelayMs = 1000L;
        try {
            switch (_executeStatus) {
            case EXECUTE_STATUS_NONE:
                if (checkStartRace()) {
                    initRaceGame();
                    _executeStatus = EXECUTE_STATUS_PREPARE;
                }
                break;
            case EXECUTE_STATUS_PREPARE:
                startSellTicket();
                _executeStatus = EXECUTE_STATUS_READY;
                break;
            case EXECUTE_STATUS_READY:
                final long remainTime = checkTicketSellTime();
                if (remainTime > 0L) {
                    nextDelayMs = remainTime;
                } else {
                    _executeStatus = EXECUTE_STATUS_STANDBY;
                }
                break;
            case EXECUTE_STATUS_STANDBY:
                if (checkWatingTime()) {
                    startBugRace();
                    _executeStatus = EXECUTE_STATUS_PROGRESS;
                }
                break;
            case EXECUTE_STATUS_PROGRESS:
                if (broadcastBettingRate() && _complete) {
                    _executeStatus = EXECUTE_STATUS_FINALIZE;
                }
                break;
            case EXECUTE_STATUS_FINALIZE:
                wrapUpRace();
                _executeStatus = EXECUTE_STATUS_NONE;
                break;
            default:
                _executeStatus = EXECUTE_STATUS_NONE;
                break;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "BugRace controller tick failed", e);
        } finally {
            // Preserve the old 1s/60s phase delays without retaining a chain
            // of one-shot futures. Retry after a rejection on the next signal.
            nextRunAtMs = System.currentTimeMillis() + nextDelayMs;
        }
    }

	public boolean checkStartRace() {
		long currentTime = System.currentTimeMillis();
		if (_nextRaceTime < currentTime) {
			_nextRaceTime = currentTime + RACE_INTERVAL;
			return true;
		}
		return false;
	}

	public void initRaceGame() {
		try {
			++_round;
			_ranking = 0;
			_complete = false;
			_first = null;

			Lucky = rnd.nextInt(50);

			broadcastNpc("소지중인 레이스표를 판매하시길 바랍니다.");
			_is_downs = new boolean[]{false, false, false, false, false};
			
			// 버그베어 경주 초기화
			initNpc();
			// 상점 Npc초기화
			initShopNpc();
			// 버그베어 달리기 속도 지정
			sleepTime();
			// 버그베어 초기화 및 로딩
			loadDog();
			// 승률 초기화
			initWinRate();
			// 게임시
			doorAction(false);
		} catch (Exception e) {
		}
	}
	
	public synchronized void on_buy_ticket(int ticket_id, int amount){
		BugTicketInfo tInfo = m_tickets.get(ticket_id);
		if(tInfo == null)
			return;
		
		int ticket_count = _ticketCount[tInfo.racerId] + (amount * tInfo.packCount);
		_ticketCount[tInfo.racerId] = ticket_count;		
		SettingRate();
		//if(IS_RATE_REALTIME_UPDATE)
			
	}

	public void initTicketCount() {
		for (int row = 0; row < 5; row++) {
			this._ticketCount[row] = 20;
			_ration[row] = 5.0D;
		}
		SettingRate();
	}

	// 생성된 Npc객체를 이니셜라이즈 한다.
	public void initNpc() {
		/*
		 * 기존에는 map 4의 모든 visible object를 훑었다.
		 * L1World에 이미 npcId -> L1NpcInstance 인덱스가 있으므로 바로 조회한다.
		 */
		_npc[0] = L1World.getInstance().findNpc(70041);
		_npc[1] = L1World.getInstance().findNpc(70035);
		_npc[2] = L1World.getInstance().findNpc(70042);

		final L1NpcInstance boxNpc =
				L1World.getInstance().findNpc(50000063);

		if (boxNpc instanceof MJMessengerInstance) {
			_box_npc = (MJMessengerInstance) boxNpc;
			_box_npc.set_is_ghost(true);
		} else {
			_box_npc = null;
		}
	}

	public void initShopNpc() {
		List<L1ShopItem> sellingList = new ArrayList<L1ShopItem>();
		L1Shop shop = new L1Shop(70035, sellingList, _purchasingList);
		ShopTable.getInstance().addShop(70035, shop);
		L1Shop shop1 = new L1Shop(70041, sellingList, _purchasingList);
		ShopTable.getInstance().addShop(70041, shop1);
		L1Shop shop2 = new L1Shop(70042, sellingList, _purchasingList);
		ShopTable.getInstance().addShop(70042, shop2);
	}

	private void sleepTime() {
		for (int i = 0; i < 5; i++) {
			int bugState = _rnd.nextInt(5);
			int addValue = 0;

			switch (bugState) {
			case 0:
				_bugCondition[i] = "매우좋음";
				addValue = -2;
				break;
			case 1:
				_bugCondition[i] = "좋음";
				addValue = -1;
				break;
			case 2:
				_bugCondition[i] = "나쁨";
				addValue = 1;
				break;
			case 3:
				_bugCondition[i] = "매우나쁨";
				addValue = 2;
				break;
			default:
				_bugCondition[i] = "보통";
				addValue = 0;
				break;
			}
			_time[i] = BUG_BASIC_SPEED + addValue;
		}
	}

	// 승률처리
	public void initWinRate() {
		double presentation_rate = MJRnd.next_double(20, 30);
		for (int i = 0; i < 5; i++) {
			_winRate[i] = Double.parseDouble(_df.format(MJRnd.next_double(20.1, 21)));
			 _winViewRate[i] = Double.parseDouble(_df.format(presentation_rate));
		}
	}

	private FastTable<L1NpcInstance> list = new FastTable<L1NpcInstance>();

	public L1NpcInstance find_bug(int object_id){
		for(L1NpcInstance npc : list){
			if(npc.getId() == object_id)
				return npc;
		}
		return null;
	}
	
	public boolean down_bug(int object_id){
		boolean do_down = false;
		for(int i=list.size() - 1; i>=0; --i){
			L1NpcInstance npc = list.get(i);
			if(npc.getId() != object_id)
				continue;
			
			_is_downs[i] = true;
			do_down = true;
			break;
		}
		return do_down;
	}
	
	public Iterator<L1NpcInstance> get_race_iter() {
		return list.iterator();
	}

	public void setSpeed(int i, int speed){
		_time[i] = speed;
	}
	
	public int getSpeed(int i){
		return _time[i];
	}
	
	private void loadDog() {
	    L1Npc dogs = null;
	    List<L1PcInstance> players = null;

	    list.clear();

	    Collections.shuffle(_bugs);

	    for (int m = 0; m < 5; ++m) {
	        try {
	            BugStruct bs = _bugs.get(m);

	            dogs = new L1Npc();
	            dogs.set_family(0);
	            dogs.set_agrofamily(0);
	            dogs.set_picupitem(false);

	            _littleBugBear[m] = new L1NpcInstance(dogs);
	            _littleBugBear[m].setCurrentSprite(bs.gfx);

	            _littleBugBear[m].setNameId(
	                    String.format("#%d%s", bs.id, bs.name));
	            _littleBugBear[m].setName(bs.name);
	            _littleBugBear[m].set_num(bs.id);
	            _littleBugBear[m].setX(Start_X[m]);
	            _littleBugBear[m].setY(Start_Y[m]);
	            _littleBugBear[m].setMap((short) 4);
	            _littleBugBear[m].setHeading(6);
	            _littleBugBear[m].setId(
	                    IdFactory.getInstance().nextId());

	            L1World.getInstance()
	                    .storeObject(_littleBugBear[m]);

	            L1World.getInstance()
	                    .addVisibleObject(_littleBugBear[m]);

	            list.add(_littleBugBear[m]);

	            players = L1World.getInstance().getVisiblePlayerFast(_littleBugBear[m]);

	            for (L1PcInstance member : players) {
	                if (member != null && member.canSeeObject(_littleBugBear[m])) {
	                    member.inSight(_littleBugBear[m]);
	                }
	            }

	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
	}

	public void broadcastNpc(String msg) {
		for (int i = 0; i < _npc.length; ++i) {
			if (_npc[i] != null) {
				SC_NpcChatPacket.broadcastNpcFast(_npc[i], _npc[i], msg, 2);
			}
		}
	}
	
	public void broadcastBox(String msg){
		if(_box_npc != null){
			_box_npc.set_current_message(String.format("\\fS%s", msg));
			_box_npc.broadcast_message();
		}
	}

	public void 우승자멘트(String msg) {
		for (int i = 0; i < _npc.length; ++i) {
			if (_npc[i] != null) {
				SC_NpcChatPacket.broadcastNpcFast(_npc[i], _npc[i], msg, 2);
			}
		}
	}

	public void doorAction(boolean open) {

		final L1IntRegistry<L1Object> mapObjects =
		        L1World.getInstance().getVisibleObjects(4);

		if (mapObjects == null || mapObjects.isEmpty()) {
			return;
		}

		for (L1Object object : mapObjects.values()) {
			if (!(object instanceof L1DoorInstance)) {
				continue;
			}

			final L1DoorInstance door =
					(L1DoorInstance) object;

			if (!door.equalsCurrentSprite(1487)) {
				continue;
			}

			if (open) {
				if (door.getOpenStatus() == ActionCodes.ACTION_Close) {
					door.open();
				}
			} else if (door.getOpenStatus() == ActionCodes.ACTION_Open) {
				door.close();
			}
		}
	}

	public void startSellTicket() {
		LoadNpcShopList();
		// 토탈 판매 장수 초기화
		initTicketCount();
		broadcastNpc("레이스표 판매가 시작되었습니다.");
		this.setBugState(0);
		_ticketSellRemainTime = 60 * 3;
	}

	public long checkTicketSellTime() {
		if (_ticketSellRemainTime == 3 * 60) {
			_ticketSellRemainTime -= 60;
			broadcastNpc("버경 시작 3분전!!");
			broadcastBox("판매 마감 : 3 분 전");
			return 60 * 1000;
		} else if (_ticketSellRemainTime == 2 * 60) {
			_ticketSellRemainTime -= 60;
			broadcastNpc("버경 시작 2분전!!");
			//broadcastBox("\\aY판매 마감 : 2 분 전");
			broadcastBox("판매 마감 : 2 분 전");
			return 60 * 1000;
		} else if (_ticketSellRemainTime == 1 * 60) {
			_ticketSellRemainTime -= 60;
			broadcastNpc("버경 시작 1분전!!");
			broadcastBox("판매 마감 : 1 분 전");//색상이 표기가 안됨
			scheduleTicketCloseCountdown();

			return 60 * 1000;
		} else if (_ticketSellRemainTime == 1 * 30) {//밑으로 읽질않음
			_ticketSellRemainTime = 0;
			return 30 * 1000;
		}
		initShopNpc();
		broadcastNpc("출발 준비!");
		SettingRate();
		_raceWatingTime = 5;
		return 0;
	}

	private void scheduleTicketCloseCountdown() {
		final int epoch = clockEpoch;
		ThreadPool.getInstance().scheduleRequired(new Runnable() {
			private int remain = 30;

			@Override
			public void run() {
				if (!clockRegistered || epoch != clockEpoch) return;
				try {
					if (remain == 30) {
						broadcastNpc("30초 후 레이스표 판매가 마감됩니다.");
					}

					if (remain > 0) {
						broadcastBox(String.format("판매 마감 : %d 초 전", remain));
						--remain;
						ThreadPool.getInstance().scheduleRequired(this, 1000L);
						return;
					}

					broadcastBox("판매가 종료 되었습니다.");

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, 35000L);
	}

	private boolean checkWatingTime() {
		setBugState(1);
		if (_raceWatingTime > 0) {
			broadcastNpc(_raceWatingTime + "초");
			--_raceWatingTime;

			return false;
		}

		return true;
	}

	private void startBugRace() {

		broadcastNpc("시작");
		doorAction(true);

		StartGame();

		_currentBroadcastRacer = 0;
	}

	private boolean broadcastBettingRate() {
		if (_currentBroadcastRacer == 5) {
			return true;
		}

		if (_currentBroadcastRacer == 0) {
			broadcastNpc("배팅 배율을 발표하겠습니다.");
		}

		broadcastNpc(_littleBugBear[_currentBroadcastRacer].getNameId() + ": " + _ration[_currentBroadcastRacer] + " ");

		++_currentBroadcastRacer;

		return false;
	}

	public void SettingRate() {// 배율설정
		double total = getTotalTicketCount();
		ArrayList<RationInfo> temporary_ration = new ArrayList<RationInfo>(5);
		for (int row = 0; row < 5; row++) {
			double cnt = _ticketCount[row];
			RationInfo rInfo = new RationInfo();
			rInfo.idx = row;
			rInfo.ration = _ration[row];
			if(cnt <= 0){
				while(rInfo.ration <= BUG_MIN_RATE)
					rInfo.ration = MJRnd.next_double() * 10;
			}else{
				rInfo.ration = total / cnt;
			}
			if(rInfo.ration <= BUG_MIN_RATE)
				rInfo.ration = BUG_MIN_RATE + MJRnd.next_double();
			temporary_ration.add(rInfo);
		}
		Collections.sort(temporary_ration);
		for(int i=0; i<5; ++i){
			double need_range = BUG_MAX_RATES[i] - 1D;
			double range = BUG_MAX_RATES[i];
			RationInfo rInfo = temporary_ration.get(i);
			if(rInfo.ration >= range){
				double d = MJRnd.next_double();
				rInfo.ration = need_range + d;
			}
			_ration[rInfo.idx] = Double.parseDouble(_df.format(rInfo.ration));
		}
	}
	
	static class RationInfo implements Comparable<RationInfo>{
		int idx;
		double ration;
		@Override
		public int compareTo(RationInfo o) {
			double d = o.ration - ration;
			return d > 0 ? 1 : d < 0 ? -1 : 0;
		}
	}
	
	public double[] calc_rations(){
		double total = getTotalTicketCount();
		double[] rations = new double[5];
		for(int i=4; i>=0; --i)
			rations[i] = total / _ticketCount[i];
		return rations;
	}

	public void AddWinCount(int j) {
		L1Racer racer = RaceTable.getInstance().getTemplate(_littleBugBear[j].get_num());
		if (racer != null) {
			racer.setWinCount(racer.getWinCount() + 1);
			racer.setLoseCount(racer.getLoseCount());
			saveRaceResult(_littleBugBear[j].get_num(), true);
		}
	}

	public void AddLoseCount(int j) {
		L1Racer racer = RaceTable.getInstance().getTemplate(_littleBugBear[j].get_num());
		if (racer != null) {
			racer.setWinCount(racer.getWinCount());
			racer.setLoseCount(racer.getLoseCount() + 1);
			saveRaceResult(_littleBugBear[j].get_num(), false);
		}
	}

	public void SaveAllRacer(L1Racer racer, int num) {
		if (racer == null) return;
		final int racerId = num;
		final int wins = racer.getWinCount();
		final int losses = racer.getLoseCount();
		submitRaceDb(() -> {
			try (Connection con = DatabaseFactory.getInstance().getConnection();
			     PreparedStatement statement = con.prepareStatement(
					"UPDATE util_racer SET 승리횟수=GREATEST(승리횟수, ?), 패횟수=GREATEST(패횟수, ?) WHERE 레이서번호=?")) {
				statement.setInt(1, wins);
				statement.setInt(2, losses);
				statement.setInt(3, racerId);
				statement.executeUpdate();
			} catch (SQLException e) {
				throw new IllegalStateException("BugRace racer save failed: " + racerId, e);
			}
		});
	}

	private static void saveRaceResult(int racerId, boolean winner) {
		submitRaceDb(() -> {
			try (Connection con = DatabaseFactory.getInstance().getConnection();
			     PreparedStatement statement = con.prepareStatement(winner
			             ? "UPDATE util_racer SET 승리횟수=승리횟수+1 WHERE 레이서번호=?"
			             : "UPDATE util_racer SET 패횟수=패횟수+1 WHERE 레이서번호=?")) {
				statement.setInt(1, racerId);
				statement.executeUpdate();
			} catch (SQLException e) {
				throw new IllegalStateException("BugRace racer result save failed: " + racerId, e);
			}
		});
	}

	private static void submitRaceDb(Runnable job) {
		try {
			CacheD.getInstance().submitDbJob(job);
		} catch (RejectedExecutionException rejected) {
			LOG.log(Level.WARNING, "BugRace CacheD queue rejected; saving on race scheduler thread", rejected);
			try {
				job.run();
			} catch (RuntimeException failure) {
				LOG.log(Level.SEVERE, "BugRace fallback DB write failed", failure);
			}
		}
	}

	public void SetWinRaceTicketPrice(int id, double rate) {
		L1ShopItem newItem = new L1ShopItem(id, (int) (500 * rate), 1);// 승리표 판매 리스트 레이스표 매입
		_purchasingList.add(newItem);
		initShopNpc();
	}

	public void SetLoseRaceTicketPrice(int id, double rate) {
		L1ShopItem newItem = new L1ShopItem(id, 0, 1);// 승리표 판매 리스트 // 레이스표 매입
		_purchasingList.add(newItem);
		initShopNpc();
	}

	private synchronized int next_ticket_id() {
		/*
		 * _race.size() 기반 ID 발급 금지.
		 * 임시 티켓 정리 후에도 절대 같은 런타임에서 ID가 재사용되지 않는다.
		 */
		while (_race.containsKey(_nextTicketId)
				|| ItemTable.getInstance().getAllTemplates().containsKey(_nextTicketId)) {
			++_nextTicketId;
		}

		return _nextTicketId++;
	}

	private synchronized BugTicketInfo create_ticket(int racer_id, int pack_count, int converter_itemid, String color_code){
		BugTicketInfo tInfo = new BugTicketInfo();
		tInfo.itemId = next_ticket_id();
		tInfo.racerId = racer_id;
		tInfo.packCount = pack_count;
		tInfo.converter_itemid = converter_itemid == -1 ? tInfo.itemId : converter_itemid;

		if(pack_count > 1) {
			SaveRace(tInfo.itemId,
					String.format("%s%d-%d %s X %d",
							color_code,
							_round,
							_littleBugBear[racer_id].get_num(),
							_littleBugBear[racer_id].getName(),
							pack_count));

			/*
			 * 패키지 티켓은 실제 지급용 기본 티켓이 아니라
			 * 구매 시 수량 변환을 위한 임시 상점 SKU.
			 */
			_temporaryTicketIds.add(tInfo.itemId);

		} else {
			SaveRace(tInfo.itemId,
					String.format("%s%d-%d %s",
							color_code,
							_round,
							_littleBugBear[racer_id].get_num(),
							_littleBugBear[racer_id].getName()));
		}

		m_tickets.put(tInfo.itemId, tInfo);
		return tInfo;
	}
	
	private L1ShopItem create_ticket_shop_item(BugTicketInfo tInfo){
		return new L1ShopItem(tInfo.itemId, 500 * tInfo.packCount, tInfo.packCount);
	}
	
	private synchronized void cleanupTemporaryTicketTemplates() {
		if (_temporaryTicketIds.isEmpty()) {
			return;
		}

		for (int i = 0; i < _temporaryTicketIds.size(); i++) {
			final Integer itemId = _temporaryTicketIds.get(i);

			if (itemId == null) {
				continue;
			}

			m_tickets.remove(itemId);
			_race.remove(itemId);
			ItemTable.getInstance().getAllTemplates().remove(itemId);
		}

		_temporaryTicketIds.clear();
	}

	public void LoadNpcShopList() { // 버경 레이스표
		try {
			List<L1ShopItem> sellingList = new ArrayList<L1ShopItem>();
			for (int i = 0; i < 5; i++) {
				BugTicketInfo tInfo = create_ticket(i, 1, -1, "");
				sellingList.add(create_ticket_shop_item(tInfo));
				int default_itemid = tInfo.itemId;
				_ticket[i] = default_itemid;
			}
			for(int i=0; i<5; ++i){
				int default_itemid = _ticket[i];
				BugTicketInfo tInfo = create_ticket(i, 20000, default_itemid, "\\aS");
				sellingList.add(create_ticket_shop_item(tInfo));
			}
			for(int i=0; i<5; ++i){
				int default_itemid = _ticket[i];
				BugTicketInfo tInfo = create_ticket(i, 50000, default_itemid, "\\aJ");
				sellingList.add(create_ticket_shop_item(tInfo));
			}
			for(int i=0; i<5; ++i){
				int default_itemid = _ticket[i];
				BugTicketInfo tInfo = create_ticket(i, 100000, default_itemid, "\\aG");
				sellingList.add(create_ticket_shop_item(tInfo));
			}

			L1Shop shop = new L1Shop(70035, sellingList, _purchasingList);
			ShopTable.getInstance().addShop(70035, shop);
			L1Shop shop1 = new L1Shop(70041, sellingList, _purchasingList);
			ShopTable.getInstance().addShop(70041, shop1);
			L1Shop shop2 = new L1Shop(70042, sellingList, _purchasingList);
			ShopTable.getInstance().addShop(70042, shop2);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void reLoadNpcShopList() {
		try {
			List<L1ShopItem> sellingList = new ArrayList<L1ShopItem>();
			for (int i = 0; i < 5; i++) {
				BugTicketInfo tInfo = create_ticket(i, 1, -1, "");
				sellingList.add(create_ticket_shop_item(tInfo));
				int default_itemid = tInfo.itemId;
				_ticket[i] = default_itemid;				
			}
			for(int i=0; i<5; ++i){
				int default_itemid = _ticket[i];
				BugTicketInfo tInfo = create_ticket(i, 20000, default_itemid, "\\aS");
				sellingList.add(create_ticket_shop_item(tInfo));
			}
			for(int i=0; i<5; ++i){
				int default_itemid = _ticket[i];
				BugTicketInfo tInfo = create_ticket(i, 50000, default_itemid, "\\aJ");
				sellingList.add(create_ticket_shop_item(tInfo));
			}
			for(int i=0; i<5; ++i){
				int default_itemid = _ticket[i];
				BugTicketInfo tInfo = create_ticket(i, 100000, default_itemid, "\\aG");
				sellingList.add(create_ticket_shop_item(tInfo));
			}

			L1Shop shop = new L1Shop(70035, sellingList, _purchasingList);
			ShopTable.getInstance().addShop(70035, shop);
			L1Shop shop1 = new L1Shop(70041, sellingList, _purchasingList);
			ShopTable.getInstance().addShop(70041, shop1);
			L1Shop shop2 = new L1Shop(70042, sellingList, _purchasingList);
			ShopTable.getInstance().addShop(70042, shop2);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void SaveRace(int i, String j) {
		L1RaceTicket etcItem = new L1RaceTicket();
		etcItem.setType2(0);
		etcItem.setItemId(i);
		etcItem.setName(j);
		etcItem.setNameId(j);
		etcItem.setType(12);
		etcItem.setType1(12);
		etcItem.setMaterial(5);
		etcItem.setWeight(0);
		etcItem.set_price(1000);
		etcItem.setGfxId(143);//143
		etcItem.setGroundGfxId(151);
		etcItem.setMinLevel(0);
		etcItem.setMaxLevel(0);
		etcItem.setBless(1);
		etcItem.setTradable(false);
		etcItem.setDmgSmall(0);
		etcItem.setDmgLarge(0);
		etcItem.set_stackable(true);
		// ItemTable.getInstance().AddTicket(etcItem);
		AddTicket(etcItem);
	}

	public void goalIn(final int i) {
		synchronized (this) {
			_ranking = _ranking + 1;
			// broadcastNpc(_ranking + "위 - " + _littleBugBear[i].getNameId());
			if (_ranking == 1) {
				_first = _littleBugBear[i].getName();
				SetWinRaceTicketPrice(_ticket[i], _ration[i]);
				AddWinCount(i);
				ThreadPool.getInstance().scheduleRequired(new Runnable(){
					@Override
					public void run(){
						우승자멘트(String.format("제 %d회 우승자는 '%s' 입니다.", _round, _littleBugBear[i].getNameId()));						
					}
				}, 700L);
				final int raceRound = _round;
				final int winnerId = i;
				final String winnerName = _littleBugBear[i].getName();
				final int totalTickets = getTotalTicketCount();
				final int winnerTickets = _ticketCount[i];
				final double winnerRate = _ration[i];
				final int totalPrice = totalTickets * 500;
				final int winnerPrice = (int) (winnerRate * winnerTickets);
				submitRaceDb(() -> {
					try (Connection con = DatabaseFactory.getInstance().getConnection();
					     PreparedStatement statement = con.prepareStatement(
						"INSERT INTO bug_history SET round=?, winner_id=?, winner_name=?, total_ticket_count=?, winner_ticket_count=?, winner_ration=?, total_price=?, winner_price=?")) {
						statement.setInt(1, raceRound);
						statement.setInt(2, winnerId);
						statement.setString(3, winnerName);
						statement.setInt(4, totalTickets);
						statement.setInt(5, winnerTickets);
						statement.setDouble(6, winnerRate);
						statement.setInt(7, totalPrice);
						statement.setInt(8, winnerPrice);
						statement.executeUpdate();
					} catch (SQLException e) {
						throw new IllegalStateException("BugRace history save failed: round=" + raceRound, e);
					}
				});
			} else {
				SetLoseRaceTicketPrice(_ticket[i], _ration[i]);
				AddLoseCount(i);
			}
		}

		if (_ranking == 5) {
			_complete = true;
		}
	}

	public void wrapUpRace() throws Exception {
		_littleBugBear[0].deleteMe();
		_littleBugBear[1].deleteMe();
		_littleBugBear[2].deleteMe();
		_littleBugBear[3].deleteMe();
		_littleBugBear[4].deleteMe();

		/*
		 * 실제 지급되지 않는 20,000/50,000/100,000 묶음용 SKU만 정리.
		 * 기본 티켓 5개는 과거 경기표 환전을 위해 남긴다.
		 */
		cleanupTemporaryTicketTemplates();

		_raceCount = _raceCount + 1;
		setBugState(2);
		broadcastNpc("다음 버그베어경기를 준비중입니다.");
	}

	public void BroadcastAllUser(String text) {
		for (L1PcInstance player : L1World.getInstance().getAllPlayers()) {
			try {
				SC_SystemMessage.send(player, text);
			} catch (Exception exception) {
			}
		}
	}

	private void StartGame() {
        final RunBug[] runners = new RunBug[5];
		for (int i = 0; i < runners.length; ++i) {
            runners[i] = new RunBug(i);
		}
        // Publish before first scheduling: even an initial rejection now has
        // an existing clock callback which will retry the same runner.
        activeRunners = runners;
        for (int i = 0; i < runners.length; i++) {
            runners[i].scheduleNext(100L);
        }
	}

	public class RunBug implements Runnable {
		// Race movement timers can outlive the controller's stop/restart.
		// Ignore old timers rather than moving despawned racers.
		private final int _epoch = clockEpoch;
		private int _status = 0;

		private int[][] _BUG_INFO = { 
				{ 45, 4, 5, 6, 50 }, 
				{ 42, 6, 5, 7, 50 }, 
				{ 39, 8, 5, 8, 50 },
				{ 36, 10, 5, 9, 50 }, 
				{ 33, 12, 5, 10, 50 }
		};

		private int _bugId;
		private int _remainRacingCount;
		private Random _rndGen = new Random(System.nanoTime());
        private long rejectedDueMs;

        private synchronized void scheduleNext(long delayMs) {
            if (!clockRegistered || _epoch != clockEpoch) return;
            final long delay = Math.max(1L, delayMs);
            final long due = System.currentTimeMillis() + delay;
            if (ThreadPool.getInstance().schedule(this, delay) == null) {
                // Retain the current phase, position and runner instance.
                // Losing this scheduled callback would freeze the race.
                rejectedDueMs = due;
            } else {
                rejectedDueMs = 0L;
            }
        }

        private synchronized void retryIfRejected(long nowMs) {
            if (rejectedDueMs != 0L && nowMs >= rejectedDueMs
                    && clockRegistered && _epoch == clockEpoch) {
                scheduleNext(1L);
            }
        }

		public RunBug(int bugId) {
			_bugId = bugId;
			_remainRacingCount = _BUG_INFO[_bugId][0];
		}

		private boolean is_down(){
			boolean result = _is_downs[_bugId];
			_is_downs[_bugId] = false;
			return result;
		}
		
		@Override
		public void run() {
			if (!clockRegistered || _epoch != clockEpoch) return;
			try {
				switch (_status) {
				case 0: {
					if (_remainRacingCount == 0) {
						_remainRacingCount = _BUG_INFO[_bugId][1];
						_status = 1;
                        scheduleNext(1L);
                        break;
					} else {
						if(is_down() || (IS_AUTO_TUMBLE && _rndGen.nextInt(MAX_TUMBLE_PROBABILITY) < 1 && _rndGen.nextInt(100) > (int) (_winRate[_bugId]))){
/*						if () {*/
							SC_AttackPacket.broadcast(_littleBugBear[_bugId], _littleBugBear[_bugId].getId(), 30);
							scheduleNext(2000 - (int) (_winRate[_bugId]) * 5);
						} else {
							_littleBugBear[_bugId].setDirectionMove(6);
							--_remainRacingCount;
							scheduleNext(_time[_bugId]);
						}
						break;
					}
				}
				case 1: {
					if (_remainRacingCount == 0) {
						_remainRacingCount = _BUG_INFO[_bugId][2];
						_status = 2;
                        scheduleNext(1L);
                        break;
					} else {
						if(is_down() || (IS_AUTO_TUMBLE && _rndGen.nextInt(MAX_TUMBLE_PROBABILITY) < 2 && _rndGen.nextInt(100) > (int) (_winRate[_bugId]))){
//						if () {
							SC_AttackPacket.broadcast(_littleBugBear[_bugId], _littleBugBear[_bugId].getId(), 30);
							// GeneralThreadPool.getInstance().schedule(this,
							// 3500 - (int)(_winRate[_bugId]) * 5);
							scheduleNext(2000 - (int) (_winRate[_bugId]) * 5);
						} else {
							_littleBugBear[_bugId].setDirectionMove(7);
							--_remainRacingCount;

							scheduleNext(_time[_bugId]);
						}
						break;
					}
				}
				case 2: {
					if (_remainRacingCount == 0) {
						_remainRacingCount = _BUG_INFO[_bugId][3];
						_status = 3;
                        scheduleNext(1L);
                        break;
					} else {
						if(is_down() || (IS_AUTO_TUMBLE && _rndGen.nextInt(MAX_TUMBLE_PROBABILITY) < 2 && _rndGen.nextInt(100) > (int) (_winRate[_bugId]))){
//						if () {
							SC_AttackPacket.broadcast(_littleBugBear[_bugId], _littleBugBear[_bugId].getId(), 30);
							scheduleNext(2000 - (int) (_winRate[_bugId]) * 5);
						} else {
							_littleBugBear[_bugId].setDirectionMove(0);
							--_remainRacingCount;

							scheduleNext(_time[_bugId]);
						}
						break;
					}
				}
				case 3: {
					if (_remainRacingCount == 0) {
						_status = 4;
                        scheduleNext(1L);
                        break;
					} else {
						if(is_down() || (IS_AUTO_TUMBLE && _rndGen.nextInt(MAX_TUMBLE_PROBABILITY) < 2 && _rndGen.nextInt(100) > (int) (_winRate[_bugId]))){
//						if () {
							SC_AttackPacket.broadcast(_littleBugBear[_bugId], _littleBugBear[_bugId].getId(), 30);
							scheduleNext(2000 - (int) (_winRate[_bugId]) * 5);
						} else {
							_littleBugBear[_bugId].setDirectionMove(1);
							--_remainRacingCount;

							scheduleNext(_time[_bugId]);
						}
						break;
					}
				}
				case 4: {
					if (_littleBugBear[_bugId].getX() == 33527) {
						goalIn(_bugId);
					} else if(is_down() || (IS_AUTO_TUMBLE && _littleBugBear[_bugId].getX() < 33522 && _rndGen.nextInt(MAX_TUMBLE_PROBABILITY) < 2 && _rndGen.nextInt(100) > (int) (_winRate[_bugId]))){
						SC_AttackPacket.broadcast(_littleBugBear[_bugId], _littleBugBear[_bugId].getId(), 30);
						scheduleNext(2000 - (int) (_winRate[_bugId]) * 5);
					} else {
						_littleBugBear[_bugId].setDirectionMove(2);
						--_remainRacingCount;

						scheduleNext(_time[_bugId]);
					}
					break;
				}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	public int getTotalTicketCount() {
		int total = 0;
		for (int row = 0; row < 5; row++) {
			total += this._ticketCount[row];
		}
		return total;
	}

	public int getBugState() {
		return this._bugRaceState;
	}

	public void setBugState(int state) {
		this._bugRaceState = state;
	}

	public int getRaceCount() {
		return this._raceCount;
	}

	public void setRaceCount(int cnt) {
		this._raceCount = cnt;
	}

	/* 버경 추가 */
	public synchronized void AddTicket(L1RaceTicket race) {
		_race.put(race.getItemId(), race);
		ItemTable.getInstance().getAllTemplates().put(race.getItemId(), race);
	}

	public int GetIssuedTicket() {
		return _race.size();
	}
	
	static class BugStruct{
		public BugStruct(int id, int gfx, String name){
			this.id = id;
			this.gfx = gfx;
			this.name = name;
		}
		public int id;
		public int gfx;
		public String name;
	}
}
