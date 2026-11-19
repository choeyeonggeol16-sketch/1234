package l1j.server.MJItemEnchantSystem;

import l1j.server.MJTemplate.Command.MJCommand;
import l1j.server.MJTemplate.Command.MJCommandArgs;
import l1j.server.MJTemplate.Command.MJCommandTree;

public class MJItemEnchantSystemLoadManager implements MJCommand{
	private static MJItemEnchantSystemLoadManager _instance;
	public static MJItemEnchantSystemLoadManager getInstance(){
		if(_instance == null)
			_instance = new MJItemEnchantSystemLoadManager();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		MJItemEnchantSystemLoadManager old = _instance;
		_instance = new MJItemEnchantSystemLoadManager();
		if(old != null){
			old.dispose();
			old = null;
		}
	}
	
	private MJCommandTree _commands;
	private MJItemEnchantSystemLoadManager(){
		_commands = createCommand();
	}
	
	public void load(){
		MJItemEnchanterLoader.getInstance();
		MJItemEnchanteeLoader.getInstance();
	}

	private MJCommandTree createCommand(){
		return new MJCommandTree(".인챈트시스템", "1(리로드) ->(1)인챈터/(2)인챈티", null)
			.add_command(createReloadCommand());
	}
	
	private MJCommandTree createReloadCommand(){
		return new MJCommandTree("1", "리로드 관련 명령을 수행합니다.", null)
			.add_command(new MJCommandTree("1", "", null){
				@Override
				protected void to_handle_command(MJCommandArgs args) throws Exception{
					MJItemEnchanterLoader.reload();
					args.notify("tb_enchanters 테이블을 리로드 하였습니다.");
				}
			})
			.add_command(new MJCommandTree("2", "", null){
				@Override
				protected void to_handle_command(MJCommandArgs args) throws Exception{
					MJItemEnchanteeLoader.reload();
					args.notify("tb_enchanties 테이블을 리로드 하였습니다.");
				}
			});
	}
	
	public void dispose(){
	}

	@Override
	public void execute(MJCommandArgs args) {
		_commands.execute(args, new StringBuilder(256).append(_commands.to_operation()));
	}
}
