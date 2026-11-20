package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_Strup {
    private static final int WIRE_SIZE = 12;

    private SC_Strup() {}

    public static boolean send(CUserSocket client, L1PcInstance actor, int type, int time) {
        if (actor == null || actor.getAbility() == null || actor.getInventory() == null) return false;
        final int totalStr = actor.getAbility().getTotalStr();
        final int weight100 = actor.getInventory().getWeight100();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_MAGE_STRENGTH);
            buffer.writeH(time);
            buffer.writeC(totalStr);
            buffer.writeC(weight100);
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
        if(actor == null || actor.getAbility()==null || actor.getInventory()==null)return;
        final int totalStr=actor.getAbility().getTotalStr();
        final int weight100=actor.getInventory().getWeight100();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_STRUP, Opcodes.S_MAGE_STRENGTH, time, totalStr, weight100, type, false, -1, null);
    }

    public static void broadcastFast(L1Character anchor, L1PcInstance actor, int type, int time) {
        if(anchor==null)return;
        if(actor == null || actor.getAbility()==null || actor.getInventory()==null)return;
        final int totalStr=actor.getAbility().getTotalStr();
        final int weight100=actor.getInventory().getWeight100();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_STRUP, Opcodes.S_MAGE_STRENGTH, time, totalStr, weight100, type, true, -1, null);
    }

}
