package l1j.server.server.model.item.function;

import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.storage.CacheD;

public final class ClassRewardPackages {

	private static final Logger LOG = Logger.getLogger(ClassRewardPackages.class.getName());

	public static void clickItem(L1PcInstance pc, int itemId, L1ItemInstance input) {
		if (pc == null || input == null || input.getItemId() != itemId
				|| pc.getInventory().getItem(input.getId()) != input
				|| !pc.getInventory().checkItem(itemId, 1)) return;
		try (L1InventoryTransaction tx = L1InventoryTransaction.begin(
				pc, CacheD.accountOrderKey("item-use:", pc.getId()))) {
			if (!tx.debit(input, 1)) return;
			final ArrayList<String> messages = new ArrayList<>(2);
		switch (itemId) {
            case 4100164, 4100165, 3000035 -> StarterSupportPackages.stage(pc, itemId, tx, messages);
            case 68088, 68089, 68090, 68091, 68092, 68083, 68084, 68085, 68086, 68087, 68078, 68079, 68080, 68081, 68082, 60041, 60042, 60043, 60044, 60045, 60036, 60037, 60038, 60039, 60040 -> RuneRewardPackages.stage(pc, itemId, tx, messages);
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
