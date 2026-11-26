package l1j.server.server.model.item.function;

import java.sql.Timestamp;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.item.NpcReissuedItemPolicy;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1EtcItem;

import static l1j.server.server.model.item.function.SpecialConsumableRouter.nowTimestamp;

/** Domain-specific item handler; original transaction ordering preserved. */
final class ResolventSettlement {
    private ResolventSettlement() {}

    static void settleResolvent(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent,
            int crystalId, int mainCount, int bonusCount, boolean evaporated) {
        if (mainCount < 0 || bonusCount < 0
                || (long) mainCount + bonusCount > Integer.MAX_VALUE) {
            SC_ServerMessage.send(pc, 1161);
            return;
        }
        final String targetName = target.getName();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, solvent.getId())) {
            if (NpcReissuedItemPolicy.isProtected(target.getItemId())
                    || !tx.debit(target, 1) || !tx.debit(solvent, 1)) {
                SC_ServerMessage.send(pc, 1161);
                return;
            }
            // The ordinary dispatcher used to touch the delay timestamp
            // after consuming a stackable solvent. Keep that durable, but
            // never update a removed one-count solvent.
            if (solvent.getCount() > 1 && solvent.getItem() instanceof L1EtcItem
                    && ((L1EtcItem) solvent.getItem()).get_delayEffect() > 0) {
                final Timestamp used = nowTimestamp();
                tx.change(solvent, image -> image.setLastUsed(used));
            }
            // The original bonus branch stored extra crystals first, then
            // the main reward. Both deposits share the same durable receipt.
            if (bonusCount != 0 && tx.grant(crystalId, bonusCount) == null) {
                SC_SystemMessage.send(pc, "용해 결과를 소지할 수 없습니다.");
                return;
            }
            final L1ItemInstance crystal = mainCount == 0 ? null : tx.grant(crystalId, mainCount);
            if (mainCount != 0 && crystal == null) {
                SC_SystemMessage.send(pc, "용해 결과를 소지할 수 없습니다.");
                return;
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "용해 처리가 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                if (evaporated) SC_ServerMessage.send(pc, 158, targetName);
                else if (crystal != null) SC_ServerMessage.send(pc, 403, crystal.getLogName());
                L1ItemDelay.onItemUse(pc, solvent);
            });
        } catch (IllegalStateException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중이거나 용해 결과를 소지할 수 없습니다.");
        }
    }
}
