package l1j.server.server.model.Warehouse;

import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.storage.CacheD;

public abstract class WarehouseList {
    private final ConcurrentHashMap<String, Warehouse> warehouses = new ConcurrentHashMap<>();

    protected abstract Warehouse createWarehouse(String name);

    public Warehouse findWarehouse(String name) {
        for (;;) {
            Warehouse warehouse = warehouses.get(name);
            if (warehouse == null) {
                // Only construction runs under the map's bucket lock.
                warehouse = warehouses.computeIfAbsent(name, this::createWarehouse);
            }
            if (!warehouse.isRetired()) return warehouse;
            // World detachment completed before retired was published.
            warehouses.remove(name, warehouse);
        }
    }

    Warehouse bindWarehouse(String name, long lease) {
        if (lease <= 0L) throw new IllegalArgumentException("Warehouse session lease");
        for (;;) {
            final Warehouse warehouse = findWarehouse(name);
            synchronized (warehouse) {
                if (warehouse.isRetired() || warehouses.get(name) != warehouse) continue;
                final long current = warehouse.cacheSessionLease;
                if (current > lease || current <= -lease)
                    throw new IllegalStateException("Stale warehouse session lease");
                warehouse.cacheSessionLease = lease;
                return warehouse;
            }
        }
    }

    void releaseWarehouse(String name, long lease) {
        if (lease <= 0L || name == null) return;
        final Warehouse warehouse = warehouses.get(name);
        if (warehouse == null) return;
        synchronized (warehouse) {
            if (warehouses.get(name) != warehouse || warehouse.cacheSessionLease != lease) return;
            warehouse.cacheSessionLease = -lease;
            retireWhenIdleLocked(name, warehouse, lease);
        }
    }

    private void retryRetirement(String name, Warehouse expected, long lease) {
        synchronized (expected) {
            if (warehouses.get(name) != expected || expected.cacheSessionLease != -lease) return;
            retireWhenIdleLocked(name, expected, lease);
        }
    }

    // Only this warehouse is locked. Do not hold a map bucket lock or the
    // CacheD lock across world detachment. Deferred work revalidates its lease.
    private void retireWhenIdleLocked(String name, Warehouse expected, long lease) {
        final CacheD cache = CacheD.getInstance();
        if (cache.deferWarehouseRetirement(expected.transferOrderKey(), expected,
                () -> retryRetirement(name, expected, lease))) return;
        expected.retireWarehouse();
        warehouses.remove(name, expected);
    }

    public void delWarehouse(String name) {
        final Warehouse old = warehouses.get(name);
        if (old == null) return;
        synchronized (old) {
            if (warehouses.get(name) != old || old.isRetired()) return;
            if (old instanceof ClanWarehouse clan && clan.getWarehouseUsingChar() != 0)
                throw new IllegalStateException("Clan warehouse is in use: " + name);
            if (!CacheD.getInstance().canRetireWarehouseExplicitly(old.transferOrderKey()))
                throw new IllegalStateException("Warehouse writes still pending: " + name);
            old.retireWarehouse();
            warehouses.remove(name, old);
        }
    }

}
