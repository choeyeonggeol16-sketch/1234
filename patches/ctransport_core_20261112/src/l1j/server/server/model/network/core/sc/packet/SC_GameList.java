package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.L1Racing;

public final class SC_GameList {

    private SC_GameList() {}

    public static boolean send(CIOClient client, int rank) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final L1Racing racing = L1Racing.getInstance();
        int size = 0;
        String[] names = new String[0];
        try {
            if (racing != null) {
                size = racing.size(0);
                names = new String[size];
                for (int i = 0; i < size; i++) {
                    L1PcInstance racer = racing.arrayList(0).get(i);
                    names[i] = racer == null || racer.getName() == null ? "" : racer.getName();
                }
            }
        } catch (Exception ignored) {
            names = new String[0];
            size = 0;
        }
        int nameBytes = 0;
        for (int i = 0; i < size; i++) nameBytes += (names[i].length() << 1) + 1;
        final CIOFrame frame = connection.beginDirectFrame(8 + nameBytes);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EVENT);
            buffer.writeC(0x42);
            buffer.writeH(size);
            buffer.writeH(rank);
            for (int i = 0; i < size; i++) buffer.writeS(names[i]);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int rank) {
        return pc != null && send(pc.getNetConnection(), rank);
    }

}
