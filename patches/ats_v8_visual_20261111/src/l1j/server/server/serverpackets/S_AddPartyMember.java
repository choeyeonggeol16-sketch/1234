package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;

public class S_AddPartyMember extends ServerBasePacket {

	public S_AddPartyMember(L1PcInstance cha) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(0x69);
		double nowhp = 0.0d;
		double maxhp = 0.0d;
		nowhp = cha.getCurrentHp();
		maxhp = cha.getMaxHp();
		writeD(cha.getId());
		writeS(cha.getName());
		writeD(cha.getMapId());
		writeC((int) ((nowhp / maxhp) * 100));
		writeH(cha.getX());
		writeH(cha.getY());
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
