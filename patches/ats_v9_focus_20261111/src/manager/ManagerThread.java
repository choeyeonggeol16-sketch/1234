package manager;

import l1j.server.server.GeneralThreadPool;

/** 30분 마다 서버로그창 저장 -> 지우기 **/
public class ManagerThread implements Runnable {
	
	private final int _runTime;
	
	public ManagerThread(int runTime) {
		_runTime = runTime;
	}
	public void start() {
		GeneralThreadPool.getInstance().scheduleAtFixedRate(ManagerThread.this, _runTime, _runTime);
	}

	@Override
	public void run() {
		try {
			SJManager.display.syncExec(new Runnable() {
				public void run() {
					try {
						SJManager.savelog();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}