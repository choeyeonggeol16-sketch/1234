package l1j.server.server.model;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;

import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.templates.L1Npc;

/** Secondary keys for registered NPCs. Name and template may both change while spawned. */
final class CWorldNpcIndex {
    private record Keys(String name, L1Npc template, int templateId) {}

    private final L1IntRegistry<L1NpcInstance> byId = new L1IntRegistry<>();
    private final L1IntRegistry<L1NpcShopInstance> shopsById = new L1IntRegistry<>();
    private final L1IntRegistry<L1CompanionInstance> companionsById = new L1IntRegistry<>();
    private final L1IntRegistry<L1SummonInstance> summonsById = new L1IntRegistry<>();
    final Collection<L1NpcInstance> all = Collections.unmodifiableCollection(byId.values());
    final Collection<L1NpcShopInstance> allShops = Collections.unmodifiableCollection(shopsById.values());
    final Collection<L1CompanionInstance> allCompanions = Collections.unmodifiableCollection(companionsById.values());
    final Collection<L1SummonInstance> allSummons = Collections.unmodifiableCollection(summonsById.values());
    final CWorldFurniture furniture = new CWorldFurniture();
    private final IdentityHashMap<L1NpcInstance, Keys> registrations = new IdentityHashMap<>();
    private final L1IntRegistry<Bucket> templates = new L1IntRegistry<>();
    private final Map<String, Bucket> names = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Bucket> shops = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);

    void add(L1NpcInstance npc) {
        final int id = npc.getId();
        byId.put(id, npc);
        synchronized (this) {
            update(npc, registrations.get(npc));
            if (npc instanceof L1FurnitureInstance placed) furniture.install(placed);
        }
        if (npc instanceof L1SummonInstance summon) summonsById.put(id, summon);
        else if (npc instanceof L1NpcShopInstance shop) shopsById.put(id, shop);
        else if (npc instanceof L1CompanionInstance companion) companionsById.put(id, companion);
    }

    boolean containsCompanion(int id) {
        return companionsById.containsKey(id);
    }

    synchronized void changed(L1NpcInstance npc) {
        final Keys previous = registrations.get(npc);
        if (previous != null) update(npc, previous);
    }

    synchronized void templateChanged(L1Npc template, int previousId) {
        final Bucket previous = templates.get(previousId);
        if (previous == null) return;
        // Several prototype objects may share a number. Rekey only this prototype's actors.
        for (L1NpcInstance npc : previous.snapshot()) {
            if (npc.getNpcTemplate() == template) update(npc, registrations.get(npc));
        }
    }

    private void update(L1NpcInstance npc, Keys previous) {
        final String name = npc.getName();
        final L1Npc template = npc.getNpcTemplate();
        final int templateId = template == null ? 0 : template.get_npcId();
        final boolean nameChanged = previous == null || !sameName(previous.name, name);
        final boolean idChanged = previous == null || (previous.template == null) != (template == null)
                || previous.templateId != templateId;
        if (!nameChanged && !idChanged && previous.template == template) return;
        if (nameChanged) {
            if (previous != null && previous.name != null) {
                remove(names, previous.name, npc);
                if (npc instanceof L1NpcShopInstance) remove(shops, previous.name, npc);
            }
            if (name != null) {
                add(names, name, npc);
                if (npc instanceof L1NpcShopInstance) add(shops, name, npc);
            }
        }
        if (idChanged) {
            if (previous != null && previous.template != null) removeTemplate(previous.templateId, npc);
            if (template != null) {
                final Bucket bucket = templates.get(templateId);
                if (bucket == null) templates.put(templateId, new Bucket(npc));
                else bucket.add(npc);
            }
        }
        registrations.put(npc, new Keys(name, template, templateId));
    }

    void remove(L1NpcInstance npc) {
        final int id = npc.getId();
        byId.remove(id, npc);
        removeKeys(npc);
        if (npc instanceof L1SummonInstance summon) summonsById.remove(id, summon);
        else if (npc instanceof L1NpcShopInstance shop) shopsById.remove(id, shop);
        else if (npc instanceof L1CompanionInstance companion) companionsById.remove(id, companion);
    }

    private synchronized void removeKeys(L1NpcInstance npc) {
        final Keys keys = registrations.remove(npc);
        if (keys == null) return;
        if (npc instanceof L1FurnitureInstance placed) furniture.remove(placed);
        if (keys.template != null) removeTemplate(keys.templateId, npc);
        if (keys.name != null) {
            remove(names, keys.name, npc);
            if (npc instanceof L1NpcShopInstance) remove(shops, keys.name, npc);
        }
    }

    private void removeTemplate(int id, L1NpcInstance npc) {
        final Bucket bucket = templates.get(id);
        if (bucket.remove(npc)) templates.remove(id, bucket);
    }

    private static boolean sameName(String left, String right) {
        return left == right || left != null && left.equalsIgnoreCase(right);
    }

    private static <K> void add(Map<K, Bucket> index, K key, L1NpcInstance npc) {
        final Bucket bucket = index.get(key);
        if (bucket == null) index.put(key, new Bucket(npc));
        else bucket.add(npc);
    }

    private static <K> void remove(Map<K, Bucket> index, K key, L1NpcInstance npc) {
        final Bucket bucket = index.get(key);
        if (bucket.remove(npc)) index.remove(key);
    }

    L1NpcInstance find(int templateId) {
        final Bucket bucket = templates.get(templateId);
        return bucket == null ? null : bucket.first;
    }

    L1NpcInstance find(String name) {
        final Bucket bucket = names.get(name);
        return bucket == null ? null : bucket.first;
    }

    L1NpcShopInstance findShop(String name) {
        final Bucket bucket = shops.get(name);
        return bucket == null ? null : (L1NpcShopInstance) bucket.first;
    }

    List<L1NpcInstance> findAll(int templateId) {
        final Bucket bucket = templates.get(templateId);
        return bucket == null ? List.of() : bucket.snapshot();
    }

    private static final class Bucket {
        private static final Comparator<L1NpcInstance> BY_ID =
                (a, b) -> Integer.compareUnsigned(a.getId(), b.getId());

        private volatile L1NpcInstance first;
        private TreeSet<L1NpcInstance> sorted;
        private List<L1NpcInstance> cached;

        Bucket(L1NpcInstance npc) { first = npc; }

        synchronized void add(L1NpcInstance npc) {
            if (sorted == null) {
                sorted = new TreeSet<>(BY_ID);
                sorted.add(first);
            }
            sorted.add(npc);
            first = sorted.first();
            cached = null;
        }

        synchronized boolean remove(L1NpcInstance npc) {
            cached = null;
            if (sorted == null) {
                first = null;
                return true;
            }
            sorted.remove(npc);
            first = sorted.first();
            if (sorted.size() == 1) sorted = null;
            return false;
        }

        synchronized List<L1NpcInstance> snapshot() {
            if (cached == null) cached = sorted != null ? List.copyOf(sorted)
                    : first == null ? List.of() : List.of(first);
            return cached;
        }
    }
}
