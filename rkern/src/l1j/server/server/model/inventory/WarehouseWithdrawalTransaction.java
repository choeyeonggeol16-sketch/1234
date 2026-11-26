package l1j.server.server.model.inventory;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.inventory.warehouse.Warehouse;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;

/** Warehouse withdrawal batch execution; SQL commit precedes owner replay. */
final class WarehouseWithdrawalTransaction {
    private WarehouseWithdrawalTransaction() {}

    /** One durable receipt for a selected warehouse withdrawal. */
    static boolean RequestBatch(CInventory ownerInventory, int[] ids, int[] counts,
            Warehouse source, int feeItemId, int feeEach, java.sql.Timestamp[] deadlines,
            Runnable onCommitted) {
        if (!(ownerInventory instanceof CUserInventory) || ids == null || counts == null
                || ids.length <= 1 || ids.length > 100 || ids.length != counts.length
                || source == null || (deadlines != null && (deadlines.length != ids.length
                        || !(source instanceof l1j.server.server.model.inventory.warehouse.SupplementaryService)))
                || (feeItemId != 0 && feeItemId != L1ItemId.ADENA && feeItemId != 40494)
                || (feeItemId == 0) != (feeEach == 0) || feeEach < 0
                || ownerInventory.rejectPendingItemTransfer()) return false;
        final l1j.server.server.model.Instance.L1PcInstance pc =
                ((CUserInventory) ownerInventory).getOwner();
        final CUserSocket session = pc.getNetConnection();
        final int stamp = pc.getSessionStamp();
        final int token = pc.tryReserveItemTransfer(session, stamp);
        if (token == 0) return false;
        boolean submitted = false;
        try {
            final int size = ids.length;
            final long totalFee = (long) feeEach * size;
            final L1ItemInstance[] sources = new L1ItemInstance[size];
            final int[] sourceCounts = new int[size];
            final int[] group = new int[size];
            final long[] groupTotals = new long[size];
            final L1ItemInstance[] destinations = new L1ItemInstance[size];
            final int[] destCounts = new int[size];
            long[] chargeTotals = null;
            int[] destCharges = null;
            final L1ItemInstance[] carries = new L1ItemInstance[size];
            synchronized (source) {
                final L1ItemInstance fee = feeEach == 0 ? null : ownerInventory.GetFirstItemByClassID(feeItemId);
                if (feeEach != 0 && (fee == null || !fee.isStackable() || fee.getCount() < totalFee))
                    return false;
                final int feeCount = fee == null ? 0 : fee.getCount();
                int slots = ownerInventory.getSize();
                long additionalWeight = 0L;
                int feeGroup = -1;
                for (int i = 0; i < size; i++) {
                    final L1ItemInstance item = source.GetByServerID(ids[i]);
                    final int count = counts[i];
                    if (item == null || item.getId() != ids[i] || !item.IsTransferableAmount(count))
                        return false;
                    for (int j = 0; j < i; j++) if (ids[j] == ids[i]) return false;
                    sources[i] = item;
                    sourceCounts[i] = item.getCount();
                    long weightProduct = (long) item.getItem().getWeight() * count;
                    if (weightProduct < 0) return false;
                    additionalWeight += weightProduct / 1000L + 1L;
                    if (additionalWeight > Integer.MAX_VALUE) return false;

                    final boolean stackable = item.isStackable();
                    final boolean mergeCharge = !stackable && Warehouse.chargeMergeItem(item.getItemId());
                    int g = i;
                    if (stackable || mergeCharge) {
                        for (int j = 0; j < i; j++) {
                            if (stackable ? L1ItemInstance.CanMergeStack(sources[j], item)
                                    : sources[j].getItemId() == item.getItemId()) {
                                g = group[j];
                                break;
                            }
                        }
                    }
                    group[i] = g;
                    if (!stackable && slots >= CUserInventory.MAX_SIZE) return false;
                    if (g == i) {
                        destinations[i] = Warehouse.withdrawalStack(ownerInventory, item);
                        destCounts[i] = destinations[i] == null ? 0 : destinations[i].getCount();
                        if (destinations[i] == null && ++slots > CUserInventory.MAX_SIZE) return false;
                        if (fee != null && destinations[i] == fee) feeGroup = i;
                    }
                    // A timed supplementary item must retain its own deadline.
                    // Its replay path does not collect a withdrawal fee.
                    if (deadlines != null) {
                        if (deadlines[i] != null && (feeEach != 0 || g != i || destinations[i] != null))
                            return false;
                        if (g != i && deadlines[g] != null) return false;
                    }
                    // Check the complete template total, not just the first
                    // stack: replay updates the character's aggregate count.
                    long addedCount = mergeCharge ? (g == i && destinations[i] == null ? 1L : 0L) : count;
                    for (int j = 0; j < i; j++) {
                        if (sources[j].getItemId() != item.getItemId()) continue;
                        if (mergeCharge) {
                            if (group[j] == j && destinations[j] == null) addedCount++;
                        } else addedCount += counts[j];
                    }
                    if ((long) ownerInventory.GetItemAmount(item.getItemId()) + addedCount > CInventory.MAX_AMOUNT)
                        return false;
                    if (mergeCharge) {
                        if (chargeTotals == null) {
                            chargeTotals = new long[size];
                            destCharges = new int[size];
                        }
                        if (g == i && destinations[i] != null) destCharges[i] = destinations[i].getChargeCount();
                        final int charge = item.getChargeCount();
                        if (charge < 0 || destCharges[g] < 0
                                || (chargeTotals[g] += charge) + destCharges[g] > Integer.MAX_VALUE) return false;
                        if (g == i && destinations[i] == null) groupTotals[g] = 1;
                    } else {
                        groupTotals[g] += count;
                        if (groupTotals[g] + destCounts[g] > 1_800_000_000L) return false;
                    }
                }
                // The inventory's weight is rate-scaled. Conservatively
                // account for the configured rate and per-stack rounding so
                // committed replay cannot hit a later weight rejection.
                final double weightRate = Config.RATE_WEIGHT_LIMIT;
                if (!Double.isFinite(weightRate) || weightRate <= 0.0
                        || pc.getMaxWeight() <= 0) return false;
                final double projectedWeight = ownerInventory.GetCarryingWeight()
                        + (additionalWeight + size) / Math.min(1.0, weightRate) + size;
                if (projectedWeight < 0.0
                        || projectedWeight >= (double) pc.getMaxWeight() * 2.4
                        || projectedWeight >= Integer.MAX_VALUE / 100.0)
                    return false;
                // When taking out the fee currency itself, the final count of
                // that destination stack is (old + received - all fees).
                if (feeGroup >= 0 && (long) feeCount + groupTotals[feeGroup] - totalFee < 0L)
                    return false;
                final CacheD.WarehouseMove reserved;
                try { reserved = CacheD.beginWarehouseMove(pc.getId(), source.transferOrderKey()); }
                catch (java.util.concurrent.RejectedExecutionException busy) { return false; }
                try (CacheD.WarehouseMove move = reserved) {
                    source.requireWithdrawalTable(move);
                    final int[] expectedCharges = destCharges;
                    move.onFailure(() -> pc.quarantineItemTransferSession(session, stamp));
                    for (int i = 0; i < size; i++) {
                        final L1ItemInstance item = sources[i];
                        final int count = counts[i];
                        move.expectOriginal(item);
                        if (count == sourceCounts[i]) {
                            carries[i] = item;
                            source.prepareTransferDelete(item);
                        } else {
                            carries[i] = l1j.server.server.model.inventory.warehouse.Warehouse
                                    .allocateDetachedTransfer(item, count);
                            move.prepareCarry(item.getId(), carries[i]);
                            source.updateItem(l1j.server.server.model.inventory.warehouse.Warehouse.transferCopy(
                                    item, item.getId(), sourceCounts[i] - count, item.getChargeCount()));
                        }
                    }
                    for (int i = 0; i < size; i++) {
                        if (group[i] != i) continue;
                        final L1ItemInstance dest = destinations[i];
                        int finalCount = (int) (groupTotals[i] + destCounts[i]);
                        if (dest != null && dest == fee) finalCount -= (int) totalFee;
                        final boolean mergeCharge = !sources[i].isStackable()
                                && Warehouse.chargeMergeItem(sources[i].getItemId());
                        final int finalCharge = mergeCharge ? (int) (chargeTotals[i] + destCharges[i])
                                : dest == null ? carries[i].getChargeCount() : dest.getChargeCount();
                        if (dest != null) {
                            move.expectOriginal(dest);
                            if (finalCount == 0) move.prepareCharacterDelete(dest);
                            else move.prepareCharacterWrite(Warehouse.transferCopy(
                                    dest, dest.getId(), finalCount, finalCharge), false);
                        } else {
                            L1ItemInstance row = Warehouse.characterTransferCopy(
                                    carries[i], carries[i].getId(), finalCount, finalCharge);
                            if (deadlines != null && deadlines[i] != null) row.setEndTime(deadlines[i]);
                            move.prepareCharacterWrite(row, true);
                        }
                    }
                    if (fee != null && feeGroup < 0) {
                        move.expectOriginal(fee);
                        if (feeCount == totalFee) move.prepareCharacterDelete(fee);
                        else move.prepareCharacterWrite(l1j.server.server.model.inventory.warehouse.Warehouse
                                .transferCopy(fee, fee.getId(), (int) (feeCount - totalFee),
                                        fee.getChargeCount()), false);
                    }
                    move.commit(() -> CIOThreadCallback.getInstance().completeWarehouseMove(pc,
                            stamp, () -> {
                        synchronized (session) {
                            synchronized (ownerInventory) {
                                synchronized (source) {
                                    if (!pc.isItemTransferSession(session, stamp))
                                        throw new IllegalStateException("Stale warehouse withdrawal batch session");
                                    if (fee != null && (ownerInventory.GetByServerID(fee.getId()) != fee || fee.getCount() != feeCount))
                                        throw new IllegalStateException("Withdrawal batch fee preimage changed");
                                    for (int i = 0; i < size; i++) {
                                        if (source.GetByServerID(ids[i]) != sources[i]
                                                || sources[i].getCount() != sourceCounts[i])
                                            throw new IllegalStateException("Withdrawal source changed after COMMIT");
                                        if (group[i] == i && destinations[i] != null
                                                && (ownerInventory.GetByServerID(destinations[i].getId()) != destinations[i]
                                                        || destinations[i].getCount() != destCounts[i]
                                                        || (expectedCharges != null
                                                            && !sources[i].isStackable()
                                                            && Warehouse.chargeMergeItem(sources[i].getItemId())
                                                            && destinations[i].getChargeCount() != expectedCharges[i])))
                                            throw new IllegalStateException("Withdrawal destination changed after COMMIT");
                                    }
                                    CacheD.applyCommittedWarehouseMove(move, () -> {
                                        for (int i = 0; i < size; i++) {
                                            final L1ItemInstance result = deadlines != null && deadlines[i] != null
                                                    ? source.tradeSupplementaryItem(sources[i], counts[i],
                                                            ownerInventory, deadlines[i], null)
                                                    : source.tradeItem(sources[i], counts[i],
                                                            ownerInventory, feeItemId, feeEach);
                                            if (result == null)
                                                throw new IllegalStateException("Withdrawal replay failed id=" + ids[i]);
                                            if (move.replayCarry(ids[i]) == result)
                                                L1World.getInstance().storeObject(result);
                                        }
                                    });
                                    for (int i = 0; i < size; i++)
                                        l1j.server.server.model.inventory.warehouse.Warehouse.recordCommittedMove(
                                                pc, source, false, sources[i], counts[i]);
                                    pc.endItemTransfer(token);
                                    if (onCommitted != null) {
                                        try { onCommitted.run(); }
                                        catch (RuntimeException auditFailure) {
                                            java.util.logging.Logger.getLogger(CInventory.class.getName())
                                                    .log(java.util.logging.Level.WARNING,
                                                            "Committed warehouse withdrawal audit failed", auditFailure);
                                        }
                                    }
                                }
                            }
                        }
                    }, move::finishOwnerApply));
                    submitted = true;
                    return true;
                }
            }
        } finally {
            if (!submitted) pc.endItemTransfer(token);
        }
    }

}
