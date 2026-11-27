package l1j.server.server.model.character.combat.statistics.chart;
import java.util.Calendar;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.CDateTime;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.gametime.CTimeListener;

public class KdaChartScheduler implements CTimeListener {
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
		CGameTime.removeRealListener(this, Calendar.SECOND);
		if(_updator != null)
			_updator.dispose();
	}
	
	public void onLoginUser(L1PcInstance pc){
		_updator.onLoginUser(pc);
	}
	
	public void run(){
		CGameTime.addRealListener(this, Calendar.SECOND);
	}
	




	@Override
	public void onSecondChanged(CDateTime time) {
		Calendar cal = time.getCalendar();
		if(_updator.isBefore(cal))
			_updator.update();
	}

}
