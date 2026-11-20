package l1j.server.server.model.network.packet;

import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;

/**
 * 스트림 받는 패킷 구조
 */
public class BytesWritablePacket extends WritablePacket<CIOClient> implements WritablePacket.DirectTail {
	private final byte[] _data;

	public BytesWritablePacket(byte[] data) {
		_data = data;
	}

	@Override
	public int maximumWireBytes() {
		// A subclass may override encoding and has to declare its own bound.
		return getClass() == BytesWritablePacket.class && _data != null && _data.length > 0
				&& _data.length <= 65533 ? _data.length + 2 : 0;
	}

	@Override
	public boolean write(CIOClient client, WriteBuffer buffer) {
		if (_data != null && _data.length > 0) {
			buffer.writeBytes(_data); 
		}
		return true;
	}
}
