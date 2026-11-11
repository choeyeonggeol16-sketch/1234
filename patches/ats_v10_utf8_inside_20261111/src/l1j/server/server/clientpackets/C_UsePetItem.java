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
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;

public class C_UsePetItem extends ClientBasePacket {

	private static final String C_USE_PET_ITEM = "[C] C_UsePetItem";

	public C_UsePetItem(byte abyte0[], GameClient clientthread) throws Exception {
		super(abyte0);

		int data = readC(); // ?
		int petId = readD();
		int listNo = readC();

		PetInstance pet = (PetInstance) L1World.getInstance().findObject(petId);
		L1PcInstance pc = clientthread.getActiveChar();

		if (pet != null && pc != null) {
			ItemInstance item = pet.getInventory().getItems().get(listNo);
			if (item != null) {
				if (item.getItem().getType() == 11) { // petitem
					// XXX
				}
			}
		}
	}

	@Override
	public String getType() {
		return C_USE_PET_ITEM;
	}
}
