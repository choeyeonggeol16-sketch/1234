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

import l1j.server.CharacterAction.PickupActionHandlerFactory;
import l1j.server.Template.PacketHelper.PacketParser;
import l1j.server.server.GameClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_Disconnect;

public class C_PickUpItem extends ClientBasePacket {

	private static final String C_PICK_UP_ITEM = "[C] C_PickUpItem";

	public C_PickUpItem(byte decrypt[], GameClient client) throws Exception {
		super(decrypt);
		L1PcInstance pc = client.getActiveChar();
		if (pc == null || pc.isTwoLogin(pc) || pc.isGhost() || pc.isDead() || pc.isInvisble() || pc.isInvisDelay()
				|| pc.isFishing()) {
			return;
		}

		if (pc.getOnlineStatus() != 1) {
			pc.sendPackets(new S_Disconnect());
			return;
		}

		PacketParser parser = PickupActionHandlerFactory.create();
		if (parser == null) {
			return;
		}

		parser.parse(pc, this);
		parser.doWork();
	}

	@Override
	public String getType() {
		return C_PICK_UP_ITEM;
	}
}
