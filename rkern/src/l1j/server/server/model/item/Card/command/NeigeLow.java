package l1j.server.server.model.item.Card.command;

import l1j.server.server.model.Instance.L1PcInstance;
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
			NeigeMorph.applyPaid(pc, 21350);
			return false;
		case "nelowp02":
			NeigeMorph.applyPaid(pc, 21354);
			return false;
		case "nelowp03":
			NeigeMorph.applyPaid(pc, 21356);
			return false;
		case "nelowp04":
			NeigeMorph.applyPaid(pc, 21359);
			return false;
		case "nelowp05":
			NeigeMorph.applyPaid(pc, 21361);
			return false;
		case "nelowp06":
			NeigeMorph.applyPaid(pc, 21363);
			return false;
		case "nelowp07":
			NeigeMorph.applyPaid(pc, 21366);
			return false;
		case "nelowp08":
			NeigeMorph.applyPaid(pc, 21369);
			return false;
		case "nelowp09":
			NeigeMorph.applyPaid(pc, 21373);
			return false;
		case "nelowp10":
			NeigeMorph.applyPaid(pc, 21377);
			return false;
		case "nelowp11":
			NeigeMorph.applyPaid(pc, 21381);
			return false;
		case "nelowp12":
			NeigeMorph.applyPaid(pc, 21384);
			return false;
		case "nelowp13":
			NeigeMorph.applyPaid(pc, 21387);
			return false;
		case "nelowp14":
			NeigeMorph.applyPaid(pc, 21390);
			return false;
		case "nelowp15":
			NeigeMorph.applyPaid(pc, 21393);
			return false;
		case "nelowp16":
			NeigeMorph.applyPaid(pc, 21397);
			return false;
		case "nelowp17":
			NeigeMorph.applyPaid(pc, 21399);
			return false;
		case "nelowp18":
			NeigeMorph.applyPaid(pc, 21402);
			return false;
			
	     default: {
	    	
	            return false;
	        }
	     
	    }
		
	}
	
	
}
