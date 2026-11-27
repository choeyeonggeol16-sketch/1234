package l1j.server.server.model.world.instance.dungeon.time.updator;

import java.time.Instant;

public final class DungeonResetBoundaryRegression {
    public static void main(String[] args) {
        long reset = Instant.parse("2026-11-26T23:59:00Z").getEpochSecond();
        check(DungeonTimeUpdator.resetBoundary(reset - 1, 8, 59) == reset - 86_400);
        check(DungeonTimeUpdator.resetBoundary(reset, 8, 59) == reset);
        check(DungeonTimeUpdator.resetBoundary(reset + 9, 8, 59) == reset);
        check(DungeonTimeUpdator.resetBoundary(reset + 3 * 86_400 + 9, 8, 59) == reset + 3 * 86_400);
        long midnight = Instant.parse("2026-11-27T15:00:00Z").getEpochSecond();
        check(DungeonTimeUpdator.resetBoundary(midnight - 1, 0, 0) == midnight - 86_400);
        check(DungeonTimeUpdator.resetBoundary(midnight + 1, 0, 0) == midnight);
        System.out.println("PASS KST reset boundaries, delayed ticks and multi-day gaps");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Wrong reset period");
    }
}
