package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.animation.EActionCodes;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class SC_CharVisualUpdate {
    private SC_CharVisualUpdate() {}

    public static long pack(L1PcInstance pc) {
        if (pc == null || pc.isFishing() || pc.isFishingReady()) return 0L;
        return pack(pc, pc.isDead() ? EActionCodes.death.toInt() : pc.getCurrentWeapon());
    }

    public static long pack(L1Character subject, int status) {
        if (subject == null) return 0L;
        return ((long) subject.getId() << 32) | (status & 0xffL);
    }

    public static boolean sendPacked(L1PcInstance pc, long packed) {
        return pc != null && sendPacked(pc.getNetConnection(), packed);
    }

    public static boolean sendPacked(CUserSocket client, long packed) {
        if (packed == 0L || client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(12);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_WIELD);
            buffer.writeD((int) (packed >>> 32));
            buffer.writeC((int) packed);
            buffer.writeC(0xFF);
            buffer.writeC(0xFF);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static void broadcastPacked(L1Character anchor, long packed) {
        if (anchor == null || packed == 0L) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_CHARVISUALUPDATE, Opcodes.S_WIELD, (int)(packed >>> 32), (int)packed, 0, 0, false, -1, null);
    }

    public static void broadcastFastPacked(L1Character anchor, long packed) {
        if (anchor == null || packed == 0L) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_CHARVISUALUPDATE, Opcodes.S_WIELD, (int)(packed >>> 32), (int)packed, 0, 0, true, -1, null);
    }
}
