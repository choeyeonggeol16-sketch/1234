package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

/** All-or-nothing grant of the existing administrator package (123123). */
public final class AdminItemPackages {
    private static final Logger LOG = Logger.getLogger(AdminItemPackages.class.getName());
    private static final int[] REWARD_ID = {
            46162, 46161, 46160, 40308, 900010, 42501, 12301,
            45450, 301066, 41415, 40018, 210110, 875640450
    };
    private static final int[] REWARD_COUNT = {
            1, 1, 1, 100_000_000, 1, 1, 1, 1, 1, 100, 100, 100, 1
    };

    private AdminItemPackages() {}

    public static void open(L1PcInstance pc, L1ItemInstance packageItem) {
        if (pc == null || !pc.isGm() || packageItem == null || packageItem.getItemId() != 123123
                || pc.getInventory().getItem(packageItem.getId()) != packageItem) return;
        try (L1InventoryTransaction tx = User.CheckAndSetPendingUseETCItem(pc,
                CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(packageItem, 1)) return;
            final String[] rewardNames = new String[REWARD_ID.length];
            for (int i = 0; i < REWARD_ID.length; ++i) {
                L1ItemInstance granted = tx.grant(REWARD_ID[i], REWARD_COUNT[i]);
                if (granted == null) {
                    SC_ServerMessage.send(pc, 82);
                    return;
                }
                rewardNames[i] = granted.getLogName();
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "운영자 세트 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                for (int i = 0; i < rewardNames.length; ++i)
                    SC_ServerMessage.send(pc, 403, rewardNames[i]);
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Admin package transaction failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "운영자 세트를 처리하지 못했습니다.");
        }
    }
}
