package l1j.server.server.packets.Protocol;

import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.session.CIOClient;

/**
 * 기존 코드 호환용 bridge.
 *
 * 신규 ProtoPacket은 직접 ServerPacket이므로
 * 가능하면 Proto.send() 또는 pc.sendPackets(packet)을 사용한다.
 */
public final class ProtoPacketWriter extends ServerPacket {

	private final ProtoPacket _packet;

	public ProtoPacketWriter(ProtoPacket packet) {
		if (packet == null) throw new NullPointerException("packet");
		_packet = packet;
	}

	@Override
	protected void writeImpl(CIOClient client, WriteBuffer buffer) {
		_packet.writeTo(buffer, true);
	}

	/** 이미 완성된 cache byte[] 전송용 */
	public static final class Raw extends ServerPacket {
		private final byte[] _data;

		public Raw(byte[] data) {
			if (data == null) throw new NullPointerException("data");
			_data = data;
		}

		@Override
		protected void writeImpl(CIOClient client, WriteBuffer buffer) {
			buffer.writeBytes(_data);
		}
	}
}