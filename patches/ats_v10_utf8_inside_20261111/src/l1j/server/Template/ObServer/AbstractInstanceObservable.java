package l1j.server.Template.ObServer;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractInstanceObservable<T>{
	
	protected ConcurrentHashMap<T, InstanceObserver> _observers;
	public void initializeObservable(int capacity) {
		_observers = new ConcurrentHashMap<T, InstanceObserver>(capacity);
	}
	
	public T register(T key, InstanceObserver osv) {
		InstanceObserver old = _observers.get(key);
		_observers.put(key, osv);
		osv.setObservable(this);
		
		if(old != null) {
			old.dispose();
			old = null;
		}
		return key;
	}
	
	public InstanceObserver get(T key) {
		return _observers.get(key);
	}
	
	public InstanceObserver remove(T key) {
		InstanceObserver observer = _observers.remove(key);
		if(observer != null)
			observer.dispose();
		return observer;
	}
	
	public Collection<InstanceObserver> values() {
		return _observers.values();
	}
	
	public Collection<T> keys(){
		return _observers.keySet();
	}
	
	public void disposes(){
		for(InstanceObserver ovs : _observers.values()) {
			ovs.dispose();
		}
		_observers.clear();	
		_observers = null;
	}
	
	public void onNotify(InstanceObserver observer){}
	public void onNotify(InstanceObserver observer, int reason){}
}

