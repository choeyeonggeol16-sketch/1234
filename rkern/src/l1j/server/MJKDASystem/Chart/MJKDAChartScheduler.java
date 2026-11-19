package l1j.server.MJKDASystem.Chart;
/**********************************
 * 
 * MJ Kill Death Assist Chart System scheduler.
 * made by mjsoft, 2017.
 *  
 **********************************/
import java.util.Calendar;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;

public class MJKDAChartScheduler implements TimeListener {
	private static MJKDAChartScheduler _instance;
	public static MJKDAChartScheduler getInstance(){
		if(_instance == null)
			_instance = new MJKDAChartScheduler();
		return _instance;
	}
	
	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}
	
	public static boolean isLoaded(){
		return _instance != null;
	}
	
	private MJKDAChartUpdator _updator;
	private MJKDAChartScheduler(){
		_updator = new MJKDAChartUpdator();
	}
	
	private void dispose(){
		RealTimeClock.getInstance().removeListener(this, Calendar.SECOND);
		if(_updator != null)
			_updator.dispose();
	}
	
	public void onLoginUser(L1PcInstance pc){
		_updator.onLoginUser(pc);
	}
	
	public void run(){
		RealTimeClock.getInstance().addListener(this, Calendar.SECOND);
	}
	
	@Override
	public void onMonthChanged(BaseTime time) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onDayChanged(BaseTime time) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onHourChanged(BaseTime time) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onMinuteChanged(BaseTime time) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSecondChanged(BaseTime time) {
		Calendar cal = time.getCalendar();
		if(_updator.isBefore(cal))
			_updator.update();
	}

}
