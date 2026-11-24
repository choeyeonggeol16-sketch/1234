package l1j.server.server.model.character.Item.Drop;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import l1j.server.server.model.Instance.L1PcInstance;

public final class PartyLootCursor {

	private static final ConcurrentHashMap<Integer, AtomicInteger> CUR = new ConcurrentHashMap<>();

    private PartyLootCursor() {}

    public static void clearParty(l1j.server.server.model.CParty party) {
        if (party != null) CUR.remove(party.hashCode());
    }

    public static void clearSolo(L1PcInstance pc) {
        if (pc != null && pc.getParty() == null) CUR.remove(pc.getId());
    }

    public static int nextIndex(L1PcInstance base, int size) {
        if (base == null || size <= 1) return 0;
        final int key = base.getParty() != null ? base.getParty().hashCode() : base.getId();

        AtomicInteger ai = CUR.get(key);
        if (ai == null) {
            AtomicInteger n = new AtomicInteger(0);
            AtomicInteger prev = CUR.putIfAbsent(key, n);
            ai = (prev != null) ? prev : n;
        }
        int v = ai.getAndIncrement();
        if (v < 0) v = -v;
        return v % size;
    }
}