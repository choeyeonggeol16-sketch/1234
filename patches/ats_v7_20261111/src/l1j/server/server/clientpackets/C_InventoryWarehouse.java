package l1j.server.server.clientpackets;

import l1j.server.server.GameClient;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Warehouse.PresentWarehouse;
import l1j.server.server.model.Warehouse.WarehouseManager;
import l1j.server.server.serverpackets.S_NPCTalkReturn;
import l1j.server.server.serverpackets.S_RetrievePresentList;

public class C_InventoryWarehouse extends ClientBasePacket {

	private static final String C_InventoryWarehouse = "C_InventoryWarehouse"; 

	public C_InventoryWarehouse(byte[] decrypt, GameClient client) {
		super(decrypt);
		try {
			int type = readH();
			L1PcInstance pc = client.getActiveChar();
			//System.out.println(type);

			switch (type) {
			case 262: { // 부가서비스 창고
				PresentWarehouse w = WarehouseManager.getInstance().getPresentWarehouse(pc.getAccountName());
				if (w.getSize() != 0){ 
					pc.sendPackets(new S_RetrievePresentList(pc.getId(), pc));
				} else {   
					pc.sendPackets(new S_NPCTalkReturn(pc.getId(), "noitemret")); 
				}
			}
			break;
			default:
				break;
			}
		} catch (Exception e) {
		} finally {
		}
	}

	@Override
	public String getType() {
		return C_InventoryWarehouse;
	}
}