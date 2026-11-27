package l1j.server.server.model.character.combat.statistics.chart;
import java.util.Calendar;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.L2DateTime;
import l1j.server.server.model.gametime.CL2Time;
import l1j.server.server.model.gametime.L2TimeListener;

public class KdaChartScheduler implements L2TimeListener {
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
		CL2Time.removeRealListener(this, Calendar.SECOND);
		if(_updator != null)
			_updator.dispose();
	}
	
	public void onLoginUser(L1PcInstance pc){
		_updator.onLoginUser(pc);
	}
	
	public void run(){
		CL2Time.addRealListener(this, Calendar.SECOND);
	}
	




	@Override
	public void onSecondChanged(L2DateTime time) {
		Calendar cal = time.getCalendar();
		if(_updator.isBefore(cal))
			_updator.update();
	}

}
