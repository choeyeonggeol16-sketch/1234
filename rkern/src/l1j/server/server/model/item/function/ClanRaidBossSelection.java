package l1j.server.server.model.item.function;



final class ClanRaidBossSelection {
    private ClanRaidBossSelection() {}
    private static final int[] EMPTY_BOSS_IDS = new int[0];
    private static volatile BossList cachedBossList;

    private static final class BossList {
        final String config;
        final int[] ids;
        BossList(String config, int[] ids) {
            this.config = config;
            this.ids = ids;
        }
    }
    static int[] configuredBossIds(String csv) {
        final BossList known = cachedBossList;
        if (known != null && (known.config == csv
                || (known.config != null && known.config.equals(csv)))) {
            return known.ids;
        }
        final int[] parsed = parseBossIds(csv);
        cachedBossList = new BossList(csv, parsed);
        return parsed;
    }

    static int[] parseBossIds(String csv) {
        if (csv == null || csv.isEmpty()) return EMPTY_BOSS_IDS;

        // The old split/trim/parse loop allocated a String[] and a String
        // for every token. A direct scan preserves trim() and parseInt()
        // semantics while creating only the final primitive ID array.
        final int length = csv.length();
        // Count nonblank fields, not separators: a malformed config containing
        // millions of commas must not allocate a millions-entry int array.
        int capacity = 0;
        int fieldStart = 0;
        for (int i = 0; i <= length; ++i) {
            if (i != length && csv.charAt(i) != ',') continue;
            int first = fieldStart;
            int last = i;
            while (first < last && csv.charAt(first) <= ' ') ++first;
            while (first < last && csv.charAt(last - 1) <= ' ') --last;
            if (first < last) ++capacity;
            fieldStart = i + 1;
        }
        if (capacity == 0) return EMPTY_BOSS_IDS;
        final int[] parsed = new int[capacity];
        int count = 0;
        int begin = 0;
        for (int end = 0; end <= length; ++end) {
            if (end != length && csv.charAt(end) != ',') continue;
            int first = begin;
            int last = end;
            // String.trim() removes code units <= U+0020 (not Unicode space).
            while (first < last && csv.charAt(first) <= ' ') ++first;
            while (first < last && csv.charAt(last - 1) <= ' ') --last;
            if (first < last) {
                try {
                    final int id = Integer.parseInt(csv, first, last, 10);
                    if (id > 0) parsed[count++] = id;
                } catch (NumberFormatException ignore) {
                    // Retain the original silent skip for invalid boss IDs.
                }
            }
            begin = end + 1;
        }
        if (count == 0) return EMPTY_BOSS_IDS;
        if (count == parsed.length) return parsed;
        final int[] exact = new int[count];
        System.arraycopy(parsed, 0, exact, 0, count);
        return exact;
    }
}
