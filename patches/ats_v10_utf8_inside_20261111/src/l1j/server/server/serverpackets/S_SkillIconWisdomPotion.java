
package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;

public class S_SkillIconWisdomPotion extends ServerBasePacket {

	public S_SkillIconWisdomPotion(int time) {
		writeC(Opcodes.S_OPCODE_SKILLICONGFX);
		writeC(0x39);
		writeC(0x2c);
		writeH(time);
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
