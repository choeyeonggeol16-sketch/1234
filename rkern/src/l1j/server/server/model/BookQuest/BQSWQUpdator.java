package l1j.server.server.model.BookQuest;

import java.util.Calendar;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.gametime.CDateTime;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.gametime.GameTimeListener;

public class BQSWQUpdator implements GameTimeListener{
	private static BQSWQUpdator _instance;
	public static BQSWQUpdator getInstance(){
		if(_instance == null)
			_instance = new BQSWQUpdator();
		return _instance;
	}
	
	private static final Logger LOG = Logger.getLogger(BQSWQUpdator.class.getName());
	private boolean _pendingUpdate;
	private boolean _updateQueued;
	private int							_accumulate_clock;
	private volatile boolean				_isrun;
	private int							_current_listener_type;
	private int							_update_times;
	private BQSWQUpdator(){
	}
	
	public void run(){
		_isrun = true;
		update_listener();
	}
	
	public void update_listener(){
		if(_current_listener_type > 0)
			CGameTime.removeRealListener(this, _current_listener_type);
			
		_current_listener_type = BQSLoadManager.BQS_UPDATE_TYPE;
		Calendar cal = CGameTime.getRealTimeCalendar();
		switch(BQSLoadManager.BQS_UPDATE_TYPE){
		case Calendar.MINUTE:
			_accumulate_clock = accumulate_minute(cal);
			_update_times = BQSLoadManager.BQS_UPDATE_HOURS * 60;
			break;
			
		case Calendar.SECOND:
			_accumulate_clock = accumulate_second(cal);
			_update_times = BQSLoadManager.BQS_UPDATE_HOURS * 3600;
			break;
			
		default:
			_accumulate_clock = accumulate_hour(cal);
			_update_times = BQSLoadManager.BQS_UPDATE_HOURS;
			break;
		}
		CGameTime.addRealListener(this, _current_listener_type);
	}
	
    public int accumulate_hour(Calendar cal) {
        return elapsedBuckets(cal, 3_600_000L);
    }

    public int accumulate_minute(Calendar cal) {
        return elapsedBuckets(cal, 60_000L);
    }

    public int accumulate_second(Calendar cal) {
        return elapsedBuckets(cal, 1_000L);
    }

    private int elapsedBuckets(Calendar cal, long unit) {
        long start = BQSLoadManager.BQS_UPDATE_CALENDAR.getTimeInMillis();
        long difference = Math.floorDiv(cal.getTimeInMillis(), unit) - Math.floorDiv(start, unit);
        return (int) Math.clamp(difference, 0, Integer.MAX_VALUE);
    }

	@Override
	public void onHourChanged(CDateTime time) { onClockTick(); }

	@Override
	public void onMinuteChanged(CDateTime time) { onClockTick(); }

	@Override
	public void onSecondChanged(CDateTime time) { onClockTick(); }

	private void onClockTick() {
		synchronized (this) {
			if (!_isrun) return;
			if (++_accumulate_clock >= _update_times) {
				_accumulate_clock = 0;
				_pendingUpdate = true;
			}
		}
		submitPending();
	}

	private void submitPending() {
		synchronized (this) {
			if (!_isrun || !_pendingUpdate || _updateQueued) return;
			_updateQueued = true;
		}
		try {
			CWorldWork.execute(new WeekQuestConsumer());
		} catch (RejectedExecutionException rejected) {
			synchronized (this) { _updateQueued = false; }
			LOG.log(Level.WARNING, "Book quest update admission rejected; retry next clock tick", rejected);
		}
	}

	class WeekQuestConsumer implements Runnable {
		@Override
		public void run() {
			try {
				synchronized (BQSWQUpdator.this) {
					if (!_isrun || !_pendingUpdate) return;
					_pendingUpdate = false;
				}
				Calendar cal = CGameTime.getRealTimeCalendar();
				System.out.println(String.format("[Book System Update...!] %4d-%2d%2d %2d:%2d:%2d",
						cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
						cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND)));
				BQSLoadManager.BQS_IS_ONUPDATE_BOOKS = false;
				try {
					BQSLoadManager.updateBqsUpdateCalendar();
				} catch (Exception failure) {
					// Keep the pending event for a subsequent clock retry.
					synchronized (BQSWQUpdator.this) { _pendingUpdate = true; }
					LOG.log(Level.WARNING, "Book quest update failed", failure);
				} finally {
					BQSLoadManager.BQS_IS_ONUPDATE_BOOKS = true;
				}
			} finally {
				synchronized (BQSWQUpdator.this) { _updateQueued = false; }
				// Pending work is retried on the next clock tick; do not
				// busy-loop if the database remains unavailable.
			}
		}
	}
}
