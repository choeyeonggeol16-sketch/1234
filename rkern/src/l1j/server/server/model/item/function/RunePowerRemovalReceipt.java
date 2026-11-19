package l1j.server.server.model.item.function;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.WeaponTransformationTables.*;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class RunePowerRemovalReceipt {
    private RunePowerRemovalReceipt() {}

    static void useRunePowerRemover(L1PcInstance pc, L1ItemInstance remover,
            L1ItemInstance rune) {
        if (pc == null || remover == null || rune == null || remover == rune
                || remover.getItemId() != 60035
                || pc.getInventory().getItem(remover.getId()) != remover
                || pc.getInventory().getItem(rune.getId()) != rune) return;
        final int runeId = rune.getItemId();
        final int rewardId;
        final String message;
        if (runeId >= 222295 && runeId <= 222299) {
            rewardId = 60034; message = " 마력을 잃은 룬으로 변경되었습니다.";
        } else if (runeId >= 222312 && runeId <= 222316) {
            rewardId = 68097; message = " 마력을 잃은 룬(70)으로 변경되었습니다.";
        } else if (runeId >= 900135 && runeId <= 900139) {
            rewardId = 68098; message = " 마력을 잃은 룬(80)으로 변경되었습니다.";
        } else if (runeId >= 900140 && runeId <= 900144) {
            rewardId = 68099; message = " 마력을 잃은 룬(85)으로 변경되었습니다.";
        } else if (runeId >= 900145 && runeId <= 900149) {
            rewardId = 68100; message = " 마력을 잃은 룬(90)으로 변경되었습니다.";
        } else {
            SC_SystemMessage.send(pc, "엘릭서 룬에 사용할 수 있습니다.");
            return;
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, remover.getId())) {
            if (!tx.debit(remover, 1) || !tx.debit(rune, 1)) return;
            if (tx.grant(rewardId, 1) == null) {
                SC_SystemMessage.send(pc, "마력을 잃은 룬을 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "룬 변환이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> SC_SystemMessage.send(pc, message));
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 룬 변환을 완료할 수 없습니다.");
        }
    }
}
