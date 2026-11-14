package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Packet.Attend.AttendanceBonusGroup;
import l1j.server.server.packets.Protocol.Packet.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.ProtoMsg;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.utils.SQLUtil;

/**
 * SC_ATTENDANCE_BONUS_GROUP_INFO (0x0224) — 출석 보상 그룹 정보
 *  1 group_type enum [required]
 *  2 bonus_group repeated AttendanceBonusGroup (도메인 오브젝트, DB 로드)
 *
 * NORMAL/PC_CAFE 두 그룹 — 서버 시작 시 DB 로드 후 인코딩 1회 캐시, 전원 Raw 공유
 */
public final class SC_ATTENDANCE_BONUS_GROUP_INFO extends ProtoPacket {

    private final AttendanceGroupType _groupType;
    private final ArrayList<AttendanceBonusGroup> _bonusGroup;

    private SC_ATTENDANCE_BONUS_GROUP_INFO(AttendanceGroupType groupType, ArrayList<AttendanceBonusGroup> bonusGroup){
        _groupType = groupType;
        _bonusGroup = bonusGroup;
    }

    /** — 두 그룹 모두 전송 */
    public static void send(L1PcInstance pc){
        AttendanceGroupType.NORMAL.send(pc);
        AttendanceGroupType.PC_CAFE.send(pc);
    }

    /**  DB에서 그룹 로드 → AttendanceGroupType에 캐시 */
    public static void load(){
        SC_ATTENDANCE_BONUS_GROUP_INFO gInfo = new SC_ATTENDANCE_BONUS_GROUP_INFO(
                AttendanceGroupType.NORMAL, new ArrayList<AttendanceBonusGroup>(32));
        createAttendanceBonusGroup(gInfo, "attendance_item");
        AttendanceGroupType.NORMAL.setGroupInfo(gInfo);

        gInfo = new SC_ATTENDANCE_BONUS_GROUP_INFO(
                AttendanceGroupType.PC_CAFE, new ArrayList<AttendanceBonusGroup>(32));
        createAttendanceBonusGroup(gInfo, "attendance_item_pc");
        AttendanceGroupType.PC_CAFE.setGroupInfo(gInfo);
    }

    private static void createAttendanceBonusGroup(SC_ATTENDANCE_BONUS_GROUP_INFO gInfo, String table){
        Connection con = null;
        PreparedStatement pstm = null;
        ResultSet rs = null;
        try{
            con = DatabaseFactory.getInstance().getConnection();
            pstm = con.prepareStatement(String.format("SELECT * FROM %s ORDER BY id ASC", table));
            rs = pstm.executeQuery();
            int idx = 0;
            while(rs.next())
                gInfo.add_bonusGroup(AttendanceBonusGroup.newInstance(rs, ++idx));
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            SQLUtil.close(rs, pstm, con);
        }
    }

    // ────────────── setter/getter (AttendanceGroupType이 참조) ──────────────

    public AttendanceGroupType get_groupType(){ return _groupType; }

    public ArrayList<AttendanceBonusGroup> get_bonusGroup(){ return _bonusGroup; }
    public void add_bonusGroup(AttendanceBonusGroup val){
        _bonusGroup.add(val);
    }

    // ────────────── 직렬화 ──────────────

    @Override
    public int msgId() { return ProtoMsg.SC_ATTENDANCE_BONUS_GROUP_INFO; }

    @Override
    protected void body(ProtoWriter w) {
        w.int32(1, _groupType.toInt());
        for(AttendanceBonusGroup val : _bonusGroup){
            w.msg(2);
            val.writeTo(w);      // 도메인 오브젝트가 스스로 직렬화
            w.end();
        }
    }


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, AttendanceGroupType _groupType, ArrayList<AttendanceBonusGroup> _bonusGroup) {
        w.int32(1, _groupType.toInt());
            for (AttendanceBonusGroup val : _bonusGroup) {
                w.msg(2);
                val.writeTo(w);
                w.end();
            }
    }

    public static boolean sendFields(L1PcInstance recipient, AttendanceGroupType _groupType, ArrayList<AttendanceBonusGroup> _bonusGroup) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _groupType, _bonusGroup);
    }

    public static boolean sendFields(CIOClient recipient, AttendanceGroupType _groupType, ArrayList<AttendanceBonusGroup> _bonusGroup) {
        final ProtoWriter writer = Proto.begin(recipient, ProtoMsg.SC_ATTENDANCE_BONUS_GROUP_INFO, Proto.capacityBound(65535L));
        if (writer == null) return false;
        try {
            writeFields(writer, _groupType, _bonusGroup);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(AttendanceGroupType _groupType, ArrayList<AttendanceBonusGroup> _bonusGroup) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _groupType, _bonusGroup);
        return writer.finishWithTrailer(ProtoMsg.SC_ATTENDANCE_BONUS_GROUP_INFO);
    }

    private static boolean sendCtor(CIOClient protoRecipient, AttendanceGroupType groupType, ArrayList<AttendanceBonusGroup> bonusGroup) {
        AttendanceGroupType proto13_groupType = null;
        ArrayList<AttendanceBonusGroup> proto13_bonusGroup = null;

            proto13_groupType = groupType;
            proto13_bonusGroup = bonusGroup;

        return sendFields(protoRecipient, proto13_groupType, proto13_bonusGroup);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, AttendanceGroupType groupType, ArrayList<AttendanceBonusGroup> bonusGroup) { return sendCtor(protoRecipient, groupType, bonusGroup); }

    public static boolean sendConstructed(L1PcInstance protoRecipient, AttendanceGroupType groupType, ArrayList<AttendanceBonusGroup> bonusGroup) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), groupType, bonusGroup); }

}
