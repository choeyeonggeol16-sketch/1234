package l1j.server.server.model.network.packet;

import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;

/**
 * 스트림 받는 패킷 구조
 */
public class CBytesPacket extends IPacketWriter<CUserSocket> implements IPacketWriter.DirectTail {
	private final byte[] _data;

	public CBytesPacket(byte[] data) {
		_data = data;
	}

	@Override
	public int maximumWireBytes() {
		// A subclass may override encoding and has to declare its own bound.
		return getClass() == CBytesPacket.class && _data != null && _data.length > 0
				&& _data.length <= 65533 ? _data.length + 2 : 0;
	}

	@Override
	public boolean write(CUserSocket client, CPacketWriteBuffer buffer) {
		if (_data != null && _data.length > 0) {
			buffer.writeBytes(_data); 
		}
		return true;
	}
}
