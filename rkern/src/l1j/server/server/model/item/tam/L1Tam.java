package l1j.server.server.model.item.tam;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import l1j.server.server.storage.cached.CTransaction;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.storage.CacheD;
import l1j.server.server.utils.SQLUtil;

public class L1Tam {
	
    private static final Logger _log = Logger.getLogger(L1Tam.class.getName());

    private static final String SQL_SELECT_CHARS_BY_ACCOUNT =
            "SELECT objid, TamEndTime FROM characters WHERE account_name=?";

    private static final String SQL_SELECT_MIN_TAM_ENDTIME =
            "SELECT TamEndTime FROM characters WHERE account_name=? ORDER BY TamEndTime ASC";

    private static final String SQL_UPDATE_ACCOUNT_TAM_POINT =
            "UPDATE accounts SET Tam_Point=? WHERE login=?";

    private static final String SQL_HAS_WAIT_TAM =
            "SELECT 1 FROM tam WHERE objid=? LIMIT 1";

    private static final String SQL_SELECT_NEXT_TAM_DAY =
            "SELECT day FROM tam WHERE objid=? ORDER BY id ASC LIMIT 1";

    // ====== (추가) encobjid 기반 취소/조회 ======
    private static final String SQL_SELECT_TAM_DAY_BY_ENC =
            "SELECT day FROM tam WHERE encobjid=? ORDER BY id ASC LIMIT 1";

    private static final String SQL_SELECT_TAM_OBJID_BY_ENC =
            "SELECT objid FROM tam WHERE encobjid=? ORDER BY id ASC LIMIT 1";

    private static final String SQL_DELETE_ONE_TAM_BY_ENC =
            "DELETE FROM tam WHERE encobjid=? ORDER BY id ASC LIMIT 1";

    private static final String SQL_SELECT_ACCOUNT_BY_OBJID =
            "SELECT account_name FROM characters WHERE objid=? LIMIT 1";

    // =========================================================
    // 온라인 TamController(30분)과 1:1로 맞춘 오프라인 정산값
    // =========================================================
    private static final long OFFLINE_TICK_MS = 60L * 60L * 1000L; // 30분
    private static int TAM_PER_TICK_PER_RING = Config.탐갯수;
    
    private final Account owner;

    public L1Tam(Account owner) {
        this.owner = owner;
    }

    private Connection newCon() throws SQLException {
        return DatabaseFactory.getInstance().getConnection();
    }

    public void lessTimeUpdate() {
        if (owner == null || owner.getName() == null) return;

        Timestamp lastQuit = owner.getLastLogOut();
        if (lastQuit == null) return;

        long quitMs = lastQuit.getTime();
        long nowMs  = System.currentTimeMillis();
        if (nowMs <= quitMs) return;

        int addTotal = 0;
        int[] expired = null;
        int expiredCount = 0;

        Connection con = null;
        PreparedStatement pstm = null;
        ResultSet rs = null;

        try {
            con = newCon();
            pstm = con.prepareStatement(SQL_SELECT_CHARS_BY_ACCOUNT);
            pstm.setString(1, owner.getName());
            rs = pstm.executeQuery();

            while (rs.next()) {
                Timestamp end = rs.getTimestamp("TamEndTime");
                int objid = rs.getInt("objid");
                if (end == null) continue;

                long endMs = end.getTime();

                if ((nowMs / 1000L) + 2 > (endMs / 1000L)) {
                    if (expired == null) expired = new int[8];
                    else if (expiredCount == expired.length) expired = Arrays.copyOf(expired, expiredCount << 1);
                    expired[expiredCount++] = objid;
                    continue;
                }

                long activeUntil = Math.min(nowMs, endMs);
                if (activeUntil <= quitMs) continue;

                long activeMs = activeUntil - quitMs;
                int ticks = (int) (activeMs / OFFLINE_TICK_MS);
                if (ticks <= 0) continue;

                addTotal += ticks * TAM_PER_TICK_PER_RING;
            }

            rs.close(); rs = null;
            pstm.close(); pstm = null;
            for (int i = 0; i < expiredCount; i++) {
                Timestamp extended = tryExtendByReservation(con, expired[i], nowMs);
                if (extended == null) continue;
                long activeUntil = Math.min(nowMs, extended.getTime());
                if (activeUntil <= quitMs) continue;
                int ticks = (int) ((activeUntil - quitMs) / OFFLINE_TICK_MS);
                if (ticks > 0) addTotal += ticks * TAM_PER_TICK_PER_RING;
            }
            if (addTotal > 0) {
                owner.addTamPoint(addTotal);
                updateTamPoint(con);
            }

        } catch (Exception e) {
            _log.log(Level.SEVERE, "Tam lessTimeUpdate failed: " + owner.getName(), e);
        } finally {
            SQLUtil.close(rs, pstm, con);
        }
    }

    public void updateTamPoint() {
        if (owner == null || owner.getName() == null) return;
        final String account = owner.getName();
        synchronized (owner) {
            // The inventory receipt updates Tam_Point in SQL before its CIO
            // callback updates the Account object. A snapshot written here
            // would otherwise undo the committed item award.
            if (owner.isTamAwardPending()) return;
            final int point = owner.getTamPoint();
            CacheD.getInstance().submitDbJobOrdered(account, () -> {
                try (Connection con = newCon();
                     PreparedStatement ps = con.prepareStatement(SQL_UPDATE_ACCOUNT_TAM_POINT)) {
                    ps.setInt(1, point);
                    ps.setString(2, account);
                    if (ps.executeUpdate() != 1)
                        throw new SQLException("TAM account row not found");
                } catch (SQLException e) {
                    _log.log(Level.SEVERE, "Tam point save failed: " + account, e);
                    throw new IllegalStateException("TAM DB save failed: " + account, e);
                }
            });
        }
    }

    private void updateTamPoint(Connection con) throws SQLException {
        try (PreparedStatement pstm = con.prepareStatement(SQL_UPDATE_ACCOUNT_TAM_POINT)) {
            pstm.setInt(1, owner.getTamPoint());
            pstm.setString(2, owner.getName());
            pstm.executeUpdate();
        }
    }

    public long getRemainTimeMs() {
        Connection con = null;
        PreparedStatement pstm = null;
        ResultSet rs = null;

        long now = System.currentTimeMillis();
        try {
            con = newCon();
            pstm = con.prepareStatement(SQL_SELECT_MIN_TAM_ENDTIME);
            pstm.setString(1, owner.getName());
            rs = pstm.executeQuery();

            while (rs.next()) {
                Timestamp t = rs.getTimestamp(1);
                if (t != null && now < t.getTime()) return t.getTime() - now;
            }
            return 0L;
        } catch (Exception e) {
            return 0L;
        } finally {
            SQLUtil.close(rs, pstm, con);
        }
    }

    public int getActiveCountAndRefresh() {
        int count = 0;
        int[] expired = null;
        int expiredCount = 0;
        long now = System.currentTimeMillis();

        Connection con = null;
        PreparedStatement pstm = null;
        ResultSet rs = null;

        try {
            con = newCon();
            pstm = con.prepareStatement(SQL_SELECT_CHARS_BY_ACCOUNT);
            pstm.setString(1, owner.getName());
            rs = pstm.executeQuery();

            while (rs.next()) {
                Timestamp end = rs.getTimestamp("TamEndTime");
                int objid = rs.getInt("objid");
                if (end == null) continue;

                long endMs = end.getTime();

                if ((now / 1000L) + 2 <= (endMs / 1000L)) {
                    count++;
                    continue;
                }

                if (expired == null) expired = new int[8];
                else if (expiredCount == expired.length) expired = Arrays.copyOf(expired, expiredCount << 1);
                expired[expiredCount++] = objid;
            }
            rs.close(); rs = null;
            pstm.close(); pstm = null;
            for (int i = 0; i < expiredCount; i++) {
                if (tryExtendByReservation(con, expired[i], now) != null) count++;
            }
        } catch (Exception e) {
            _log.log(Level.WARNING, "Tam getActiveCountAndRefresh failed: " + owner.getName(), e);
        } finally {
            SQLUtil.close(rs, pstm, con);
        }
        return count;
    }

    // =========================================================
    // (기존 호환)
    // =========================================================

    public boolean hasReservation(int objid) {
        Connection con = null;
        try {
            con = newCon();
            return hasWait(con, objid);
        } catch (Exception e) {
            return false;
        } finally {
            SQLUtil.close(con);
        }
    }

    public int nextReservationDay(int objid) {
        Connection con = null;
        try {
            con = newCon();
            return nextDay(con, objid);
        } catch (Exception e) {
            return 0;
        } finally {
            SQLUtil.close(con);
        }
    }

    public void tamdel(int objid) {
        Connection con = null;
        try {
            con = newCon();
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM tam WHERE objid=? ORDER BY id ASC LIMIT 1")) {
                ps.setInt(1, objid);
                ps.executeUpdate();
            }
            invalidateCache();
        } catch (Exception ignore) {
        } finally {
            SQLUtil.close(con);
        }
    }

    // =========================================================
    //  encobjid 기반 취소/조회 + 계정 소유권 체크
    // =========================================================

    /** encobjid로 다음 예약 day 조회 (없으면 0) */
    public int Nexttam(String encobjid) {
        if (encobjid == null || encobjid.isEmpty()) return 0;
        try (Connection con = newCon();
             PreparedStatement p = con.prepareStatement(SQL_SELECT_TAM_DAY_BY_ENC)) {
            p.setString(1, encobjid);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignore) {}
        return 0;
    }

    /** encobjid로 예약 대상 objid 조회 (없으면 0) */
    public int TamCharid(String encobjid) {
        if (encobjid == null || encobjid.isEmpty()) return 0;
        try (Connection con = newCon();
             PreparedStatement p = con.prepareStatement(SQL_SELECT_TAM_OBJID_BY_ENC)) {
            p.setString(1, encobjid);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception ignore) {}
        return 0;
    }

    /** encobjid 기준 예약 1개 삭제(취소) 성공 여부 */
    public boolean tamcancle(String encobjid) {
        if (encobjid == null || encobjid.isEmpty()) return false;
        try (Connection con = newCon();
             PreparedStatement p = con.prepareStatement(SQL_DELETE_ONE_TAM_BY_ENC)) {
            p.setString(1, encobjid);
            return p.executeUpdate() > 0;
        } catch (Exception ignore) {}
        return false;
    }

    /** objid가 내 계정(owner) 소유인지 확인 */
    public boolean isMyAccountChar(int objid) {
        if (owner == null || owner.getName() == null) return false;
        if (objid <= 0) return false;

        try (Connection con = newCon();
             PreparedStatement p = con.prepareStatement(SQL_SELECT_ACCOUNT_BY_OBJID)) {
            p.setInt(1, objid);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return false;
                String accName = rs.getString(1);
                return accName != null && accName.equalsIgnoreCase(owner.getName());
            }
        } catch (Exception ignore) {}
        return false;
    }

    // =========================================================
    // 내부: 예약 pop + TamEndTime 연장
    // =========================================================

    private Timestamp tryExtendByReservation(Connection con, int objid, long nowMs) throws SQLException {
        // No write transaction for the common expired-with-no-reservation case.
        if (!hasWait(con, objid)) return null;
        if (!con.getAutoCommit()) throw new SQLException("Unexpected TAM transaction scope");
        con.setAutoCommit(false);
        boolean resolved = false;
        try {
            Timestamp current;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT TamEndTime FROM characters WHERE objid=? AND account_name=? FOR UPDATE")) {
                ps.setInt(1, objid);
                ps.setString(2, owner.getName());
                try (ResultSet rows = ps.executeQuery()) {
                    current = rows.next() ? rows.getTimestamp(1) : null;
                }
            }
            Timestamp next = current;
            if (current != null && nowMs / 1000L + 2 > current.getTime() / 1000L) {
                CTransaction.requireTransactionalTables(con, "characters", "tam");
                next = TamFruitUseService.extendReservation(con, owner.getName(), objid, nowMs);
            }
            con.commit();
            resolved = true;
            return next;
        } catch (SQLException | RuntimeException failure) {
            try {
                con.rollback();
                resolved = true;
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        } finally {
            // Never implicitly commit after an unresolved rollback. The caller
            // closes the failed connection; Hikari retires/resets it.
            if (resolved) con.setAutoCommit(true);
        }
    }

    private boolean hasWait(Connection con, int objid) throws SQLException {
        try (PreparedStatement p = con.prepareStatement(SQL_HAS_WAIT_TAM)) {
            p.setInt(1, objid);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int nextDay(Connection con, int objid) throws SQLException {
        try (PreparedStatement p = con.prepareStatement(SQL_SELECT_NEXT_TAM_DAY)) {
            p.setInt(1, objid);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    // ===== CIO: non-blocking TAM cache =====
    private volatile long _cacheAtMs;
    private volatile int _cacheActiveCount;
    private volatile long _cacheRemainMs;
    private static final long CACHE_TTL_MS = 3000L;
    private final Object _cacheLock = new Object();
    private boolean _refreshing;
    private long _cacheVersion;

    public void invalidateCache() {
        synchronized (_cacheLock) {
            _cacheVersion++;
            _cacheAtMs = 0L;
        }
    }

    // Call from an existing CacheD owner job, never from CIO.
    public void refreshCacheOnDbWorker() {
        final long version;
        synchronized (_cacheLock) {
            version = _cacheVersion;
        }
        final int active = getActiveCountAndRefresh();
        final long remaining = getRemainTimeMs();
        synchronized (_cacheLock) {
            if (version == _cacheVersion) {
                _cacheActiveCount = active;
                _cacheRemainMs = remaining;
                _cacheAtMs = System.currentTimeMillis();
            }
        }
    }

    private void requestCacheRefresh() {
        if (owner == null || owner.getName() == null) return;
        synchronized (_cacheLock) {
            if (_refreshing || System.currentTimeMillis() - _cacheAtMs < CACHE_TTL_MS) return;
            _refreshing = true;
        }
        try {
            CacheD.getInstance().submitDbJobOrdered(owner.getName(), () -> {
                try {
                    refreshCacheOnDbWorker();
                } catch (Throwable e) {
                    _log.log(Level.WARNING, "Async TAM refresh failed: " + owner.getName(), e);
                } finally {
                    synchronized (_cacheLock) {
                        _refreshing = false;
                    }
                }
            });
        } catch (RuntimeException ex) {
            synchronized (_cacheLock) { _refreshing = false; }
            _log.log(Level.WARNING, "Async TAM refresh rejected: " + owner.getName(), ex);
        }
    }

    public int getActiveCountAndRefreshCached() {
        if (System.currentTimeMillis() - _cacheAtMs >= CACHE_TTL_MS) requestCacheRefresh();
        return _cacheActiveCount;
    }

    public long getRemainTimeMsCached() {
        if (System.currentTimeMillis() - _cacheAtMs >= CACHE_TTL_MS) requestCacheRefresh();
        return _cacheRemainMs;
    }
}
