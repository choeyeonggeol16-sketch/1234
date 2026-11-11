/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
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

import java.io.IOException;

import l1j.server.server.Opcodes.Opcodes;

public class S_DoActionGFX extends ServerBasePacket {
	
	public static S_DoActionGFX get(int objectId, int actionId) {
		S_DoActionGFX s = new S_DoActionGFX();
		s.writeD(objectId);
		s.writeC(actionId);
		return s;
	}
	
	private S_DoActionGFX() {
		writeC(Opcodes.S_OPCODE_DOACTIONGFX);
	}
	
	@Override
	public byte[] getContent() throws IOException {
		return getBytes();
	}
	
}
