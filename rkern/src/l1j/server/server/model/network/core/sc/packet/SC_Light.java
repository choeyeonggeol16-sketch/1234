package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.config.CIOConfig;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class SC_Light {

    private static final int WIRE_SIZE =
            CIOConfig.HEADER_SIZE
                    + 1
                    + Integer.BYTES
                    + 1;

    private SC_Light() {
    }

    public static boolean send(
            CUserSocket client,
            int objectId,
            int type) {

        if (client == null || !client.isConnected()) {
            return false;
        }

        final CUserSocket connection =
                client;

        if (connection == null
                || !connection.isClientConnected()) {
            return false;
        }


        final CIOFrame frame =
                connection.beginDirectFrame(
                        WIRE_SIZE);

        if (frame == null) {
            return false;
        }

        final Assemble buffer =
                frame.buffer();

        boolean committed = false;

        try {
            buffer.writeC(Opcodes.S_CHANGE_LIGHT);
            buffer.writeD(objectId);
            buffer.writeC(type);

            committed =
                    connection.commitDirectFrame(
                            frame);

            return committed;

        } finally {
            if (!committed) {
                connection.abortDirectFrame(frame);
            }
        }
    }

    public static void broadcast(
            L1Character cha,
            int type) {
        if (cha == null) return;
        final int objectId = cha.getId();
        CSharedBroadcast.sendFormatted(
                cha, CSharedBroadcast.FORMAT_LIGHT,
                Opcodes.S_CHANGE_LIGHT, objectId, type, 0, 0, true, -1, null);
    }
}