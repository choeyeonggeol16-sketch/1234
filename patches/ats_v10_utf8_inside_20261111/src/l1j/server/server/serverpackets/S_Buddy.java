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
 * Author: ChrisLiu.2007.07.20
 */
package l1j.server.server.serverpackets;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.model.L1Buddy;

public class S_Buddy extends ServerBasePacket {
	private static final String _S_Buddy = "[S] _S_Buddy";
	private static final String _HTMLID = "buddy";

	private byte[] _byte = null;

	public S_Buddy(int objId, L1Buddy buddy) {
		buildPacket(objId, buddy);
	}
	
	public S_Buddy(int objId, String name, String text) {
		if (text.length() > 0) {
			buildPacket(objId, _HTMLID, name, text);
		} else {
			close(objId);
		}
	}

	public void close(int objId) {
		buildPacket(objId, null, null, null);
	}

	private void buildPacket(int objId, L1Buddy buddy) {
		writeC(Opcodes.S_OPCODE_SHOWHTML);
		writeD(objId);
		writeS(_HTMLID);
		writeH(0x02);
		writeH(0x02);

		writeS(buddy.getBuddyListString());
		writeS(buddy.getOnlineBuddyListString());
	}
	
	private void buildPacket(int objId, String html, String name, String text) {
		writeC(Opcodes.S_OPCODE_SHOWHTML);
		writeD(objId);
		writeS(html);
		writeH(0x02);
		writeH(0x02);
		writeS(name);
		writeS(text);
	}


	@Override
	public byte[] getContent() {
		if (_byte == null) {
			_byte = _bao.toByteArray();
		}
		return _byte;
	}
	@Override
	public String getType() {
		return _S_Buddy;
	}
}
