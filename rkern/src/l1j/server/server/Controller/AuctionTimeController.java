package l1j.server.server.Controller;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;

import l1j.server.Config;
import l1j.server.server.ThreadPool;
import l1j.server.server.datatables.AuctionBoardTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.storage.CharactersItemStorage;
import l1j.server.server.templates.L1AuctionBoard;
import l1j.server.server.templates.L1House;

public class AuctionTimeController implements Runnable, TimeListener {
    private static final Logger LOG = Logger.getLogger(AuctionTimeController.class.getName());
    private final AtomicBoolean inFlight = new AtomicBoolean();
	public static final int SLEEP_TIME = 60000; // 1분 (60초)

	private static volatile AuctionTimeController _instance;

	public static AuctionTimeController getInstance() {
		if (_instance == null) {
			synchronized (AuctionTimeController.class) {
				if (_instance == null) {
					_instance = new AuctionTimeController();
					RealTimeClock.getInstance().addListener(_instance, Calendar.MINUTE);
				}
			}
		}
		return _instance;
	}

	private AuctionTimeController() {}

	@Override
	public void run() {
		try {
			checkAuctionDeadline();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    @Override
    public void onMinuteChanged(BaseTime time) {
        // The shared clock only publishes a signal. SQL/economy operations
        // run on an existing instant worker, never on the clock thread.
        if (!inFlight.compareAndSet(false, true)) return;
        try {
            ThreadPool.getInstance().execute(() -> {
                try {
                    run();
                } finally {
                    inFlight.set(false);
                }
            });
        } catch (RejectedExecutionException rejected) {
            inFlight.set(false);
            // Next clock minute retries overdue rows. Never run JDBC on clock.
            LOG.log(Level.WARNING, "AuctionTimeController tick admission rejected", rejected);
        }
    }

    @Override public void onMonthChanged(BaseTime time) { }
    @Override public void onDayChanged(BaseTime time) { }
    @Override public void onHourChanged(BaseTime time) { }
    @Override public void onSecondChanged(BaseTime time) { }

	public Calendar getRealTime() {
		TimeZone tz = TimeZone.getTimeZone(Config.TIME_ZONE);
		return Calendar.getInstance(tz);
	}

	private void checkAuctionDeadline() {
		AuctionBoardTable boardTable = AuctionBoardTable.getInstance();
		if (boardTable == null || boardTable.getAuctionBoardTableList() == null) {
			return;
		}

		Calendar now = getRealTime();
		for (L1AuctionBoard board : boardTable.getAuctionBoardTableList()) {
			if (board.getDeadline() != null && board.getDeadline().before(now)) {
				endAuction(board);
			}
		}
	}

	private void endAuction(L1AuctionBoard board) {
        l1j.server.server.model.L1AuctionTransaction.expire(board);
    }

	/**
	 * 이전 소유자의 아지트 설정 제거
	 */
	private void deleteHouseInfo(int houseId) {
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (clan.getHouseId() == houseId) {
				clan.setHouseId(0);
				ClanTable.getInstance().updateClan(clan);
				break; // 해당 아지트 혈맹을 찾았으면 탐색 중단
			}
		}
	}

	/**
	 * 낙찰자의 아지트 설정
	 */
	private void setHouseInfo(int houseId, int bidderId) {
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			if (clan.getLeaderId() == bidderId) {
				clan.setHouseId(houseId);
				ClanTable.getInstance().updateClan(clan);
				break;
			}
		}
	}

	/**
	 * 아지트 경매 완료 처리 및 게시판 삭제
	 */
	private void deleteNote(int houseId) {
		L1House house = HouseTable.getInstance().getHouseTable(houseId);
		if (house != null) {
			house.setOnSale(false);
			Calendar cal = getRealTime();
			cal.add(Calendar.DATE, Config.HOUSE_TAX_INTERVAL);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			house.setTaxDeadline(cal);
			HouseTable.getInstance().updateHouse(house);
		}
		
		AuctionBoardTable.getInstance().deleteAuctionBoard(houseId);
	}
}