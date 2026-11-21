package l1j.server.server.model.character.combat.arena.dogfight.packets;

import java.util.ArrayList;

import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.shared.KeyValuePair;

public class S_DogFightSellList extends CServerPacket {

	private final int _npcObjectId;
	private final ArrayList<KeyValuePair<Integer, Integer>> _pairs;

	public static S_DogFightSellList newInstance(
			int npc_object_id,
			ArrayList<KeyValuePair<Integer, Integer>> pairs) {
		return new S_DogFightSellList(npc_object_id, pairs);
	}

	private S_DogFightSellList(
			int npc_object_id,
			ArrayList<KeyValuePair<Integer, Integer>> pairs) {
		_npcObjectId = npc_object_id;
		_pairs = pairs;
	}

	@Override
	public void writeImpl(CUserSocket client, CPacketWriteBuffer buffer) {
		buffer.writeC(Opcodes.S_SELL_LIST);
		buffer.writeD(_npcObjectId);

		if (_pairs != null) {
			buffer.writeH(_pairs.size());

			for (KeyValuePair<Integer, Integer> pair : _pairs) {
				buffer.writeD(pair.key);
				buffer.writeD(pair.value);
			}
		} else {
			buffer.writeH(0);
		}

		buffer.writeH(0x07);
	}

}