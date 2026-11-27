package l1j.server.server.model.world.instance.raid;

import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.model.world.instance.raid.loader.RaidLoadManager;

/** Dragon raid scripts run inside private instant zones. */
public final class RaidSpace {
    private static final Logger LOG = Logger.getLogger(RaidSpace.class.getName());
    private static final RaidSpace INSTANCE = new RaidSpace();
    private final CInstantZoneManager<RaidObject> rooms = new CInstantZoneManager<>(Integer.MAX_VALUE);

    private long revision;

    private RaidSpace() {}

    public static RaidSpace getInstance() { return INSTANCE; }
    public static void release() { INSTANCE.clear(); }
    public static void reload() { INSTANCE.clear(); }

    public void startInstance(RaidObject raid) {
        startInstance(raid, () -> true);
    }

    public boolean startInstance(RaidObject raid, BooleanSupplier accept) {
        final long openingRevision;
        synchronized (this) { openingRevision = revision; }
        final int first = RaidLoadManager.MRS_COPYMAP_START_ID;
        final int count = RaidLoadManager.MRS_COPYMAP_SIZE;
        final CInstantZone<RaidObject> zone = count <= 0 ? null
                : rooms.tryOpen(raid.getBaseMapId(), -1, first, Math.addExact(first, count - 1));
        if (zone == null) {
            raid.notifySizeOver();
            return false;
        }
        final boolean replaced;
        synchronized (this) {
            raid.attach(zone);
            zone.bind(raid);
            replaced = openingRevision != revision;
        }
        if (replaced) {
            raid.failRaid();
            return false;
        }
        try {
            if (raid.open(accept)) return true;
        } catch (RuntimeException failure) {
            LOG.log(Level.SEVERE, "Opening dragon raid on map " + zone.mapId(), failure);
        }
        return false;
    }

    public void releaseInstance(RaidObject raid) {
        if (raid != null) raid.failRaid();
    }

    public boolean isInInstance(L1PcInstance pc) { return isInInstance(pc.getMapId()); }

    public boolean isInInstance(int id) {
        int first = RaidLoadManager.MRS_COPYMAP_START_ID;
        return (id >= first && (long) id - first < RaidLoadManager.MRS_COPYMAP_SIZE)
                || rooms.get(id) != null;
    }

    public void getBackPc(L1PcInstance pc) {
        if (!isInInstance(pc)) return;
        RaidObject raid = rooms.get(pc.getMapId());
        RaidType type = raid == null ? null : raid.getRaidType();
        pc.setX(type == null ? RaidLoadManager.MRS_ERRBACK_X : type.getOutX());
        pc.setY(type == null ? RaidLoadManager.MRS_ERRBACK_Y : type.getOutY());
        pc.setMap((short) (type == null ? RaidLoadManager.MRS_ERRBACK_MAPID : type.getOutMapId()));
    }

    public int getIdenMap(int id) {
        RaidObject raid = rooms.get(id);
        return raid == null ? id : raid.getBaseMapId();
    }

    public void clear() {
        List<RaidObject> snapshot;
        synchronized (this) {
            revision++;
            snapshot = rooms.values();
        }
        for (RaidObject raid : snapshot) raid.failRaid();
    }

    public RaidObject getOpenObject(int id) { return rooms.get(id); }
    public int getOpenSize() { return rooms.values().size(); }
    public Collection<RaidObject> getOpens() { return rooms.values(); }
    public Collection<Integer> getOpensMaps() {
        return rooms.values().stream().map(RaidObject::getCopyMapId).toList();
    }

    public static void startEffect(L1NpcInstance caster, Runnable effect) {
        RaidObject raid = INSTANCE.rooms.get(caster.getMapId());
        if (raid != null) raid.startEffect(caster, effect);
    }
}
