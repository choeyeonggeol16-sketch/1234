package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_Dexup {
    private static final int WIRE_SIZE = 11;

    private SC_Dexup() {}

    public static boolean send(CUserSocket client, L1PcInstance actor, int type, int time) {
        if (actor == null) return false;
        final int totalDex = actor.getAbility() == null ? 0 : actor.getAbility().getTotalDex();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_MAGE_DEXTERITY);
            buffer.writeH(time);
            buffer.writeC(totalDex);
            buffer.writeC(type);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1Character target, L1PcInstance actor, int type, int time) {
        return target instanceof L1PcInstance && send(((L1PcInstance) target).getNetConnection(), actor, type, time);
    }

    public static void broadcast(L1Character anchor, L1PcInstance actor, int type, int time) {
        if(anchor==null)return;
        if(actor == null)return;
        final int dex=actor.getAbility()==null?0:actor.getAbility().getTotalDex();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_DEXUP, Opcodes.S_MAGE_DEXTERITY, time, dex, type, 0, false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, L1PcInstance actor, int type, int time) {
        if(anchor==null)return;
        if(actor == null)return;
        final int dex=actor.getAbility()==null?0:actor.getAbility().getTotalDex();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_DEXUP, Opcodes.S_MAGE_DEXTERITY, time, dex, type, 0, true, -1, null);
    }

}
