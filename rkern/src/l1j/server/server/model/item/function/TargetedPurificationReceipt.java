package l1j.server.server.model.item.function;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.WeaponTransformationTables.*;

/** Dedicated item action; original transaction and effect ordering preserved. */
final class TargetedPurificationReceipt {
    private TargetedPurificationReceipt() {}

    static void useTargetedPurification(L1PcInstance pc, L1ItemInstance reagent,
            L1ItemInstance target) {
        if (pc == null) return;
        if (reagent == null || target == null || reagent == target
                || pc.getInventory().getItem(reagent.getId()) != reagent
                || pc.getInventory().getItem(target.getId()) != target) {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        final int reagentId = reagent.getItemId();
        final int targetId = target.getItemId();
        final int resultId;
        final boolean success;
        final boolean destroyTargetOnFailure;
        final int failMessage;
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        if (reagentId == 40925 && targetId >= 40987 && targetId <= 40989) {
            success = random.nextInt(100) < Config.CREATE_CHANCE_RECOLLECTION;
            resultId = targetId + 186;
            destroyTargetOnFailure = true;
            failMessage = 158;
        } else if (reagentId >= 40926 && reagentId <= 40929
                && targetId >= (41173 + (reagentId - 40926) * 3)
                && targetId <= (41175 + (reagentId - 40926) * 3)) {
            // The old roll was Random.nextInt(99) + 1 (1..99), strict '<'.
            success = random.nextInt(99) + 1 < Config.CREATE_CHANCE_MYSTERIOUS;
            resultId = targetId - 12;
            destroyTargetOnFailure = false;
            failMessage = 160;
        } else if (reagentId == 40964 && targetId >= 41011 && targetId <= 41018) {
            success = random.nextInt(99) + 1 <= Config.CREATE_CHANCE_HISTORY_BOOK;
            resultId = targetId + 8;
            destroyTargetOnFailure = true;
            failMessage = 158;
        } else if (reagentId == 41036 && targetId >= 41038 && targetId <= 41047) {
            success = random.nextInt(99) + 1 <= Config.CREATE_CHANCE_DIARY;
            resultId = targetId + 10;
            destroyTargetOnFailure = true;
            failMessage = 158;
        } else {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        final String targetName = target.getName();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, reagent.getId())) {
            if (!tx.debit(reagent, 1)) return;
            if ((success || destroyTargetOnFailure) && !tx.debit(target, 1)) {
                SC_ServerMessage.send(pc, 79);
                return;
            }
            if (success && tx.grant(resultId, 1) == null) {
                SC_SystemMessage.send(pc, "정화 결과 아이템을 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "정화 결과가 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                if (!success) SC_ServerMessage.send(pc, failMessage, targetName);
            });
        } catch (RuntimeException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 정화를 완료할 수 없습니다.");
        }
    }
}
