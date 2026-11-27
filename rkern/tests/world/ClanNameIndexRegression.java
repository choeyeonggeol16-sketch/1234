package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import l1j.server.server.model.world.siege.War;
import l1j.server.server.model.world.siege.WarFactory.WAR_TYPE;

/** Exercises the public world facade with no database or network. */
public final class ClanNameIndexRegression {
    public static void main(String[] args) throws Exception {
        WorldShapeRegression.installMapDefinitions();
        final CWorld world = CWorld.getInstance();
        verifyIdentity(world);
        verifyMatching(world);
        verifyConcurrentReplacement(world);
        verifyWarRetirement(world);
        System.out.println("PASS clan name parity, order, mutation/reload, concurrent replacement and war retirement");
    }

    private static void verifyIdentity(CWorld world) {
        final Collection<L1Clan> legacyView = world.getAllClans();
        final L1Clan negative = clan(-1, "Pledge");
        final L1Clan high = clan(Integer.MIN_VALUE, "pLEDGE");
        final L1Clan low = clan(7, "pledge");
        for (L1Clan clan : List.of(negative, high, low)) world.storeClan(clan);
        require(List.copyOf(legacyView).equals(List.of(low, high, negative)), "unsigned ID traversal");
        require(world.findClan("PLEDGE") == low, "lowest unsigned ID wins duplicate name");

        final L1Clan replacement = clan(7, "Pledge");
        world.storeClan(replacement);
        world.removeClan(low);
        low.setClanName("obsolete");
        low.setClanId(8);
        require(world.getClan(7) == replacement && world.findClan("pledge") == replacement,
                "stale remove and detached edits preserve same-ID replacement");
        require(world.getClan(8) == null && world.findClan("obsolete") == null, "detached object is not indexed");

        replacement.setClanName("renamed");
        require(world.findClan("pledge") == high && world.findClan("RENAMED") == replacement,
                "rename exposes the remaining duplicate and new name");
        negative.setClanId(3);
        require(world.getClan(-1) == null && world.getClan(3) == negative && world.findClan("pledge") == negative,
                "rekey changes ID and duplicate priority together");
        replacement.setClanId(3);
        world.removeClan(negative);
        require(world.getClan(7) == null && world.getClan(3) == replacement
                && world.findClan("renamed") == replacement && world.findClan("pledge") == high,
                "rekey replaces an occupied ID without stale names");

        replacement.setClanName(null);
        require(world.getClan(3) == replacement && world.findClan("renamed") == null,
                "an unnamed clan is available only by ID");
        replacement.setClanName("named again");
        require(world.findClan("NAMED AGAIN") == replacement, "name assigned after registration becomes visible");

        world.clearClan();
        high.setClanName("detached");
        require(legacyView.isEmpty() && world.getClan(3) == null && world.findClan("named again") == null
                && world.findClan("detached") == null, "clear removes both indexes and detaches objects");
        final L1Clan reloaded = clan(3, "named again");
        world.storeClan(reloaded);
        world.removeClan(replacement);
        require(legacyView.contains(reloaded) && world.findClan("named again") == reloaded,
                "reload repopulates the existing read-only view and protects fresh identities");
        try {
            legacyView.clear();
            throw new AssertionError("clan view must remain read only");
        } catch (UnsupportedOperationException expected) {
            // Existing callers receive an unmodifiable live collection.
        }
        world.clearClan();
        try {
            world.findClan(null);
            throw new AssertionError("null lookup must still fail");
        } catch (NullPointerException expected) {
            // The previous world facade rejected null through String.length().
        }
    }

    private static void verifyMatching(CWorld world) {
        final Locale original = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            final List<String> names = List.of("I", "i", "\u0130", "\u0131", "\u03A3", "\u03C3", "\u03C2",
                    "\uD801\uDC00", "\uD801\uDC28", "\u00DF", "SS", " ", "", " a", "a", "한글");
            for (int i = 0; i < names.size(); i++) {
                world.storeClan(clan((i + 1) * 0x9e37_79b9, names.get(i)));
            }
            final List<String> queries = new ArrayList<>(names);
            queries.addAll(List.of("sS", "A", "a ", "없는혈맹", "z"));
            for (String query : queries) {
                final L1Clan legacy = world.getAllClans().stream()
                        .filter(clan -> clan.getClanName().equalsIgnoreCase(query)).findFirst().orElse(null);
                require(world.findClan(query) == legacy, "Unicode/locale name predicate differs for " + query);
            }
        } finally {
            Locale.setDefault(original);
            world.clearClan();
        }
    }

    private static void verifyConcurrentReplacement(CWorld world) throws InterruptedException {
        final L1Clan first = clan(1, "repeat");
        final L1Clan second = clan(1, "REPEAT");
        final L1Clan fallback = clan(-1, "repeat");
        world.storeClan(first);
        world.storeClan(fallback);
        final CountDownLatch ready = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread reader = new Thread(() -> {
            ready.countDown();
            try {
                for (int i = 0; i < 15_000; i++) {
                    final L1Clan actual = world.findClan("RePeAt");
                    require(actual == first || actual == second, "same-name replacement drops the current winner");
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "clan-name-fixture-reader");
        reader.setDaemon(true);
        reader.start();
        ready.await();
        for (int i = 0; i < 15_000; i++) {
            world.storeClan((i & 1) == 0 ? second : first);
            first.setClanName((i & 1) == 0 ? "REPEAT" : "repeat");
        }
        reader.join(5_000);
        require(!reader.isAlive() && failure.get() == null, "concurrent lookup failed: " + failure.get());
        world.clearClan();
        require(world.findClan("repeat") == null, "clear removes the winner after concurrent replacement");

        world.storeClan(first);
        world.clear();
        final CWorld current = CWorld.getInstance();
        require(current.findClan("repeat") == null && current.getAllClans().isEmpty(), "new world has its own index");
        current.storeClan(first);
        first.setClanName("moved");
        require(world.getAllClans().isEmpty() && current.findClan("moved") == first,
                "a republished clan leaves its retired registry and rekeys in the current world");
        current.clearClan();
    }

    private static void verifyWarRetirement(CWorld previousWorld) {
        final CWorld world = CWorld.getInstance();
        require(world != previousWorld && world.createWarStream().findAny().isEmpty(), "empty war stream after world clear");
        final L1Clan clan = clan(1, "defense");
        final War stale = War.newInstance(clan, WAR_TYPE.NORMAL, 4);
        final War current = War.newInstance(clan, WAR_TYPE.NORMAL, 4);
        require(world.removeWar(stale) == null, "stale war cannot unpublish a same-ID replacement");
        stale.dispose();
        require(world.createWarStream().toList().equals(List.of(current)) && clan.getCurrentWar() == current,
                "disposing obsolete war preserves newer registry and participant links");
        require(world.removeWar(current) == current, "exact removal returns its war");
        current.dispose();
        require(world.createWarStream().findAny().isEmpty() && clan.getCurrentWar() == null,
                "normal disposal empties registry and participant link");
    }

    private static L1Clan clan(int id, String name) {
        final L1Clan clan = new L1Clan();
        clan.setClanId(id);
        clan.setClanName(name);
        return clan;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
