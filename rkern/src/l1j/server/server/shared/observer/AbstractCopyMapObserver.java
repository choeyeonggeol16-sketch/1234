package l1j.server.server.shared.observer;

import java.util.Collection;
import java.util.List;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.world.cleanup.MapCleaner;
import l1j.server.server.model.world.cleanup.MapCleanerFilterFactory;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.shared.listener.InstanceObserver;

public abstract class AbstractCopyMapObserver implements InstanceObserver {
    private AbstractInstanceObservable<?> observable;
    private volatile CInstantZone<AbstractCopyMapObserver> zone;
    protected short _sourceMapId;
    protected short _copyMapId;

    final void attach(CopyMapObservable owner, short source, CInstantZone<AbstractCopyMapObserver> lease) {
        if (zone != null) throw new IllegalStateException("Observer already owns a zone");
        _sourceMapId = source;
        _copyMapId = (short) lease.mapId();
        setObservable(owner);
        zone = lease;
    }

    @Override public void setObservable(AbstractInstanceObservable<?> owner) { observable = owner; }
    public AbstractInstanceObservable<?> getObservable() { return observable; }

    @Override public void notifyObservable() { notifyObservable(_copyMapId); }
    @Override public void notifyObservable(int reason) {
        if (observable != null) observable.onNotify(this, reason);
    }

    protected final L1Map copyMap() {
        final var current = zone;
        return current == null ? null : current.map();
    }

    protected final Collection<L1PcInstance> visibleZonePlayers() {
        final L1Map map = copyMap();
        return map == null ? List.of() : CWorld.getInstance().getVisiblePlayers(map);
    }

    protected final boolean enterZone(Runnable action) {
        final var current = zone;
        return current != null && current.enter(action);
    }

    protected final void closeCopyMap() {
        final var current = zone;
        if (current != null) {
            current.close(() -> MapCleaner.cleanupNow(current.map(), MapCleanerFilterFactory.createDefaultFilter()));
        }
    }

    protected void closeImmediately() { closeCopyMap(); }

    @Override public void dispose() { closeCopyMap(); }
}
