package l1j.server.CTSystem.Loader;

import java.util.ArrayDeque;

import l1j.server.CTSystem.MJCTObject;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;
/** 
 * Character TradeSystem - Load Manager
 * CTSystem managed class.
 **/
public class CTLoadManager {
	public static final int CTSYSTEM_STORE_ID = 32553;	
	public static final int CTSYSTEM_LOAD_ID = 32554;
	
	private static CTLoadManager _instance;
	public static CTLoadManager getInstance(){
		if(_instance == null)
			_instance = new CTLoadManager();
		return _instance;
	}
	
	private static ArrayDeque<String> parseToStringArray(String s){
		String[] arr 	= s.split(" ");
		int size		= arr.length;
		ArrayDeque<String> argsQ = new ArrayDeque<String>(size);
		for(int i=0; i<size; i++) {
			try{
				argsQ.offer(arr[i]);
			}catch(Exception e){
				break;
			}
		}
		return argsQ;
	}
	
	private static final S_SystemMessage _basicMenus = new S_SystemMessage(
			"--------------------------------------------------\n" +
			"교환전 반드시 캐릭터 정보를 확인하시기 바랍니다\n" +
			//".캐릭터 2.[인벤정보] 3.[스킬정보] + 캐릭터명\n" + 
			"1.[캐릭정보] 2.[인벤정보] 3.[스킬정보] + 캐릭터명\n\n" +
			"예).구슬 1 캐릭명(정보 확인)\n" +
			"--------------------------------------------------"
			);
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
			pc.sendPackets(_basicMenus, false);
		}
	}
	
	public static final S_SystemMessage _invalidCharacter = new S_SystemMessage("캐릭터 정보를 찾을 수 없습니다.");
	public static void infoAskCommands(L1PcInstance pc, String cName){
		MJCTObject obj = MJCTSystemLoader.getInstance().get(cName);
		if(obj == null){
			pc.sendPackets(CTLoadManager._invalidCharacter, false);
			return;
		} 
		
		
		if(obj.infoPck != null) {
			pc.sendPackets(obj.infoPck, false); 
		}
	}
	
	public static void inventoryAskCommands(L1PcInstance pc, String cName){
		MJCTObject obj = MJCTSystemLoader.getInstance().get(cName);
		if(obj == null){
			pc.sendPackets(CTLoadManager._invalidCharacter, false);
			return;
		} 
		
		if(obj.invPck != null) {
			pc.sendPackets(obj.invPck, false);
		}
	}
	
	public static void skillsAskCommands(L1PcInstance pc, String cName){
		MJCTObject obj = MJCTSystemLoader.getInstance().get(cName);
		if(obj == null){
			pc.sendPackets(CTLoadManager._invalidCharacter, false);
			return;
		} 
		if(obj.spPck != null)
			pc.sendPackets(obj.spPck, false);
	}
	

	
	private CTLoadManager(){}
	
	public void load(){
		MJCTSpellLoader.getInstance();
		MJCTSystemLoader.getInstance();
	}
	
	public void  release(){
		MJCTSpellLoader.release();
		MJCTSystemLoader.release();
	}
}
