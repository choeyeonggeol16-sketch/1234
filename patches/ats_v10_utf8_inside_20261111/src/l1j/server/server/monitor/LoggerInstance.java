package l1j.server.server.monitor;

import l1j.server.server.GeneralThreadPool;

public class LoggerInstance extends FileLogger implements Runnable {
	
	public static final int SLEEP_TIME = 60000;
	private static LoggerInstance _instance = null;

	private LoggerInstance() {
	};

	public static LoggerInstance getInstance() {
		if (_instance == null) {
			_instance = new LoggerInstance();
			GeneralThreadPool.getInstance().execute(_instance);
		}
		return _instance;
	}

	public void run() {
		try{
			flush();
		}catch(Exception e){
			
		}finally{
			GeneralThreadPool.getInstance().schedule(this, SLEEP_TIME);
		}
	}
}
