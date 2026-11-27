package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import l1j.server.Config;
import l1j.server.server.datatables.CNpcMakerDB;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1FurnitureInstance;
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
import l1j.server.server.templates.L1Npc;

/**
 * Owns the world registries and spatial indexes. Synchronous mutations keep
 * their identity and spatial locks. Async operations use a ticket serializer.
 * The first submitting thread drains other producers in FIFO order and
 * executes nested submissions on that same thread.
 * No worker, scheduler or CIO command lane is created.
 */
public final class CWorld {
    private final CWorldMapObjects mapObjects;
    private final CWorldGroundItems groundItems;
    private final CWorldUsers users = new CWorldUsers();

    private final CClanRegistry clanRegistry = new CClanRegistry();
    private final CWorldNpcIndex npcLookup = new CWorldNpcIndex();

    private volatile int _weather = 4;

    private volatile boolean _worldChatEnabled = true;

    private volatile boolean _processingContributionTotal = false;

    static final int MAX_MAP_ID = 32768;

    private static volatile CWorld _instance;

    private CWorld() {
        mapObjects = new CWorldMapObjects(MapsTable.getInstance().getMaps().keySet(), objectFactory);
        groundItems = new CWorldGroundItems(this, mapObjects);
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
    private final Collection<L1Object> allObjects = Collections.unmodifiableCollection(objectFactory.values());
    private final LocalWorldMap clusterGrid = new LocalWorldMap();
    private final WorldCollisionHash collision = WorldCollisionHash.getInstance();

    private final CWorldExecutionSerializer execution = new CWorldExecutionSerializer(this);
    private static final int EXEC_STORE = 1;
    private static final int EXEC_REMOVE = 2;
    private static final int EXEC_STORE_ENTER = 3;
    private static final int EXEC_ENTER = 4;
    private static final int EXEC_LEAVE = 5;
    private static final int EXEC_MOVE = 6;
    private static final int EXEC_ITEM_TIMER = 7;
    private static final int EXEC_PICK_ITEM = 9;

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
        return execution.backlog();
    }

    private boolean publishWorld(int operation, L1Object object, int x, int y, L1Map destination) {
        return object != null && execution.submit(operation, object, x, y, destination, objectFactory.worldIdentityOf(object));
    }

    void executeWorld(int operation, L1Object object, int x, int y, L1Map destination, long identity) {
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

    public List<L1FurnitureInstance> findFurnitureByItem(int itemObjectId) {
        return npcLookup.furniture.find(itemObjectId);
    }

    public static void furnitureItemChanged(L1FurnitureInstance furniture) {
        CWorld world = _instance;
        if (world != null) world.npcLookup.furniture.rekey(furniture);
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
            users.register(pc);
        } else if (object instanceof L1NpcInstance npc) {
            npcLookup.add(npc);
        }
    }

    private void removeSecondary(L1Object object) {
        if (object instanceof L1PcInstance) {
            final L1PcInstance pc = (L1PcInstance) object;
            users.remove(pc);
        } else if (object instanceof L1NpcInstance npc) {
            npcLookup.remove(npc);
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

        final boolean belongsTo(CInstantZone<?> zone) {
            return generation == zone;
        }
    }

    boolean PickItemCond(CInventory source, L1ItemInstance item, int count) {
        return groundItems.ownsStack(source, item, count);
    }

    int PickItem(L1Location origin, CInventory source, L1ItemInstance item, int count,
            L1PcInstance recipient) {
        return groundItems.pickItem(origin, source, item, count, recipient);
    }

    boolean PutItem(L1Location origin, CInventory source, L1ItemInstance item) {
        return groundItems.putItem(origin, source, item);
    }

    boolean RecoverDropRemainder(L1Location origin, CInventory source, L1ItemInstance item) {
        return groundItems.recoverRemainder(origin, source, item);
    }

    public boolean deleteGroundItem(L1ItemInstance item, L1Map expected) {
        return groundItems.deleteGroundItem(item, expected);
    }

    public L1GroundInventory findGroundInventory(int x, int y, short mapId) {
        return groundItems.findGroundInventory(x, y, mapId);
    }

    public L1GroundInventory getGroundInventory(int x, int y, short mapId) {
        return groundItems.getGroundInventory(x, y, mapId);
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
        return users.find(name);
    }

    public Collection<L1Object> getObject() {
        return allObjects;
    }

    private L1IntRegistry<L1Object> visibleMap(int mapId, boolean create) {
        return mapObjects.registry(mapId, create);
    }

    public boolean PickNpcDrop(L1Location origin, CInventory source, L1ItemInstance item,
            L1PcInstance recipient) {
        if (item == null) return false;
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
        return loc == null ? null : groundItems.tryGroundInventory(loc.getX(), loc.getY(), loc.getMap());
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
        final ArrayList<L1Object> result = clusterGrid.queryLine(map, src, target);
        sortByWorldId(result);
        return result;
    }

    public ArrayList<L1Object> getVisibleBoxObjects(L1Object object, int heading, int width, int height) {
        final L1Map map = object.getMap();
        final L1IntRegistry<L1Object> visible = visibleMap((short) map.getId(), false);
        if (visible == null || visible.isEmpty()) return null;
        final ArrayList<L1Object> result = clusterGrid.queryBox(map, object, heading, width, height);
        sortByWorldId(result);
        return result;
    }

    public final Collection<L1PcInstance> getVisiblePlayers(int mapId) {
        return mapObjects.players(mapId, null);
    }

    public final Collection<L1PcInstance> getVisiblePlayers(L1Map map) {
        if (map == null || map.isNull()) return Collections.emptyList();
        return mapObjects.players(map.getId(), map);
    }

    public boolean hasOtherMapPlayer(L1Map terrain, L1PcInstance except) {
        return terrain != null && !terrain.isNull() && mapObjects.hasOtherPlayer(terrain, except);
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
        return users.online().size();
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
        return users.online().toArray(L1PcInstance[]::new);
    }

    public Collection<L1PcInstance> getAllPlayers() {
        return users.online();
    }

    /** Character creation/loading can set names before the world exists. */
    public static void playerKeysChanged(L1PcInstance pc) {
        CWorld world = _instance;
        if (world != null) world.users.keysChanged(pc);
    }

    public boolean hasOtherPrivateShop(L1PcInstance pc) {
        return users.hasOtherPrivateShop(pc);
    }

    public Stream<L1PcInstance> getAllPlayerStream(){
        Collection<L1PcInstance> col = getAllPlayers();
        return col.size() > 1024 ? col.parallelStream() : col.stream();
    }

    public Collection<L1NpcInstance> getAllNpc() {
        return npcLookup.all;
    }

    public L1PcInstance getPlayer(String name) {
        return users.find(name);
    }

    public L1NpcShopInstance getShopNpc(String name) {
        return npcLookup.findShop(name);
    }

    public Collection<L1NpcShopInstance> getAllShopNpc() {
        return npcLookup.allShops;
    }


    public Collection<L1CompanionInstance> getAllCompanions() {
        return npcLookup.allCompanions;
    }

    public boolean contains_companion(int companion_id){
        return npcLookup.containsCompanion(companion_id);
    }

    public Collection<L1SummonInstance> getAllSummons() {
        return npcLookup.allSummons;
    }

    /** Map-wide, read-only ID-order traversal. A read of a cold map does not allocate a registry. */
    public Collection<L1Object> getMapObjects(int mapId) {
        return mapObjects.members(mapId);
    }

    public final L1IntRegistry<L1Object> getVisibleObjects(int mapId) {
        return mapObjects.legacyRegistry(mapId);
    }

    public void addWar(War war) {
        clanRegistry.addWar(war);
    }

    public War removeWar(War war) {
        return clanRegistry.removeWar(war);
    }

    public Stream<War> createWarStream() {
        return clanRegistry.wars();
    }

    public void storeClan(L1Clan clan) {
        clanRegistry.store(clan);
    }

    public void removeClan(L1Clan clan) {
        clanRegistry.remove(clan);
    }

    public void clearClan() {
        clanRegistry.clear();
    }

    public L1Clan findClan(String clanName) {
        return clanRegistry.find(clanName);
    }

    public L1Clan getClan(int clanId) {
        return clanRegistry.get(clanId);
    }

    public Collection<L1Clan> getAllClans() {
        return clanRegistry.clans();
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
        return allObjects.toArray(L1Object[]::new);
    }

    /** Compatibility entry point for administrative spawn reloads. */
    public void getMapObject(int mapId) {
        CNpcMakerDB.despawnForReload(getMapObjects(mapId), mapId);
    }

    public void broadcastServerMessage(String message) {
        SC_SystemMessage.broadcast(message);
    }

    // Legacy callers pass template IDs, not world object IDs.
    public L1NpcInstance findNpc(int id) {
        return findNpcByNpcId(id);
    }

    public L1NpcInstance findNpcByNpcId(int npcId) {
        return npcLookup.find(npcId);
    }

    public List<L1NpcInstance> getNpcsByTemplateId(int npcId) {
        return npcLookup.findAll(npcId);
    }

    public L1NpcInstance findNpc(String name) {
        return npcLookup.find(name);
    }

    public static void npcIdentityChanged(L1NpcInstance npc) {
        final CWorld world = _instance;
        if (world != null) world.npcLookup.changed(npc);
    }

    public static void npcTemplateIdChanged(L1Npc prototype, int previousId) {
        final CWorld world = _instance;
        if (world != null) world.npcLookup.templateChanged(prototype, previousId);
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

        if (out != null) clusterGrid.queryNearby(origin, radius, limit, out);
    }


}
