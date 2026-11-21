package l1j.server.server.model.character.combat.arena.dogfight.packets;

import java.util.ArrayList;

import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightTicketInfo;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.packet.CServerPacket;

public class S_DogFightBuyList extends CServerPacket {

	private final int _objectId;
	private final ArrayList<DogFightTicketInfo> _ticketsInfo;
	private final int _price;

	public static S_DogFightBuyList newInstance(
			L1PcInstance pc,
			int object_id,
			ArrayList<DogFightTicketInfo> tickets_info,
			int price) {
		return new S_DogFightBuyList(object_id, tickets_info, price);
	}

	private S_DogFightBuyList(
			int object_id,
			ArrayList<DogFightTicketInfo> tickets_info,
			int price) {
		_objectId = object_id;
		_ticketsInfo = tickets_info;
		_price = price;
	}

	@Override
	public void writeImpl(CUserSocket client, CPacketWriteBuffer buffer) {

		buffer.writeC(Opcodes.S_BUY_LIST);
		buffer.writeD(_objectId);

		if (_ticketsInfo != null) {

			int size = _ticketsInfo.size();
			buffer.writeH(size);

			for (int i = 0; i < size; ++i) {

				DogFightTicketInfo tInfo = _ticketsInfo.get(i);

				buffer.writeD(i);
				buffer.writeH(tInfo.get_template().getGfxId());
				buffer.writeD(_price);
				buffer.writeS(tInfo.get_template().getName());
				buffer.writeD(tInfo.get_template().getUseType());

				L1ItemInstance dummy = new L1ItemInstance();
				dummy.setItem(tInfo.get_template());

				byte[] status = dummy.getStatusBytes();

				buffer.writeC(status.length);

				for (byte b : status) {
					buffer.writeC(b & 0xFF);
				}
			}

		} else {
			buffer.writeH(0);
		}

		buffer.writeH(7);
	}

}