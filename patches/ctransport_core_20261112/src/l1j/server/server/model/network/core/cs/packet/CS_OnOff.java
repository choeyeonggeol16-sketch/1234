package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;

public final class CS_OnOff {

	private CS_OnOff() {
	}

	public static void handle(
			CIOClient client,
			int type,
			int button) {

		if (client == null) {
			return;
		}

		final L1PcInstance pc =
				client.getActiveChar();

		if (pc == null) {
			return;
		}

		switch (type) {

		/*
		 * 월드채팅 + 귓말 조합값.
		 */
		case 255:

			switch (button) {

			case 95:
			case 127:
				pc.setShowWorldChat(true);
				pc.setCanWhisper(true);
				return;

			case 91:
			case 123:
				pc.setShowWorldChat(true);
				pc.setCanWhisper(false);
				return;

			case 94:
			case 126:
				pc.setShowWorldChat(false);
				pc.setCanWhisper(true);
				return;

			case 90:
			case 122:
				pc.setShowWorldChat(false);
				pc.setCanWhisper(false);
				return;

			default:
				return;
			}

			/*
			 * 월드채팅
			 */
		case 0:

			if (button == 0) {

				pc.setShowWorldChat(false);

			} else if (button == 1) {

				pc.setShowWorldChat(true);
			}

			return;

			/*
			 * 귓말
			 */
		case 2:

			if (button == 0) {

				pc.setCanWhisper(false);

			} else if (button == 1) {

				pc.setCanWhisper(true);
			}

			return;

			/*
			 * 거래채팅
			 */
		case 6:

			if (button == 0) {

				pc.setShowTradeChat(false);

			} else if (button == 1) {

				pc.setShowTradeChat(true);
			}

			return;

		default:
			return;
		}
	}
}