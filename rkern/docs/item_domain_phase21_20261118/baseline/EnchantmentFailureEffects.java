package l1j.server.server.model.item.function;

import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

/** Phase 12: original EnchantmentItems.java FailureEnchant body, unchanged. */
final class EnchantmentFailureEffects {
    private EnchantmentFailureEffects() {}

	static void FailureEnchant(L1PcInstance pc, L1ItemInstance item, CIOClient client) {
		String s = "";
		String sa = "";
		int itemType = item.getItem().getType2();
		int itemId = item.getItem().getItemId();
		String nameId = item.getName();
		String pm = "";

		if (itemType == 1) { // 무기
			if (!item.isIdentified() || item.getEnchantLevel() == 0) {
				s = nameId;
				// \f1%0이 강렬하게%1 빛난 뒤, 증발하고 있지 않게 됩니다.
				sa = "$245";
			} else {
				if (item.getEnchantLevel() > 0) {
					pm = "+";

				}
				s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(nameId).toString();
				// \f1%0이 강렬하게%1 빛난 뒤, 증발하고 있지 않게 됩니다.
				sa = "$245";
			}
		} else if (itemType == 2) { // 방어용 기구
			if (!item.isIdentified() || item.getEnchantLevel() == 0) {
				s = nameId;
				// \f1%0이 강렬하게%1 빛난 뒤, 증발하고 있지 않게 됩니다.
				sa = " $252";
			} else {
				if (item.getEnchantLevel() > 0) {
					pm = "+";
				}
				s = (new StringBuilder()).append(pm + item.getEnchantLevel()).append(" ").append(nameId).toString();
				// \f1%0이 강렬하게%1 빛난 뒤, 증발하고 있지 않게 됩니다.
				sa = " $252";
			}
		}
		// if ((itemId >= 1115 && itemId <= 1118) || (itemId >= 22250 && itemId
		// <= 22252)) {//신묘한 날자에 러쉬부분?
		if ((itemId >= 1115 && itemId <= 1118)) {

			SC_ServerMessage.send(pc, 1310);
			pc.getInventory().setEquipped(item, false);
			item.setEnchantLevel(0);
			pc.getInventory().updateItem(item, L1PcInventory.COL_ENCHANTLVL);
			pc.saveInventory();
			if (itemType == 1) {
			} else if (itemType == 2) {
			}
		} else {
			SC_ServerMessage.send(pc, 164, s, sa);
			pc.getInventory().removeItem(item, item.getCount());
			/** 로그파일저장 **/
			LoggerInstance.getInstance().addEnchant(pc, item, false);
		}
		SC_OwnCharStatus.send(pc);
	}
}
