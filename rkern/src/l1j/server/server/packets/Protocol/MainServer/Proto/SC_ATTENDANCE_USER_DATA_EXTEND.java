package l1j.server.server.packets.Protocol.MainServer.Proto;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.model.Attendance.AttendanceRewardClaims;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceUserFlag;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataExtend;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataGroup;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceState;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceTimeStatus;
import l1j.server.server.storage.CacheD;

/**
 * SC_ATTENDANCE_USER_DATA_EXTEND (0x0221)
 * 유저별 출석 데이터 본체 + DB 로드/저장 + 전송.
 *
 * 와이어: 1 groups(repeated msg) / 2 user_flag(enum) / 3 active_group(repeated enum)
 */
public class SC_ATTENDANCE_USER_DATA_EXTEND extends ProtoPacket {
    private static final String READ_SNAPSHOT =
            "SELECT attendance_data, attendance_indices, attendance_calendar FROM attendance_userinfo WHERE account=?";
    private static final String UPDATE_SNAPSHOT =
            "UPDATE attendance_userinfo SET attendance_data=?, attendance_indices=?, attendance_calendar=? WHERE account=?";

    // ────────────── 전송 ──────────────

    public static void send(L1PcInstance pc) {
        if (pc == null) return;
        final SC_ATTENDANCE_USER_DATA_EXTEND data = pc.getAttendanceData();
        if (data != null) {
            Proto.send(pc, data);
            return;
        }
        // A packet sender runs on a game/CIO owner. Never synchronously
        // query JDBC here when the login snapshot is absent.
        final CUserSocket socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return;
        final String account = pc.getAccountName();
        if (account == null || account.isEmpty()) return;
        try {
            CacheD.getInstance().submitDbJobOrdered(account, () -> {
                try {
                    final LoginSnapshot loaded = readLoginSnapshot(account);
                    if (!l1j.server.server.model.network.thread.CIOThreadCallback.getInstance()
                            .completeSessionReply(socket, () -> {
                        if (!socket.isConnected() || socket.getActiveChar() != pc
                                || !account.equals(pc.getAccountName())) return;
                        if (pc.getAttendanceData() == null) applyLoginSnapshot(pc, loaded);
                        final SC_ATTENDANCE_USER_DATA_EXTEND current = pc.getAttendanceData();
                        if (current != null) Proto.send(pc, current);
                    })) {
                        java.util.logging.Logger.getLogger(SC_ATTENDANCE_USER_DATA_EXTEND.class.getName())
                                .warning("Attendance snapshot callback rejected: " + account);
                    }
                } catch (RuntimeException e) {
                    java.util.logging.Logger.getLogger(SC_ATTENDANCE_USER_DATA_EXTEND.class.getName())
                            .log(java.util.logging.Level.WARNING, "Attendance snapshot load failed: " + account, e);
                }
            });
        } catch (RuntimeException rejected) {
            java.util.logging.Logger.getLogger(SC_ATTENDANCE_USER_DATA_EXTEND.class.getName())
                    .log(java.util.logging.Level.WARNING, "Attendance snapshot admission rejected: " + account, rejected);
        }
    }

    // ────────────── DB: update ──────────────

    public static void update(String account, SC_ATTENDANCE_USER_DATA_EXTEND userData) {
        if (account == null || userData == null) return;
        synchronized (userData) {
            userData.queueSnapshot(account, userData.bodyBytes());
        }
    }

    private void queueSnapshot(String account, byte[] bytes) {
        final long day = attendanceDay;
        if (day <= 0L) return;
        final String indices = createIndices();
        CacheD.getInstance().submitDbJobOrdered(account, () -> saveSnapshot(account, bytes, indices, day));
    }

    /** Stage exactly one claim on the caller's inventory receipt connection. */
    public static boolean stageRewardClaim(Connection con, String account, AttendanceGroupType type,
            int indexZeroBased) throws SQLException {
        final LoginSnapshot saved = readSnapshot(con, account, true);
        if (!saved.present || saved.data == null) return false;
        final SC_ATTENDANCE_USER_DATA_EXTEND data = decodeSnapshot(saved.data);
        final UserAttendanceDataGroup group = AttendanceRewardClaims.findGroup(data, type);
        final UserAttendanceDataExtend slot = AttendanceRewardClaims.findSlot(group, indexZeroBased);
        if (slot == null || slot.get_state() != UserAttendanceState.COMPLETE) return false;
        AttendanceRewardClaims.markClaimed(group, slot);
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE attendance_userinfo SET attendance_data=? WHERE account=?")) {
            ps.setBytes(1, data.bodyBytes());
            ps.setString(2, account);
            if (ps.executeUpdate() != 1) throw new SQLException("Attendance reward row changed: " + account);
        }
        return true;
    }

    private static void saveSnapshot(String account, byte[] bytes, String indices, long day) {
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                final LoginSnapshot saved = readSnapshot(con, account, true);
                final long savedDay = saved.dayMillis();
                // Only the first login creates a row. A queued snapshot cannot
                // recreate data that an administrator explicitly cleared.
                if (saved.present && savedDay <= day) {
                    if (saved.data != null && saved.data.length > 0) {
                        final SC_ATTENDANCE_USER_DATA_EXTEND incoming = decodeSnapshot(bytes);
                        final SC_ATTENDANCE_USER_DATA_EXTEND durable = decodeSnapshot(saved.data);
                        incoming.applyIndices(indices);
                        durable.applyIndices(saved.indices);
                        if (savedDay > 0L && savedDay < day)
                            durable.advanceDay(incoming._userFlag == AttendanceUserFlag.USER_FLAG_PC_CAFE);
                        if (mergeProgress(incoming, durable, savedDay > 0L)) {
                            bytes = incoming.bodyBytes();
                            indices = incoming.createIndices();
                        }
                    }
                    try (PreparedStatement ps = con.prepareStatement(UPDATE_SNAPSHOT)) {
                        ps.setBytes(1, bytes);
                        ps.setString(2, indices);
                        ps.setTimestamp(3, new Timestamp(day));
                        ps.setString(4, account);
                        ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException failure) {
                try { con.rollback(); }
                catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
        } catch (SQLException | RuntimeException failure) {
            throw new IllegalStateException("Attendance DB save failed: " + account, failure);
        }
    }

    private static boolean mergeProgress(SC_ATTENDANCE_USER_DATA_EXTEND incoming,
            SC_ATTENDANCE_USER_DATA_EXTEND durable, boolean alignedDay) throws SQLException {
        if (durable._groups == null) return false;
        boolean changed = false;
        for (UserAttendanceDataGroup source : durable._groups) {
            final UserAttendanceDataGroup target = AttendanceRewardClaims.findGroup(incoming, source.get_groupType());
            for (UserAttendanceDataExtend stored : source.get_groupData()) {
                final UserAttendanceState state = stored.get_state();
                if (state != UserAttendanceState.CLEAR && state != UserAttendanceState.COMPLETE) continue;
                final UserAttendanceDataExtend slot = AttendanceRewardClaims.findSlot(target, stored.get_index() - 1);
                if (slot == null) throw new SQLException("Attendance snapshot loses a completed slot");
                if (state == UserAttendanceState.CLEAR && slot.get_state() != UserAttendanceState.CLEAR) {
                    slot.set_time(Math.max(slot.get_time(), stored.get_time()));
                    slot.set_state(UserAttendanceState.CLEAR);
                    changed = true;
                } else if (state == UserAttendanceState.COMPLETE && slot.get_state() == UserAttendanceState.INIT) {
                    slot.set_state(UserAttendanceState.COMPLETE);
                    slot.set_time(stored.get_time());
                    changed = true;
                }
            }
            if (target == null) continue;
            final UserAttendanceTimeStatus previousStatus = target.get_resultCode();
            AttendanceRewardClaims.reconcileGroupStatus(target);
            changed |= previousStatus != target.get_resultCode();
            if (alignedDay) {
                if (target.get_currentAttendanceIndex() < source.get_currentAttendanceIndex()) {
                    target.set_currentAttendanceIndex(source.get_currentAttendanceIndex());
                    target.set_attendanceTime(source.get_attendanceTime());
                    changed = true;
                }
                if (target.get_recvedRewardCount() < source.get_recvedRewardCount()) {
                    target.set_recvedRewardCount(source.get_recvedRewardCount());
                    if (source.get_resultCode() == UserAttendanceTimeStatus.ATTENDANCE_NO_MORE_TIME
                            && target.get_resultCode() == UserAttendanceTimeStatus.ATTENDANCE_NORMAL)
                        target.set_resultCode(UserAttendanceTimeStatus.ATTENDANCE_NO_MORE_TIME);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private void advanceDay(boolean cafe) {
        if (_groups == null) return;
        for (UserAttendanceDataGroup group : _groups) group.onUpdateDay(cafe);
    }

    public static final class LoginSnapshot {
        public final boolean present;
        public final byte[] data;
        public final String indices;
        public final Timestamp calendar;

        LoginSnapshot(boolean present, byte[] data, String indices, Timestamp calendar) {
            this.present = present;
            this.data = data;
            this.indices = indices;
            this.calendar = calendar;
        }

        private long dayMillis() {
            return calendar == null ? 0L : atSecond(calendar.getTime());
        }
    }

    private static long atSecond(long millis) {
        return millis / 1000L * 1000L;
    }

    private static LoginSnapshot readSnapshot(Connection con, String account, boolean lock) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(READ_SNAPSHOT + (lock ? " FOR UPDATE" : " LIMIT 1"))) {
            ps.setString(1, account);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new LoginSnapshot(false, null, null, null);
                return new LoginSnapshot(true, rs.getBytes(1), rs.getString(2), rs.getTimestamp(3));
            }
        }
    }

    public static LoginSnapshot readLoginSnapshot(String account) {
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            return readSnapshot(con, account, false);
        } catch (SQLException failure) {
            throw new IllegalStateException("Attendance DB load failed: " + account, failure);
        }
    }

    private static SC_ATTENDANCE_USER_DATA_EXTEND decodeSnapshot(byte[] bytes) {
        final SC_ATTENDANCE_USER_DATA_EXTEND data = newInstance();
        if (bytes != null && bytes.length > 0) data.readFrom(bytes);
        return data;
    }

    private void applyIndices(String indices) {
        if (_groups == null || indices == null || indices.isEmpty()) return;
        final String[] parts = indices.split(",");
        for (int i = 0; i < parts.length && i < _groups.size(); i++)
            _groups.get(i).set_currentAttendanceIndex(Integer.parseInt(parts[i].trim()));
    }

    private void insertInitialSnapshot(String account) {
        final byte[] bytes = bodyBytes();
        final String indices = createIndices();
        final long day = attendanceDay;
        if (day <= 0L) return;
        CacheD.getInstance().submitDbJobOrdered(account, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO attendance_userinfo (account, attendance_data, attendance_indices, attendance_calendar) "
                         + "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE account=VALUES(account)")) {
                ps.setString(1, account);
                ps.setBytes(2, bytes);
                ps.setString(3, indices);
                ps.setTimestamp(4, new Timestamp(day));
                ps.executeUpdate();
            } catch (SQLException failure) {
                throw new IllegalStateException("Attendance DB initial insert failed: " + account, failure);
            }
        });
    }

    public static void applyLoginSnapshot(L1PcInstance pc, LoginSnapshot snapshot) {
        if (snapshot == null || pc == null) return;
        final SC_ATTENDANCE_USER_DATA_EXTEND userData = decodeSnapshot(snapshot.data);
        final long savedDay = snapshot.dayMillis();
        final long currentDay = atSecond(AttendanceLoadManager.attendanceDayMillis());
        if (savedDay <= 0L && currentDay <= 0L) return;
        final boolean needsGroups = userData._groups == null;
        final boolean cafe = pc.PC방_버프;
        userData.attendanceDay = Math.max(savedDay, currentDay);
        if (needsGroups) {
            for (AttendanceGroupType group : AttendanceGroupType.values()) {
                userData.add_groups(UserAttendanceDataGroup.newInstance(pc, group));
                userData.add_active_group(group);
            }
        } else {
            userData.applyIndices(snapshot.indices);
            for (UserAttendanceDataGroup group : userData._groups)
                AttendanceRewardClaims.reconcileGroupStatus(group);
        }
        userData.set_userFlag(cafe ? AttendanceUserFlag.USER_FLAG_PC_CAFE : AttendanceUserFlag.USER_FLAG_NORMAL);
        if (savedDay > 0L && savedDay < currentDay && !needsGroups) userData.advanceDay(cafe);
        synchronized (userData) {
            pc.setAttendanceData(userData);
            if (!snapshot.present) {
                userData.insertInitialSnapshot(pc.getAccountName());
            } else if (needsGroups || savedDay < currentDay) {
                userData.queueSnapshot(pc.getAccountName(), userData.bodyBytes());
            }
        }
    }

    // ────────────── 갱신 (타이머) ──────────────

    private void handleUpdateTime(L1PcInstance pc, int second, boolean checkDayUpdate) throws IOException {
        final byte[] frame;
        synchronized (this) {
            if (pc.getAttendanceData() != this || _userFlag == null || _groups == null) return;
            final long day = checkDayUpdate ? atSecond(AttendanceLoadManager.attendanceDayMillis()) : attendanceDay;
            if (checkDayUpdate && day <= attendanceDay) return;

            boolean changed = false;
            final int activeGroupLimit = _userFlag.toInt();
            for (int i = 0; i <= activeGroupLimit && i < _groups.size(); i++) {
                UserAttendanceDataGroup group = _groups.get(i);
                if (group != null) changed |= group.onUpdateTime(second);
            }

            if (checkDayUpdate) {
                attendanceDay = day;
                advanceDay(_userFlag == AttendanceUserFlag.USER_FLAG_PC_CAFE);
                changed = true;
            }
            if (!changed) return;

            final ProtoWriter writer = ProtoWriter.newInstance();
            body(writer);
            queueSnapshot(pc.getAccountName(), writer.bodyBytes());
            frame = writer.finishWithTrailer(ProtoMsg.SC_ATTENDANCE_USER_DATA_EXTEND);
        }
        Proto.sendRaw(pc, frame);
    }

    public void onUpdateDay(L1PcInstance pc, int second) throws IOException {
        handleUpdateTime(pc, second, true);
    }

    public void onUpdateTime(L1PcInstance pc, int second) throws IOException {
        handleUpdateTime(pc, second, false);
    }

    // ────────────── indices ──────────────

    public synchronized String createIndices() {
        if (_groups == null || _groups.isEmpty()) {
            return "";
        }
        int size = _groups.size();
        StringBuilder sb = new StringBuilder(size * 3);
        for (int i = 0; i < size; ++i) {
            if (i != 0) sb.append(",");
            sb.append(_groups.get(i).get_currentAttendanceIndex());
        }
        return sb.toString();
    }

    // ────────────── 본체 ──────────────

    public static SC_ATTENDANCE_USER_DATA_EXTEND newInstance() {
        return new SC_ATTENDANCE_USER_DATA_EXTEND();
    }

    private ArrayList<UserAttendanceDataGroup> _groups;
    private AttendanceUserFlag _userFlag;
    private ArrayList<AttendanceGroupType> _active_group;
    private long attendanceDay;

    private SC_ATTENDANCE_USER_DATA_EXTEND() {}

    public ArrayList<UserAttendanceDataGroup> get_groups() {
        return _groups;
    }

    public void add_groups(UserAttendanceDataGroup val) {
        if (_groups == null) {
            _groups = new ArrayList<UserAttendanceDataGroup>(AttendanceGroupType.values().length);
        }
        _groups.add(val);
    }

    public AttendanceUserFlag get_userFlag() {
        return _userFlag;
    }

    public synchronized void set_userFlag(AttendanceUserFlag val) {
        _userFlag = val;

        final UserAttendanceDataGroup dGroup = AttendanceRewardClaims.findGroup(this, AttendanceGroupType.PC_CAFE);
        if (dGroup == null || val == null
                || dGroup.get_resultCode() == UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR
                || dGroup.get_resultCode() == UserAttendanceTimeStatus.ATTENDANCE_ALL_COMPLETE) return;
        if (val == AttendanceUserFlag.USER_FLAG_NORMAL) {
            dGroup.set_resultCode(UserAttendanceTimeStatus.ATTENDANCE_CANT_BE_ACHIEVE_TIME);
        } else {
            int index = dGroup.get_currentAttendanceIndex();
            if (index <= dGroup.get_groupData().size() - 1) {
                UserAttendanceDataExtend gData = dGroup.get_groupData().get(index);
                if (gData.get_state().equals(UserAttendanceState.INIT)) {
                    dGroup.set_resultCode(UserAttendanceTimeStatus.ATTENDANCE_NORMAL);
                } else {
                    dGroup.set_resultCode(UserAttendanceTimeStatus.ATTENDANCE_NO_MORE_TIME);
                }
            }
        }
    }

    public ArrayList<AttendanceGroupType> get_active_group() {
        return _active_group;
    }

    public void add_active_group(AttendanceGroupType val) {
        if (_active_group == null) {
            _active_group = new ArrayList<AttendanceGroupType>(AttendanceGroupType.values().length);
        }
        _active_group.add(val);
    }

    // ────────────── 직렬화 ──────────────

    @Override
    public int msgId() {
        return ProtoMsg.SC_ATTENDANCE_USER_DATA_EXTEND;
    }

    @Override
    protected synchronized void body(ProtoWriter w) {
        writeFields(w, _groups, _userFlag, _active_group);
    }

    /** DB 저장용 body만 */
    public byte[] bodyBytes() {
        ProtoWriter w = ProtoWriter.newInstance();
        body(w);
        return w.bodyBytes();
    }

    // ────────────── 역직렬화 (DB blob) ──────────────

    public void readFrom(byte[] blob) {
        ProtoReader r = new ProtoReader(blob, 0, blob.length);
        while (!r.isAtEnd()) {
            int tag = r.tag();
            switch (tag >>> 3) {
            case 1:
                add_groups(UserAttendanceDataGroup.parse(r.readBytes()));
                break;
            case 2:
                _userFlag = AttendanceUserFlag.fromInt(r.varint32());
                break;
            case 3:
                add_active_group(AttendanceGroupType.fromInt(r.varint32()));
                break;
            default:
                r.skip(tag & 7);
                break;
            }
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, ArrayList<UserAttendanceDataGroup> _groups, AttendanceUserFlag _userFlag, ArrayList<AttendanceGroupType> _active_group) {
        if (_groups != null) {
                for (UserAttendanceDataGroup val : _groups) {
                    w.msg(1);
                    val.writeTo(w);
                    w.end();
                }
            }
            if (_userFlag != null) {
                w.enumV(2, _userFlag.toInt());
            }
            if (_active_group != null) {
                for (AttendanceGroupType val : _active_group) {
                    w.enumV(3, val.toInt());
                }
            }
    }

    public static boolean sendFields(L1PcInstance recipient, ArrayList<UserAttendanceDataGroup> _groups, AttendanceUserFlag _userFlag, ArrayList<AttendanceGroupType> _active_group) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _groups, _userFlag, _active_group);
    }

    public static boolean sendFields(CUserSocket recipient, ArrayList<UserAttendanceDataGroup> _groups, AttendanceUserFlag _userFlag, ArrayList<AttendanceGroupType> _active_group) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ATTENDANCE_USER_DATA_EXTEND, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _groups, _userFlag, _active_group);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(ArrayList<UserAttendanceDataGroup> _groups, AttendanceUserFlag _userFlag, ArrayList<AttendanceGroupType> _active_group) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _groups, _userFlag, _active_group);
        return writer.finishWithTrailer(ProtoMsg.SC_ATTENDANCE_USER_DATA_EXTEND);
    }

    private static boolean sendCtor(CUserSocket protoRecipient) {
        ArrayList<UserAttendanceDataGroup> proto17_groups = null;
        AttendanceUserFlag proto17_userFlag = null;
        ArrayList<AttendanceGroupType> proto17_active_group = null;


        return sendFields(protoRecipient, proto17_groups, proto17_userFlag, proto17_active_group);
    }

    public static boolean sendNewInstance(CUserSocket protoRecipient) {
        return sendCtor(protoRecipient);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }
    public static boolean sendConstructed(CUserSocket protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
