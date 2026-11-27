package l1j.server.server.model.world.town;

/** Character-local residence data; the settlement period fences older save images. */
public final class CTownMembership {
    public record Snapshot(int townId, int contribution, long period) {
        private static final Snapshot NONE = new Snapshot(0, 0, 0);

        public int townIdFor(long currentPeriod) {
            return townId == -1 && period != currentPeriod ? 0 : townId;
        }

        public int contributionFor(long currentPeriod) {
            return period == currentPeriod ? contribution : 0;
        }
    }

    private volatile int townId;
    private int contribution;
    private volatile long period;

    public int townId() {
        int current = townId;
        return current == -1 && period != CResidenceSettlement.period() ? 0 : current;
    }

    public synchronized void restore(int id, int amount, long savedPeriod) {
        contribution = id > 0 ? Math.max(0, amount) : 0;
        period = savedPeriod;
        townId = id;
    }

    public synchronized void changeTown(int id) {
        long current = CResidenceSettlement.period();
        normalize(current);
        if (townId != id) {
            contribution = 0;
            townId = id;
        }
    }

    public synchronized void setContribution(int amount) {
        normalize(CResidenceSettlement.period());
        contribution = townId > 0 ? Math.max(0, amount) : 0;
    }

    public synchronized void earn(int amount) {
        if (amount <= 0 || townId <= 0 || CResidenceSettlement.isSettling()) return;
        normalize(CResidenceSettlement.period());
        contribution = (int) Math.min(Integer.MAX_VALUE, (long) contribution + amount);
    }

    public synchronized int contribution() {
        return period == CResidenceSettlement.period() ? contribution : 0;
    }

    public synchronized Snapshot snapshot() {
        normalize(CResidenceSettlement.period());
        return townId == 0 ? Snapshot.NONE : new Snapshot(townId, contribution, period);
    }

    private void normalize(long currentPeriod) {
        if (period == currentPeriod) return;
        if (townId == -1) townId = 0;
        contribution = 0;
        period = currentPeriod;
    }
}
