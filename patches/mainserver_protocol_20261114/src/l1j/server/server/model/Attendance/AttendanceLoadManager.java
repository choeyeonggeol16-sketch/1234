package l1j.server.server.model.Attendance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Calendar;

import l1j.server.DatabaseFactory;
import l1j.server.MJTemplate.MJPropertyReader;
import l1j.server.MJTemplate.Command.MJCommand;
import l1j.server.MJTemplate.Command.MJCommandArgs;
import l1j.server.MJTemplate.Exceptions.MJCommandArgsIndexException;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.packets.Protocol.Packet.SC_ATTENDANCE_BONUS_GROUP_INFO;
import l1j.server.server.packets.Protocol.Packet.SC_ATTENDANCE_BONUS_INFO_EXTEND;
import l1j.server.server.packets.Protocol.Packet.SC_ATTENDANCE_USER_DATA_EXTEND;
import l1j.server.server.packets.Protocol.Packet.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceDataGroup;
import l1j.server.server.packets.Protocol.Packet.Attend.UserAttendanceTimeStatus;
import l1j.server.server.utils.SQLUtil;

public class AttendanceLoadManager implements MJCommand{
	public static AttendanceLoadManager _instance;
	public static AttendanceLoadManager getInstance(){
		if(_instance == null)
			_instance = new AttendanceLoadManager();
		return _instance;
	}
	
	public static void loadAttendanceStartupCalendar(){
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try{
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from attendance_startup");
			rs = pstm.executeQuery();
			if(rs.next()){
				Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
				ATTEN_STARTUP_CALENDAR = (Calendar)cal.clone();
				ATTEN_STARTUP_CALENDAR.setTimeInMillis(rs.getTimestamp("startup_info").getTime());
				
				long diff = (cal.getTimeInMillis() - ATTEN_STARTUP_CALENDAR.getTimeInMillis()) / 1000; 
				if(diff > ATTEN_RESET_PERIOD_SECOND)
					updateServerStartupInfo();
				cal.clear();
			}else{
				updateServerStartupInfo();
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(rs, pstm, con);
		}
	}
	
	public static void updateServerStartupInfo(){
		Connection con			= null;
		PreparedStatement pstm 	= null;
		Calendar old = ATTEN_STARTUP_CALENDAR;
		ATTEN_STARTUP_CALENDAR = RealTimeClock.getInstance().getRealTimeCalendar();
		Timestamp ts = new Timestamp(ATTEN_STARTUP_CALENDAR.getTimeInMillis());
		try{
			con 	= DatabaseFactory.getInstance().getConnection();
			pstm 	= con.prepareStatement("insert into attendance_startup set id=1, startup_info=? on duplicate key update startup_info=?");
			pstm.setTimestamp(1, ts);
			pstm.setTimestamp(2, ts);
			pstm.execute();
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			SQLUtil.close(pstm, con);
		}
		if(old != null){
			old.clear();
			old = null;
		}
	}
	
	private AttendanceLoadManager(){
	}
	
	public static Calendar ATTEN_STARTUP_CALENDAR;
	public static int ATTEN_CHECK_INTERVAL;
	public static int ATTEN_RESET_PERIOD_SECOND;
	public static int ATTEN_DAILY_MAX_COUNT;
	public static int ATTEN_WEEKEND_MAX_COUNT;
	public static int ATTEN_TOTAL_TIMESECOND;
	public void load(){
		loadConfig();
		loadBonusGroup();
		loadAttendanceStartupCalendar();
		AttendanceScheduler.getInstance().run();
	}
	
	public void loadBonusGroup(){
		SC_ATTENDANCE_BONUS_GROUP_INFO.load();
	}
	
	public void loadConfig(){
		MJPropertyReader reader = new MJPropertyReader("./config/mj_attendance.properties");
		ATTEN_CHECK_INTERVAL = reader.readInt("checkInterval", "1800");
		ATTEN_RESET_PERIOD_SECOND = reader.readInt("resetPeriodSecond", "86400");
		ATTEN_DAILY_MAX_COUNT = reader.readInt("dailyMaxCount", "1");
		ATTEN_WEEKEND_MAX_COUNT = reader.readInt("weekendMaxCount", "1");
		ATTEN_TOTAL_TIMESECOND = reader.readInt("totalAttendanceTimeSecond", "3600");
		reader.dispose();
		SC_ATTENDANCE_BONUS_INFO_EXTEND.load();
	}

	@Override
	public void execute(MJCommandArgs args) {
		try{
			switch(args.nextInt()){
			case 1:
				execReload(args);
				break;
				
			case 2:
				execCharacterCommand(args);
				break;
			}
		}catch(Exception e){
			args.notify(".출석체크 [1.리로드][2.캐릭터설정]");
		}finally{
			args.dispose();
			args = null;
		}
	}
	
	private void execReload(MJCommandArgs args){
		try{
			switch(args.nextInt()){
			case 1:
				loadConfig();
				args.notify("출석체크 컨픽 리로드가 완료되었습니다.");
				break;
			case 2:
				loadBonusGroup();
				args.notify("출석체크 아이템테이블 리로드가 완료되었습니다.");
				break;
			}
		}catch(Exception e){
			args.notify(".출석체크 1");
			args.notify("[1. 컨픽][2. 아이템디비]");
		}
	}
	
	private L1PcInstance findPc(MJCommandArgs args) throws MJCommandArgsIndexException{
		String name = args.nextString();
		L1PcInstance pc = L1World.getInstance().findpc(name);
		if(pc == null)
			args.notify(String.format("%s님을 찾을 수 없습니다.", name));
		else if(pc.getAttendanceData() == null)
			args.notify(String.format("%s님의 출석체크 정보를 찾을 수 없습니다.", name));
		else
			return pc;
		return null;
	}
	
	private void execCharacterCommand(MJCommandArgs args){
		try{
			switch(args.nextInt()){
			case 1:{
				L1PcInstance pc = findPc(args);
				if(pc == null)
					return;
				
				SC_ATTENDANCE_USER_DATA_EXTEND userData = pc.getAttendanceData();
				for(UserAttendanceDataGroup dGroup : userData.get_groups()){
					args.notify(String.format("%s님의 [%s]정보 - 상태 : %s, 누적시간 : %d, 현재인덱스 : %d, 현재타입에 누적된 보상 : %d", 
							pc.getName(),
							dGroup.get_groupType().name(), 
							dGroup.get_resultCode().name(), 
							dGroup.get_attendanceTime(), 
							dGroup.get_currentAttendanceIndex(), 
							dGroup.get_recvedRewardCount())
					);
				}
				break;
			}
			
			case 2:{
				L1PcInstance pc = findPc(args);
				if(pc == null)
					return;
				
				SC_ATTENDANCE_USER_DATA_EXTEND userData = pc.getAttendanceData();
				for(UserAttendanceDataGroup dGroup : userData.get_groups()){
					int index = dGroup.get_currentAttendanceIndex();
					dGroup.get_groupData().get(index).set_time(0);
					dGroup.set_currentAttendanceIndex(Math.max(0, dGroup.get_currentAttendanceIndex() - 1));
					dGroup.set_attendanceTime(0);
					if(dGroup.get_groupType().equals(AttendanceGroupType.NORMAL)){
						dGroup.set_resultCode(UserAttendanceTimeStatus.ATTENDANCE_NORMAL);
					}else{
						dGroup.set_resultCode(pc.PC방_버프 ? UserAttendanceTimeStatus.ATTENDANCE_NORMAL : UserAttendanceTimeStatus.ATTENDANCE_CANT_BE_ACHIEVE_TIME);
					}
					SC_ATTENDANCE_BONUS_INFO_EXTEND.send(pc);
					SC_ATTENDANCE_BONUS_GROUP_INFO.send(pc);
					SC_ATTENDANCE_USER_DATA_EXTEND.send(pc);
				}
				break;
			}
			case 3:{
				args.notify("2초 후 모든 출석체크 정보가 초기화됩니다.");
				AttendanceScheduler.getInstance().dispose();
				ThreadPool.getInstance().scheduleRequired(new Runnable(){
					@Override
					public void run(){
						L1World.getInstance().getAllPlayerStream()
						.filter((L1PcInstance pc) -> pc != null && pc.getAttendanceData() != null)
						.forEach((L1PcInstance pc) ->{
							try{
								pc.setAttendanceData(null);
							}catch(Exception e){
								e.printStackTrace();
							}
						});
						
						Connection con = null;
						PreparedStatement pstm = null;
						try{
							con = DatabaseFactory.getInstance().getConnection();
							pstm = con.prepareStatement("truncate table attendance_startup");
							pstm.executeUpdate();
							SQLUtil.close(pstm);
							pstm = con.prepareStatement("truncate table attendance_userinfo");
							pstm.executeUpdate();
						}catch(Exception e){
							e.printStackTrace();
						}finally{
							SQLUtil.close(pstm, con);
						}
						try{
							load();
							L1World.getInstance().getAllPlayerStream()
							.filter((L1PcInstance pc) -> pc != null && !pc.is무인상점())
							.forEach((L1PcInstance pc) ->{
								try{
									SC_ATTENDANCE_BONUS_INFO_EXTEND.send(pc);
									SC_ATTENDANCE_BONUS_GROUP_INFO.send(pc);
									SC_ATTENDANCE_USER_DATA_EXTEND.send(pc);
								}catch(Exception e){
									e.printStackTrace();
								}
							});

						}catch(Exception e){}
					}
				}, 2000L);
				break;
			}
			}
		}catch(Exception e){
			args.notify(".출석체크 2");
			args.notify("[1.케릭터조회][2.누적시간초기화][3.전체초기화]");
		}
	}
}
