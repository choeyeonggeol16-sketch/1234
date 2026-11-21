package l1j.server.server.model.world.instance.lobby.template;

public class LobbyDummy extends LobbyObject{
	public static LobbyDummy createInstance(){
		return new LobbyDummy();
	}
	
	@Override
	public void init(){
	}
	
	@Override
	public void run() {
		System.out.println("why dummy run???");
	}
	
	@Override
	public void close(){
	}
	
	@Override
	public String getName(){
		return "LobbyDummy";
	}
}
