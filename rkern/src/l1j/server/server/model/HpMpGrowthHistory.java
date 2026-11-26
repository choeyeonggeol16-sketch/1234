package l1j.server.server.model;

import java.util.Arrays;

/** Per-character earned deltas. Save together with level/HP/MP in one characters row.
 * The legacy floor is historical data we do not possess, never a guessed reroll. */
public final class HpMpGrowthHistory {
    public static final int MAX_LEVEL = 127;
    private static final int MAX_DELTA = 32767;
    private static final int[] EMPTY = {};
    private final int floorLevel;
    // Both deltas fit in 15 bits. Zero is unrecorded, including unused capacity.
    private int[] gains = EMPTY;
    private String encoded;

    public HpMpGrowthHistory(int baselineLevel) {
        checkLevel(baselineLevel);
        floorLevel = baselineLevel;
    }

    public int floorLevel() { return floorLevel; }

    private int entry(int level) {
        checkLevel(level);
        int index = level - floorLevel - 1;
        return index >= 0 && index < gains.length ? gains[index] : 0;
    }

    public boolean known(int level) { return entry(level) != 0; }
    public int hp(int level) { return unpack(level) >>> 15; }
    public int mp(int level) { return unpack(level) & MAX_DELTA; }

    private int unpack(int level) {
        int value = entry(level);
        if (value == 0) throw new IllegalStateException("No historical growth for level " + level);
        return value - 1;
    }

    public void record(int level, int hpDelta, int mpDelta) {
        checkLevel(level);
        if (level <= floorLevel || hpDelta < 0 || hpDelta > MAX_DELTA || mpDelta < 0 || mpDelta > MAX_DELTA)
            throw new IllegalArgumentException("Invalid earned growth delta");
        int index = level - floorLevel - 1;
        if (index >= gains.length) {
            int capacity = Math.max(index + 1, Math.max(4, gains.length * 2));
            gains = Arrays.copyOf(gains, Math.min(capacity, MAX_LEVEL - floorLevel));
        } else if (gains[index] != 0) {
            throw new IllegalStateException("Growth already recorded");
        }
        gains[index] = ((hpDelta << 15) | mpDelta) + 1;
        encoded = null;
    }

    private static void checkLevel(int level) {
        if (level < 1 || level > MAX_LEVEL) throw new IllegalArgumentException("Invalid growth level");
    }

    public String encode() {
        if (encoded != null) return encoded;
        StringBuilder out = new StringBuilder("1:").append(floorLevel);
        for (int i = 0; i < gains.length; i++) {
            if (gains[i] == 0) continue;
            int pair = gains[i] - 1;
            out.append(';').append(floorLevel + 1 + i).append(',').append(pair >>> 15)
                    .append(',').append(pair & MAX_DELTA);
        }
        return encoded = out.toString();
    }

    public static HpMpGrowthHistory decode(String text) {
        if (text == null || text.isEmpty()) return null;
        if (text.length() > 8192) throw new IllegalArgumentException("Growth history too long");
        if (!text.startsWith("1:")) throw new IllegalArgumentException("Unknown growth history version");
        int end = text.indexOf(';');
        if (end < 0) end = text.length();
        HpMpGrowthHistory history = new HpMpGrowthHistory(Integer.parseInt(text, 2, end, 10));
        int expected = history.floorLevel + 1;
        while (end < text.length()) {
            int start = end + 1;
            end = text.indexOf(';', start);
            if (end < 0) end = text.length();
            int first = text.indexOf(',', start);
            int second = first < 0 ? -1 : text.indexOf(',', first + 1);
            if (first < 0 || second < 0 || second >= end)
                throw new IllegalArgumentException("Malformed growth history");
            int level = Integer.parseInt(text, start, first, 10);
            if (level != expected++) throw new IllegalArgumentException("Nonconsecutive growth history");
            history.record(level, Integer.parseInt(text, first + 1, second, 10),
                    Integer.parseInt(text, second + 1, end, 10));
        }
        return history;
    }
}
