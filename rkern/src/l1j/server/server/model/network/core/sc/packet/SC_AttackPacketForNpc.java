package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CUserSession;
import l1j.server.server.model.network.utill.CConnectionConfig;

public final class SC_AttackPacketForNpc {

	private static final int WIRE_SIZE =
			CConnectionConfig.HEADER_SIZE + 18;

	private SC_AttackPacketForNpc() {
	}

	public static boolean send(
			CUserSession client,
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
        if (anchor == null) return;
        final int chaId = cha != null ? cha.getId() : 0;
        final int heading = cha != null ? cha.getHeading() : 0;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendExtended(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_ATTACKNPC, Opcodes.S_ATTACK, type, npcObjectId, chaId, heading, 0, 0, 0, 0, 0, 0, 0, true);
    }

	private static boolean send0(
			CUserSession client,
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

		final CPacketWriteBuffer buffer =
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
