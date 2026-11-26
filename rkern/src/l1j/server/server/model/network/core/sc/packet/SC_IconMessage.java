package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoMsg;
import l1j.server.server.shared.SimpleRgb;

public final class SC_IconMessage {
    private static final int INDEX_MESSAGE = 0x67;
    private static final SimpleRgb GM_COLOR = new SimpleRgb(249, 68, 253);

    private SC_IconMessage() {}

    public static NativeWireWriter nativeGmWriter(String message) {
        return nativeTextWriter(message, GM_COLOR, 28, 60);
    }

    private static NativeWireWriter nativeTextWriter(String message, SimpleRgb color, int surface, int duration) {
        return nativeWriter(ProtoBroadcastFields.encode(message), 0, color, surface, duration, false);
    }

    public static NativeWireWriter nativeIndexWriter(int index, SimpleRgb color, int surface, int duration) {
        return nativeWriter(null, index, color, surface, duration, true);
    }

    private static NativeWireWriter nativeWriter(byte[] text, int index, SimpleRgb color,
                                                int surface, int duration, boolean indexMode) {
        final byte[] rgb = color == null ? ProtoBroadcastFields.EMPTY : color.get_bytes().clone();
        if (!indexMode && text == null) return null;
        final int wireSurface = zigZag(surface);
        final int wireDuration = indexMode ? duration : zigZag(duration);
        if (frameSize(text, index, rgb, wireSurface, wireDuration, indexMode)
                > CIOBufferPool.MAX_WIRE_BYTES) return null;
        return out -> {
            out.put((byte) Opcodes.S_EXTENDED_PROTOBUF)
                    .putShort((short) (indexMode ? INDEX_MESSAGE : ProtoMsg.SC_NOTIFICATION_MESSAGE_NOT))
                    .put((byte) 0x08);
            ProtoBroadcastFields.putVarint(out, wireSurface);
            if (indexMode) {
                out.put((byte) 0x10);
                ProtoBroadcastFields.putVarint(out, index * 2);
            } else {
                ProtoBroadcastFields.putBytes(out, 0x12, text);
            }
            ProtoBroadcastFields.putBytes(out, 0x1a, rgb);
            out.put((byte) 0x20);
            ProtoBroadcastFields.putVarint(out, wireDuration);
            out.putShort((short) 0);
        };
    }

    public static boolean sendGm(L1PcInstance pc, String message) {
        return send(pc, message, GM_COLOR, 28, 60);
    }

    public static boolean send(L1PcInstance pc, String message, SimpleRgb color, int surface, int duration) {
        return sendInternal(pc, message, 0, color, surface, duration, false);
    }

    public static boolean send(L1PcInstance pc, int index, SimpleRgb color, int surface, int duration) {
        return sendInternal(pc, null, index, color, surface, duration, true);
    }

    public static void broadcast(String message, SimpleRgb color, int surface, int duration) {
        CSharedBroadcast.sendWrittenAll(nativeTextWriter(message, color, surface, duration));
    }

    public static void broadcast(int index, SimpleRgb color, int surface, int duration) {
        CSharedBroadcast.sendWrittenAll(nativeIndexWriter(index, color, surface, duration));
    }

    private static int zigZag(int value) {
        return (value << 1) ^ (value >> 31);
    }

    private static int frameSize(byte[] text, int index, byte[] rgb,
                                 int wireSurface, int wireDuration, boolean indexMode) {
        return 9 + ProtoBroadcastFields.varintSize(wireSurface) + ProtoBroadcastFields.varintSize(wireDuration)
                + (indexMode ? 1 + ProtoBroadcastFields.varintSize(index * 2) : ProtoBroadcastFields.bytesSize(text))
                + ProtoBroadcastFields.bytesSize(rgb);
    }

    private static boolean sendInternal(L1PcInstance pc, String message, int index, SimpleRgb color,
                                        int surface, int duration, boolean indexMode) {
        if (pc == null) return false;
        final CUserSocket connection = pc.getNetConnection();
        if (connection == null || !connection.isConnected() || !connection.isClientConnected()) return false;
        final byte[] text = indexMode ? null : ProtoBroadcastFields.encode(message);
        if (!indexMode && text == null) return false;
        final byte[] rgb = color == null ? ProtoBroadcastFields.EMPTY : color.get_bytes();
        final int wireSurface = zigZag(surface);
        final int wireDuration = indexMode ? duration : zigZag(duration);
        final int size = frameSize(text, index, rgb, wireSurface, wireDuration, indexMode);
        if (size > CIOBufferPool.MAX_WIRE_BYTES) return false;
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeH(indexMode ? INDEX_MESSAGE : ProtoMsg.SC_NOTIFICATION_MESSAGE_NOT);
            buffer.writeC(0x08);
            buffer.writeBit(wireSurface);
            if (indexMode) {
                buffer.writeC(0x10);
                buffer.writeBit(index * 2);
            } else {
                ProtoBroadcastFields.writeBytes(buffer, 0x12, text);
            }
            ProtoBroadcastFields.writeBytes(buffer, 0x1a, rgb);
            buffer.writeC(0x20);
            buffer.writeBit(wireDuration);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
