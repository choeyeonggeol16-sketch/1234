package l1j.server.server.model.network.packet;

import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.session.CUserSession;

public abstract class CServerPacket extends IPacketWriter<CUserSession> {
    @Override
    protected boolean write(CUserSession client, CPacketWriteBuffer buffer) {
        writeImpl(client, buffer);
        return true;
    }

    protected abstract void writeImpl(CUserSession client, CPacketWriteBuffer buffer);
}
