package l1j.server.server.shared.command;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.shared.exception.CommandArgsIndexException;

public class CommandArgs {
	private L1PcInstance 	_owner;
	private String[]		_args;
	private int				_idx;
	public CommandArgs setOwner(L1PcInstance pc){
		_owner = pc;
		return this;
	}
	
	public CommandArgs setParam(String param){
		_args 	= param.split(" ");
		_idx	= 0;
		return this;
	}
	
	public L1PcInstance getOwner(){
		return _owner;
	}
	
	public int nextInt() throws CommandArgsIndexException{
		isValidRange();
		return Integer.parseInt(_args[_idx++]);
	}
	
	public String nextString() throws CommandArgsIndexException{
		isValidRange();
		return _args[_idx++];
	}
	
	private void isValidRange() throws CommandArgsIndexException{
		if(_args.length <= _idx)
			throw new CommandArgsIndexException(_args, _idx);
	}
	
	public CommandArgs undo(){
		--_idx;
		return this;
	}
	
	public boolean isRange(){
		return _idx < _args.length;
	}
	
	public void notify(String message){
		if(_owner != null)
			_owner.sendPackets(message);
	}
	
	public void dispose(){
		_args 	= null;
		_owner 	= null;
	}
}
