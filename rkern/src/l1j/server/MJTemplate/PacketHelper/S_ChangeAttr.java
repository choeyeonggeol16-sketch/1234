package l1j.server.MJTemplate.PacketHelper;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.session.CIOClient;

public class S_ChangeAttr extends ServerPacket {

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
	public void writeImpl(CIOClient client, WriteBuffer buffer) {
		buffer.writeC(Opcodes.S_CHANGE_ATTR);
		buffer.writeH(_x);
		buffer.writeH(_y);
		buffer.writeB(_isWidth);
		buffer.writeB(_isClose);
	}

}