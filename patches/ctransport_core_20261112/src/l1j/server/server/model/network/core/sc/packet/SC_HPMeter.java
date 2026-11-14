package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_HPMeter {
    private SC_HPMeter() {}

    public static boolean send(CIOClient client, int objId, int hpRatio, int mpRatio) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(11);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_HIT_RATIO);
            buffer.writeD(objId);
            buffer.writeC(hpRatio);
            buffer.writeC(mpRatio);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int objId, int hpRatio, int mpRatio) {
        return pc != null && send(pc.getNetConnection(), objId, hpRatio, mpRatio);
    }

    public static boolean send(L1PcInstance pc, L1Character cha) {
        if (pc == null) return false;
        int objectId = 0, hpRatio = 0, mpRatio = 0;
        if (cha != null) {
            objectId = cha.getId();
            try {
                int maxHp = cha.getMaxHp();
                if (maxHp > 0) hpRatio = (int) ((100.0 / maxHp) * cha.getCurrentHp());
                int maxMp = cha.getMaxMp();
                if (maxMp > 0) mpRatio = (int) ((100.0 / maxMp) * cha.getCurrentMp());
            } catch (Exception ignored) {}
        }
        return send(pc, objectId, hpRatio, mpRatio);
    }

    public static void broadcast(L1Character anchor, L1Character cha) {
        if (anchor == null) return;
        int objectId = 0, hpRatio = 0, mpRatio = 0;
        if (cha != null) {
            objectId = cha.getId();
            try {
                int maxHp = cha.getMaxHp();
                if (maxHp > 0) hpRatio = (int) ((100.0 / maxHp) * cha.getCurrentHp());
                int maxMp = cha.getMaxMp();
                if (maxMp > 0) mpRatio = (int) ((100.0 / maxMp) * cha.getCurrentMp());
            } catch (Exception ignored) {}
        }
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(anchor))
            send(pc, objectId, hpRatio, mpRatio);
    }

}
