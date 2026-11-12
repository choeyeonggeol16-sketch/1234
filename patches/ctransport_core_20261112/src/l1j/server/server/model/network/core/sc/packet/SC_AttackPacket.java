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

public final class SC_AttackPacket {

	private static final int MODE_1 = 1;
	private static final int MODE_2 = 2;
	private static final int MODE_3 = 3;

	private static final int WIRE_SIZE_MODE_1 =
			ConnectionConfig.HEADER_SIZE + 18;

	private static final int WIRE_SIZE_MODE_2 =
			ConnectionConfig.HEADER_SIZE + 18;

	private static final int WIRE_SIZE_MODE_3 =
			ConnectionConfig.HEADER_SIZE + 24;

	private SC_AttackPacket() {
	}

	public static boolean send(
			CIOClient client,
			L1Character cha,
			int objId,
			int type) {

		final int chaId =
				cha != null ? cha.getId() : 0;

		final int heading =
				cha != null ? cha.getHeading() : 0;

		return send0(
				client,
				MODE_1,
				chaId,
				objId,
				type,
				heading,
				0,
				0);
	}

	public static boolean send(
			CIOClient client,
			L1Character cha,
			int objId,
			int type,
			int attackType) {

		final int chaId =
				cha != null ? cha.getId() : 0;

		final int heading =
				cha != null ? cha.getHeading() : 0;

		return send0(
				client,
				MODE_2,
				chaId,
				objId,
				type,
				heading,
				attackType,
				0);
	}

	public static boolean send(
			CIOClient client,
			L1Character cha,
			int objId,
			int type,
			int attackType,
			int gfx) {

		final int chaId =
				cha != null ? cha.getId() : 0;

		final int heading =
				cha != null ? cha.getHeading() : 0;

		return send0(
				client,
				MODE_3,
				chaId,
				objId,
				type,
				heading,
				attackType,
				gfx);
	}

	public static void broadcast(
			L1Character cha,
			int objId,
			int type) {

		if (cha == null) {
			return;
		}

		broadcast0(
				cha,
				MODE_1,
				objId,
				type,
				0,
				0);
	}

	public static void broadcast(
			L1Character cha,
			int objId,
			int type,
			int attackType) {

		if (cha == null) {
			return;
		}

		broadcast0(
				cha,
				MODE_2,
				objId,
				type,
				attackType,
				0);
	}

	public static void broadcast(
			L1Character cha,
			int objId,
			int type,
			int attackType,
			int gfx) {

		if (cha == null) {
			return;
		}

		broadcast0(
				cha,
				MODE_3,
				objId,
				type,
				attackType,
				gfx);
	}

	private static void broadcast0(
			L1Character cha,
			int mode,
			int objId,
			int type,
			int attackType,
			int gfx) {

		final int chaId = cha.getId();
		final int heading = cha.getHeading();

		{
		    final L1World __cioWorld0 = L1World.getInstance();
		    final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(cha);
		    try {
		        for (L1PcInstance pc : __cioVisible0) {
        
        			if (pc == null) {
        				continue;
        			}
        
        			send0(
        					pc.getNetConnection(),
        					mode,
        					chaId,
        					objId,
        					type,
        					heading,
        					attackType,
        					gfx);
        		}
		    } finally {
		        __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
		    }
		}
	}

	private static boolean send0(
			CIOClient client,
			int mode,
			int chaId,
			int objId,
			int type,
			int heading,
			int attackType,
			int gfx) {

		if (client == null || !client.isConnected()) {
			return false;
		}

		final CUserSocket connection = client.getConnection();
		if (connection == null || !connection.isClientConnected()) {
			return false;
		}

		final int wireSize;

		switch (mode) {
		case MODE_1:
			wireSize = WIRE_SIZE_MODE_1;
			break;

		case MODE_2:
			wireSize = WIRE_SIZE_MODE_2;
			break;

		case MODE_3:
			wireSize = WIRE_SIZE_MODE_3;
			break;

		default:
			return false;
		}

		final CIOFrame frame =
				connection.beginDirectFrame(
						wireSize);

		if (frame == null) {
			return false;
		}

		final WriteBuffer buffer = frame.buffer();
		boolean committed = false;

		try {
			buffer.writeC(Opcodes.S_ATTACK);
			buffer.writeC(type);

			switch (mode) {
			case MODE_1:
				buffer.writeD(objId);
				buffer.writeD(chaId);
				buffer.writeC(0x4E);
				buffer.writeC(0);
				buffer.writeC(heading);
				buffer.writeD(0x00000000);
				buffer.writeC(0x00);
				break;

			case MODE_2:
				buffer.writeD(chaId);
				buffer.writeD(objId);
				buffer.writeH(0x02);
				buffer.writeC(heading);
				buffer.writeH(0x0000);
				buffer.writeH(0x0000);
				buffer.writeC(attackType);
				break;

			case MODE_3:
				buffer.writeD(chaId);
				buffer.writeD(objId);
				buffer.writeC(0x01);
				buffer.writeC(0x00);
				buffer.writeC(heading);
				buffer.writeH(0x0000);
				buffer.writeH(0x0000);
				buffer.writeC(attackType);
				buffer.writeH(gfx);
				buffer.writeH(0x00);
				buffer.writeH(0x00);
				break;
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
