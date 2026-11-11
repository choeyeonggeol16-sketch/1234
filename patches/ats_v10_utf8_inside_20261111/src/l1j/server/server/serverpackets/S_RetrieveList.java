package l1j.server.server.serverpackets;

import java.io.IOException;

import l1j.server.server.Opcodes.Opcodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.PrivateWarehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;

public class S_RetrieveList extends ServerBasePacket {
	public S_RetrieveList(int objid, L1PcInstance pc) {
		if (pc.getInventory().getSize() < 180) {
			PrivateWarehouse warehouse = WarehouseManager.getInstance().getPrivateWarehouse(pc.getAccountName());				
			int size = warehouse.getSize();
			if (size > 0) {
				writeC(Opcodes.S_OPCODE_SHOWRETRIEVELIST);
				writeD(objid);
				writeH(size);
				writeC(3); // 개인 창고
				ItemInstance item = null;
				for (Object itemObject : warehouse.getItems()) {
					item = (ItemInstance) itemObject;
					writeD(item.getId());
					writeC(0);
					writeH(item.get_gfxid());
					writeC(item.getItem().getBless());
					writeD(item.getCount());
					writeC(item.isIdentified() ? 1 : 0);
					writeS(item.getViewName());
				}
			}
		} else {
			pc.sendPackets(new S_ServerMessage(263)); // \f1한사람의 캐릭터가 가지고 걸을 수 있는 아이템은 최대 180개까지입니다.
		}
	}
	
	public S_RetrieveList(int objid, L1PcInstance pc, int start, int count) {
		writeC(Opcodes.S_OPCODE_SHOWRETRIEVELIST);
		writeD(objid);
		writeH(count);
		writeC(3);
		ItemInstance item = ItemTable.getInstance().createItem(40005);
		for (int i = 0; i < count; i++) {
			item.getItem().setGfxId(start + i);
			item.getItem().setName(String.valueOf(start + i));
			item.getItem().setNameId(String.valueOf(start + i));
			
			writeD(item.getId());
			writeC(item.getItem().getType2());
			writeH(item.get_gfxid());
			writeC(1);
			writeD(start + i);
			writeC(item.isIdentified() ? 1 : 0);
			writeS(item.getViewName());
		}
	}

	@Override
	public byte[] getContent() throws IOException {
		return getBytes();
	}

}
