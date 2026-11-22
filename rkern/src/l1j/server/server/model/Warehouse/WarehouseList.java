package l1j.server.server.model.Warehouse;

import java.util.HashMap;

import l1j.server.server.storage.CacheD;

public abstract class WarehouseList {
    private final HashMap<String, Warehouse> warehouses = new HashMap<>();

    protected abstract Warehouse createWarehouse(String name);

    public synchronized Warehouse findWarehouse(String name) {
        Warehouse warehouse = warehouses.get(name);
        if (warehouse == null) {
            warehouse = createWarehouse(name);
            warehouses.put(name, warehouse);
        }
        return warehouse;
    }

    // Session binding invalidates an older deferred logout, even when the
    // same warehouse object is reused by a reconnecting account.
    synchronized Warehouse bindWarehouse(String name, long lease) {
        if (lease <= 0L) throw new IllegalArgumentException("Warehouse session lease");
        Warehouse warehouse = findWarehouse(name);
        final long current = warehouse.cacheSessionLease;
        if (current > lease || current <= -lease)
            throw new IllegalStateException("Stale warehouse session lease");
        warehouse.cacheSessionLease = lease;
        return warehouse;
    }

    synchronized void releaseWarehouse(String name, long lease) {
        if (lease <= 0L || name == null) return;
        Warehouse warehouse = warehouses.get(name);
        if (warehouse == null || warehouse.cacheSessionLease != lease) return;
        warehouse.cacheSessionLease = -lease;
        retireWhenIdle(name, warehouse, lease);
    }

    private synchronized void retireWhenIdle(String name, Warehouse expected, long lease) {
        if (warehouses.get(name) != expected || expected.cacheSessionLease != -lease) return;
        synchronized (expected) {
            // Lock order is the same as explicit deletion: list -> warehouse
            // -> CacheD. CacheD dispatches callbacks only outside its lock.
            final CacheD cache = CacheD.getInstance();
            final int key = expected.transferOrderKey();
            if (!cache.isWarehouseOwnerIdle(key)
                    && cache.deferWarehouseRetirement(key, expected,
                            () -> retireWhenIdle(name, expected, lease))) return;
            expected.retireWarehouse();
            warehouses.remove(name);
        }
    }

    // Only explicit owner-close may evict: no idle eviction of unsaved inventory.
    public synchronized void delWarehouse(String name) {
        Warehouse old = warehouses.get(name);
        if (old != null) {
            // Hold the warehouse's existing mutation monitor across the
            // nonblocking CacheD fence and world-object detach. Otherwise a
            // simultaneous item insertion can land between the fence and
            // clearItems(), disappearing without a corresponding new owner.
            synchronized (old) {
                // A clan-warehouse session still owns this instance. Detaching it
                // would allow a second cache object for the same SQL owner while
                // the first session continues using its old reference.
                if (old instanceof ClanWarehouse clan && clan.getWarehouseUsingChar() != 0) {
                    throw new IllegalStateException("Clan warehouse is in use: " + name);
                }
                final CacheD cache = CacheD.getInstance();
                final int key = old.transferOrderKey();
                if (cache.hasPendingOwnerApply(key) || !cache.flushDbOwner(key, 0L)) {
                    throw new IllegalStateException("Warehouse writes still pending: " + name);
                }
                old.retireWarehouse();
                warehouses.remove(name);
            }
        }
    }

    public synchronized Warehouse findWarehouse1(String name) {
        return findWarehouse(name);
    }

    public synchronized void delWarehouse1(String name) {
        delWarehouse(name);
    }
}
