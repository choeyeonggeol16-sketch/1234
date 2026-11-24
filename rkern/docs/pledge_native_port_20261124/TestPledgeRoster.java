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
        System.out.println("PASS pledge roster identity, memo, rank, ordered ID removal, rejoin and concurrent registration");
    }
}
