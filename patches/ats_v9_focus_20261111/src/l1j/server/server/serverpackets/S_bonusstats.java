
package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;

public class S_bonusstats extends ServerBasePacket {

	public S_bonusstats(int i, int j) {
		buildPacket(i, j);
	}
	private void buildPacket(int i, int j) {
		writeC(Opcodes.S_OPCODE_SHOWHTML);
		writeD(i);
		writeS("RaiseAttr");
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
