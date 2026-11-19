package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.ThreadPool;

public final class SC_Ping {
    private static final int SC_PING_REQ = 0x03E8;
    public static long _lastMs;
    public static boolean _isRun;

    private SC_Ping() {}

    public static boolean sendForGM(L1PcInstance pc) {
        _lastMs = System.currentTimeMillis();
        return send(pc, 1);
    }

    public static boolean send(L1PcInstance pc, int value) {
        return pc != null && send(pc.getNetConnection(), value);
    }

    public static boolean send(CIOClient client, int value) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(9);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(SC_PING_REQ);
            buffer.writeC(0x08);
            buffer.writeC(value);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static void reqForGM(L1PcInstance pc) {
        if (!_isRun) return;

        final long del = System.currentTimeMillis() - _lastMs;
        final String message = String.format("[PING CHECK] %dms.", del);

        System.out.println(message);
        SC_SystemMessage.send(pc, message);

        ThreadPool.getInstance().scheduleRequired(
            () -> sendForGM(pc),
            Math.max(0L, 1000L - del)
        );
    }
}
