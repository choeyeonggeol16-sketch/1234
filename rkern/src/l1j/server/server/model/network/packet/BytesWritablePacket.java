package l1j.server.server.model.network.packet;

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

	@Override
	public boolean write(CIOClient client, WriteBuffer buffer) {
		if (_data != null && _data.length > 0) {
			buffer.writeBytes(_data); 
		}
		return true;
	}
}