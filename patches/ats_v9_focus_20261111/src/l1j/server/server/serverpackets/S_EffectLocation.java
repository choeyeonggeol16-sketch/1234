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
import l1j.server.server.model.L1Location;

public class S_EffectLocation extends ServerBasePacket {

	/**
	 * 지정된 위치에 효과를 표시하는 패킷을 구축한다.
	 * 
	 * @param loc - 효과를 표시하는 위치를 격납한 L1Location 오브젝트
	 * @param gfxId - 표시하는 효과의 ID
	 */
	public static S_EffectLocation get(L1Location loc, int gfxId) {
		S_EffectLocation s = new S_EffectLocation();
		s.writeH(loc.getX());
		s.writeH(loc.getY());
		s.writeH(gfxId);
		s.writeC(0);
		return s;
	}
	
	/**
	 * 지정된 위치에 효과를 표시하는 패킷을 구축한다.
	 * 
	 * @param x - 효과를 표시하는 위치의 X좌표
	 * @param y - 효과를 표시하는 위치의 Y좌표
	 * @param gfxId - 표시하는 효과의 ID
	 */
	public static S_EffectLocation get(int x, int y, int gfxId) {
		S_EffectLocation s = new S_EffectLocation();
		s.writeH(x);
		s.writeH(y);
		s.writeH(gfxId);
		s.writeC(0);
		return s;
	}

	private S_EffectLocation() {
		writeC(Opcodes.S_OPCODE_EFFECTLOCATION);
	}
	@Override
	public byte[] getContent() throws IOException {
		return getBytes();
	}
}
