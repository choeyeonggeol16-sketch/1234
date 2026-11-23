package l1j.server.server;




import java.util.ArrayList;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;




public class NeigeGMEvent{

	boolean spawnTF = false;  


	private static NeigeGMEvent _instance; 
	
	private NeigeGMEvent() {
	}

	public static NeigeGMEvent getInstance() {
		if (_instance == null) {
			_instance = new NeigeGMEvent(); 
		}
		return _instance;
	}
	public static void settingpage(L1PcInstance pc) {
		ArrayList<String> setting = new ArrayList<String>();
	    if ((pc == null) || (pc.isDead())) {
		      return;
		}
	    setting.add(Config.흑단무빙 == 1 ? "5208" : "5209");
	    setting.add(Config.Neige_Count_Pass == 2 ? "5208" : "5209");
	    setting.add(Config.펑몹이벤트 == 2 ? "5208" : "5209");
	    setting.add(Config.Neige_Reward == 2 ? "5208" : "5209");
	    setting.add(Config.Neige_Myth_Skill == 2 ? "5208" : "5209");
	    setting.add(Config.myth_possible == 0 ? "5208" : "5209");
	    setting.add(Config.sole_possible == 0 ? "5208" : "5209");
	    
	    SC_NPCTalkReturn.send(pc, pc.getId(), "inshop20", (String[]) setting.toArray(new String[setting.size()]));
	    
	}

}
