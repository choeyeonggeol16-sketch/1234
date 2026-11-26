import l1j.server.server.model.HpMpGrowthHistory;

/** Storage text must survive repeated autosaves, level changes, and login. */
public final class CharacterGrowthSnapshotRegression {
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    private static void rejects(Runnable call) {
        try { call.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("expected invalid growth data to be rejected");
    }

    public static void main(String[] args) {
        require(HpMpGrowthHistory.decode(null) == null && HpMpGrowthHistory.decode("") == null);
        for (int floor = 1; floor <= HpMpGrowthHistory.MAX_LEVEL; floor++) {
            var history = new HpMpGrowthHistory(floor);
            StringBuilder wanted = new StringBuilder("1:").append(floor);
            require(!history.known(floor) && history.encode().contentEquals(wanted));
            for (int level = floor + 1; level <= HpMpGrowthHistory.MAX_LEVEL; level++) {
                String before = history.encode();
                int hp = level % 2 == 0 ? 32767 : 0;
                int mp = level % 3 == 0 ? 32767 : 0;
                history.record(level, hp, mp);
                wanted.append(';').append(level).append(',').append(hp).append(',').append(mp);
                require(history.encode().contentEquals(wanted) && !before.equals(history.encode()));
                require(history.known(level) && history.hp(level) == hp && history.mp(level) == mp);
            }
            require(HpMpGrowthHistory.decode(history.encode()).encode().contentEquals(wanted));
        }
        var historical = HpMpGrowthHistory.decode("1:001;02,+3,04");
        require(historical.encode().equals("1:1;2,3,4"));
        rejects(() -> historical.record(2, 3, 4));
        var sparse = new HpMpGrowthHistory(1);
        sparse.record(127, 0, 0);
        require(!sparse.known(2) && sparse.encode().equals("1:1;127,0,0"));
        rejects(() -> sparse.mp(2));
        for (String invalid : new String[] {"0:1","1:","1:128","1:1;","1:1;3,1,1","1:1;2,1,",
                "1:1;2,1,0,0","1:1;2,-1,0","1:1;2,0,32768","1:1;2;3,2,1","1:1;2,0,0;2,1,0"}) {
            rejects(() -> HpMpGrowthHistory.decode(invalid));
        }
        System.out.println("PASS growth snapshots, historical text and invalid input for every supported level");
    }
}
