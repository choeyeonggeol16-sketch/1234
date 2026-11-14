package l1j.server.server.packets.Protocol;

import java.nio.charset.Charset;

/**
 * 프로토 수신 파서 — CS 패킷 body 읽기용.
 *
 * 인스턴스는 단일 frame 전용.
 * thread-safe 아님.
 */
public final class ProtoReader {

    private static final Charset MS949 =
            Charset.forName("MS949");

    private final byte[] _a;

    /** 현재 읽기 위치 */
    private int _p;

    /** frame body 끝 위치 */
    private final int _end;

    /** frame body 시작 위치 */
    private final int _origin;

    public ProtoReader(
            byte[] data,
            int offset,
            int length) {

        if (data == null) {
            throw new NullPointerException("data");
        }

        if (offset < 0
                || length < 0
                || offset > data.length
                || length > data.length - offset) {

            throw new IndexOutOfBoundsException(
                    "offset=" + offset
                    + ", length=" + length
                    + ", capacity=" + data.length);
        }

        _a = data;
        _p = offset;
        _origin = offset;
        _end = offset + length;
    }

    // ------------------------------------------------------------------
    // 상태
    // ------------------------------------------------------------------

    public boolean isAtEnd() {
        return _p >= _end;
    }

    public int remaining() {
        return _end - _p;
    }

    // ------------------------------------------------------------------
    // Varint
    // ------------------------------------------------------------------

    public long varint64() {
        long value = 0L;
        // The first nine bytes contribute bits 0..62.
        for (int shift = 0; shift < 63; shift += 7) {
            if (_p >= _end) throw malformed("truncated varint");
            final int b = _a[_p++] & 0xFF;
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        // Validate byte ten before shifting; never consume a following field.
        if (_p >= _end) throw malformed("truncated varint64");
        final int last = _a[_p++] & 0xFF;
        if ((last & 0xFE) != 0) throw malformed("varint64 overflow");
        return value | ((long) last << 63);
    }

    public int varint32() {
        return (int) varint64();
    }

    /**
     * protobuf tag 전체.
     * fieldNumber << 3 | wireType
     */
    public int tag() {
        final long value = varint64();
        // A protobuf tag is uint32 and field numbers start at one.
        if (value <= 0L || value > 0xFFFF_FFFFL || (value >>> 3) == 0L) {
            throw malformed("invalid tag: " + value);
        }
        return (int) value;
    }

    /**
     * stream 끝이면 0.
     */
    public int next() {
        return isAtEnd()
                ? 0
                : tag();
    }

    // ------------------------------------------------------------------
    // Skip
    // ------------------------------------------------------------------

    public void skip(int wire) {

        switch (wire) {

        case 0:
            varint64();
            return;

        case 1:
            require(8);
            _p += 8;
            return;

        case 2: {
            final int len =
                    readLength();

            require(len);
            _p += len;
            return;
        }

        case 5:
            require(4);
            _p += 4;
            return;

        default:
            throw malformed(
                    "bad wire type: " + wire);
        }
    }

    // ------------------------------------------------------------------
    // Embedded message
    // ------------------------------------------------------------------

    public ProtoReader subReader() {

        final int len =
                readLength();

        require(len);

        final ProtoReader out =
                new ProtoReader(
                        _a,
                        _p,
                        len);

        _p += len;

        return out;
    }

    // ------------------------------------------------------------------
    // LEN fields
    // ------------------------------------------------------------------

    public byte[] readBytes() {

        final int len =
                readLength();

        require(len);

        final byte[] out =
                new byte[len];

        System.arraycopy(
                _a,
                _p,
                out,
                0,
                len);

        _p += len;

        return out;
    }

    /**
     * MS949 문자열.
     *
     * 중간 byte[] 생성 없이 원본 frame 배열에서 직접 decode.
     */
    public String readString() {

        final int len =
                readLength();

        require(len);

        final String out =
                new String(
                        _a,
                        _p,
                        len,
                        MS949);

        _p += len;

        return out;
    }

    /**
     * protobuf LEN varint.
     *
     * length는 Java array/index 특성상 int 범위까지만 허용.
     */
    private int readLength() {

        final long len =
                varint64();

        if (len < 0L
                || len > Integer.MAX_VALUE) {

            throw malformed(
                    "invalid length: " + len);
        }

        return (int) len;
    }

    private void require(int length) {

        if (length < 0
                || length > _end - _p) {

            throw malformed(
                    "truncated field"
                    + " need=" + length
                    + " remaining=" + (_end - _p));
        }
    }

    private IllegalStateException malformed(
            String reason) {

        return new IllegalStateException(
                "[ProtoReader] "
                + reason
                + " | pos=" + (_p - _origin)
                + " | length=" + (_end - _origin));
    }

    // ------------------------------------------------------------------
    // Raw adapter access
    // ------------------------------------------------------------------

    /** 원본 backing array */
    public byte[] array() {
        return _a;
    }

    /** body 시작 절대 offset */
    public int origin() {
        return _origin;
    }

    /** body 전체 길이 */
    public int frameLength() {
        return _end - _origin;
    }
}