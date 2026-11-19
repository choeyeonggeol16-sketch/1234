package l1j.server.MJTemplate.ObServer;

import l1j.server.MJTemplate.Interface.MJInstanceObserver;

public abstract class MJAbstractInstanceObserver implements MJInstanceObserver{
	private MJAbstractInstanceObservable<?> _observable;
	
	@Override
	public void setObservable(MJAbstractInstanceObservable<?> observable) {
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
	
	public MJAbstractInstanceObservable<?> getObservable(){
		return _observable;
	}
	
	@Override
	public abstract void dispose();
}
