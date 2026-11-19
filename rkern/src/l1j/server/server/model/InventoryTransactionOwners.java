package l1j.server.server.model;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

/** The 1..6 owners of a durable receipt, captured once at admission.
 * Fixed fields keep the ordinary one/two-owner path allocation-light.
 * Gate release is idempotent, including a callback which starts a new receipt.
 * No per-player buffer, thread, queue or JDBC connection is retained here.
 */
final class InventoryTransactionOwners {
    private static final AtomicIntegerFieldUpdater<InventoryTransactionOwners> RESERVED =
            AtomicIntegerFieldUpdater.newUpdater(InventoryTransactionOwners.class, "reserved");

    private final L1PcInstance primary;
    private final L1PcInstance first, second;
    private final CIOClient firstSession, secondSession;
    private final int firstStamp, secondStamp;
    // Only party transactions allocate these arrays. One/two-owner hot paths remain flat.
    private final L1PcInstance[] additional;
    private final CIOClient[] additionalSessions;
    private final int[] additionalStamps;
    private volatile int reserved;

    InventoryTransactionOwners(L1PcInstance primary, L1PcInstance other, boolean trade) {
        this.primary = primary;
        boolean ordered = other == null || primary.getId() <= other.getId();
        first = ordered ? primary : other;
        second = ordered ? other : primary;

        CIOClient firstConnection = admit(first, trade);
        int firstIdentity = first.getSessionStamp();
        CIOClient secondConnection = null;
        int secondIdentity = 0;
        try {
            if (second != null) {
                secondConnection = admit(second, trade);
                secondIdentity = second.getSessionStamp();
            }
        } catch (RuntimeException failure) {
            // Only the reservation acquired by this constructor is released.
            if (first.getSessionStamp() == firstIdentity) first.endItemTransfer();
            throw failure;
        }
        firstSession = firstConnection;
        secondSession = secondConnection;
        firstStamp = firstIdentity;
        secondStamp = secondIdentity;
        additional = null;
        additionalSessions = null;
        additionalStamps = null;
        reserved = second == null ? 1 : 3;
    }

    /** Sorted admission gates for an atomic 3..6 player dungeon debit. */
    InventoryTransactionOwners(L1PcInstance primary, L1PcInstance[] players) {
        this.primary = java.util.Objects.requireNonNull(primary);
        if (players == null || players.length < 3 || players.length > 6)
            throw new IllegalArgumentException("Party owner count must be 3..6");
        L1PcInstance[] sorted = players.clone();
        for (L1PcInstance pc : sorted) if (pc == null)
            throw new IllegalArgumentException("Null party owner");
        Arrays.sort(sorted, Comparator.comparingInt(L1PcInstance::getId));
        boolean hasPrimary = false;
        for (int i = 0; i < sorted.length; i++) {
            if (sorted[i] == primary) hasPrimary = true;
            if (i > 0 && sorted[i - 1].getId() == sorted[i].getId())
                throw new IllegalArgumentException("Duplicate inventory owner");
        }
        if (!hasPrimary) throw new IllegalArgumentException("Missing primary owner");
        first = sorted[0];
        second = sorted[1];
        additional = Arrays.copyOfRange(sorted, 2, sorted.length);
        additionalSessions = new CIOClient[additional.length];
        additionalStamps = new int[additional.length];
        CIOClient[] sessions = new CIOClient[sorted.length];
        int[] stamps = new int[sorted.length];
        int taken = 0;
        try {
            for (int i = 0; i < sorted.length; i++) {
                sessions[i] = admit(sorted[i], false);
                stamps[i] = sorted[i].getSessionStamp();
                taken |= 1 << i;
            }
        } catch (RuntimeException | Error failure) {
            for (int i = 0; i < sorted.length; i++)
                if ((taken & (1 << i)) != 0 && sorted[i].getSessionStamp() == stamps[i])
                    sorted[i].endItemTransfer();
            throw failure;
        }
        firstSession = sessions[0];
        secondSession = sessions[1];
        firstStamp = stamps[0];
        secondStamp = stamps[1];
        for (int i = 0; i < additional.length; i++) {
            additionalSessions[i] = sessions[i + 2];
            additionalStamps[i] = stamps[i + 2];
        }
        reserved = taken;
    }

    private CIOClient admit(L1PcInstance pc, boolean trade) {
        CIOClient session = pc.getNetConnection();
        boolean connected = session != null && session.isConnected() && session.getActiveChar() == pc;
        boolean market = !trade && pc != primary && pc.isPrivateShop()
                && L1World.getInstance().findObject(pc.getId()) == pc;
        if ((!connected && !market)
                || (!trade && (pc.getTradeID() != 0 || pc.isTradeResolving()))
                || !pc.tryBeginItemTransfer())
            throw new IllegalStateException("Inventory owner unavailable or busy");
        return connected ? session : null;
    }

    int size() { return second == null ? 1 : 2 + (additional == null ? 0 : additional.length); }

    L1PcInstance get(int index) {
        if (index == 0) return first;
        if (index == 1 && second != null) return second;
        if (additional != null && index >= 2 && index - 2 < additional.length) return additional[index - 2];
        throw new IndexOutOfBoundsException("Inventory owner index: " + index);
    }

    boolean contains(L1PcInstance pc) {
        if (pc == first || (pc != null && pc == second)) return true;
        if (additional != null) for (L1PcInstance extra : additional) if (pc == extra) return true;
        return false;
    }

    int stamp(L1PcInstance pc) {
        if (pc == first) return firstStamp;
        if (pc != null && pc == second) return secondStamp;
        if (additional != null) for (int i = 0; i < additional.length; i++)
            if (pc == additional[i]) return additionalStamps[i];
        throw new IllegalArgumentException("Unreserved inventory owner");
    }

    private CIOClient session(L1PcInstance pc) {
        if (pc == first) return firstSession;
        if (pc != null && pc == second) return secondSession;
        if (additional != null) for (int i = 0; i < additional.length; i++)
            if (pc == additional[i]) return additionalSessions[i];
        throw new IllegalArgumentException("Unreserved inventory owner");
    }

    Object monitor(L1PcInstance pc) {
        CIOClient session = session(pc);
        return session == null ? pc.getInventory() : session;
    }

    boolean current(L1PcInstance pc) {
        if (pc.getSessionStamp() != stamp(pc)) return false;
        CIOClient session = session(pc);
        if (session == null) return pc != primary && pc.isPrivateShop()
                && L1World.getInstance().findObject(pc.getId()) == pc;
        return session.isConnected() && session.getActiveChar() == pc && pc.getNetConnection() == session;
    }

    void release() {
        int acquired = RESERVED.getAndSet(this, 0);
        if ((acquired & 1) != 0 && first.getSessionStamp() == firstStamp) first.endItemTransfer();
        if ((acquired & 2) != 0 && second.getSessionStamp() == secondStamp) second.endItemTransfer();
        if (additional != null) for (int i = 0; i < additional.length; i++)
            if ((acquired & (1 << (i + 2))) != 0
                    && additional[i].getSessionStamp() == additionalStamps[i])
                additional[i].endItemTransfer();
    }

    void quarantine() {
        if (firstSession != null && firstSession.isConnected()) firstSession.kick();
        if (secondSession != null && secondSession.isConnected()) secondSession.kick();
        if (additionalSessions != null) for (CIOClient session : additionalSessions)
            if (session != null && session.isConnected()) session.kick();
    }
}
