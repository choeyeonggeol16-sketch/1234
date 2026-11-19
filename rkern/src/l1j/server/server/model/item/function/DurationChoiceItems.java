package l1j.server.server.model.item.function;

import java.util.Random;
import l1j.server.Config;

/** One current configuration per skill family; no player or item references. */
final class DurationChoiceItems {
    private static final Choices BLAZE = new Choices();
    private static final Choices ELVEN = new Choices();
    private static final Choices DEMOLITION = new Choices();

    private DurationChoiceItems() {}

    static int blaze(Random random) { return BLAZE.choose(Config.STUN_SKILL_RND_TIME, random); }
    static int elven(Random random) { return ELVEN.choose(Config.STUN_SKILL_RND_TIME111, random); }
    static int demolition(Random random) { return DEMOLITION.choose(Config.STUN_SKILL_RND_TIME11, random); }

    static final class Choices {
        private volatile Entry current;

        int choose(String csv, Random random) {
            Entry entry = current;
            if (entry == null || !csv.equals(entry.csv)) {
                entry = new Entry(csv, csv.split(","));
                current = entry;
            }
            // Parse only the selected token, preserving the old malformed-value behavior.
            return Integer.parseInt(entry.values[random.nextInt(entry.values.length)]);
        }
    }

    private record Entry(String csv, String[] values) {}
}
