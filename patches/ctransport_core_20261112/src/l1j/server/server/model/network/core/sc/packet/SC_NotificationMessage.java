package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;
import l1j.server.MJTemplate.MJSimpleRgb;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_NotificationMessage {
    public static final int DISPLAY_POSITION_TOP = 1;
    public static final int DISPLAY_POSITION_MIDDLE = 2;
    public static final int DISPLAY_POSITION_BOTTOM = 3;
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_NotificationMessage() {}

    private static int bitSize(long n) {
        if (n < 0) n &= 0xFFFFFFFFL;
        int size = 1;
        while ((n & ~0x7FL) != 0L) { ++size; n >>>= 7; }
        return size;
    }

    public static boolean send(L1PcInstance pc, int position, String message,
                               MJSimpleRgb rgb, int durationSecond) {
        return send(pc, position, message, rgb == null ? null : rgb.get_bytes(), durationSecond);
    }

    public static boolean send(L1PcInstance pc, int position, String message,
                               byte[] rgb, int durationSecond) {
        if (pc == null) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final byte[] msg = (message == null ? "" : message).getBytes(MS949);
        if (msg.length > 255) return false;
        final int rgbSize = rgb == null ? 0 : rgb.length;
        final long size = 13L + msg.length + rgbSize + bitSize(durationSecond);
        if (size > Integer.MAX_VALUE) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer out = frame.buffer();
            out.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            out.writeH(0x013C);
            out.writeC(0x08);
            out.writeC(position);
            out.writeC(0x12);
            out.writeC(msg.length);
            out.writeByte(msg);
            out.writeC(0x1A);
            if (rgbSize > 0) out.writeByte(rgb);
            out.writeBit(0x20);
            out.writeBit(durationSecond);
            out.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
