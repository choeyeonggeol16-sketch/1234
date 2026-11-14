package l1j.server.server.model.Attendance;

import java.util.Calendar;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;
import l1j.server.server.packets.Protocol.Packet.SC_ATTENDANCE_USER_DATA_EXTEND;

public class AttendanceScheduler implements TimeListener{
	private static final int UPDATE_SECOND = 1;
	public static final int ATTEN_SHD_RESULT_ERROR = -1;
	public static final int ATTEN_SHD_RESULT_NONUPDATE = 0;
	public static final int ATTEN_SHD_RESULT_ONUPDATE = 1;
	
	private static AttendanceScheduler _instance;
	public static AttendanceScheduler getInstance(){
		if(_instance == null)
			_instance = new AttendanceScheduler();
		return _instance;
	}
	
	private static final Logger LOG = Logger.getLogger(AttendanceScheduler.class.getName());
	private final Runnable _updateTask = new MJAttendanceConsumer();
	private int _accumulate_second;
	// Counts are aggregated across scheduler saturation instead of accumulating
	// one Runnable per second. Counters and admission gate use this monitor.
	private int _pendingSeconds;
	private int _pendingSecondsAfterDay;
	private boolean _pendingDay;
	private boolean _updateQueued;
	private volatile boolean _isrun;
	private AttendanceScheduler() { }


	public void run(){
		_isrun = true;
		Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
		_accumulate_second += (cal.get(Calendar.HOUR_OF_DAY) * 3600);
		_accumulate_second += (cal.get(Calendar.MINUTE) * 60);
		_accumulate_second += (cal.get(Calendar.SECOND));
		_accumulate_second %= AttendanceLoadManager.ATTEN_RESET_PERIOD_SECOND;
		RealTimeClock.getInstance().addListener(this, Calendar.SECOND);
	}
	
	public void dispose(){
		_isrun = false;
		RealTimeClock.getInstance().removeListener(this,  Calendar.SECOND);
		synchronized (this) {
			_pendingSeconds = 0;
			_pendingSecondsAfterDay = 0;
			_pendingDay = false;
		}
	}
	
	@Override
	public void onMonthChanged(BaseTime time) {
	}

	@Override
	public void onDayChanged(BaseTime time) {
	}

	@Override
	public void onHourChanged(BaseTime time) {
	}

	@Override
	public void onMinuteChanged(BaseTime time) {
	}

	@Override
	public void onSecondChanged(BaseTime time) {
		boolean submit = false;
		synchronized (this) {
			if (!_isrun) return;
			_accumulate_second += UPDATE_SECOND;
			if (_accumulate_second >= AttendanceLoadManager.ATTEN_RESET_PERIOD_SECOND) {
				_accumulate_second = 0;
				_pendingDay = true;
			} else if (_pendingDay) {
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
			ThreadPool.getInstance().execute(_updateTask);
		} catch (RejectedExecutionException rejected) {
			synchronized (this) { _updateQueued = false; }
			LOG.log(Level.WARNING, "Attendance update admission rejected; retry next second", rejected);
		}
	}

	class MJAttendanceConsumer implements Runnable {
		@Override
		public void run() {
			final int before, after;
			final boolean day;
			synchronized (AttendanceScheduler.this) {
				if (!_isrun) { _updateQueued = false; return; }
				before = _pendingSeconds;
				after = _pendingSecondsAfterDay;
				day = _pendingDay;
				_pendingSeconds = 0;
				_pendingSecondsAfterDay = 0;
				_pendingDay = false;
			}
			try {
				// Apply pre-midnight ticks BEFORE the reset, and post-midnight
				// ticks AFTER it. No worker remains blocked between clock ticks.
				if (before > 0) doUpdateSecond(before);
				if (day) {
					AttendanceLoadManager.updateServerStartupInfo();
					doUpdateDay();
				}
				if (after > 0) doUpdateSecond(after);
			} catch (Exception error) {
				LOG.log(Level.WARNING, "Attendance update failed", error);
			} finally {
				synchronized (AttendanceScheduler.this) { _updateQueued = false; }
			}
		}
	}

	private void doUpdateDay(){
		L1World.getInstance().getAllPlayerStream()
		.filter((L1PcInstance pc) -> pc != null && pc.getAttendanceData() != null)
		.forEach((L1PcInstance pc) ->{
			try{
				SC_ATTENDANCE_USER_DATA_EXTEND userData = pc.getAttendanceData();
				userData.onUpdateDay(pc, UPDATE_SECOND);
			}catch(Exception e){
				e.printStackTrace();
			}
		});
	}
	
	private void doUpdateSecond(final int seconds){
		L1World.getInstance().getAllPlayerStream()
		.filter((L1PcInstance pc) -> pc != null && pc.getAttendanceData() != null)
		.forEach((L1PcInstance pc) ->{
			try{
				SC_ATTENDANCE_USER_DATA_EXTEND userData = pc.getAttendanceData();
				userData.onUpdateTime(pc, seconds);
			}catch(Exception e){
				e.printStackTrace();
			}
		});
	}
}
