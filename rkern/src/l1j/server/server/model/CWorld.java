package l1j.server.server.model;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import l1j.server.Config;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.inventory.L1GroundInventory;
import l1j.server.server.model.map.L1Map;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.world.instance.CInstantZone;
import l1j.server.server.model.world.instance.CInstantZoneManager;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.world.instance.inn.InnHelper;
import l1j.server.server.model.world.instance.inn.InnRoom;
import l1j.server.server.model.world.siege.War;
import l1j.server.server.shared.L1Type;

/**
 * Owns the world registries and spatial indexes. Synchronous mutations keep
 * their identity and spatial locks. Async operations use a ticket serializer.
 * The first submitting thread drains other producers in FIFO order and
 * executes nested submissions on that same thread.
 * No worker, scheduler or CIO command lane is created.
 */
public final class CWorld {
    private final CWorldMapObjects mapObjects;
    private final ConcurrentHashMap<String, L1PcInstance> _allPlayers;
    private final L1IntRegistry<L1CompanionInstance> _allCompanions;
    private final L1IntRegistry<L1SummonInstance> _allSummons;

    private final L1IntRegistry<War> _allClanWars;
    private final L1IntRegistry<L1Clan> _allClans;
    private final L1IntRegistry<L1NpcInstance> _allNpc;
    private final L1IntRegistry<L1NpcShopInstance> _allShopNpc;

    private int _weather = 4;

    private boolean _worldChatEnabled = true;

    private boolean _processingContributionTotal = false;

    static final int MAX_MAP_ID = 32768;

    private static volatile CWorld _instance;

    private CWorld() {
        _allPlayers = new ConcurrentHashMap<>(8192);
        _allCompanions = new L1IntRegistry<>();
        _allSummons = new L1IntRegistry<>();

        _allClanWars = new L1IntRegistry<>();
        _allClans = new L1IntRegistry<>();
        _allNpc = new L1IntRegistry<>();
        _allShopNpc = new L1IntRegistry<>();

        mapObjects = new CWorldMapObjects(MapsTable.getInstance().getMaps().keySet());
    }

    public static CWorld getInstance() {
        CWorld instance = _instance;
        if (instance == null) {
            synchronized (CWorld.class) {
                instance = _instance;
                if (instance == null) {
                    instance = new CWorld();
                    _instance = instance;
                }
            }
        }
        return instance;
    }

    public void clear() {
        _instance = new CWorld();
    }

    private final L1ObjectFactory objectFactory = new L1ObjectFactory();
    private final LocalWorldMap clusterGrid = new LocalWorldMap();
    private final WorldCollisionHash collision = WorldCollisionHash.getInstance();

    /*
     * CExecutionSerializer<CWorld, 4194304> policy (0xA3A490).
     * Keep the original 2^22 ticket space and eight-slot admission margin.
     * Unlike the native 80-byte fixed-size argument pads, Java only needs
     * one object reference and primitive arguments for the six world methods.
     * Allocate pages on first use instead of reserving ~352 MiB at startup.
     * Each page is reused and never allocates one Runnable per event.
     */
    private static final Logger EXEC_LOG = Logger.getLogger(CWorld.class.getName());
    private static final int EXEC_SLOTS = 1 << 22;
    private static final int EXEC_MASK = EXEC_SLOTS - 1;
    private static final int EXEC_LIMIT = EXEC_SLOTS - 8;
    private static final int EXEC_PAGE_BITS = 10;
    private static final int EXEC_PAGE_SIZE = 1 << EXEC_PAGE_BITS;
    private static final int EXEC_PAGE_MASK = EXEC_PAGE_SIZE - 1;
    private static final int EXEC_PAGE_COUNT = EXEC_SLOTS >>> EXEC_PAGE_BITS;
    // Preserve the full admission ring; only cold, empty backing pages are reclaimed.
    private static final int EXEC_TRIM_MIN_PAGES = 64;
    private static final long EXEC_TRIM_INTERVAL_NS = 60_000_000_000L;
    private static final long EXEC_TRIM_GATE = Long.MIN_VALUE;

    private static final int EXEC_STORE = 1;
    private static final int EXEC_REMOVE = 2;
    private static final int EXEC_STORE_ENTER = 3;
    private static final int EXEC_ENTER = 4;
    private static final int EXEC_LEAVE = 5;
    private static final int EXEC_MOVE = 6;
    private static final int EXEC_ITEM_TIMER = 7;
    private static final int EXEC_CANCEL = 8;
    private static final int EXEC_PICK_ITEM = 9;

    private static final VarHandle EXEC_TAIL;
    private static final VarHandle EXEC_PENDING;
    private static final VarHandle EXEC_PAGE =
            MethodHandles.arrayElementVarHandle(ExecutionPage[].class);
    private static final VarHandle EXEC_READY =
            MethodHandles.arrayElementVarHandle(long[].class);

    static {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            EXEC_TAIL = lookup.findVarHandle(CWorld.class, "executionTail", long.class);
            EXEC_PENDING = lookup.findVarHandle(CWorld.class, "executionPending", int.class);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static final class ExecutionPage {
        final L1Object[] objects = new L1Object[EXEC_PAGE_SIZE];
        final L1Map[] destinations = new L1Map[EXEC_PAGE_SIZE];
        final long[] coordinates = new long[EXEC_PAGE_SIZE];
        final long[] identities = new long[EXEC_PAGE_SIZE];
        final long[] ready = new long[EXEC_PAGE_SIZE];
    }

    private final ExecutionPage[] executionPages = new ExecutionPage[EXEC_PAGE_COUNT];

    private volatile long executionTail;
    private volatile long executionHead;
    private volatile int executionPending;
    private volatile Thread executionThread;
    private volatile Thread executionWaiter;
    private int executionDepth;
    // Mutated only under executionPages; the consumer reads it after a drain.
    private volatile int executionAllocatedPages;
    private volatile long executionLastTrimNs;

    /** World jobs are admitted without routing through the CIO command FIFO. */
    public boolean AsyncStoreObject(L1Object object) {
        return publishWorld(EXEC_STORE, object, 0, 0, null);
    }

    public boolean AsyncRemoveObject(L1Object object) {
        return publishWorld(EXEC_REMOVE, object, 0, 0, null);
    }

    public boolean AsyncStoreAndEnterWorld(L1Object object) {
        return publishWorld(EXEC_STORE_ENTER, object, 0, 0, null);
    }

    public boolean AsyncEnterWorld(L1Object object) {
        return publishWorld(EXEC_ENTER, object, 0, 0, null);
    }

    public boolean AsyncLeaveWorld(L1Object object) {
        return publishWorld(EXEC_LEAVE, object, 0, 0, null);
    }

    public boolean AsyncMove(L1Object object, int x, int y, int mapId) {
        return object != null && publishWorld(EXEC_MOVE, object, x, y, L1WorldMap.getInstance().findMap(mapId));
    }

    /** Ground item expiry shares the same world identity serializer as removal. */
    public boolean AsyncItemTimer(L1ItemInstance item, int timerId, int generation) {
        return publishWorld(EXEC_ITEM_TIMER, item, timerId, generation, null);
    }

    /** One existing serializer ticket per party death, not per recipient or item. */
    public boolean AsyncPickItem(L1MonsterInstance npc, int deathEpoch) {
        return publishWorld(EXEC_PICK_ITEM, npc, deathEpoch, 0, null);
    }

    private void DoAsyncPickItem(L1Object object, int deathEpoch) {
        if (object instanceof L1MonsterInstance npc) npc.DispatchPartyDrop(deathEpoch);
    }

    public int ExecutableBacklog() {
        final long outstanding = executionTail - executionHead;
        return (int) Math.max(0L, Math.min(EXEC_SLOTS, outstanding));
    }

    private ExecutionPage ensureExecutionPage(int index) {
        ExecutionPage page = (ExecutionPage) EXEC_PAGE.getAcquire(executionPages, index);
        if (page != null) return page;
        synchronized (executionPages) {
            page = executionPages[index];
            if (page == null) {
                page = new ExecutionPage();
                EXEC_PAGE.setRelease(executionPages, index, page);
                ++executionAllocatedPages;
            }
            return page;
        }
    }

    /**
     * Retire unused serializer pages only after the last admitted ticket drained.
     * A CAS gate on the tail prevents a new reservation while arrays are retired.
     * Advancing the empty ring by one full revolution also invalidates any
     * producer that loaded the old tail before the gate was acquired: it must
     * retry page initialization instead of publishing into a retired page.
     * No per-event locks, ticket-capacity reduction, or extra worker.
     */
    private void trimIdleExecutionPages() {
        if (executionAllocatedPages < EXEC_TRIM_MIN_PAGES) return;
        final long now = System.nanoTime();
        final long last = executionLastTrimNs;
        // A second large burst can be the last traffic for hours. The cooldown only
        // applies to the smaller, routinely reused working set.
        if (executionAllocatedPages < EXEC_TRIM_MIN_PAGES * 4
                && last != 0L && now - last < EXEC_TRIM_INTERVAL_NS) return;
        final long emptyTicket = executionHead;
        if (emptyTicket < 0L || !EXEC_TAIL.compareAndSet(this,
                emptyTicket, emptyTicket | EXEC_TRIM_GATE)) return;
        final long nextTicket = emptyTicket + EXEC_SLOTS;
        try {
            synchronized (executionPages) {
                Arrays.fill(executionPages, null);
                executionAllocatedPages = 0;
                executionLastTrimNs = now;
            }
            executionHead = nextTicket;
        } finally {
            // Publish the new generation only after all retired pages and
            // head position are visible. Waiting producers then retry.
            EXEC_TAIL.setRelease(this, nextTicket);
        }
    }

    private boolean publishWorld(int operation, L1Object object,
            int x, int y, L1Map destination) {
        if (object == null) return false;
        if (Thread.currentThread() == executionThread) {
            runWorld(operation, object, x, y, destination,
                    objectFactory.worldIdentityOf(object));
            return true;
        }
        // Capture the generation before reserving a ticket. Already-queued
        // STORE followed by ENTER may legitimately have generation zero.
        final long identity = objectFactory.worldIdentityOf(object);
        long ticket;
        int slot;
        ExecutionPage page;
        for (;;) {
            ticket = executionTail;
            if (ticket < 0L) {
                Thread.onSpinWait();
                continue;
            }
            if (ticket - executionHead >= EXEC_LIMIT) return false;
            slot = (int) ticket & EXEC_MASK;
            page = ensureExecutionPage(slot >>> EXEC_PAGE_BITS);
            if (EXEC_TAIL.compareAndSet(this, ticket, ticket + 1L)) break;
        }
        final int entry = slot & EXEC_PAGE_MASK;
        int publishedOperation = operation;
        try {
            page.objects[entry] = object;
            page.coordinates[entry] =
                    ((long) x << 32) | (y & 0xffffffffL);
            page.destinations[entry] = destination;
            page.identities[entry] = identity;
        } catch (Throwable failure) {
            // A reserved ticket must always be consumable by the drainer.
            publishedOperation = EXEC_CANCEL;
            EXEC_READY.setRelease(page.ready, entry,
                    ((ticket + 1L) << 4) | publishedOperation);
            signalWorld();
            throw failure;
        }
        EXEC_READY.setRelease(page.ready, entry,
                ((ticket + 1L) << 4) | publishedOperation);
        signalWorld();
        return true;
    }

    private void signalWorld() {
        final Thread waiter = executionWaiter;
        if (waiter != null) LockSupport.unpark(waiter);
        if ((int) EXEC_PENDING.getAndAdd(this, 1) == 0) {
            drainWorldExecutables();
        }
    }

    private void drainWorldExecutables() {
        final Thread runner = Thread.currentThread();
        executionThread = runner;
        for (;;) {
            final long ticket = executionHead;
            final int slot = (int) ticket & EXEC_MASK;
            final ExecutionPage page = (ExecutionPage) EXEC_PAGE.getAcquire(executionPages, slot >>> EXEC_PAGE_BITS);
            final int entry = slot & EXEC_PAGE_MASK;
            final long[] ready = page.ready;
            final long expected = (ticket + 1L) << 4;
            long stamp;
            int spins = 0;
            while (((stamp = (long) EXEC_READY.getAcquire(ready, entry)) & ~15L)
                    != expected) {
                if (++spins < 1000) {
                    Thread.onSpinWait();
                } else {
                    executionWaiter = runner;
                    if (((long) EXEC_READY.getAcquire(ready, entry) & ~15L)
                            != expected) {
                        LockSupport.parkNanos(this, 10_000_000L);
                    }
                    executionWaiter = null;
                    spins = 0;
                }
            }
            try {
                final int operation = (int) (stamp & 15L);
                if (operation != EXEC_CANCEL) {
                    final long position = page.coordinates[entry];
                    runWorld(operation, page.objects[entry],
                            (int) (position >>> 32), (int) position,
                            page.destinations[entry],
                            page.identities[entry]);
                }
            } catch (Throwable failure) {
                EXEC_LOG.log(Level.SEVERE, "CWorld executable failed", failure);
            } finally {
                page.objects[entry] = null;
                page.destinations[entry] = null;
                executionHead = ticket + 1L;
            }
            executionThread = null;
            if ((int) EXEC_PENDING.getAndAdd(this, -1) == 1) {
                trimIdleExecutionPages();
                return;
            }
            executionThread = runner;
        }
    }

    private void runWorld(int operation, L1Object object,
            int x, int y, L1Map destination, long identity) {
        if (++executionDepth > 4) {
            EXEC_LOG.warning("CWorld nested executable depth=" + executionDepth);
        }
        try {
        	
            // The NPC owns an admitted settlement until this callback releases it.
            // Do not discard this ticket at the ordinary world-identity gate:
            // an explicit removal must still complete/release the admitted drop.
            if (operation == EXEC_PICK_ITEM) {
                DoAsyncPickItem(object, x);
                return;
            }
            // A queued STORE followed by ENTER may have been admitted before the first
            // registration existed. Bind that dependency now, then validate under the
            // factory stripe at the actual mutation (same Java object can be reinstalled).
            if (identity == 0 && operation != EXEC_STORE && operation != EXEC_STORE_ENTER) {
                identity = objectFactory.worldIdentityOf(object);
                if (identity == 0) return;
            }
            switch (operation) {
            case EXEC_STORE:
                installObject(object, identity);
                break;
            case EXEC_REMOVE:
                // Remove both registries and visibility as one transition.
                leaveAndRemoveObject(object, identity);
                break;
            case EXEC_STORE_ENTER:
                installAndEnterWorld(object, identity);
                break;
            case EXEC_ENTER:
                addVisibleObject0(object, true, identity);
                break;
            case EXEC_LEAVE:
                removeVisibleObject(object, identity);
                break;
            case EXEC_MOVE:
                relocateVisibleObject(object, x, y, destination, identity);
                break;
            case EXEC_ITEM_TIMER:
                if (object instanceof L1ItemInstance && objectFactory.findByWorldIdentity(identity) == object) {
                    ((L1ItemInstance) object).TimerExpired(x, y);
                }
                break;
            default:
                throw new IllegalArgumentException("invalid world executable " + operation);
            }
        } finally {
            --executionDepth;
        }
    }

    // World mutations commit canonical spatial data synchronously.

    public LocalWorldMap getClusterGrid() {
        return clusterGrid;
    }

    WorldCollisionHash worldCollision() {
        return collision;
    }

    public long smartIdOf(int objectId) {
        return objectFactory.smartIdOf(objectId);
    }

    public long smartIdOf(L1Object object) {
        return objectFactory.smartIdOf(object);
    }

    public L1Object findObjectBySmartId(long smartId) {
        return objectFactory.findBySmartId(smartId);
    }

    public L1Object findObject(int objectId) {
        return objectFactory.find(objectId);
    }

    public Collection<L1Object> values() {
        return objectFactory.values();
    }

    public int objectCount() {
        return objectFactory.size();
    }

    // World object identity and its secondary registries must change in one
    // serial operation. Reuse the factory's existing ID writer monitor instead
    // of creating a global lock, worker, task object, or new queue.
    public void storeObject(L1Object object) {
        installObject(object, 0L);
    }

    /** Synchronous registration and entry; callers can use the actor immediately. */
    public void storeAndEnterWorld(L1Object object) {
        installAndEnterWorld(object, 0L);
    }

    /** Remove the identity and its visibility as one generation transition. */
    public void leaveAndRemoveObject(L1Object object) {
        leaveAndRemoveObject(object, 0L);
    }

    private void leaveAndRemoveObject(L1Object object, long expected) {
        if (object == null) {
            throw new NullPointerException("object");
        }
        // The same instance can be removed and re-registered under its old ID.
        // Keep the object's relocation lock and the existing ID writer lock
        // across both operations, otherwise a delayed leave can erase the new
        // registration's visibility/occupancy after the ID has been reinstalled.
        // Object -> ID stripe is also the order used by relocation and visibility.
        if (object instanceof L1GroundInventory) {
            removeVisibleObject(object, expected);
            return;
        }
        final long oldPos;
        final boolean removed;
        synchronized (object) {
            synchronized (objectFactory.slotLock(object.getId())) {
                if (expected != 0 && objectFactory.findByWorldIdentity(expected) != object) return;
                oldPos = removeVisibleObjectLocked(object);
                removed = objectFactory.remove(object);
                if (removed) removeSecondary(object);
            }
        }
        clusterGrid.reconcileLeave(object, oldPos);
        if (removed) {
            if (object instanceof L1NpcInstance npc) npc.StopRegenerationIfUnregistered();
            if (object instanceof L1ItemInstance item && item.getItemId() == InnHelper.INN_KEYID)
                InnRoom.remove(item);
        }
    }

    // The previous actor must be captured under the same ID writer lock
    // used for replacement; a read outside the lock can miss an intervening
    // registration and strand its visible entry on another map.
    private void installAndEnterWorld(L1Object object, long expected) {
        final long installed = installObject(object, expected);
        if (installed != 0) addVisibleObject0(object, true, installed);
    }

    private long installObject(L1Object object, long expected) {
        if (object == null) {
            throw new NullPointerException();
        }

        final L1Object previous;
        final long installed;
        synchronized (objectFactory.slotLock(object.getId())) {
            if (expected != 0 && objectFactory.findByWorldIdentity(expected) != object) return 0L;
            final int id = object.getId();
            final L1Object registered = objectFactory.find(id);
            final L1Object local = mapObjects.mapOnlyOwner(id);
            if (local != null) mapObjects.forgetMapOnly(local);
            previous = registered != null ? registered : local;
            if (registered != null && registered != object) removeSecondary(registered);
            objectFactory.install(object);
            installSecondary(object);
            installed = objectFactory.worldIdentityOf(object);
        }
        // Keep NPC speed initialization outside the registry page monitor.
        if (object instanceof L1NpcInstance npc) {
            NpcSpeedData.install_npc(npc);
            npc.StartRegeneration();
        }
        if (previous != null && previous != object) {
            retireReplacedVisible(previous);
        }
        return installed;
    }

    // An earlier registration can lose its ID, then regain it before the
    // replacement's delayed visibility cleanup runs. In that case it is live
    // again and its occupancy/view must not be removed. Recheck the canonical
    // ID under the same per-object -> ID stripe order used by relocation.
    private void retireReplacedVisible(L1Object previous) {
        long oldPos = -1L;
        synchronized (previous) {
            synchronized (objectFactory.slotLock(previous.getId())) {
                if (objectFactory.find(previous.getId()) != previous && mapObjects.mapOnlyOwner(previous.getId()) != previous) {
                    oldPos = removeVisibleObjectLocked(previous);
                }
            }
        }
        clusterGrid.reconcileLeave(previous, oldPos);
        if (previous instanceof L1NpcInstance npc) npc.StopRegenerationIfUnregistered();
    }

    public void removeObject(L1Object object) {
        leaveAndRemoveObject(object);
    }

    private void installSecondary(L1Object object) {
        if (object instanceof L1PcInstance) {
            final L1PcInstance pc = (L1PcInstance) object;
            _allPlayers.put(pc.getName().toUpperCase(), pc);
        } else if (object instanceof L1NpcInstance) {
            final L1NpcInstance npc = (L1NpcInstance) object;
            _allNpc.put(npc.getId(), npc);
            if (object instanceof L1SummonInstance) {
                _allSummons.put(object.getId(), (L1SummonInstance) object);
            } else if (object instanceof L1NpcShopInstance) {
                _allShopNpc.put(object.getId(), (L1NpcShopInstance) object);
            } else if (object instanceof L1CompanionInstance) {
                _allCompanions.put(object.getId(), (L1CompanionInstance) object);
            }
        }
    }

    private void removeSecondary(L1Object object) {
        if (object instanceof L1PcInstance) {
            final L1PcInstance pc = (L1PcInstance) object;
            _allPlayers.remove(pc.getName().toUpperCase(), pc);
        } else if (object instanceof L1NpcInstance) {
            final L1NpcInstance npc = (L1NpcInstance) object;
            _allNpc.remove(npc.getId(), npc);
            if (object instanceof L1SummonInstance) {
                _allSummons.remove(object.getId(), (L1SummonInstance) object);
            } else if (object instanceof L1NpcShopInstance) {
                _allShopNpc.remove(object.getId(), (L1NpcShopInstance) object);
            } else if (object instanceof L1CompanionInstance) {
                _allCompanions.remove(object.getId(), (L1CompanionInstance) object);
            }
        }
    }

    static final int PICK_REJECTED = 0;
    static final int PICK_INVENTORY = 1;
    static final int PICK_GROUND = 2;

    /** Carries the generation at death even when the terrain of a borrowed base room is reused. */
    public static class DropOrigin extends L1Location {
        private final CInstantZone<?> generation;

        public DropOrigin(L1Location position) {
            super(position);
            generation = CInstantZoneManager.destination(getMapId());
        }
    }

    /** Validate ownership before a party route; tradeItem revalidates under both inventory monitors. */
    boolean PickItemCond(CInventory source, L1ItemInstance item, int count) {
        return source != null && item != null && count > 0
                && source.GetByServerID(item.getId()) == item && count <= item.getCount();
    }

    /** Existing ordered inventory monitors remain authoritative for each transfer. */
    int PickItem(L1Location origin, CInventory source, L1ItemInstance item, int count,
            L1PcInstance recipient) {
        if (origin == null || !PickItemCond(source, item, count)) return PICK_REJECTED;
        if (recipient != null) {
            final CInstantZone<?> zone = CInstantZoneManager.destination(origin.getMapId());
            if (origin instanceof DropOrigin saved && saved.generation != zone) return PICK_REJECTED;
            final boolean awarded = zone == null
                    ? tryAwardDrop(origin, source, item, count, recipient)
                    : zone.trySettle(origin.getMap(), () -> tryAwardDrop(origin, source, item, count, recipient));
            if (awarded) return PICK_INVENTORY;
            int targetX = 0, targetY = 0;
            final boolean live;
            synchronized (recipient.autoSaveLifecycleLock()) {
                live = isDropRecipient(origin, recipient);
                if (live) {
                    targetX = recipient.getX();
                    targetY = recipient.getY();
                }
            }
            if (live && DropAtTile(source, item, count, targetX, targetY, origin)) return PICK_GROUND;
        }
        return DropAtTile(source, item, count, origin.getX(), origin.getY(), origin)
                ? PICK_GROUND : PICK_REJECTED;
    }

    private boolean tryAwardDrop(L1Location origin, CInventory source, L1ItemInstance item, int count,
            L1PcInstance recipient) {
        synchronized (recipient.autoSaveLifecycleLock()) {
            return isDropRecipient(origin, recipient)
                    && DoAsyncAddItemToInventory(source, item, count, recipient);
        }
    }

    private static boolean isDropRecipient(L1Location origin, L1PcInstance recipient) {
        final var session = recipient.getNetConnection();
        return recipient.getMap() == origin.getMap() && !recipient.isLogoutStarted()
                && session != null && session.isConnected() && session.getActiveChar() == recipient;
    }

    /**
     * Delivery phase of DoAsyncPickItem, executed in the already admitted world
     * job (or its mandatory synchronous fallback). Do not enqueue a second
     * ticket: completion belongs to the outer NPC settlement.
     * Caller holds the recipient's existing logout/save lifecycle monitor.
     */
    private boolean DoAsyncAddItemToInventory(CInventory source, L1ItemInstance item, int count,
            L1PcInstance recipient) {
        final CInventory dest = recipient.getInventory();
        final L1ItemInstance adena = item.getItemId() == l1j.server.server.model.item.L1ItemId.ADENA
                ? dest.GetFirstItemByClassID(item.getItemId()) : null;
        return (adena == null || (long) adena.getCount() + count <= 2_000_000_000L)
                && source.tradeItem(item, count, dest) != null;
    }

    private boolean DropAtTile(CInventory source, L1ItemInstance item, int count,
            int x, int y, L1Location origin) {
        if (!PickItemCond(source, item, count)) return false;
        final CInstantZone<?> zone = CInstantZoneManager.destination(origin.getMapId());
        if (origin instanceof DropOrigin saved && saved.generation != zone) return false;
        final L1Map expected = origin.getMap();
        return zone == null ? deliverToGround(source, item, count, x, y, expected)
                : zone.trySettle(expected, () -> deliverToGround(source, item, count, x, y, expected));
    }

    private boolean deliverToGround(CInventory source, L1ItemInstance item, int count,
            int x, int y, L1Map expected) {
        final short map = (short) expected.getId();
        final L1GroundInventory ground = tryGroundInventory(x, y, expected);
        if (ground == null) return false;
        // Pickup may have removed an empty bucket between lookup and transfer.
        // Preserve the existing ground -> source monitor order.
        synchronized (ground) {
            if (ground.getMap() != expected || findGroundInventory(x, y, map) != ground) return false;
            return source.tradeItem(item, count, ground) != null;
        }
    }

    // Legacy nearest-passable order, precomputed instead of allocating a BFS per item.
    private static final byte[] DROP_OFFSETS = { 0, 0, 0, -1, 1, -1, 1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -2, 1, -2, -1, -2, 2, -2, 2, -1, 2, 0, 2, 1, 2, 2, 1, 2, 0, 2, -1, 2, -2, 2, -2, 1, -2, 0, -2, -1, -2, -2, 0, -3, 1, -3, -1, -3, 2, -3, -2, -3, 3, -3, 3, -2, 3, -1, 3, 0, 3, 1, 3, 2, 3, 3, 2, 3, 1, 3, 0, 3, -1, 3, -2, 3, -3, 3, -3, 2, -3, 1, -3, 0, -3, -1, -3, -2, -3, -3 };

    boolean PutItem(L1Location origin, CInventory source, L1ItemInstance item) {
        if (origin == null || !PickItemCond(source, item, item.getCount())) return false;
        final int x = origin.getX(), y = origin.getY();
        for (int i = 0; i < DROP_OFFSETS.length; i += 2) {
            final int tx = x + DROP_OFFSETS[i], ty = y + DROP_OFFSETS[i + 1];
            if (origin.getMap().isPassable(tx, ty))
                return DropAtTile(source, item, item.getCount(), tx, ty, origin);
        }
        return DropAtTile(source, item, item.getCount(), x, y, origin);
    }

    /** Try the corpse tile and nearby passable tiles, without changing stack caps. */
    boolean RecoverDropRemainder(L1Location origin, CInventory source, L1ItemInstance item) {
        if (source.GetByServerID(item.getId()) != item || item.getCount() <= 0) return true;
        final int x = origin.getX(), y = origin.getY();
        if (DropAtTile(source, item, item.getCount(), x, y, origin)) return true;
        for (int radius = 1; radius <= 3; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius
                            || !origin.getMap().isPassable(x + dx, y + dy)) continue;
                    if (DropAtTile(source, item, item.getCount(), x + dx, y + dy, origin)) return true;
                }
            }
        }
        return source.GetByServerID(item.getId()) != item;
    }

    private static int groundKey(int x, int y) {
        return -((x - 30000) * 10000 + (y - 30000));
    }

    /** A numeric map ID can be reused after an instance closes. */
    public L1GroundInventory findGroundInventory(int x, int y, short mapId) {
        final var map = visibleMap(mapId, false);
        if (map == null || !(map.get(groundKey(x, y)) instanceof L1GroundInventory ground)) return null;
        return ground.getX() == x && ground.getY() == y
                && L1WorldMap.getInstance().findMap(mapId) == ground.getMap() ? ground : null;
    }

    public L1GroundInventory getGroundInventory(int x, int y, short mapId) {
        final L1Map expected = L1WorldMap.getInstance().findMap(mapId);
        final L1GroundInventory ground = tryGroundInventory(x, y, expected);
        if (ground == null) throw new IllegalStateException("Ground tile unavailable: " + mapId + "/" + x + "/" + y);
        return ground;
    }

    private L1GroundInventory tryGroundInventory(int x, int y, L1Map expected) {
        if (expected == null || !LocalWorldMap.isSanePosition(expected.getId(), x, y)) return null;
        final int mapId = expected.getId();
        final var terrain = L1WorldMap.getInstance();
        if (terrain.findMap(mapId) != expected) return null;
        final int key = groundKey(x, y);
        final var registry = visibleMap(mapId, true);
        if (registry == null) return null;
        for (;;) {
            final L1GroundInventory retired;
            synchronized (registry.lockForKey(key)) {
                if (terrain.findMap(mapId) != expected) return null;
                final L1Object current = registry.get(key);
                if (current == null) {
                    // Only the fresh, unpublished tile's monitor is acquired under this stripe.
                    final var created = new L1GroundInventory(key, x, y, expected);
                    synchronized (created) {
                        mapObjects.enter(created, false);
                        clusterGrid.enterSilently(created);
                    }
                    return terrain.findMap(mapId) == expected ? created : null;
                }
                if (!(current instanceof L1GroundInventory ground) || current.getX() != x || current.getY() != y)
                    return null;
                if (ground.getMap() == expected) return ground;
                retired = ground;
            }
            // An outstanding pickup owns the item and its original tile. Never overwrite it.
            synchronized (retired) {
                if (registry.get(key) != retired) continue;
                if (retired.getSize() != 0) return null;
                removeVisibleObject(retired);
            }
        }
    }

    public void addVisibleObject(L1Object object) {
        addVisibleObject0(object, false, 0L);
    }

    private void addVisibleObject0(L1Object object, boolean registeredOnly, long expected) {
        if (object == null)
            return;

        final long newPos;
        synchronized (object) {
            if (object instanceof L1GroundInventory && !registeredOnly) {
                // Ground tiles have coordinate-derived IDs and no factory registration.
                if (!mapObjects.enter(object, false)) return;
                newPos = clusterGrid.enterSilently(object);
            } else {
                final int id = object.getId();
                synchronized (objectFactory.slotLock(id)) {
                    if (expected != 0 && objectFactory.findByWorldIdentity(expected) != object) return;
                    final L1Object owner = objectFactory.find(id);
                    // Traps and certain item publications legitimately have no factory entry.
                    // A registered actor's late enter must never displace its replacement.
                    final boolean canonical = owner == object;
                    if (!canonical && (registeredOnly || object instanceof L1PcInstance || object instanceof L1NpcInstance))
                        return;
                    final L1Object local = canonical ? null : mapObjects.mapOnlyOwner(id);
                    if ((!canonical && owner != null) || (local != null && local != object)
                            || !mapObjects.enter(object, canonical)) {
                        // The inventory insertion path uses failure to undo the staged item and its timer.
                        if (!registeredOnly && object instanceof L1ItemInstance)
                            throw new IllegalStateException("Conflicting ground item visibility: " + id);
                        return;
                    }
                    if (!canonical) mapObjects.rememberMapOnly(object);
                    final long old = LocalWorldMap.publishedPosition(object);
                    if (old != LocalWorldMap.NO_POSITION && (object instanceof L1PcInstance || object instanceof L1NpcInstance)
                            && (LocalWorldMap.positionMap(old) != object.getMapId()
                            || LocalWorldMap.positionX(old) != object.getX() || LocalWorldMap.positionY(old) != object.getY()))
                        collision.leaveCreature(object, LocalWorldMap.publishedTerrain(object),
                                LocalWorldMap.positionX(old), LocalWorldMap.positionY(old));
                    collision.enterCreature(object);
                    newPos = clusterGrid.enterSilently(object);
                }
            }
        }
        if (object instanceof L1NpcInstance) {
            NpcEventFactory.onNpcWorldEvent((L1NpcInstance) object);
        }

        if (object instanceof L1PcInstance) {
            final L1PcInstance pc = (L1PcInstance) object;
            pc.onWorldMove(pc.getMapId(), pc.getX(), pc.getY());
        }
        clusterGrid.reconcileEnter(object, newPos);
    }

    public void removeVisibleObject(L1Object object) {
        removeVisibleObject(object, 0L);
    }

    private void removeVisibleObject(L1Object object, long expected) {
        if (object == null) return;
        final long oldPos;
        synchronized (object) {
            if (expected == 0) {
                oldPos = removeVisibleObjectLocked(object);
            } else {
                synchronized (objectFactory.slotLock(object.getId())) {
                    if (objectFactory.findByWorldIdentity(expected) != object) return;
                    oldPos = removeVisibleObjectLocked(object);
                }
            }
        }
        clusterGrid.reconcileLeave(object, oldPos);
    }

    // Called under the object monitor. Keep numeric collision ownership and map membership
    // ordered with ID replacements, while ground tiles require no factory page.
    private long removeVisibleObjectLocked(L1Object object) {
        if (object instanceof L1GroundInventory) {
            mapObjects.remove(object, object.getClusterMapId());
            return clusterGrid.leaveSilently(object);
        }
        synchronized (objectFactory.slotLock(object.getId())) {
            mapObjects.forgetMapOnly(object);
            final long old = LocalWorldMap.publishedPosition(object);
            if (old != LocalWorldMap.NO_POSITION) {
                int map = LocalWorldMap.positionMap(old);
                boolean removed = mapObjects.remove(object, map);
                if (object instanceof L1PcInstance || object instanceof L1NpcInstance) {
                    final L1Object current = removed ? null : objectFactory.find(object.getId());
                    // The collision table stores numeric IDs. Retiring an old actor at
                    // the replacement's exact tile must preserve the new occupancy.
                    if (current == null || LocalWorldMap.publishedPosition(current) != old
                            || (object instanceof L1PcInstance) != (current instanceof L1PcInstance)) {
                        collision.leaveCreature(object, LocalWorldMap.publishedTerrain(object), LocalWorldMap.positionX(old), LocalWorldMap.positionY(old));
                    }
                }
            }
            return clusterGrid.leaveSilently(object);
        }
    }

    public void moveVisibleObject(L1Object object, int newMap) {
        if (object == null) return;
        final L1Map destination = L1WorldMap.getInstance().findMap(newMap);
        synchronized (object) {
            relocateLocked(object, object.getX(), object.getY(), destination);
        }
    }

    public void relocateVisibleObject(L1Object object, int x, int y, int mapId) {
        if (object != null)
            relocateVisibleObject(object, x, y, L1WorldMap.getInstance().findMap(mapId));
    }

    /** A delayed caller holding a recycled terrain reference cannot enter the replacement. */
    public boolean relocateVisibleObject(L1Object object, int x, int y, L1Map destination) {
        return relocateVisibleObject(object, x, y, destination, 0L);
    }

    private boolean relocateVisibleObject(L1Object object, int x, int y, L1Map destination, long expected) {
        if (object == null) return false;
        synchronized (object) {
            if (!relocateLocked(object, x, y, destination, expected)) return false;
        }
        if (object instanceof L1PcInstance pc) pc.InvalidateWarehouseAccess();
        return true;
    }

    /** Move with the ordinary old/new visibility notification after the structural locks are released. */
    public boolean moveVisibleObject(L1Object object, int x, int y, L1Map destination) {
        if (object == null) return false;
        final long previous, current;
        final L1Map unchangedTerrain;
        synchronized (object) {
            previous = LocalWorldMap.publishedPosition(object);
            unchangedTerrain = LocalWorldMap.publishedTerrain(object) == destination ? destination : null;
            if (!relocateLocked(object, x, y, destination)) return false;
            current = LocalWorldMap.publishedPosition(object);
        }
        if (object instanceof L1PcInstance pc) pc.CheckWarehouseRange();
        clusterGrid.reconcileMove(object, previous, current, unchangedTerrain);
        return true;
    }

    private boolean relocateLocked(L1Object object, int x, int y, L1Map destination) {
        return relocateLocked(object, x, y, destination, 0L);
    }

    private boolean relocateLocked(L1Object object, int x, int y, L1Map destination, long expected) {
        if (destination == null) return false;
        synchronized (objectFactory.slotLock(object.getId())) {
            if ((expected != 0 ? objectFactory.findByWorldIdentity(expected) : objectFactory.find(object.getId())) != object
                    || LocalWorldMap.publishedPosition(object) == LocalWorldMap.NO_POSITION
                    || !LocalWorldMap.isSanePosition(destination.getId(), x, y)
                    || L1WorldMap.getInstance().findMap(destination.getId()) != destination) return false;
            if (!mapObjects.move(object, destination.getId(), x, y)) return false;
            object.getLocation().set(x, y, destination);
            clusterGrid.relocateSilently(object);
            return true;
        }
    }

    /** Caller has already updated its location; reconcile from the last published tile. */
    public void moveTo(L1Object object, int oldMap, int oldX, int oldY) {
        if (object == null) return;
        final long previous, current;
        final L1Map unchangedTerrain;
        synchronized (object) {
            synchronized (objectFactory.slotLock(object.getId())) {
                if (objectFactory.find(object.getId()) != object) return;
                previous = LocalWorldMap.publishedPosition(object);
                if (previous == LocalWorldMap.NO_POSITION) return;
                final L1Map before = LocalWorldMap.publishedTerrain(object);
                final L1Map destination = object.getMap();
                unchangedTerrain = before == destination ? before : null;
                if ((destination != before && L1WorldMap.getInstance().findMap(destination.getId()) != destination)
                        || !mapObjects.move(object, object.getMapId(), object.getX(), object.getY())) {
                    object.getLocation().set(LocalWorldMap.positionX(previous), LocalWorldMap.positionY(previous), before);
                    return;
                }
                current = clusterGrid.relocateSilently(object);
            }
        }
        if (object instanceof L1PcInstance pc) pc.CheckWarehouseRange();
        clusterGrid.reconcileMove(object, previous, current, unchangedTerrain);
    }

    public L1Object findObject(String name) {
        if (name == null) {
            return null;
        }
        return _allPlayers.get(name.toUpperCase());
    }

    private Collection<L1Object> _allValues;
    public Collection<L1Object> getObject() {
        Collection<L1Object> vs = _allValues;
        return (vs != null) ? vs : (_allValues = Collections.unmodifiableCollection(values()));
    }

    private L1IntRegistry<L1Object> visibleMap(int mapId, boolean create) {
        return mapObjects.registry(mapId, create);
    }

    public boolean PickNpcDrop(L1Location origin, CInventory source, L1ItemInstance item,
            L1PcInstance recipient) {
        if (PickItem(origin, source, item, item.getCount(), recipient)
                != PICK_REJECTED) return true;
        return RecoverDropRemainder(origin, source, item);
    }

    public boolean PutNpcDrop(L1Location origin, CInventory source, L1ItemInstance item) {
        return PutItem(origin, source, item);
    }

    public boolean RecoverNpcDrop(L1Location origin, CInventory source, L1ItemInstance item) {
        return RecoverDropRemainder(origin, source, item);
    }

    public L1GroundInventory getInventory(int x, int y, short map) {
        return getGroundInventory(x, y, map);
    }

    public L1GroundInventory getInventory(L1Location loc) {
        return loc == null ? null : tryGroundInventory(loc.getX(), loc.getY(), loc.getMap());
    }

    public void attack(L1PcInstance attacker, L1Character target) {
        if (attacker == null || target == null) {
            return;
        }
        target.onAction(attacker);
    }


    public ArrayList<L1Object> getVisibleLineObjects(L1Object src, L1Object target) {
        final L1Map map = target.getMap();
        final L1IntRegistry<L1Object> visible = visibleMap((short) map.getId(), false);
        if (visible == null || visible.isEmpty()) return null;

        final ArrayList<L1Object> result = new ArrayList<>();
        if (src.getMap() != map) return result;
        final int x0 = src.getX(), y0 = src.getY(), x1 = target.getX(), y1 = target.getY();
        if (src.getMap() != map || target.getMap() != map) return result;
        clusterGrid.queryRectangle(map, Math.min(x0, x1), Math.min(y0, y1),
                Math.max(x0, x1), Math.max(y0, y1), result);

        final long dx = Math.abs((long) x1 - x0), dy = Math.abs((long) y1 - y0);
        final int sx = x1 >= x0 ? 1 : -1, sy = y1 >= y0 ? 1 : -1;
        final boolean xMajor = dx >= dy;
        final long major = xMajor ? dx : dy, minor = xMajor ? dy : dx;
        int kept = 0;
        for (int i = 0; i < result.size(); i++) {
            final L1Object object = result.get(i);
            if (object.getMap() != map || object.equals(src)) continue;
            final long stepX = ((long) object.getX() - x0) * sx;
            final long stepY = ((long) object.getY() - y0) * sy;
            final long along = xMajor ? stepX : stepY;
            if (along < 0L || along > major) continue;
            // The original error accumulator advances the minor axis on an
            // exact half-step, measured from this direction's starting tile.
            // Unsigned division also covers products of two full int spans.
            final long across = major == 0L ? 0L
                    : Long.divideUnsigned(along * minor + major / 2L, major);
            if ((xMajor ? stepY : stepX) == across) result.set(kept++, object);
        }
        return finishShapeQuery(result, kept);
    }

    public ArrayList<L1Object> getVisibleBoxObjects(L1Object object, int heading, int width, int height) {
        final L1Map map = object.getMap();
        final L1IntRegistry<L1Object> visible = visibleMap((short) map.getId(), false);
        if (visible == null || visible.isEmpty()) return null;
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative visibility box size");
        if (heading < 0 || heading >= 8) throw new ArrayIndexOutOfBoundsException(heading);

        final int x = object.getX(), y = object.getY();
        final ArrayList<L1Object> result = new ArrayList<>();
        if (object.getMap() != map) return result;
        // The rotated box is additionally bounded by Chebyshev distance.
        clusterGrid.queryRectangle(map, Math.clamp((long) x - height, Integer.MIN_VALUE, Integer.MAX_VALUE),
                Math.clamp((long) y - height, Integer.MIN_VALUE, Integer.MAX_VALUE),
                Math.clamp((long) x + height, Integer.MIN_VALUE, Integer.MAX_VALUE),
                Math.clamp((long) y + height, Integer.MIN_VALUE, Integer.MAX_VALUE), result);

        final double angle = ((heading + 6) & 7) * Math.PI / 4;
        final double cos = Math.cos(angle), sin = Math.sin(angle);
        int kept = 0;
        for (int i = 0; i < result.size(); i++) {
            final L1Object element = result.get(i);
            if (element.getMap() != map || element.equals(object)) continue;
            final long dx = (long) element.getX() - x;
            final long dy = (long) element.getY() - y;
            if (dx == 0L && dy == 0L) {
                result.set(kept++, element);
                continue;
            }
            if (Math.max(Math.abs(dx), Math.abs(dy)) > height) continue;
            final long forward = Math.round(dx * cos + dy * sin);
            final long lateral = Math.round(-dx * sin + dy * cos);
            if (forward > 0L && lateral >= -width && lateral <= width)
                result.set(kept++, element);
        }
        return finishShapeQuery(result, kept);
    }

    private static ArrayList<L1Object> finishShapeQuery(ArrayList<L1Object> result, int kept) {
        for (int i = result.size() - 1; i >= kept; i--) result.remove(i);
        sortByWorldId(result);
        return result;
    }

    public final Collection<L1PcInstance> getVisiblePlayers(int mapId) {
        final L1IntRegistry<L1Object> visible = visibleMap(mapId, false);
        if (visible == null || visible.isEmpty()) return new ArrayList<>();
        return visiblePlayers(visible, L1WorldMap.getInstance().getMap((short) mapId));
    }

    public final Collection<L1PcInstance> getVisiblePlayers(L1Map map) {
        if (map == null || map.isNull()) return Collections.emptyList();
        final L1IntRegistry<L1Object> visible = visibleMap(map.getId(), false);
        return visible == null ? Collections.emptyList() : visiblePlayers(visible, map);
    }

    private static List<L1PcInstance> visiblePlayers(L1IntRegistry<L1Object> mapSrc, L1Map currentMap) {
        final List<L1PcInstance> result = new ArrayList<>();
        for (L1Object object : mapSrc.values()) {
            if (object instanceof L1PcInstance pc && pc.getMap() == currentMap) result.add(pc);
        }
        return result;
    }

    /** Snapshot one terrain generation before running actor callbacks outside the registry. */
    public List<L1Object> snapshotZoneOccupants(L1Map expected) {
        if (expected == null) throw new NullPointerException("expected");
        final L1IntRegistry<L1Object> visible = visibleMap(expected.getId(), false);
        if (visible == null || visible.isEmpty()) return Collections.emptyList();
        final ArrayList<L1Object> result = new ArrayList<>();
        for (L1Object object : visible.values()) {
            if (object.getMap() == expected) result.add(object);
        }
        return result;
    }

    private static final class VisibleScratch {
        @SuppressWarnings("unchecked")
        ArrayList<L1PcInstance>[] lists = (ArrayList<L1PcInstance>[]) new ArrayList<?>[4];
        int depth;
    }

    private static final ThreadLocal<VisibleScratch> VISIBLE_SCRATCH =
            ThreadLocal.withInitial(VisibleScratch::new);

    /**
     * Borrow a thread-confined AOI snapshot for a synchronous broadcast.
     * Always pair with releaseVisiblePlayerFast() in finally. Nested
     * broadcasts use distinct lists, and each worker reuses its own lists.
     */
    public List<L1PcInstance> borrowVisiblePlayerFast(L1Object object) {
        return borrowVisiblePlayerFast(object, -1);
    }

    public List<L1PcInstance> borrowVisiblePlayerFast(L1Object object, int radius) {
        final VisibleScratch scratch = VISIBLE_SCRATCH.get();
        final int depth = scratch.depth;
        if (depth == scratch.lists.length) {
            scratch.lists = Arrays.copyOf(scratch.lists, depth << 1);
        }
        ArrayList<L1PcInstance> list = scratch.lists[depth];
        if (list == null) {
            list = new ArrayList<>(16);
            scratch.lists[depth] = list;
        }
        scratch.depth = depth + 1;
        try {
            getClusterGrid().queryNearbyPlayers(object, radius, list);
            return list;
        } catch (RuntimeException | Error failure) {
            releaseVisiblePlayerFast(list);
            throw failure;
        }
    }

    public void releaseVisiblePlayerFast(List<L1PcInstance> list) {
        final VisibleScratch scratch = VISIBLE_SCRATCH.get();
        final int depth = scratch.depth - 1;
        if (depth < 0 || scratch.lists[depth] != list) {
            throw new IllegalStateException("AOI snapshot release out of order");
        }
        scratch.depth = depth;
        // Do not retain exceptionally large AOI arrays for the worker lifetime.
        if (list.size() > 512) {
            scratch.lists[depth] = null;
        }
        list.clear();
    }

    public List<L1PcInstance> getVisiblePlayerFast(L1Object object) {
        return clusterGrid.queryScreenPlayers(object);
    }

    public int getAllPlayersSize() {
        return _allPlayers.size();
    }

    // The map registry enumerates unsigned world IDs. Skills that choose the
    // first matching target must see the same order after a spatial lookup.
    private static void sortByWorldId(List<L1Object> objects) {
        if (objects.size() > 1) {
            objects.sort((left, right) -> Integer.compareUnsigned(left.getId(), right.getId()));
        }
    }

    public ArrayList<L1Object> getVisibleObjects(L1Object object) {
        return getVisibleObjects(object, -1);
    }

    public ArrayList<L1Object> getVisibleObjects(L1Object object, int radius) {
        final ArrayList<L1Object> result = new ArrayList<>();
        clusterGrid.collectVisibleObjects(object, radius, result);
        sortByWorldId(result);
        return result;
    }

    public ArrayList<L1Object> getVisiblePoint(L1Location loc, int radius) {
        final ArrayList<L1Object> result = new ArrayList<>();
        clusterGrid.queryPoint(loc, radius, result);
        sortByWorldId(result);
        return result;
    }

    public ArrayList<L1PcInstance> getVisiblePlayer(L1Object object) {
        return getVisiblePlayer(object, -1);
    }

    public ArrayList<L1PcInstance> getVisiblePlayer(L1Object object, int radius) {
        final ArrayList<L1PcInstance> result = new ArrayList<>();
        collectVisiblePlayers(object, radius, result);
        return result;
    }

    /** Replace the destination with players currently present in the visible cells. */
    public void collectVisiblePlayers(L1Object object, int radius, List<L1PcInstance> result) {
        clusterGrid.collectVisiblePlayers(object, radius, result);
    }

    /** Legacy overload returns all spatial object types within a tile radius. */
    public ArrayList<L1Object> getVisiblePlayer(L1Object object, int radius, int unused) {
        return radius < 0 ? new ArrayList<>() : getVisibleObjects(object, radius);
    }

    public boolean hasRecognizePlayerFast(L1Object obj) {
        return getClusterGrid().hasNearbyPlayer(obj, -1);
    }

    public ArrayList<L1PcInstance> getRecognizePlayer(L1Object object) {
        return getVisiblePlayer(object, Config.PC_RECOGNIZE_RANGE);
    }

    public L1PcInstance[] getAllPlayers3() {
        return _allPlayers.values().toArray(new L1PcInstance[_allPlayers.size()]);
    }

    private Collection<L1PcInstance> _allPlayerValues;

    public Collection<L1PcInstance> getAllPlayers() {
        Collection<L1PcInstance> vs = _allPlayerValues;
        return (vs != null) ? vs : (_allPlayerValues = Collections.unmodifiableCollection(_allPlayers.values()));
    }

    public Stream<L1PcInstance> getAllPlayerStream(){
        Collection<L1PcInstance> col = getAllPlayers();
        return col.size() > 1024 ? col.parallelStream() : col.stream();
    }

    public Collection<L1NpcInstance> getAllNpc() {
        return Collections.unmodifiableCollection(_allNpc.values());
    }

    public L1PcInstance getPlayer(String name) {
        if (null == name)
            return null;
        return _allPlayers.get(name.toUpperCase());
    }

    public L1NpcShopInstance getShopNpc(String name) {
        Collection<L1NpcShopInstance> npc = getAllShopNpc();
        int nl = name.length();
        for (L1NpcShopInstance each : npc) {
            if (each == null)
                continue;

            String s = each.getName();
            if(s.length() != nl)
                continue;

            if (s.equalsIgnoreCase(name)) {
                return each;
            }
        }
        return null;
    }

    private Collection<L1NpcShopInstance> _allShopNpcValues;

    public Collection<L1NpcShopInstance> getAllShopNpc() {
        Collection<L1NpcShopInstance> vs = _allShopNpcValues;
        return (vs != null) ? vs : (_allShopNpcValues = Collections.unmodifiableCollection(_allShopNpc.values()));
    }


    private Collection<L1CompanionInstance> _allCompanionValues;
    public Collection<L1CompanionInstance> getAllCompanions() {
        Collection<L1CompanionInstance> vs = _allCompanionValues;
        return (vs != null) ? vs : (_allCompanionValues = Collections.unmodifiableCollection(_allCompanions.values()));
    }

    public boolean contains_companion(int companion_id){
        return _allCompanions.containsKey(companion_id);
    }

    private Collection<L1SummonInstance> _allSummonValues;

    public Collection<L1SummonInstance> getAllSummons() {
        Collection<L1SummonInstance> vs = _allSummonValues;
        return (vs != null) ? vs : (_allSummonValues = Collections.unmodifiableCollection(_allSummons.values()));
    }

    /** Map-wide, read-only ID-order traversal. A read of a cold map does not allocate a registry. */
    public Collection<L1Object> getMapObjects(int mapId) {
        return mapObjects.members(mapId);
    }

    public final L1IntRegistry<L1Object> getVisibleObjects(int mapId) {
        return mapObjects.legacyRegistry(mapId);
    }

    public void addWar(War war) {
        _allClanWars.put(war.getId(), war);
    }

    public War removeWar(War war) {
        return _allClanWars.remove(war.getId());
    }

    public Stream<War> createWarStream(){
        return _allClanWars.size() <= 0 ? null : _allClanWars.values().stream();
    }

    public void storeClan(L1Clan clan) {
        _allClans.put(clan.getClanId(), clan);
    }

    public void removeClan(L1Clan clan) {
        _allClans.remove(clan.getClanId());
    }

    public void clearClan() {
        _allClans.clear();
    }

    public L1Clan findClan(String clanName){
        int len = clanName.length();
        for(L1Clan clan : getAllClans()){
            String name = clan.getClanName();
            if(name.length() != len)
                continue;

            if(name.equalsIgnoreCase(clanName))
                return clan;
        }
        return null;
    }

    public L1Clan getClan(int clanId) {
        return _allClans.get(clanId);
    }

    private Collection<L1Clan> _allClanValues;

    public Collection<L1Clan> getAllClans() {
        Collection<L1Clan> vs = _allClanValues;
        return (vs != null) ? vs : (_allClanValues = Collections.unmodifiableCollection(_allClans.values()));
    }

    public void setWeather(int weather) {
        _weather = weather;
    }

    public int getWeather() {
        return _weather;
    }

    public void set_worldChatElabled(boolean flag) {
        _worldChatEnabled = flag;
    }

    public boolean isWorldChatElabled() {
        return _worldChatEnabled;
    }

    public void setProcessingContributionTotal(boolean flag) {
        _processingContributionTotal = flag;
    }

    public boolean isProcessingContributionTotal() {
        return _processingContributionTotal;
    }

    public L1Object[] getObject2() {
        return values().toArray(new L1Object[objectCount()]);
    }

    public void getMapObject(int mapId) {
        for (L1Object object : getMapObjects(mapId)) {
            if (object instanceof L1MonsterInstance monster
                    && monster.getMapId() == mapId
                    && monster.getHiddenStatus() == 0 && !monster.isDead()) {
                monster.re();
            }
        }
    }

    public void broadcastServerMessage(String message) {
        SC_SystemMessage.broadcast(message);
    }

    // Legacy callers pass template IDs, not world object IDs.
    public L1NpcInstance findNpc(int id) {
        return findNpcByNpcId(id);
    }

    public L1NpcInstance findNpcByNpcId(int npcId) {
        for (L1NpcInstance npc : _allNpc.values()) {
            if (npc != null && npc.getNpcTemplate() != null
                    && npc.getNpcTemplate().get_npcId() == npcId) return npc;
        }
        return null;
    }

    public L1NpcInstance findNpc(String name){
        int nl = name.length();
        for (L1NpcInstance each : getAllNpc()) {
            String s = each.getName();
            if(s.length() != nl)
                continue;

            if (s.equalsIgnoreCase(name)) {
                return each;
            }
        }
        return null;
    }

    public Stream<L1Object> createVisibleObjectsStream(int mapId) {
        Collection<L1Object> objects = getMapObjects(mapId);
        return objects.size() > 1024 ? objects.parallelStream() : objects.stream();
    }

    public void fillVisibleObjectsLimit(
            L1Object origin,
            int radius,
            int limit,
            List<L1Object> out) {

        if (out == null) {
            return;
        }
        out.clear();
        if (origin == null || limit <= 0) {
            return;
        }

        getClusterGrid().queryNearby(origin, radius, out);

        // limit 초과분 제거
        for (int i = out.size() - 1; i >= limit; i--) {
            out.remove(i);
        }
    }


}
