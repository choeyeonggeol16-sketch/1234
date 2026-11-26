package l1j.server.server.model.character.actionValidator;

import java.util.concurrent.TimeUnit;

import l1j.server.Config;

/** Movement cadence for one player action owner. Record only committed steps. */
public final class WalkDelayValidator {
    private static final long FALLBACK_INTERVAL_MILLIS = 100L;
    private static final long MAX_INTERVAL_MILLIS = 60_000L;

    private boolean hasStep;
    private int sessionStamp;
    private int mapId;
    private int x;
    private int y;
    private long stepCursorNanos;

    public long remainingNanos(int sessionStamp, int mapId, int x, int y,
            long nowNanos, long intervalMillis) {
        if (!Config.WALK_POSITION_CHECK || !matches(sessionStamp, mapId, x, y)) return 0L;

        final long intervalNanos = intervalNanos(intervalMillis);
        final long toleranceNanos = Math.min(intervalNanos / 2L,
                TimeUnit.MILLISECONDS.toNanos(Math.max(0L, Config.WALK_NETWORK_TOLERANCE)));
        final long untilDue = stepCursorNanos + intervalNanos - nowNanos;
        return untilDue > toleranceNanos ? untilDue - toleranceNanos : 0L;
    }

    public void recordStep(int sessionStamp, int mapId, int fromX, int fromY, int toX, int toY,
            long nowNanos, long intervalMillis) {
        long nextCursor = nowNanos;
        if (Config.WALK_POSITION_CHECK && matches(sessionStamp, mapId, fromX, fromY)) {
            final long due = stepCursorNanos + intervalNanos(intervalMillis);
            // Early arrivals spend their tolerance once. Late arrivals start from now.
            if (due - nowNanos > 0L) nextCursor = due;
        }
        hasStep = true;
        this.sessionStamp = sessionStamp;
        this.mapId = mapId;
        this.x = toX;
        this.y = toY;
        stepCursorNanos = nextCursor;
    }

    private boolean matches(int sessionStamp, int mapId, int x, int y) {
        return hasStep && this.sessionStamp == sessionStamp && this.mapId == mapId
                && this.x == x && this.y == y;
    }

    private static long intervalNanos(long intervalMillis) {
        final long bounded = intervalMillis <= 0L ? FALLBACK_INTERVAL_MILLIS
                : Math.min(intervalMillis, MAX_INTERVAL_MILLIS);
        return TimeUnit.MILLISECONDS.toNanos(bounded);
    }
}
