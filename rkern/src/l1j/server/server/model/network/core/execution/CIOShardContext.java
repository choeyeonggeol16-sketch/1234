package l1j.server.server.model.network.core.execution;

/**
 * Logical network shard identity of the current executor.
 *
 * Fixed owners publish the identity on thread entry. Shared workers publish it
 * for one exclusive turn. The JDK caches the small boxed shard IDs.
 */
public final class CIOShardContext {

    private static final ThreadLocal<Integer> CURRENT_SHARD =
            new ThreadLocal<>();

    private CIOShardContext() {
    }

    public static int currentShardIndex() {
        final Integer shard =
                CURRENT_SHARD.get();

        return shard != null
                ? shard.intValue()
                : -1;
    }

    public static void enterShard(int shardIndex) {
        if (shardIndex < 0) {
            throw new IllegalArgumentException(
                    "shardIndex < 0");
        }

        CURRENT_SHARD.set(
                Integer.valueOf(shardIndex));
    }

    public static void exitShard() {
        CURRENT_SHARD.remove();
    }

    /** Leave a shared turn while retaining this worker's ThreadLocal slot. */
    public static void leaveSharedTurn() {
        CURRENT_SHARD.set(null);
    }
}
