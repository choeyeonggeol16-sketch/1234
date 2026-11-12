package l1j.server.server.model.network.core.sc.packet;

import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.server.Opcodes;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_IconMessage {
    private static final int TEXT_MESSAGE = 0x40;
    private static final MJSimpleRgb GM_COLOR = new MJSimpleRgb(249, 68, 253);
    private static final int INDEX_MESSAGE = 0x67;

    private SC_IconMessage() {}

    public static boolean sendGm(L1PcInstance pc, String message) {
        return send(pc, message, GM_COLOR, 28, 60);
    }

    public static boolean send(L1PcInstance pc, String message, MJSimpleRgb color, int surface, int duration) {
        return sendInternal(pc, message, 0, color, surface, duration, false);
    }

    public static boolean send(L1PcInstance pc, int index, MJSimpleRgb color, int surface, int duration) {
        return sendInternal(pc, "", index, color, surface, duration, true);
    }

    public static void broadcast(String message, MJSimpleRgb color, int surface, int duration) {
        for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
            send(pc, message, color, surface, duration);
        }
    }

    public static void broadcast(int index, MJSimpleRgb color, int surface, int duration) {
        for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
            send(pc, index, color, surface, duration);
        }
    }

    private static boolean sendInternal(L1PcInstance pc, String message, int index, MJSimpleRgb color,
                                        int surface, int duration, boolean indexMode) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final String msg = message == null ? "" : message;
        final byte[] rgb = color == null ? null : color.get_bytes();
        final long needed = 64L + (indexMode ? 0L : ((long) msg.length() << 2))
                + (rgb == null ? 0 : rgb.length);
        if (needed > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) needed);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writePayload(frame.buffer(), msg, index, rgb, surface, duration, indexMode);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static void writePayload(WriteBuffer buffer, String msg, int index, byte[] rgb,
                                     int surface, int duration, boolean indexMode) {
        buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
        buffer.writeH(indexMode ? INDEX_MESSAGE : TEXT_MESSAGE);
        if (indexMode) {
            buffer.writeC(0x08);
            buffer.writeBit(surface * 2);
            buffer.writeC(0x10);
            buffer.writeBit(index * 2);
            buffer.writeC(0x1A);
            if (rgb != null) buffer.writeByte(rgb);
            buffer.writeC(0x20);
            buffer.writeBit(duration);
        } else {
            buffer.writeBit(0x08);
            buffer.writeBit(surface * 2);
            buffer.writeBit(0x12);
            buffer.writeS2(msg);
            buffer.writeBit(0x1A);
            if (rgb != null) buffer.writeByte(rgb);
            buffer.writeBit(0x20);
            buffer.writeBit(duration);
        }
        buffer.writeH(0);
    }
}
