package l1j.server.server.model.world.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1GroundInventory;
import l1j.server.server.model.world.cleanup.MapCleaner;
import l1j.server.server.model.world.cleanup.MapCleanerFilterFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;

/** Allocates one globally unique map ID per dungeon run, including runs on original terrain. */
public final class CInstantZoneManager<T> {
    interface Maps {
        L1Map borrow(int id);
        L1Map clone(int template, int id);
        void release(L1Map map);
        boolean occupied(L1Map map);
        default boolean drainGround(L1Map map) { return true; }
    }

    private static final Maps WORLD = new Maps() {
        public L1Map borrow(int id) { return L1WorldMap.getInstance().findMap(id); }
        public L1Map clone(int template, int id) { return L1WorldMap.getInstance().tryCloneMap(template, id); }
        public void release(L1Map map) {
            MapCleaner.cleanupNow(map, MapCleanerFilterFactory.createBattleHunterFilter());
            L1WorldMap.getInstance().removeMap(map);
        }
        public boolean drainGround(L1Map map) { return drainWorldGround(map); }
        public boolean occupied(L1Map map) {
            for (var obj : CWorld.getInstance().getMapObjects(map.getId())) {
                if (obj.getMap() != map) continue;
                if (isProtectedOccupant(obj)) return true;
            }
            return false;
        }
    };
    private static final Logger LOG = Logger.getLogger(CInstantZoneManager.class.getName());
    private static final ConcurrentHashMap<Integer, CInstantZone<?>> OWNERS = new ConcurrentHashMap<>();
    private static final Set<CInstantZone<?>> DRAINING = ConcurrentHashMap.newKeySet();
    private static volatile boolean stopping;
    private static final AtomicIntegerArray PUBLIC_ENTRIES = new AtomicIntegerArray(Short.MAX_VALUE + 1);
    private final ConcurrentHashMap<Integer, CInstantZone<T>> rooms = new ConcurrentHashMap<>();
    private final int limit;
    private final Maps maps;

    public CInstantZoneManager(int limit) { this(limit, WORLD); }

    CInstantZoneManager(int limit, Maps maps) {
        if (limit <= 0) throw new IllegalArgumentException("Invalid room limit");
        this.limit = limit;
        this.maps = Objects.requireNonNull(maps);
    }

    public static boolean isPlayerCompanion(L1Object obj) {
        return obj instanceof L1CompanionInstance || (obj instanceof L1NpcInstance npc
                && npc.getMaster() instanceof L1PcInstance);
    }

    public static boolean isProtectedOccupant(L1Object obj) {
        return obj instanceof L1PcInstance || isPlayerCompanion(obj)
                || (obj instanceof L1MonsterInstance monster && monster.hasPendingPartyDrop());
    }

    public static CInstantZone<?> destination(int id) { return OWNERS.get(id); }

    // A teleport to a currently public base can already be moving a player into
    // the world when the first party tries to claim it. Publish the pin before
    // checking ownership; the allocator publishes ownership before checking pins.
    public static boolean enterDestination(int id, CInstantZone<?> expected, Runnable move) {
        if (expected != null) return expected.mapId() == id && expected.enter(move);
        if (id < 0 || id >= PUBLIC_ENTRIES.length()) return false;
        PUBLIC_ENTRIES.incrementAndGet(id);
        try {
            if (OWNERS.containsKey(id)) return false;
            move.run();
            return true;
        } finally {
            PUBLIC_ENTRIES.decrementAndGet(id);
        }
    }

    static boolean drainWorldGround(L1Map map) {
        final var world = CWorld.getInstance();
        for (var obj : world.snapshotZoneOccupants(map)) {
            if (obj instanceof L1GroundInventory tile) tile.clearItems();
        }
        for (var obj : world.getMapObjects(map.getId())) {
            if ((obj instanceof L1GroundInventory || obj instanceof L1ItemInstance) && obj.getMap() == map)
                return false;
        }
        return true;
    }

    public CInstantZone<T> tryOpen(int template, int base, int firstClone, int lastClone) {
        if (base < -1 || base > Short.MAX_VALUE || firstClone < 0 || lastClone > Short.MAX_VALUE)
            throw new IllegalArgumentException("Invalid dungeon map range");
        if (base >= 0) {
            final CInstantZone<T> zone = acquire(template, base, true);
            if (zone != null) return zone;
        }
        for (int id = firstClone; id <= lastClone && rooms.size() < limit; id++) {
            if (id != base) {
                final CInstantZone<T> zone = acquire(template, id, false);
                if (zone != null) return zone;
            }
        }
        return null;
    }

    private CInstantZone<T> acquire(int template, int id, boolean borrowed) {
        final CInstantZone<T> zone;
        synchronized (rooms) {
            if (stopping || rooms.size() >= limit || OWNERS.containsKey(id)) return null;
            zone = new CInstantZone<>(this, id, borrowed);
            if (OWNERS.putIfAbsent(id, zone) != null) return null;
            rooms.put(id, zone);
        }
        boolean published = false;
        try {
            if (PUBLIC_ENTRIES.get(id) != 0) return null;
            final L1Map terrain = borrowed ? maps.borrow(id) : maps.clone(template, id);
            if (terrain == null) {
                if (borrowed) throw new IllegalStateException("Missing dungeon base map " + id);
                return null;
            }
            if (borrowed && maps.occupied(terrain)) return null;
            zone.publish(terrain);
            published = true;
            return zone;
        } finally {
            if (!published) forget(zone);
        }
    }

    public int size() { return rooms.size(); }

    public T get(int id) {
        final CInstantZone<T> zone = rooms.get(id);
        return zone == null ? null : zone.encounter();
    }

    public List<T> values() {
        final List<T> result = new ArrayList<>();
        for (var zone : rooms.values()) {
            final T value = zone.encounter();
            if (value != null) result.add(value);
        }
        return result;
    }

    private void forget(CInstantZone<T> zone) {
        rooms.remove(zone.mapId(), zone);
        OWNERS.remove(zone.mapId(), zone);
    }

    void cleaned(CInstantZone<T> zone) {
        DRAINING.add(zone);
        retire(zone);
    }

    void retire(CInstantZone<T> zone) {
        if (!zone.beginRetirement()) return;
        boolean released = false;
        try {
            final L1Map map = zone.map();
            if (maps.occupied(map) || !maps.drainGround(map)) return;
            if (!zone.borrowed) maps.release(map);
            zone.released();
            DRAINING.remove(zone);
            forget(zone);
            released = true;
        } finally {
            if (!released) {
                DRAINING.add(zone);
                zone.deferRetirement();
            }
        }
    }

    /** Re-check the draining rooms owned by this manager. */
    public void reap() {
        for (var zone : DRAINING) if (zone.manager == this) zone.reap();
    }

    /** The existing real-time world clock drives retirement even if timer admission was saturated. */
    public static void reapWorld() {
        if (DRAINING.isEmpty()) return;
        for (var zone : DRAINING) {
            if (zone.manager.maps != WORLD) continue;
            try {
                zone.evacuateRemaining();
                zone.reap();
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Dungeon retirement on map " + zone.mapId(), failure);
            }
        }
    }

    public static void stopAccepting() { stopping = true; }

    /** Sessions and script workers have quiesced. Retire remaining prepared rooms with their own cleanup. */
    public static void closePrepared() {
        for (var zone : OWNERS.values()) {
            try { zone.closePrepared(); }
            catch (RuntimeException failure) { LOG.log(Level.SEVERE, "Closing dungeon " + zone.mapId(), failure); }
        }
        reapWorld();
    }
}
