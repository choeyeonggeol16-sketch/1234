package l1j.server.MJTemplate.ObServer;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.MJTemplate.Interface.MJInstanceObserver;

public abstract class MJAbstractInstanceObservable<T>{
	protected ConcurrentHashMap<T, MJInstanceObserver> _observers;
	public void initializeObservable(int capacity){
		_observers = new ConcurrentHashMap<T, MJInstanceObserver>(capacity);
	}
	
	public T register(T key, MJInstanceObserver osv){
		MJInstanceObserver old = _observers.get(key);
		_observers.put(key, osv);
		osv.setObservable(this);
		
		if(old != null){
			old.dispose();
			old = null;
		}
		return key;
	}
	
	public MJInstanceObserver get(T key){
		return _observers.get(key);
	}
	
	public MJInstanceObserver remove(T key){
		MJInstanceObserver observer = _observers.remove(key);
		if(observer != null)
			observer.dispose();
		return observer;
	}
	
	public Collection<MJInstanceObserver> values(){
		return _observers.values();
	}
	
	public Collection<T> keys(){
		return _observers.keySet();
	}
	
	public void disposes(){
		for(MJInstanceObserver ovs : _observers.values())
			ovs.dispose();
		_observers.clear();
		_observers = null;
	}
	
	public void onNotify(MJInstanceObserver observer){}
	public void onNotify(MJInstanceObserver observer, int reason){}
}
