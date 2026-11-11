package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Character;

public class S_HPMeter extends ServerBasePacket {
	
	public S_HPMeter(int objId, int hpRatio) {
		buildPacket(objId, hpRatio);
	}

	public S_HPMeter(L1Character cha) {
		int objId = cha.getId();
		int hpRatio = 100;
		if (0 < cha.getMaxHp()) {
			hpRatio = 100 * cha.getCurrentHp() / cha.getMaxHp();
		}

		buildPacket(objId, hpRatio);
	}

	private void buildPacket(int objId, int hpRatio) {
		writeC(Opcodes.S_OPCODE_HPMETER);
		writeD(objId);
		writeC(hpRatio);
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
