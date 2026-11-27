package l1j.server.server.model.world.town;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.DatabaseFactory;
import l1j.server.server.datatables.TownTable;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.L2DateTime;
import l1j.server.server.model.gametime.L2TimeListener;
import l1j.server.server.model.gametime.CL2Time;
import l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.shared.ObjectWrapper;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.templates.L1Town;

public final class CResidenceSettlement implements L2TimeListener {
    public static final int FIRST_TOWN = 1;
    public static final int LAST_TOWN = 10;
    private static final int SETTLEMENT_DAY = 25;
    private static final int DB_LANE = CacheD.accountOrderKey("$residence-settlement");
    private static final Logger LOG = Logger.getLogger(CResidenceSettlement.class.getName());
    private static final CResidenceSettlement INSTANCE = new CResidenceSettlement();
    private static final ReentrantReadWriteLock PERSISTENCE = new ReentrantReadWriteLock(true);

    private static volatile boolean settling;
    private static volatile long period = 1;
    private CL2Time clock;
    private boolean scheduled;
    private long clockGeneration;
    private int lastMonth = Integer.MIN_VALUE;
    private int lastDay = Integer.MIN_VALUE;
    private static volatile int uncertainMonth = Integer.MIN_VALUE;
    private L2DateTime pendingDay;
    private boolean pendingMonthly;

    private CResidenceSettlement() {}

    public static CResidenceSettlement getInstance() { return INSTANCE; }
    public static long period() { return period; }
    public static boolean isSettling() { return settling || isSettlementUncertain(); }

    public static boolean isSettlementUncertain() { return uncertainMonth != Integer.MIN_VALUE; }

    public static boolean isTown(int id) { return id >= FIRST_TOWN && id <= LAST_TOWN; }
    public static boolean isTracked(int id) { return id == -1 || isTown(id); }

    /** Character reads and deferred writes must agree with the published month. */
    public static void lockPersistence() { PERSISTENCE.readLock().lock(); }

    public static void unlockPersistence() { PERSISTENCE.readLock().unlock(); }

    /** Game actions can decline quickly while the DB worker closes a period. */
    public static boolean tryMembershipChange() {
        if (isSettling() || !PERSISTENCE.readLock().tryLock()) return false;
        if (!isSettling()) return true;
        unlockPersistence();
        return false;
    }

    public synchronized void start(CL2Time source) {
        if (clock == source) return;
        stop();
        TownTable.getInstance();
        clock = source;
        clock.addGameListener(this, Calendar.DAY_OF_MONTH);
        clock.addGameListener(this, Calendar.HOUR_OF_DAY);
    }

    public synchronized void stop() {
        if (clock == null) return;
        clock.removeGameListener(this, Calendar.DAY_OF_MONTH);
        clock.removeGameListener(this, Calendar.HOUR_OF_DAY);
        clock = null;
        pendingDay = null;
        clockGeneration++;
    }

    @Override
    public synchronized void onDayChanged(L2DateTime time) {
        if (clock == null) return;
        if (pendingDay == null || !pendingMonthly) {
            pendingDay = time;
            pendingMonthly = isMonthly(time);
        }
        submitPending();
    }

    @Override
    public synchronized void onHourChanged(L2DateTime time) {
        submitPending();
    }

    private void submitPending() {
        L2DateTime time = pendingDay;
        if (time == null || scheduled) return;
        if (alreadyProcessed(time, pendingMonthly) || request(time, pendingMonthly)) pendingDay = null;
    }

    private static boolean isMonthly(L2DateTime time) {
        return time.get(Calendar.DAY_OF_MONTH) == SETTLEMENT_DAY;
    }

    public synchronized boolean request(boolean monthly) {
        return clock != null && request(CL2Time.getGameDateTime(), monthly);
    }

    private static int monthKey(L2DateTime time) {
        return time.get(Calendar.YEAR) * 12 + time.get(Calendar.MONTH);
    }

    private static int dayKey(L2DateTime time) {
        return monthKey(time) * 32 + time.get(Calendar.DAY_OF_MONTH);
    }

    private boolean alreadyProcessed(L2DateTime time, boolean monthly) {
        return monthly ? monthKey(time) == lastMonth : dayKey(time) == lastDay;
    }

    private boolean request(L2DateTime time, boolean monthly) {
        if (clock == null || scheduled || alreadyProcessed(time, monthly)) return false;
        long generation = clockGeneration;
        scheduled = true;
        try {
            CacheD.getInstance().submitDbJobOrdered(DB_LANE, () -> run(generation, time, monthly));
            return true;
        } catch (RuntimeException rejected) {
            scheduled = false;
            LOG.log(Level.WARNING, "Town settlement could not be scheduled", rejected);
            return false;
        }
    }

    private void run(long generation, L2DateTime time, boolean monthly) {
        int month = monthKey(time);
        int day = dayKey(time);
        try {
            synchronized (this) {
                if (clock == null || generation != clockGeneration) return;
            }
            Map<Integer, L1Town> result = settle(month, day, monthly);
            synchronized (this) {
                lastDay = day;
                if (monthly) lastMonth = month;
            }
            if (monthly && result != null) {
                try {
                    publishLeaders(result);
                } catch (RuntimeException failure) {
                    LOG.log(Level.WARNING, "Town leader notification failed after settlement", failure);
                }
            }
        } catch (SQLException | RuntimeException failure) {
            synchronized (this) {
                if (clock != null && generation == clockGeneration && (monthly || pendingDay == null)) {
                    pendingDay = time;
                    pendingMonthly = monthly;
                }
            }
            LOG.log(Level.SEVERE, monthly ? "Town monthly settlement failed" : "Town daily close failed", failure);
        } finally {
            synchronized (this) {
                scheduled = false;
            }
        }
    }

    private Map<Integer, L1Town> settle(int month, int day, boolean monthly) throws SQLException {
        if (monthly) {
            settling = true;
            PERSISTENCE.writeLock().lock();
        }
        try {
            var result = new ObjectWrapper<Map<Integer, L1Town>>();
            String receiptKey = monthly ? "town-month:" + month : "town-day:" + day;
            String receiptId = UUID.nameUUIDFromBytes(receiptKey.getBytes(StandardCharsets.UTF_8)).toString();
            CTransaction.persistEconomy(receiptId, DB_LANE, 0, new CTransaction.Mutation[]{con -> {
                if (monthly) settleMonth(con);
                else {
                    CTransaction.requireTransactionalTables(con, "town");
                    try (var ps = con.prepareStatement(
                            "UPDATE town SET tax_rate=tax_rate_reserved,sales_money_yesterday=sales_money,sales_money=0")) {
                        ps.executeUpdate();
                    }
                }
                result.value = TownTable.read(con, false);
            }});
            if (monthly && result.value == null && uncertainMonth == month) {
                try (Connection con = DatabaseFactory.getInstance().getConnection()) {
                    result.value = TownTable.read(con, false);
                }
            }
            if (result.value == null) return null; // An earlier process already committed this receipt.
            if (monthly) {
                period++;
                uncertainMonth = Integer.MIN_VALUE;
            }
            TownTable.getInstance().publish(result.value);
            return result.value;
        } catch (CTransaction.OutcomeUnknownException failure) {
            if (monthly) uncertainMonth = month;
            throw failure;
        } finally {
            if (monthly) {
                settling = false;
                PERSISTENCE.writeLock().unlock();
            }
        }
    }

    private static void settleMonth(Connection con) throws SQLException {
        CTransaction.requireTransactionalTables(con, "town", "characters");
        Map<Integer, L1Town> towns = TownTable.read(con, true);
        for (int id = FIRST_TOWN; id <= LAST_TOWN; id++) {
            if (!towns.containsKey(id)) throw new SQLException("Missing town " + id);
        }
        flushMemberships(con);

        long[] totals = new long[LAST_TOWN + 1];
        try (var ps = con.prepareStatement("SELECT HomeTownID,SUM(Contribution) FROM characters "
                + "WHERE HomeTownID BETWEEN ? AND ? AND Contribution>0 GROUP BY HomeTownID")) {
            ps.setInt(1, FIRST_TOWN);
            ps.setInt(2, LAST_TOWN);
            try (var rows = ps.executeQuery()) {
                while (rows.next()) totals[rows.getInt(1)] = rows.getLong(2);
            }
        }

        try (var leader = con.prepareStatement("SELECT objid,char_name FROM characters "
                     + "WHERE HomeTownID=? AND Contribution>0 ORDER BY Contribution DESC,objid DESC LIMIT 1");
             var payout = con.prepareStatement("UPDATE characters "
                     + "SET Pay=Pay+FLOOR(Contribution*?),Contribution=0 WHERE HomeTownID=? AND Contribution>0");
             var town = con.prepareStatement("UPDATE town SET leader_id=?,leader_name=?,tax_rate=0,"
                     + "tax_rate_reserved=0,sales_money_yesterday=sales_money,sales_money=0,town_tax=0,town_fix_tax=0"
                     + " WHERE town_id=?")) {
            for (int id = FIRST_TOWN; id <= LAST_TOWN; id++) {
                int leaderId = 0;
                String leaderName = null;
                if (totals[id] > 0) {
                    leader.setInt(1, id);
                    try (var rows = leader.executeQuery()) {
                        if (!rows.next()) throw new SQLException("Missing leader for town " + id);
                        leaderId = rows.getInt(1);
                        leaderName = rows.getString(2);
                    }
                    payout.setBigDecimal(1, payoutRate(towns.get(id).fixedTax(), totals[id]));
                    payout.setInt(2, id);
                    payout.executeUpdate();
                }
                town.setInt(1, leaderId);
                town.setString(2, leaderName);
                town.setInt(3, id);
                town.addBatch();
            }
            town.executeBatch();
        }

        try (var ps = con.prepareStatement("UPDATE characters SET HomeTownID=0,Contribution=0 WHERE HomeTownID=-1")) {
            ps.executeUpdate();
        }
    }

    static BigDecimal payoutRate(long tax, long total) {
        return total > 0 ? BigDecimal.valueOf(tax).divide(BigDecimal.valueOf(total), 2, RoundingMode.DOWN)
                : BigDecimal.ZERO;
    }

    private static void flushMemberships(Connection con) throws SQLException {
        var pending = new TIntObjectHashMap<CTownMembership.Snapshot>();
        for (L1PcInstance pc : CWorld.getInstance().getTownResidents()) {
            var data = pc.captureHomeTown();
            if (isTracked(data.townId())) pending.put(pc.getId(), data);
        }
        // Logout queues its last save before it leaves the resident index.
        CacheD.getInstance().collectPendingHomeTowns(period, pending);
        if (pending.isEmpty()) return;

        try (var ps = con.prepareStatement("UPDATE characters SET HomeTownID=?,Contribution=? "
                + "WHERE objid=? AND (HomeTownID<>? OR Contribution<>?)")) {
            int[] ids = pending.keys();
            Arrays.sort(ids);
            int batchSize = 0;
            for (int id : ids) {
                var data = pending.get(id);
                ps.setInt(1, data.townId());
                ps.setInt(2, data.contribution());
                ps.setInt(3, id);
                ps.setInt(4, data.townId());
                ps.setInt(5, data.contribution());
                ps.addBatch();
                if (++batchSize == 128) {
                    ps.executeBatch();
                    batchSize = 0;
                }
            }
            if (batchSize != 0) ps.executeBatch();
        }
    }

    private static void publishLeaders(Map<Integer, L1Town> snapshot) {
        CAsyncSendConstBuffer[] packets = new CAsyncSendConstBuffer[LAST_TOWN + 1];
        try {
            CWorld world = CWorld.getInstance();
            for (L1PcInstance pc : world.getTownResidents()) {
                int townId = pc.getHomeTownId();
                if (!isTown(townId)) {
                    CWorld.playerTownChanged(pc);
                    continue;
                }
                L1Town town = snapshot.get(townId);
                if (town.leaderName() == null || pc.isLogoutStarted()) continue;
                var packet = packets[townId];
                if (packet == null) {
                    packet = new CAsyncSendConstBuffer(SC_PacketBox.nativeTownLeaderWriter(town.leaderName()),
                            Integer.MIN_VALUE, Integer.MAX_VALUE);
                    packets[townId] = packet;
                }
                CSharedBroadcast.sendRecipient(packet, pc);
            }
        } finally {
            for (var packet : packets) if (packet != null) packet.close();
        }
    }
}
