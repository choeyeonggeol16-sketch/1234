package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1ItemInstance;

public final class SC_TradeAddItem {
    public static boolean send(L1PcInstance pc, L1ItemInstance item, int count, int type) {
        return pc != null && send(pc.getNetConnection(), item, item == null ? "" : item.getNumberedViewName(count), count, type);
    }

    private SC_TradeAddItem() {}

    public static boolean send(CIOClient client, L1ItemInstance item, String name, int count, int type) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final String actualName = name == null ? "" : name;
        final int gfxId = item == null || item.getItem() == null ? 0 : item.getItem().getGfxId();
        final boolean identified = item == null || item.isIdentified();
        final int bless = item == null ? 1 : item.getBless();
        final byte[] raw = item == null ? null : item.getStatusBytes();
        final byte[] status = raw;
        final CIOFrame frame = connection.beginDirectFrame(11 + (actualName.length() << 1) + (identified && status != null ? status.length : 0));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_ADD_XCHG);
            buffer.writeC(type);
            buffer.writeH(gfxId);
            buffer.writeS(actualName);
            if (!identified) {
                buffer.writeH(0x03);
            } else {
                buffer.writeC(bless);
                buffer.writeC(status == null ? 0 : status.length);
                if (status != null) for (int i = 0; i < status.length; i++) buffer.writeC(status[i]);
            }
            buffer.writeH(0x07);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, L1ItemInstance item, String name, int count, int type) {
        return pc != null && send(pc.getNetConnection(), item, name, count, type);
    }

    public static boolean send(CIOClient client, L1ItemInstance item, int count, int type) {
        return send(client, item, item == null ? "" : item.getNumberedViewName(count), count, type);
    }
}
