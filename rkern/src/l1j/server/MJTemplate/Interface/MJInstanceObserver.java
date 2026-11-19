package l1j.server.MJTemplate.Interface;

import l1j.server.MJTemplate.ObServer.MJAbstractInstanceObservable;

public interface MJInstanceObserver {
	public void setObservable(MJAbstractInstanceObservable<?> observable);
	public void notifyObservable();
	public void notifyObservable(int reason);
	public void dispose();
}
