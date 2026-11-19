package l1j.server.server.model.item.function;

import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

/** One receipt for the seal scroll debit and target blessing state change. */
public final class SealRemovalScrolls {
    private static final Logger LOG = Logger.getLogger(SealRemovalScrolls.class.getName());

    private SealRemovalScrolls() {}

    public static void use(L1PcInstance pc, L1ItemInstance l1iteminstance,
            L1ItemInstance l1iteminstance1, int itemId) {
                    // The scroll and the target must be the actual live inventory
                    // instances, not stale handles or objects from another player.
                    if (!SpecialEnchantScrolls.isOwnedScrollTarget(
                            pc, l1iteminstance, l1iteminstance1)) {
                        SC_ServerMessage.send(pc, 79);
                        return;
                    }
                    final int oldBless = l1iteminstance1.getBless();
                    final boolean sealing = itemId == 50020;
                    if (sealing ? oldBless < 0 || oldBless > 3
                            : oldBless < 128 || oldBless > 131) {
                        SC_ServerMessage.send(pc, 79);
                        return;
                    }
                    final int newBless = sealing ? oldBless + 128 : oldBless - 128;
                    try (l1j.server.server.model.L1InventoryTransaction tx =
                            l1j.server.server.model.L1InventoryTransaction.begin(pc, l1iteminstance1.getId())) {
                        if (!tx.debit(l1iteminstance, 1)) {
                            SC_ServerMessage.send(pc, 79);
                            return;
                        }
                        // The source scroll and target blessing are one durable
                        // receipt-backed DB transaction, including equipped targets.
                        tx.changeBless(l1iteminstance1, newBless);
                        tx.requireTables("character_items");
                        tx.onFailure(() -> SC_SystemMessage.send(pc,
                                "봉인 변경이 확정되지 않았습니다. 주문서와 아이템을 확인해 주세요."));
                        tx.commitAfterInventoryRelease(() -> {
                            ItemActionCompletion.finishCommittedConsumable(pc, l1iteminstance);
                            int st = l1iteminstance1.isIdentified() ? 1 : 0;
                            if (!l1iteminstance1.getItem().isTradable()) st += 2;
                            if (l1iteminstance1.getItem().isCantDelete()) st += 4;
                            if (l1iteminstance1.getItem().get_safeenchant() < 0) st += 8;
                            if (l1iteminstance1.getBless() >= 128) {
                                st = l1iteminstance1.isIdentified() ? 47 : 46;
                            }
                            try {
                                SC_PacketBox.send(pc, SC_PacketBox.ITEM_STATUS, l1iteminstance1, st);
                            } catch (RuntimeException notificationFailure) {
                                LOG.log(Level.WARNING,
                                        "Seal status packet failed after durable commit", notificationFailure);
                            }
                        });
                    } catch (RuntimeException failure) {
                        LOG.log(Level.WARNING, "Seal item transaction prepare failed", failure);
                        SC_SystemMessage.send(pc, "봉인 변경에 실패했습니다. 주문서는 소비되지 않았습니다.");
                    }
    }
}
