package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;

/** Dedicated item-use handler extracted from a mixed domain. */
final class PureShirtSoapExchange {
    private PureShirtSoapExchange() {}

    private static final Logger LOG = Logger.getLogger(PureShirtSoapExchange.class.getName());
    public static void exchangePureShirt(L1PcInstance pc, L1ItemInstance soap, L1ItemInstance shirt) {
        if (pc == null || soap == null || shirt == null || soap == shirt
                || soap.getItemId() != 30116
                || pc.getInventory().getItem(soap.getId()) != soap
                || pc.getInventory().getItem(shirt.getId()) != shirt) {
            if (pc != null) SC_ServerMessage.send(pc, 79);
            return;
        }
        final int id = shirt.getItemId();
        final int rewardId;
        if (id >= 22340 && id <= 22348) rewardId = 20084;
        else if (id >= 22349 && id <= 22357) rewardId = 20085;
        else {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        if (shirt.isEquipped()) {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        final int enchant = shirt.getEnchantLevel();
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(soap, 1) || !tx.debit(shirt, 1)) return;
            final L1ItemInstance reward = tx.grant(rewardId, 1);
            if (reward == null) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            reward.setEnchantLevel(enchant);
            reward.setIdentified(true);
            tx.onFailure(() -> SC_SystemMessage.send(pc, "순백의 티 교환 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                ItemActionCompletion.finishCommittedConsumable(pc, soap);
                try {
                    SC_ServerMessage.send(pc, 403, reward.getLogName());
                } catch (RuntimeException notificationFailure) {
                    LOG.log(Level.WARNING, "Pure shirt exchange notification failed after commit",
                            notificationFailure);
                }
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Pure shirt exchange failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "순백의 티 교환을 처리하지 못했습니다.");
        }
    }
}
