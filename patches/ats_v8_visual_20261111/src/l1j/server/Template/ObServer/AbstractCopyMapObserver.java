package l1j.server.Template.ObServer;
import l1j.server.Template.MapCleaner.MapCleaner;
import l1j.server.Template.MapCleaner.MapCleanerFilterFactory;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;


public abstract class AbstractCopyMapObserver implements InstanceObserver {
	private AbstractInstanceObservable<?> _observable;
	protected short							_sourceMapId;
	protected short							_copyMapId;
	protected boolean						_isDispose;
	public L1Map initializeMap(short sourceMapId, short copyMapId) {
		_sourceMapId 	= sourceMapId;
		_copyMapId		= copyMapId;
		return L1WorldMap.getInstance().cloneMapAndGet(sourceMapId, copyMapId);
	}
	
	@Override
	public void setObservable(AbstractInstanceObservable<?> observable) {
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

	public AbstractInstanceObservable<?> getObservable(){
		return _observable;
	}
	
	@Override
	public void dispose() {
		MapCleaner.cleanup(_copyMapId, MapCleanerFilterFactory.createDefaultFilter());
		L1WorldMap.getInstance().removeMap(_copyMapId);
	}
}
