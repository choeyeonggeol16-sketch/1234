package l1j.server.server.model.character.combat.statistics.chart;
/**********************************
 * 
 * server Kill Death Assist Chart System scheduler.
 * made by mjsoft, 2017.
 *  
 **********************************/
import java.util.Calendar;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;

public class KdaChartScheduler implements TimeListener {
	private static KdaChartScheduler _instance;
	public static KdaChartScheduler getInstance(){
		if(_instance == null)
			_instance = new KdaChartScheduler();
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
	
	private KdaChartUpdator _updator;
	private KdaChartScheduler(){
		_updator = new KdaChartUpdator();
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
