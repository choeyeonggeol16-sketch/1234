package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.config.CIOConfig;

public final class SC_Teleport {

	private static final int WIRE_SIZE =
			CIOConfig.HEADER_SIZE + 1;

	private SC_Teleport() {
	}

	public static boolean send(CUserSocket client) {
		if (client == null || !client.isConnected()) {
			return false;
		}

		final CUserSocket connection = client;
		if (connection == null || !connection.isClientConnected()) {
			return false;
		}

		final CIOFrame frame =
				connection.beginDirectFrame(
						WIRE_SIZE);

		if (frame == null) {
			return false;
		}

		final CPacketWriteBuffer buffer = frame.buffer();
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
