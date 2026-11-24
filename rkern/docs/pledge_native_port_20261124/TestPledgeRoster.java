package l1j.server.server.model;

public final class TestPledgeRoster {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        L1Clan clan = new L1Clan();
        clan.addLoadedClanMember("Alpha", 7, 20, "old", 10, 1);
        clan.addLoadedClanMember("Beta", 8, 30, "", 11, 2);
        clan.addLoadedClanMember("Alpha", 9, 21, "new", 10, 1);
        check(clan.getClanMemberList().size() == 2, "duplicate ID");
        L1Clan.ClanMember alpha = clan.getClanMemberList().get(0);
        check(alpha.rank == 9 && alpha.level == 21 && alpha.notes.equals("new"), "duplicate refresh");
        check(clan.UpdateMemberMemo(10, "memo"), "memo update");
        check(clan.getClanMemberList().get(0) == alpha && alpha.notes.equals("memo"), "memo does not remove/reinsert");
        check(!clan.UpdateMemberMemo(100, "missing"), "unknown member");
        check(clan.getAllMembersFP().equals("Alpha Beta "), "legacy name packet order and spaces");
        check(java.util.Arrays.equals(clan.getAllMembersName(), new String[]{"Alpha","Beta"}), "name list");
        clan.UpdataClanMember("Beta", 9);
        check(clan.getClanMemberList().get(1).rank == 9, "rank update");
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 1000; i++)
                    clan.addLoadedClanMember("Alpha", 9, 21, "memo", 10, 1);
            });
            threads[t].start();
        }
        for (Thread t : threads) t.join();
        check(clan.getClanMemberList().size() == 2, "concurrent duplicate registration");
        check(clan.GetDbMemberCount() == 2, "index count");
        check(clan.GetDbMember(10) == alpha, "ID index shares roster identity");
        check(clan.GetDbMember(999) == null, "unknown ID");
        try {
            clan.getClanMemberList().clear();
            throw new AssertionError("mutable roster escaped");
        } catch (UnsupportedOperationException expected) { }
        check(clan.GetDbMemberCount() == 2, "read-only roster");
        check(clan.RemoveDbMember(999) == null, "unknown removal no-op");
        check(clan.RemoveDbMember(10) == alpha, "remove indexed identity");
        check(clan.GetDbMember(10) == null && clan.GetDbMemberCount() == 1, "remove updates index");
        check(clan.getClanMemberList().size() == 1 && clan.getClanMemberList().get(0).memberId == 11,
                "remove updates ordered view");
        check(!alpha.online && alpha.player == null, "retired session cleared");
        check(clan.RemoveDbMember(10) == null, "duplicate removal no-op");
        clan.addLoadedClanMember("Alpha", 7, 20, "", 10, 1);
        check(clan.GetDbMember(10) != alpha && clan.getClanMemberList().get(1) == clan.GetDbMember(10),
                "rejoin creates new identity at end");
        check(clan.getClanMemberList() == clan.getClanMemberList(), "one stable roster view");
        java.util.Iterator<L1Clan.ClanMember> snapshot = clan.getClanMemberList().iterator();
        check(clan.RemoveDbMember(11) != null, "remove beta");
        check(snapshot.next().memberId == 11 && snapshot.next().memberId == 10 && !snapshot.hasNext(),
                "existing packet iteration remains snapshot ordered");
        check(clan.GetDbMemberCount() == 1 && clan.getClanMemberList().get(0).memberId == 10,
                "fresh view and ID index after removal");
        java.util.concurrent.atomic.AtomicReference<Throwable> workerFailure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread[] churn = new Thread[8];
        for (int t = 0; t < churn.length; t++) {
            final int id = 100 + t;
            churn[t] = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    clan.addLoadedClanMember("Member" + id, 7, 20, "", id, 1);
                    if (clan.RemoveDbMember(id) == null)
                        throw new AssertionError("unique add/remove lost");
                }
            });
            churn[t].setUncaughtExceptionHandler((thread, failure) -> workerFailure.compareAndSet(null, failure));
            churn[t].start();
        }
        for (Thread t : churn) t.join();
        if (workerFailure.get() != null) throw new AssertionError("concurrent roster mutation failed", workerFailure.get());
        check(clan.GetDbMemberCount() == 1 && clan.getClanMemberList().size() == 1,
                "concurrent unique add/remove leaves index and roster aligned");
        // Old snapshots must not change while writes are batched and while
        // a new snapshot is published. Verify legacy array/stream/list order.
        L1Clan bulk = new L1Clan();
        for (int i = 1; i <= 256; i++)
            bulk.addLoadedClanMember("Bulk" + i, 7, 20, "", i, 1);
        java.util.List<L1Clan.ClanMember> view = bulk.getClanMemberList();
        java.util.Iterator<L1Clan.ClanMember> old = view.iterator();
        java.util.Spliterator<L1Clan.ClanMember> split = view.spliterator();
        java.util.List<L1Clan.ClanMember> sub = view.subList(0, 2);
        for (int i = 1; i <= 128; i++) check(bulk.RemoveDbMember(i) != null, "bulk removal");
        for (int i = 257; i <= 384; i++)
            bulk.addLoadedClanMember("Bulk" + i, 7, 20, "", i, 1);
        check(view.size() == 256 && bulk.GetDbMemberCount() == 256, "bulk count");
        for (int i = 1; i <= 256; i++) check(old.next().memberId == i, "old snapshot stable");
        check(!old.hasNext(), "old snapshot length");
        final int[] expected = {1};
        split.forEachRemaining(m -> check(m.memberId == expected[0]++, "old spliterator stable"));
        check(expected[0] == 257 && sub.get(0).memberId == 1, "old sublist stable");
        L1Clan.ClanMember[] fresh = view.toArray(new L1Clan.ClanMember[0]);
        for (int i = 0; i < fresh.length; i++) check(fresh[i].memberId == i + 129, "new snapshot order");
        check(bulk.createMembersStream().count() == 256, "stream count");
        try {
            view.listIterator().set(fresh[0]);
            throw new AssertionError("mutable list iterator");
        } catch (UnsupportedOperationException expectedFailure) { }
        for (int i = 129; i <= 384; i++) check(bulk.RemoveDbMember(i) != null, "clear roster");
        check(view.isEmpty() && bulk.GetDbMemberCount() == 0 && !view.iterator().hasNext(), "empty snapshot");
        check(bulk.getOnlineClanMember() == bulk.getOnlineClanMember(), "shared empty online result");
        System.out.println("PASS pledge roster identity, memo, rank, ordered snapshots, rejoin and concurrent add/remove");
    }
}
