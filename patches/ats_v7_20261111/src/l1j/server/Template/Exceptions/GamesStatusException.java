package l1j.server.Template.Exceptions;

@SuppressWarnings("serial")
public class GamesStatusException extends Exception{
	public GamesStatusException(String method, String status){
		super(String.format("method : %s, status : %s", method, status));
	}
}
