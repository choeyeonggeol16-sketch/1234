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
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.serverpackets.S_ServerMessage;
import manager.SJManager;

public class C_DeleteInventoryItem extends ClientBasePacket {

	private static final String C_DELETE_INVENTORY_ITEM = "[C] C_DeleteInventoryItem";

	public C_DeleteInventoryItem(byte[] decrypt, GameClient client) {
		super(decrypt);
		int itemObjectId = readD();
		L1PcInstance pc = client.getActiveChar();
		if (pc == null) {
			return;
		}
		ItemInstance item = pc.getInventory().getItem(itemObjectId);

		// 삭제하려고 한 아이템이 서버상에 없는 경우
		if (item == null) {
			return;
		}

		if (item.getItem().isCantDelete()) {
			// \f1 삭제할 수없는 항목이나 갖추고있는 아이템을 버리고없습니다.
			pc.sendPackets(new S_ServerMessage(125));
			return;
		}
		if(item.getBless() >= 128){
			pc.sendPackets(new S_ServerMessage(210, item.getItem().getName())); // \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
			return;
		}
		Object[] petlist = pc.getPetList().values().toArray();
		for (Object petObject : petlist) {
			if (petObject instanceof PetInstance) {
				PetInstance pet = (PetInstance) petObject;
				if (item.getId() == pet.getItemObjId()) {
					// \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
					pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
					return;
				}
			}
		}
		DollInstance doll = pc.getMagicDoll();
		if (doll != null) {
			if (item.getId() == doll.getItemObjId()) {
				// \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
				pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
				return;
			}
		}

		if (item.isEquipped()) {
			// \f1삭제할 수 없는 아이템이나 장비 하고 있는 아이템은 버릴 수 없습니다.
			pc.sendPackets(new S_ServerMessage(125));
			return;
		}
		 SJManager.getInstance().itemDeleteAppend(pc.getName(), item , item.getCount());
		pc.getInventory().removeItem(item, item.getCount());
		pc.getLight().turnOnOffLight();
	}

	@Override
	public String getType() {
		return C_DELETE_INVENTORY_ITEM;
	}
}
