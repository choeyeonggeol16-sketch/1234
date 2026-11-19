package l1j.server.MJTemplate.Exceptions;

public class MJCommandArgsIndexException extends Exception{
	private static final long serialVersionUID = 1L;

	public MJCommandArgsIndexException(String[] param, int index){
		super(param + " " + index);
	}
}
