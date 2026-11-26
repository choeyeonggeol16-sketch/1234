package l1j.server.server.model;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Instance.L1SupportInstance;
import l1j.server.server.model.ai.core.WorldCollisionHash;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.world.instance.inn.InnHelper;
import l1j.server.server.model.world.instance.inn.InnRoom;
import l1j.server.server.shared.L1Type;

/**
 * World mutation boundary. The synchronous legacy API retains its completion
 * semantics and existing identity/spatial-index locks. Async* operations use
 * CWorld's own ticket serializer: same executing thread runs recursively;
 * the first submitting thread drains other producers in FIFO order.
 * No worker, scheduler or CIO command lane is created.
 */
public final class CWorld {
    private final L1World world;
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
            MethodHandles.arrayElementVarHandle(long[][].class);
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

    private final L1Object[][] executionObjects = new L1Object[EXEC_PAGE_COUNT][];
    private final long[][] executionCoordinates = new long[EXEC_PAGE_COUNT][];
    private final int[][] executionMaps = new int[EXEC_PAGE_COUNT][];
    private final long[][] executionIdentities = new long[EXEC_PAGE_COUNT][];
    private final long[][] executionReady = new long[EXEC_PAGE_COUNT][];

    private volatile long executionTail;
    private volatile long executionHead;
    private volatile int executionPending;
    private volatile Thread executionThread;
    private volatile Thread executionWaiter;
    private int executionDepth;
    // Mutated only under executionReady; the consumer reads it after a drain.
    private volatile int executionAllocatedPages;
    private volatile long executionLastTrimNs;

    /** World jobs are admitted without routing through the CIO command FIFO. */
    public boolean AsyncStoreObject(L1Object object) {
        return publishWorld(EXEC_STORE, object, 0, 0, 0);
    }

    public boolean AsyncRemoveObject(L1Object object) {
        return publishWorld(EXEC_REMOVE, object, 0, 0, 0);
    }

    public boolean AsyncStoreAndEnterWorld(L1Object object) {
        return publishWorld(EXEC_STORE_ENTER, object, 0, 0, 0);
    }

    public boolean AsyncEnterWorld(L1Object object) {
        return publishWorld(EXEC_ENTER, object, 0, 0, 0);
    }

    public boolean AsyncLeaveWorld(L1Object object) {
        return publishWorld(EXEC_LEAVE, object, 0, 0, 0);
    }

    public boolean AsyncMove(L1Object object, int x, int y, int mapId) {
        return publishWorld(EXEC_MOVE, object, x, y, mapId);
    }

    /** Ground item expiry shares the same world identity serializer as removal. */
    public boolean AsyncItemTimer(L1ItemInstance item, int timerId, int generation) {
        return publishWorld(EXEC_ITEM_TIMER, item, timerId, generation, 0);
    }

    /** One existing serializer ticket per party death, not per recipient or item. */
    public boolean AsyncPickItem(L1MonsterInstance npc, int deathEpoch) {
        return publishWorld(EXEC_PICK_ITEM, npc, deathEpoch, 0, 0);
    }

    private void DoAsyncPickItem(L1Object object, int deathEpoch) {
        if (object instanceof L1MonsterInstance npc) npc.DispatchPartyDrop(deathEpoch);
    }

    public int ExecutableBacklog() {
        final long outstanding = executionTail - executionHead;
        return (int) Math.max(0L, Math.min(EXEC_SLOTS, outstanding));
    }

    private void ensureExecutionPage(int page) {
        if (EXEC_PAGE.getAcquire(executionReady, page) != null) return;
        // Pages are cold-path allocations only; readiness is published last.
        synchronized (executionReady) {
            if (executionReady[page] != null) return;
            executionObjects[page] = new L1Object[EXEC_PAGE_SIZE];
            executionCoordinates[page] = new long[EXEC_PAGE_SIZE];
            executionMaps[page] = new int[EXEC_PAGE_SIZE];
            executionIdentities[page] = new long[EXEC_PAGE_SIZE];
            EXEC_PAGE.setRelease(executionReady, page, new long[EXEC_PAGE_SIZE]);
            ++executionAllocatedPages;
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
        if (last != 0L && now - last < EXEC_TRIM_INTERVAL_NS) return;
        final long emptyTicket = executionHead;
        if (emptyTicket < 0L || !EXEC_TAIL.compareAndSet(this,
                emptyTicket, emptyTicket | EXEC_TRIM_GATE)) return;
        final long nextTicket = emptyTicket + EXEC_SLOTS;
        try {
            synchronized (executionReady) {
                // The exclusive gate prevents tail CAS and there were no
                // outstanding reservations when it was acquired.
                for (int p = 0; p < EXEC_PAGE_COUNT; ++p) {
                    if (executionReady[p] == null) continue;
                    // No pending producer or drainer can refer to these arrays.
                    EXEC_PAGE.setRelease(executionReady, p, null);
                    executionObjects[p] = null;
                    executionCoordinates[p] = null;
                    executionMaps[p] = null;
                    executionIdentities[p] = null;
                }
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
            int x, int y, int mapId) {
        if (object == null) return false;
        if (Thread.currentThread() == executionThread) {
            runWorld(operation, object, x, y, mapId,
                    objectFactory.worldIdentityOf(object));
            return true;
        }
        // Capture the generation before reserving a ticket. Already-queued
        // STORE followed by ENTER may legitimately have generation zero.
        final long identity = objectFactory.worldIdentityOf(object);
        long ticket;
        int slot;
        for (;;) {
            ticket = executionTail;
            if (ticket < 0L) {
                Thread.onSpinWait();
                continue;
            }
            if (ticket - executionHead >= EXEC_LIMIT) return false;
            slot = (int) ticket & EXEC_MASK;
            ensureExecutionPage(slot >>> EXEC_PAGE_BITS);
            if (EXEC_TAIL.compareAndSet(this, ticket, ticket + 1L)) break;
        }
        final int page = slot >>> EXEC_PAGE_BITS;
        final int entry = slot & EXEC_PAGE_MASK;
        int publishedOperation = operation;
        try {
            executionObjects[page][entry] = object;
            executionCoordinates[page][entry] =
                    ((long) x << 32) | (y & 0xffffffffL);
            executionMaps[page][entry] = mapId;
            executionIdentities[page][entry] = identity;
        } catch (Throwable failure) {
            // A reserved ticket must always be consumable by the drainer.
            publishedOperation = EXEC_CANCEL;
            EXEC_READY.setRelease(executionReady[page], entry,
                    ((ticket + 1L) << 4) | publishedOperation);
            signalWorld();
            throw failure;
        }
        EXEC_READY.setRelease(executionReady[page], entry,
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
            final int page = slot >>> EXEC_PAGE_BITS;
            final int entry = slot & EXEC_PAGE_MASK;
            final long[] ready = executionReady[page];
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
                    final long position = executionCoordinates[page][entry];
                    runWorld(operation, executionObjects[page][entry],
                            (int) (position >>> 32), (int) position,
                            executionMaps[page][entry],
                            executionIdentities[page][entry]);
                }
            } catch (Throwable failure) {
                EXEC_LOG.log(Level.SEVERE, "CWorld executable failed", failure);
            } finally {
                executionObjects[page][entry] = null;
                executionIdentities[page][entry] = 0L;
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
            int x, int y, int mapId, long identity) {
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
            if (operation != EXEC_STORE && operation != EXEC_STORE_ENTER) {
                if (identity != 0L) {
                    if (objectFactory.findByWorldIdentity(identity) != object) return;
                } else if (objectFactory.find(object.getId()) != object) {
                    return;
                }
            }
            switch (operation) {
            case EXEC_STORE:
                installObject(object);
                break;
            case EXEC_REMOVE:
                // Remove both registries and visibility as one transition.
                leaveAndRemoveObject(object);
                break;
            case EXEC_STORE_ENTER:
                installAndEnterWorld(object);
                break;
            case EXEC_ENTER:
                addRegisteredVisibleObject(object);
                break;
            case EXEC_LEAVE:
                removeVisibleObject(object);
                break;
            case EXEC_MOVE:
                relocateVisibleObject(object, x, y, mapId);
                break;
            case EXEC_ITEM_TIMER:
                if (object instanceof L1ItemInstance) {
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

    CWorld(L1World world) {
        this.world = world;
    }

    LocalWorldMap getClusterGrid() {
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
    // serial operation. Reuse the factory's existing ID-page monitor instead
    // of creating a global lock, worker, task object, or new queue.
    public void storeObject(L1Object object) {
        installObject(object);
    }

    /** Synchronous registration and entry; callers can use the actor immediately. */
    public void storeAndEnterWorld(L1Object object) {
        installAndEnterWorld(object);
    }

    /** Remove the identity and its visibility as one generation transition. */
    public void leaveAndRemoveObject(L1Object object) {
        if (object == null) {
            throw new NullPointerException("object");
        }
        // The same instance can be removed and re-registered under its old ID.
        // Keep the object's relocation lock and the existing ID-page writer lock
        // across both operations, otherwise a delayed leave can erase the new
        // registration's visibility/occupancy after the ID has been reinstalled.
        // Object -> ID-page is also the order used by relocation and visibility.
        final long oldPos;
        synchronized (object) {
            synchronized (objectFactory.slotLock(object.getId())) {
                oldPos = removeVisibleObjectLocked(object);
                removeObject(object);
            }
        }
        clusterGrid.reconcileLeave(object, oldPos);
    }

    // The previous actor must be captured under the same ID-page writer lock
    // used for replacement; a read outside the lock can miss an intervening
    // registration and strand its visible entry on another map.
    private void installAndEnterWorld(L1Object object) {
        if (object == null) {
            throw new NullPointerException("object");
        }

        final L1Object previous;
        synchronized (objectFactory.slotLock(object.getId())) {
            previous = objectFactory.find(object.getId());
            if (previous != null && previous != object) {
                removeSecondary(previous);
            }
            objectFactory.install(object);
            installSecondary(object);
        }
        if (object instanceof L1NpcInstance npc) {
            NpcSpeedData.install_npc(npc);
            npc.StartRegeneration();
        }
        if (previous != null && previous != object) {
            retireReplacedVisible(previous);
        }
        if (objectFactory.find(object.getId()) == object) {
            addRegisteredVisibleObject(object);
            // The registry may be replaced while entry side effects execute.
            if (objectFactory.find(object.getId()) != object) {
                removeVisibleObject(object);
            }
        }
    }

    private void installObject(L1Object object) {
        if (object == null) {
            throw new NullPointerException();
        }

        final L1Object previous;
        synchronized (objectFactory.slotLock(object.getId())) {
            previous = objectFactory.find(object.getId());
            if (previous != null && previous != object) {
                removeSecondary(previous);
            }
            objectFactory.install(object);
            installSecondary(object);
        }
        // Keep NPC speed initialization outside the registry page monitor.
        if (object instanceof L1NpcInstance npc) {
            NpcSpeedData.install_npc(npc);
            npc.StartRegeneration();
        }
        if (previous != null && previous != object) {
            retireReplacedVisible(previous);
        }
    }

    // An earlier registration can lose its ID, then regain it before the
    // replacement's delayed visibility cleanup runs. In that case it is live
    // again and its occupancy/view must not be removed. Recheck the canonical
    // ID under the same per-object -> ID-page order used by relocation.
    private void retireReplacedVisible(L1Object previous) {
        long oldPos = -1L;
        synchronized (previous) {
            synchronized (objectFactory.slotLock(previous.getId())) {
                if (objectFactory.find(previous.getId()) != previous) {
                    oldPos = removeVisibleObjectLocked(previous);
                }
            }
        }
        clusterGrid.reconcileLeave(previous, oldPos);
        if (previous instanceof L1NpcInstance npc) npc.StopRegenerationIfUnregistered();
    }

    public void removeObject(L1Object object) {
        if (object == null) {
            throw new NullPointerException();
        }

        synchronized (objectFactory.slotLock(object.getId())) {
            if (!objectFactory.remove(object)) {
                return;
            }
            removeSecondary(object);
        }

        if (object instanceof L1NpcInstance npc) npc.StopRegenerationIfUnregistered();
        if (object.instanceOf(L1Type.L1TYPE_ITEMINSTANCE)) {
            final L1ItemInstance item = (L1ItemInstance) object;
            if (item.getItemId() == InnHelper.INN_KEYID) {
                InnRoom.remove(item);
            }
        }
    }

    private void installSecondary(L1Object object) {
        if (object instanceof L1PcInstance) {
            final L1PcInstance pc = (L1PcInstance) object;
            world._allPlayers.put(pc.getName().toUpperCase(), pc);
        } else if (object instanceof L1ItemInstance) {
            world.registerWorldItem((L1ItemInstance) object);
        } else if (object instanceof L1NpcInstance) {
            final L1NpcInstance npc = (L1NpcInstance) object;
            world._allNpc.put(npc.getId(), npc);
            if (object instanceof L1SummonInstance) {
                world._allSummons.put(object.getId(), (L1SummonInstance) object);
            } else if (object instanceof L1SupportInstance) {
                world._allSupports.put(object.getId(), (L1SupportInstance) object);
            } else if (object instanceof L1NpcShopInstance) {
                world._allShopNpc.put(object.getId(), (L1NpcShopInstance) object);
            } else if (object instanceof L1CompanionInstance) {
                world._allCompanions.put(object.getId(), (L1CompanionInstance) object);
            }
        }
    }

    private void removeSecondary(L1Object object) {
        if (object instanceof L1PcInstance) {
            final L1PcInstance pc = (L1PcInstance) object;
            world._allPlayers.remove(pc.getName().toUpperCase(), pc);
        } else if (object instanceof L1ItemInstance) {
            world.unregisterWorldItem((L1ItemInstance) object);
        } else if (object instanceof L1NpcInstance) {
            final L1NpcInstance npc = (L1NpcInstance) object;
            world._allNpc.remove(npc.getId(), npc);
            if (object instanceof L1SummonInstance) {
                world._allSummons.remove(object.getId(), (L1SummonInstance) object);
            } else if (object instanceof L1SupportInstance) {
                world._allSupports.remove(object.getId(), (L1SupportInstance) object);
            } else if (object instanceof L1NpcShopInstance) {
                world._allShopNpc.remove(object.getId(), (L1NpcShopInstance) object);
            } else if (object instanceof L1CompanionInstance) {
                world._allCompanions.remove(object.getId(), (L1CompanionInstance) object);
            }
        }
    }

    static final int PICK_REJECTED = 0;
    static final int PICK_INVENTORY = 1;
    static final int PICK_GROUND = 2;

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
            int x = 0, y = 0;
            short map = 0;
            boolean live = false;
            // Logout sets its admission flag before taking this same lock for its
            // final inventory save. A successful transfer must precede that save.
            synchronized (recipient.autoSaveLifecycleLock()) {
                final var session = recipient.getNetConnection();
                if (!recipient.isLogoutStarted() && session != null && session.isConnected()
                        && session.getActiveChar() == recipient) {
                    if (DoAsyncAddItemToInventory(source, item, count, recipient)) return PICK_INVENTORY;
                    x = recipient.getX();
                    y = recipient.getY();
                    map = recipient.getMapId();
                    live = true;
                }
            }
            if (live && DropAtTile(source, item, count, x, y, map)) return PICK_GROUND;
        }
        return DropAtTile(source, item, count, origin.getX(), origin.getY(), (short) origin.getMapId())
                ? PICK_GROUND : PICK_REJECTED;
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
            int x, int y, short map) {
        if (!PickItemCond(source, item, count)) return false;
        final L1GroundInventory ground = getGroundInventory(x, y, map);
        // Pickup may have removed an empty bucket between lookup and transfer.
        // Preserve the existing ground -> source monitor order.
        synchronized (ground) {
            if (findGroundInventory(x, y, map) != ground) return false;
            return source.tradeItem(item, count, ground) != null;
        }
    }

    // Legacy nearest-passable order, precomputed instead of allocating a BFS per item.
    private static final byte[] DROP_OFFSETS = { 0, 0, 0, -1, 1, -1, 1, 0, 1, 1, 0, 1, -1, 1, -1, 0, -1, -1, 0, -2, 1, -2, -1, -2, 2, -2, 2, -1, 2, 0, 2, 1, 2, 2, 1, 2, 0, 2, -1, 2, -2, 2, -2, 1, -2, 0, -2, -1, -2, -2, 0, -3, 1, -3, -1, -3, 2, -3, -2, -3, 3, -3, 3, -2, 3, -1, 3, 0, 3, 1, 3, 2, 3, 3, 2, 3, 1, 3, 0, 3, -1, 3, -2, 3, -3, 3, -3, 2, -3, 1, -3, 0, -3, -1, -3, -2, -3, -3 };

    boolean PutItem(L1Location origin, CInventory source, L1ItemInstance item) {
        if (origin == null || !PickItemCond(source, item, item.getCount())) return false;
        final int x = origin.getX(), y = origin.getY();
        final short map = (short) origin.getMapId();
        for (int i = 0; i < DROP_OFFSETS.length; i += 2) {
            final int tx = x + DROP_OFFSETS[i], ty = y + DROP_OFFSETS[i + 1];
            if (origin.getMap().isPassable(tx, ty))
                return DropAtTile(source, item, item.getCount(), tx, ty, map);
        }
        return DropAtTile(source, item, item.getCount(), x, y, map);
    }

    /** Try the corpse tile and nearby passable tiles, without changing stack caps. */
    boolean RecoverDropRemainder(L1Location origin, CInventory source, L1ItemInstance item) {
        if (source.GetByServerID(item.getId()) != item || item.getCount() <= 0) return true;
        final int x = origin.getX(), y = origin.getY();
        final short map = (short) origin.getMapId();
        if (DropAtTile(source, item, item.getCount(), x, y, map)) return true;
        for (int radius = 1; radius <= 3; radius++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != radius
                            || !origin.getMap().isPassable(x + dx, y + dy)) continue;
                    if (DropAtTile(source, item, item.getCount(), x + dx, y + dy, map)) return true;
                }
            }
        }
        return source.GetByServerID(item.getId()) != item;
    }

    /** Non-creating ground lookup: pickup and validation must never publish empty tiles. */
    public L1GroundInventory findGroundInventory(int x, int y, short mapId) {
        final L1IntRegistry<L1Object> registry = world.visibleMap(mapId, false);
        if (registry == null) return null;
        final int key = ((x - 30000) * 10000 + (y - 30000)) * -1;
        final L1Object object = registry.get(key);
        return object instanceof L1GroundInventory ? (L1GroundInventory) object : null;
    }

    /** Ground-item tile lookup / first publication belongs to CWorld. */
    public L1GroundInventory getGroundInventory(int x, int y, short mapId) {
        final int inventoryKey = ((x - 30000) * 10000 + (y - 30000)) * -1;
        final L1IntRegistry<L1Object> registry = world.visibleMap(mapId, true);
        // Reuse the visible map's writer leaf. The constructor publishes
        // its inventory before the lock is released; competing drops at the
        // same tile therefore cannot create two ground inventories.
        synchronized (registry.lockForKey(inventoryKey)) {
            final L1Object current = registry.get(inventoryKey);
            if (current != null) {
                return (L1GroundInventory) current;
            }
            return new L1GroundInventory(inventoryKey, x, y, mapId);
        }
    }

    public void addVisibleObject(L1Object object) {
        addVisibleObject0(object, false);
    }

    private void addRegisteredVisibleObject(L1Object object) {
        addVisibleObject0(object, true);
    }

    private void addVisibleObject0(L1Object object, boolean registeredOnly) {
        if (object == null)
            return;

        final long newPos;
        synchronized (object) {
            final int mapId = object.getMapId();
            final int id = object.getId();
            if (mapId >= 0 && mapId <= L1World.MAX_MAP_ID) {
                final L1IntRegistry<L1Object> map = world.visibleMap(mapId, true);
                if (registeredOnly) {
                    synchronized (objectFactory.slotLock(id)) {
                        synchronized (map.lockForKey(id)) {
                            if (objectFactory.find(id) != object) return;
                            map.put(id, object);
                            collision.enterCreature(object);
                        }
                    }
                } else {
                    // Keep the legacy explicit addVisibleObject semantics.
                    map.put(id, object);
                    collision.enterCreature(object);
                }
            } else if (registeredOnly && objectFactory.find(id) != object) {
                return;
            }
            newPos = clusterGrid.enterSilently(object);
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
        if (object == null) return;
        final long oldPos;
        synchronized (object) {
            oldPos = removeVisibleObjectLocked(object);
        }
        clusterGrid.reconcileLeave(object, oldPos);
    }

    // Used by compound leave+remove / replacement while identity lock is held.
    private long removeVisibleObjectLocked(L1Object object) {
        final int mapId = object.getMapId();
        final int id = object.getId();
        if (mapId >= 0 && mapId <= L1World.MAX_MAP_ID) {
            final L1IntRegistry<L1Object> map = world.visibleMap(mapId, false);
            if (map != null && map.remove(id, object)) {
                collision.leaveCreature(object);
            }
        }
        return clusterGrid.leaveSilently(object);
    }

    public void moveVisibleObject(L1Object object, int newMap) {
        if (object == null) return;

        synchronized (object) {
            final int id = object.getId();
            // A late move must not republish a removed actor or an older
            // generation that has already been replaced under this ID.
            synchronized (objectFactory.slotLock(id)) {
                if (objectFactory.find(id) != object) return;
                final int oldMap = object.getMapId();
                if (oldMap != newMap) {
                    if (oldMap >= 0 && oldMap <= L1World.MAX_MAP_ID) {
                        final L1IntRegistry<L1Object> source = world.visibleMap(oldMap, false);
                        if (source != null) source.remove(id, object);
                    }
                    if (newMap >= 0 && newMap <= L1World.MAX_MAP_ID) {
                        world.visibleMap(newMap, true).put(id, object);
                    }
                }
                clusterGrid.relocate(object);
            }
        }
    }

    /**
     * Change an actor's location while preserving the registration generation.
     * The ID-page monitor also protects concurrent registration/removal.
     */
    public void relocateVisibleObject(L1Object object, int x, int y, int mapId) {
        if (object == null) return;

        synchronized (object) {
            final int id = object.getId();
            synchronized (objectFactory.slotLock(id)) {
                if (objectFactory.find(id) != object) return;
                final int oldMap = object.getMapId();
                if (oldMap != mapId) {
                    if (oldMap >= 0 && oldMap <= L1World.MAX_MAP_ID) {
                        final L1IntRegistry<L1Object> source = world.visibleMap(oldMap, false);
                        if (source != null) source.remove(id, object);
                    }
                    if (mapId >= 0 && mapId <= L1World.MAX_MAP_ID) {
                        world.visibleMap(mapId, true).put(id, object);
                    }
                }
                object.setLocation(x, y, mapId);
                clusterGrid.relocate(object);
            }
        }
        // Warehouse lifecycle is outside the world identity/object monitors.
        if (object instanceof L1PcInstance pc) pc.InvalidateWarehouseAccess();
    }

    /** Caller has updated position and supplies the prior coordinates. */
    public void moveTo(L1Object object, int oldMap, int oldX, int oldY) {
        if (object == null) return;
        final int newMap, newX, newY;
        synchronized (object) {
            final int id = object.getId();
            synchronized (objectFactory.slotLock(id)) {
                if (objectFactory.find(id) != object) return;
                newMap = object.getMapId();
                if (oldMap != newMap) {
                    if (oldMap >= 0 && oldMap <= L1World.MAX_MAP_ID) {
                        final L1IntRegistry<L1Object> source = world.visibleMap(oldMap, false);
                        if (source != null) source.remove(id, object);
                    }
                    if (newMap >= 0 && newMap <= L1World.MAX_MAP_ID) {
                        world.visibleMap(newMap, true).put(id, object);
                    }
                }
                // The index is committed under identity/object locks; do not
                // invoke a PC serializer until after both monitors are released.
                clusterGrid.relocate(object);
                newX = object.getX();
                newY = object.getY();
            }
        }
        if (object instanceof L1PcInstance pc) pc.CheckWarehouseRange();
        clusterGrid.reconcileMove(object, oldMap, oldX, oldY,
                newMap, newX, newY);
    }
}
