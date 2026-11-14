package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;

public final class SC_OnlyEffect {
    private SC_OnlyEffect() {}

    public static boolean send(L1Character character, int objectId, int effectId) {
        return character instanceof L1PcInstance && send((L1PcInstance) character, objectId, effectId);
    }

    public static boolean send(L1PcInstance pc, int objectId, int effectId) {
        return pc != null && send(pc.getNetConnection(), objectId, effectId);
    }

    public static boolean send(CIOClient client, int objectId, int effectId) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(9);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EFFECT);
            buffer.writeD(objectId);
            buffer.writeH(effectId);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static void broadcast(L1Character anchor, int objectId, int effectId) {
        if (anchor == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(anchor))
            send(pc, objectId, effectId);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int effectId) {
        if (anchor == null) return;
        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(anchor);
            try {
                for (L1PcInstance pc : __cioVisible0) send(pc, objectId, effectId);
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }
}
