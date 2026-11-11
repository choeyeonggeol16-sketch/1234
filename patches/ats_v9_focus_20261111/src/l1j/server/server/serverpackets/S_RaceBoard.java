package l1j.server.server.serverpackets;

import l1j.server.BugRace.BugRaceController;
import l1j.server.server.Opcodes.Opcodes;

public class S_RaceBoard extends ServerBasePacket {

	private static final String S_RaceBoard = "[C] S_RaceBoard";

	public S_RaceBoard(int number, boolean is_viewed_gm) {
		buildPacket(number, is_viewed_gm);
	}

	private void buildPacket(int number, boolean is_viewed_gm) {
	writeC(Opcodes.S_OPCODE_SHOWHTML);
	writeD(number);
	writeS("maeno4");
	writeC(0);
	writeH(15);

	if(is_viewed_gm){
		double[] rations = BugRaceController.getInstance().calc_rations();
		for( int i = 0; i < 5; ++i ) {
			double rate = BugRaceController.getInstance()._winRate[i];
			double ration = rations[i];
			int move_speed = BugRaceController.getInstance().getSpeed(i);
			writeS(String.format("%s/%s/%.2f%%/%.2f¹è/%d", BugRaceController.getInstance()._littleBugBear[i].getName(), BugRaceController.getInstance()._bugCondition[i], rate, ration, move_speed));
			writeS("");
			writeS("");
		}
	}else{
		for( int i = 0; i < 5; ++i ) {
			writeS(BugRaceController.getInstance()._littleBugBear[i].getName()); //¹ö±×º£¾î ÀÌ¸§
			writeS(BugRaceController.getInstance()._bugCondition[i]); //»óÅÂ
			writeS(Double.toString(BugRaceController.getInstance()._winRate[i]) + "%"); //½Â·ü ½ÇÁ¦
//			writeS(Double.toString(BugRaceController.getInstance()._winViewRate[i]) + "%"); //½Â·ü ÅëÇÕ
		}
	}
}

	@Override
	public byte[] getContent() {
		return getBytes();
	}

	public String getType() {
		return S_RaceBoard;
	}
}

