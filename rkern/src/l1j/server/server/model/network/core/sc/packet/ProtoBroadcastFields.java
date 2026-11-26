package l1j.server.server.model.network.core.sc.packet;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CIOBufferPool;

/** The field layout shared by the direct and fanout protobuf encoders. */
final class ProtoBroadcastFields {
    static final byte[] EMPTY = new byte[0];
    private static final Charset MS949 = Charset.forName("MS949");

    private ProtoBroadcastFields() {}

    static byte[] encode(String text) {
        if (text == null || text.isEmpty()) return EMPTY;
        if (text.length() > CIOBufferPool.MAX_WIRE_BYTES * 2) return null;
        final byte[] encoded = text.getBytes(MS949);
        return encoded.length <= CIOBufferPool.MAX_WIRE_BYTES ? encoded : null;
    }

    static int varintSize(int value) {
        return Math.max(1, (32 - Integer.numberOfLeadingZeros(value) + 6) / 7);
    }

    static int bytesSize(byte[] value) {
        return value == null ? 0 : 1 + varintSize(value.length) + value.length;
    }

    static void putVarint(ByteBuffer out, int value) {
        while ((value & ~0x7f) != 0) {
            out.put((byte) ((value & 0x7f) | 0x80));
            value >>>= 7;
        }
        out.put((byte) value);
    }

    static void putBytes(ByteBuffer out, int tag, byte[] value) {
        if (value == null) return;
        out.put((byte) tag);
        putVarint(out, value.length);
        out.put(value);
    }

    static void writeBytes(Assemble out, int tag, byte[] value) {
        if (value == null) return;
        out.writeC(tag);
        out.writeBit(value.length);
        out.writeBytes(value);
    }
}
