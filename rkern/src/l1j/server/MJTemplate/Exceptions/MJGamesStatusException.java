package l1j.server.MJTemplate.Exceptions;

@SuppressWarnings("serial")
public class MJGamesStatusException extends Exception{
	public MJGamesStatusException(String method, String status){
		super(String.format("method : %s, status : %s", method, status));
	}
}
