package l1j.server.DogFightSystem;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.serverpackets.ServerBasePacket;

public class S_DogFightBoard extends ServerBasePacket {

	private static final String S_RaceBoard = "[C] S_RaceBoard";

	public S_DogFightBoard(int number) {
		buildPacket(number);
	}

	private void buildPacket(int number) {
	writeC(Opcodes.S_OPCODE_SHOWHTML);
	writeD(number);
	writeS("maeno4");
	writeC(0);                        
	writeH(15);
	
	for( int i = 0; i < 2; ++i ) {
		writeS(DogFightController.getInstance()._dogfight[i].getName()); 				//Åõ°ß ÀÌ¸§
		writeS(DogFightController.getInstance()._FightCondition[i]);					//»óÅÂ
		writeS(Double.toString(DogFightController.getInstance()._winRate[i]) + "%"); 	//½Â·ü
	}

	writeS("");
	writeS("");
	writeS("");
	writeS("");
	writeS("");
	writeS("");
	writeS("");
	writeS("");
	writeS("");
}

	@Override
	public byte[] getContent() {
		return getBytes();
	}

	public String getType() {
		return S_RaceBoard;
	}
}

