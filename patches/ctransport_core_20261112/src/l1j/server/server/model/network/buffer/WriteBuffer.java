package l1j.server.server.model.network.buffer;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


public class WriteBuffer {

    /*
     * Class-scoped allocation hint, not a permanent maximum.
     * The lower 16 bits hold the predicted wire size; the upper 16 bits
     * count consecutive successful small frames after a large one.
     * Only large-class recovery pays the contended CAS on small writes.
     */
    private static final int SMALL_WIRE_LIMIT = 16 * 1024;
    private static final int MAX_WIRE_LIMIT = 0xffff;
    private static final int SMALL_SUCCESS_TO_DEMOTE = 128;

    private static final ClassValue<PacketSizeState> PACKET_SIZE_CACHE =
            new ClassValue<>() {
                @Override
                protected PacketSizeState computeValue(Class<?> type) {
                    return new PacketSizeState();
                }
            };

    private static final class PacketSizeState {
        private final AtomicInteger sizing = new AtomicInteger();

        int get() {
            return sizing.get() & 0xffff;
        }

        void overflow(int required) {
            final int candidate = Math.min(required, MAX_WIRE_LIMIT);
            int old = sizing.get();
            for (;;) {
                final int current = old & 0xffff;
                // Capacity overflow is observed from a live packet. Reset
                // the decay window whenever large output is observed.
                if (candidate <= current && (old >>> 16) == 0) return;
                final int next = Math.max(current, candidate);
                if (sizing.compareAndSet(old, next)) return;
                old = sizing.get();
            }
        }

        void completed(int size) {
            if (size <= 0) return;
            final int candidate = Math.min(size, MAX_WIRE_LIMIT);
            int old = sizing.get();
            for (;;) {
                final int hint = old & 0xffff;
                final int next;
                if (candidate > SMALL_WIRE_LIMIT) {
                    next = Math.max(hint, candidate); // refresh large-class window
                } else if (hint <= SMALL_WIRE_LIMIT) {
                    if (candidate <= hint) return; // common small-class hot path
                    next = candidate;
                } else {
                    final int consecutive = old >>> 16;
                    next = consecutive + 1 >= SMALL_SUCCESS_TO_DEMOTE
                            ? candidate // route future small frames to 16K again
                            : ((consecutive + 1) << 16) | hint;
                }
                if (old == next || sizing.compareAndSet(old, next)) return;
                old = sizing.get();
            }
        }
    }

    /** Best-effort capacity hint for choosing the 16K or 64K SEND pool. */
    public static int learnedPacketSize(Class<?> packetClass) {
        return packetClass == null ? 0 : PACKET_SIZE_CACHE.get(packetClass).get();
    }

    private static final Charset MS949 = Charset.forName("MS949");

    private static final ThreadLocal<StringCodec> STRING_CODEC =
            ThreadLocal.withInitial(StringCodec::new);

    private static final class StringCodec {

        private final CharsetEncoder ms949 =
                MS949.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE);

        private char[] chars = new char[128];
        private CharBuffer charBuffer = CharBuffer.wrap(chars);

        private byte[] bytes = new byte[256];
        private ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

        CharBuffer prepareChars(String text) {
            final int length = text.length();

            if (length > chars.length) {
                int newSize = chars.length;
                while (newSize < length) {
                    final int next = newSize << 1;
                    if (next <= 0) {
                        newSize = length;
                        break;
                    }
                    newSize = next;
                }

                chars = new char[newSize];
                charBuffer = CharBuffer.wrap(chars);
            }

            text.getChars(0, length, chars, 0);

            charBuffer.clear();
            charBuffer.limit(length);
            return charBuffer;
        }

        ByteBuffer prepareBytes(int required) {
            if (required > bytes.length) {
                int newSize = bytes.length;
                while (newSize < required) {
                    final int next = newSize << 1;
                    if (next <= 0) {
                        newSize = required;
                        break;
                    }
                    newSize = next;
                }

                bytes = new byte[newSize];
                byteBuffer = ByteBuffer.wrap(bytes);
            }

            byteBuffer.clear();
            return byteBuffer;
        }
    }

    /**
     * Registered SEND region overflow sentinel.
     *
     * No temporary direct buffer and no memcpy fallback exists anymore.
     * ensureSize() records the newly required size, then throws this reusable
     * no-stack-trace sentinel so Client can retry the same packet on a 64K
     * registered slot without consuming/reordering it.
     */
    public static final class RegisteredCapacityExceededException
            extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private RegisteredCapacityExceededException() {
            super(
                    "registered SEND region capacity exceeded",
                    null,
                    false,
                    false);
        }
    }

    private static final RegisteredCapacityExceededException
            REGISTERED_CAPACITY_EXCEEDED =
                    new RegisteredCapacityExceededException();

    /*
     * 패킷마다 변경되는 값.
     * WriteBuffer wrapper 재사용을 위해 final이 아니다.
     */
    private Class<?> _packetClass;
    private PacketSizeState _packetSizeState;

    /*
     * Two-entry class-to-state lookup cache on each reusable writer.
     * Do not cache the size value: a stale local hint would prevent demotion
     * after a class returns from large to small packets.
     */
    private Class<?> _sizeCacheClass0;
    private PacketSizeState _sizeCacheState0;

    private Class<?> _sizeCacheClass1;
    private PacketSizeState _sizeCacheState1;

    /* State selected by Client before CIOUserSocket slot routing. */
    private Class<?> _preparedPacketClass;
    private PacketSizeState _preparedPacketSizeState;
    

    /*
     * 현재 packet backing buffer.
     *
     * registered external mode:
     *   _buf is a long-lived transport-owned direct view
     *   _base is the packet start inside that view
     *   _capacity is the writable logical capacity from _base
     *
     * This lets the NIO.2 transport serialize directly into pooled direct memory while all
     * packet code keeps using zero-based logical offsets.
     */
    private ByteBuffer _buf;
    private int _base;
    private int _capacity;
    private boolean _external;

    /*
     * One-shot registered backing prepared by CIOUserSocket before
     * WritablePacket.writeData() consumes this arm through beginPrepared().
     */
    private ByteBuffer _armedBuf;
    private int _armedBase;
    private int _armedCapacity;

    private int _position;
    private int _limit;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Client당 재사용되는 wrapper. 실제 backing은 RConnection이 arm한다. */
    public WriteBuffer() {
        _packetClass = null;
        _packetSizeState = null;
        _sizeCacheClass0 = null;
        _sizeCacheState0 = null;
        _sizeCacheClass1 = null;
        _sizeCacheState1 = null;
        _preparedPacketClass = null;
        _preparedPacketSizeState = null;
        _buf = null;
        _base = 0;
        _capacity = 0;
        _external = false;
        _armedBuf = null;
        _armedBase = 0;
        _armedCapacity = 0;
        _position = 0;
        _limit = 0;
    }

    // -------------------------------------------------------------------------
    // Reuse lifecycle
    // -------------------------------------------------------------------------

    /**
     * Client owner가 packet class를 한 번만 해석해서 CIOUserSocket routing과
     * reset()이 같은 PacketSizeState를 공유하게 한다.
     *
     * @return 현재 pooled-capacity hint, 0이면 미학습
     */
    public int preparePacketClass(Class<?> packetClass) {
        Objects.requireNonNull(packetClass, "packetClass");

        final PacketSizeState state =
                packetSizeStateCached(packetClass);

        final int learnedSize = state.get();

        _preparedPacketClass = packetClass;
        _preparedPacketSizeState = state;

        return learnedSize;
    }

    /**
     * Compatibility alias.
     *
     * 일부 Client/CIOUserSocket 버전은 preparePacket() 이름을 사용한다.
     * 실제 동작은 preparePacketClass()와 완전히 동일하게 유지한다.
     */
    public int preparePacket(Class<?> packetClass) {
        return preparePacketClass(packetClass);
    }

    private PacketSizeState packetSizeStateCached(Class<?> packetClass) {
        if (packetClass == _sizeCacheClass0) {
            return _sizeCacheState0;
        }

        if (packetClass == _sizeCacheClass1) {
            return _sizeCacheState1;
        }

        final PacketSizeState state = PACKET_SIZE_CACHE.get(packetClass);
        _sizeCacheClass1 = _sizeCacheClass0;
        _sizeCacheState1 = _sizeCacheState0;

        _sizeCacheClass0 = packetClass;
        _sizeCacheState0 = state;

        return state;
    }

    private void recordCompletedPacketSize(int value) {
        final PacketSizeState state = _packetSizeState;
        if (state != null) state.completed(value);
    }

    private void recordOverflowSize(int required) {
        final PacketSizeState state = _packetSizeState;
        if (state != null) state.overflow(required);
    }

    private void clearPreparedPacketClass() {
        _preparedPacketClass = null;
        _preparedPacketSizeState = null;
    }
    /** Owner hot path: consume prepared packet metadata/backing directly. */
    public void beginPrepared() {
        if (_buf != null) {
            throw new IllegalStateException(
                    "WriteBuffer still owns a backing buffer. detach() or releaseResources() was not called.");
        }

        final Class<?> packetClass = _preparedPacketClass;
        final PacketSizeState state = _preparedPacketSizeState;

        if (packetClass == null || state == null) {
            clearPreparedPacketClass();
            throw new IllegalStateException(
                    "WriteBuffer beginPrepared without preparePacketClass().");
        }

        _packetClass = packetClass;
        _packetSizeState = state;

        final ByteBuffer armed = _armedBuf;
        final int armedCapacity = _armedCapacity;

        if (armed == null || armedCapacity <= 0) {
            clearArm();
            clearPreparedPacketClass();
            throw new IllegalStateException(
                    "WriteBuffer beginPrepared without registered SEND backing.");
        }

        _buf = armed;
        _base = _armedBase;
        _capacity = armedCapacity;
        _external = true;

        /*
         * ByteBuffer position/limit are mutable shared state.
         * Previous direct/string/bulk writes may have narrowed the limit.
         * Every newly armed logical region must restore the physical view
         * before any absolute put/get is attempted.
         */
        _buf.limit(_base + _capacity);
        _buf.position(_base);

        clearArm();
        clearPreparedPacketClass();

        _position = 0;
        _limit = _capacity;
    }

    /**
     * 새로운 패킷 작성을 시작한다.
     * wrapper는 재사용하고 backing은 반드시 RConnection의 registered region을 사용한다.
     */
    public void reset(Class<?> packetClass) {
        Objects.requireNonNull(packetClass, "packetClass");

        if (_buf != null) {
            throw new IllegalStateException(
                    "WriteBuffer still owns a backing buffer. detach() or releaseResources() was not called.");
        }

        _packetClass = packetClass;

        final PacketSizeState state;
        if (packetClass == _preparedPacketClass
                && _preparedPacketSizeState != null) {

            state = _preparedPacketSizeState;
        } else {
            /* Compatibility path for callers that invoke reset() directly. */
            state = packetSizeStateCached(packetClass);
        }

        clearPreparedPacketClass();
        _packetSizeState = state;

        /*
         * Pooled transport: reset() must always consume an armed direct-buffer region
         * armed by CIOUserSocket. There is no heap/direct fallback backing.
         */
        final ByteBuffer armed =
                _armedBuf;

        final int armedCapacity =
                _armedCapacity;

        if (armed == null
                || armedCapacity <= 0) {

            clearArm();

            throw new IllegalStateException(
                    "WriteBuffer reset without registered SEND backing.");
        }

        _buf = armed;
        _base = _armedBase;
        _capacity = armedCapacity;
        _external = true;

        /*
         * Restore ByteBuffer mutable state for this newly armed region.
         * A previous packet may have left a narrower limit behind.
         */
        _buf.limit(_base + _capacity);
        _buf.position(_base);

        clearArm();

        _position = 0;
        _limit = _capacity;
    }

    /**
     * Prepare a transport-owned external region for the next reset().
     *
     * No slice/duplicate is created here; CIOUserSocket passes its one
     * per-connection registered SEND view.
     */
    public void armExternal(
            ByteBuffer buffer,
            int base,
            int capacity) {

        Objects.requireNonNull(
                buffer,
                "buffer");

        if (_buf != null) {
            throw new IllegalStateException(
                    "WriteBuffer already owns an active backing buffer.");
        }

        if (base < 0
                || capacity <= 0
                || base > buffer.capacity()
                || capacity
                        > buffer.capacity() - base) {

            throw new IllegalArgumentException(
                    "Invalid external region: base="
                            + base
                            + ", capacity="
                            + capacity
                            + ", bufferCapacity="
                            + buffer.capacity());
        }

        _armedBuf = buffer;
        _armedBase = base;
        _armedCapacity = capacity;
    }

    private void clearArm() {
        _armedBuf = null;
        _armedBase = 0;
        _armedCapacity = 0;
    }

    /**
     * 실제 ByteBuffer의 소유권이 Connection으로 넘어간 후 호출.
     * Registered backing ownership은 RConnection에 남긴 채 wrapper만 비운다.
     */
    public void detach() {
        recordPacketSize();

        _buf = null;
        _base = 0;
        _capacity = 0;
        _external = false;

        clearArm();
        clearPreparedPacketClass();

        _packetClass = null;
        _packetSizeState = null;
        _position = 0;
        _limit = 0;
    }

    public boolean isAttached() {
        return _buf != null;
    }

    public boolean isExternalBacking() {
        return _buf != null
                && _external;
    }

    public boolean isExternalBacking(
            ByteBuffer expected) {

        return _buf != null
                && _external
                && _buf == expected;
    }

    /**
     * Logical packet start inside getBuffer().
     */
    public int baseOffset() {
        return _base;
    }

    /**
     * Logical writable capacity for the current packet.
     */
    public int capacity() {
        return _capacity;
    }

    private void recordPacketSize() {
        /* Successful frame sizes drive the adaptive 16K/64K pool hint. */
        recordCompletedPacketSize(_limit);
    }

    // -------------------------------------------------------------------------
    // Capacity
    // -------------------------------------------------------------------------

    /**
     * Hot-path capacity guard.
     *
     * 정상 packet write에서는 _capacity 비교 한 번만 남긴다.
     * _buf null 검증, learned-size 갱신, sentinel throw는 overflow/오사용 때만
     * 들어가는 cold path로 분리한다. detach/release 상태는 _capacity=0이므로
     * 기존 "reset 없이 write" 오류도 그대로 slow path에서 검출된다.
     */
    private void ensureSize(int required) {
        if (required <= _capacity) {
            return;
        }

        ensureSizeSlow(required);
    }

    private void ensureSizeSlow(int required) {
        if (_buf == null) {
            throw new IllegalStateException(
                    "WriteBuffer is not initialized. reset() required.");
        }
        recordOverflowSize(required);

        throw REGISTERED_CAPACITY_EXCEEDED;
    }

    /**
     * MS949 문자열을 중간 byte[] 없이 현재 packet ByteBuffer에 직접 기록한다.
     *
     * @return 실제 기록된 byte 수
     */
    private int writeMs949Direct(String text, int trailingBytes) {
        final int start = _position;
        final StringCodec codec = STRING_CODEC.get();

        if ((long) start + ((long) text.length() << 1) + trailingBytes > _capacity) {
            final int actualBytes = encodeMs949Scratch(text, codec);
            ensureSize(start + actualBytes + trailingBytes);
            _buf.limit(_base + _capacity);
            _buf.position(_base + start);
            _buf.put(codec.bytes, 0, actualBytes);
            _position = start + actualBytes;
            return actualBytes;
        }

        final CharBuffer chars = codec.prepareChars(text);
        _buf.limit(_base + _capacity);
        _buf.position(_base + start);

        final CharsetEncoder encoder = codec.ms949;
        encoder.reset();

        CoderResult result = encoder.encode(chars, _buf, true);
        if (!result.isUnderflow()) {
            throw new IllegalStateException("MS949 encode failed: " + result);
        }

        result = encoder.flush(_buf);
        if (!result.isUnderflow()) {
            throw new IllegalStateException("MS949 flush failed: " + result);
        }

        _position = _buf.position() - _base;
        return _position - start;
    }

    /**
     * writeSS용 MS949 scratch encode.
     *
     * 매 호출마다 byte[]를 만들지 않고 ThreadLocal byte[]를 재사용한다.
     *
     * @return scratch에 기록된 MS949 byte 길이
     */
    private int encodeMs949Scratch(String text, StringCodec codec) {
        final CharBuffer chars = codec.prepareChars(text);
        final int maxBytes = text.length() << 1;
        final ByteBuffer target = codec.prepareBytes(maxBytes);

        final CharsetEncoder encoder = codec.ms949;
        encoder.reset();

        CoderResult result = encoder.encode(chars, target, true);
        if (!result.isUnderflow()) {
            throw new IllegalStateException("MS949 encode failed: " + result);
        }

        result = encoder.flush(target);
        if (!result.isUnderflow()) {
            throw new IllegalStateException("MS949 flush failed: " + result);
        }

        return target.position();
    }

    // -------------------------------------------------------------------------
    // Primitive writes
    // -------------------------------------------------------------------------

    /*
     * Primitive hot path:
     * DirectByteBuffer absolute put* already performs the final bounds check.
     * Do not perform the same _capacity comparison twice on every field.
     * Overflow is cold: convert the ByteBuffer bounds failure back into the
     * reusable registered-capacity sentinel so Client retry semantics stay intact.
     */
    public void writeByte(byte value) {
        final int position = _position;

        try {
            _buf.put(_base + position, value);
        } catch (IndexOutOfBoundsException e) {
            ensureSizeSlow(position + 1);
            throw e; // unreachable: ensureSizeSlow always throws
        }

        _position = position + 1;
    }

    public void writeByte(int value) { writeByte((byte) value); }
    public void writeByte(boolean value) { writeByte((byte) (value ? 1 : 0)); }

    public void writeShort(short value) {
        final int position = _position;

        try {
            _buf.putShort(_base + position, value);
        } catch (IndexOutOfBoundsException e) {
            ensureSizeSlow(position + 2);
            throw e; // unreachable: ensureSizeSlow always throws
        }

        _position = position + 2;
    }

    public void writeShort(int value) { writeShort((short) value); }
    public void writeShort(boolean value) { writeShort((short) (value ? 1 : 0)); }

    public void writeInt(int value) {
        final int position = _position;

        try {
            _buf.putInt(_base + position, value);
        } catch (IndexOutOfBoundsException e) {
            ensureSizeSlow(position + 4);
            throw e; // unreachable: ensureSizeSlow always throws
        }

        _position = position + 4;
    }

    public void writeInt(boolean value) { writeInt(value ? 1 : 0); }

    public void writeLong(long value) {
        final int position = _position;

        try {
            _buf.putLong(_base + position, value);
        } catch (IndexOutOfBoundsException e) {
            ensureSizeSlow(position + 8);
            throw e; // unreachable: ensureSizeSlow always throws
        }

        _position = position + 8;
    }

    public void writeFloat(float value) { writeInt(Float.floatToRawIntBits(value)); }
    public void writeDouble(double value) { writeLong(Double.doubleToRawLongBits(value)); }
    public void writeChar(char value) { writeShort((short) value); }

    // -------------------------------------------------------------------------
    // L1J primitive aliases
    // -------------------------------------------------------------------------

    public void writeC(int value) {
        final int position = _position;

        try {
            _buf.put(_base + position, (byte) (value & 0xff));
        } catch (IndexOutOfBoundsException e) {
            ensureSizeSlow(position + 1);
            throw e; // unreachable: ensureSizeSlow always throws
        }

        _position = position + 1;
    }

    public void writeH(int value) {
        final int position = _position;

        try {
            _buf.putShort(_base + position, (short) value);
        } catch (IndexOutOfBoundsException e) {
            ensureSizeSlow(position + 2);
            throw e; // unreachable: ensureSizeSlow always throws
        }

        _position = position + 2;
    }

    public void writeD(int value) {
        final int position = _position;

        try {
            _buf.putInt(_base + position, value);
        } catch (IndexOutOfBoundsException e) {
            ensureSizeSlow(position + 4);
            throw e; // unreachable: ensureSizeSlow always throws
        }

        _position = position + 4;
    }

    public void writeL(long value) { writeLong(value); }
    public void writeF(double value) { writeDouble(value); }
    public void writeP(int value) { writeByte((byte) value); }
    public void writeB(boolean b) { writeC(b ? 0x01 : 0x00); }
    public void writeB(Object o) { writeC(o != null ? 0x01 : 0x00); }

    // -------------------------------------------------------------------------
    // Bulk writes
    // -------------------------------------------------------------------------

    public void writeBytes(byte... value) {
        if (value == null || value.length == 0) return;
        
        ensureSize(_position + value.length);
        _buf.limit(_base + _capacity);
        _buf.position(_base + _position);
        _buf.put(value, 0, value.length);
        _position += value.length;
    }

    public void writeByte(byte[] text) {
        if (text != null && text.length > 0) {
            writeBytes(text);
        }
    }

    // -------------------------------------------------------------------------
    // L1J String Processing Methods
    // -------------------------------------------------------------------------

    /**
     * MS949 + NUL.
     *
     * 기존 String.getBytes("MS949")의 per-call byte[] allocation 제거.
     * 인코딩 결과를 현재 packet ByteBuffer에 직접 기록한다.
     */
    public void writeS(String text) {
        if (text != null && !text.isEmpty()) {
            writeMs949Direct(text, 1);
        }
        writeByte(0);
    }

    /**
     * 1-byte MS949 byte length + MS949 data.
     *
     * 길이 byte를 먼저 비워둔 뒤 직접 encode하고 실제 byte 길이를 back-patch한다.
     */
    public void writeS2(String text) {
        if (text == null || text.isEmpty()) {
            writeByte(0);
            return;
        }

        final int lengthIndex = _position;
        writeByte(0);

        final int dataStart = _position;
        writeMs949Direct(text, 0);

        final int encodedLength = _position - dataStart;

        if (encodedLength > 0xFF) {
            throw new IllegalStateException(
                    "writeS2 overflow: encoded=" + encodedLength
                            + " (max 255)");
        }

        _buf.put(_base + lengthIndex, (byte) encodedLength);
    }

    /**
     * UTF-16LE + 0x0000.
     *
     * String.getBytes("UTF-16LE") 없이 String의 UTF-16 code unit을
     * LITTLE_ENDIAN ByteBuffer에 바로 기록한다.
     */
    public void writeSU16(String text) {
        if (text != null && !text.isEmpty()) {
            final int length = text.length();

            ensureSize(_position + (length << 1) + 2);

            for (int i = 0; i < length; i++) {
                _buf.putChar(_base + _position, text.charAt(i));
                _position += 2;
            }
        } else {
            ensureSize(_position + 2);
        }

        _buf.putShort(_base + _position, (short) 0);
        _position += 2;
    }

    /**
     * L1J SS 형식.
     *
     * MS949 2-byte 문자는 byte 순서를 뒤집고,
     * ASCII는 [char][0]으로 기록한다.
     *
     * 변환용 MS949 byte[]는 ThreadLocal scratch를 재사용한다.
     *
     * Only 0x81..0xFE begin an MS949 double-byte sequence.
     * ASCII DEL (0x7f) and 0x80 are single-byte input units here;
     * never consume bytes beyond this conversion's encodedLength.
     */
    public void writeSS(String text) {
        if (text != null && !text.isEmpty()) {
            final StringCodec codec = STRING_CODEC.get();
            final int encodedLength = encodeMs949Scratch(text, codec);
            final byte[] bytes = codec.bytes;

            ensureSize(_position + (encodedLength << 1) + 2);

            int i = 0;
            while (i < encodedLength) {
                final byte first = bytes[i];

                final int lead = first & 0xff;
                if (lead >= 0x81 && lead <= 0xfe && i + 1 < encodedLength
                        && ((bytes[i + 1] & 0xff) >= 0x41)
                        && ((bytes[i + 1] & 0xff) <= 0xfe)
                        && ((bytes[i + 1] & 0xff) <= 0x5a
                            || ((bytes[i + 1] & 0xff) >= 0x61
                                && (bytes[i + 1] & 0xff) <= 0x7a)
                            || (bytes[i + 1] & 0xff) >= 0x81)) {
                    _buf.put(_base + _position++, bytes[i + 1]);
                    _buf.put(_base + _position++, first);
                    i += 2;
                } else {
                    _buf.put(_base + _position++, first);
                    _buf.put(_base + _position++, (byte) 0);
                    i++;
                }
            }
        } else {
            ensureSize(_position + 2);
        }

        _buf.putShort(_base + _position, (short) 0);
        _position += 2;
    }

    // -------------------------------------------------------------------------
    // Variable length encoding
    // -------------------------------------------------------------------------

    public void writeK(int value) {
        final int valueK = value / 128;

        if (valueK == 0) {
            writeByte(value);
        } else if (valueK <= 127) {
            writeByte((value & 0x7f) + 128);
            writeByte(valueK);
        } else if (valueK <= 16383) {
            writeByte((value & 0x7f) + 128);
            writeByte((valueK & 0x7f) + 128);
            writeByte(valueK / 128);
        } else if (valueK <= 2097151) {
            writeByte((value & 0x7f) + 128);
            writeByte((valueK & 0x7f) + 128);
            writeByte(((valueK / 128) & 0x7f) + 128);
            writeByte(valueK / 16384);
        } else {
            writeByte((value & 0x7f) + 128);
            writeByte((valueK & 0x7f) + 128);
            writeByte(((valueK / 128) & 0x7f) + 128);
            writeByte(((valueK / 16384) & 0x7f) + 128);
            writeByte(valueK / 2097152);
        }
    }

    public void write4bit(int value) {
        if (value <= 127) {
            writeByte(value & 0x7F);
        } else if (value <= 16383) {
            writeByte((value & 0x7F) | 0x80);
            writeByte((value >> 7) & 0x7F);
        } else if (value <= 2097151) {
            writeByte((value & 0x7F) | 0x80);
            writeByte(((value >> 7) & 0x7F) | 0x80);
            writeByte((value >> 14) & 0x7F);
        } else if (value <= 268435455) {
            writeByte((value & 0x7F) | 0x80);
            writeByte(((value >> 7) & 0x7F) | 0x80);
            writeByte(((value >> 14) & 0x7F) | 0x80);
            writeByte(((value >> 21) & 0x7F) | 0x80);
            writeByte((value >> 28) & 0x7F);
        } else if ((long) value <= 34359738367L) {
            writeByte((value & 0x7F) | 0x80);
            writeByte(((value >> 7) & 0x7F) | 0x80);
            writeByte(((value >> 14) & 0x7F) | 0x80);
            writeByte(((value >> 21) & 0x7F) | 0x80);
            writeByte((value >> 28) & 0x7F);
        }
    }

    public int size7B(int obj) {
        if (obj < 0) return 10;
        if (obj < (1 << 7)) return 1;
        if (obj < (1 << 14)) return 2;
        if (obj < (1 << 21)) return 3;
        if (obj < (1 << 28)) return 4;
        return 5;
    }

    public void writeBit(long value) {
        if (value < 0L) {
            value &= 0xFFFFFFFFL;
        }

        while ((value & ~0x7FL) != 0L) {
            writeByte((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeByte((int) value & 0x7F);
    }
    
    public void writePoint(int x, int y) {
        int pt = (y << 16) & 0xffff0000;
        pt |= (x & 0x0000ffff);
        writeBit(pt);
    }

    public int bitlengh(int obj) {
        return size7B(obj);
    }

    public void write7B(long value) {
        while ((value & ~0x7FL) != 0) {
            writeByte((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        writeByte((int) value & 0x7F);
    }

    // -------------------------------------------------------------------------
    // Indexed Writes and Reads
    // -------------------------------------------------------------------------

    public void writeByte(int index, byte value) {
        ensureSize(index + 1);
        _buf.put(_base + index, value);
    }

    public void writeShort(int index, short value) {
        ensureSize(index + 2);
        _buf.putShort(_base + index, value);
    }

    public void writeInt(int index, int value) {
        ensureSize(index + 4);
        _buf.putInt(_base + index, value);
    }

    public byte readByte(int index) { return _buf.get(_base + index); }
    public short readShort(int index) { return _buf.getShort(_base + index); }
    public int readInt(int index) { return _buf.getInt(_base + index); }

    // -------------------------------------------------------------------------
    // Position / Limit
    // -------------------------------------------------------------------------

    /** Only a committed transport-owned region may expand for FIFO SEND packing. */
    public void expandExternalForPacking(int capacity) {
        if (!_external || _buf == null || capacity < _capacity
                || capacity > _buf.capacity() - _base)
            throw new IllegalStateException("invalid external SEND pack capacity");
        _capacity = capacity;
        _buf.limit(_base + capacity);
    }

    public int position() { return _position; }
    public void position(int pos) { _position = pos; }
    public int limit() { return _limit; }
    
    public void limit(int newLimit) {
        ensureSize(newLimit);
        _limit = newLimit;
    }

    /**
     * Client owner hot path용 seal.
     *
     * writeData()가 끝난 뒤 position은 이미 모든 개별 write의 capacity 검사를
     * 통과한 값이다. 따라서 position을 다시 ensureSize() 할 필요 없이 실제
     * wire limit으로 확정한다.
     */
    public void sealToPosition() { _limit = _position; }

    /** Record completed wire size without detaching the asynchronous SEND buffer. */
    public void notePacketSize() { recordCompletedPacketSize(_position); }

    
    /**
     * 호환 유지용. Client가 commit 전 실제 position으로 다시 봉인하므로
     * 기존 packet 코드의 mark() 호출 여부는 transport 정확성에 영향이 없다.
     */
    public void mark() { _limit = _position; }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Release
    // -------------------------------------------------------------------------

    /**
     * 전송 실패/취소. Registered backing은 transport 소유이므로
     * wrapper 참조만 끊는다.
     */
    public void releaseResources() {
        _buf = null;
        _base = 0;
        _capacity = 0;
        _external = false;

        clearArm();
        clearPreparedPacketClass();

        _packetClass = null;
        _packetSizeState = null;
        _position = 0;
        _limit = 0;
    }
    
    public void beginDirect() {
        if (_buf != null) {
            throw new IllegalStateException(
                    "WriteBuffer still owns a backing buffer.");
        }

        final ByteBuffer armed = _armedBuf;
        final int armedCapacity = _armedCapacity;

        if (armed == null || armedCapacity <= 0) {
            clearArm();
            clearPreparedPacketClass();
            throw new IllegalStateException(
                    "WriteBuffer beginDirect without backing.");
        }

        _packetClass = null;
        _packetSizeState = null;

        _buf = armed;
        _base = _armedBase;
        _capacity = armedCapacity;
        _external = true;

        /*
         * Direct frames reuse the same backing ByteBuffer.
         * Never inherit position/limit left by the previous frame.
         */
        _buf.limit(_base + _capacity);
        _buf.position(_base);

        clearArm();
        clearPreparedPacketClass();

        _position = 0;
        _limit = _capacity;
    }

    /**
     * 암호화 코드 등 기존 접근용.
     */
    public ByteBuffer getBuffer() { return _buf; }
}