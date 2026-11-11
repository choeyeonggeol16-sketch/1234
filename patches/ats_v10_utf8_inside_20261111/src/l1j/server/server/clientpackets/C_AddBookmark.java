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

import l1j.server.Templates.L1BookMark;
import l1j.server.server.GameClient;
import l1j.server.server.datatables.MapReturnTable;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_ServerMessage;

public class C_AddBookmark extends ClientBasePacket {

	private static final String C_ADD_BOOKMARK = "[C] C_AddBookmark";

	public C_AddBookmark(byte[] decrypt, GameClient client) {
		super(decrypt);
		String s = readS();

		L1PcInstance pc = client.getActiveChar();
		if (pc == null || pc.isGhost()) {
			return;
		}
		if (MapReturnTable.getInstance().isMapZoneChange(pc.getMapId()) && !pc.isGm()) {
			pc.sendPackets(new S_ServerMessage(214));
			return;
		}

		if (pc.getMap().isMarkable() || pc.isGm()) {
			if ((L1CastleLocation.checkInAllWarArea(pc.getX(), pc.getY(), pc.getMapId()) 
/*화둥*/			|| ((pc.getX() >= 33514 && pc.getX() <= 33809) && (pc.getY() >= 32210 && pc.getY() <= 32457) && pc.getMapId() == 4)
/*황혼산맥*/		|| ((pc.getX() >= 34207 && pc.getX() <= 34287) && (pc.getY() >= 33097 && pc.getY() <= 33515) && pc.getMapId() == 4)
/*버경장*/		|| ((pc.getX() >= 33460 && pc.getX() <= 33538) && (pc.getY() >= 32829 && pc.getY() <= 32878) && pc.getMapId() == 4)
				|| L1HouseLocation.isInHouse(pc.getX(), pc.getY(), pc.getMapId())) && !pc.isGm()) {
				// \f1여기를 기억할 수가 없습니다.
				pc.sendPackets(new S_ServerMessage(214));
			} else {
				L1BookMark.addBookmark(pc, s);
			}
		} else {
			// \f1여기를 기억할 수가 없습니다.
			pc.sendPackets(new S_ServerMessage(214));
		}
	}

	@Override
	public String getType() {
		return C_ADD_BOOKMARK;
	}
}
