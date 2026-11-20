package l1j.server.server.model.network.core.connection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class CIOBufferPoolAffinityTest {
    public static void main(String[] args) throws Exception {
        var bitmap = new CIOSlotPool(4096, 192, 0);
        check(bitmap.acquireNearShard(0, 3) == 0, "first shard starts at the first word");
        check(bitmap.acquireNearShard(1, 3) == 64, "second shard starts at its own word");
        check(bitmap.acquireNearShard(2, 3) == 128, "third shard starts at its own word");

        var pool = new CIOBufferPool(130, 1, 2, 1, 2);
        var leases = new ArrayList<CIOBufferEx>();
        try (var foreign = Executors.newSingleThreadExecutor()) {
            leases.add(pool.acquireRecv(0));
            CIOBufferEx remote = pool.acquireRecv(1);
            var buffer = remote.buffer();
            foreign.submit(() -> pool.release(remote)).get(5, TimeUnit.SECONDS);
            CIOBufferEx again = pool.acquireRecv(1);
            check(again.buffer() == buffer, "returned region preferred and reused by its shard");
            leases.add(again);
            for (int i = 2; i < 130; i++) {
                CIOBufferEx lease = pool.acquireRecv(0);
                check(lease != null, "hot shard can use the complete global budget");
                leases.add(lease);
            }
            check(pool.acquireRecv(0) == null && pool.acquireRecv(1) == null, "hard capacity is global");
            check(pool.allocatedDirectBytes() == 130L * CIOBufferPool.RECV_BYTES, "one backing buffer per slot");
            foreign.submit(() -> leases.forEach(pool::release)).get(5, TimeUnit.SECONDS);
            check(Arrays.stream(pool.snapshot()).allMatch(row -> row[2] == 0 && row[3] == row[4]),
                    "cross-thread return balances every bucket");
        }
        System.out.println("PASS shard-local receive preference, global overflow capacity and cross-thread return");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
