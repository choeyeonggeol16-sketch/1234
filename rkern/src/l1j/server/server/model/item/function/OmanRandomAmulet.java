package l1j.server.server.model.item.function;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class OmanRandomAmulet {

    public static void clickItem(L1PcInstance pc, int itemId, L1ItemInstance item) {
        if (pc == null || item == null || item.getItemId() != itemId
                || pc.getInventory().getItem(item.getId()) != item) return;

        // Both chaos (830042-830051) and transformed (830052-830061)
        // amulets give the same floor-specific reward. Preserve the
        // original 40% chance on floors 1-6, 30% on floors 7-10.
        final int floor = itemId >= 830052 ? itemId - 830052 : itemId - 830042;
        if (floor < 0 || floor >= 10) return;
        final int successRate = floor < 6 ? 40 : 30;
        final int rewardId = (ThreadLocalRandom.current().nextInt(100) < successRate
                ? 830022 : 830012) + floor;

        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
            if (!tx.debit(item, 1)) return;
            final L1ItemInstance reward = tx.grant(rewardId, 1);
            if (reward == null) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            final String rewardName = reward.getLogName();
            tx.onFailure(() -> SC_SystemMessage.send(pc, "오만의 탑 부적 교환이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> SC_ServerMessage.send(pc, 403, rewardName));
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 부적을 소지할 수 없습니다.");
        }
    }
}
