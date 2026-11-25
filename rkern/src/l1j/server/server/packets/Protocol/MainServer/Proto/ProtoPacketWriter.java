package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.model.network.packet.IPacketWriter;
import l1j.server.server.model.network.core.connection.CUserSocket;

/**
 * 기존 코드 호환용 bridge.
 *
 * 신규 ProtoPacket은 직접 CServerPacket이므로
 * 가능하면 Proto.send() 또는 pc.sendPackets(packet)을 사용한다.
 */
public final class ProtoPacketWriter extends CServerPacket {

	private final ProtoPacket _packet;

	public ProtoPacketWriter(ProtoPacket packet) {
		if (packet == null) throw new NullPointerException("packet");
		_packet = packet;
	}

	@Override
	protected void writeImpl(CUserSocket client, Assemble buffer) {
		_packet.writeTo(buffer, true);
	}

	/** 이미 완성된 cache byte[] 전송용 */
	public static final class Raw extends CServerPacket implements IPacketWriter.DirectTail {
		private final byte[] _data;

		public Raw(byte[] data) {
			if (data == null) throw new NullPointerException("data");
			_data = data;
		}

		@Override
		public int maximumWireBytes() {
			return _data.length > 0 && _data.length <= 65533 ? _data.length + 2 : 0;
		}

		@Override
		protected void writeImpl(CUserSocket client, Assemble buffer) {
			buffer.writeBytes(_data);
		}
	}
}
