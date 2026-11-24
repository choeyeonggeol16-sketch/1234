package l1j.server.server.model;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class TestPledgeReplyChecker {
    private static void check(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
    }

    private static CReplyChecker.Pending attach(CReplyChecker.Registry r,
            int a, int b, int sa, int sb, long now) {
        return r.attach(a, b, null, null, sa, sb, null, null, 0, 7, 4, now);
    }

    public static void main(String[] args) throws Exception {
        CReplyChecker.Registry r = new CReplyChecker.Registry();
        long now = 1_000_000L;
        CReplyChecker.Pending p = attach(r, 1, 2, 3, 4, now);
        check(p != null && p.token > 0 && r.size() == 2, "two-side reservation");
        check(attach(r, 2, 1, 4, 3, now) == null, "reverse request");
        check(attach(r, 3, 2, 5, 4, now) == null, "shared recipient");
        check(attach(r, 1, 3, 3, 5, now) == null, "shared requester");
        check(r.finish(2, null, 4, 0, now) == null, "legacy zero token");
        check(r.finish(1, null, 3, p.token, now) == null, "wrong reply side");
        check(r.finish(2, null, 5, p.token, now) == null, "wrong generation");
        check(r.finish(2, null, 4, p.token + 1, now) == null, "wrong token");
        check(r.finish(2, null, 4, p.token, now + CReplyChecker.TIMEOUT_NS - 1) == p,
                "valid reply before deadline");
        check(r.size() == 0 && p.isCancelled(), "both mappings removed");
        check(r.finish(2, null, 4, p.token, now) == null, "duplicate response");
        CReplyChecker.Pending next = attach(r, 1, 2, 3, 4, now);
        check(next != null && next.token != p.token, "token not reused");
        p.run();
        check(r.size() == 2, "old wheel task cannot clear new request");
        check(r.finish(2, null, 4, p.token, now) == null, "late reply cannot consume new");
        r.cancelSession(2, null, 5);
        check(r.size() == 2, "stale logout ignored");
        r.cancelSession(1, null, 3);
        check(r.size() == 0, "requester logout cancels both");
        CReplyChecker.Pending timeout = attach(r, 1, 2, 3, 4, now);
        check(r.finish(2, null, 4, timeout.token, now + CReplyChecker.TIMEOUT_NS) == null,
                "deadline equality expires");
        check(r.size() == 0, "expired reply removes both");
        timeout = attach(r, 1, 2, 3, 4, now);
        CReplyChecker.Pending replacement = attach(r, 3, 2, 5, 4, now + CReplyChecker.TIMEOUT_NS);
        check(replacement != null && timeout.isCancelled() && r.size() == 2, "lazy expiry");
        timeout.run();
        check(r.size() == 2, "late expiry cannot remove replacement");
        r.cancelSession(2, null, 4);
        check(r.size() == 0, "replier logout cancels both");
        for (int round = 0; round < 100; round++) {
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger wins = new AtomicInteger();
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread[] threads = new Thread[8];
            for (int t = 0; t < threads.length; t++) {
                final int id = 10 + t;
                threads[t] = new Thread(() -> {
                    try {
                        go.await();
                        if (attach(r, id, 100, 1, 1, now) != null) wins.incrementAndGet();
                    } catch (Throwable failure) { error.compareAndSet(null, failure); }
                });
                threads[t].start();
            }
            go.countDown();
            for (Thread t : threads) t.join();
            check(error.get() == null && wins.get() == 1 && r.size() == 2, "concurrent single winner");
            r.cancelSession(100, null, 1);
            check(r.size() == 0, "no mapping leak after race");
        }
        System.out.println("PASS pledge reply checker: identity, tokens, deadline, cancellation, stale delivery and 100 concurrent races");
    }
}
