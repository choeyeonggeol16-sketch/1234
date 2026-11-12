package l1j.server.server.model.network.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import l1j.server.server.model.network.buffer.ReadBuffer;
import l1j.server.server.model.network.buffer.WriteBuffer;

/**********************************
 * Network Server Cryptor
 **********************************/
public class Cryptor {

    private static final long U32_MASK = 0xFFFF_FFFFL;
    private static final int DK1_DELTA = 679411651;

    public int overPending;

    private long _dk0;
    private long _dk1;

    private final int[] _clntHash;

    public Cryptor() {
        _clntHash = new int[256];
    }

    public void initKey(long seed) {
        final long dk0 = Integer.toUnsignedLong(
                Integer.rotateRight(
                        (int) (seed ^ 0x9C30D539L),
                        13));

        final long dk1 = 0x930FD7E2L ^ dk0 ^ 0x7C72E993L;

        _dk0 = dk0;
        _dk1 = dk1;

        initEncHash(dk0, dk1);
    }

    private void initEncHash(long dk0, long dk1) {
        int k = 0;
        final int[] hash = _clntHash;

        for (int i = 255; i >= 0; i--) {
            hash[i] = i;
        }

        for (int i = 0; i < 256; i++) {
            final int keyIndex = i & 7;
            final int keyByte;

            if (keyIndex < 4) {
                keyByte = (int) (dk0 >>> (keyIndex << 3)) & 0xFF;
            } else {
                keyByte = (int) (dk1 >>> ((keyIndex - 4) << 3)) & 0xFF;
            }

            k = (hash[i] + k + keyByte) & 0xFF;

            final int tmp = hash[k];
            hash[k] = hash[i];
            hash[i] = tmp;
        }
    }

    /**
     * Peek the first decrypted payload byte without modifying cipher state or
     * the wire buffer. A full decrypt must still advance the cipher exactly once.
     * The NIO.2 READ pump serializes these accesses for each connection.
     */
    public int peekOpcode(ReadBuffer buffer, int offset, int size) {
        if (size < 8) return -1;
        final int c3 = (buffer.readByte(offset + 3) & 0xff)
                ^ (int) ((_dk0 >>> 16) & 0xffL);
        return ((buffer.readByte(offset + 4) & 0xff)
                ^ (int) (_dk1 & 0xffL) ^ c3) & 0xff;
    }

    /** Serialized READ view: preview without creating/resetting a ReadBuffer. */
    public int peekOpcode(ByteBuffer wire, int start, int size) {
        if (size < 8) return -1;
        final int c3 = (wire.get(start + 3) & 0xff)
                ^ (int) ((_dk0 >>> 16) & 0xffL);
        return ((wire.get(start + 4) & 0xff)
                ^ (int) (_dk1 & 0xffL) ^ c3) & 0xff;
    }

    /** Non-mutating skill-ID preview for C_USE_SPELL. The two bytes after the
     * opcode use the same rolling XOR and key bytes as decrypt(), without
     * advancing the session cipher or copying the encrypted frame.
     */
    public int peekSkillId(ByteBuffer wire, int start, int size) {
        if (size < 8) return -1;
        final long key = _dk1;
        final int c4 = wire.get(start + 4) & 0xFF;
        final int c5 = wire.get(start + 5) & 0xFF;
        final int c6 = wire.get(start + 6) & 0xFF;
        final int first = c5 ^ c4 ^ ((int) (key >>> 8) & 0xFF);
        final int second = c6 ^ c5 ^ ((int) (key >>> 16) & 0xFF);
        return first * 8 + second + 1;
    }

    // ------------------------------------------------------------------
    // Decrypt
    // ------------------------------------------------------------------

    /**
     * 패킷 복호화.
     *
     * encrypted size N -> plaintext logical size N - 4.
     * 마지막 4바이트는 0으로 지우며 ReadHandler가 limit을 N - 4로 줄인다.
     */
    public void decrypt(ReadBuffer buffer, int offset, int size) {
        if (size < 8) {
            throw new IllegalArgumentException("Invalid encrypted packet size: " + size);
        }

        final long dk0 = _dk0;
        final long dk1 = _dk1;

        final int db0 = (int) dk0 & 0xFF;
        final int db1 = (int) (dk0 >>> 8) & 0xFF;
        final int db2 = (int) (dk0 >>> 16) & 0xFF;
        final int db3 = (int) (dk0 >>> 24) & 0xFF;

        final int db4 = (int) dk1 & 0xFF;
        final int db5 = (int) (dk1 >>> 8) & 0xFF;
        final int db6 = (int) (dk1 >>> 16) & 0xFF;
        final int db7 = (int) (dk1 >>> 24) & 0xFF;


        int c3 = (buffer.readByte(offset + 3) & 0xFF) ^ db2;

        final long keyMask =
                (dk1 & U32_MASK)
                        | ((dk0 & U32_MASK) << 32);

        int i = 4;
        final int wordEnd = 4 + ((size - 4) & ~7);

        while (i < wordEnd) {
            final long raw = buffer.readLong(offset + i);

            final long decoded =
                    raw
                            ^ (raw << 8)
                            ^ keyMask
                            ^ (c3 & 0xFFL);

            buffer.writeLong(offset + i - 4, decoded);

            c3 = (int) (raw >>> 56) & 0xFF;
            i += 8;
        }

        /* 0~7 byte tail. */
        final int remaining = size - i;
        final int src = offset + i;
        final int dst = src - 4;

        int current;

        if (remaining > 0) {
            current = buffer.readByte(src) & 0xFF;
            buffer.writeByte(dst, (byte) (current ^ db4 ^ c3));
            c3 = current;
        }
        if (remaining > 1) {
            current = buffer.readByte(src + 1) & 0xFF;
            buffer.writeByte(dst + 1, (byte) (current ^ db5 ^ c3));
            c3 = current;
        }
        if (remaining > 2) {
            current = buffer.readByte(src + 2) & 0xFF;
            buffer.writeByte(dst + 2, (byte) (current ^ db6 ^ c3));
            c3 = current;
        }
        if (remaining > 3) {
            current = buffer.readByte(src + 3) & 0xFF;
            buffer.writeByte(dst + 3, (byte) (current ^ db7 ^ c3));
            c3 = current;
        }
        if (remaining > 4) {
            current = buffer.readByte(src + 4) & 0xFF;
            buffer.writeByte(dst + 4, (byte) (current ^ db0 ^ c3));
            c3 = current;
        }
        if (remaining > 5) {
            current = buffer.readByte(src + 5) & 0xFF;
            buffer.writeByte(dst + 5, (byte) (current ^ db1 ^ c3));
            c3 = current;
        }
        if (remaining > 6) {
            current = buffer.readByte(src + 6) & 0xFF;
            buffer.writeByte(dst + 6, (byte) (current ^ db2 ^ c3));
        }

        final long plainHead = buffer.readInt(offset) & U32_MASK;

        _dk0 = (dk0 ^ plainHead) & U32_MASK;
        _dk1 = Integer.toUnsignedLong((int) dk1 + DK1_DELTA);

        buffer.writeInt(offset + size - 4, 0);
    }

    // ------------------------------------------------------------------
    // Encrypt
    // ------------------------------------------------------------------

    /**
     * 송신 암호화.
     */
    public void encrypt(WriteBuffer wb, int offset, int size) {
        if (size <= 0) {
            return;
        }

        final ByteBuffer bb = wb.getBuffer();
        final int base = wb.baseOffset();
        final int[] hash = _clntHash;
        if (hash.length < 256) throw new IllegalStateException("Invalid cipher state");
        int k = 0;
        int j = 1;
        int pos = base + offset;
        final int end = pos + size;
        final int wordEnd = pos + (size & ~7);
        int hj = hash[j];

        // 8-byte action frame: keep its 2-byte wire length untouched, while
        // applying the six RC4 keystream bytes with one 64-bit read/write.
        // The pooled SEND buffer is LITTLE_ENDIAN. Other buffer orders and
        // offsets retain the original bytewise path.
        if (size == 6 && offset >= 2 && bb.order() == ByteOrder.LITTLE_ENDIAN) {
            long mask = 0L;
            int nextJ, nextHJ, hk;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long) hash[(hj + hk) & 255]) << 0;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long) hash[(hj + hk) & 255]) << 8;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long) hash[(hj + hk) & 255]) << 16;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long) hash[(hj + hk) & 255]) << 24;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long) hash[(hj + hk) & 255]) << 32;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long) hash[(hj + hk) & 255]) << 40;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            final int frame = pos - 2;
            bb.putLong(frame, bb.getLong(frame) ^ (mask << 16));
            return;
        }
        while (pos < wordEnd) {
            long mask = 0L;
            int nextJ, nextHJ, hk;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long)hash[(hj + hk) & 255]) << 0;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long)hash[(hj + hk) & 255]) << 8;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long)hash[(hj + hk) & 255]) << 16;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long)hash[(hj + hk) & 255]) << 24;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long)hash[(hj + hk) & 255]) << 32;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long)hash[(hj + hk) & 255]) << 40;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long)hash[(hj + hk) & 255]) << 48;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            nextJ = (j + 1) & 255;
            nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            mask |= ((long)hash[(hj + hk) & 255]) << 56;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
            bb.putLong(pos, bb.getLong(pos) ^ mask);
            pos += 8;
        }
        while (pos < end) {
            int nextJ = (j + 1) & 255;
            int nextHJ = hash[nextJ];
            k = (k + hj) & 255;
            int hk = hash[k];
            hash[k] = hj;
            hash[j] = hk;
            bb.put(pos, (byte)(bb.get(pos) ^ hash[(hj + hk) & 255]));
            pos++;
            j = nextJ;
            hj = (k == j) ? hj : nextHJ;
        }

    }
}
