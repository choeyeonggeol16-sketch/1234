package l1j.server.MJTemplate.ObServer;

import l1j.server.MJTemplate.Interface.MJInstanceObserver;
import l1j.server.MJTemplate.MapCleaner.MJMapCleaner;
import l1j.server.MJTemplate.MapCleaner.MJMapCleanerFilterFactory;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

public abstract class MJAbstractCopyMapObserver implements MJInstanceObserver{
	
	private MJAbstractInstanceObservable<?> _observable;
	protected short							_sourceMapId;
	protected short							_copyMapId;
	protected boolean						_isDispose;
	public L1Map initializeMap(short sourceMapId, short copyMapId){
		_sourceMapId 	= sourceMapId;
		_copyMapId		= copyMapId;
		return L1WorldMap.getInstance().cloneMapAndGet(sourceMapId, copyMapId);
	}
	
	@Override
	public void setObservable(MJAbstractInstanceObservable<?> observable) {
		_observable = observable;
	}

	@Override
	public void notifyObservable() {
		if(!_isDispose){
			_isDispose = true;
			_observable.onNotify(this, _copyMapId);
		}
	}

	@Override
	public void notifyObservable(int reason) {	
		_observable.onNotify(this, reason);
	}

	public MJAbstractInstanceObservable<?> getObservable(){
		return _observable;
	}
	
	@Override
	public void dispose(){
		MJMapCleaner.cleanup(_copyMapId, MJMapCleanerFilterFactory.createDefaultFilter());
		L1WorldMap.getInstance().removeMap(_copyMapId);
	}
}
