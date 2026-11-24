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
        check(r.finish(2, null, 4, p.token, now) == null, "no reply before DB check");
        check(r.publish(p, now), "publish checked invitation");
        check(!r.publish(p, now), "duplicate completion");
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
        check(r.publish(timeout, now), "publish timeout");
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
        CReplyChecker.Pending preflight = attach(r, 1, 2, 3, 4, now);
        check(attach(r, 1, 3, 3, 5, now) == null, "DB in-flight blocks duplicate");
        r.cancelSession(1, null, 3);
        CReplyChecker.Pending auto = attach(r, 1, 2, 3, 4, now);
        check(!r.publish(preflight, now), "stale DB callback cannot publish");
        check(!r.claimAuto(preflight, now), "stale DB callback cannot admit");
        check(r.claimAuto(auto, now) && r.size() == 0, "auto admission claims once");
        check(!r.claimAuto(auto, now), "duplicate auto admission");
        CReplyChecker.Pending expired = attach(r, 1, 2, 3, 4, now);
        check(!r.publish(expired, now + CReplyChecker.TIMEOUT_NS) && r.size() == 0,
                "expired preflight releases both");
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
        testAccountCheck();
        System.out.println("PASS pledge reply checker: identity, DB preflight, account restriction, deadline, cancellation and 100 concurrent races");
    }

    private static void testAccountCheck() throws Exception {
        check(accountRows(new int[][]{{1,0},{2,7},{3,0}}, false), "same clan or no clan");
        check(accountRows(new int[][]{{1,0},{2,9}}, true) == false, "conflicting clan");
        check(!accountRows(new int[][]{{2,7}}, false), "requester row missing");
        check(!accountRows(new int[][]{{1,7}}, true), "requester clan changed");
        check(!accountRows(new int[][]{{1,0},{1,0}}, false), "duplicate requester row");
    }

    private static boolean accountRows(int[][] rows, boolean lock) throws Exception {
        int[] cursor = {-1};
        java.sql.ResultSet rs = (java.sql.ResultSet) java.lang.reflect.Proxy.newProxyInstance(
                TestPledgeReplyChecker.class.getClassLoader(), new Class<?>[]{java.sql.ResultSet.class},
                (o,m,a) -> switch(m.getName()) {
                    case "next" -> ++cursor[0] < rows.length;
                    case "getInt" -> rows[cursor[0]][(Integer)a[0]-1];
                    case "close" -> null;
                    default -> throw new AssertionError("Unexpected ResultSet: " + m.getName());
                });
        java.sql.PreparedStatement ps = (java.sql.PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(
                TestPledgeReplyChecker.class.getClassLoader(), new Class<?>[]{java.sql.PreparedStatement.class},
                (o,m,a) -> switch(m.getName()) {
                    case "setString" -> {
                        check((Integer)a[0] == 1 && "account".equals(a[1]), "account parameter binding");
                        yield null;
                    }
                    case "executeQuery" -> rs;
                    case "close" -> null;
                    default -> throw new AssertionError("Unexpected statement: " + m.getName());
                });
        java.sql.Connection con = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                TestPledgeReplyChecker.class.getClassLoader(), new Class<?>[]{java.sql.Connection.class},
                (o,m,a) -> {
                    if (!m.getName().equals("prepareStatement")) throw new AssertionError(m.getName());
                    String sql = (String)a[0];
                    check(sql.contains("account_name=?") && sql.contains("FOR UPDATE") == lock,
                            "account SQL and lock mode");
                    return ps;
                });
        return L1ClanJoin.CheckAccountPledge(con, "account", 1, 0, 7, lock);
    }
}
