package l1j.server.server.shared.listener;

import l1j.server.server.shared.observer.AbstractInstanceObservable;

public interface InstanceObserver {
	public void setObservable(AbstractInstanceObservable<?> observable);
	public void notifyObservable();
	public void notifyObservable(int reason);
	public void dispose();
}
