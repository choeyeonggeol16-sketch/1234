package l1j.server.server.model.network.core.cs.packet;

import java.util.logging.Logger;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.CSProto;

public final class CS_ExtendedProto {

	private static final Logger LOGGER = Logger.getLogger(CS_ExtendedProto.class.getName());

	private static final int FRAME_HEADER = 5;

	private CS_ExtendedProto() {
	}

	public static void handle(CIOClient client, Object object) {

		if (client == null || !(object instanceof byte[])) {
			return;
		}

		final byte[] frames = (byte[]) object;
		final int end = frames.length;
		int pos = 0;

		while (pos + FRAME_HEADER <= end) {

			final int opcode = frames[pos] & 0xFF;
			final int msgId = (frames[pos + 1] & 0xFF) | ((frames[pos + 2] & 0xFF) << 8);
			final int len = (frames[pos + 3] & 0xFF) | ((frames[pos + 4] & 0xFF) << 8);
			final int body = pos + FRAME_HEADER;

			/*
			 * 클라이언트가 붙이는
			 * 5 byte NULL padding.
			 */
			if (opcode == 0 && msgId == 0 && len == 0) {
				pos = body;
				continue;
			}

			/*
			 * 잘린 frame 이후는 신뢰하지 않는다.
			 */
			if (body + len > end) {
				LOGGER.warning(String.format(
						"[GAME PROTO] truncated frame opcode=0x%02X msgId=0x%04X need=%d have=%d",
						opcode, msgId, body + len, end));
				return;
			}

            if (opcode == (Opcodes.C_EXTENDED_PROTOBUF & 0xFF)) {
                CSProto.processFrame(client, frames, pos, msgId, len);
            }

			pos = body + len;
		}
	}
}