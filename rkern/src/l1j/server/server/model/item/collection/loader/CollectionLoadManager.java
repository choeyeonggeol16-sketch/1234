package l1j.server.server.model.item.collection.loader;

import java.util.ArrayDeque;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.collection.CollectionObject;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
public class CollectionLoadManager {
	public static final int CTSYSTEM_STORE_ID = 3000468;	
	public static final int CTSYSTEM_LOAD_ID = 3000469;
	
	private static CollectionLoadManager _instance;
	public static CollectionLoadManager getInstance(){
		if(_instance == null)
			_instance = new CollectionLoadManager();
		return _instance;
	}
	
	private static ArrayDeque<String> parseToStringArray(String s){
		String[] arr 	= s.split(" ");
		int size		= arr.length;
		ArrayDeque<String> argsQ = new ArrayDeque<String>(size);
		for(int i=0; i<size; i++){
			try{
				argsQ.offer(arr[i]);
			}catch(Exception e){
				break;
			}
		}
		return argsQ;
	}
	
	private static final String _basicMenus = 
			".구슬\n" +
			"[1. 캐릭터정보] [2. 인벤토리] [3. 스킬]\n" + 
			"[캐릭터 이름]";
	public static void commands(L1PcInstance pc, String param){
		try{
			ArrayDeque<String> argsQ = parseToStringArray(param);
			if(argsQ == null || argsQ.isEmpty())
				throw new Exception("");
			
			int 	cmd	= Integer.parseInt(argsQ.poll());
			
			switch(cmd){
			case 1:
				infoAskCommands(pc, argsQ.poll());
				break;
			case 2:
				inventoryAskCommands(pc, argsQ.poll());
				break;
			case 3:
				skillsAskCommands(pc, argsQ.poll());
				break;
			default:
				throw new Exception("");
			}
			
		}catch(Exception e){
			SC_SystemMessage.send(pc, _basicMenus);
		}
	}
	
	public static final String _invalidCharacter = "캐릭터 정보를 찾을 수 없습니다.";
	public static void infoAskCommands(L1PcInstance pc, String cName){
		CollectionObject obj = CollectionSystemLoader.getInstance().get(cName);
		if(obj == null){
			SC_SystemMessage.send(pc, _invalidCharacter);
			return;
		}
		
		if(obj.infoPck != null)
			obj.infoPck.send(pc.getNetConnection());
	}
	
	public static void inventoryAskCommands(L1PcInstance pc, String cName){
		CollectionObject obj = CollectionSystemLoader.getInstance().get(cName);
		if(obj == null){
			SC_SystemMessage.send(pc, _invalidCharacter);
			return;
		}
		
		if(obj.invPck != null)
			l1j.server.server.packets.Protocol.MainServer.Proto.Proto.sendRaw(pc, obj.invPck);
	}
	
	public static void skillsAskCommands(L1PcInstance pc, String cName){
		CollectionObject obj = CollectionSystemLoader.getInstance().get(cName);
		if(obj == null){
			SC_SystemMessage.send(pc, _invalidCharacter);
			return;
		}
		
		if(obj.spPck != null)
			l1j.server.server.packets.Protocol.MainServer.Proto.Proto.sendRaw(pc, obj.spPck);
	}
	
	private CollectionLoadManager(){}
	
	public void load(){
		CollectionSpellLoader.getInstance();
		CollectionSystemLoader.getInstance();
	}
	
	public void  release(){
		CollectionSpellLoader.release();
		CollectionSystemLoader.release();
	}
}
