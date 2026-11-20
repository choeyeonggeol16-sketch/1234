package l1j.server.server.model.network.packet;

import java.nio.ByteBuffer;

import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;

/**
 * 스트림 받는 패킷 구조
 */
public class BytesWritablePacket extends WritablePacket<CIOClient> {
	private final byte[] _data;

	public BytesWritablePacket(byte[] data) {
		_data = data;
	}

	public final int payloadLength() { return _data == null ? 0 : _data.length; }

	public final void copyPayloadTo(ByteBuffer target, int offset) {
		target.put(offset, _data, 0, _data.length);
	}

	@Override
	public boolean write(CIOClient client, WriteBuffer buffer) {
		if (_data != null && _data.length > 0) {
			buffer.writeBytes(_data); 
		}
		return true;
	}
}
