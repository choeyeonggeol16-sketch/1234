package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.storage.CacheD;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class LegacyEquipmentExchangeReceipt {
    private LegacyEquipmentExchangeReceipt() {}
    private static final Logger LOG = Logger.getLogger("l1j.server.server.model.item.function.EconomyRewardItems");

    public static void exchangeLegacyItem(L1PcInstance pc, L1ItemInstance source) {
        if (pc == null || source == null
                || pc.getInventory().GetByServerID(source.getId()) != source) return;
        final int id = source.getItemId();
        final int rewardId;
        if (id >= 40280 && id <= 40288) {
            rewardId = id + 9;
        } else if (id >= 830032 && id <= 830041) {
            rewardId = id - 20;
        } else if (id == 3000063) {
            rewardId = 900007;
        } else {
            return;
        }
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(source, 1)) return;
            final L1ItemInstance reward = tx.grant(rewardId, 1);
            if (reward == null) {
                SC_SystemMessage.send(pc, "아이템을 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "아이템 교환 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                try {
                    try {
                        l1j.server.server.model.network.core.sc.packet.SC_ServerMessage.send(pc, 403,
                                reward.getLogName());
                    } catch (RuntimeException outputFailure) {
                        LOG.log(Level.WARNING, "Committed equipment exchange notice failed: " + id,
                                outputFailure);
                    }
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, source);
                }
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Sealed item exchange failed: " + pc.getId() + " / " + id,
                    failure);
            SC_SystemMessage.send(pc, "아이템 교환을 처리하지 못했습니다.");
        }
    }
}
