
package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;

public class S_MapID extends ServerBasePacket {

	public S_MapID(int mapid, boolean isUnderwater) {
		writeC(Opcodes.S_OPCODE_MAPID);
		writeH(mapid);  
		writeC(isUnderwater ? 1 : 0);
		writeC(0);
		writeH(0);
		writeC(0);
		writeD(0);
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
