package l1j.server.server.model.network.packet;

import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;

public abstract class CServerPacket extends IPacketWriter<CUserSocket> {
    @Override
    protected boolean write(CUserSocket client, CPacketWriteBuffer buffer) {
        writeImpl(client, buffer);
        return true;
    }

    protected abstract void writeImpl(CUserSocket client, CPacketWriteBuffer buffer);
}
