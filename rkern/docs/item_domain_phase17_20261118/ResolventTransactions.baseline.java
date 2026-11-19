package l1j.server.server.model.item.function;

import java.sql.Timestamp;

import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.datatables.ResolventTable;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1EtcItem;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import static l1j.server.server.model.item.function.SpecialConsumableRouter.nowTimestamp;

final class ResolventTransactions {
    private ResolventTransactions() {}

    static void useResolvent(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent) {
        if (!validResolventPair(pc, target, solvent, 41245)) return;
        if (target.getItem().getType2() == 1 || target.getItem().getType2() == 2) {
            if (target.getEnchantLevel() != 0 || target.isEquipped()
                    || target.getBless() >= 128) {
                SC_ServerMessage.send(pc, 1161);
                return;
            }
        }
        final int base = ResolventTable.getInstance().getCrystalCount(target.getItemId());
        if (base <= 0) {
            SC_ServerMessage.send(pc, 1161);
            return;
        }

        useResolventRoll(pc, target, solvent, base,
                ThreadLocalRandom.current().nextInt(100) + 1);
    }

    static void useResolventRoll(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent,
            int base, int roll) {
        if (roll < 1 || roll > 100 || base <= 0) {
            SC_ServerMessage.send(pc, 1161);
            return;
        }
        final int mainCount;
        final int bonusCount;
        if (roll <= 20) {
            mainCount = 0;
            bonusCount = 0;
        } else if (roll <= 90) {
            mainCount = base;
            bonusCount = 0;
        } else {
            // Keep the original L1 91-100 reward formula, including the
            // independent extra reward before the normal crystal reward.
            // Compute in long so invalid configuration cannot wrap negative.
            final long raised = (long) (base * 1.5d);
            final long extra = (long) (raised * 1.5d);
            if (raised > Integer.MAX_VALUE || extra > Integer.MAX_VALUE) {
                SC_ServerMessage.send(pc, 1161);
                return;
            }
            mainCount = (int) raised;
            bonusCount = (int) extra;
        }
        settleResolvent(pc, target, solvent, 41246, mainCount, bonusCount, roll <= 20);
    }

    static void useResolventCount(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent, int base) {
        if (!validResolventPair(pc, target, solvent, 41245)) return;
        if (base <= 0) {
            SC_ServerMessage.send(pc, 1161);
            return;
        }
        final int count = ThreadLocalRandom.current().nextInt(100) + 1 >= 70
                ? base / 2 : base;
        settleResolvent(pc, target, solvent, 41246, count, 0, false);
    }

    static void useDragonResolvent(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent) {
        if (!validResolventPair(pc, target, solvent, 442455)) return;
        if (target.getItemId() < 4000138 || target.getItemId() > 4000144) return;
        final int count = ResolventTable.getInstance().getCrystalCount(target.getItemId());
        if (count <= 0) {
            SC_ServerMessage.send(pc, 1161);
            return;
        }
        settleResolvent(pc, target, solvent, 40000155, count, 0, false);
    }

    static boolean validResolventPair(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent, int expectedId) {
        if (pc == null) return false;
        if (target == null || solvent == null) {
            SC_ServerMessage.send(pc, 79);
            return false;
        }
        if (solvent.getItemId() != expectedId || target == solvent) {
            SC_ServerMessage.send(pc, 1161);
            return false;
        }
        return true;
    }

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
            if (!tx.debit(target, 1) || !tx.debit(solvent, 1)) {
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
