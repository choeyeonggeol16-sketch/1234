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
        if (csv == null) {
            return EMPTY_BOSS_IDS;
        }

        final String text = csv.trim();

        if (text.length() == 0) {
            return EMPTY_BOSS_IDS;
        }

        final String[] tokens = text.split(",");
        final int[] temp = new int[tokens.length];

        int count = 0;

        for (int i = 0; i < tokens.length; i++) {
            final String token = tokens[i].trim();

            if (token.length() == 0) {
                continue;
            }

            try {
                // Parse before advancing count: a failed parse must never
                // leave an implicit NPC id 0 in the boss selection array.
                final int npcId = Integer.parseInt(token);
                if (npcId > 0) temp[count++] = npcId;
            } catch (NumberFormatException ignore) {
            }
        }

        if (count == temp.length) {
            return temp;
        }

        if (count == 0) {
            return EMPTY_BOSS_IDS;
        }

        final int[] result = new int[count];
        System.arraycopy(temp, 0, result, 0, count);

        return result;
    }
}
