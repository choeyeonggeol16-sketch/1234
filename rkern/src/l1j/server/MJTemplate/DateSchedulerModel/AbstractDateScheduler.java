package l1j.server.MJTemplate.DateSchedulerModel;

import java.util.concurrent.CopyOnWriteArrayList;

import l1j.server.MJTemplate.DateSchedulerModel.Acceptor.AbstractDateAcceptor;

public abstract class AbstractDateScheduler{
	protected CopyOnWriteArrayList<AbstractDateAcceptor> _acceptors;
	protected AbstractDateScheduler(){
		_acceptors = new CopyOnWriteArrayList<AbstractDateAcceptor>();
	}
	
	public void register(AbstractDateAcceptor element){
		if(!_acceptors.contains(element))
			_acceptors.add(element);
	}
	
	public void removed(AbstractDateAcceptor element){
		_acceptors.remove(element);
	}
}
