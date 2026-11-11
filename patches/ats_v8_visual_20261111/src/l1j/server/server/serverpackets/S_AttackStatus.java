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

import java.io.IOException;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;

public class S_AttackStatus extends ServerBasePacket {
	
	public static S_AttackStatus get(L1PcInstance pc, int objid, int type) {
		S_AttackStatus s = new S_AttackStatus();
		s.writeC(type);
		s.writeD(pc.getId());
		s.writeD(objid);
		s.writeC(0x01);
		s.writeC(0);
		s.writeC(pc.getHeading());
		s.writeD(0);
		s.writeC(0);
		return s;
	}
    private S_AttackStatus() {
    	writeC(Opcodes.S_OPCODE_ATTACKPACKET);
    }
    @Override
	public byte[] getContent() throws IOException {
		return getBytes();
	}
}
