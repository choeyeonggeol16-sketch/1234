package l1j.server.server.model.item.enchant;

import l1j.server.server.shared.command.Command;
import l1j.server.server.shared.command.CommandArgs;
import l1j.server.server.shared.command.CommandTree;

public class ItemEnchantSystemLoadManager implements Command{
	private static ItemEnchantSystemLoadManager _instance;
	public static ItemEnchantSystemLoadManager getInstance(){
		if(_instance == null)
			_instance = new ItemEnchantSystemLoadManager();
		return _instance;
	}

	public static void release(){
		if(_instance != null){
			_instance.dispose();
			_instance = null;
		}
	}

	public static void reload(){
		ItemEnchantSystemLoadManager old = _instance;
		_instance = new ItemEnchantSystemLoadManager();
		if(old != null){
			old.dispose();
			old = null;
		}
	}
	
	private CommandTree _commands;
	private ItemEnchantSystemLoadManager(){
		_commands = createCommand();
	}
	
	public void load(){
		ItemEnchanterLoader.getInstance();
		ItemEnchanteeLoader.getInstance();
	}

	private CommandTree createCommand(){
		return new CommandTree(".인챈트시스템", "1(리로드) ->(1)인챈터/(2)인챈티", null)
			.add_command(createReloadCommand());
	}
	
	private CommandTree createReloadCommand(){
		return new CommandTree("1", "리로드 관련 명령을 수행합니다.", null)
			.add_command(new CommandTree("1", "", null){
				@Override
				protected void to_handle_command(CommandArgs args) throws Exception{
					ItemEnchanterLoader.reload();
					args.notify("tb_enchanters 테이블을 리로드 하였습니다.");
				}
			})
			.add_command(new CommandTree("2", "", null){
				@Override
				protected void to_handle_command(CommandArgs args) throws Exception{
					ItemEnchanteeLoader.reload();
					args.notify("tb_enchanties 테이블을 리로드 하였습니다.");
				}
			});
	}
	
	public void dispose(){
	}

	@Override
	public void execute(CommandArgs args) {
		_commands.execute(args, new StringBuilder(256).append(_commands.to_operation()));
	}
}
