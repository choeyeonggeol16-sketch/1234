package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;

public final class SC_Teleport {

	private static final int WIRE_SIZE =
			ConnectionConfig.HEADER_SIZE + 1;

	private SC_Teleport() {
	}

	public static boolean send(CIOClient client) {
		if (client == null || !client.isConnected()) {
			return false;
		}

		final CUserSocket connection = client.getConnection();
		if (connection == null || !connection.isClientConnected()) {
			return false;
		}

		final CIOFrame frame =
				connection.beginDirectFrame(
						WIRE_SIZE);

		if (frame == null) {
			return false;
		}

		final WriteBuffer buffer = frame.buffer();
		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_REQUEST_SUMMON);

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

	public static boolean send(L1PcInstance pc) {
		return pc != null
				&& send(pc.getNetConnection());
	}
}
