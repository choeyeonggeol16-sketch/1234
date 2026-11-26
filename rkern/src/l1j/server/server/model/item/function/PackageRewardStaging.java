package l1j.server.server.model.item.function;

import l1j.server.server.datatables.CObjectDB;
import java.util.ArrayList;
import l1j.server.server.IdFactory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.templates.L1Item;
import l1j.server.server.utils.CommonUtil;

/** Detached rewards staged inside an existing CacheD-backed inventory receipt. */
final class PackageRewardStaging {
    private PackageRewardStaging() {}
	/** Prepare a detached reward. Never publish or announce it before the receipt commits. */
	static void stageReward(L1InventoryTransaction tx, ArrayList<String> notices, int itemId,
			int count, int enchant, int bless, int attr, boolean identified) {
		stage(tx, notices, itemId, count, enchant, bless, attr, true, true);
	}
	static void stageSealedReward(L1InventoryTransaction tx, ArrayList<String> notices, int itemId,
			int count, int enchant, int bless, int attr, boolean identified) {
		stage(tx, notices, itemId, count, enchant, bless, attr, false, false);
	}
	private static void stage(L1InventoryTransaction tx, ArrayList<String> notices, int itemId,
			int count, int enchant, int bless, int attr, boolean identified, boolean expiry) {
		L1Item template = CObjectDB.GetItem(itemId);
		if (template == null || count <= 0) throw new IllegalStateException("Missing box reward: " + itemId);
		L1ItemInstance reward = new L1ItemInstance(template, count);
		reward.setId(IdFactory.getInstance().nextId());
		reward.setEnchantLevel(enchant);
		reward.setBless(bless);
		reward.setAttrEnchantLevel(attr);
		reward.setIdentified(identified);
		if (expiry && !reward.isStackable()) CommonUtil.SetTodayDeleteTime(reward, 4320);
		if (tx.grant(reward) == null) throw new IllegalStateException("Unable to stage reward: " + itemId);
		notices.add(reward.getLogName());
	}
}
