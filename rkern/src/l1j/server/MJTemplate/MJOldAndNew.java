package l1j.server.MJTemplate;

public class MJOldAndNew<T> {
	
	private T _old;
	private T _new;
	
	public MJOldAndNew(T oldval, T newval){
		_old = oldval;
		_new = newval;
	}
	
	public T getOld(){
		return _old;
	}
	
	public void setOld(T o){
		_old = o;
	}
	
	public T getNew(){
		return _new;
	}
	
	public void setNew(T n){
		_new = n;
	}
	
	public void update(T n){
		_old = _new;
		_new = n;
	}
	
	public boolean isChanged(){
		return _old != _new;
	}
}
