package l1j.server.server.packets.Protocol.MainServer.Proto.Attend;

import java.io.IOException;
import java.util.ArrayList;

import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

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
 * DB blob(attendance_data)이 server 바이너리 형식 → fromBinary 역직렬화 유지.
 * 직렬화는 신규 엔진만. 게임로직(onUpdateDay/onUpdateTime) 1:1 유지.
 */
public class UserAttendanceDataGroup {	
    public static UserAttendanceDataGroup newInstance(L1PcInstance pc, AttendanceGroupType gType){
        UserAttendanceDataGroup dGroup = new UserAttendanceDataGroup();
        dGroup._groupType = gType;
        dGroup._attendanceTime = 0;
        dGroup._totalAttendanceTime = AttendanceLoadManager.settings().requirement();
        dGroup._recvedRewardCount = 0;
        dGroup._totalRewardCount = AttendanceLoadManager.settings().dailyMax();
        dGroup._currentAttendanceIndex = 0;
        int totalBonusCount = gType.getBonusSize();
        for(int i=0; i<totalBonusCount; ++i)
            dGroup.add_groupData(UserAttendanceDataExtend.newInstance(i+1, UserAttendanceState.INIT, 0));
        
        dGroup._resultCode = !gType.equals(AttendanceGroupType.PC_CAFE) 
                ? UserAttendanceTimeStatus.ATTENDANCE_NORMAL 
                : UserAttendanceTimeStatus.ATTENDANCE_CANT_BE_ACHIEVE_TIME;
        return dGroup;
    }
    
    private int	_currentAttendanceIndex;
    private AttendanceGroupType _groupType;
    private ArrayList<UserAttendanceDataExtend> _groupData = new ArrayList<UserAttendanceDataExtend>(48);
    private UserAttendanceTimeStatus _resultCode;
    private int _recvedRewardCount;
    private int _totalRewardCount;
    private int _attendanceTime;
    private int _totalAttendanceTime;
    
    // ────────────── 게임로직 (server 1:1) ──────────────

    public boolean onUpdateDay(boolean isPcCafe) {
        if (_resultCode == UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR
                || _resultCode == UserAttendanceTimeStatus.ATTENDANCE_ALL_COMPLETE) {
            return updateFinishedStatus();
        }

        _currentAttendanceIndex = Math.max(0, _currentAttendanceIndex);
        while (_currentAttendanceIndex < _groupData.size()
                && _groupData.get(_currentAttendanceIndex).get_state() != UserAttendanceState.INIT) {
            ++_currentAttendanceIndex;
        }
        if (_currentAttendanceIndex >= _groupData.size()) return updateFinishedStatus();

        _attendanceTime = 0;
        _totalAttendanceTime = AttendanceLoadManager.settings().requirement();
        _recvedRewardCount = 0;
        _totalRewardCount = AttendanceLoadManager.settings().dailyMax();
        _groupData.get(_currentAttendanceIndex).set_time(0);
        _resultCode = _groupType == AttendanceGroupType.PC_CAFE && !isPcCafe
                ? UserAttendanceTimeStatus.ATTENDANCE_CANT_BE_ACHIEVE_TIME
                : UserAttendanceTimeStatus.ATTENDANCE_NORMAL;
        return true;
    }

    public void applyCurrentSettings() {
        final var settings = AttendanceLoadManager.settings();
        _totalAttendanceTime = settings.requirement();
        if (_resultCode != UserAttendanceTimeStatus.ATTENDANCE_NORMAL
                && _resultCode != UserAttendanceTimeStatus.ATTENDANCE_CANT_BE_ACHIEVE_TIME) return;
        _totalRewardCount = settings.dailyMax();
        if (_recvedRewardCount >= _totalRewardCount)
            _resultCode = UserAttendanceTimeStatus.ATTENDANCE_NO_MORE_TIME;
    }

    public int secondsUntilCompletion() {
        final int threshold = AttendanceLoadManager.settings().requirement();
        if (_resultCode != UserAttendanceTimeStatus.ATTENDANCE_NORMAL || threshold <= 0
                || _totalRewardCount <= 0 || _currentAttendanceIndex < 0
                || _currentAttendanceIndex >= _groupData.size()
                || _groupData.get(_currentAttendanceIndex).get_state() != UserAttendanceState.INIT) return -1;
        return Math.max(1, threshold - _attendanceTime);
    }

    public boolean creditEligibleTime(int seconds) {
        if (seconds <= 0) return false;
        // Time captured before a PC cafe flag change still belongs to its original group.
        final boolean paused = _groupType == AttendanceGroupType.PC_CAFE
                && _resultCode == UserAttendanceTimeStatus.ATTENDANCE_CANT_BE_ACHIEVE_TIME;
        if (paused) _resultCode = UserAttendanceTimeStatus.ATTENDANCE_NORMAL;
        try {
            return onUpdateTime(seconds);
        } finally {
            if (paused && _resultCode == UserAttendanceTimeStatus.ATTENDANCE_NORMAL) {
                _resultCode = UserAttendanceTimeStatus.ATTENDANCE_CANT_BE_ACHIEVE_TIME;
            }
        }
    }

    public boolean onUpdateTime(int seconds) {
        if (secondsUntilCompletion() < 0 || seconds <= 0) return false;
        final int threshold = AttendanceLoadManager.settings().requirement();

        boolean completedAny = false;
        while (seconds > 0) {
            final UserAttendanceDataExtend slot = _groupData.get(_currentAttendanceIndex);
            if (slot.get_state() != UserAttendanceState.INIT) break;

            _attendanceTime = Math.clamp(_attendanceTime, 0, threshold);
            final int applied = Math.min(seconds, threshold - _attendanceTime);
            _attendanceTime += applied;
            seconds -= applied;
            slot.set_time(_attendanceTime);
            if (_attendanceTime < threshold) break;

            ++_recvedRewardCount;
            slot.set_state(UserAttendanceState.COMPLETE);
            completedAny = true;
            if (_currentAttendanceIndex + 1 == _groupData.size()) {
                _currentAttendanceIndex = _groupData.size();
                updateFinishedStatus();
                break;
            }
            if (_recvedRewardCount >= _totalRewardCount) {
                _resultCode = UserAttendanceTimeStatus.ATTENDANCE_NO_MORE_TIME;
                break;
            }
            ++_currentAttendanceIndex;
            _attendanceTime = 0;
        }
        return completedAny;
    }

    private boolean updateFinishedStatus() {
        UserAttendanceTimeStatus status = UserAttendanceTimeStatus.ATTENDANCE_ALL_CLEAR;
        for (UserAttendanceDataExtend slot : _groupData) {
            if (slot.get_state() != UserAttendanceState.CLEAR) {
                status = UserAttendanceTimeStatus.ATTENDANCE_ALL_COMPLETE;
                break;
            }
        }
        if (_resultCode == status) return false;
        _resultCode = status;
        return true;
    }

    // ────────────── getter/setter (호출부 유지) ──────────────

    public void set_currentAttendanceIndex(int index){
        _currentAttendanceIndex = index;
    }
    
    public int get_currentAttendanceIndex(){
        return _currentAttendanceIndex;
    }

    public AttendanceGroupType get_groupType(){
        return _groupType;
    }
    public void set_groupType(AttendanceGroupType val){
        _groupType = val;
    }

    public ArrayList<UserAttendanceDataExtend> get_groupData(){
        return _groupData;
    }
    public void add_groupData(UserAttendanceDataExtend val){
        _groupData.add(val);
    }

    public UserAttendanceTimeStatus get_resultCode(){
        return _resultCode;
    }
    public void set_resultCode(UserAttendanceTimeStatus val){
        _resultCode = val;
    }

    public int get_recvedRewardCount(){
        return _recvedRewardCount;
    }
    public void set_recvedRewardCount(int val){
        _recvedRewardCount = val;
    }

    public int get_totalRewardCount(){
        return _totalRewardCount;
    }
    public void set_totalRewardCount(int val){
        _totalRewardCount = val;
    }

    public int get_attendanceTime(){
        return _attendanceTime;
    }
    public void set_attendanceTime(int val){
        _attendanceTime = val;
    }

    public int get_totalAttendanceTime(){
        return _totalAttendanceTime;
    }
    public void set_totalAttendanceTime(int val){
        _totalAttendanceTime = val;
    }
    
    // ────────────── DB blob 역직렬화 ──────────────

    public static UserAttendanceDataGroup fromBinary(ProtoInputStream input) throws IOException {
        UserAttendanceDataGroup group = new UserAttendanceDataGroup();
        while(!input.isAtEnd()){
            int tag = input.readTag();
            switch(tag){
                default: return group;
                case 0x00000008: group._groupType = AttendanceGroupType.fromInt(input.readEnum()); break;
                case 0x00000012:{
                    byte[] sub = input.readBytes();
                    group.add_groupData(UserAttendanceDataExtend.fromBinary(ProtoInputStream.newInstance(sub)));
                    break;
                }
                case 0x00000018: group._resultCode = UserAttendanceTimeStatus.fromInt(input.readEnum()); break;
                case 0x00000020: group._recvedRewardCount = input.readInt32(); break;
                case 0x00000028: group._totalRewardCount = input.readInt32(); break;
                case 0x00000030: group._attendanceTime = input.readInt32(); break;
                case 0x00000038: group._totalAttendanceTime = input.readInt32(); break;
            }
        }
        return group;
    }
    
    public static UserAttendanceDataGroup parse(byte[] bytes){
        if(bytes == null || bytes.length == 0)
            return new UserAttendanceDataGroup();
        try{
            return fromBinary(ProtoInputStream.newInstance(bytes));
        }catch(IOException e){
            e.printStackTrace();
            return new UserAttendanceDataGroup();
        }
    }
    
    // ────────────── 신규 직렬화 — 전 필드 required, 항상 방출 ──────────────

    public void writeTo(ProtoWriter w){
        w.int32(1, _groupType.toInt());
        for(UserAttendanceDataExtend val : _groupData){
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