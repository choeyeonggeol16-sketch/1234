package l1j.server.server.shared.observer;

import java.util.ArrayDeque;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.shared.listener.InstanceObserver;

public class CopyMapObservable extends AbstractInstanceObservable<Short>{
	private static final int MAXIMUM_CAPACITY = 1000;
	public static final int RESET_X = 33430; //베이스 마을
	public static final int RESET_Y = 32823; //
	public static final short RESET_MAPID = 4;
	
	private static CopyMapObservable _instance;
	public static CopyMapObservable getInstance(){
		if(_instance == null)
			_instance = new CopyMapObservable();
		return _instance;
	}
	
	private ArrayDeque<Short> _mapQ;
	private CopyMapObservable(){
		initializeObservable(MAXIMUM_CAPACITY);
		_mapQ = new ArrayDeque<Short>(MAXIMUM_CAPACITY);
		for(int i = MAXIMUM_CAPACITY - 1; i>= 0; --i)
			_mapQ.push((short)(i + 30000));
	}
	
	private Short popId(){
		synchronized(_mapQ){
			return _mapQ.poll();
		}
	}
	
	@Override
	public InstanceObserver remove(Short key){
        InstanceObserver observer = _observers.remove(key);
        if (observer != null) {
            try { observer.dispose(); }
            finally { pushId(key); }
        }
        return observer;
	}
	
	private void pushId(Short id){
		synchronized(_mapQ){
			_mapQ.push(id);
		}
	}
	
	@Override
	public Short register(Short key, InstanceObserver osv){
		if(!(osv instanceof AbstractCopyMapObserver)){
			return null;
		}
		AbstractCopyMapObserver observer = (AbstractCopyMapObserver)osv;
		Short next = popId();
        if (next == null) return null;
        try {
            L1Map m = observer.initializeMap(key, next);
            if (m == null) {
                pushId(next);
                return null;
            }
            super.register(next, observer);
            return next;
        } catch (RuntimeException e) {
            // initializeMap failed before registration; never expose an orphan ID.
            if (!_observers.containsKey(next)) pushId(next);
            throw e;
        }
	}
	
	@Override
	public void onNotify(InstanceObserver observer){
	}
	
	@Override
	public void onNotify(InstanceObserver observer, int reason){
        short id = (short) reason;
        if (_observers.remove(id, observer)) {
            try { observer.dispose(); }
            finally { pushId(id); }
        }
	}
	
	public short idenMap(short mapId){
		InstanceObserver osv = get(mapId);
		if(osv == null)
			return mapId;
		
		AbstractCopyMapObserver observer = (AbstractCopyMapObserver)osv;
		return observer._sourceMapId;
	}
	
	public void resetTeleport(L1PcInstance pc){
		if(pc.getMapId() >= 30000){
			pc.do_simple_teleport(RESET_X, RESET_Y, RESET_MAPID);
		}
	}
	
	public void resetPosition(L1PcInstance pc){
		if(pc.getMapId() >= 30000){
			resetAlwaysPositon(pc);
		}
	}
	
	public void resetAlwaysPositon(L1PcInstance pc){
		pc.setX(RESET_X);
		pc.setY(RESET_Y);
		pc.setMap(RESET_MAPID);
	}
}
