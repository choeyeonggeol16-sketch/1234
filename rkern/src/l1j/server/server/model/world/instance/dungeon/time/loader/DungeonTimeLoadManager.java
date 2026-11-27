package l1j.server.server.model.world.instance.dungeon.time.loader;

import java.util.Calendar;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.world.instance.dungeon.time.updator.DungeonTimeUpdator;
import l1j.server.server.shared.command.Command;
import l1j.server.server.shared.command.CommandArgs;
import l1j.server.server.shared.command.CommandTree;

public class DungeonTimeLoadManager implements Command{
	private static DungeonTimeLoadManager _instance;
	public static DungeonTimeLoadManager getInstance(){
		if(_instance == null)
			_instance = new DungeonTimeLoadManager();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		DungeonTimeLoadManager old = _instance;
		_instance = new DungeonTimeLoadManager();
		if(old != null){
			old.dispose();
			old = null;
		}
	}

	private CommandTree _commands;
	private DungeonTimeLoadManager(){
		_commands = createCommand();
	}
	
	public void load() {
		Calendar now = CGameTime.getRealTimeCalendar();
		int hour = Config.DungeonResetHour, minute = Config.DungeonResetMinute;
		if (hour >= 0 && hour < 24 && minute >= 0 && minute < 60) {
			long seconds = Math.floorDiv(now.getTimeInMillis(), 1000L);
			now.setTimeInMillis(DungeonTimeUpdator.resetBoundary(seconds, hour, minute) * 1000L);
			DungeonTimeProgressLoader.resetAll(now);
		}
		DungeonTimeInformationLoader.getInstance();
		DungeonTimeUpdator.getInstance().run();
		DungeonTimePotionLoader.getInstance();
	}

	public void dispose(){
	}

	@Override
	public void execute(CommandArgs args) {
		_commands.execute(args, new StringBuilder(256).append(_commands.to_operation()));
	}
	
	private CommandTree createCommand(){
		return new CommandTree(".던전타이머", "던전타이머 관련 명령을 수행합니다.", null)
				.add_command(createReloadCommand())
				.add_command(createInitializeCommand())
				.add_command(createSchedulerCommand());
	}
	
	private CommandTree createReloadCommand(){
		return new CommandTree("리로드", "리로드 관련 명령을 수행합니다.", null)
				.add_command(new CommandTree("정보", "", null){
					@Override
					protected void to_handle_command(CommandArgs args) throws Exception{
						DungeonTimeInformationLoader.reload();
						args.notify("tb_dungeon_time_information 테이블을 리로드 하였습니다.");
					}
				})
				.add_command(new CommandTree("포션", "", null){
					@Override
					protected void to_handle_command(CommandArgs args) throws Exception{
						DungeonTimePotionLoader.reload();
						args.notify("tb_dungeon_time_potion 테이블을 리로드 하였습니다.");
					}
				});
	}
	
	private CommandTree createInitializeCommand(){
		return new CommandTree("초기화", "초기화 관련 명령을 수행합니다.", null)
				.add_command(new CommandTree("캐릭터", "지정된 캐릭터의 던전시간을 초기화합니다.", new String[]{"캐릭터이름"}){
					@Override
					protected void to_handle_command(CommandArgs args) throws Exception{
						String character_name = args.nextString();
						L1PcInstance pc = CWorld.getInstance().getPlayer(character_name);
						if(pc == null){
							args.notify(String.format("캐릭터 이름 %s을(를) 찾을 수 없습니다.", character_name));
							return;
						}
						DungeonTimeProgressLoader.delete(pc);
						pc.initialize_dungeon_progress();
						pc.sendPackets("모든 던전 시간이 초기화 되었습니다.");
						pc.sendPackets("[NOTICE] All dungeon timers have been reset."); // [19fix-20 영문 병기]
						args.notify(String.format("%s의 모든 던전 시간을 초기화 했습니다.", character_name));
					}
				})
				.add_command(new CommandTree("전체초기화", "전체 케릭터의 던전시간을 초기화합니다.", null){
					@Override
					protected void to_handle_command(CommandArgs args) throws Exception{
						boolean accepted = DungeonTimeUpdator.getInstance().do_initialize(CGameTime.getRealTimeCalendar());
						args.notify(accepted ? "전체 던전 초기화 요청을 접수했습니다."
								: "초기화 요청을 접수하지 못했습니다. 잠시 후 다시 시도하십시오.");
					}
				});
	}
	
	private CommandTree createSchedulerCommand(){
		return new CommandTree("스케줄", "스케줄 관련 명령을 수행합니다.", null)
				.add_command(new CommandTree("상태", "현재 스케줄의 상태를 나타냅니다.", null){
					@Override
					protected void to_handle_command(CommandArgs args) throws Exception{
						args.notify(String.format("현재 스케줄링 상태 : %s", DungeonTimeUpdator.getInstance().is_running() ? "켜짐" : "꺼짐"));
					}
				})
				.add_command(new CommandTree("재시작", "스케줄을 재시작합니다.", null){
					@Override
					protected void to_handle_command(CommandArgs args) throws Exception{
						DungeonTimeUpdator.getInstance().dispose();
						Thread.sleep(500L);
						DungeonTimeUpdator.getInstance().run();
						args.notify("스케줄링을 재시작 했습니다.");
					}
				})
				.add_command(new CommandTree("중지", "스케줄을 중지합니다.", null){
					@Override
					protected void to_handle_command(CommandArgs args) throws Exception{
						DungeonTimeUpdator.getInstance().dispose();
						args.notify("스케줄링을 중지 했습니다.");
					}
				});
	}
}
