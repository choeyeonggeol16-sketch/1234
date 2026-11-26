package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;

public final class SC_GameTime {
    private SC_GameTime() {}

    public static boolean send(CUserSocket client, long gameTime) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(7);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_TIME);
            buffer.writeD((int)gameTime);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, long gameTime) {
        return pc != null && send(pc.getNetConnection(), gameTime);
    }

    public static boolean send(L1PcInstance pc) {
        int time = 0;
        try {
            time = L1GameTimeClock.getInstance().getGameTime().getSeconds();
        } catch (Exception ignored) {}
        return send(pc, time);
    }

}
