package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.Instance.L1ItemInstance;

public final class SC_ItemName {
    private SC_ItemName() {}

    public static boolean send(CIOClient client, int itemId, String name) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(8 + (name == null ? 0 : (name.length() << 1)));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_CHANGE_ITEM_DESC);
            buffer.writeD(itemId);
            buffer.writeS(name == null ? "" : name);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int itemId, String name) {
        return pc != null && send(pc.getNetConnection(), itemId, name);
    }

    public static boolean send(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null) return false;
        return send(pc.getNetConnection(), item == null ? 0 : item.getId(),
                item == null || item.getViewName() == null ? "" : item.getViewName());
    }
}
