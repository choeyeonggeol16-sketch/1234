package l1j.server.server.model.network.buffer;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.InvalidMarkException;
import java.util.Objects;

/**
 * 패킷 수신용 ByteBuffer wrapper.
 */
public final class ReadBuffer {

    private ByteBuffer _buf;

    /** 원본 ByteBuffer에서 현재 payload가 시작되는 절대 offset. */
    private int _base;

    /** payload 기준 상대 position. */
    private int _position;

    /** payload 기준 상대 limit. */
    private int _limit;

    /** payload 기준 mark. */
    private int _mark = -1;

    public ReadBuffer() {
    }

    /**
     * 일반/호환 경로.
     * ByteOrder까지 여기서 확정한다.
     */
    public void reset(ByteBuffer buffer, int base, int length) {
        Objects.requireNonNull(buffer, "ReadBuffer buffer must not be null");

        if (base < 0 || length < 0 || base > buffer.capacity() - length) {
            throw new IndexOutOfBoundsException(
                    "base=" + base
                            + ", length=" + length
                            + ", capacity=" + buffer.capacity());
        }

        _buf = buffer;
        _buf.order(ByteOrder.LITTLE_ENDIAN);

        _base = base;
        _position = 0;
        _limit = length;
        _mark = -1;
    }

    /**
     * network completion hot path.
     *
     * RWorker registered RECV view와 ReadHandler scratch는 생성 시 이미
     * LITTLE_ENDIAN으로 고정되어 있고, base/length도 RConnection이 검증한
     * frame 범위에서만 들어온다. 패킷마다 Objects/order/bounds 검사를
     * 반복하지 않기 위한 trusted reset이다.
     */
    public void resetTrusted(ByteBuffer buffer, int base, int length) {
        _buf = buffer;
        _base = base;
        _position = 0;
        _limit = length;
        _mark = -1;
    }

    public boolean isValid() {
        return _buf != null;
    }

    // -------------------------------------------------------------------------
    // Sequential reads
    // -------------------------------------------------------------------------

    public char readChar() {
        require(2);
        final char value = _buf.getChar(_base + _position);
        _position += 2;
        return value;
    }

    public byte readByte() {
        require(1);
        return _buf.get(_base + _position++);
    }

    /** unsigned byte. */
    public int readUnsignedByte() {
        require(1);
        return _buf.get(_base + _position++) & 0xff;
    }

    public short readShort() {
        require(2);
        final short value = _buf.getShort(_base + _position);
        _position += 2;
        return value;
    }

    /** LITTLE_ENDIAN unsigned short. */
    public int readUnsignedShort() {
        require(2);
        final int value = _buf.getShort(_base + _position) & 0xffff;
        _position += 2;
        return value;
    }

    /**
     * LITTLE_ENDIAN unsigned 24-bit integer.
     */
    public int readUnsignedMedium() {
        require(3);
        final int absolute = _base + _position;
        final int value =
                (_buf.getShort(absolute) & 0xffff)
                        | ((_buf.get(absolute + 2) & 0xff) << 16);
        _position += 3;
        return value;
    }

    public int readInt() {
        require(4);
        final int value = _buf.getInt(_base + _position);
        _position += 4;
        return value;
    }

    public long readLong() {
        require(8);
        final long value = _buf.getLong(_base + _position);
        _position += 8;
        return value;
    }

    public float readFloat() {
        require(4);
        final float value = _buf.getFloat(_base + _position);
        _position += 4;
        return value;
    }

    public double readDouble() {
        require(8);
        final double value = _buf.getDouble(_base + _position);
        _position += 8;
        return value;
    }

    // -------------------------------------------------------------------------
    // Sequential bulk reads
    // -------------------------------------------------------------------------

    public byte[] readBytes(int length) {
        require(length);
        final byte[] dst = new byte[length];
        _buf.get(_base + _position, dst, 0, length);
        _position += length;
        return dst;
    }

    public void readBytes(byte[] dst) {
        Objects.requireNonNull(dst, "dst");
        readBytes(dst, 0, dst.length);
    }

    public void readBytes(byte[] dst, int offset, int length) {
        Objects.requireNonNull(dst, "dst");
        require(length);
        _buf.get(_base + _position, dst, offset, length);
        _position += length;
    }

    public int remaining() {
        return _limit - _position;
    }

    // -------------------------------------------------------------------------
    // Absolute access - payload 상대 index
    // -------------------------------------------------------------------------

    public byte readByte(int index) {
        checkIndex(index, 1);
        return _buf.get(_base + index);
    }

    public short readShort(int index) {
        checkIndex(index, 2);
        return _buf.getShort(_base + index);
    }

    public int readInt(int index) {
        checkIndex(index, 4);
        return _buf.getInt(_base + index);
    }

    public long readLong(int index) {
        checkIndex(index, 8);
        return _buf.getLong(_base + index);
    }

    /** Cryptor용 absolute bulk read. */
    public void readBytes(int index, byte[] dst, int offset, int length) {
        Objects.requireNonNull(dst, "dst");
        checkIndex(index, length);
        _buf.get(_base + index, dst, offset, length);
    }

    // -------------------------------------------------------------------------
    // Absolute writes - Cryptor용
    // -------------------------------------------------------------------------

    public void writeByte(int index, byte value) {
        checkIndex(index, 1);
        _buf.put(_base + index, value);
    }

    public void writeShort(int index, short value) {
        checkIndex(index, 2);
        _buf.putShort(_base + index, value);
    }

    public void writeInt(int index, int value) {
        checkIndex(index, 4);
        _buf.putInt(_base + index, value);
    }

    public void writeLong(int index, long value) {
        checkIndex(index, 8);
        _buf.putLong(_base + index, value);
    }

    /** Cryptor용 absolute bulk write. */
    public void writeBytes(int index, byte[] src, int offset, int length) {
        Objects.requireNonNull(src, "src");
        checkIndex(index, length);
        _buf.put(_base + index, src, offset, length);
    }

    // -------------------------------------------------------------------------
    // Buffer state - payload 상대 좌표
    // -------------------------------------------------------------------------

    public int limit() {
        return _limit;
    }

    public void limit(int newLimit) {
        if (newLimit < 0 || newLimit > _limit) {
            throw new IllegalArgumentException(
                    "newLimit=" + newLimit + ", limit=" + _limit);
        }

        _limit = newLimit;

        if (_position > newLimit) {
            _position = newLimit;
        }

        if (_mark > newLimit) {
            _mark = -1;
        }
    }

    public int position() {
        return _position;
    }

    public void position(int newPosition) {
        if (newPosition < 0 || newPosition > _limit) {
            throw new IllegalArgumentException(
                    "newPosition=" + newPosition + ", limit=" + _limit);
        }

        _position = newPosition;

        if (_mark > newPosition) {
            _mark = -1;
        }
    }

    public void mark() {
        _mark = _position;
    }

    /** mark 위치로 복원. */
    public void reset() {
        if (_mark < 0) {
            throw new InvalidMarkException();
        }
        _position = _mark;
    }

    public void resetMark() {
        reset();
    }

    // -------------------------------------------------------------------------
    // Internal bounds
    // -------------------------------------------------------------------------

    private void require(int bytes) {
        if (bytes < 0 || bytes > _limit - _position) {
            throw new BufferUnderflowException();
        }
    }

    private void checkIndex(int index, int bytes) {
        if (index < 0 || bytes < 0 || index > _limit - bytes) {
            throw new IndexOutOfBoundsException(
                    "index=" + index
                            + ", bytes=" + bytes
                            + ", limit=" + _limit);
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void clear() {
        _buf = null;
        _base = 0;
        _position = 0;
        _limit = 0;
        _mark = -1;
    }
}
