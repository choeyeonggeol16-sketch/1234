package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class SC_ShowCmd {
    private static final byte[] EMPTY = ProtoBroadcastFields.EMPTY;
    private static final int PK = 1, MOVIE = 2, TEXT = 3, PROTO8 = 4, PROTOA = 5;
    private SC_ShowCmd() {}

    public static byte[] encode(String text) {
        return ProtoBroadcastFields.encode(text);
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

    private static int frameSize(int mode, byte[] a, byte[] b, int sec) {
        if (a == null || b == null || a.length > CIOBufferPool.MAX_WIRE_BYTES
                || b.length > CIOBufferPool.MAX_WIRE_BYTES) return 0;
        final int aSize = ProtoBroadcastFields.bytesSize(a);
        final int size = switch (mode) {
            case PK -> 13 + aSize + ProtoBroadcastFields.bytesSize(b);
            case MOVIE -> 8 + aSize + ProtoBroadcastFields.varintSize(sec);
            case TEXT -> 9 + aSize;
            case PROTO8 -> 11;
            default -> 7;
        };
        return size <= CIOBufferPool.MAX_WIRE_BYTES ? size : 0;
    }

    public static NativeWireWriter nativePkWriter(byte[] killer, byte[] victim) {
        if (frameSize(PK, killer, victim, 0) == 0) return null;
        final byte[] killerName = killer.clone();
        final byte[] victimName = victim.clone();
        return out -> {
            out.put((byte) Opcodes.S_EXTENDED_PROTOBUF).putShort((short) 0x247)
                    .put((byte) 0x08).put((byte) 0);
            ProtoBroadcastFields.putBytes(out, 0x12, killerName);
            out.put((byte) 0x18).put((byte) 0);
            ProtoBroadcastFields.putBytes(out, 0x22, victimName);
            out.put((byte) 0x28).put((byte) 1).putShort((short) 0);
        };
    }


    private static boolean send(L1PcInstance pc, int mode, byte[] a, byte[] b, int sec, int opcode) {
        if (pc == null) return false;
        final CUserSocket connection = pc.getNetConnection();
        if (connection == null || !connection.isConnected() || !connection.isClientConnected()) return false;
        final int size = frameSize(mode, a, b, sec);
        if (size == 0) return false;
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble out = frame.buffer();
            out.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            out.writeH(mode == PK ? 0x247 : mode == MOVIE ? 0x245 : mode == TEXT ? 0x47 : opcode);
            switch (mode) {
            case PK:
                out.writeC(0x08); out.writeC(0);
                ProtoBroadcastFields.writeBytes(out, 0x12, a);
                out.writeC(0x18); out.writeC(0);
                ProtoBroadcastFields.writeBytes(out, 0x22, b);
                out.writeC(0x28); out.writeC(1); out.writeH(0);
                break;
            case MOVIE:
                ProtoBroadcastFields.writeBytes(out, 0x0a, a);
                out.writeC(0x10); out.writeBit(sec); out.writeH(0);
                break;
            case TEXT:
                out.writeC(0x08); out.writeC(1);
                ProtoBroadcastFields.writeBytes(out, 0x12, a);
                out.writeH(0);
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

}
