package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_EffectLocation {
    private static final int WIRE_SIZE = 2 + 8;

    private SC_EffectLocation() {
    }

    public static boolean send(CIOClient client, int x, int y, int gfxId) {
        if (client == null || !client.isConnected()) {
            return false;
        }
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) {
            return false;
        }
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) {
            return false;
        }
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EFFECT_LOC);
            buffer.writeH(x);
            buffer.writeH(y);
            buffer.writeH(gfxId);
            buffer.writeC(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) {
                connection.abortDirectFrame(frame);
            }
        }
    }

    public static boolean send(L1PcInstance pc, int x, int y, int gfxId) {
        return pc != null && send(pc.getNetConnection(), x, y, gfxId);
    }

    public static void broadcast(L1Character anchor, int x, int y, int gfxId) {
        if (anchor == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(anchor)) {
            send(pc, x, y, gfxId);
        }
    }

    public static long pack(int x, int y, int gfxId) {
        return ((long) (x & 0xffff) << 32) | ((long) (y & 0xffff) << 16) | (gfxId & 0xffffL);
    }

    public static long pack(l1j.server.server.model.L1Location loc, int gfxId) {
        return pack(loc == null ? 0 : loc.getX(), loc == null ? 0 : loc.getY(), gfxId);
    }

    public static int locX(l1j.server.server.model.L1Location loc) { return loc == null ? 0 : loc.getX(); }
    public static int locY(l1j.server.server.model.L1Location loc) { return loc == null ? 0 : loc.getY(); }

    public static void sendPacked(l1j.server.server.model.L1Character target, long packet) {
        if (target instanceof L1PcInstance) {
            send((L1PcInstance) target, (int) (packet >> 32) & 0xffff,
                    (int) (packet >> 16) & 0xffff, (int) packet & 0xffff);
        }
    }

    public static void send(l1j.server.server.model.L1Character pc, int x, int y, int gfxId) {
        if (pc instanceof L1PcInstance) send((L1PcInstance) pc, x, y, gfxId);
    }

    public static void broadcastPacked(l1j.server.server.model.L1Character c, long p) {
        broadcast(c, (int) (p >> 32) & 0xffff, (int) (p >> 16) & 0xffff, (int) p & 0xffff);
    }

    public static void broadcastFast(l1j.server.server.model.L1Character c, int x, int y, int gfxId) {
        if (c == null) return;
        {
            final L1World __cioWorld0 = L1World.getInstance();
            final java.util.List<L1PcInstance> __cioVisible0 = __cioWorld0.borrowVisiblePlayerFast(c);
            try {
                for (L1PcInstance pc : __cioVisible0) send(pc, x, y, gfxId);
            } finally {
                __cioWorld0.releaseVisiblePlayerFast(__cioVisible0);
            }
        }
    }

    public static void broadcastFastPacked(l1j.server.server.model.L1Character c, long p) {
        broadcastFast(c, (int) (p >> 32) & 0xffff, (int) (p >> 16) & 0xffff, (int) p & 0xffff);
    }

    public static void broadcastM(l1j.server.server.model.L1Character c, int x, int y, int gfxId) {
        if (c == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(c)) {
            if (pc.getLinMeff() != 2) send(pc, x, y, gfxId);
        }
    }

    public static void broadcastMPacked(l1j.server.server.model.L1Character c, long p) {
        broadcastM(c, (int) (p >> 32) & 0xffff, (int) (p >> 16) & 0xffff, (int) p & 0xffff);
    }

    public static void broadcastWide(l1j.server.server.model.L1Character c, int x, int y, int gfxId) {
        if (c == null) return;
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayer(c, 50)) send(pc, x, y, gfxId);
    }

    public static void broadcastWidePacked(l1j.server.server.model.L1Character c, long p) {
        broadcastWide(c, (int) (p >> 32) & 0xffff, (int) (p >> 16) & 0xffff, (int) p & 0xffff);
    }

    public static void sendAndBroadcast(l1j.server.server.model.L1Character c, int x, int y, int gfxId) {
        send(c, x, y, gfxId); broadcast(c, x, y, gfxId);
    }

    public static void sendAndBroadcastPacked(l1j.server.server.model.L1Character c, long p) {
        sendAndBroadcast(c, (int) (p >> 32) & 0xffff, (int) (p >> 16) & 0xffff, (int) p & 0xffff);
    }
}
