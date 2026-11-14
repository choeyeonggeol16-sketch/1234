package LIN;

import java.io.IOException;
import java.util.ArrayList;
import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Stream.ProtoInputStream;



/**
 * 출석 데이터 그룹 (SC_ATTENDANCE_USER_DATA_EXTEND의 반복 요소)
 *  1 group_type enum [required]
 *  2 group_data repeated UserAttendanceDataExtend
 *  3 result_code enum [required]
 *  4 recved_reward_count int32 [required]
 *  5 total_reward_count int32 [required]
 *  6 attendance_time int32 [required]
 *  7 total_attendance_time int32 [required]
 *
 * DB blob(attendance_data)이 MJ 바이너리 형식 → fromBinary 역직렬화 유지.
 * 직렬화는 신규 엔진만. 게임로직(onUpdateDay/onUpdateTime) 1:1 유지.
 */
public class UserAttendanceDataGroup {	
    public static LIN.UserAttendanceDataGroup newInstance(L1PcInstance pc, LIN.AttendanceGroupType gType){
        LIN.UserAttendanceDataGroup dGroup = new LIN.UserAttendanceDataGroup();
        dGroup._groupType = gType;
        dGroup._attendanceTime = 0;
        dGroup._totalAttendanceTime = AttendanceLoadManager.ATTEN_TOTAL_TIMESECOND;
        dGroup._recvedRewardCount = 0;
        dGroup._totalRewardCount = AttendanceLoadManager.ATTEN_DAILY_MAX_COUNT;
        dGroup._currentAttendanceIndex = 0;
        int totalBonusCount = gType.getBonusSize();
        for(int i=0; i<totalBonusCount; ++i)
            dGroup.add_groupData(LIN.UserAttendanceDataExtend.newInstance(i+1, LIN.UserAttendanceState.INIT, 0));
        
        dGroup._resultCode = !gType.equals(LIN.AttendanceGroupType.PC_CAFE) 
                ? LIN.UserAttendanceTimeStatus.ATTENDANCE_NORMAL 
                : LIN.UserAttendanceTimeStatus.ATTENDANCE_CANT_BE_ACHIEVE_TIME;
        return dGroup;
    }
    
    private int	_currentAttendanceIndex;
    private LIN.AttendanceGroupType _groupType;
    private ArrayList<LIN.UserAttendanceDataExtend> _groupData = new ArrayList<LIN.UserAttendanceDataExtend>(48);
    private LIN.UserAttendanceTimeStatus _resultCode;
    private int _recvedRewardCount;
    private int _totalRewardCount;
    private int _attendanceTime;
    private int _totalAttendanceTime;
    
    // ────────────── 게임로직 (MJ 1:1) ──────────────

    public boolean onUpdateDay(boolean isPcCafe){
        if(_resultCode.equals(LIN.UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR) || _resultCode.equals(LIN.UserAttendanceTimeStatus.ATTENDANCE_ALL_COMPLETE))
            return false;
        
        if(_currentAttendanceIndex > _groupData.size() - 1){
            int clearcount = 0;
            for(LIN.UserAttendanceDataExtend gData : _groupData){
                if(gData.state().equals(LIN.UserAttendanceState.CLEAR))
                    ++clearcount;
            }
            _resultCode = clearcount >= _groupData.size() ? LIN.UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR : LIN.UserAttendanceTimeStatus.ATTENDANCE_ALL_COMPLETE;
        }else{
            LIN.UserAttendanceDataExtend gData = _groupData.get(_currentAttendanceIndex);
            _attendanceTime = 0;
            _totalAttendanceTime = AttendanceLoadManager.ATTEN_TOTAL_TIMESECOND;
            _recvedRewardCount = 0;
            _totalRewardCount = AttendanceLoadManager.ATTEN_DAILY_MAX_COUNT;
            if(gData.state().equals(LIN.UserAttendanceState.INIT)){
                gData.set_time(0);
            }else{
                ++_currentAttendanceIndex;
            }
            if(_groupType.equals(LIN.AttendanceGroupType.PC_CAFE) && !isPcCafe)
                _resultCode = LIN.UserAttendanceTimeStatus.ATTENDANCE_CANT_BE_ACHIEVE_TIME;
            else
                _resultCode = LIN.UserAttendanceTimeStatus.ATTENDANCE_NORMAL;
        }
        return true;
    }
    
    public boolean onUpdateTime(int second){
        if(!_resultCode.equals(LIN.UserAttendanceTimeStatus.ATTENDANCE_NORMAL))
            return false;
        
        if(_currentAttendanceIndex > _groupData.size() - 1){
            return true;
        }
        
        LIN.UserAttendanceDataExtend attendance_data = _groupData.get(_currentAttendanceIndex);
        if(!attendance_data.state().equals(LIN.UserAttendanceState.INIT))
            return false;
        
        boolean isUpdate = false;
        int updateTime = _attendanceTime + second;
        if(updateTime >= AttendanceLoadManager.ATTEN_TOTAL_TIMESECOND){
            updateTime = AttendanceLoadManager.ATTEN_TOTAL_TIMESECOND;
            _recvedRewardCount = _recvedRewardCount + 1;
            attendance_data.set_state(LIN.UserAttendanceState.COMPLETE);
            if(_recvedRewardCount >= _totalRewardCount){
                _resultCode = LIN.UserAttendanceTimeStatus.ATTENDANCE_NO_MORE_TIME;
            }else{					
                if(++_currentAttendanceIndex > _groupData.size() - 1)
                    _resultCode = LIN.UserAttendanceTimeStatus.ATTENDANCE_ALL_COMPLETE;
                attendance_data.set_time(updateTime);
                _attendanceTime = 0;
                return true;
            }
            isUpdate = true;
        }
        attendance_data.set_time(updateTime);
        _attendanceTime = updateTime;
        return isUpdate;
    }
    
    // ────────────── getter/setter (호출부 유지) ──────────────

    public void set_currentAttendanceIndex(int index){
        _currentAttendanceIndex = index;
    }
    
    public int get_currentAttendanceIndex(){
        return _currentAttendanceIndex;
    }

    public LIN.AttendanceGroupType groupType(){
        return _groupType;
    }
    public void set_groupType(LIN.AttendanceGroupType val){
        _groupType = val;
    }

    public ArrayList<LIN.UserAttendanceDataExtend> groupData(){
        return _groupData;
    }
    public void add_groupData(LIN.UserAttendanceDataExtend val){
        _groupData.add(val);
    }

    public LIN.UserAttendanceTimeStatus resultCode(){
        return _resultCode;
    }
    public void set_resultCode(LIN.UserAttendanceTimeStatus val){
        _resultCode = val;
    }

    public int recvedRewardCount(){
        return _recvedRewardCount;
    }
    public void set_recvedRewardCount(int val){
        _recvedRewardCount = val;
    }

    public int totalRewardCount(){
        return _totalRewardCount;
    }
    public void set_totalRewardCount(int val){
        _totalRewardCount = val;
    }

    public int attendanceTime(){
        return _attendanceTime;
    }
    public void set_attendanceTime(int val){
        _attendanceTime = val;
    }

    public int totalAttendanceTime(){
        return _totalAttendanceTime;
    }
    public void set_totalAttendanceTime(int val){
        _totalAttendanceTime = val;
    }
    
    // ────────────── DB blob 역직렬화 ──────────────

    public static LIN.UserAttendanceDataGroup fromBinary(ProtoInputStream input) throws IOException {
        LIN.UserAttendanceDataGroup group = new LIN.UserAttendanceDataGroup();
        while(!input.isAtEnd()){
            int tag = input.readTag();
            switch(tag){
                default: return group;
                case 0x00000008: group._groupType = LIN.AttendanceGroupType.fromInt(input.readEnum()); break;
                case 0x00000012:{
                    byte[] sub = input.readBytes();
                    group.add_groupData(LIN.UserAttendanceDataExtend.fromBinary(ProtoInputStream.newInstance(sub)));
                    break;
                }
                case 0x00000018: group._resultCode = LIN.UserAttendanceTimeStatus.fromInt(input.readEnum()); break;
                case 0x00000020: group._recvedRewardCount = input.readInt32(); break;
                case 0x00000028: group._totalRewardCount = input.readInt32(); break;
                case 0x00000030: group._attendanceTime = input.readInt32(); break;
                case 0x00000038: group._totalAttendanceTime = input.readInt32(); break;
            }
        }
        return group;
    }
    
    public static LIN.UserAttendanceDataGroup parse(byte[] bytes){
        if(bytes == null || bytes.length == 0)
            return new LIN.UserAttendanceDataGroup();
        try{
            return fromBinary(ProtoInputStream.newInstance(bytes));
        }catch(IOException e){
            e.printStackTrace();
            return new LIN.UserAttendanceDataGroup();
        }
    }
    
    // ────────────── 신규 직렬화 — 전 필드 required, 항상 방출 ──────────────

    public void writeTo(ProtoWriter w){
        w.int32(1, _groupType.toInt());
        for(LIN.UserAttendanceDataExtend val : _groupData){
            w.msg(2);
            val.writeTo(w);
            w.end();
        }
        w.int32(3, _resultCode.toInt());
        w.int32(4, _recvedRewardCount);
        w.int32(5, _totalRewardCount);
        w.int32(6, _attendanceTime);
        w.int32(7, _totalAttendanceTime);
    }
}