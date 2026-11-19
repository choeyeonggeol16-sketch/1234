package l1j.server.server;

import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Card.L1CardA;
import l1j.server.server.model.item.Card.L1CardAlchemy2;
import l1j.server.server.model.item.Card.L1CardB;
import l1j.server.server.model.item.Card.L1CardC;
import l1j.server.server.model.item.Card.L1CardHPast;
import l1j.server.server.model.item.Card.L1CardHeroList;
import l1j.server.server.model.item.Card.L1CardLegendList;
import l1j.server.server.model.item.Card.L1CardStep2;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

public class NeigeLow{

	boolean spawnTF = false;  


	private static NeigeLow _instance; 
	
	private NeigeLow() {
	}

	public static NeigeLow getInstance() {
		if (_instance == null) {
			_instance = new NeigeLow(); 
		}
		return _instance;
	}
	


public static boolean Low(L1PcInstance pc, String s) {

		String s2 = s;
		int a = 0;
		switch (s2) {
		case "nelome00":
			SC_NPCTalkReturn.send(pc, pc.getId(), "2007santa4");
			return true;

		case "nelowp01":
			L1PolyMorph.doPoly(pc, 21350, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp02":
			L1PolyMorph.doPoly(pc, 21354, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp03":
			L1PolyMorph.doPoly(pc, 21356, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp04":
			L1PolyMorph.doPoly(pc, 21359, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp05":
			L1PolyMorph.doPoly(pc, 21361, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp06":
			L1PolyMorph.doPoly(pc, 21363, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp07":
			L1PolyMorph.doPoly(pc, 21366, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp08":
			L1PolyMorph.doPoly(pc, 21369, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp09":
			L1PolyMorph.doPoly(pc, 21373, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp10":
			L1PolyMorph.doPoly(pc, 21377, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp11":
			L1PolyMorph.doPoly(pc, 21381, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp12":
			L1PolyMorph.doPoly(pc, 21384, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp13":
			L1PolyMorph.doPoly(pc, 21387, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp14":
			L1PolyMorph.doPoly(pc, 21390, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp15":
			L1PolyMorph.doPoly(pc, 21393, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp16":
			L1PolyMorph.doPoly(pc, 21397, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp17":
			L1PolyMorph.doPoly(pc, 21399, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
		case "nelowp18":
			L1PolyMorph.doPoly(pc, 21402, 14400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
			
			SC_SkillSound.send(pc, pc.getId(), 6082);
			SC_SkillSound.broadcast(pc, pc.getId(), 6082);
			return false;
			
	     default: {
	    	
	            return false;
	        }
	     
	    }
		
	}
	
	
}
