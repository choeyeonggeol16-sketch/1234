package l1j.server.server.model.network.core.cs.packet;

import java.util.List;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_NpcItemControl {

	private CS_NpcItemControl() {
	}

	public static void handle(CIOClient client, int data, int petId, int listNo) {

		if (client == null) {
			return;
		}

		final L1PcInstance pc = client.getActiveChar();

		if (pc == null) {
			return;
		}

		final L1Object object = L1World.getInstance().findObject(petId);

		if (!(object instanceof L1PetInstance)) {
			return;
		}

		final L1PetInstance pet = (L1PetInstance) object;
		final List<L1ItemInstance> items = pet.getInventory().getItems();

		if (items == null) {
			return;
		}

		final int size = items.size();

		if (size <= 0 || listNo >= size) {
			return;
		}

		final L1ItemInstance item = items.get(listNo);

		if (item == null || item.getItem() == null) {
			return;
		}

		if (item.getItem().getType2() != 0 || item.getItem().getType() != 11) {
			SC_ServerMessage.send(pc, 79);
			return;
		}

		final int itemId = item.getItem().getItemId();

		/*
		 * 펫 무기
		 */
		if ((itemId >= 40749 && itemId <= 40752) || (itemId >= 40756 && itemId <= 40758)) {
			pet.usePetWeapon(pc, pet, item);
			SC_PacketBox.send(pc, SC_PacketBox.SOMETHING2, data, pet.getId(), pet.getAC().getAc());
			return;
		}

		/*
		 * 펫 방어구
		 */
		if (itemId >= 40761 && itemId <= 40766) {
			pet.usePetArmor(pc, pet, item);
			SC_PacketBox.send(pc, SC_PacketBox.SOMETHING2, data, pet.getId(), pet.getAC().getAc());
			return;
		}

		SC_ServerMessage.send(pc, 79);
	}
}