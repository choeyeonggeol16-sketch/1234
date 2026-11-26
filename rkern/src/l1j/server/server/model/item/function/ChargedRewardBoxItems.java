package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;

public final class ChargedRewardBoxItems {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private ChargedRewardBoxItems() {}

    private static void sendCommittedReward(L1PcInstance pc, String rewardName) {
        try {
            SC_ServerMessage.send(pc, 403, rewardName);
        } catch (RuntimeException outputFailure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Committed charged box reward notice failed: " + pc.getId(), outputFailure);
        }
    }

    private static void sendCommittedText(L1PcInstance pc, String message) {
        try {
            SC_SystemMessage.send(pc, message);
        } catch (RuntimeException outputFailure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Committed charged box notice failed: " + pc.getId(), outputFailure);
        }
    }

    public static void UseDurableChargedBox(L1PcInstance pc, L1ItemInstance box) {
        if (pc == null || box == null || pc.getInventory().GetByServerID(box.getId()) != box)
            return;
        final int id = box.getItemId();
        switch (id) {
        case 42656: case 90736: case 90735: case 3000245: case 3000244:
        case 3000242: case 90737: case 6012: case 6016:
            break;
        default:
            return;
        }
        final int charge = box.getChargeCount();
        if (charge <= 0) {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        final boolean exhausted = charge == 1;
        final String boxName = exhausted ? box.getLogName() : null;
        try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (exhausted) {
                // Legacy removeItem(box) removed the entire exhausted instance.
                if (!tx.debit(box, box.getCount())) return;
            } else {
                tx.changeChargeCount(box, charge - 1);
            }
            L1ItemInstance r1 = null, r2 = null, r3 = null, r4 = null;
            L1ItemInstance r5 = null, r6 = null;
            if (id == 90737) {
                r1 = tx.grant(700013, 1);
            } else if (id == 6012) {
                r1 = tx.grant(6003, 1);
            } else if (id == 6016) {
                r1 = tx.grant(40098, 10);
                r2 = tx.grant(40099, 10);
                r3 = tx.grant(40029, 100);
                r4 = tx.grant(40096, 3);
                r5 = tx.grant(40096, 2);
                int classItem = pc.isElf() ? 42651
                        : (pc.isWizard() || pc.isBlackwizard()) ? 42652 : 42650;
                r6 = tx.grant(classItem, 3);
            }
            if ((id == 90737 || id == 6012) && r1 == null
                    || id == 6016 && (r1 == null || r2 == null || r3 == null
                            || r4 == null || r5 == null || r6 == null)) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            final L1ItemInstance reward1 = r1, reward2 = r2, reward3 = r3;
            final L1ItemInstance reward4 = r4, reward5 = r5, reward6 = r6;
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "상자 사용 내역을 저장하지 못했습니다. 아이템을 확인해 주세요."));
            CDB.RequestUseItem(pc, tx, () -> {
                try {
                    if (id == 90737) {
                        sendCommittedText(pc, "획득: " + reward1.getLogName());
                    } else if (id == 6012 || id == 6016) {
                        if (reward1 != null) sendCommittedReward(pc, reward1.getLogName());
                        if (reward2 != null) sendCommittedReward(pc, reward2.getLogName());
                        if (reward3 != null) sendCommittedReward(pc, reward3.getLogName());
                        if (reward4 != null) sendCommittedReward(pc, reward4.getLogName());
                        if (reward5 != null) sendCommittedReward(pc, reward5.getLogName());
                        if (reward6 != null) sendCommittedReward(pc, reward6.getLogName());
                    }
                    if (exhausted) sendCommittedText(pc, boxName + " 다 사용 하였습니다.");
                } finally {
                    ItemActionCompletion.finishCommittedConsumable(pc, box);
                }
            });
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Charged box prepare failed: " + pc.getId() + " / " + id, failure);
            SC_SystemMessage.send(pc, "상자 사용을 처리하지 못했습니다.");
        }
    }
}
