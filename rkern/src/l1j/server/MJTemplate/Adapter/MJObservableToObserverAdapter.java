package l1j.server.MJTemplate.Adapter;

import l1j.server.MJTemplate.ObServer.MJAbstractInstanceObservable;
import l1j.server.MJTemplate.ObServer.MJAbstractInstanceObserver;

public class MJObservableToObserverAdapter extends MJAbstractInstanceObserver{
	private MJAbstractInstanceObservable<?> _observer;
	
	public MJObservableToObserverAdapter setObserver(MJAbstractInstanceObservable<?> observer){
		_observer = observer;
		return this;
	}
	
	public void onNotify(){
		notifyObservable();
	}
	
	public void onNotify(int reason){
		notifyObservable(reason);
	}
	
	@Override
	public void dispose() {
		if(_observer != null){
			_observer.disposes();
			_observer = null;
		}
	}

}
