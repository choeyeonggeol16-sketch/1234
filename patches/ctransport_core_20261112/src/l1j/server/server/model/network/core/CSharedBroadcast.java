package l1j.server.server.model.network.core;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;

/** Shared recipient traversal; encoder payloads must be independent of the recipient. */
public final class CSharedBroadcast {
    private CSharedBroadcast() {}
    public static void send(L1Object anchor, byte[] wire, boolean fast, int radius,
            Predicate<L1PcInstance> filter, Consumer<CIOClient> legacy) {
        if (anchor == null) return;
        L1World world = L1World.getInstance();
        boolean borrowed = fast && radius < 0;
        List<L1PcInstance> recipients = borrowed ? world.borrowVisiblePlayerFast(anchor)
                : radius < 0 ? world.getVisiblePlayer(anchor) : world.getVisiblePlayer(anchor, radius);
        try (var shared = new CAsyncSendConstBufferExceptSelf(wire, anchor.getId(), Integer.MAX_VALUE)) {
            for (L1PcInstance pc : recipients) {
                // Recheck map/self after the AOI snapshot: teleport may have raced it.
                if (!CTLMFilter.sameWorldOther(anchor, pc) || filter != null && !filter.test(pc)) continue;
                CIOClient client = pc.getNetConnection();
                if (client != null && client.isConnected() && !shared.apply(client.getConnection(), pc.getId()))
                    legacy.accept(client);
            }
        } finally { if (borrowed) world.releaseVisiblePlayerFast(recipients); }
    }
}
