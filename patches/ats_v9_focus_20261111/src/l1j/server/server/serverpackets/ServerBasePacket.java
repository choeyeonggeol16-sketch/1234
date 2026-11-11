package l1j.server.server.serverpackets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

public abstract class ServerBasePacket {

	private int OpKey; // opcode Key

	private boolean isKey = true;

	ByteArrayOutputStream _bao;
	private byte[] _byte;

	protected ServerBasePacket() {
		_bao = new ByteArrayOutputStream(128);
	}

	protected ServerBasePacket(int capacity) {
		_bao = new ByteArrayOutputStream(capacity);
	}

	public void clear() {
		if (_bao == null) {
			return;
		}
		try {
			_bao.reset();
			_bao.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		_bao = null;
	}

	// Key
	private void setKey(int i) {
		OpKey = i;
	}

	private int getKey() {
		return OpKey;
	}

	protected void writeD(int value) {
		_bao.write(value & 0xff);
		_bao.write(value >> 8 & 0xff);
		_bao.write(value >> 16 & 0xff);
		_bao.write(value >> 24 & 0xff);
	}

	protected void writeL(long value) {
		_bao.write((byte) (value & 0xff));
		_bao.write((byte) (value >> 8 & 0xff));
		_bao.write((byte) (value >> 16 & 0xff));
		_bao.write((byte) (value >> 24 & 0xff));
		_bao.write((byte) (value >> 32 & 0xff));
		_bao.write((byte) (value >> 40 & 0xff));
		_bao.write((byte) (value >> 48 & 0xff));
		_bao.write((byte) (value >> 56 & 0xff));
	}

	protected void writeH(int value) {
		_bao.write(value & 0xff);
		_bao.write(value >> 8 & 0xff);
	}

	protected void writeC(int value) {
		_bao.write(value & 0xff);
		// 옵코드 wirteC 첫번째 호출만 셋팅...
		if (isKey) {
			setKey(value);
			isKey = false;
		}
	}

	protected void writeSU16(String text) {
		try {
			if (text != null) {
				_bao.write(text.getBytes("UTF-16LE"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		_bao.write(0);
		_bao.write(0);
	}

	protected void writeK(int value) {
		int valueK = (int) (value / 128);
		if (valueK == 0) {
			_bao.write(value);
		} else if (valueK <= 127) {
			_bao.write((value & 0x7f) + 128);
			_bao.write(valueK);
		} else if (valueK <= 16383) {
			_bao.write((value & 0x7f) + 128);
			_bao.write((valueK & 0x7f) + 128);
			_bao.write(valueK / 128);
		} else if (valueK <= 2097151) {
			_bao.write((value & 0x7f) + 128);
			_bao.write((valueK & 0x7f) + 128);
			_bao.write(((valueK / 128) & 0x7f) + 128);
			_bao.write(valueK / 16384);
		} else {
			_bao.write((value & 0x7f) + 128);
			_bao.write((valueK & 0x7f) + 128);
			_bao.write(((valueK / 128) & 0x7f) + 128);
			_bao.write(((valueK / 16384) & 0x7f) + 128);
			_bao.write(valueK / 2097152);
		}
	}

	public int bitlengh(int obj) {
		int length = 0;
		if (obj < 0) {
			BigInteger b = new BigInteger("18446744073709551615");
			while (BigInteger.valueOf(obj).and(b).shiftRight((length + 1) * 7).longValue() > 0) {
				length++;
			}
			length++;
		} else {
			if (obj <= 127) {
				length = 1;
			} else if (obj <= 16383) {
				length = 2;
			} else if (obj <= 2097151) {
				length = 3;
			} else if (obj <= 268435455) {
				length = 4;
			} else if ((long) obj <= 34359738367L) {
				length = 5;
			}
		}
		return length;
	}

	/* 한국 옵코드 추가 패킷 */
	protected void write4bit(int value) {
		if (value <= 127) {
			this._bao.write(value & 0x7F);
		} else if (value <= 16383) {
			this._bao.write(value & 0x7F | 0x80);
			this._bao.write(value >> 7 & 0x7F);
		} else if (value <= 2097151) {
			this._bao.write(value & 0x7F | 0x80);
			this._bao.write(value >> 7 & 0x7F | 0x80);
			this._bao.write(value >> 14 & 0x7F);
		} else if (value <= 268435455) {
			this._bao.write(value & 0x7F | 0x80);
			this._bao.write(value >> 7 & 0x7F | 0x80);
			this._bao.write(value >> 14 & 0x7F | 0x80);
			this._bao.write(value >> 21 & 0x7F);
		} else if (value <= 34359738367L) {
			this._bao.write(value & 0x7F | 0x80);
			this._bao.write(value >> 7 & 0x7F | 0x80);
			this._bao.write(value >> 14 & 0x7F | 0x80);
			this._bao.write(value >> 21 & 0x7F | 0x80);
			this._bao.write(value >> 28 & 0x7F);
		}
	}

	protected void writeBit(long value) {
		if (value < 0L) {
			String stringValue = Integer.toBinaryString((int) value);
			value = Long.valueOf(stringValue, 2).longValue();
		}
		int i = 0;
		while (value >> 7 * (i + 1) > 0L) {
			_bao.write((int) ((value >> 7 * i++) % 128L | 0x80));
		}
		_bao.write((int) ((value >> 7 * i) % 128L));
	}

	protected void writeBit(int x, int y) {
		String value = new StringBuilder().append(Integer.toBinaryString(y)).append("").append(x < 32768 ? "0" : "")
				.append(Integer.toBinaryString(x)).toString();
		writeBit(Long.valueOf(value, 2).longValue());
	}

	protected void writePoint(int x, int y) {
		int pt = (y << 16) & 0xffff0000;
		pt |= (x & 0x0000ffff);
		writeBit(pt);
	}

	protected void writeB(boolean b) {
		writeC(b ? 0x1 : 0x0);
	}

	protected void writeB(Object o) {
		writeC(o != null ? 0x01 : 0x00);
	}

	protected int getBitSize(long value) {
		if (value < 0L) {
			String stringValue = Integer.toBinaryString((int) value);
			value = Long.valueOf(stringValue, 2).longValue();
		}
		int size = 0;
		while (value >> (size + 1) * 7 > 0L) {
			size++;
		}
		size++;

		return size;
	}

	protected void write7B(long value) {
		int i = 0;
		BigInteger b = new BigInteger("18446744073709551615");

		while (BigInteger.valueOf(value).and(b).shiftRight((i + 1) * 7).longValue() > 0) {
			_bao.write(BigInteger.valueOf(value).and(b).shiftRight(7 * i++).remainder(BigInteger.valueOf(0x80))
					.or(BigInteger.valueOf(0x80)).intValue());
		}
		_bao.write(BigInteger.valueOf(value).and(b).shiftRight(7 * i++).remainder(BigInteger.valueOf(0x80)).intValue());
	}

	public int size7B(int obj) {
		int length = 0;
		if (obj < 0) {
			BigInteger b = new BigInteger("18446744073709551615");
			while (BigInteger.valueOf(obj).and(b).shiftRight((length + 1) * 7).longValue() > 0) {
				length++;
			}
			length++;
		} else {
			if (obj <= 127) {
				length = 1;
			} else if (obj <= 16383) {
				length = 2;
			} else if (obj <= 2097151) {
				length = 3;
			} else if (obj <= 268435455) {
				length = 4;
			} else if ((long) obj <= 34359738367L) {
				length = 5;
			}
		}
		return length;
	}

	protected void writeP(int value) {
		_bao.write(value);
	}

	protected void writeF(double org) {
		long value = Double.doubleToRawLongBits(org);
		_bao.write((int) (value & 0xff));
		_bao.write((int) (value >> 8 & 0xff));
		_bao.write((int) (value >> 16 & 0xff));
		_bao.write((int) (value >> 24 & 0xff));
		_bao.write((int) (value >> 32 & 0xff));
		_bao.write((int) (value >> 40 & 0xff));
		_bao.write((int) (value >> 48 & 0xff));
		_bao.write((int) (value >> 56 & 0xff));
	}

	protected void writeS(String text) {
		try {
			if (text != null) {
				_bao.write(text.getBytes("MS949"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		_bao.write(0);
	}

	protected void writeSS(String text) {
		try {
			if (text != null) {
				byte[] test = text.getBytes("EUC-KR");
				for (int i = 0; i < test.length;) {
					if ((test[i] & 0xff) >= 0x7F) {
						/** 한글 **/
						_bao.write(test[i + 1]);
						_bao.write(test[i]);
						i += 2;
					} else {
						/** 영문&숫자 **/
						_bao.write(test[i]);
						_bao.write(0);
						i += 1;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		_bao.write(0);
		_bao.write(0);
	}

	protected void writeByte(byte[] text) {
		try {
			if (text != null) {
				_bao.write(text);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int getLength() {
		return _bao.size() + 2;
	}

	public byte[] getBytes() {
		if (_byte == null) {
			_byte = _bao.toByteArray();
		}
		return _byte;
	}

	protected void writeS2(String text) {
		try {
			if (text != null && !text.isEmpty()) {
				byte[] name = text.getBytes("EUC-KR");
				_bao.write(name.length & 0xff);
				if (name.length > 0) {
					_bao.write(name);
				}
			} else {
				_bao.write(0 & 0xff);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public abstract byte[] getContent() throws IOException;

	/**
	 * 서버 패킷의 종류를 나타내는 캐릭터 라인을 돌려준다. ("[S] S_WhoAmount" 등 )
	 */
	public String getType() {
		return "";
	}

	public String toString() {
		// getType() 의 리턴이 "" 이라면 빈값 아니면 패킷이름 + 코드값 출력
		// [옵코드] 패킷명
		String sTemp = getType().equals("") ? "" : "[" + getKey() + "] " + getType();
		return sTemp;
	}

	public void writeBytes(byte[] data) throws Exception {
		if (data == null || data.length <= 0) {
			writeC(0x00);
		}
		else {
			writeBit(data.length);
			_bao.write(data);
		}
	}

	public void close() {
		if (_byte != null) {
			_byte = null;
		}
		try {
			_bao.close();
		} catch (Exception e) {
		}
	}

}
