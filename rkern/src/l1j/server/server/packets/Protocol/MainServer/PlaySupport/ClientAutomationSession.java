package l1j.server.server.packets.Protocol.MainServer.PlaySupport;

/** One connection-owned lease. No player/item reference is retained. */
public record ClientAutomationSession(int mode, int playerStamp, int mapId,
        long deadlineNanos, int quotaCharId, long quotaDayStartMs, long quotaToken, long quotaExpiresMs, long quotaNextResetMs) {
    public static final ClientAutomationSession SERVER = new ClientAutomationSession(0, 0, 0, 0L);

    public ClientAutomationSession(int mode, int playerStamp, int mapId, long deadlineNanos) {
        this(mode, playerStamp, mapId, deadlineNanos, 0, 0L, 0L, 0L, 0L);
    }

    public ClientAutomationSession(int mode, int playerStamp, int mapId, long deadlineNanos,
            int quotaCharId, long quotaDayStartMs, long quotaToken) {
        this(mode, playerStamp, mapId, deadlineNanos, quotaCharId, quotaDayStartMs, quotaToken, 0L, 0L);
    }

    public boolean clientDriven() { return this != SERVER; }

    public int remainingSeconds(long nowNanos) {
        long remaining = deadlineNanos - nowNanos;
        return remaining <= 0L ? 0 : (int) ((remaining + 999_999_999L) / 1_000_000_000L);
    }
}
