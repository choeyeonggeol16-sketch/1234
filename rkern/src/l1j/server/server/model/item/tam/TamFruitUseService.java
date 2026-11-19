package l1j.server.server.model.item.tam;

import java.sql.Connection;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1World;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.storage.CacheD;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_TAM_WINDOW;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class TamFruitUseService {

    private TamFruitUseService() {}

    private static final long DAY_MS = TimeUnit.DAYS.toMillis(1);
    private static final int MAX_STAGE = 5;

    private static final String SQL_LOCK_ACCOUNT_CHARS =
            "SELECT objid, char_name, TamEndTime FROM characters WHERE account_name=? ORDER BY objid FOR UPDATE";
    private static final String SQL_UPDATE_END =
            "UPDATE characters SET TamEndTime=? WHERE objid=? AND account_name=?";
    private static final String SQL_INSERT_RESERVE =
            "INSERT INTO tam SET objid=?, Name=?, Day=?, encobjid=?";
    private static final String SQL_SELECT_ENDS_BY_ACCOUNT =
            "SELECT TamEndTime FROM characters WHERE account_name=?";
    private static final long RESERVE_THROTTLE_MS = 2000L;
    private static final ConcurrentHashMap<Integer, Long> RESERVE_THROTTLE = new ConcurrentHashMap<>(4096);
    private static final AtomicInteger RESERVE_SWEEPS = new AtomicInteger();

    private static final int[] TAM_SKILLS = {
            0,
            L1SkillId.Tam_Fruit1,
            L1SkillId.Tam_Fruit2,
            L1SkillId.Tam_Fruit3,
            L1SkillId.Tam_Fruit4,
            L1SkillId.Tam_Fruit5
    };

    private static final int[] ICON1 = { 0, 8265, 8266, 8267, 8268, 8269 };
    private static final int[] ICON2 = { 0, 4181, 4182, 4183, 5046, 5047 };

    // 3/4/5단: 데미지감소 2
    private static final int[] DR_BY_STAGE = { 0, 0, 0, 2, 2, 2 };

    public static final int[] hextable = {
            0x80,0x81,0x82,0x83,0x84,0x85,0x86,0x87,0x88,0x89,0x8a,0x8b,0x8c,0x8d,0x8e,0x8f,
            0x90,0x91,0x92,0x93,0x94,0x95,0x96,0x97,0x98,0x99,0x9a,0x9b,0x9c,0x9d,0x9e,0x9f,
            0xa0,0xa1,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xa9,0xaa,0xab,0xac,0xad,0xae,0xaf,
            0xb0,0xb1,0xb2,0xb3,0xb4,0xb5,0xb6,0xb7,0xb8,0xb9,0xba,0xbb,0xbc,0xbd,0xbe,0xbf,
            0xc0,0xc1,0xc2,0xc3,0xc4,0xc5,0xc6,0xc7,0xc8,0xc9,0xca,0xcb,0xcc,0xcd,0xce,0xcf,
            0xd0,0xd1,0xd2,0xd3,0xd4,0xd5,0xd6,0xd7,0xd8,0xd9,0xda,0xdb,0xdc,0xdd,0xde,0xdf,
            0xe0,0xe1,0xe2,0xe3,0xe4,0xe5,0xe6,0xe7,0xe8,0xe9,0xea,0xeb,0xec,0xed,0xee,0xef,
            0xf0,0xf1,0xf2,0xf3,0xf4,0xf5,0xf6,0xf7,0xf8,0xf9,0xfa,0xfb,0xfc,0xfd,0xfe,0xff
    };

    /**
     * - "탐 5캐릭 제한"은 "새로 켜는 캐릭"만 막는다.
     * - 이미 탐이 켜져있는 캐릭(=ALREADY_ACTIVE)에 먹이면 "예약"은 5캐릭 꽉 차도 가능해야 한다.
     */
    public static void useFruit(final L1PcInstance caller, final int rawObjId,
            final L1ItemInstance item, final int day, final Runnable completion) {
        if (caller == null || item == null || caller.getId() == 0 || day <= 0) return;
        final String account = caller.getAccountName();
        if (account == null || account.isEmpty()) return;
        final int callerId = caller.getId();
        final int targetId = rawObjId <= 0 ? callerId : rawObjId;
        final long now = System.currentTimeMillis();
        final FruitResult result = new FruitResult();
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(
                caller, CacheD.accountOrderKey(account))) {
            if (!tx.debit(item, 1)) return;
            tx.requireTables("characters", "tam");
            tx.sql(con -> applyFruit(con, account, callerId, targetId, day, now, result));
            tx.onFailure(() -> {
                if (result.limitReached) SC_ServerMessage.send(caller, 3904);
                else SC_SystemMessage.send(caller, result.failureMessage);
            });
            CDB.RequestUseItem(caller, tx, () -> {
                // No SQL or fruit consumption in the owner callback.
                if (result.callerEnd != null) caller.setTamTime(result.callerEnd);
                invalidateTamCacheForPc(caller);
                if (result.reserved) {
                    recordReserve(targetId, System.currentTimeMillis());
                    SC_TAM_WINDOW.sendAsync(caller);
                    final String name = result.name == null || result.name.isEmpty() ? caller.getName() : result.name;
                    SC_SystemMessage.send(caller, "[" + name + "] 에 이미 이용중인 상품이 있어 예약 되었습니다.");
                } else {
                    if (targetId == caller.getId()) caller.setTamTime(result.targetEnd);
                    applyOnlineBuffWithSnapshot(caller, result.stage, result.remainSec);
                    if (targetId != callerId) syncOnlineTarget(account, targetId, result);
                    postEffectForCaller(caller);
                }
                if (completion != null) completion.run();
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(caller, "탐 열매 사용 중 오류가 발생했습니다.");
        }
    }

    private static final class FruitResult {
        String failureMessage = "탐 열매 사용을 저장하지 못했습니다.";
        String name;
        Timestamp callerEnd, targetEnd;
        boolean reserved, limitReached;
        int stage, remainSec;
    }

    // Runs on the existing receipt DB worker. All writes, including refresh of
    // expired reservations, roll back with the fruit debit on any failure.
    private static void applyFruit(Connection con, String account, int callerId,
            int targetId, int day, long now, FruitResult result) throws SQLException {
        result.failureMessage = "탐 열매 사용을 저장하지 못했습니다.";
        result.callerEnd = null;
        result.targetEnd = null;
        result.reserved = false;
        result.limitReached = false;
        result.name = null;
        int active = 0;
        long minRemain = Long.MAX_VALUE;
        boolean ownCaller = false, ownTarget = false;
        try (PreparedStatement ps = con.prepareStatement(SQL_LOCK_ACCOUNT_CHARS)) {
            ps.setString(1, account);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("objid");
                    ownCaller |= id == callerId;
                    ownTarget |= id == targetId;
                }
            }
        }
        if (!ownCaller || !ownTarget) {
            result.failureMessage = "탐 열매 적용 실패(대상 오류).";
            throw new SQLException("Tam target is not owned by caller account");
        }
        try (PreparedStatement ps = con.prepareStatement(SQL_LOCK_ACCOUNT_CHARS)) {
            ps.setString(1, account);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("objid");
                    Timestamp end = rs.getTimestamp("TamEndTime");
                    // The previous refresh popped a reservation only for an
                    // expired, non-null end date. Preserve that condition.
                    if (end != null && !isActive(end, now)) {
                        Timestamp extended = extendReservation(con, account, id, now);
                        if (extended != null) {
                            end = extended;
                            if (id == callerId) result.callerEnd = end;
                        }
                    }
                    if (id == targetId) {
                        result.name = rs.getString("char_name");
                        result.targetEnd = end;
                    }
                    if (isActive(end, now)) {
                        active++;
                        minRemain = Math.min(minRemain, end.getTime() - now);
                    }
                }
            }
        }
        final Timestamp current = result.targetEnd;
        if (!isActive(current, now) && active >= MAX_STAGE) {
            result.limitReached = true;
            throw new SQLException("Tam active character limit");
        }
        // Preserve the original update cutoff (now - 2 seconds) and duration.
        if (current != null && current.getTime() >= now - 2000L) {
            Long last = RESERVE_THROTTLE.get(targetId);
            if (last != null && now - last < RESERVE_THROTTLE_MS) {
                result.failureMessage = "잠시 후 다시 시도해주세요.";
                throw new SQLException("Tam reservation throttled");
            }
            try (PreparedStatement ps = con.prepareStatement(SQL_INSERT_RESERVE)) {
                ps.setInt(1, targetId);
                ps.setString(2, result.name == null ? "" : result.name);
                ps.setInt(3, day);
                ps.setString(4, byteWrite(targetId));
                if (ps.executeUpdate() != 1) throw new SQLException("Tam reservation insert failed");
            }
            result.reserved = true;
        } else {
            final Timestamp end = new Timestamp(now + DAY_MS * (long) day + 10_000L);
            updateEnd(con, account, targetId, end);
            result.targetEnd = end;
            active++;
            minRemain = Math.min(minRemain, end.getTime() - now);
        }
        result.stage = Math.min(active, MAX_STAGE);
        result.remainSec = minRemain == Long.MAX_VALUE ? 0 : (int) Math.max(0L, minRemain / 1000L);
    }

    private static boolean isActive(Timestamp end, long now) {
        return end != null && now / 1000L + 2 <= end.getTime() / 1000L;
    }

    private static void updateEnd(Connection con, String account, int id, Timestamp end) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_UPDATE_END)) {
            ps.setTimestamp(1, end);
            ps.setInt(2, id);
            ps.setString(3, account);
            if (ps.executeUpdate() != 1) throw new SQLException("Tam character update failed");
        }
    }

    static Timestamp extendReservation(Connection con, String account, int id, long now) throws SQLException {
        long reservationId;
        int days;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id, day FROM tam WHERE objid=? ORDER BY id ASC LIMIT 1 FOR UPDATE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                reservationId = rs.getLong(1);
                days = rs.getInt(2);
            }
        }
        if (days <= 0) return null;
        Timestamp end = new Timestamp(now + DAY_MS * (long) days + 10_000L);
        updateEnd(con, account, id, end);
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM tam WHERE id=? AND objid=?")) {
            ps.setLong(1, reservationId);
            ps.setInt(2, id);
            if (ps.executeUpdate() != 1) throw new SQLException("Tam reservation refresh failed");
        }
        return end;
    }

    private static void recordReserve(int id, long now) {
        RESERVE_THROTTLE.put(id, now);
        if ((RESERVE_SWEEPS.incrementAndGet() & 1023) == 0) {
            final long cutoff = now - 60_000L;
            RESERVE_THROTTLE.forEach((key, timestamp) -> {
                if (timestamp < cutoff) RESERVE_THROTTLE.remove(key, timestamp);
            });
        }
    }

    private static void syncOnlineTarget(String account, int id, FruitResult result) {
        L1Object object = L1World.getInstance().findObject(id);
        if (!(object instanceof L1PcInstance target) || !account.equalsIgnoreCase(target.getAccountName())) return;
        final var client = target.getNetConnection();
        if (client == null || client.getActiveChar() != target) return;
        CIOThreadCallback.getInstance().completeSession(client, () -> {
            // No foreign owner mutation from the caller's CIO shard.
            if (client.getActiveChar() != target || !account.equalsIgnoreCase(target.getAccountName())) return;
            target.setTamTime(result.targetEnd);
            invalidateTamCacheForPc(target);
            applyOnlineBuffWithSnapshot(target, result.stage, result.remainSec);
        });
    }

    public static void cancelReservation(L1PcInstance pc, String encoded) {
        if (pc == null || encoded == null || pc.getNetConnection() == null
                || pc.getNetConnection().getAccount() == null) return;
        final String account = pc.getNetConnection().getAccount().getName();
        if (account == null || account.isEmpty() || pc.isItemTransferBusy()
                || pc.isTradeResolving() || pc.getTradeID() != 0) return;
        final long reservationId;
        final int targetId, day;
        // Preserve the first reservation's order. SQL verification below
        // rejects a consumed/replaced row instead of refunding another one.
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT id, objid, day FROM tam WHERE encobjid=? ORDER BY id ASC LIMIT 1")) {
            ps.setString(1, encoded);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    SC_SystemMessage.send(pc, "예약 정보를 찾을 수 없습니다.");
                    return;
                }
                reservationId = rs.getLong(1);
                targetId = rs.getInt(2);
                day = rs.getInt(3);
            }
        } catch (SQLException failure) {
            SC_SystemMessage.send(pc, "예약 정보를 찾을 수 없습니다.");
            return;
        }
        final int itemId = switch (day) { case 3 -> 600226; case 7 -> 3000235; case 30 -> 600227; default -> 0; };
        if (itemId == 0 || targetId <= 0) return;
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc, CacheD.accountOrderKey(account))) {
            final L1ItemInstance fruit = tx.grant(itemId, 1);
            if (fruit == null) return;
            tx.requireTables("characters", "tam");
            tx.sql(con -> deleteReservation(con, account, encoded, reservationId, targetId, day));
            tx.onFailure(() -> {
                SC_SystemMessage.send(pc, "예약 취소 처리에 실패했습니다.");
                SC_TAM_WINDOW.sendAsync(pc);
            });
            CDB.RequestUseItem(pc, tx, () -> {
                invalidateTamCacheForPc(pc);
                SC_ServerMessage.send(pc, 403, fruit.getName() + " (1)");
                SC_TAM_WINDOW.sendAsync(pc);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "예약 취소 처리에 실패했습니다.");
        }
    }

    private static void deleteReservation(Connection con, String account, String encoded,
            long reservationId, int targetId, int day) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT objid FROM characters WHERE objid=? AND account_name=? FOR UPDATE")) {
            ps.setInt(1, targetId);
            ps.setString(2, account);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Tam refund owner mismatch");
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id, objid, day FROM tam WHERE encobjid=? ORDER BY id ASC LIMIT 1 FOR UPDATE")) {
            ps.setString(1, encoded);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getLong(1) != reservationId
                        || rs.getInt(2) != targetId || rs.getInt(3) != day)
                    throw new SQLException("Tam refund reservation changed");
            }
        }
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM tam WHERE id=? AND objid=?")) {
            ps.setLong(1, reservationId);
            ps.setInt(2, targetId);
            if (ps.executeUpdate() != 1) throw new SQLException("Tam refund delete failed");
        }
    }

    private static void applyOnlineBuffWithSnapshot(L1PcInstance pc, int stage, int remainSec) {
        if (pc == null) return;

        if (stage < 0) stage = 0;
        if (stage > MAX_STAGE) stage = MAX_STAGE;
        if (remainSec < 0) remainSec = 0;

        clearAllTamEffects(pc);

        if (stage >= 1 && remainSec > 0) {
            applyStage(pc, stage, remainSec);
        }

        SC_TAM_WINDOW.sendAsync(pc);
        try { SC_OwnCharStatus.send(pc); } catch (Throwable ignore) {};
    }

    private static void clearAllTamEffects(L1PcInstance pc) {
        for (int s = 1; s <= MAX_STAGE; s++) {
            final int skill = TAM_SKILLS[s];
            try {
                if (pc.hasSkillEffect(skill)) {
                    pc.killSkillEffectTimer(skill);
                    
                    try {
                        SC_NewCreateItem.sendBuffOrder(pc, SC_NewCreateItem.버프창, 0, ICON1[s], ICON2[s]);
                    } catch (Throwable ignore) {}

                    try { if (pc.getAC() != null) pc.getAC().addAc(s); } catch (Throwable ignore) {}

                    try {
                        int dr = DR_BY_STAGE[s];
                        if (dr != 0) pc.addDamageReductionByArmor(-dr);
                    } catch (Throwable ignore) {}
                }
            } catch (Throwable ignore) {}
        }
    }

    private static void applyStage(L1PcInstance pc, int stage, int remainSec) {
        if (remainSec < 0) remainSec = 0;

        // 아이콘/버프창
        try {
            SC_NewCreateItem.sendBuffOrder(pc, SC_NewCreateItem.버프창, remainSec, ICON1[stage], ICON2[stage]);
        } catch (Throwable ignore) {}

        // 스킬 타이머(ms)
        try { pc.setSkillEffect(TAM_SKILLS[stage], timerMillis(remainSec)); } catch (Throwable ignore) {}

        // AC 적용
        try { if (pc.getAC() != null) pc.getAC().addAc(-stage); } catch (Throwable ignore) {}

        // DR 적용(3~5단)
        try {
            int dr = DR_BY_STAGE[stage];
            if (dr != 0) pc.addDamageReductionByArmor(dr);
        } catch (Throwable ignore) {}
    }

    private static int timerMillis(int remainSec) {
        // The existing skill API accepts signed int milliseconds. A 30-day
        // fruit exceeds that range; refresh normally at the clamped expiry.
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) remainSec * 1000L));
    }

    public static void onTamSkillStopped(L1PcInstance pc, int skillId) {
        // The skill container removes the expired ID before invoking its stop
        // handler. clearAllTamEffects cannot see that ID to undo its stats.
        for (int stage = 1; stage <= MAX_STAGE; stage++) {
            if (TAM_SKILLS[stage] != skillId) continue;
            if (pc.getAC() != null) pc.getAC().addAc(stage);
            int dr = DR_BY_STAGE[stage];
            if (dr != 0) pc.addDamageReductionByArmor(-dr);
            try {
                SC_NewCreateItem.sendBuffOrder(pc, SC_NewCreateItem.버프창, 0, ICON1[stage], ICON2[stage]);
            } catch (RuntimeException ignored) {}
            break;
        }
        syncForPc(pc);
    }

    private static void postEffectForCaller(L1PcInstance caller) {
        SC_TAM_WINDOW.sendAsync(caller);
        SC_ServerMessage.send(caller, 3916);
        SC_SkillSound.sendAndBroadcast(caller, caller.getId(), 2028);
        try { SC_SkillSound.broadcast(caller, caller.getId(), 2028); } catch (Throwable ignore) {}
        try { SC_OwnCharStatus.send(caller); } catch (Throwable ignore) {};
    }

    private static void invalidateTamCacheForPc(L1PcInstance pc) {
        try {
            if (pc == null) return;
            if (pc.getNetConnection() == null) return;
            Account acc = pc.getNetConnection().getAccount();
            if (acc != null) acc.invalidateTamCache();
        } catch (Throwable ignore) {}
    }

    private static final class TamSnapshot {
        final int stage;
        final int remainSec;
        TamSnapshot(int stage, int remainSec) { this.stage = stage; this.remainSec = remainSec; }
    }

    /**
     * 진짜 갱신용 스냅샷 - DB에서 바로 stage/remain 계산 
     */
    private static TamSnapshot snapshotTamFromDb(String accountName) {
        if (accountName == null || accountName.isEmpty()) return new TamSnapshot(0, 0);

        int stage = 0;
        long minRemainMs = Long.MAX_VALUE;
        final long now = System.currentTimeMillis();

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(SQL_SELECT_ENDS_BY_ACCOUNT)) {

            ps.setString(1, accountName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp end = rs.getTimestamp(1);
                    if (end == null) continue;

                    long endMs = end.getTime();
                    // 동일하게 "2초 여유" 기준으로 활성 판단
                    if ((now / 1000L) + 2 <= (endMs / 1000L)) {
                        stage++;
                        long remain = endMs - now;
                        if (remain > 0 && remain < minRemainMs) minRemainMs = remain;
                    }
                }
            }
        } catch (Throwable ignore) {}

        if (stage < 0) stage = 0;
        if (stage > MAX_STAGE) stage = MAX_STAGE;

        int remainSec = (minRemainMs == Long.MAX_VALUE) ? 0 : (int) Math.max(0L, minRemainMs / 1000L);
        return new TamSnapshot(stage, remainSec);
    }

    private static String byteWrite(long value) {
        long temp = value / 128;
        StringBuilder sb = new StringBuilder(32);

        if (temp > 0) {
            sb.append((byte) hextable[(int) (value % 128)]);
            while (temp >= 128) {
                sb.append((byte) hextable[(int) (temp % 128)]);
                temp = temp / 128;
            }
            if (temp > 0) sb.append((int) temp);
        } else {
            if (value == 0) {
                sb.append(0);
            } else {
                sb.append((byte) hextable[(int) value]);
                sb.append(0);
            }
        }
        return sb.toString();
    }

    public static long readLoginSnapshot(String accountName) {
        TamSnapshot result = snapshotTamFromDb(accountName);
        return ((long) result.stage << 32) | (result.remainSec & 0xffffffffL);
    }

    public static void applyLoginSnapshot(L1PcInstance pc, long packed) {
        if (pc == null || pc.getNetConnection() == null) return;
        applyLoginBuffWithSnapshot(pc, (int) (packed >>> 32), (int) packed);
    }

    /**
     * 로그인/리프레시용 강제 동기화
     * - pc 로그인 직후, 또는 TamEndTime 변경 직후 호출하면 됨.
     */
    public static void syncForPc(L1PcInstance pc) {
        if (pc == null) return;
        if(pc.getNetConnection() == null) {
        	return;
        }
        TamSnapshot fresh = snapshotTamFromDb(pc.getAccountName());
        applyLoginBuffWithSnapshot(pc, fresh.stage, fresh.remainSec);
    }
    
    private static void applyLoginBuffWithSnapshot(L1PcInstance pc, int stage, int remainSec) {
        if (pc == null) return;

        if (stage < 0) stage = 0;
        if (stage > MAX_STAGE) stage = MAX_STAGE;
        if (remainSec < 0) remainSec = 0;

        clearAllTamEffects(pc);

        if (stage >= 1 && remainSec > 0) {
            applyStage(pc, stage, remainSec);
        }
        try { SC_OwnCharStatus.send(pc); } catch (Throwable ignore) {};
    }
}
