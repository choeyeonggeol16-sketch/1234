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
package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;

public class S_Unknown1 extends ServerBasePacket {
	public S_Unknown1() {
		writeC(Opcodes.S_OPCODE_UNKNOWN1);
		writeC(0x03); //
		writeC(0xa0); //
		writeC(0x48); //
		writeC(0x19); //
		writeC(0x82); //
		writeC(0x6a); //
		writeC(0xfb); //

	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
