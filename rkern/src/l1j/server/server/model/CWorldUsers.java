package l1j.server.server.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import l1j.server.server.model.Instance.L1PcInstance;

/** Online users by character name and the other characters on their account. */
final class CWorldUsers {
    private static final class Keys {
        String name;
        String account;
    }

    private final ConcurrentHashMap<String, L1PcInstance> byName = new ConcurrentHashMap<>(8192);
    private final Collection<L1PcInstance> online = Collections.unmodifiableCollection(byName.values());
    private final ConcurrentSkipListMap<String, L1PcInstance[]> byAccount =
            new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    // Writers also need the keys from before an in-place rename or account change.
    // Indexed reads never acquire this monitor or call into the world under it.
    private final IdentityHashMap<L1PcInstance, Keys> keys = new IdentityHashMap<>();

    Collection<L1PcInstance> online() {
        return online;
    }

    L1PcInstance find(String name) {
        return name == null ? null : byName.get(name.toUpperCase(Locale.ROOT));
    }

    synchronized void register(L1PcInstance pc) {
        Keys entry = keys.get(pc);
        if (entry == null) {
            entry = new Keys();
            keys.put(pc, entry);
        }
        update(pc, entry, true);
    }

    synchronized void keysChanged(L1PcInstance pc) {
        Keys entry = keys.get(pc);
        if (entry != null) update(pc, entry, false);
    }

    private void update(L1PcInstance pc, Keys entry, boolean registering) {
        String name = pc.getName();
        String key = name == null ? null : name.toUpperCase(Locale.ROOT);
        String account = pc.getAccountName();
        boolean renamed = !Objects.equals(entry.name, key);
        boolean accountChanged = !Objects.equals(entry.account, account);
        if (!registering && !renamed && !accountChanged) return;

        boolean wasOnline = entry.name != null && byName.get(entry.name) == pc;
        if (wasOnline && (renamed || accountChanged)) removeAccount(entry.account, pc);
        if (wasOnline && renamed) byName.remove(entry.name, pc);
        entry.name = key;
        entry.account = account;
        if (key == null) return;
        if (renamed || registering) {
            L1PcInstance displaced = byName.put(key, pc);
            if (displaced != null && displaced != pc) {
                removeAccount(keys.get(displaced).account, displaced);
            }
        }
        if ((!wasOnline || renamed || accountChanged) && byName.get(key) == pc) addAccount(account, pc);
    }

    synchronized void remove(L1PcInstance pc) {
        Keys entry = keys.remove(pc);
        if (entry != null && entry.name != null && byName.remove(entry.name, pc)) {
            removeAccount(entry.account, pc);
        }
    }

    private void addAccount(String account, L1PcInstance pc) {
        if (account == null) return;
        L1PcInstance[] members = byAccount.get(account);
        if (members == null) {
            byAccount.put(account, new L1PcInstance[] {pc});
        } else {
            L1PcInstance[] next = Arrays.copyOf(members, members.length + 1);
            next[members.length] = pc;
            byAccount.put(account, next);
        }
    }

    private void removeAccount(String account, L1PcInstance pc) {
        if (account == null) return;
        L1PcInstance[] members = byAccount.get(account);
        if (members == null) return;
        for (int i = 0; i < members.length; i++) {
            if (members[i] != pc) continue;
            if (members.length == 1) {
                byAccount.remove(account);
            } else {
                L1PcInstance[] next = Arrays.copyOf(members, members.length - 1);
                System.arraycopy(members, i + 1, next, i, next.length - i);
                byAccount.put(account, next);
            }
            return;
        }
    }

    boolean hasOtherPrivateShop(L1PcInstance pc) {
        String account = pc.getAccountName();
        L1PcInstance[] members = account == null ? null : byAccount.get(account);
        if (members != null) {
            for (L1PcInstance other : members) {
                if (other.getId() != pc.getId() && account.equalsIgnoreCase(other.getAccountName())
                        && other.isPrivateShop()) return true;
            }
        }
        return false;
    }
}
