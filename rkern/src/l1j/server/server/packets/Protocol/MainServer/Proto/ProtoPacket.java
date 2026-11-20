package l1j.server.server.packets.Protocol.MainServer.Proto;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.model.network.core.connection.CUserSocket;

public abstract class ProtoPacket extends CServerPacket {

	public abstract int msgId();
	protected abstract void body(ProtoWriter w);

	@Override
	protected final void writeImpl(CUserSocket client, CPacketWriteBuffer buffer) {
		writeTo(buffer, true);
	}

	final void writeTo(CPacketWriteBuffer buffer, boolean trailer) {
		buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
		buffer.writeH(msgId());

		final ProtoWriter w = ProtoWriter.direct(buffer);

		try {
			body(w);
			w.finishDirect(trailer);
		} finally {
			w.detachDirect();
		}
	}

	/** 캐시가 필요한 경우에만 byte[] 생성 */
	public final byte[] encode() {
		final ProtoWriter w = ProtoWriter.tl();
		body(w);
		return w.finishWithTrailer(msgId());
	}

}