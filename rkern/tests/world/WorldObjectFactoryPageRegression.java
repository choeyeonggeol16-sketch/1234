package l1j.server.server.model;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Standalone Java 21 regression. Checks retention structurally without depending on GC. */
public final class WorldObjectFactoryPageRegression {
    public static void main(String[] args) throws Exception {
        final L1ObjectFactory factory = new L1ObjectFactory();
        for (int i = 0; i < 128; i++) {
            int id = (i << 22) | (i << 12) | 7;
            synchronized (factory.slotLock(id)) {
                check(factory.find(id) == null && !factory.remove(actor(id)), "missing identity");
            }
        }
        checkPages(factory, 0, 0);
        final int id = 2_000_001;
        final Object originalLock = factory.slotLock(id);
        final L1Object first = actor(id), replacement = actor(id), neighbor = actor(id + 1);
        final L1Object nextPage = actor(id + (1 << 12));
        factory.install(first);
        long firstTicket = factory.smartIdOf(first);
        factory.install(first);
        check(factory.smartIdOf(id) == firstTicket, "repeat install preserves identity");
        factory.install(neighbor);
        factory.install(nextPage);
        checkPages(factory, 1, 2);
        factory.install(replacement);
        check(!factory.remove(first) && factory.findBySmartId(firstTicket) == null, "old registration retired");
        check(factory.remove(replacement) && factory.remove(neighbor), "remove last two on page");
        checkPages(factory, 1, 1);
        check(factory.values().size() == 1 && factory.values().iterator().next() == nextPage, "iteration after deletion");
        check(factory.remove(nextPage), "remove final page of group");
        checkPages(factory, 0, 0);
        factory.install(first);
        check(factory.slotLock(id) == originalLock && factory.findBySmartId(firstTicket) == null
                && factory.findBySmartId(factory.smartIdOf(first)) == first, "reattachment generation and stable mutex");
        factory.remove(first);
        for (int signed : new int[] {Integer.MIN_VALUE, -1, 0}) {
            final L1Object object = actor(signed);
            factory.install(object);
            long ticket = factory.worldIdentityOf(object);
            check(ticket != 0 && factory.worldIdentityOf(signed) == ticket && factory.findByWorldIdentity(ticket) == object
                    && factory.smartIdOf(object) == 0 && factory.findBySmartId(ticket) == null, "signed world ticket");
            factory.remove(object);
            factory.install(object);
            check(factory.findByWorldIdentity(ticket) == null, "stale signed ticket");
            factory.remove(object);
        }
        checkPages(factory, 0, 0);
        concurrent(factory, first, replacement);
        check(factory.size() == 0 && !factory.values().iterator().hasNext(), "empty registry");
        checkPages(factory, 0, 0);
        System.out.println("PASS sparse identity pages, live iterators, stable locks and generation-safe concurrent replacement");
    }

    private static void concurrent(L1ObjectFactory factory, L1Object first, L1Object second) throws Exception {
        final ExecutorService workers = Executors.newFixedThreadPool(4);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicLong firstTicket = new AtomicLong(), secondTicket = new AtomicLong();
        // Keep the contested page live while independently retiring pages in the same group.
        final L1Object neighbor = actor(first.getId() + 1);
        factory.install(neighbor);
        try {
            final Future<?> writer = workers.submit(() -> {
                start.await();
                for (int i = 0; i < 30_000; i++) {
                    factory.install(first);
                    firstTicket.set(factory.smartIdOf(first));
                    factory.install(second);
                    secondTicket.set(factory.worldIdentityOf(second));
                }
                return null;
            });
            final Future<?> reader = workers.submit(() -> {
                start.await();
                for (int i = 0; i < 100_000; i++) {
                    L1Object a = factory.findBySmartId(firstTicket.get());
                    L1Object b = factory.findByWorldIdentity(secondTicket.get());
                    check((a == null || a == first) && (b == null || b == second), "ticket resolved to different owner");
                }
                return null;
            });
            final Future<?> pageOne = shufflePages(workers, start, factory, 0);
            final Future<?> pageTwo = shufflePages(workers, start, factory, 1);
            start.countDown();
            for (Future<?> future : List.of(writer, reader, pageOne, pageTwo)) future.get(10, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
            factory.remove(first);
            factory.remove(second);
            factory.remove(neighbor);
        }
    }

    private static Future<?> shufflePages(ExecutorService workers, CountDownLatch start, L1ObjectFactory factory, int offset) {
        return workers.submit(() -> {
            start.await();
            for (int i = 0; i < 256; i++) {
                // Alternate separate top-level groups that have no permanently live neighbor.
                final int group = 1 + (i & 3);
                final L1Object actor = actor((group << 22) | (offset << 12) | i);
                factory.install(actor);
                check(factory.findBySmartId(factory.smartIdOf(actor)) == actor, "concurrent page publication");
                check(factory.remove(actor), "concurrent group retirement");
            }
            return null;
        });
    }

    private static void checkPages(L1ObjectFactory factory, int groups, int pages) throws Exception {
        int actualGroups = 0, actualPages = 0;
        for (Object group : (Object[]) field(factory, "groups")) {
            if (group == null) continue;
            actualGroups++;
            for (Object page : (Object[]) field(group, "pages")) {
                if (page != null) actualPages++;
            }
        }
        check(actualGroups == groups && actualPages == pages,
                "expected groups/pages " + groups + "/" + pages + ", found " + actualGroups + "/" + actualPages);
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static L1Object actor(int id) {
        L1Object actor = new L1Object();
        actor.setId(id);
        return actor;
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
