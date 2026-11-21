package l1j.server.server.shared.exception;

public class CommandArgsIndexException extends Exception{
	private static final long serialVersionUID = 1L;

	public CommandArgsIndexException(String[] param, int index){
		super(param + " " + index);
	}
}
