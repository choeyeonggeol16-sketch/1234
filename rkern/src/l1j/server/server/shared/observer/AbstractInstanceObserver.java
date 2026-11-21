package l1j.server.server.shared.observer;

import l1j.server.server.shared.listener.InstanceObserver;

public abstract class AbstractInstanceObserver implements InstanceObserver{
	private AbstractInstanceObservable<?> _observable;
	
	@Override
	public void setObservable(AbstractInstanceObservable<?> observable) {
		_observable = observable;
	}

	@Override
	public void notifyObservable(){
		_observable.onNotify(this);
	}
	
	@Override
	public void notifyObservable(int reason){
		_observable.onNotify(this, reason);
	}
	
	public AbstractInstanceObservable<?> getObservable(){
		return _observable;
	}
	
	@Override
	public abstract void dispose();
}
