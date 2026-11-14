package LIN;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import l1j.server.server.packets.Protocol.ProtoWriter;



/**
 * 출석 보상 그룹 (0x0224의 반복 요소)
 *  1 attendace_index int32 [required]
 *  2 bonuses         repeated AttendanceBonusInfo
 */
public class AttendanceBonusGroup {

    public static LIN.AttendanceBonusGroup newInstance(ResultSet rs, int index) throws SQLException{
        LIN.AttendanceBonusGroup group = new LIN.AttendanceBonusGroup();
        group._attendaceIndex = index;
        group.add_bonuses(LIN.AttendanceBonusInfo.newInstance(rs));
        return group;
    }

    private int _attendaceIndex;
    private LinkedList<LIN.AttendanceBonusInfo> _bonuses = new LinkedList<LIN.AttendanceBonusInfo>();

    public int attendaceIndex(){ return _attendaceIndex; }

    public LinkedList<LIN.AttendanceBonusInfo> bonuses(){ return _bonuses; }
    public void add_bonuses(LIN.AttendanceBonusInfo val){
        _bonuses.add(val);
    }

    /** 신규 직렬화 — 항상 방출 */
    public void writeTo(ProtoWriter w){
        w.int32(1, _attendaceIndex);
        for(LIN.AttendanceBonusInfo val : _bonuses){
            w.msg(2);
            val.writeTo(w);
            w.end();
        }
    }
}