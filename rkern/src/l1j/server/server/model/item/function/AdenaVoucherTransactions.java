package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;


import l1j.server.server.model.item.function.SpecialConsumableRouter.Result;

final class AdenaVoucherTransactions {
    private AdenaVoucherTransactions() {}

    static final int ITEM_ADENA_VOUCHER_100M = 400254;
    static final int ITEM_ADENA_VOUCHER_50M = 500254;
    static final int ITEM_ADENA_VOUCHER_10M = 600254;
    private static final int ITEM_ADENA = 40308;
    private static final int ADENA_EXCHANGE_LIMIT = 2_000_000_000;

    static Result useAdenaVoucher(L1PcInstance pc, L1ItemInstance item) {
        final int itemId = item.getItemId();
        final int adena;
        final String success;
        switch (itemId) {
        case ITEM_ADENA_VOUCHER_100M:
            adena = 100_000_000;
            success = "1억 아데나로 환전 되었습니다.";
            if (item.getBless() >= 128) {
                SC_SystemMessage.send(pc, "봉인해제 후 아데나로 변환 가능합니다.");
                return Result.RETURN;
            }
            break;
        case ITEM_ADENA_VOUCHER_50M:
            adena = 50_000_000;
            success = "5천 아데나로 환전 되었습니다.";
            break;
        case ITEM_ADENA_VOUCHER_10M:
            adena = 10_000_000;
            success = "1천 아데나로 환전 되었습니다.";
            break;
        default:
            return Result.NOT_HANDLED;
        }

        // Enforce the original 2-billion warning as a real projected limit.
        // Using subtraction also avoids an int overflow near MAX_AMOUNT.
        final int heldAdena = pc.getInventory().GetUnequippedItemAmount(ITEM_ADENA);
        if (heldAdena < 0 || heldAdena > ADENA_EXCHANGE_LIMIT - adena) {
            SC_ServerMessage.send(pc, 3369);
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE,
                    "20억 아데나 초과시 무용지물됩니다.");
            return Result.RETURN;
        }

        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
            if (!tx.debit(item, 1)) {
                pc.sendPackets("수표가 부족합니다.");
                return Result.RETURN;
            }
            if (tx.grant(ITEM_ADENA, adena) == null) {
                SC_SystemMessage.send(pc, "아데나 환전 수량을 처리할 수 없습니다.");
                return Result.RETURN;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc,
                    "아데나 환전이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                pc.sendPackets(success);
                SC_ServerMessage.send(pc, 3369);
                SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE,
                        "20억 아데나 초과시 무용지물됩니다.");
                L1ItemDelay.onItemUse(pc, item);
            });
        } catch (IllegalStateException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 환전 한도를 초과했습니다.");
        }
        return Result.RETURN;
    }
}
