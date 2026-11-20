package l1j.server.DogFight.Packets;

import java.util.ArrayList;

import l1j.server.MJTemplate.MJKeyValuePair;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.CPacketWriteBuffer;
import l1j.server.server.model.network.packet.CServerPacket;
import l1j.server.server.model.network.core.connection.CUserSocket;

public class S_DogFightSellList extends CServerPacket {

	private final int _npcObjectId;
	private final ArrayList<MJKeyValuePair<Integer, Integer>> _pairs;

	public static S_DogFightSellList newInstance(
			int npc_object_id,
			ArrayList<MJKeyValuePair<Integer, Integer>> pairs) {
		return new S_DogFightSellList(npc_object_id, pairs);
	}

	private S_DogFightSellList(
			int npc_object_id,
			ArrayList<MJKeyValuePair<Integer, Integer>> pairs) {
		_npcObjectId = npc_object_id;
		_pairs = pairs;
	}

	@Override
	public void writeImpl(CUserSocket client, CPacketWriteBuffer buffer) {
		buffer.writeC(Opcodes.S_SELL_LIST);
		buffer.writeD(_npcObjectId);

		if (_pairs != null) {
			buffer.writeH(_pairs.size());

			for (MJKeyValuePair<Integer, Integer> pair : _pairs) {
				buffer.writeD(pair.key);
				buffer.writeD(pair.value);
			}
		} else {
			buffer.writeH(0);
		}

		buffer.writeH(0x07);
	}

}