package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;

public final class SC_RemoveObject {

	private static final int WIRE_SIZE =
			ConnectionConfig.HEADER_SIZE
					+ 1
					+ Integer.BYTES
					+ Short.BYTES;

	private SC_RemoveObject() {
	}

	public static boolean send(
			CIOClient client,
			L1Object obj) {

		return send(
				client,
				obj != null ? obj.getId() : 0);
	}

	public static boolean send(
			CIOClient client,
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

		final WriteBuffer buffer = frame.buffer();
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

		{
		    final L1World __cioWorld0 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(anchor);
		    try {
		        for (L1PcInstance pc : __cioVisible0) {
        
        			if (pc == null) {
        				continue;
        			}
        
        			final CIOClient client =
        					pc.getNetConnection();
        
        			if (client == null
        					|| !client.isConnected()) {
        				continue;
        			}
        
        			send(
        					client,
        					objectId);
        		}
		    } finally {
		        __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
		    }
		}
	}
}
