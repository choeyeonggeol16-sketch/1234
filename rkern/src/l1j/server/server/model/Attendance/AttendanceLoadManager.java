package l1j.server.server.model.Attendance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.gametime.CL2Time;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.AttendanceGroupType;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceDataGroup;
import l1j.server.server.packets.Protocol.MainServer.Proto.Attend.UserAttendanceTimeStatus;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_BONUS_GROUP_INFO;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_BONUS_INFO_EXTEND;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ATTENDANCE_USER_DATA_EXTEND;
import l1j.server.server.shared.PropertyReader;
import l1j.server.server.shared.command.Command;
import l1j.server.server.shared.command.CommandArgs;
import l1j.server.server.shared.exception.CommandArgsIndexException;
import l1j.server.server.utils.SQLUtil;

public class AttendanceLoadManager implements Command{
	private static final Logger LOG = Logger.getLogger(AttendanceLoadManager.class.getName());
	public static AttendanceLoadManager _instance;
	public static AttendanceLoadManager getInstance(){
		if(_instance == null)
			_instance = new AttendanceLoadManager();
		return _instance;
	}
	
	public static void loadAttendanceStartupCalendar() {
		final Timestamp stored;
		try (Connection con = DatabaseFactory.getInstance().getConnection();
				 PreparedStatement statement = con.prepareStatement(
						 "SELECT startup_info FROM attendance_startup WHERE id=1");
				 ResultSet rows = statement.executeQuery()) {
			stored = rows.next() ? rows.getTimestamp(1) : null;
		} catch (SQLException error) {
			throw new IllegalStateException("Attendance startup day read failed", error);
		}

		final Calendar current = CL2Time.getRealTimeCalendar();
		final long now = current.getTimeInMillis();
		if (stored != null) {
			current.setTimeInMillis(wholeSecond(stored.getTime()));
			ATTEN_STARTUP_CALENDAR = current;
			if (now - current.getTimeInMillis() < ATTEN_RESET_PERIOD_SECOND * 1000L) return;
		}
		updateServerStartupInfo(now);
	}

	static void updateServerStartupInfo(long dayMillis) {
		final Calendar day = CL2Time.getRealTimeCalendar();
		day.setTimeInMillis(wholeSecond(dayMillis));
		final Timestamp time = new Timestamp(day.getTimeInMillis());
		try (Connection con = DatabaseFactory.getInstance().getConnection();
				 PreparedStatement statement = con.prepareStatement(
						 "INSERT INTO attendance_startup (id, startup_info) VALUES (1, ?) "
								 + "ON DUPLICATE KEY UPDATE startup_info=VALUES(startup_info)")) {
			statement.setTimestamp(1, time);
			statement.executeUpdate();
		} catch (SQLException error) {
			throw new IllegalStateException("Attendance startup day save failed", error);
		}
		ATTEN_STARTUP_CALENDAR = day;
	}

	private static long wholeSecond(long millis) {
		return Math.floorDiv(millis, 1000L) * 1000L;
	}

	public static long attendanceDayMillis() {
		final Calendar day = ATTEN_STARTUP_CALENDAR;
		return day == null ? 0L : day.getTimeInMillis();
	}
	
	private AttendanceLoadManager(){
	}
	
	public static volatile Calendar ATTEN_STARTUP_CALENDAR;
	public static int ATTEN_CHECK_INTERVAL;
	public static int ATTEN_RESET_PERIOD_SECOND;
	public static int ATTEN_DAILY_MAX_COUNT;
	public static int ATTEN_WEEKEND_MAX_COUNT;
	public static int ATTEN_TOTAL_TIMESECOND;
	public void load(){
		loadConfig();
		loadBonusGroup();
		try {
			loadAttendanceStartupCalendar();
		} catch (IllegalStateException unavailable) {
			LOG.log(Level.WARNING, "Attendance startup day unavailable; scheduler will retry", unavailable);
		}
		AttendanceScheduler.getInstance().run();
	}
	
	public void loadBonusGroup(){
		SC_ATTENDANCE_BONUS_GROUP_INFO.load();
	}
	
	public void loadConfig(){
		PropertyReader reader = new PropertyReader("./config/mj_attendance.properties");
		ATTEN_CHECK_INTERVAL = reader.readInt("checkInterval", "1800");
		ATTEN_RESET_PERIOD_SECOND = reader.readInt("resetPeriodSecond", "86400");
		ATTEN_DAILY_MAX_COUNT = reader.readInt("dailyMaxCount", "1");
		ATTEN_WEEKEND_MAX_COUNT = reader.readInt("weekendMaxCount", "1");
		ATTEN_TOTAL_TIMESECOND = reader.readInt("totalAttendanceTimeSecond", "3600");
		reader.dispose();
		SC_ATTENDANCE_BONUS_INFO_EXTEND.load();
	}

	@Override
	public void execute(CommandArgs args) {
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
	
	private void execReload(CommandArgs args){
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
	
	private L1PcInstance findPc(CommandArgs args) throws CommandArgsIndexException{
		String name = args.nextString();
		L1PcInstance pc = CWorld.getInstance().getPlayer(name);
		if(pc == null)
			args.notify(String.format("%s님을 찾을 수 없습니다.", name));
		else if(pc.getAttendanceData() == null)
			args.notify(String.format("%s님의 출석체크 정보를 찾을 수 없습니다.", name));
		else
			return pc;
		return null;
	}
	
	private void execCharacterCommand(CommandArgs args){
		try{
			switch(args.nextInt()){
			case 1:{
				L1PcInstance pc = findPc(args);
				if(pc == null)
					return;
				
				SC_ATTENDANCE_USER_DATA_EXTEND userData = pc.getAttendanceData();
				if (userData == null) return;
				final ArrayList<String> summary = new ArrayList<>();
				synchronized (userData) {
					if (pc.getAttendanceData() != userData || userData.get_groups() == null) return;
					for (UserAttendanceDataGroup group : userData.get_groups()) {
						if (group == null) continue;
						summary.add(String.format("%s님의 [%s]정보 - 상태 : %s, 누적시간 : %d, 현재인덱스 : %d, 현재타입에 누적된 보상 : %d",
								pc.getName(), group.get_groupType(), group.get_resultCode(), group.get_attendanceTime(),
								group.get_currentAttendanceIndex(), group.get_recvedRewardCount()));
					}
				}
				for (String line : summary) args.notify(line);
				break;
			}
			
			case 2:{
				L1PcInstance pc = findPc(args);
				if(pc == null)
					return;
				
				SC_ATTENDANCE_USER_DATA_EXTEND userData = pc.getAttendanceData();
				if (userData == null) return;
				boolean reset = false;
				synchronized (userData) {
					if (pc.getAttendanceData() != userData || userData.get_groups() == null) return;
					for (UserAttendanceDataGroup group : userData.get_groups()) {
						if (group == null) continue;
						final int index = group.get_currentAttendanceIndex();
						if (index < 0 || index >= group.get_groupData().size()) continue;
						group.get_groupData().get(index).set_time(0);
						group.set_currentAttendanceIndex(Math.max(0, index - 1));
						group.set_attendanceTime(0);
						group.set_resultCode(group.get_groupType() == AttendanceGroupType.NORMAL || pc.PC방_버프
								? UserAttendanceTimeStatus.ATTENDANCE_NORMAL
								: UserAttendanceTimeStatus.ATTENDANCE_CANT_BE_ACHIEVE_TIME);
						reset = true;
					}
				}
				if (reset) {
					SC_ATTENDANCE_BONUS_INFO_EXTEND.send(pc);
					SC_ATTENDANCE_BONUS_GROUP_INFO.send(pc);
					SC_ATTENDANCE_USER_DATA_EXTEND.send(pc);
				}
				break;
			}
			case 3:{
				args.notify("2초 후 모든 출석체크 정보가 초기화됩니다.");
				AttendanceScheduler.getInstance().dispose();
				CWorldWork.scheduleRequired(new Runnable(){
					@Override
					public void run(){
						CWorld.getInstance().getAllPlayerStream()
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
							CWorld.getInstance().getAllPlayerStream()
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
