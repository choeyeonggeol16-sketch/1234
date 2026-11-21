package l1j.server.server.model.network.packethelper;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.packet.CServerPacket;

public class S_ChangeAttr extends CServerPacket {

	private final int _x;
	private final int _y;
	private final boolean _isWidth;
	private final boolean _isClose;

	public static S_ChangeAttr get(L1Object obj, boolean isWidth, boolean isClose) {
		return get(obj.getX(), obj.getY(), isWidth, isClose);
	}

	public static S_ChangeAttr get(int x, int y, boolean isWidth, boolean isClose) {
		return new S_ChangeAttr(x, y, isWidth, isClose);
	}

	private S_ChangeAttr(int x, int y, boolean isWidth, boolean isClose) {
		_x = x;
		_y = y;
		_isWidth = isWidth;
		_isClose = isClose;
	}

	@Override
	public void writeImpl(CUserSocket client, CPacketWriteBuffer buffer) {
		buffer.writeC(Opcodes.S_CHANGE_ATTR);
		buffer.writeH(_x);
		buffer.writeH(_y);
		buffer.writeB(_isWidth);
		buffer.writeB(_isClose);
	}

}