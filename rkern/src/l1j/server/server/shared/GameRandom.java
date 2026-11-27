package l1j.server.server.shared;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Random draws for gameplay, with separate secure operations for identity and challenges. */
public final class GameRandom {
    private GameRandom() {}

    /** For existing APIs that accept an injected Random. Obtain it on the calling thread. */
    public static Random current() {
        return ThreadLocalRandom.current();
    }

    public static int nextInt(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    public static int nextInt(int origin, int bound) {
        return ThreadLocalRandom.current().nextInt(origin, bound);
    }

    /** Fixed configurations use their single value. Otherwise the upper bound is exclusive. */
    public static int nextIntOrFixed(int origin, int bound) {
        return origin == bound ? origin : nextInt(origin, bound);
    }

    public static long nextLong(long bound) {
        return ThreadLocalRandom.current().nextLong(bound);
    }

    public static long nextLong(long origin, long bound) {
        return ThreadLocalRandom.current().nextLong(origin, bound);
    }

    public static boolean nextBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    public static double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /** Difference of two uniform draws, retaining the triangular spawn distribution. */
    public static int spawnOffset(int range) {
        if (range == 0) return 0;
        if (range < 0) return nextInt(range + 1, 1) - nextInt(range + 1, 1);
        return nextInt(range) - nextInt(range);
    }

    public static void shuffle(List<?> values) {
        Collections.shuffle(values, ThreadLocalRandom.current());
    }

    public static int next(int bound) {
        return nextInt(bound);
    }

    /** Uniform draw including both endpoints, also for the full signed int range. */
    public static int next(int min, int max) {
        if (min == max) return min;
        if (max == Integer.MAX_VALUE) return (int) nextLong(min, (long) max + 1);
        return nextInt(min, max + 1);
    }

    /** Number of winning outcomes out of a positive total. */
    public static boolean isWinning(int whole, int dice) {
        if (whole <= 0) throw new IllegalArgumentException("whole must be positive");
        return dice > 0 && (dice >= whole || nextInt(whole) < dice);
    }

    public static boolean isBoolean() {
        return nextBoolean();
    }

    public static double next_double() {
        return nextDouble();
    }

    /** Uniform draw from the hundredth steps inside the closed interval. */
    public static double next_double(double min, double max) {
        if (!(min <= max && min >= Integer.MIN_VALUE / 100.0 && max <= Integer.MAX_VALUE / 100.0)) {
            throw new IllegalArgumentException("invalid hundredth range");
        }
        int first = (int) Math.round(min * 100);
        int last = (int) Math.round(max * 100);
        if (first / 100.0 < min) first++;
        if (last / 100.0 > max) last--;
        return next(first, last) / 100.0;
    }

    public static String receiptId() {
        return UUID.randomUUID().toString();
    }

    public static long secureToken() {
        return SecureSource.INSTANCE.nextLong(1L, Long.MAX_VALUE);
    }

    public static int secureIndex(int bound) {
        return SecureSource.INSTANCE.nextInt(bound);
    }

    private static final class SecureSource {
        private static final SecureRandom INSTANCE = new SecureRandom();
    }
}
