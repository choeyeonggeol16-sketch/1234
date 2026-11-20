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

public final class SC_AttackMissPacket {

	private static final int WIRE_SIZE_WITH_EXTRA =
			CConnectionConfig.HEADER_SIZE + 18;

	private static final int WIRE_SIZE_NO_EXTRA =
			CConnectionConfig.HEADER_SIZE + 17;

	private SC_AttackMissPacket() {
	}

	public static boolean send(
			CUserSession client,
			L1Character attacker,
			int targetId) {

		return send(
				client,
				attacker,
				targetId,
				1);
	}

	public static boolean send(
			CUserSession client,
			L1Character attacker,
			int targetId,
			int actId) {

		final int attackerId =
				attacker != null ? attacker.getId() : 0;

		final int heading =
				attacker != null ? attacker.getHeading() : 0;

		return send0(
				client,
				attackerId,
				targetId,
				actId,
				heading,
				true);
	}

	public static boolean send(
			CUserSession client,
			int attackerId,
			int targetId) {

		return send(
				client,
				attackerId,
				targetId,
				1);
	}

	public static boolean send(
			CUserSession client,
			int attackerId,
			int targetId,
			int actId) {

		return send0(
				client,
				attackerId,
				targetId,
				actId,
				0,
				false);
	}

	public static void broadcast(
			L1Character attacker,
			int targetId) {

		broadcast(
				attacker,
				targetId,
				1);
	}

	public static void broadcast(L1Character attacker, int targetId, int actId) {
        if (attacker == null) return;
        final int attackerId = attacker.getId();
        final int heading = attacker.getHeading();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendExtended(attacker, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_ATTACKMISS, Opcodes.S_ATTACK, attackerId, targetId, actId, heading, 0, 0, 0, 0, 0, 0, 0, true);
    }

private static boolean send0(
			CUserSession client,
			int attackerId,
			int targetId,
			int actId,
			int heading,
			boolean hasExtraByte) {

		if (client == null || !client.isConnected()) {
			return false;
		}

		final CUserSocket connection = client.getConnection();
		if (connection == null || !connection.isClientConnected()) {
			return false;
		}

		final int wireSize =
				hasExtraByte
						? WIRE_SIZE_WITH_EXTRA
						: WIRE_SIZE_NO_EXTRA;

		final CIOFrame frame =
				connection.beginDirectFrame(
						wireSize);

		if (frame == null) {
			return false;
		}

		final CPacketWriteBuffer buffer = frame.buffer();
		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_ATTACK);
			buffer.writeC(actId);
			buffer.writeD(attackerId);
			buffer.writeD(targetId);
			buffer.writeC(0);
			buffer.writeC(0);
			buffer.writeC(heading);
			buffer.writeD(0);

			if (hasExtraByte) {
				buffer.writeC(0);
			}

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
