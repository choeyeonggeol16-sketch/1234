package l1j.server.server.packets.Protocol.MainServer.Proto.Attend;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;

/**
 * 출석 보상 그룹 (0x0224의 반복 요소)
 *  1 attendace_index int32 [required]
 *  2 bonuses         repeated AttendanceBonusInfo
 */
public class AttendanceBonusGroup {

    public static AttendanceBonusGroup newInstance(ResultSet rs, int index) throws SQLException{
        AttendanceBonusGroup group = new AttendanceBonusGroup();
        group._attendaceIndex = index;
        group.add_bonuses(AttendanceBonusInfo.newInstance(rs));
        return group;
    }

    private int _attendaceIndex;
    private LinkedList<AttendanceBonusInfo> _bonuses = new LinkedList<AttendanceBonusInfo>();

    public int get_attendaceIndex(){ return _attendaceIndex; }

    public LinkedList<AttendanceBonusInfo> get_bonuses(){ return _bonuses; }
    public void add_bonuses(AttendanceBonusInfo val){
        _bonuses.add(val);
    }

    /** 신규 직렬화 — 항상 방출 */
    public void writeTo(ProtoWriter w){
        w.int32(1, _attendaceIndex);
        for(AttendanceBonusInfo val : _bonuses){
            w.msg(2);
            val.writeTo(w);
            w.end();
        }
    }
}