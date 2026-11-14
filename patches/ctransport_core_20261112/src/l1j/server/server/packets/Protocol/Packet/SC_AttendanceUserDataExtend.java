package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.Packet.Attend.AttendanceUserFlag;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceDataExtend;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceDataGroup;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceState;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceTimeStatus;
import l1j.server.server.utils.SQLUtil;

/**
 * SC_ATTENDANCE_USER_DATA_EXTEND (0x0221)
 * 유저별 출석 데이터 본체 + DB 로드/저장 + 전송.
 *
 * 와이어: 1 groups(repeated msg) / 2 user_flag(enum) / 3 active_group(repeated enum)
 */
public class SC_AttendanceUserDataExtend extends ProtoPacket {

    // ────────────── 전송 ──────────────

    public static void send(L1PcInstance pc) {
        if (pc == null) return;
        final SC_AttendanceUserDataExtend data = pc.getAttendanceData();
        if (data != null) {
            Proto.send(pc, data);
            return;
        }
        // A packet sender runs on a game/CIO owner. Never synchronously
        // register()/query JDBC here when the login snapshot is absent.
        final l1j.server.server.model.network.core.session.CIOClient socket = pc.getNetConnection();
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
                        final SC_AttendanceUserDataExtend current = pc.getAttendanceData();
                        if (current != null) Proto.send(pc, current);
                    })) {
                        java.util.logging.Logger.getLogger(SC_AttendanceUserDataExtend.class.getName())
                                .warning("Attendance snapshot callback rejected: " + account);
                    }
                } catch (RuntimeException e) {
                    java.util.logging.Logger.getLogger(SC_AttendanceUserDataExtend.class.getName())
                            .log(java.util.logging.Level.WARNING, "Attendance snapshot load failed: " + account, e);
                }
            });
        } catch (RuntimeException rejected) {
            java.util.logging.Logger.getLogger(SC_AttendanceUserDataExtend.class.getName())
                    .log(java.util.logging.Level.WARNING, "Attendance snapshot admission rejected: " + account, rejected);
        }
    }

    // ────────────── DB: update ──────────────

    /** 외부에서 호출되는 동기식 업데이트 메서드 */
    public static void update(String account, SC_AttendanceUserDataExtend userData) {
        if (userData == null) return;
        update(account, userData.bodyBytes(), userData.createIndices());
    }

    /** 실제 DB 업데이트 처리를 수행하는 내부 메서드 */
    public static void update(String account, byte[] data, String indices) {
        if (account == null || data == null) return;
        final byte[] bytes = data.clone();
        final Timestamp ts = new Timestamp(AttendanceLoadManager.ATTEN_STARTUP_CALENDAR.getTimeInMillis());
        CacheD.getInstance().submitDbJobOrdered(account, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO attendance_userInfo SET account=?, attendance_data=?, attendance_indices=?, attendance_calendar=? "
                         + "ON DUPLICATE KEY UPDATE attendance_data=VALUES(attendance_data), "
                         + "attendance_indices=VALUES(attendance_indices), attendance_calendar=VALUES(attendance_calendar)")) {
                ps.setString(1, account);
                ps.setBytes(2, bytes);
                ps.setString(3, indices);
                ps.setTimestamp(4, ts);
                ps.executeUpdate();
            } catch (Exception e) {
                throw new IllegalStateException("Attendance DB save failed: " + account, e);
            }
        });
    }

    public static void updateAsync(final String account, final byte[] data, final String indices) {
        update(account, data, indices);
    }

    // ────────────── DB: store / register ──────────────

    public static void store(L1PcInstance pc) {
        if (pc == null) return;
        SC_AttendanceUserDataExtend data = pc.getAttendanceData();
        if (data == null) return;
        try {
            update(pc.getAccountName(), data.bodyBytes(), data.createIndices());
        } catch (Exception e) {
            throw new IllegalStateException("Attendance snapshot failed: " + pc.getAccountName(), e);
        }
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
    }

    public static LoginSnapshot readLoginSnapshot(String account) {
        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT attendance_data, attendance_indices, attendance_calendar FROM attendance_userInfo WHERE account=? LIMIT 1")) {
            ps.setString(1, account);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new LoginSnapshot(false, null, null, null);
                return new LoginSnapshot(true, rs.getBytes(1), rs.getString(2), rs.getTimestamp(3));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Attendance DB load failed: " + account, e);
        }
    }

    private static void insertInitialSnapshotAsync(String account, SC_AttendanceUserDataExtend data) {
        final byte[] bytes = data.bodyBytes();
        final String indices = data.createIndices();
        final Timestamp calendar = new Timestamp(AttendanceLoadManager.ATTEN_STARTUP_CALENDAR.getTimeInMillis());
        CacheD.getInstance().submitDbJobOrdered(account, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO attendance_userInfo SET account=?, attendance_data=?, attendance_indices=?, attendance_calendar=? "
                         + "ON DUPLICATE KEY UPDATE account=VALUES(account)")) {
                ps.setString(1, account);
                ps.setBytes(2, bytes);
                ps.setString(3, indices);
                ps.setTimestamp(4, calendar);
                ps.executeUpdate();
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(SC_AttendanceUserDataExtend.class.getName())
                        .log(java.util.logging.Level.SEVERE, "Attendance CacheD initial insert failed: " + account, e);
            }
        });
    }

    private static void storeRolloverSnapshotAsync(String account, SC_AttendanceUserDataExtend data,
                                                   LoginSnapshot previous) {
        final byte[] bytes = data.bodyBytes();
        final String indices = data.createIndices();
        final Timestamp calendar = new Timestamp(AttendanceLoadManager.ATTEN_STARTUP_CALENDAR.getTimeInMillis());
        CacheD.getInstance().submitDbJobOrdered(account, () -> {
            try (Connection con = DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "UPDATE attendance_userInfo SET attendance_data=?, attendance_indices=?, attendance_calendar=? "
                         + "WHERE account=? AND attendance_calendar <=> ? "
                         + "AND attendance_data <=> ? AND attendance_indices <=> ?")) {
                ps.setBytes(1, bytes);
                ps.setString(2, indices);
                ps.setTimestamp(3, calendar);
                ps.setString(4, account);
                ps.setTimestamp(5, previous.calendar);
                ps.setBytes(6, previous.data);
                ps.setString(7, previous.indices);
                ps.executeUpdate();
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(SC_AttendanceUserDataExtend.class.getName())
                        .log(java.util.logging.Level.SEVERE, "Attendance CacheD day rollover failed: " + account, e);
            }
        });
    }

    public static void applyLoginSnapshot(L1PcInstance pc, LoginSnapshot snapshot) {
        if (snapshot == null || pc == null) return;
        SC_AttendanceUserDataExtend userData = newInstance();
        if (snapshot.present) {
            if (snapshot.data != null && snapshot.data.length > 0) userData.readFrom(snapshot.data);
            pc.setAttendanceData(userData);
            if (snapshot.indices != null && !snapshot.indices.isEmpty()) {
                String[] parts = snapshot.indices.split(",");
                for (int i = 0; i < parts.length && i < userData.get_groups().size(); i++)
                    userData.get_groups().get(i).set_currentAttendanceIndex(Integer.parseInt(parts[i].trim()));
            }
            userData.set_userFlag(pc.PC방_버프 ? AttendanceUserFlag.USER_FLAG_PC_CAFE : AttendanceUserFlag.USER_FLAG_NORMAL);
            Calendar systemCal = AttendanceLoadManager.ATTEN_STARTUP_CALENDAR;
            if (snapshot.calendar != null
                    && systemCal.getTimeInMillis() / 1000L != snapshot.calendar.getTime() / 1000L) {
                boolean cafe = userData.get_userFlag().equals(AttendanceUserFlag.USER_FLAG_PC_CAFE);
                for (UserAttendanceDataGroup g : userData.get_groups()) g.onUpdateDay(cafe);
                storeRolloverSnapshotAsync(pc.getAccountName(), userData, snapshot);
            }
        } else {
            for (AttendanceGroupType group : AttendanceGroupType.values()) {
                userData.add_groups(UserAttendanceDataGroup.newInstance(pc, group));
                userData.add_active_group(group);
            }
            pc.setAttendanceData(userData);
            userData.set_userFlag(pc.PC방_버프 ? AttendanceUserFlag.USER_FLAG_PC_CAFE : AttendanceUserFlag.USER_FLAG_NORMAL);
            insertInitialSnapshotAsync(pc.getAccountName(), userData);
        }
    }

    public static void register(L1PcInstance pc) {
        SC_AttendanceUserDataExtend userData = newInstance();

        try (Connection con = DatabaseFactory.getInstance().getConnection();
             PreparedStatement pstm = con.prepareStatement("SELECT * FROM attendance_userInfo WHERE account=? LIMIT 1")) {
            pstm.setString(1, pc.getAccountName());
            try (ResultSet rs = pstm.executeQuery()) {
                if (rs.next()) {
                    byte[] blob = rs.getBytes("attendance_data");
                    if (blob != null && blob.length > 0) {
                        userData.readFrom(blob);
                    }
                    pc.setAttendanceData(userData);

                    String indicesStr = rs.getString("attendance_indices");
                    if (indicesStr != null && !indicesStr.isEmpty()) {
                        String[] parts = indicesStr.split(",");
                        for (int i = 0; i < parts.length && i < userData.get_groups().size(); ++i) {
                            userData.get_groups().get(i).set_currentAttendanceIndex(Integer.parseInt(parts[i].trim()));
                        }
                    }

                    userData.set_userFlag(pc.PC방_버프 ? AttendanceUserFlag.USER_FLAG_PC_CAFE : AttendanceUserFlag.USER_FLAG_NORMAL);

                    Calendar systemCal = AttendanceLoadManager.ATTEN_STARTUP_CALENDAR;
                    Timestamp dbTs = rs.getTimestamp("attendance_calendar");
                    if (dbTs != null) {
                        if ((systemCal.getTimeInMillis() / 1000) != (dbTs.getTime() / 1000)) {
                            boolean isPcCafe = userData.get_userFlag().equals(AttendanceUserFlag.USER_FLAG_PC_CAFE);
                            for (UserAttendanceDataGroup g : userData.get_groups()) {
                                g.onUpdateDay(isPcCafe);
                            }
                            store(pc);
                        }
                    }
                } else {
                    for (AttendanceGroupType gType : AttendanceGroupType.values()) {
                        userData.add_groups(UserAttendanceDataGroup.newInstance(pc, gType));
                        userData.add_active_group(gType);
                    }
                    pc.setAttendanceData(userData);
                    userData.set_userFlag(pc.PC방_버프 ? AttendanceUserFlag.USER_FLAG_PC_CAFE : AttendanceUserFlag.USER_FLAG_NORMAL);
                    store(pc);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // ────────────── 갱신 (타이머) ──────────────

    private void handleUpdateTime(L1PcInstance pc, int second, boolean checkDayUpdate) throws IOException {
        if (_userFlag == null || _groups == null) return;

        boolean isUpdate = false;
        int activeGroupLimit = _userFlag.toInt();

        for (int i = 0; i <= activeGroupLimit && i < _groups.size(); ++i) {
            UserAttendanceDataGroup dGroup = _groups.get(i);
            if (dGroup != null) {
                isUpdate |= dGroup.onUpdateTime(second);
            }
        }

        if (checkDayUpdate) {
            boolean isPcCafe = _userFlag.equals(AttendanceUserFlag.USER_FLAG_PC_CAFE);
            for (UserAttendanceDataGroup g : _groups) {
                isUpdate |= g.onUpdateDay(isPcCafe);
            }
        }

        if (isUpdate) {
        	ProtoWriter w = ProtoWriter.newInstance();
        	body(w);
        	byte[] body = w.bodyBytes();                                    // DB용
        	byte[] frame = w.finishWithTrailer(ProtoMsg.SC_ATTENDANCE_USER_DATA_EXTEND); // 전송용
        	updateAsync(pc.getAccountName(), body, createIndices());
            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, frame);               // 재직렬화 없이 전송
        }
    }


    public void onUpdateDay(L1PcInstance pc, int second) throws IOException {
        handleUpdateTime(pc, second, true);
    }

    public void onUpdateTime(L1PcInstance pc, int second) throws IOException {
        handleUpdateTime(pc, second, false);
    }

    // ────────────── indices ──────────────

    public String createIndices() {
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

    public static SC_AttendanceUserDataExtend newInstance() {
        return new SC_AttendanceUserDataExtend();
    }

    private ArrayList<UserAttendanceDataGroup> _groups;
    private AttendanceUserFlag _userFlag;
    private ArrayList<AttendanceGroupType> _active_group;

    private SC_AttendanceUserDataExtend() {}

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

    public void set_userFlag(AttendanceUserFlag val) {
        _userFlag = val;

        if (_groups == null) return;

        int pcCafeIndex = AttendanceUserFlag.USER_FLAG_PC_CAFE.toInt();
        if (_groups.size() <= pcCafeIndex) return;

        UserAttendanceDataGroup dGroup = _groups.get(pcCafeIndex);
        if (val.equals(AttendanceUserFlag.USER_FLAG_NORMAL)) {
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
    protected void body(ProtoWriter w) {
        if (_groups != null) {
            for (UserAttendanceDataGroup val : _groups) {
                w.msg(1);			// repeated group
                val.writeTo(w);		// 도메인 오브젝트가 스스로 직렬화
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
                set_userFlag(AttendanceUserFlag.fromInt(r.varint32()));
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

    public static boolean sendFields(CIOClient recipient, ArrayList<UserAttendanceDataGroup> _groups, AttendanceUserFlag _userFlag, ArrayList<AttendanceGroupType> _active_group) {
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

    private static boolean sendCtor(CIOClient protoRecipient) {
        ArrayList<UserAttendanceDataGroup> proto17_groups = null;
        AttendanceUserFlag proto17_userFlag = null;
        ArrayList<AttendanceGroupType> proto17_active_group = null;


        return sendFields(protoRecipient, proto17_groups, proto17_userFlag, proto17_active_group);
    }

    public static boolean sendNewInstance(CIOClient protoRecipient) {
        return sendCtor(protoRecipient);
    }

    public static boolean sendNewInstance(L1PcInstance protoRecipient) {
        return sendNewInstance(protoRecipient == null ? null : protoRecipient.getNetConnection());
    }
    public static boolean sendConstructed(CIOClient protoRecipient) { return sendCtor(protoRecipient); }

    public static boolean sendConstructed(L1PcInstance protoRecipient) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection()); }

}
