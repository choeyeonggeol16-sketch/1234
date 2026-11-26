package l1j.server.server.model.item.Card.command;




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
			NeigeMorph.apply(pc, 21314);
			return false;
		case "nenormal02":
			NeigeMorph.apply(pc, 21317);
			return false;
		case "nenormal03":
			NeigeMorph.apply(pc, 21318);
			return false;
		case "nenormal04":
			NeigeMorph.apply(pc, 21320);
			return false;
		case "nenormal05":
			NeigeMorph.apply(pc, 21322);
			return false;
		case "nenormal06":
			NeigeMorph.apply(pc, 21324);
			return false;
		case "nenormal07":
			NeigeMorph.apply(pc, 21326);
			return false;
		case "nenormal08":
			NeigeMorph.apply(pc, 21330);
			return false;
		case "nenormal09":
			NeigeMorph.apply(pc, 21331);
			return false;
		case "nenormal10":
			NeigeMorph.apply(pc, 21333);
			return false;
		case "nenormal11":
			NeigeMorph.apply(pc, 21336);
			return false;
		case "nenormal12":
			NeigeMorph.apply(pc, 21337);
			return false;
		case "nenormal13":
			NeigeMorph.apply(pc, 21339);
			return false;
		case "nenormal14":
			NeigeMorph.apply(pc, 21342);
			return false;
		case "nenormal15":
			NeigeMorph.apply(pc, 21343);
			return false;
		case "nenormal16":
			NeigeMorph.apply(pc, 21345);
			return false;
		case "nenormal17":
			NeigeMorph.apply(pc, 21347);
			return false;
	     default: {
	    	
	            return false;
	        }
	     
	    }
		
	}
	
	
}
