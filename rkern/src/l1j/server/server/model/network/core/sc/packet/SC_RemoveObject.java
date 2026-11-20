package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.utill.CConnectionConfig;

public final class SC_RemoveObject {

	private static final int WIRE_SIZE =
			CConnectionConfig.HEADER_SIZE
					+ 1
					+ Integer.BYTES
					+ Short.BYTES;

	private SC_RemoveObject() {
	}

	public static boolean send(
			CUserSocket client,
			L1Object obj) {

		return send(
				client,
				obj != null ? obj.getId() : 0);
	}

	public static boolean send(
			CUserSocket client,
			int objectId) {

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

		final CPacketWriteBuffer buffer = frame.buffer();
		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_REMOVE_OBJECT);
			buffer.writeD(objectId);
			buffer.writeH(0);

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

	public static void broadcast(L1Object obj) {
		if (obj == null) {
			return;
		}

		broadcast(
				obj,
				obj.getId());
	}

	public static void broadcast(
			L1Object anchor,
			int objectId) {

		if (anchor == null) {
			return;
		}

        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_REMOVE_OBJECT,
                Opcodes.S_REMOVE_OBJECT, objectId, 0, 0, 0, true, -1, null);
    }


    /** Immutable remove-object frame, including the original 2-byte trailer. */

    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int objectId) {
        return b -> b.put((byte)Opcodes.S_REMOVE_OBJECT).putInt(objectId).putShort((short)0);
    }


}
