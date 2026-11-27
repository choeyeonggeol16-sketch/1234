package l1j.server.server.model.Attendance;

import java.util.Calendar;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.gametime.CDateTime;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.gametime.CTimeListener;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_USER_DATA_EXTEND;

public class AttendanceScheduler implements CTimeListener{
	private static final int UPDATE_SECOND = 1;
	public static final int ATTEN_SHD_RESULT_ERROR = -1;
	public static final int ATTEN_SHD_RESULT_NONUPDATE = 0;
	public static final int ATTEN_SHD_RESULT_ONUPDATE = 1;
	
	private static final AttendanceScheduler INSTANCE = new AttendanceScheduler();
	public static AttendanceScheduler getInstance(){
		return INSTANCE;
	}
	
	private static final Logger LOG = Logger.getLogger(AttendanceScheduler.class.getName());
	private final Runnable _updateTask = new AttendanceConsumer();
	private int _accumulate_second;
	// Counts are aggregated across scheduler saturation instead of accumulating
	// one Runnable per second. Counters and admission gate use this monitor.
	private int _pendingSeconds;
	private int _pendingSecondsAfterDay;
	private long _pendingDayMillis;
	private boolean _updateQueued;
	private volatile boolean _isrun;
	private AttendanceScheduler() { }


	public synchronized void run() {
		if (_isrun) return;
		final Calendar cal = CGameTime.getRealTimeCalendar();
		final int period = AttendanceLoadManager.ATTEN_RESET_PERIOD_SECOND;
		if (period <= 0) throw new IllegalStateException("Invalid attendance reset period: " + period);
		_accumulate_second = (cal.get(Calendar.HOUR_OF_DAY) * 3600
				+ cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)) % period;
		queueOverdueDay(cal.getTimeInMillis());
		_isrun = true;
		CGameTime.addRealListener(this, Calendar.SECOND);
	}
	
	public synchronized void dispose() {
		if (!_isrun) return;
		_isrun = false;
		CGameTime.removeRealListener(this, Calendar.SECOND);
		_pendingSeconds = 0;
		_pendingSecondsAfterDay = 0;
		_pendingDayMillis = 0L;
	}

	private void queueOverdueDay(long now) {
		final long storedDay = AttendanceLoadManager.attendanceDayMillis();
		if (_pendingDayMillis == 0L && storedDay != 0L
				&& now - storedDay >= AttendanceLoadManager.ATTEN_RESET_PERIOD_SECOND * 1000L) {
			_pendingDayMillis = Math.floorDiv(now, 1000L) * 1000L;
		}
	}
	




	@Override
	public void onSecondChanged(CDateTime time) {
		boolean submit = false;
		synchronized (this) {
			if (!_isrun) return;
			_accumulate_second += UPDATE_SECOND;
			if (_accumulate_second >= AttendanceLoadManager.ATTEN_RESET_PERIOD_SECOND) {
				_accumulate_second = 0;
				_pendingDayMillis = time.getSeconds() * 1000L;
				_pendingSecondsAfterDay = 0;
			} else if (_pendingDayMillis != 0L) {
				if (_pendingSecondsAfterDay < Integer.MAX_VALUE) _pendingSecondsAfterDay++;
			} else {
				if (_pendingSeconds < Integer.MAX_VALUE) _pendingSeconds++;
			}
			if (!_updateQueued) {
				_updateQueued = true;
				submit = true;
			}
		}
		if (!submit) return;
		try {
			CWorldWork.execute(_updateTask);
		} catch (RejectedExecutionException rejected) {
			synchronized (this) { _updateQueued = false; }
			LOG.log(Level.WARNING, "Attendance update admission rejected; retry next second", rejected);
		}
	}

	class AttendanceConsumer implements Runnable {
		@Override
		public void run() {
			final int before;
			final long day;
			synchronized (AttendanceScheduler.this) {
				if (!_isrun) { _updateQueued = false; return; }
				before = _pendingSeconds;
				day = _pendingDayMillis;
				_pendingSeconds = 0;
			}
			try {
				// Apply pre-midnight ticks BEFORE the reset, and post-midnight
				// ticks AFTER it. No worker remains blocked between clock ticks.
				if (AttendanceLoadManager.attendanceDayMillis() == 0L) {
					AttendanceLoadManager.loadAttendanceStartupCalendar();
				}
				if (before > 0) updatePlayers(before, false);
				if (day != 0L) {
					// Leave the pending boundary published until SQL succeeds so
					// new ticks cannot be counted on the old side during a retry.
					if (AttendanceLoadManager.attendanceDayMillis() < day) {
						AttendanceLoadManager.updateServerStartupInfo(day);
					}
					final int after;
					synchronized (AttendanceScheduler.this) {
						if (!_isrun) return;
						if (_pendingDayMillis == day) {
							after = _pendingSecondsAfterDay;
							_pendingDayMillis = 0L;
							_pendingSecondsAfterDay = 0;
						} else {
							after = 0;
						}
					}
					updatePlayers(UPDATE_SECOND, true);
					if (after > 0) updatePlayers(after, false);
				}
			} catch (Exception error) {
				synchronized (AttendanceScheduler.this) {
					if (_isrun) queueOverdueDay(System.currentTimeMillis());
				}
				LOG.log(Level.WARNING, "Attendance update failed", error);
			} finally {
				synchronized (AttendanceScheduler.this) { _updateQueued = false; }
			}
		}
	}

	private void updatePlayers(int seconds, boolean rollover) {
		CWorld.getInstance().getAllPlayerStream().forEach(pc -> {
			if (pc == null || pc.isLogoutStarted() || pc.is무인상점() || pc.getOnlineStatus() != 1) return;
			final SC_ATTENDANCE_USER_DATA_EXTEND data = pc.getAttendanceData();
			if (data == null) return;
			try {
				if (rollover) data.onUpdateDay(pc, seconds);
				else data.onUpdateTime(pc, seconds);
			} catch (Exception error) {
				LOG.log(Level.WARNING, "Attendance player update failed: " + pc.getId(), error);
			}
		});
	}
}
