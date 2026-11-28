package l1j.server.server.model.world.residence;

import java.util.Calendar;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.server.model.gametime.CDateTime;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.gametime.CTimeListener;
import l1j.server.server.model.network.thread.CServerTimer;
import l1j.server.server.model.world.CWorldWork;

/** One reservation per committed residence deadline, with one world timer for the nearest. */
public final class CWorldAgitTimer {
    private static final Logger LOG = Logger.getLogger(CWorldAgitTimer.class.getName());
    private static final long MINUTE_MS = 60_000L;
    private static final long DISPATCH_SPACING_MS = 50L;
    private static final CWorldAgitTimer INSTANCE = new CWorldAgitTimer();

    private enum Kind { AUCTION, TAX }

    // Identity of the entry is its generation, including when an ID is re-used.
    private record Deadline(Kind kind, int id, Object row, long savedDeadline, long readyAt) {}

    private final TIntObjectHashMap<Deadline> auctions = new TIntObjectHashMap<>();
    private final TIntObjectHashMap<Deadline> taxes = new TIntObjectHashMap<>();
    private final TreeSet<Deadline> pending = new TreeSet<>(Comparator.comparingLong(Deadline::readyAt)
            .thenComparing(Deadline::kind).thenComparingInt(Deadline::id));
    private final CTimeListener clock = new CTimeListener() {
        @Override public void onSecondChanged(CDateTime time) { checkClock(time.getSeconds() * 1000L); }
    };
    private CServerTimer timer;
    private boolean running;
    private boolean dispatching;
    private long generation;
    private long armedAt = Long.MAX_VALUE;
    private volatile long fallbackAt = Long.MAX_VALUE;

    private CWorldAgitTimer() {}

    public static void start() { INSTANCE.startDeadlines(); }
    public static void stop() { INSTANCE.stopDeadlines(); }

    private synchronized void startDeadlines() {
        if (running) return;
        final HouseTable houses = HouseTable.getInstance();
        final AuctionBoardTable boards = AuctionBoardTable.getInstance();
        running = true;
        CGameTime.addRealListener(clock, Calendar.SECOND);
        installHouses(houses);
        installAuctions(boards);
        arm();
    }

    private synchronized void stopDeadlines() {
        if (!running) return;
        running = false;
        CGameTime.removeRealListener(clock, Calendar.SECOND);
        cancelWake();
        pending.clear();
        auctions.clear();
        taxes.clear();
    }

    static void housesReloaded(HouseTable houses) { INSTANCE.reloadHouses(houses); }
    static void auctionsReloaded(AuctionBoardTable boards) { INSTANCE.reloadAuctions(boards); }
    static void houseChanged(int id) { INSTANCE.refreshHouse(id); }
    static void auctionChanged(int id) { INSTANCE.refreshAuction(id); }

    private synchronized void reloadHouses(HouseTable houses) {
        if (!running || HouseTable.getInstance() != houses) return;
        installHouses(houses);
        arm();
    }

    private synchronized void reloadAuctions(AuctionBoardTable boards) {
        if (!running || AuctionBoardTable.getInstance() != boards) return;
        installAuctions(boards);
        arm();
    }

    private void installHouses(HouseTable houses) {
        for (Deadline entry : taxes.valueCollection()) pending.remove(entry);
        taxes.clear();
        for (L1House house : houses.getHouseTableList()) {
            replace(Kind.TAX, house.getHouseId(), house, house.isOnSale() ? null : house.getTaxDeadline());
        }
    }

    private void installAuctions(AuctionBoardTable boards) {
        for (Deadline entry : auctions.valueCollection()) pending.remove(entry);
        auctions.clear();
        for (L1AuctionBoard board : boards.getAuctionBoardTableList()) {
            replace(Kind.AUCTION, board.getHouseId(), board, board.getDeadline());
        }
    }

    private synchronized void refreshHouse(int id) {
        if (!running) return;
        final L1House house = HouseTable.getInstance().getHouseTable(id);
        replace(Kind.TAX, id, house, house == null || house.isOnSale() ? null : house.getTaxDeadline());
        arm();
    }

    private synchronized void refreshAuction(int id) {
        if (!running) return;
        final L1AuctionBoard board = AuctionBoardTable.getInstance().getAuctionBoardTable(id);
        replace(Kind.AUCTION, id, board, board == null ? null : board.getDeadline());
        arm();
    }

    private TIntObjectHashMap<Deadline> entries(Kind kind) {
        return kind == Kind.AUCTION ? auctions : taxes;
    }

    private void replace(Kind kind, int id, Object row, Calendar deadline) {
        final TIntObjectHashMap<Deadline> index = entries(kind);
        final Deadline previous = index.get(id);
        final long now = System.currentTimeMillis();
        if (deadline == null) {
            if (previous != null) {
                index.remove(id);
                pending.remove(previous);
            }
            return;
        }
        final long saved = deadline.getTimeInMillis();
        if (previous != null && previous.row() == row && previous.savedDeadline() == saved) return;
        if (previous != null) pending.remove(previous);
        final Deadline current = new Deadline(kind, id, row, saved, nextMinuteAfter(Math.max(saved, now)));
        index.put(id, current);
        pending.add(current);
    }

    static long nextMinuteAfter(long wallMillis) {
        return wallMillis + MINUTE_MS - Math.floorMod(wallMillis, MINUTE_MS);
    }

    private void arm() {
        if (dispatching) return;
        if (!running || pending.isEmpty()) {
            cancelWake();
            return;
        }
        final long target = pending.first().readyAt();
        if (timer != null && !timer.isCancelled() && armedAt == target) return;
        cancelWake();
        final long token = generation;
        armedAt = target;
        timer = CWorldWork.schedule(() -> wake(token),
                Math.max(DISPATCH_SPACING_MS, target - System.currentTimeMillis()));
        fallbackAt = timer == null ? target : target + 1_000L;
    }

    private void cancelWake() {
        ++generation;
        if (timer != null) timer.cancel();
        timer = null;
        armedAt = fallbackAt = Long.MAX_VALUE;
    }

    private void checkClock(long now) {
        if (now < fallbackAt) return;
        synchronized (this) {
            if (!running || dispatching || now < fallbackAt) return;
            // Recover rejected registration and wall-clock jumps on the existing clock.
            final long token = generation + 1;
            if (CWorldWork.tryExecute(() -> wake(token))) cancelWake();
        }
    }

    private void wake(long token) {
        final Deadline entry;
        synchronized (this) {
            if (!running || dispatching || token != generation) return;
            cancelWake();
            if (pending.isEmpty() || pending.first().readyAt() > System.currentTimeMillis()) {
                arm();
                return;
            }
            entry = pending.pollFirst();
            dispatching = true;
        }
        try {
            dispatch(entry);
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Residence expiry failed houseId=" + entry.id(), failure);
        } finally {
            synchronized (this) {
                final TIntObjectHashMap<Deadline> index = entries(entry.kind());
                if (running && index.get(entry.id()) == entry) {
                    // Until a receipt publishes a new deadline or retirement, retry on the
                    // following minute. CacheD fences owners awaiting or uncertain of COMMIT.
                    final long now = System.currentTimeMillis();
                    final Deadline retry = new Deadline(entry.kind(), entry.id(), entry.row(),
                            entry.savedDeadline(), nextMinuteAfter(Math.max(now, entry.savedDeadline())));
                    index.put(entry.id(), retry);
                    pending.add(retry);
                }
                dispatching = false;
                arm();
            }
        }
    }

    private void dispatch(Deadline entry) {
        synchronized (this) {
            if (!running || entries(entry.kind()).get(entry.id()) != entry) return;
        }
        if (entry.kind() == Kind.AUCTION) {
            L1AuctionTransaction.expire((L1AuctionBoard) entry.row(), entry.savedDeadline());
        } else {
            L1AuctionTransaction.foreclose((L1House) entry.row(), entry.savedDeadline());
        }
    }
}
