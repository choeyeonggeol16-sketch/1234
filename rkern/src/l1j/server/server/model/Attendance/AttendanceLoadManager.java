package l1j.server.server.model.Attendance;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.gametime.CGameTime;
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

public class AttendanceLoadManager implements Command{
	private static final Logger LOG = Logger.getLogger(AttendanceLoadManager.class.getName());
	public static AttendanceLoadManager _instance;
	public static AttendanceLoadManager getInstance(){
		if(_instance == null)
			_instance = new AttendanceLoadManager();
		return _instance;
	}
	
    private static final int RESET_OWNER = CacheD.accountOrderKey("$attendance-reset");
    private static final int RESET_AUXILIARY = CacheD.accountOrderKey("$attendance-reset-calendar");
    private static volatile long generation;
    private static volatile boolean resetting;

    public static boolean isResetting() { return resetting; }
    public static long generation() { return generation; }
    public static boolean accepts(long expected) {
        return expected != 0L && !resetting && generation == expected;
    }

    public static void loadAttendanceStartupCalendar() {
        final long expected;
        final long stored;
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            CTransaction.requireTransactionalTables(con, "attendance_startup", "attendance_userinfo");
            AttendanceEpoch.ensure(con);
            con.setAutoCommit(false);
            expected = AttendanceEpoch.lockShared(con);
            stored = AttendanceEpoch.readDay(con);
            con.commit();
        } catch (SQLException failure) {
            throw new IllegalStateException("Attendance startup data load failed", failure);
        }
        synchronized (AttendanceLoadManager.class) {
            if (resetting || (generation != 0L && generation != expected)) return;
            generation = expected;
            if (stored != 0L) publishDay(stored);
        }
        final long now = System.currentTimeMillis();
        if (stored == 0L || now - stored >= settings.resetSeconds() * 1000L) {
            updateServerStartupInfo(now, expected);
        }
    }

    static boolean updateServerStartupInfo(long dayMillis, long expected) {
        if (!accepts(expected)) return false;
        try (Connection con = DatabaseFactory.getInstance().getConnection()) {
            con.setAutoCommit(false);
            try {
                if (AttendanceEpoch.lockShared(con) != expected) {
                    con.rollback();
                    return false;
                }
                AttendanceEpoch.writeDay(con, wholeSecond(dayMillis));
                con.commit();
            } catch (SQLException | RuntimeException failure) {
                try { con.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Attendance startup day save failed", failure);
        }
        synchronized (AttendanceLoadManager.class) {
            if (!accepts(expected)) return false;
            publishDay(dayMillis);
        }
        return true;
    }

    private static void publishDay(long millis) {
        final Calendar day = CGameTime.getRealTimeCalendar();
        day.setTimeInMillis(wholeSecond(millis));
        ATTEN_STARTUP_CALENDAR = day;
    }

    public static synchronized boolean requestReset(Consumer<Boolean> completion) {
        final long before = generation;
        if (!accepts(before)) return false;
        final long after = before + 1000L;
        final long day = wholeSecond(System.currentTimeMillis());
        final CWorld world = CWorld.getInstance();
        resetting = true;
        try (var move = CacheD.beginEconomyMove(RESET_OWNER, RESET_AUXILIARY)) {
            move.stageWarehouseSql(con -> AttendanceEpoch.stageReset(con, before, after, day));
            move.onError(failure -> {
                if (!(failure instanceof CTransaction.OutcomeUnknownException)
                        && !(failure instanceof CacheD.CommittedReplayException)) {
                    resetting = false;
                    CWorldWork.tryExecute(AttendanceScheduler.getInstance()::refreshConfiguration);
                }
                LOG.log(Level.WARNING, "Attendance reset failed", failure);
                completion.accept(false);
            });
            move.commit(() -> CIOThreadCallback.getInstance().completeWorldMutation(world,
                    () -> publishReset(world, after, day), failure -> move.finishOwnerApply(failure,
                            outcome -> completion.accept(outcome == null))));
            return true;
        } catch (RuntimeException failure) {
            resetting = false;
            LOG.log(Level.WARNING, "Attendance reset admission failed", failure);
            return false;
        }
    }

    private static void publishReset(CWorld world, long after, long day) {
        final AttendanceScheduler scheduler = AttendanceScheduler.getInstance();
        scheduler.dispose();
        world.getAllPlayerStream().forEach(pc -> pc.setAttendanceData(null));
        synchronized (AttendanceLoadManager.class) {
            generation = after;
            publishDay(day);
            if (Config.USE_ATTENDANCE_SYSTEM) scheduler.run();
            resetting = false;
        }
        if (!Config.USE_ATTENDANCE_SYSTEM) return;
        world.getAllPlayerStream().filter(pc -> !pc.is무인상점() && !pc.isLogoutStarted() && pc.getLevel() >= 50)
                .forEach(pc -> {
                    SC_ATTENDANCE_BONUS_INFO_EXTEND.send(pc);
                    SC_ATTENDANCE_BONUS_GROUP_INFO.send(pc);
                    SC_ATTENDANCE_USER_DATA_EXTEND.send(pc);
                });
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
    public record Settings(int checkInterval, int resetSeconds, int dailyMax, int weekendMax, int requirement) {
        public Settings {
            if (checkInterval <= 0 || resetSeconds <= 0 || dailyMax < 0 || weekendMax < 0 || requirement <= 0)
                throw new IllegalArgumentException("Invalid attendance settings");
        }
    }

    private static volatile Settings settings = new Settings(1800, 86400, 1, 1, 3600);
    public static Settings settings() { return settings; }
    static void publishSettings(Settings replacement) { settings = replacement; }

	public void load(){
		publishSettings(readConfig());
		SC_ATTENDANCE_BONUS_INFO_EXTEND.load();
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
	
    private Settings readConfig() {
        PropertyReader reader = new PropertyReader("./config/mj_attendance.properties");
        try {
            return new Settings(reader.readInt("checkInterval", "1800"), reader.readInt("resetPeriodSecond", "86400"),
                    reader.readInt("dailyMaxCount", "1"), reader.readInt("weekendMaxCount", "1"),
                    reader.readInt("totalAttendanceTimeSecond", "3600"));
        } finally {
            reader.dispose();
        }
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
                final Settings replacement = readConfig();
                final L1PcInstance gm = args.getOwner();
                if (!CWorldWork.tryExecute(() -> {
                    if (AttendanceScheduler.getInstance().reconfigure(replacement)) {
                        SC_ATTENDANCE_BONUS_INFO_EXTEND.load();
                        gm.sendPackets("출석체크 컨픽 리로드가 완료되었습니다.");
                    } else {
                        gm.sendPackets("출석체크 설정을 적용하지 못했습니다. 잠시 후 다시 시도해 주세요.");
                    }
                })) args.notify("출석체크 설정 리로드를 접수할 수 없습니다.");
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
            case 3: {
                final L1PcInstance gm = args.getOwner();
                if (!requestReset(success -> gm.sendPackets(success
                        ? "모든 출석체크 정보를 초기화했습니다." : "출석체크 초기화 처리에 실패했습니다."))) {
                    args.notify("출석체크 초기화를 접수할 수 없습니다.");
                }
                break;
            }
			}
		}catch(Exception e){
			args.notify(".출석체크 2");
			args.notify("[1.케릭터조회][2.누적시간초기화][3.전체초기화]");
		}
	}
}
