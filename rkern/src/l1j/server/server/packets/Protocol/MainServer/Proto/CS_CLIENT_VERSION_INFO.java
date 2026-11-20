package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.model.network.core.session.CUserSession;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoReader;

/**
 * 클라 버전 정보 요청 — body 파싱 후 SC_ServerVersionInfo로 응답
 *  [opcode][msgid2 LE][len2 LE][body]
 */
public final class CS_CLIENT_VERSION_INFO {
	private CS_CLIENT_VERSION_INFO() {}

	public static void handleRequest(CUserSession clnt, ProtoReader r) {
		int clientVersion = 0;
		int instanceCount = 0;

		while (!r.isAtEnd()) {
			final int tag = r.tag();
			switch (tag) {
			case 0x08: r.varint32(); break;                  // 1 protocol_version
			case 0x10: r.varint32(); break;                  // 2 active_code_page
			case 0x18: r.varint32(); break;                  // 3 check_seed
			case 0x20: clientVersion = r.varint32(); break;  // 4 client_version
			case 0x28: instanceCount = r.varint32(); break;  // 5 client_instance_count
			default:
				respond(clnt, clientVersion, instanceCount);
				return;
			}
		}
		respond(clnt, clientVersion, instanceCount);
	}

	private static void respond(CUserSession clnt, int clientVersion, int instanceCount) {
		if (clientVersion == CPacketFilter.NS_CLIENT_VERSION
				&& instanceCount >= 1 && instanceCount <= 2) {
			SC_SERVER_VERSION_INFO.send(clnt);
		} else {
			SC_SERVER_VERSION_INFO.denial(clnt);
		}
	}
}