package l1j.server.server.model;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import l1j.server.server.model.world.siege.War;

/** World-owned clan identity indexes and active wars. */
final class CClanRegistry {
    // A detached clan can be edited or moved between worlds under the same lock.
    private static final Object CLAN_LOCK = new Object();
    private static final Comparator<String> NAME_ORDER = Comparator.comparingInt(String::length)
            .thenComparing(String.CASE_INSENSITIVE_ORDER);

    private final L1IntRegistry<L1Clan> byId = new L1IntRegistry<>();
    private final L1IntRegistry<L1Clan> activeBlessings = new L1IntRegistry<>();
    private final Collection<L1Clan> blessings = Collections.unmodifiableCollection(activeBlessings.values());
    private final Collection<L1Clan> clans = Collections.unmodifiableCollection(byId.values());
    private volatile ConcurrentSkipListMap<String, NameMatches> byName = new ConcurrentSkipListMap<>(NAME_ORDER);
    private final L1IntRegistry<War> wars = new L1IntRegistry<>();
    private final WeakReference<CClanRegistry> owner = new WeakReference<>(this);

    void store(L1Clan clan) {
        synchronized (CLAN_LOCK) {
            final CClanRegistry previousOwner = clan.clanRegistry();
            if (previousOwner == this) return;
            if (previousOwner != null) previousOwner.removeLocked(clan);
            storeLocked(clan);
        }
    }

    private void storeLocked(L1Clan clan) {
        final L1Clan replaced = byId.put(clan.getClanId(), clan);
        clan.setClanRegistry(owner);
        if (replaced != null) activeBlessings.remove(clan.getClanId(), replaced);
        updateBlessingLocked(clan);
        if (replaced != null) {
            replaced.setClanRegistry(null);
            final String name = clan.getClanName();
            if (name != null && name.equalsIgnoreCase(replaced.getClanName())) {
                byName.get(name).replace(replaced, clan);
                return;
            }
            removeName(replaced);
        }
        addName(clan);
    }

    void remove(L1Clan clan) {
        synchronized (CLAN_LOCK) {
            removeLocked(clan);
        }
    }

    private void removeLocked(L1Clan clan) {
        if (byId.remove(clan.getClanId(), clan)) detachName(clan);
    }

    private void detachName(L1Clan clan) {
        activeBlessings.remove(clan.getClanId(), clan);
        removeName(clan);
        clan.setClanRegistry(null);
    }

    void clear() {
        synchronized (CLAN_LOCK) {
            for (L1Clan clan : clans) clan.setClanRegistry(null);
            byId.clear();
            activeBlessings.clear();
            byName = new ConcurrentSkipListMap<>(NAME_ORDER);
        }
    }

    L1Clan find(String name) {
        Objects.requireNonNull(name, "clanName");
        final NameMatches matches = byName.get(name);
        return matches == null ? null : matches.first;
    }

    L1Clan get(int id) {
        return byId.get(id);
    }

    Collection<L1Clan> clans() {
        return clans;
    }

    Collection<L1Clan> activeBlessings() { return blessings; }

    static void blessingChanged(L1Clan clan) {
        synchronized (CLAN_LOCK) {
            CClanRegistry registry = clan.clanRegistry();
            if (registry != null) registry.updateBlessingLocked(clan);
        }
    }

    private void updateBlessingLocked(L1Clan clan) {
        int id = clan.getClanId();
        if (byId.get(id) != clan) return;
        if (clan.getBless() != 0) activeBlessings.put(id, clan);
        else activeBlessings.remove(id, clan);
    }

    static void changeId(L1Clan clan, int id) {
        synchronized (CLAN_LOCK) {
            if (clan.getClanId() == id) return;
            final CClanRegistry registry = clan.clanRegistry();
            if (registry != null) registry.removeLocked(clan);
            clan.assignClanId(id);
            if (registry != null) registry.storeLocked(clan);
        }
    }

    static void rename(L1Clan clan, String name) {
        synchronized (CLAN_LOCK) {
            final String previousName = clan.getClanName();
            if (Objects.equals(previousName, name)) return;
            final CClanRegistry registry = clan.clanRegistry();
            final boolean reindex = registry != null && (name == null || !name.equalsIgnoreCase(previousName));
            if (reindex) registry.removeName(clan);
            clan.assignClanName(name);
            if (reindex) registry.addName(clan);
        }
    }

    private void addName(L1Clan clan) {
        final String name = clan.getClanName();
        if (name == null) return;
        final NameMatches matches = byName.get(name);
        if (matches == null) byName.put(name, new NameMatches(clan));
        else matches.add(clan);
    }

    private void removeName(L1Clan clan) {
        final String name = clan.getClanName();
        if (name == null) return;
        final NameMatches matches = byName.get(name);
        if (matches.duplicates == null) byName.remove(name);
        else matches.remove(clan);
    }

    void addWar(War war) {
        wars.put(war.getId(), war);
    }

    War removeWar(War war) {
        return wars.remove(war.getId(), war) ? war : null;
    }

    Stream<War> wars() {
        return wars.values().stream();
    }

    private static final class NameMatches {
        private volatile L1Clan first;
        // Old ID iteration selected the lowest unsigned ID. Only shared names need an ordered bucket.
        private TreeMap<Integer, L1Clan> duplicates;

        private NameMatches(L1Clan clan) {
            first = clan;
        }

        private void add(L1Clan clan) {
            if (duplicates == null) {
                duplicates = new TreeMap<>(Integer::compareUnsigned);
                duplicates.put(first.getClanId(), first);
            }
            duplicates.put(clan.getClanId(), clan);
            if (Integer.compareUnsigned(clan.getClanId(), first.getClanId()) < 0) first = clan;
        }

        private void remove(L1Clan clan) {
            duplicates.remove(clan.getClanId());
            if (first == clan) first = duplicates.firstEntry().getValue();
            if (duplicates.size() == 1) duplicates = null;
        }

        private void replace(L1Clan previous, L1Clan replacement) {
            if (duplicates != null) duplicates.put(replacement.getClanId(), replacement);
            if (first == previous) first = replacement;
        }
    }
}
