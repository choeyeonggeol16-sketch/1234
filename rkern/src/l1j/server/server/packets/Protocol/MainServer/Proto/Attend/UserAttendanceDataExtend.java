package l1j.server.server.packets.Protocol.MainServer.Proto.Attend;

import java.io.IOException;

import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 출석 데이터 슬롯 (UserAttendanceDataGroup의 반복 요소)
 *  1 index int32 [required]  2 state enum [required]  3 time int32 [required]
 *
 * DB blob(attendance_data)이 바이너리 형식이라 fromBinary 역직렬화 유지.
 * 직렬화는 신규 엔진(writeTo(ProtoWriter))만 사용.
 */
public class UserAttendanceDataExtend {

	public static UserAttendanceDataExtend newInstance(){
		return new UserAttendanceDataExtend();
	}

	public static UserAttendanceDataExtend newInstance(int index, UserAttendanceState state, int time){
		UserAttendanceDataExtend data = new UserAttendanceDataExtend();
		data._index = index;
		data._state = state;
		data._time = time;
		return data;
	}

	private int _index;
	private UserAttendanceState _state;
	private int _time;

	private UserAttendanceDataExtend(){
	}

	public int get_index(){
		return _index;
	}
	public void set_index(int val){
		_index = val;
	}

	public UserAttendanceState get_state(){
		return _state;
	}
	public void set_state(UserAttendanceState val){
		_state = val;
	}

	public int get_time(){
		return _time;
	}
	public void set_time(int val){
		_time = val;
	}

	public boolean addTimeAndOnUpdate(int second){
		if(!_state.equals(UserAttendanceState.INIT))
			return false;

		int updateTime = _time + second;
		if(updateTime >= AttendanceLoadManager.settings().requirement()){
			updateTime = AttendanceLoadManager.settings().requirement();
			_state = UserAttendanceState.COMPLETE;
		}
		_time = updateTime;
		return !_state.equals(UserAttendanceState.INIT);
	}

	/** DB blob 역직렬화 — 바이너리 형식 (태그: 0x08 index, 0x10 state, 0x18 time) */
	public static UserAttendanceDataExtend fromBinary(ProtoInputStream input) throws IOException {
		UserAttendanceDataExtend data = new UserAttendanceDataExtend();
		while(!input.isAtEnd()){
			int tag = input.readTag();
			switch(tag){
			default: return data;
			case 0x00000008: data._index = input.readInt32(); break;
			case 0x00000010: data._state = UserAttendanceState.fromInt(input.readEnum()); break;
			case 0x00000018: data._time = input.readInt32(); break;
			}
		}
		return data;
	}

	/** 신규 직렬화 — 전 필드 required, 항상 방출 */
	public void writeTo(ProtoWriter w){
		w.int32(1, _index)
		.int32(2, _state.toInt())
		.int32(3, _time);
	}
}