package l1j.server.MJTemplate.PacketHelper;

import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.model.network.core.session.CUserSession;

public class S_BuilderPacket extends CServerPacket {
	
	private final byte[] _b;

	public S_BuilderPacket(byte[] b) {
		_b = b;
	}

	@Override
	public void writeImpl(CUserSession client, CPacketWriteBuffer buffer) {
		if (_b != null && _b.length > 0) {
			buffer.writeBytes(_b); 
		}
	}

}