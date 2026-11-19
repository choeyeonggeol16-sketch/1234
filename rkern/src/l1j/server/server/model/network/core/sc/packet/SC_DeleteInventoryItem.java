package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_DeleteInventoryItem {
    private SC_DeleteInventoryItem() {}

    public static boolean send(CIOClient client, int objectId) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(7);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_REMOVE_INVENTORY);
            buffer.writeD(objectId);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int objectId) {
        return pc != null && send(pc.getNetConnection(), objectId);
    }

    public static boolean send(L1PcInstance pc, L1ItemInstance item) {
        if (item == null || (item.getBless() >= 128 && !Config.봉인템삭제)) return false;
        return send(pc, item.getId());
    }

}
