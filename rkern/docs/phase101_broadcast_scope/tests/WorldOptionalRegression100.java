package l1j.server.server.model.network.core.broadcast;

import java.util.*;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

/** Real production shared broadcast plus the existing typed world/socket fixture. */
public final class WorldOptionalRegression100 {
    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
    static Iterable<L1PcInstance> brokenAfter(L1PcInstance first) {
        return () -> new Iterator<>() {
            boolean once;
            public boolean hasNext() {
                if (once) throw new ConcurrentModificationException("injected iterator invalidation");
                return true;
            }
            public L1PcInstance next() { once = true; return first; }
        };
    }
    static Iterable<L1PcInstance> brokenImmediately() {
        return () -> { throw new IllegalStateException("iterator creation failed"); };
    }
    public static void main(String[] args) {
        final CIOServer owner = new CIOServer();
        final CUserSocket firstSocket = new CUserSocket(owner);
        final CUserSocket secondSocket = new CUserSocket(owner);
        final L1PcInstance first = new L1PcInstance(10, 1, new CIOClient(firstSocket));
        final L1PcInstance second = new L1PcInstance(11, 1, new CIOClient(secondSocket));
        CSharedBroadcast.sendWrittenGroupsBestEffort(
                brokenAfter(first), List.of(second), w -> w.put((byte)0x65));
        check(firstSocket.frames.size() == 1, "first group first frame lost");
        check(secondSocket.frames.size() == 1, "second group skipped on iterator invalidation");
        check(firstSocket.frames.get(0)[2] == 0x65 && secondSocket.frames.get(0)[2] == 0x65,
                "same frozen packet not reused");
        check(firstSocket.nativeAllocs == 1 && secondSocket.nativeAllocs == 0,
                "unexpected duplicate Native source");
        firstSocket.drain(); secondSocket.drain();
        check(owner.releases == 1, "Native source not returned exactly once");
        CSharedBroadcast.sendWrittenRecipientsBestEffort(
                brokenAfter(first), w -> w.put((byte)0x67));
        check(firstSocket.frames.size() == 2,
                "optional recipients propagated iterator failure");
        firstSocket.drain();
        check(owner.releases == 2, "recipient-list source not released");
        CSharedBroadcast.sendWrittenGroupsBestEffort(
                brokenImmediately(), List.of(second), w -> w.put((byte)0x66));
        check(secondSocket.frames.size() == 2 && secondSocket.frames.get(1)[2] == 0x66,
                "second group skipped after first iterator constructor failed");
        secondSocket.drain();
        check(owner.releases == 3, "second Native source leak");
        System.out.println("100 production optional groups: iterator failure isolation, second-group FIFO, one-source refcount (PASS)");
    }
}
