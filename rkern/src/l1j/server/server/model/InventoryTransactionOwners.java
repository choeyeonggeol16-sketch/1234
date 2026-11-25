package l1j.server.server.model;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

/** The owners of a durable receipt, captured once at admission.
 * Fixed fields keep the ordinary one/two-owner path allocation-light.
 * Gate release is idempotent, including a callback which starts a new receipt.
 * No per-player buffer, thread, queue or JDBC connection is retained here.
 */
final class InventoryTransactionOwners {
    private static final AtomicIntegerFieldUpdater<InventoryTransactionOwners> RESERVED =
            AtomicIntegerFieldUpdater.newUpdater(InventoryTransactionOwners.class, "reserved");

    private final L1PcInstance primary;
    private final L1PcInstance first, second;
    private final CUserSocket firstSession, secondSession;
    private final int firstStamp, secondStamp;
    private final int firstToken, secondToken;
    // Only party transactions allocate these arrays. One/two-owner hot paths remain flat.
    private final L1PcInstance[] additional;
    private final CUserSocket[] additionalSessions;
    private final int[] additionalStamps, additionalTokens;
    private volatile int reserved;

    InventoryTransactionOwners(L1PcInstance primary, L1PcInstance other, boolean trade) {
        this.primary = primary;
        boolean ordered = other == null || primary.getId() <= other.getId();
        first = ordered ? primary : other;
        second = ordered ? other : primary;

        final int firstIdentity = first.getSessionStamp();
        final CUserSocket firstConnection = captureSession(first);
        final int acquiredFirst = admit(first, trade, firstConnection, firstIdentity);
        CUserSocket secondConnection = null;
        int secondIdentity = 0, acquiredSecond = 0;
        try {
            if (second != null) {
                secondIdentity = second.getSessionStamp();
                secondConnection = captureSession(second);
                acquiredSecond = admit(second, trade, secondConnection, secondIdentity);
            }
        } catch (RuntimeException | Error failure) {
            first.endItemTransfer(acquiredFirst);
            throw failure;
        }
        firstToken = acquiredFirst;
        secondToken = acquiredSecond;
        firstSession = firstConnection;
        secondSession = secondConnection;
        firstStamp = firstIdentity;
        secondStamp = secondIdentity;
        additional = null;
        additionalSessions = null;
        additionalStamps = null;
        additionalTokens = null;
        reserved = 1;
    }

    /** Sorted admission gates for an atomic multi-player dungeon debit. */
    InventoryTransactionOwners(L1PcInstance primary, L1PcInstance[] players) {
        this.primary = java.util.Objects.requireNonNull(primary);
        if (players == null || players.length < 3)
            throw new IllegalArgumentException("Party transaction needs at least three owners");
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
        additionalSessions = new CUserSocket[additional.length];
        additionalStamps = new int[additional.length];
        additionalTokens = new int[additional.length];
        CUserSocket[] sessions = new CUserSocket[sorted.length];
        int[] stamps = new int[sorted.length];
        int[] tokens = new int[sorted.length];
        int taken = 0;
        try {
            for (int i = 0; i < sorted.length; i++) {
                stamps[i] = sorted[i].getSessionStamp();
                sessions[i] = captureSession(sorted[i]);
                tokens[i] = admit(sorted[i], false, sessions[i], stamps[i]);
                taken++;
            }
        } catch (RuntimeException | Error failure) {
            for (int i = 0; i < taken; i++)
                sorted[i].endItemTransfer(tokens[i]);
            throw failure;
        }
        firstSession = sessions[0];
        secondSession = sessions[1];
        firstStamp = stamps[0];
        secondStamp = stamps[1];
        firstToken = tokens[0];
        secondToken = tokens[1];
        for (int i = 0; i < additional.length; i++) {
            additionalSessions[i] = sessions[i + 2];
            additionalStamps[i] = stamps[i + 2];
            additionalTokens[i] = tokens[i + 2];
        }
        reserved = 1;
    }

    private static CUserSocket captureSession(L1PcInstance pc) {
        final CUserSocket session = pc.getNetConnection();
        return session != null && session.isConnected() && session.getActiveChar() == pc
                ? session : null;
    }

    private int admit(L1PcInstance pc, boolean trade, CUserSocket session, int stamp) {
        if ((trade && session == null) || !current(pc, session, stamp)
                || (!trade && (pc.getTradeID() != 0 || pc.isTradeResolving())))
            throw new IllegalStateException("Inventory owner unavailable or busy");
        final int token = pc.tryReserveItemTransfer();
        if (token == 0) throw new IllegalStateException("Inventory owner unavailable or busy");
        if (!current(pc, session, stamp)
                || (!trade && (pc.getTradeID() != 0 || pc.isTradeResolving()))) {
            pc.endItemTransfer(token);
            throw new IllegalStateException("Inventory owner changed during admission");
        }
        return token;
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

    private CUserSocket session(L1PcInstance pc) {
        if (pc == first) return firstSession;
        if (pc != null && pc == second) return secondSession;
        if (additional != null) for (int i = 0; i < additional.length; i++)
            if (pc == additional[i]) return additionalSessions[i];
        throw new IllegalArgumentException("Unreserved inventory owner");
    }

    Object monitor(L1PcInstance pc) {
        CUserSocket session = session(pc);
        return session == null ? pc.getInventory() : session;
    }

    boolean current(L1PcInstance pc) {
        return current(pc, session(pc), stamp(pc));
    }

    private boolean current(L1PcInstance pc, CUserSocket session, int stamp) {
        if (pc.isLogoutStarted() || pc.getSessionStamp() != stamp) return false;
        if (session == null) return pc != primary && pc.isPrivateShop()
                && captureSession(pc) == null
                && L1World.getInstance().findObject(pc.getId()) == pc;
        return session.isConnected() && session.getActiveChar() == pc && pc.getNetConnection() == session;
    }

    void release() {
        if (RESERVED.getAndSet(this, 0) == 0) return;
        first.endItemTransfer(firstToken);
        if (second != null) second.endItemTransfer(secondToken);
        if (additional != null) for (int i = 0; i < additional.length; i++)
            additional[i].endItemTransfer(additionalTokens[i]);
    }

    void quarantine() {
        // Never kick a socket that has since changed its active character.
        quarantine(first, firstSession, firstStamp);
        if (second != null) quarantine(second, secondSession, secondStamp);
        if (additional != null) for (int i = 0; i < additional.length; i++)
            quarantine(additional[i], additionalSessions[i], additionalStamps[i]);
    }

    private void quarantine(L1PcInstance pc, CUserSocket session, int stamp) {
        if (session == null || !current(pc, session, stamp)) return;
        l1j.server.server.model.network.thread.CIOThreadCallback.getInstance()
                .completeSession(session, () -> {
                    synchronized (session) {
                        if (current(pc, session, stamp)) session.kick();
                    }
                });
    }
}
