package l1j.server.server.packets.Protocol;

import java.nio.charset.Charset;
import java.util.Arrays;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CUserSocket;

public final class ProtoWriter {

	private static final Charset MS949 = Charset.forName("MS949");
	private static final int INITIAL_CAPACITY = 256, INITIAL_PATCH_DEPTH = 8, MAX_RETAINED_CAPACITY = 256 * 1024;

	private static final ThreadLocal<ProtoWriter> TL = ThreadLocal.withInitial(ProtoWriter::new);
	private static final ThreadLocal<ProtoWriter> DIRECT_TL = ThreadLocal.withInitial(ProtoWriter::new);

	private byte[] _a;
	private int _p;
	private int[] _patch;
	private int _patchN;

	/* direct WriteBuffer mode */
	private WriteBuffer _out;
	private int _base;
    private ProtoWriter _nestedDirect;
    CIOFrame directFrame;
    CUserSocket directConnection;

	public ProtoWriter() {
		_a = new byte[INITIAL_CAPACITY];
		_patch = new int[INITIAL_PATCH_DEPTH];
	}

	// ------------------------------------------------------------------
	// Writer 생성 / 재사용
	// ------------------------------------------------------------------

	public static ProtoWriter tl() {
		return TL.get().reset();
	}

	public static ProtoWriter newInstance() {
		return new ProtoWriter();
	}

	static ProtoWriter direct(WriteBuffer out) {
		if (out == null) throw new NullPointerException("out");

        ProtoWriter w = DIRECT_TL.get();
        // Keep a bounded per-thread reentry chain. No message object is pooled.
        for (int depth = 0; w._out != null; depth++) {
            if (depth >= 7) return new ProtoWriter().bind(out);
            if (w._nestedDirect == null) w._nestedDirect = new ProtoWriter();
            w = w._nestedDirect;
        }
        return w.bind(out);
	}

	public ProtoWriter reset() {
        if (directFrame != null) throw new IllegalStateException("Cannot reset an owned direct frame");
		_out = null;
		_base = 0;
		_p = 0;
		_patchN = 0;

		if (_a.length > MAX_RETAINED_CAPACITY)
			_a = new byte[INITIAL_CAPACITY];

		return this;
	}

	private ProtoWriter bind(WriteBuffer out) {
		_out = out;
		_base = out.position();
		_p = 0;
		_patchN = 0;
		return this;
	}

	void detachDirect() {
		_out = null;
		_base = 0;
		_p = 0;
		_patchN = 0;
        directFrame = null;
        directConnection = null;
	}

	// ------------------------------------------------------------------
	// 내부 IO
	// ------------------------------------------------------------------

	private boolean directMode() {
		return _out != null;
	}

	private void ensure(int n) {
		if (directMode()) return;

		final int required = _p + n;
		if (required <= _a.length) return;
		if (required < 0) throw new OutOfMemoryError("ProtoWriter capacity overflow");

		int cap = _a.length;
		while (cap < required) {
			final int next = cap << 1;
			if (next <= 0) { cap = required; break; }
			cap = next;
		}

		_a = Arrays.copyOf(_a, cap);
	}

	private void putByte(int value) {
		if (directMode()) _out.writeC(value);
		else {
			ensure(1);
			_a[_p] = (byte) value;
		}
		_p++;
	}

	private byte getByte(int index) {
		return directMode() ? _out.readByte(_base + index) : _a[index];
	}

	private void putByte(int index, byte value) {
		if (directMode()) _out.writeByte(_base + index, value);
		else _a[index] = value;
	}

	private void writeArray(byte[] src, int off, int len) {
		if (len <= 0) return;

		if (directMode()) {
			if (off == 0 && len == src.length) {
				_out.writeBytes(src);
			} else {
				for (int i = 0; i < len; i++) _out.writeByte(src[off + i]);
			}
		} else {
			ensure(len);
			System.arraycopy(src, off, _a, _p, len);
		}

		_p += len;
	}

	private void moveLeft(int src, int dst, int len) {
		if (len <= 0 || src == dst) return;

		if (!directMode()) {
			System.arraycopy(_a, src, _a, dst, len);
			return;
		}

		/*
		 * dst < src 이므로 앞에서부터 복사하면 overlap 안전.
		 * 별도 byte[] 할당 없음.
		 */
		for (int i = 0; i < len; i++)
			_out.writeByte(_base + dst + i, getByte(src + i));
	}

	private void syncPosition() {
		if (directMode())
			_out.position(_base + _p);
	}

	// ------------------------------------------------------------------
	// 저수준
	// ------------------------------------------------------------------

	public ProtoWriter rawByte(int v) {
		putByte(v);
		return this;
	}

	public ProtoWriter varint32(int v) {
		ensure(5);

		while ((v & ~0x7F) != 0) {
			putByte((v & 0x7F) | 0x80);
			v >>>= 7;
		}

		putByte(v);
		return this;
	}

	public ProtoWriter varint64(long v) {
		ensure(10);

		while ((v & ~0x7FL) != 0L) {
			putByte((int) (v & 0x7F) | 0x80);
			v >>>= 7;
		}

		putByte((int) v);
		return this;
	}

	private void tag(int field, int wire) {
		varint32((field << 3) | wire);
	}

	// ------------------------------------------------------------------
	// Scalar
	// ------------------------------------------------------------------

	public ProtoWriter int32(int field, int v) {
		tag(field, 0);
		if (v >= 0) varint32(v); else varint64(v);
		return this;
	}

	public ProtoWriter uint32(int field, int v)  { tag(field, 0); varint32(v); return this; }
	public ProtoWriter int64(int field, long v)  { tag(field, 0); varint64(v); return this; }
	public ProtoWriter uint64(int field, long v) { tag(field, 0); varint64(v); return this; }
	public ProtoWriter bool(int field, boolean v){ tag(field, 0); rawByte(v ? 1 : 0); return this; }
	public ProtoWriter enumV(int field, int v)   { return int32(field, v); }

	public ProtoWriter sint32(int field, int v) {
		tag(field, 0);
		varint32((v << 1) ^ (v >> 31));
		return this;
	}

	public ProtoWriter sint64(int field, long v) {
		tag(field, 0);
		varint64((v << 1) ^ (v >> 63));
		return this;
	}

	public ProtoWriter fixed32(int field, int v) {
		tag(field, 5);
		putByte(v); putByte(v >>> 8); putByte(v >>> 16); putByte(v >>> 24);
		return this;
	}

	public ProtoWriter fixed64(int field, long v) {
		tag(field, 1);
		for (int i = 0; i < 8; i++) putByte((int) (v >>> (i << 3)));
		return this;
	}

	public ProtoWriter floatV(int field, float v)   { return fixed32(field, Float.floatToRawIntBits(v)); }
	public ProtoWriter doubleV(int field, double v) { return fixed64(field, Double.doubleToRawLongBits(v)); }

	// ------------------------------------------------------------------
	// LEN
	// ------------------------------------------------------------------

	public ProtoWriter string(int field, String s) {
		tag(field, 2);

		if (s == null || s.isEmpty()) {
			rawByte(0);
			return this;
		}

		final byte[] b = s.getBytes(MS949);
		varint32(b.length);
		writeArray(b, 0, b.length);
		return this;
	}

	public ProtoWriter bytes(int field, byte[] b) {
		tag(field, 2);

		if (b == null || b.length == 0) {
			rawByte(0);
			return this;
		}

		varint32(b.length);
		writeArray(b, 0, b.length);
		return this;
	}

	public ProtoWriter rawString(int field, byte[] encoded) {
		tag(field, 2);

		if (encoded == null || encoded.length == 0) {
			rawByte(0);
			return this;
		}

		varint32(encoded.length);
		writeArray(encoded, 0, encoded.length);
		return this;
	}

	// ------------------------------------------------------------------
	// Embedded message
	// ------------------------------------------------------------------

	public ProtoWriter msg(int field) {
		tag(field, 2);

		if (_patchN == _patch.length)
			_patch = Arrays.copyOf(_patch, _patch.length << 1);

		_patch[_patchN++] = _p;

		/*
		 * 최대 5byte length 공간 예약.
		 * direct mode에서도 최종 WriteBuffer 안에 바로 예약한다.
		 */
		for (int i = 0; i < 5; i++) putByte(0);
		return this;
	}

	public ProtoWriter end() {
		if (_patchN <= 0)
			throw new IllegalStateException("[ProtoWriter] end() without msg()");

		final int pos = _patch[--_patchN];
		final int bodyLen = _p - pos - 5;
		final int lenLen = varintLenOf(bodyLen);

		if (lenLen != 5) {
			moveLeft(pos + 5, pos + lenLen, bodyLen);
			_p -= 5 - lenLen;
			syncPosition();
		}

		int v = bodyLen;

		for (int i = 0; i < lenLen; i++) {
			int b = v & 0x7F;
			v >>>= 7;
			if (v != 0) b |= 0x80;
			putByte(pos + i, (byte) b);
		}

		return this;
	}

	public static int varintLenOf(int v) {
		if ((v & ~0x7F) == 0) return 1;
		if ((v & ~0x3FFF) == 0) return 2;
		if ((v & ~0x1FFFFF) == 0) return 3;
		if ((v & ~0x0FFFFFFF) == 0) return 4;
		return 5;
	}

	// ------------------------------------------------------------------
	// 완성
	// ------------------------------------------------------------------

	public int length() {
		return _p;
	}

	public byte[] finish(int msgId) {
		requireArrayMode();
		checkClosed();

		final byte[] out = new byte[_p + 3];
		out[0] = (byte) Opcodes.S_EXTENDED_PROTOBUF;
		out[1] = (byte) msgId;
		out[2] = (byte) (msgId >>> 8);
		System.arraycopy(_a, 0, out, 3, _p);
		return out;
	}

	public byte[] finishWithTrailer(int msgId) {
		requireArrayMode();
		checkClosed();

		final byte[] out = new byte[_p + 5];
		out[0] = (byte) Opcodes.S_EXTENDED_PROTOBUF;
		out[1] = (byte) msgId;
		out[2] = (byte) (msgId >>> 8);
		System.arraycopy(_a, 0, out, 3, _p);
		return out;
	}

	public byte[] bodyBytes() {
		requireArrayMode();
		checkClosed();
		return Arrays.copyOf(_a, _p);
	}

	void finishDirect(boolean trailer) {
		if (!directMode())
			throw new IllegalStateException("[ProtoWriter] not direct mode");

		checkClosed();

		if (trailer) {
			rawByte(0);
			rawByte(0);
		}
	}

	private void requireArrayMode() {
		if (directMode())
			throw new IllegalStateException("[ProtoWriter] byte[] finish unavailable in direct mode");
	}

	private void checkClosed() {
		if (_patchN != 0)
			throw new IllegalStateException("[ProtoWriter] unclosed msg count=" + _patchN);
	}
}
