package l1j.server.MJTemplate.Command;

import java.util.LinkedList;

public class MJCommandTree{
	private String						_operation;
	private String 						_description;
	private LinkedList<MJCommandTree> 	_command_tree;
	private String[]					_args_operations;
	public MJCommandTree(String operation, String description, String[] args_operations){
		_operation = operation;
		_description = description;
		_command_tree = new LinkedList<MJCommandTree>();
		_args_operations = args_operations;
	}
	
	public final void execute(MJCommandArgs args, StringBuilder command_path) {
		try {
			if(args.isRange()){
				String cmd = args.nextString();
				MJCommandTree next = find_command(cmd);
				if(next == null){
					to_handle_command(args.undo());
				}else{
					next.execute(args, command_path.append(" ").append(next.to_operation()));
				}
			}else{
				to_handle_command(args);
			}
		} catch (Exception e) {
			args.notify(to_description());
			args.notify(command_path.toString());
			args.notify(to_valid_operations());
			args.dispose();
		}
	}
	
	public final MJCommandTree find_command(String cmd){
		for(MJCommandTree command : _command_tree){
			if(command.to_operation().equalsIgnoreCase(cmd))
				return command;
		}
		return null;
	}
	
	public final MJCommandTree add_command(MJCommandTree command){
		_command_tree.add(command);
		return this;
	}
	
	public final String to_description(){
		return String.format("(%s)%s", to_operation(), _description);
	}
	public final String to_valid_operations(){
		StringBuilder sb = new StringBuilder(256);
		int i = 0;
		if(_args_operations == null){
			for(MJCommandTree command : _command_tree){
				if(i++ % 4 == 0)
					sb.append("\r\n선택>");
				sb.append("[").append(command.to_operation()).append("]");
			}
		}else{
			for(String s : _args_operations){
				if(i++ % 4 == 0)
					sb.append("\r\n선택>");
				sb.append("[").append(s).append("]");
			}
		}
		return sb.toString();
	}
	
	public final String to_operation(){
		return _operation;
	}
	
	protected void to_handle_command(MJCommandArgs args) throws Exception{
		throw new Exception();
	}
}
