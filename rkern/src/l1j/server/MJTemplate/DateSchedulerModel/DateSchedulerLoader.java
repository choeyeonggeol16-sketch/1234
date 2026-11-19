package l1j.server.MJTemplate.DateSchedulerModel;

public class DateSchedulerLoader {
	private static DateSchedulerLoader _instance;
	public static DateSchedulerLoader getInstance(){
		if(_instance == null)
			_instance = new DateSchedulerLoader();
		return _instance;
	}
	
	private DateSchedulerLoader(){}
	
	public void run(){
		MinuteScheduler.getInstance().run();
	}
}
