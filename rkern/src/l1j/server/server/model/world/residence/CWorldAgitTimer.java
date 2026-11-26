package l1j.server.server.model.world.residence;

import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.network.thread.CServerTimer;

/** Runs the residence economy pass once per wall-clock minute. */
public final class CWorldAgitTimer {
    private static final Logger LOG = Logger.getLogger(CWorldAgitTimer.class.getName());
    private static final long MINUTE_MS = 60_000L;
    private static CServerTimer timer;

    private CWorldAgitTimer() {}

    public static synchronized void start() {
        if (timer != null) return;
        final long firstDelay = MINUTE_MS - Math.floorMod(System.currentTimeMillis(), MINUTE_MS);
        timer = CServerTimer.fixedRate(CWorldAgitTimer::TimerExpired, firstDelay, MINUTE_MS);
        if (timer == null) throw new IllegalStateException("Agit timer admission rejected");
    }

    public static synchronized void stop() {
        if (timer != null) timer.cancel();
        timer = null;
    }

    private static void TimerExpired() {
        final long now = System.currentTimeMillis();
        for (L1AuctionBoard board : AuctionBoardTable.getInstance().getAuctionBoardTableList()) {
            final Calendar deadline = board.getDeadline();
            if (deadline == null || deadline.getTimeInMillis() >= now) continue;
            try {
                L1AuctionTransaction.expire(board);
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Auction expiry failed houseId=" + board.getHouseId(), failure);
            }
        }
        for (L1House house : HouseTable.getInstance().getHouseTableList()) {
            final Calendar deadline = house.getTaxDeadline();
            if (house.isOnSale() || deadline == null || deadline.getTimeInMillis() >= now) continue;
            try {
                L1AuctionTransaction.foreclose(house);
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Residence tax expiry failed houseId=" + house.getHouseId(), failure);
            }
        }
    }
}
