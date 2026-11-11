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
import l1j.server.server.model.L1Object;

public class S_RemoveObject extends ServerBasePacket {
	
	public static S_RemoveObject get(L1Object obj) {
		S_RemoveObject s = new S_RemoveObject();
		s.writeD(obj.getId());
		return s;
	}
	
	public static S_RemoveObject get(int obj) {
		S_RemoveObject s = new S_RemoveObject();
		s.writeD(obj);
		return s;
	}
	private S_RemoveObject() {
		writeC(Opcodes.S_OPCODE_REMOVE_OBJECT);
	}
	@Override
	public byte[] getContent() {
		return getBytes();
	}
}
