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

package l1j.server.server.clientpackets;

import l1j.server.server.GameClient;

public class C_HireSoldier extends ClientBasePacket {

	private static final String C_HIRE_SOLDIER = "[C] C_HireSoldier";

	// S_HireSoldier를 보낸다고 표시되는 고용 윈도우로 OK를 누르는 곳의 패킷이 보내진다
	public C_HireSoldier(byte[] decrypt, GameClient client) {
		super(decrypt);
		int something1 = readH(); // S_HireSoldier 패킷의 인수
		int something2 = readH(); // S_HireSoldier 패킷의 인수
		int something3 = readD(); // 1이외 들어가지 않아?
		int something4 = readD(); // S_HireSoldier 패킷의 인수
		int number = readH(); // 고용하는 수

		// < 용병 고용 처리
	}

	@Override
	public String getType() {
		return C_HIRE_SOLDIER;
	}
}
