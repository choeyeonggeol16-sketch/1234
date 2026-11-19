package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_MoveChar {

	private static final int WIRE_SIZE = 17;

	private static final int[] HEADING_TABLE_X = {
		 0, -1, -1, -1,  0,  1,  1,  1
	};

	private static final int[] HEADING_TABLE_Y = {
		 1,  1,  0, -1, -1, -1,  0,  1
	};

	private SC_MoveChar() {
	}

	public static boolean send(
			CIOClient client,
			int objectId,
			int x,
			int y,
			int heading) {

		if (client == null || !client.isConnected()) {
			return false;
		}

		final CUserSocket connection = client.getConnection();
		if (connection == null || !connection.isClientConnected()) {
			return false;
		}

		final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);

		if (frame == null) {
			return false;
		}

		final WriteBuffer buffer = frame.buffer();
		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_MOVE_OBJECT);
			buffer.writeD(objectId);
			buffer.writeH(x);
			buffer.writeH(y);
			buffer.writeC(heading);
			buffer.writeC(129);
			buffer.writeD(0);

			committed =
					connection.commitDirectFrame(frame);
			return committed;
		} finally {
			if (!committed) {
				connection.abortDirectFrame(frame);
			}
		}
	}

	/**
	 * 현재 cha 좌표에서 heading 방향으로 한 칸 이동한
	 * 목적지 좌표를 계산해서 전송한다.
	 */
	public static boolean send(CIOClient client, L1Character cha) {
		if (cha == null) {
			return false;
		}

		final int heading = cha.getHeading();

		return send(
				client,
				cha.getId(),
				cha.getX() + HEADING_TABLE_X[heading],
				cha.getY() + HEADING_TABLE_Y[heading],
				heading);
	}

    /** Exact immutable 17-byte move frame, shared by any recipient set. */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int objectId,int x,int y,int heading) {
        return b -> b.put((byte)Opcodes.S_MOVE_OBJECT).putInt(objectId).putShort((short)x)
             .putShort((short)y).put((byte)heading).put((byte)129).putInt(0);
    }


    public static void broadcast(L1Character cha, int x, int y, int heading) {
        if (cha == null) return;
        // Snapshot the moving object's ID and destination before AOI enumeration.
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendMove(
                cha, cha.getId(), x, y, heading);
    }
    public static void broadcast(L1Character cha) {
        if (cha == null) return;
        int heading = cha.getHeading();
        broadcast(cha, cha.getX() + HEADING_TABLE_X[heading], cha.getY() + HEADING_TABLE_Y[heading], heading);
    }
}
