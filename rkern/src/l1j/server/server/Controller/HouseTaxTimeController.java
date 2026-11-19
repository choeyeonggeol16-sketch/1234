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
import l1j.server.server.templates.L1AuctionBoard;
import l1j.server.server.templates.L1House;

public class HouseTaxTimeController implements Runnable, TimeListener {
    private static final Logger LOG = Logger.getLogger(HouseTaxTimeController.class.getName());
    private final AtomicBoolean inFlight = new AtomicBoolean();
	public static final int SLEEP_TIME = 60000; // 1분 (60초)

	private static volatile HouseTaxTimeController _instance;

	public static HouseTaxTimeController getInstance() {
		if (_instance == null) {
			synchronized (HouseTaxTimeController.class) {
				if (_instance == null) {
					_instance = new HouseTaxTimeController();
					// 서버 가동 시 1분 주기 스케줄러 자동 등록
					RealTimeClock.getInstance().addListener(_instance, Calendar.MINUTE);
				}
			}
		}
		return _instance;
	}

	private HouseTaxTimeController() {}

	@Override
	public void run() {
		try {
			checkTaxDeadline();
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
            LOG.log(Level.WARNING, "HouseTaxTimeController tick admission rejected", rejected);
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

	private void checkTaxDeadline() {
		Calendar now = getRealTime();
		for (L1House house : HouseTable.getInstance().getHouseTableList()) {
			if (!house.isOnSale()) { // 경매 중이 아닌 아지트만 세금 체납 체크
				if (house.getTaxDeadline() != null && house.getTaxDeadline().before(now)) {
					sellHouse(house);
				}
			}
		}
	}

	private void sellHouse(L1House house) {
        l1j.server.server.model.L1AuctionTransaction.foreclose(house);
    }
}