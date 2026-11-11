package l1j.server.Template.Chain;

import java.util.ArrayList;

public abstract class MJAbstractActionChain<T> {
	protected ArrayList<T> m_handlers;
	protected Object m_lock;
	protected MJAbstractActionChain(){
		m_lock = new Object();
		m_handlers = new ArrayList<T>();
	}
	
	public void add_handler(T handler){
		synchronized(m_lock){
			m_handlers.add(handler);
		}
	}
	
	public synchronized void remove_handler(T handler){
		synchronized(m_lock){
			ArrayList<T> changed = new ArrayList<T>(m_handlers);
			changed.remove(handler);
			m_handlers = changed;
		}
	}
}
