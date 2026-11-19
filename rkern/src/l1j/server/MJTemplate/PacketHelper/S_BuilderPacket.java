package l1j.server.MJTemplate.PacketHelper;

import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.session.CIOClient;

public class S_BuilderPacket extends ServerPacket {
	
	private final byte[] _b;

	public S_BuilderPacket(byte[] b) {
		_b = b;
	}

	@Override
	public void writeImpl(CIOClient client, WriteBuffer buffer) {
		if (_b != null && _b.length > 0) {
			buffer.writeBytes(_b); 
		}
	}

}