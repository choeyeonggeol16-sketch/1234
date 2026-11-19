package l1j.server.server.model.Instance;

import l1j.server.Config;
import l1j.server.server.IdFactory;

@SuppressWarnings("serial")
public class L1ManagerInstance extends L1PcInstance{
	private static L1ManagerInstance _instance;
	public static L1ManagerInstance getInstance(){
		if(_instance == null)
			_instance = new L1ManagerInstance();
		return _instance;
	}
	
	private L1ManagerInstance(){
		setId(IdFactory.getInstance().nextId());
	}
	
	@Override
	public short getAccessLevel(){
		return (short) Config.\u0047\u004D\u0043\u004F\u0044\u0045;
	}
	
	@Override
	public String getName(){
		return "매니저";
	}
	
	@Override
	public void sendPackets(String s){
		System.out.println(s);
	}
}
