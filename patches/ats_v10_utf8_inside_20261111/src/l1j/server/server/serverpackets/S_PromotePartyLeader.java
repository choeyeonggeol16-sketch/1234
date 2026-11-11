package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;

public class S_PromotePartyLeader extends ServerBasePacket {
	private static final String _TYPE = "[S] S_PromotePartyLeader";

	public S_PromotePartyLeader(L1PcInstance cha) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(0x6a);
		if (cha.getParty() == null) {
			writeC(0x00);
		} else {
			L1PcInstance leader = cha.getParty().getLeader();
			writeD(leader.getId());
		}
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}

	@Override
	public String getType() {
		return _TYPE;
	}
}
