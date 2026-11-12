package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;

public final class SC_AttackPacketForNpc {

	private static final int WIRE_SIZE =
			ConnectionConfig.HEADER_SIZE + 18;

	private SC_AttackPacketForNpc() {
	}

	public static boolean send(
			CIOClient client,
			L1Character cha,
			int npcObjectId,
			int type) {

		final int chaId =
				cha != null ? cha.getId() : 0;

		final int heading =
				cha != null ? cha.getHeading() : 0;

		return send0(
				client,
				type,
				npcObjectId,
				chaId,
				heading);
	}

	public static void broadcast(
			L1Character cha,
			int npcObjectId,
			int type) {

		if (cha == null) {
			return;
		}

		broadcast(
				cha,
				cha,
				npcObjectId,
				type);
	}

	public static void broadcast(
			L1Character anchor,
			L1Character cha,
			int npcObjectId,
			int type) {

		if (anchor == null) {
			return;
		}

		final int chaId =
				cha != null ? cha.getId() : 0;

		final int heading =
				cha != null ? cha.getHeading() : 0;

		{
		    final L1World __cioWorld0 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(anchor);
		    try {
		        for (L1PcInstance pc : __cioVisible0) {
        
        			if (pc == null) {
        				continue;
        			}
        
        			send0(
        					pc.getNetConnection(),
        					type,
        					npcObjectId,
        					chaId,
        					heading);
        		}
		    } finally {
		        __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
		    }
		}
	}

	private static boolean send0(
			CIOClient client,
			int type,
			int npcObjectId,
			int chaId,
			int heading) {

		if (client == null || !client.isConnected()) {
			return false;
		}

		final CUserSocket connection =
				client.getConnection();

		if (connection == null
				|| !connection.isClientConnected()) {
			return false;
		}


		final CIOFrame frame =
				connection.beginDirectFrame(
						WIRE_SIZE);

		if (frame == null) {
			return false;
		}

		final WriteBuffer buffer =
				frame.buffer();

		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_ATTACK);
			buffer.writeC(type);
			buffer.writeD(npcObjectId);
			buffer.writeD(chaId);
			buffer.writeH(0x01);
			buffer.writeC(heading);
			buffer.writeH(0x0000);
			buffer.writeH(0x0000);
			buffer.writeC(0x00);

			committed =
					connection.commitDirectFrame(
							frame);

			return committed;

		} finally {
			if (!committed) {
				connection.abortDirectFrame(frame);
			}
		}
	}
}
