package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.L1Buddy;
import l1j.server.server.model.L1World;

public final class SC_Buddy {
    private SC_Buddy() {}
    private static final class BuddyNode {
        final byte[] name;
        final boolean online;
        BuddyNode(byte[] name, boolean online) {
            this.name = name;
            this.online = online;
        }
    }

    public static boolean send(CIOClient client, L1Buddy buddy) {
        if (client == null || !client.isConnected()) return false;
        final List<BuddyNode> rows = new ArrayList<>();
        int size = 9;
        if (buddy != null && buddy.getBuddy() != null) {
            synchronized (buddy.getBuddy()) {
                for (String name : buddy.getBuddy().values()) {
                    if (name == null) continue;
                    final byte[] bytes;
                    try { bytes = name.getBytes("MS949"); }
                    catch (Exception e) { continue; }
                    rows.add(new BuddyNode(bytes, L1World.getInstance().getPlayer(name) != null));
                    size += 8 + bytes.length;
                }
            }
        }
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeD(0x01080151);
            for (int i = 0, n = rows.size(); i < n; ++i) {
                final BuddyNode row = rows.get(i);
                final int len = row.name.length;
                buffer.writeC(0x12);
                buffer.writeC(len + 6);
                buffer.writeC(0x0a);
                buffer.writeC(len);
                buffer.writeByte(row.name);
                buffer.writeC(0x10);
                buffer.writeC(row.online ? 1 : 0);
                buffer.writeH(0x001a);
            }
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    public static boolean send(L1PcInstance pc, L1Buddy buddy) {
        return pc != null && send(pc.getNetConnection(), buddy);
    }

}
