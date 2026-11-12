package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.Instance.L1ItemInstance;

public final class SC_ItemStatus {
    public static boolean send(L1PcInstance pc, L1ItemInstance item) {
        return pc != null && send(pc.getNetConnection(), item);
    }

    private SC_ItemStatus() {}

    public static boolean send(CIOClient client, l1j.server.server.model.Instance.L1ItemInstance item) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int objectId = item == null ? 0 : item.getId();
        final String rawName = item == null ? null : item.getViewName();
        final String name = rawName == null ? "" : rawName;
        final long count = item == null ? 0 : item.getCount();
        final byte[] status = item != null && item.isIdentified() ? item.getStatusBytes() : null;
        final CIOFrame frame = connection.beginDirectFrame(13 + (name.length() << 1) + (status == null ? 0 : status.length));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CHANGE_ITEM_DESC_EX);
            buffer.writeD(objectId);
            buffer.writeS(name);
            buffer.writeD((int) count);
            if (status == null) {
                buffer.writeC(0);
            } else {
                buffer.writeC(status.length);
                for (int i = 0; i < status.length; i++) buffer.writeC(status[i]);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }


}
