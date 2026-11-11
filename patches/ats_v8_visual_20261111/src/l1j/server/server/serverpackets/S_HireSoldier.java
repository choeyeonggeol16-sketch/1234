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
import l1j.server.server.model.Instance.L1PcInstance;

public class S_HireSoldier extends ServerBasePacket {

	private static final String S_HIRE_SOLDIER = "[S] S_HireSldier";

	// HTML를 열고 있을 때 이 패킷을 보내면(자) npcdeloy-j.html가 표시된다
	// OK버튼을 누르면(자) C_127이 난다
	public S_HireSoldier(L1PcInstance pc) {
		writeC(Opcodes.S_OPCODE_HIRESOLDIER);
		writeH(0); // ?  클라이언트가 돌려주는 패킷에 포함된다
		writeH(0); // ?  클라이언트가 돌려주는 패킷에 포함된다
		writeH(0); // 고용된 용병의 총수
		writeS(pc.getName());
		writeD(0); // ?  클라이언트가 돌려주는 패킷에 포함된다
		writeH(0); // 배치 가능한 용병수
	}

	@Override
	public byte[] getContent() {
		return getBytes();
	}

	public String getType() {
		return S_HIRE_SOLDIER;
	}
}
