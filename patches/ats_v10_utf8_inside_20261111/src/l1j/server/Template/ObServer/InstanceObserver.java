package l1j.server.Template.ObServer;

public interface InstanceObserver {
	public void setObservable(AbstractInstanceObservable<?> observable);
	public void notifyObservable();
	public void notifyObservable(int reason);
	public void dispose();
}
