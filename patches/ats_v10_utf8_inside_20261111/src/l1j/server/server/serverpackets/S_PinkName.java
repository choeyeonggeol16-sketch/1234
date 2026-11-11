package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;

public class S_PinkName extends ServerBasePacket {

	public S_PinkName(int objecId, int time) {
		writeC(Opcodes.S_OPCODE_PINKNAME);
		writeD(objecId);
		writeC(time);
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
