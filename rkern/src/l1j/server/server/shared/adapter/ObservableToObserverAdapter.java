package l1j.server.server.shared.adapter;

import l1j.server.server.shared.observer.AbstractInstanceObservable;
import l1j.server.server.shared.observer.AbstractInstanceObserver;

public class ObservableToObserverAdapter extends AbstractInstanceObserver{
	private AbstractInstanceObservable<?> _observer;
	
	public ObservableToObserverAdapter setObserver(AbstractInstanceObservable<?> observer){
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
