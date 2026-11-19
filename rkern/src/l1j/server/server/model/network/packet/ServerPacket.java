package l1j.server.server.model.network.packet;

import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;

public abstract class ServerPacket extends WritablePacket<CIOClient> {
    @Override
    protected boolean write(CIOClient client, WriteBuffer buffer) {
        writeImpl(client, buffer);
        return true;
    }

    protected abstract void writeImpl(CIOClient client, WriteBuffer buffer);
}
