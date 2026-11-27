package l1j.server.server.shared.observer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.shared.listener.InstanceObserver;

public final class CopyMapObservable extends AbstractInstanceObservable<Short> {
    public static final int RESET_X = 33430;
    public static final int RESET_Y = 32823;
    public static final short RESET_MAPID = 4;
    private static final int FIRST = 30000;
    private static final int LAST = 30999;
    private static final Logger LOG = Logger.getLogger(CopyMapObservable.class.getName());
    private static final CopyMapObservable INSTANCE = new CopyMapObservable();
    private final CInstantZoneManager<AbstractCopyMapObserver> rooms = new CInstantZoneManager<>(LAST - FIRST + 1);
    private volatile boolean stopped;

    private CopyMapObservable() {}
    public static CopyMapObservable getInstance() { return INSTANCE; }

    @Override public Short register(Short template, InstanceObserver value) {
        if (stopped || template == null || !(value instanceof AbstractCopyMapObserver observer)) return null;
        final var zone = rooms.tryOpen(template, -1, FIRST, LAST);
        if (zone == null) return null;
        boolean keep = false;
        try {
            synchronized (this) {
                if (stopped) return null;
                zone.setExit(33055, 32748, 4);
                observer.attach(this, template, zone);
                zone.bind(observer);
                keep = true;
            }
            return (short) zone.mapId();
        } finally {
            if (!keep) zone.close(() -> {});
        }
    }

    @Override public InstanceObserver get(Short id) { return id == null ? null : rooms.get(id); }

    @Override public InstanceObserver remove(Short id) {
        final InstanceObserver observer = get(id);
        if (observer != null) observer.notifyObservable();
        return observer;
    }

    @Override public Collection<InstanceObserver> values() { return new ArrayList<>(rooms.values()); }
    @Override public Collection<Short> keys() { return rooms.values().stream().map(o -> o._copyMapId).toList(); }

    @Override public void onNotify(InstanceObserver observer, int reason) {
        if (rooms.get(reason) == observer) observer.dispose();
    }

    @Override public void disposes() {
        final List<AbstractCopyMapObserver> active;
        synchronized (this) {
            stopped = true;
            active = rooms.values();
        }
        for (var observer : active) {
            try {
                try { observer.notifyObservable(); }
                finally { observer.closeImmediately(); }
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Copy map shutdown failed: " + observer._copyMapId, failure);
            }
        }
    }

    public short idenMap(short mapId) {
        final var observer = rooms.get(mapId);
        return observer == null ? mapId : observer._sourceMapId;
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
