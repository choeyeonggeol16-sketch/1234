package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.server.datatables.ResolventTable;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1EtcItem;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.SpecialConsumableRouter.nowTimestamp;

/** Domain-specific item handler; original transaction ordering preserved. */
final class OrdinaryResolventItems {
    private OrdinaryResolventItems() {}

    /** Dragon-ball dissolution uses the existing shared settlement path. */
    static void useDragonResolvent(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent) {
        if (!ResolventPairRules.validResolventPair(pc, target, solvent, 442455)) return;
        if (target.getItemId() < 4000138 || target.getItemId() > 4000144) return;
        final int count = ResolventTable.getInstance().getCrystalCount(target.getItemId());
        if (count <= 0) {
            SC_ServerMessage.send(pc, 1161);
            return;
        }
        ResolventSettlement.settleResolvent(pc, target, solvent, 40000155, count, 0, false);
    }

    static void useResolvent(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent) {
        if (!ResolventPairRules.validResolventPair(pc, target, solvent, 41245)) return;
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
        ResolventSettlement.settleResolvent(pc, target, solvent, 41246, mainCount, bonusCount, roll <= 20);
    }

    static void useResolventCount(
            L1PcInstance pc, L1ItemInstance target, L1ItemInstance solvent, int base) {
        if (!ResolventPairRules.validResolventPair(pc, target, solvent, 41245)) return;
        if (base <= 0) {
            SC_ServerMessage.send(pc, 1161);
            return;
        }
        final int count = ThreadLocalRandom.current().nextInt(100) + 1 >= 70
                ? base / 2 : base;
        ResolventSettlement.settleResolvent(pc, target, solvent, 41246, count, 0, false);
    }
}
