package l1j.server.server;




import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

public class NeigeNormal{

	boolean spawnTF = false;  


	private static NeigeNormal _instance; 
	
	private NeigeNormal() {
	}

	public static NeigeNormal getInstance() {
		if (_instance == null) {
			_instance = new NeigeNormal(); 
		}
		return _instance;
	}
	


public static boolean Normal(L1PcInstance pc, String s) {

		String s2 = s;
		int a = 0;
		switch (s2) {
		case "nenome00":
			SC_NPCTalkReturn.send(pc, pc.getId(), "2007santa6");
			return true;

		case "nenormal01":
			//pc.sendPackets(new S_DoActionGFX(pc.getId(), 96));
			L1PolyMorph.doPoly(pc, 21314, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal02":
			L1PolyMorph.doPoly(pc, 21317, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal03":
			L1PolyMorph.doPoly(pc, 21318, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal04":
			L1PolyMorph.doPoly(pc, 21320, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal05":
			L1PolyMorph.doPoly(pc, 21322, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal06":
			L1PolyMorph.doPoly(pc, 21324, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal07":
			L1PolyMorph.doPoly(pc, 21326, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal08":
			L1PolyMorph.doPoly(pc, 21330, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal09":
			L1PolyMorph.doPoly(pc, 21331, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal10":
			L1PolyMorph.doPoly(pc, 21333, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal11":
			L1PolyMorph.doPoly(pc, 21336, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal12":
			L1PolyMorph.doPoly(pc, 21337, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal13":
			L1PolyMorph.doPoly(pc, 21339, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal14":
			L1PolyMorph.doPoly(pc, 21342, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal15":
			L1PolyMorph.doPoly(pc, 21343, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal16":
			L1PolyMorph.doPoly(pc, 21345, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nenormal17":
			L1PolyMorph.doPoly(pc, 21347, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
	     default: {
	    	
	            return false;
	        }
	     
	    }
		
	}
	
	
}
