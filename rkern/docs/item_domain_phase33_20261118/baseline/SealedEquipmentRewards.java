package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.util.logging.Logger;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class SealedEquipmentRewards {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private SealedEquipmentRewards() {}

	static boolean UseSealedItem(L1PcInstance pc, int item_id, int count, int EnchantLevel, int Bless, int attr,
			boolean identi) {
		// 봉인템(pc, 5000045, 1, 5, 128);
		L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
		if (item != null) {
			item.setCount(count);
			item.setIdentified(identi);
			item.setEnchantLevel(EnchantLevel);
			item.setAttrEnchantLevel(attr);
			item.setIdentified(true);
			if (pc.getInventory().checkAddItem(item, count) == CInventory.OK) {
				pc.getInventory().storeItem(item);
				item.setBless(Bless);
				pc.getInventory().updateItem(item, CUserInventory.COL_BLESS);
				pc.getInventory().saveItem(item, CUserInventory.COL_BLESS);
			} else { // 가질 수 없는 경우는 지면에 떨어뜨리는 처리의 캔슬은 하지 않는다(부정 방지)
				SC_ServerMessage.send(pc, 82);
				// 무게 게이지가 부족하거나 인벤토리가 꽉차서 더 들 수 없습니다.
				return false;
			}
			SC_ServerMessage.send(pc, 403, item.getLogName()); //
			return true;
		} else {
			return false;
		}
	}
}
