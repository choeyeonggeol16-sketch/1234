package l1j.server.server.model;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import l1j.server.server.model.Instance.L1FurnitureInstance;

/** Published furniture by backing item, in the same unsigned ID order as the object factory. */
final class CWorldFurniture {
    private final ConcurrentHashMap<Integer, List<L1FurnitureInstance>> byItem = new ConcurrentHashMap<>();
    private final IdentityHashMap<L1FurnitureInstance, Integer> installedKeys = new IdentityHashMap<>();

    synchronized void install(L1FurnitureInstance furniture) {
        int itemId = furniture.getItemObjId();
        Integer old = installedKeys.put(furniture, itemId);
        if (old != null) {
            if (old == itemId) return;
            unlink(old, furniture);
        }
        List<L1FurnitureInstance> previous = byItem.get(itemId);
        if (previous == null) {
            byItem.put(itemId, List.of(furniture));
            return;
        }
        ArrayList<L1FurnitureInstance> updated = new ArrayList<>(previous.size() + 1);
        updated.addAll(previous);
        int index = 0;
        while (index < updated.size()
                && Integer.compareUnsigned(updated.get(index).getId(), furniture.getId()) < 0) index++;
        updated.add(index, furniture);
        byItem.put(itemId, List.copyOf(updated));
    }

    synchronized void rekey(L1FurnitureInstance furniture) {
        if (installedKeys.containsKey(furniture)) install(furniture);
    }

    synchronized void remove(L1FurnitureInstance furniture) {
        Integer old = installedKeys.remove(furniture);
        if (old != null) unlink(old, furniture);
    }

    private void unlink(int itemId, L1FurnitureInstance furniture) {
        List<L1FurnitureInstance> previous = byItem.get(itemId);
        if (previous.size() == 1) {
            byItem.remove(itemId);
        } else {
            ArrayList<L1FurnitureInstance> updated = new ArrayList<>(previous);
            updated.removeIf(value -> value == furniture);
            byItem.put(itemId, List.copyOf(updated));
        }
    }

    List<L1FurnitureInstance> find(int itemId) {
        return byItem.getOrDefault(itemId, List.of());
    }
}
