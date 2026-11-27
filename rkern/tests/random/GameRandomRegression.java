import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import l1j.server.server.shared.GameRandom;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.Dice;
import l1j.server.server.utils.IntRange;
import l1j.server.server.utils.NumberUtil;

public final class GameRandomRegression {
    public static void main(String[] args) throws Exception {
        check(!GameRandom.isWinning(1, 0) && GameRandom.isWinning(1, 1), "one outcome");
        check(!GameRandom.isWinning(100, -1) && GameRandom.isWinning(100, 101), "chance limits");
        check(GameRandom.next(Integer.MAX_VALUE, Integer.MAX_VALUE) == Integer.MAX_VALUE, "fixed max");
        check(GameRandom.nextIntOrFixed(-7, -7) == -7, "fixed effect value");
        check(GameRandom.nextIntOrFixed(-7, -6) == -7, "exclusive upper limit");
        check(GameRandom.next_double(1.15, 1.15) == 1.15, "decimal precision");
        check(GameRandom.next_double(-1.159, -1.145) == -1.15, "negative hundredths");
        check(GameRandom.spawnOffset(0) == 0 && GameRandom.spawnOffset(-1) == 0, "fixed spawn");
        expectInvalid(() -> GameRandom.isWinning(0, 0));
        expectInvalid(() -> GameRandom.next(2, 1));
        expectInvalid(() -> GameRandom.next_double(1.151, 1.159));

        var list = new ArrayList<>(List.of(1, 2, 2, 3, 4));
        GameRandom.shuffle(list);
        Collections.sort(list);
        check(list.equals(List.of(1, 2, 2, 3, 4)), "shuffle preserves every entry");
        check(UUID.fromString(GameRandom.receiptId()).version() == 4, "receipt UUID");
        long token = GameRandom.secureToken();
        check(token > 0 && token < Long.MAX_VALUE && GameRandom.secureIndex(1) == 0, "secure bounds");

        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> workers = Collections.nCopies(8, GameRandomRegression::draw);
            int misses = 0;
            for (var result : pool.invokeAll(workers)) misses += result.get();
            // 1M rolls at 99% should produce ~10,000 misses. The old denominator returned zero.
            check(misses >= 8_000 && misses <= 12_000, "99% misses in a million rolls: " + misses);
            System.out.println("PASS GameRandom, 8 workers, 1000000 rolls, misses=" + misses);
        }
    }

    private static int draw() {
        int misses = 0;
        int negativeEndpoints = 0;
        var dice = new Dice(6);
        var fullRange = new IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
        boolean sawPositive = false;
        boolean sawNegative = false;
        for (int i = 0; i < 125_000; i++) {
            if (!GameRandom.isWinning(100, 99)) misses++;
            int value = fullRange.randomValue();
            sawPositive |= value > 0;
            sawNegative |= value < 0;
            int endpoint = CommonUtil.random(-2, -1);
            check(endpoint == -2 || endpoint == -1, "negative integer bounds");
            negativeEndpoints |= 1 << (endpoint + 2);
            double rate = GameRandom.next_double(20.1, 21.0);
            check(rate >= 20.1 && rate <= 21 && Math.round(rate * 100) / 100.0 == rate, "race odds");
            int offset = GameRandom.spawnOffset(3);
            check(offset >= -2 && offset <= 2, "spawn radius");
            int rounded = NumberUtil.randomRound(-1.5);
            check(rounded == -2 || rounded == -1, "negative rounding");
            int face = dice.roll();
            check(face >= 1 && face <= 6, "dice");
        }
        check(sawPositive && sawNegative && negativeEndpoints == 3, "reachable endpoints");
        return misses;
    }

    private static void expectInvalid(Runnable call) {
        try {
            call.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("invalid range accepted");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
