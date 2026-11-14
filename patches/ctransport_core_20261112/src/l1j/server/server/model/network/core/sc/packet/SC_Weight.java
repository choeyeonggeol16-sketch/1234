package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_Weight {
    private SC_Weight() {}

    public static boolean send(CIOClient client, int weight100, int weight, int maxWeight) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(32);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeC(0xe5);
            buffer.writeC(0x01);
            buffer.writeC(0x08);
            buffer.writeC(weight100);
            buffer.writeC(0x10);
            write7B(buffer, weight);
            buffer.writeC(0x18);
            write7B(buffer, maxWeight);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int weight100, int weight, int maxWeight) {
        return pc != null && send(pc.getNetConnection(), weight100, weight, maxWeight);
    }
    public static boolean send(L1PcInstance pc) {
        if (pc == null || pc.getInventory() == null) return false;
        return send(pc, pc.getInventory().getWeight100(), pc.getInventory().getWeight(), pc.getMaxWeight());
    }

    private static void write7B(WriteBuffer buffer, int value) {
        while ((value & ~0x7F) != 0) {
            buffer.writeC((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buffer.writeC(value);
    }

}
