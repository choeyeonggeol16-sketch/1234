package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;

public final class SC_Light {

    private static final int WIRE_SIZE =
            ConnectionConfig.HEADER_SIZE
                    + 1
                    + Integer.BYTES
                    + 1;

    private SC_Light() {
    }

    public static boolean send(
            CIOClient client,
            int objectId,
            int type) {

        if (client == null || !client.isConnected()) {
            return false;
        }

        final CUserSocket connection =
                client.getConnection();

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

        final WriteBuffer buffer =
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

        if (cha == null) {
            return;
        }

        final int objectId =
                cha.getId();

        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(cha);
            try {
                for (L1PcInstance pc : __cioVisible0) {
        
                    final CIOClient client =
                            pc.getNetConnection();
        
                    if (client == null
                            || !client.isConnected()) {
                        continue;
                    }
        
                    send(
                            client,
                            objectId,
                            type);
                }
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }
}