package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.Instance.L1PcInstance;

public class S_PartyPosition extends ServerBasePacket {
	
	public S_PartyPosition(L1PcInstance pc) {
		writeC(Opcodes.S_OPCODE_PACKETBOX);
		writeC(0x6E);

		L1Party party = pc.getParty();

		if (party == null) {
			writeC(0x00);
		} else {
			writeC(party.getMembers().length);
			for (L1PcInstance partyMember : party.getMembers()) {
				writeD(partyMember.getId());
				writeH(partyMember.getMapId());
				writeH(0);
				writeH(partyMember.getX());
				writeH(partyMember.getY());
			}
		}
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
