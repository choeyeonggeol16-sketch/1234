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
import java.util.concurrent.locks.ReentrantLock;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.packets.Protocol.Packet.SC_TAM_WINDOW;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class TamFruitUseService {

    private TamFruitUseService() {}

    private static final long DAY_MS = TimeUnit.DAYS.toMillis(1);
    private static final int MAX_STAGE = 5;

    private static final String SQL_EXISTS_CHAR =
            "SELECT 1 FROM characters WHERE objid=? LIMIT 1";

    private static final String SQL_SELECT_CHAR_NAME_AND_END =
            "SELECT TamEndTime, char_name FROM characters WHERE objid=?";

    private static final String SQL_APPLY_TAM_ATOMIC =
            "UPDATE characters SET TamEndTime=? WHERE objid=? AND (TamEndTime IS NULL OR TamEndTime < ?)";

    private static final String SQL_INSERT_RESERVE =
            "INSERT INTO Tam SET objid=?, Name=?, Day=? , encobjid=?";

    private static final String SQL_SELECT_ENDS_BY_ACCOUNT =
            "SELECT TamEndTime FROM characters WHERE account_name=?";

    private static final long RESERVE_THROTTLE_MS = 2000L;
    private static final ConcurrentHashMap<Integer, Long> RESERVE_THROTTLE = new ConcurrentHashMap<>(4096);
    private static final AtomicInteger RESERVE_SWEEPS = new AtomicInteger();

    private static final int LOCK_STRIPES = 256;
    private static final ReentrantLock[] LOCKS = new ReentrantLock[LOCK_STRIPES];
    
    static {
        for (int i = 0; i < LOCK_STRIPES; i++) LOCKS[i] = new ReentrantLock(false);
    }
    private static ReentrantLock lockFor(int id) {
        return LOCKS[id & (LOCK_STRIPES - 1)];
    }

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
    public static void useFruit(final L1PcInstance caller, final int rawObjId, final L1ItemInstance item, final int day) {
        if (caller == null || item == null) return;
        if (caller.getId() == 0) return;
        if (day <= 0) return;

        final long now = System.currentTimeMillis();
        final Timestamp atomicNowTs = new Timestamp(now - 2000L);
        final Timestamp newEnd = new Timestamp(now + (DAY_MS * (long) day) + 10_000L);

        final int targetObjId = resolveTargetObjId(caller, rawObjId);

        final ReentrantLock lk = lockFor(targetObjId);
        lk.lock();
        try {
            invalidateTamCacheForPc(caller);

            // 5개 제한 판단용 스냅샷(이건 캐시여도 상관없음)
            final TamSnapshot callerSnap = snapshotTamFast(caller);

            boolean targetAlreadyActive = false;
            try {
                NameAndEnd ne0 = fetchNameAndEnd(targetObjId);
                if (ne0 != null && ne0.end != null) {
                    long endMs = ne0.end.getTime();
                    if ((now / 1000L) + 2 <= (endMs / 1000L)) {
                        targetAlreadyActive = true;
                    }
                }
            } catch (Throwable ignore) {}

            // 5개 제한은 "새로 켜는 경우"만 차단
            if (!targetAlreadyActive && callerSnap.stage >= MAX_STAGE) {
                SC_ServerMessage.send(caller, 3904);
                return;
            }

            // 적용 시도
            final ApplyResult ar = applyTamEndAtomic(targetObjId, newEnd, atomicNowTs);

            if (ar == ApplyResult.NO_SUCH_CHAR) {
                SC_SystemMessage.send(caller, "탐 열매 적용 실패(대상 오류).");
                return;
            }

            // 이미 켜져있음 -> 예약
            if (ar == ApplyResult.ALREADY_ACTIVE) {
                if (!allowReserveNow(targetObjId, now)) {
                    SC_SystemMessage.send(caller, "잠시 후 다시 시도해주세요.");
                    return;
                }

                final NameAndEnd ne = fetchNameAndEnd(targetObjId);
                final String name = (ne != null && ne.name != null && !ne.name.isEmpty()) ? ne.name : safeName(caller);

                insertReserveBestEffort(targetObjId, name, day);

                invalidateTamCacheForPc(caller);
                SC_TAM_WINDOW.sendAsync(caller);
                SC_SystemMessage.send(caller, "[" + name + "] 에 이미 이용중인 상품이 있어 예약 되었습니다.");

                caller.getInventory().removeItem(item, 1);
                return;
            }

            final L1PcInstance targetPc = findOnlinePc(targetObjId);
            if (targetPc != null) {
                try { targetPc.setTamTime(newEnd); } catch (Throwable ignore) {}
            }

            final TamSnapshot fresh = snapshotTamFromDb(caller.getAccountName());

            applyOnlineBuffWithSnapshot(caller, fresh.stage, fresh.remainSec);

            if (targetPc != null && targetPc != caller) {
                applyOnlineBuffWithSnapshot(targetPc, fresh.stage, fresh.remainSec);
            }

            // UI/이펙트
            postEffectForCaller(caller);

            caller.getInventory().removeItem(item, 1);

        } catch (Throwable t) {
            SC_SystemMessage.send(caller, "탐 열매 사용 중 오류가 발생했습니다.");
        } finally {
            lk.unlock();
        }
    }

    private enum ApplyResult { APPLIED, ALREADY_ACTIVE, NO_SUCH_CHAR }

    private static ApplyResult applyTamEndAtomic(int objId, Timestamp newEnd, Timestamp nowTs) {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(SQL_APPLY_TAM_ATOMIC)) {

            ps.setTimestamp(1, newEnd);
            ps.setInt(2, objId);
            ps.setTimestamp(3, nowTs);

            final int updated = ps.executeUpdate();
            if (updated > 0) return ApplyResult.APPLIED;

        } catch (SQLException e) {
            return ApplyResult.NO_SUCH_CHAR;
        }

        return existsChar(objId) ? ApplyResult.ALREADY_ACTIVE : ApplyResult.NO_SUCH_CHAR;
    }

    private static boolean existsChar(int objId) {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(SQL_EXISTS_CHAR)) {
            ps.setInt(1, objId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static boolean allowReserveNow(int objId, long now) {
        if ((RESERVE_SWEEPS.incrementAndGet() & 1023) == 0) {
            final long cutoff = now - 60_000L;
            RESERVE_THROTTLE.forEach((key, timestamp) -> {
                if (timestamp.longValue() < cutoff) RESERVE_THROTTLE.remove(key, timestamp);
            });
        }
        final Integer k = Integer.valueOf(objId);
        final Long prev = RESERVE_THROTTLE.get(k);
        if (prev == null) {
            RESERVE_THROTTLE.put(k, Long.valueOf(now));
            return true;
        }
        if ((now - prev.longValue()) >= RESERVE_THROTTLE_MS) {
            RESERVE_THROTTLE.put(k, Long.valueOf(now));
            return true;
        }
        return false;
    }

    private static void insertReserveBestEffort(int objId, String name, int day) {
        final String enc = byteWrite(objId);
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(SQL_INSERT_RESERVE)) {
            ps.setInt(1, objId);
            ps.setString(2, name != null ? name : "");
            ps.setInt(3, day);
            ps.setString(4, enc != null ? enc : "");
            ps.executeUpdate();
        } catch (SQLException ignore) {}
    }

    private static int resolveTargetObjId(L1PcInstance caller, int raw) {
        if (raw <= 0) return caller.getId();

        L1PcInstance online = findOnlinePc(raw);
        if (online != null) return online.getId();

        if (existsChar(raw)) return raw;

        return caller.getId();
    }

    private static L1PcInstance findOnlinePc(int objId) {
        try {
            L1Object o = L1World.getInstance().findObject(objId);
            if (o instanceof L1PcInstance) return (L1PcInstance) o;
        } catch (Throwable ignore) {}
        return null;
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
        try { pc.setSkillEffect(TAM_SKILLS[stage], remainSec * 1000); } catch (Throwable ignore) {}

        // AC 적용
        try { if (pc.getAC() != null) pc.getAC().addAc(-stage); } catch (Throwable ignore) {}

        // DR 적용(3~5단)
        try {
            int dr = DR_BY_STAGE[stage];
            if (dr != 0) pc.addDamageReductionByArmor(dr);
        } catch (Throwable ignore) {}
    }

    private static void postEffectForCaller(L1PcInstance caller) {
        SC_TAM_WINDOW.sendAsync(caller);
        SC_ServerMessage.send(caller, 3916);
        SC_SkillSound.sendAndBroadcast(caller, caller.getId(), 2028);
        try { SC_SkillSound.broadcast(caller, caller.getId(), 2028); } catch (Throwable ignore) {}
        try { SC_OwnCharStatus.send(caller); } catch (Throwable ignore) {};
    }

    private static final class NameAndEnd {
        final String name;
        final Timestamp end;
        NameAndEnd(String n, Timestamp e) { this.name = n; this.end = e; }
    }

    private static NameAndEnd fetchNameAndEnd(int objId) {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(SQL_SELECT_CHAR_NAME_AND_END)) {
            ps.setInt(1, objId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new NameAndEnd(rs.getString("char_name"), rs.getTimestamp("TamEndTime"));
                }
            }
        } catch (SQLException ignore) {}
        return null;
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
     * 빠른 스냅샷
     */
    private static TamSnapshot snapshotTamFast(L1PcInstance pc) {
        try {
            if (pc == null || pc.getNetConnection() == null) return new TamSnapshot(0, 0);
            Account acc = pc.getNetConnection().getAccount();
            if (acc == null) return new TamSnapshot(0, 0);

            int stage = acc.getTamHandler().getActiveCountAndRefresh();
            long remainMs = acc.getTamHandler().getRemainTimeMs();
            int remainSec = (int) Math.max(0L, remainMs / 1000L);

            if (stage < 0) stage = 0;
            if (stage > MAX_STAGE) stage = MAX_STAGE;
            return new TamSnapshot(stage, remainSec);
        } catch (Throwable t) {
            return new TamSnapshot(0, 0);
        }
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

    private static String safeName(L1PcInstance pc) {
        try { return pc.getName(); } catch (Throwable t) { return "알수없음"; }
    }

    private static void safeSendNow(L1PcInstance pc, ServerPacket pkt) {
        try { pc.sendPackets(pkt, true); } catch (Throwable ignore) {}
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
