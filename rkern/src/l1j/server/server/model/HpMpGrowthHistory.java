package l1j.server.server.model;

/** Per-character earned deltas. Save together with level/HP/MP in one characters row.
 * The legacy floor is historical data we do not possess, never a guessed reroll. */
public final class HpMpGrowthHistory {
    public static final int MAX_LEVEL = 127;
    private final int floorLevel;
    private final int[] hp = new int[MAX_LEVEL + 1];
    private final int[] mp = new int[MAX_LEVEL + 1];
    private final boolean[] known = new boolean[MAX_LEVEL + 1];

    public HpMpGrowthHistory(int baselineLevel) {
        if (baselineLevel < 1 || baselineLevel > MAX_LEVEL)
            throw new IllegalArgumentException("Invalid growth baseline level");
        floorLevel = baselineLevel;
    }
    public int floorLevel() { return floorLevel; }
    public boolean known(int level) { checkLevel(level); return known[level]; }
    public int hp(int level) { checkKnown(level); return hp[level]; }
    public int mp(int level) { checkKnown(level); return mp[level]; }
    public void record(int level, int hpDelta, int mpDelta) {
        checkLevel(level);
        if (level <= floorLevel || hpDelta < 0 || hpDelta > 32767 || mpDelta < 0 || mpDelta > 32767)
            throw new IllegalArgumentException("Invalid earned growth delta");
        if (known[level]) throw new IllegalStateException("Growth already recorded");
        hp[level] = hpDelta; mp[level] = mpDelta; known[level] = true;
    }
    private static void checkLevel(int level) {
        if (level < 1 || level > MAX_LEVEL) throw new IllegalArgumentException("Invalid growth level");
    }
    private void checkKnown(int level) {
        checkLevel(level);
        if (!known[level]) throw new IllegalStateException("No historical growth for level " + level);
    }
    public String encode() {
        StringBuilder out = new StringBuilder("1:").append(floorLevel);
        for (int i = floorLevel + 1; i <= MAX_LEVEL; i++) {
            if (known[i]) out.append(';').append(i).append(',').append(hp[i]).append(',').append(mp[i]);
        }
        return out.toString();
    }
    public static HpMpGrowthHistory decode(String text) {
        if (text == null || text.isEmpty()) return null;
        if (text.length() > 8192) throw new IllegalArgumentException("Growth history too long");
        String[] entries = text.split(";", -1);
        String[] header = entries[0].split(":", -1);
        if (header.length != 2 || !"1".equals(header[0])) throw new IllegalArgumentException("Unknown growth history version");
        HpMpGrowthHistory history = new HpMpGrowthHistory(Integer.parseInt(header[1]));
        int expected = history.floorLevel + 1;
        for (int i = 1; i < entries.length; i++) {
            String[] parts = entries[i].split(",", -1);
            if (parts.length != 3) throw new IllegalArgumentException("Malformed growth history");
            int level = Integer.parseInt(parts[0]);
            if (level != expected++) throw new IllegalArgumentException("Nonconsecutive growth history");
            history.record(level, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        return history;
    }
}
