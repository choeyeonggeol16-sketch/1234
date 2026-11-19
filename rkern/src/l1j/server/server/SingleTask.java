package l1j.server.server;

public abstract class SingleTask implements Runnable {

	public abstract void execute();

	@Override
	public final void run() {
		if (!_active) {
			return;
		}
		_executed = true;
		execute();
	}

	public void cancel() {
		_active = false;
	}

	public boolean isActive() {
		return _active;
	}

	public boolean isExecuted() {
		return _executed;
	}

	private volatile boolean _active = true;
	private volatile boolean _executed = false;
}
