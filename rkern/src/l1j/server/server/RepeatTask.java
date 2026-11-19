package l1j.server.server;

public abstract class RepeatTask implements Runnable {
	
	public RepeatTask( long interval) {
		_interval = interval;
		_active = true;
	}
	
	public long getInterval() {
		return _interval;
	}
	
    public abstract void execute();
	
	@Override
	public final void run() {
		if (!_active) {
			return;
		}		
		execute();
		
		if (_active && ThreadPool.getInstance().schedule(this, _interval) == null) {
			// Do not leave an unscheduled recurring effect marked live after
			// the bounded scheduler rejects its next tick.
			cancel();
			onScheduleRejected();
		}
	}
	
	protected void onScheduleRejected() {}

	public void cancel() {
		_active = false;
	}
	
	private volatile boolean _active;
	private long _interval;
}
