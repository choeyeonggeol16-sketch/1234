package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_ShowCmd {
    private static final Charset MS949 = Charset.forName("MS949");
    private static final byte[] EMPTY = new byte[0];
    private static final int PK = 1, MOVIE = 2, TEXT = 3, PROTO8 = 4, PROTOA = 5;
    private SC_ShowCmd() {}

    public static byte[] encode(String text) {
        return text == null || text.isEmpty() ? EMPTY : text.getBytes(MS949);
    }

    public static boolean sendPk(L1PcInstance pc, byte[] killer, byte[] victim) {
        return send(pc, PK, killer, victim, 0, 0);
    }

    public static boolean sendMovie(L1PcInstance pc, String url, int sec) {
        return send(pc, MOVIE, encode(url), EMPTY, sec, 0);
    }

    public static boolean sendText(L1PcInstance pc, String message) {
        return send(pc, TEXT, encode(message), EMPTY, 0, 0);
    }

    public static boolean sendProto8(L1PcInstance pc, int opcode) {
        return send(pc, PROTO8, EMPTY, EMPTY, 0, opcode);
    }

    public static boolean sendProtoA(L1PcInstance pc, int opcode) {
        return send(pc, PROTOA, EMPTY, EMPTY, 0, opcode);
    }

    private static int bitLen(long value) {
        if (value < 0) value &= 0xFFFFFFFFL;
        int length = 1;
        while ((value & ~0x7FL) != 0L) { ++length; value >>>= 7; }
        return length;
    }

    private static boolean send(L1PcInstance pc, int mode, byte[] a, byte[] b, int sec, int opcode) {
        if (pc == null || a.length > 255 || b.length > 255) return false;
        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int size;
        switch (mode) {
        case PK: size = 17 + a.length + b.length; break;
        case MOVIE: size = 10 + a.length + bitLen(sec); break;
        case TEXT: size = 11 + a.length; break;
        case PROTO8: size = 11; break;
        default: size = 7; break;
        }
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer out = frame.buffer();
            out.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            out.writeH(mode == PK ? 0x247 : mode == MOVIE ? 0x245 : mode == TEXT ? 0x47 : opcode);
            switch (mode) {
            case PK:
                out.writeC(0x08); out.writeC(0); out.writeC(0x12); writeS2(out, a);
                out.writeC(0x18); out.writeC(0); out.writeC(0x22); writeS2(out, b);
                out.writeC(0x28); out.writeC(1); out.writeH(0);
                break;
            case MOVIE:
                out.writeC(0x0A); writeS2(out, a);
                out.writeC(0x10); out.writeBit(sec); out.writeH(0);
                break;
            case TEXT:
                out.writeC(0x08); out.writeC(1); out.writeC(0x12); writeS2(out, a); out.writeH(0);
                break;
            case PROTO8:
                out.writeC(0x08); out.writeC(1); out.writeC(0x10); out.writeC(0); out.writeH(0);
                break;
            default:
                out.writeC(0x0A); out.writeC(0);
                break;
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static void writeS2(WriteBuffer out, byte[] bytes) {
        out.writeC(bytes.length);
        out.writeByte(bytes);
    }
}
