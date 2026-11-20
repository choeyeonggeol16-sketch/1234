package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CUserSession;

public final class SC_Sound {
    private static final int WIRE_SIZE = 2 + 4;

    private SC_Sound() {}


    public static boolean send(CUserSession client, int sound) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_SOUND_EFFECT);
            buffer.writeC(0);
            buffer.writeH(sound);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, int sound) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), sound);
    }

    public static void broadcast(L1Character anchor, int sound) {
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SOUND,
                Opcodes.S_SOUND_EFFECT, sound, 0, 0, 0, false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, int sound) {
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SOUND,
                Opcodes.S_SOUND_EFFECT, sound, 0, 0, 0, true, -1, null);
    }

    public static void sendAndBroadcastFast(L1Character anchor, int sound) {
        if (anchor == null) return;
        send(anchor, sound);
        broadcastFast(anchor, sound);
    }

}
