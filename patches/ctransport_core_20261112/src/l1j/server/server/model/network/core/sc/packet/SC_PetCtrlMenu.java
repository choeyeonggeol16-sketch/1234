package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.*;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_PetCtrlMenu {
	private SC_PetCtrlMenu() {}

	public static boolean send(L1PcInstance receiver, L1NpcInstance npc, boolean open) {
		return receiver != null && send(receiver.getNetConnection(), receiver, npc, open);
	}

	public static boolean send(L1PcInstance receiver, L1PcInstance statePc, L1NpcInstance npc, boolean open) {
		return receiver != null && send(receiver.getNetConnection(), statePc, npc, open);
	}

	public static boolean send(CIOClient client, L1PcInstance pc, L1NpcInstance npc, boolean open) {
		if (client == null || !client.isConnected() || npc == null || pc == null ) return false;
		final CUserSocket connection = client.getConnection();
		if (connection == null || !connection.isClientConnected()) return false;
		final int petListSize = pc.getPetList() == null ? 0 : pc.getPetList().size();
		final int npcObjId = npc.getId();
		final int mapId = npc.getMapId(), x = npc.getX(), y = npc.getY();
		final boolean isPet = npc instanceof L1PetInstance;
		final String nameId = npc.getNameId() == null ? "" : npc.getNameId();
		final CIOFrame frame = connection.beginDirectFrame(44 + (nameId.length() << 1));
		if (frame == null) return false;
		boolean committed = false;
		try {
			final WriteBuffer buffer = frame.buffer();


			buffer.writeC(Opcodes.S_VOICE_CHAT);
			buffer.writeC(0x0c);

			if (open) {
				buffer.writeH(petListSize * 3);
				buffer.writeD(0x00000000);
				buffer.writeD(npcObjId);
				buffer.writeH(mapId);
				buffer.writeH(0x0000);
				buffer.writeH(x);
				buffer.writeH(y);
				buffer.writeC(isPet ? 0x01 : 0x00);
				buffer.writeS(nameId);
			} else {
				buffer.writeH(petListSize * 3 - 3);
				buffer.writeD(0x00000001);
				buffer.writeD(npcObjId);
			}
			committed = connection.commitDirectFrame(frame);
			return committed;
		} finally {
			if (!committed) connection.abortDirectFrame(frame);
		}
	}

}
