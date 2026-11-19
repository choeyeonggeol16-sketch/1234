package l1j.server.server.model.item.function;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.storage.CacheD;

public final class LevelAndEquipmentPackages {

	private static final Logger LOG = Logger.getLogger(LevelAndEquipmentPackages.class.getName());

	public static void clickItem(L1PcInstance pc, int itemId, L1ItemInstance input) {
		if (pc == null || input == null || input.getItemId() != itemId
				|| pc.getInventory().getItem(input.getId()) != input
				|| !pc.getInventory().checkItem(itemId, 1)) return;
		try (L1InventoryTransaction tx = L1InventoryTransaction.begin(
				pc, CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(input, 1)) return;
			final ArrayList<String> messages = new ArrayList<>(2);
		switch (itemId) {
            case 3000508, 7020, 7021, 4100080 -> EquipmentStarterPackages.stage(pc, itemId, tx, messages);
            case 714, 715, 716, 717, 721, 722 -> LevelMilestonePackages.stage(pc, itemId, tx, messages);
            default -> { /* no reward, receipt rolls back */ }
        }
			// Unknown class/reward configuration: do not consume the input.
			if (messages.isEmpty()) return;
			tx.onFailure(() -> SC_ServerMessage.send(pc, 82));
			tx.commit(() -> {
				for (int i = 0; i < messages.size(); i++)
					SC_ServerMessage.send(pc, 403, messages.get(i));
			});
		} catch (RuntimeException failure) {
			LOG.log(Level.WARNING, "Support box receipt preparation failed: " + itemId, failure);
			SC_ServerMessage.send(pc, 82);
		}
	}

}
