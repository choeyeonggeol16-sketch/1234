package l1j.server.server.model.network.core;

/**
 * Producer-thread network shard identity.
 *
 * PcShard owner threads publish their stable shard id once on thread entry.
 * Network hot paths can then select an SPSC producer pipe without depending on
 * the game scheduler package or allocating per command. Integer.valueOf() for
 * the small shard ids is cached by the JDK, so enterShard() does not allocate.
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
}
