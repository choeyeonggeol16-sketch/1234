/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class BinaryOutputStream extends OutputStream {
	protected final ByteArrayOutputStream _bao;

	public BinaryOutputStream(int capacity){
		_bao = new ByteArrayOutputStream(capacity);
	}
	
	public BinaryOutputStream() {
		_bao = new ByteArrayOutputStream();
	}

	@Override
	public void write(int b) throws IOException {
		_bao.write(b);
	}

	public void writeS2(String text) {
		try {
			if (text != null && !text.isEmpty()) {
				byte[] name = text.getBytes("MS949");
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
	
	public void writeD(int value) {
		_bao.write(value & 0xff);
		_bao.write(value >> 8 & 0xff);
		_bao.write(value >> 16 & 0xff);
		_bao.write(value >> 24 & 0xff);
	}
	
	public void writeD(long value) {
		_bao.write((int) (value & 0xff));
		_bao.write((int) (value >> 8 & 0xff));
		_bao.write((int) (value >> 16 & 0xff));
		_bao.write((int) (value >> 24 & 0xff));
	}

	public void writeH(int value) {
		_bao.write(value & 0xff);
		_bao.write(value >> 8 & 0xff);
	}
	  public void writeS3(String text)
	  {
	    try
	    {
	      if ((text != null) && (!text.isEmpty()))
	      {
	        byte[] name = text.getBytes("MS949");
	        this._bao.write(name.length & 0xFF);
	        if (name.length > 0) {
	          this._bao.write(name);
	        }
	      }
	      else
	      {
	        this._bao.write(0);
	      }
	    }
	    catch (Exception localException) {}
	  }
	public void writeC(int value) {
		_bao.write(value & 0xff);
	}

	public void writeP(int value) {
		_bao.write(value);
	}

	public void writeL(long value) {
		_bao.write((int) (value & 0xff));
	}

	public void writeF(double org) {
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

	public void writeS(String text) {
		try {
			if (text != null) {
				_bao.write(text.getBytes("MS949"));
			}
		} catch (Exception e) {
		}

		_bao.write(0);
	}
	
	public void writeBit(long value) {
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

	public void writeByte(byte[] text) {
		try {
			if (text != null) {
				_bao.write(text);
			}
		} catch (Exception e) {
		}
	}

	public int getLength() {
		return _bao.size() + 2;
	}

	public byte[] getBytes() {
		return _bao.toByteArray();
	}
}
