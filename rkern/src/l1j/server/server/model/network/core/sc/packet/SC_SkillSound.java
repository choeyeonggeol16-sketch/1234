package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;

public final class SC_SkillSound {
    private static final int WIRE_SIZE = 2 + 13;

    private SC_SkillSound() {
    }

    public static boolean send(CUserSocket client, int objectId, int gfxId) {
        if (client == null || !client.isConnected()) {
            return false;
        }
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) {
            return false;
        }
        final CIOFrame frame = connection.beginDirectFrame(WIRE_SIZE);
        if (frame == null) {
            return false;
        }
        boolean committed = false;
        try {
            final CPacketWriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EFFECT);
            buffer.writeD(objectId);
            buffer.writeH(gfxId);
            buffer.writeH(0);
            buffer.writeD(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) {
                connection.abortDirectFrame(frame);
            }
        }
    }

    public static boolean send(L1PcInstance pc, int objectId, int gfxId) {
        return pc != null && send(pc.getNetConnection(), objectId, gfxId);
    }

    public static void broadcast(L1Character anchor, int objectId, int gfxId) {
        broadcast0(anchor, objectId, gfxId, false);
    }

    public static void broadcastFast(L1Character anchor, int objectId, int gfxId) {
        broadcast0(anchor, objectId, gfxId, true);
    }


    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter nativeWriter(int objectId,int gfxId) {
        return b -> b.put((byte)Opcodes.S_EFFECT).putInt(objectId)
            .putShort((short)gfxId).putShort((short)0).putInt(0);
    }

    private static void broadcast0(L1Character anchor, int objectId, int gfxId, boolean fast) {
        if (anchor != null) l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SKILL_SOUND,
                Opcodes.S_EFFECT, objectId, gfxId, 0, 0, fast, -1, null);
    }

    public static boolean send(l1j.server.server.model.L1Character target, int objectId, int gfxId) {
        return target instanceof L1PcInstance && send((L1PcInstance) target, objectId, gfxId);
    }

    public static long pack(int objectId, int gfxId) {
        return ((long) objectId << 32) | (gfxId & 0xffffL);
    }

    public static long pack(int objectId, int gfxId, int ignoredAid) {
        return pack(objectId, gfxId);
    }

    public static void sendPacked(l1j.server.server.model.L1Character pc, long packet) {
        send(pc, (int) (packet >> 32), (int) packet & 0xffff);
    }

    public static void sendAndBroadcast(l1j.server.server.model.L1Character c, int objectId, int gfxId) {
        send(c, objectId, gfxId);
        broadcast(c, objectId, gfxId);
    }

    public static void sendAndBroadcast(l1j.server.server.model.L1Character c, int gfxId) {
        if (c != null) sendAndBroadcast(c, c.getId(), gfxId);
    }

    public static void sendAndBroadcastPacked(l1j.server.server.model.L1Character c, long p) {
        sendAndBroadcast(c, (int) (p >> 32), (int) p & 0xffff);
    }

    public static void broadcastPacked(l1j.server.server.model.L1Character c, long p) {
        broadcast(c, (int) (p >> 32), (int) p & 0xffff);
    }

    public static void broadcastFastPacked(l1j.server.server.model.L1Character c, long p) {
        broadcastFast(c, (int) (p >> 32), (int) p & 0xffff);
    }

    public static void broadcastM(l1j.server.server.model.L1Character c, int objectId, int gfxId) {
        if (c == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                c, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SKILL_SOUND,
                Opcodes.S_EFFECT, objectId, gfxId, 0, 0, false, -1, pc -> pc.getLinMeff() != 2);
    }

    public static void broadcastMPacked(l1j.server.server.model.L1Character c, long p) {
        broadcastM(c, (int) (p >> 32), (int) p & 0xffff);
    }

    public static void broadcastWide(l1j.server.server.model.L1Character c, int objectId, int gfxId) {
        if (c == null) return;
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                c, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SKILL_SOUND,
                Opcodes.S_EFFECT, objectId, gfxId, 0, 0, false, 50, null);
    }

    public static void broadcastWidePacked(l1j.server.server.model.L1Character c, long p) {
        broadcastWide(c, (int) (p >> 32), (int) p & 0xffff);
    }
}
