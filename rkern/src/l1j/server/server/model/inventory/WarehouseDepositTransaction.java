package l1j.server.server.model.inventory;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.inventory.warehouse.Warehouse;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;

/** Warehouse deposit execution. Existing inventory and warehouse monitors own all state. */
final class WarehouseDepositTransaction {
    private WarehouseDepositTransaction() {}

    /** Persist selected private, clan or elf warehouse deposits in one CacheD receipt.
     * Memory is published only after the complete DB transaction commits.
     */
    static boolean RequestBatch(CInventory ownerInventory, int[] ids, int[] counts,
            Warehouse dest, Runnable onCommitted) {
        if (!(ownerInventory instanceof CUserInventory) || ids == null || counts == null
                || ids.length <= 1 || ids.length != counts.length || ids.length > 100
                || dest == null || !(dest instanceof l1j.server.server.model.inventory.warehouse.PrivateWarehouse
                    || dest instanceof l1j.server.server.model.inventory.warehouse.ClanWarehouse
                    || dest instanceof l1j.server.server.model.inventory.warehouse.ElfWarehouse)
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
            final L1ItemInstance[] sources = new L1ItemInstance[size];
            final int[] sourceCounts = new int[size];
            final int[] group = new int[size];
            final long[] groupTotals = new long[size];
            final L1ItemInstance[] destinations = new L1ItemInstance[size];
            final int[] destinationCounts = new int[size];
            final L1ItemInstance[] carry = new L1ItemInstance[size];
            synchronized (dest) {
                int newSlots = 0;
                for (int i = 0; i < size; i++) {
                    final L1ItemInstance item = ownerInventory.GetByServerID(ids[i]);
                    final int count = counts[i];
                    if (item == null || item.getId() != ids[i] || !item.IsTransferableAmount(count))
                        return false;
                    for (int j = 0; j < i; j++) if (ids[j] == ids[i]) return false;
                    sources[i] = item;
                    sourceCounts[i] = item.getCount();
                    int g = i;
                    if (item.isStackable()) {
                        for (int j = 0; j < i; j++) {
                            if (L1ItemInstance.CanMergeStack(sources[j], item)) {
                                g = group[j];
                                break;
                            }
                        }
                    }
                    group[i] = g;
                    if (g == i) {
                        destinations[i] = item.isStackable() ? dest.findCompatibleStack(item) : null;
                        destinationCounts[i] = destinations[i] == null ? 0 : destinations[i].getCount();
                        if (destinations[i] == null) newSlots++;
                    }
                    groupTotals[g] += count;
                    if (groupTotals[g] + destinationCounts[g] > CInventory.MAX_AMOUNT) return false;
                }
                final int maxSlots = dest instanceof l1j.server.server.model.inventory.warehouse.ClanWarehouse
                        ? Config.MAX_CLAN_WAREHOUSE_ITEM : Config.MAX_PERSONAL_WAREHOUSE_ITEM;
                if (dest.getSize() + newSlots > maxSlots) return false;
                final CacheD.WarehouseMove reserved;
                try { reserved = CacheD.beginWarehouseMove(pc.getId(), dest.transferOrderKey()); }
                catch (java.util.concurrent.RejectedExecutionException busy) { return false; }
                try (CacheD.WarehouseMove move = reserved) {
                    move.onFailure(() -> pc.quarantineItemTransferSession(session, stamp));
                    for (int i = 0; i < size; i++) {
                        final L1ItemInstance item = sources[i];
                        final int count = counts[i];
                        move.expectOriginal(item);
                        if (count == sourceCounts[i]) {
                            carry[i] = item;
                            move.prepareCharacterDelete(item);
                        } else {
                            carry[i] = Warehouse.allocateDetachedTransfer(item, count);
                            move.prepareCarry(item.getId(), carry[i]);
                            move.prepareCharacterWrite(Warehouse.transferCopy(item, item.getId(),
                                    sourceCounts[i] - count, item.getChargeCount()), false);
                        }
                    }
                    for (int i = 0; i < size; i++) {
                        if (group[i] != i) continue;
                        final int newCount = (int) (groupTotals[i] + destinationCounts[i]);
                        if (destinations[i] != null) {
                            move.expectOriginal(destinations[i]);
                            dest.updateItem(Warehouse.transferCopy(destinations[i],
                                    destinations[i].getId(), newCount,
                                    destinations[i].getChargeCount()));
                        } else {
                            dest.insertItem(Warehouse.transferCopy(carry[i], carry[i].getId(),
                                    newCount, carry[i].getChargeCount()));
                        }
                    }
                    move.commit(() -> Warehouse.replayCommittedMove(pc,
                            stamp, token, move, () -> {
                        synchronized (session) {
                            synchronized (ownerInventory) {
                                synchronized (dest) {
                                    if (!pc.isItemTransferReplaySession(session, stamp, token))
                                        throw new IllegalStateException("Stale warehouse batch session");
                                    for (int i = 0; i < size; i++) {
                                        if (ownerInventory.GetByServerID(ids[i]) != sources[i]
                                                || sources[i].getCount() != sourceCounts[i])
                                            throw new IllegalStateException("Warehouse batch source changed after COMMIT");
                                        if (group[i] == i && destinations[i] != null
                                                && (dest.GetByServerID(destinations[i].getId()) != destinations[i]
                                                || destinations[i].getCount() != destinationCounts[i]))
                                            throw new IllegalStateException("Warehouse batch destination changed after COMMIT");
                                    }
                                    CacheD.applyCommittedWarehouseMove(move, () -> {
                                        for (int i = 0; i < size; i++) {
                                            L1ItemInstance result = RequestLocked(ownerInventory, sources[i], counts[i], dest, null);
                                            if (result == null)
                                                throw new IllegalStateException("Warehouse batch memory replay failed item=" + ids[i]);
                                            if (move.replayCarry(ids[i]) == result)
                                                CWorld.getInstance().storeObject(result);
                                        }
                                    });
                                    for (int i = 0; i < size; i++)
                                        Warehouse.recordCommittedMove(pc, dest, true, sources[i], counts[i]);
                                    if (onCommitted != null) {
                                        try { onCommitted.run(); }
                                        catch (RuntimeException auditFailure) {
                                            java.util.logging.Logger.getLogger(CInventory.class.getName())
                                                    .log(java.util.logging.Level.WARNING,
                                                         "Committed warehouse deposit audit dispatch failed", auditFailure);
                                        }
                                    }
                                    try { pc.refreshLight(); }
                                    catch (RuntimeException lightFailure) {
                                        java.util.logging.Logger.getLogger(CInventory.class.getName())
                                                .log(java.util.logging.Level.WARNING,
                                                     "Warehouse batch light refresh failed", lightFailure);
                                    }
                                }
                            }
                        }
                    }));
                    submitted = true;
                    return true;
                }
            }
        } finally {
            if (!submitted) pc.endItemTransfer(token);
        }
    }

    private static L1ItemInstance Prepare(CInventory ownerInventory, L1ItemInstance item, int count, Warehouse dest,
            Runnable onCommitted) {
        if (!(ownerInventory instanceof CUserInventory)) return null;
        final CUserInventory pcInventory = (CUserInventory) ownerInventory;
        final l1j.server.server.model.Instance.L1PcInstance pc = pcInventory.getOwner();
        final CUserSocket session = pc.getNetConnection();
        final int sessionStamp = pc.getSessionStamp();
        final int token = pc.tryReserveItemTransfer(session, sessionStamp);
        if (token == 0) return null;
        boolean submitted = false;
        try {
        final int sourceCount = item.getCount();
        final L1ItemInstance destStack = item.isStackable() ? dest.findCompatibleStack(item) : null;
        final int destCount = destStack == null ? 0 : destStack.getCount();
        if (destStack != null && !destStack.IsAddableAmount(count)) return null;

        final CacheD.WarehouseMove reserved;
        try { reserved = CacheD.beginWarehouseMove(pc.getId(), dest.transferOrderKey()); }
        catch (java.util.concurrent.RejectedExecutionException busy) { return null; }
        try (CacheD.WarehouseMove move = reserved) {
            move.onFailure(() -> pc.quarantineItemTransferSession(session, sessionStamp));
            move.expectOriginal(item);
            if (destStack != null) move.expectOriginal(destStack);
            final L1ItemInstance carry;
            if (sourceCount == count) {
                carry = item;
                move.prepareCharacterDelete(item);
            } else {
                carry = Warehouse.allocateDetachedTransfer(item, count);
                move.prepareCarry(carry);
                move.prepareCharacterWrite(Warehouse.transferCopy(item, item.getId(),
                        sourceCount - count, item.getChargeCount()), false);
            }
            if (destStack != null) {
                dest.updateItem(Warehouse.transferCopy(destStack, destStack.getId(),
                        destCount + count, destStack.getChargeCount()));
            } else {
                dest.insertItem(carry);
            }
            move.commit(() -> Warehouse.replayCommittedMove(pc,
                    sessionStamp, token, move, () -> {
                // RECV packet handling uses this same session monitor.
                synchronized (session) {
                    synchronized (ownerInventory) {
                        synchronized (dest) {
                            if (!pc.isItemTransferReplaySession(session, sessionStamp, token)
                                    || ownerInventory.GetByServerID(item.getId()) != item
                                    || item.getCount() != sourceCount
                                    || (destStack != null && (dest.GetByServerID(destStack.getId()) != destStack
                                            || destStack.getCount() != destCount)))
                                throw new IllegalStateException("Warehouse deposit preimage changed after DB COMMIT");
                            CacheD.applyCommittedWarehouseMove(move, () -> {
                                L1ItemInstance applied = RequestLocked(ownerInventory, item, count, dest, null);
                                if (applied == null)
                                    throw new IllegalStateException("Warehouse deposit replay failed");
                                if (move.replayCarry() == applied)
                                    CWorld.getInstance().storeObject(applied);
                            });
                        }
                    }
                    Warehouse.recordCommittedMove(pc, dest, true, item, count);
                    try { pc.refreshLight(); }
                    catch (RuntimeException lightFailure) {
                        java.util.logging.Logger.getLogger(CInventory.class.getName())
                                .log(java.util.logging.Level.WARNING, "Warehouse light refresh failed", lightFailure);
                    }
                    if (onCommitted != null) onCommitted.run();
                }
            }));
            submitted = true;
            return item; // accepted; game memory changes ONLY in post-COMMIT callback
        }
        } finally {
            if (!submitted) pc.endItemTransfer(token);
        }
    }

    // Both monitors are already held in inventory -> warehouse order.
    static L1ItemInstance RequestLocked(CInventory ownerInventory, L1ItemInstance item, int count,
            Warehouse inventory, Runnable onCommitted) {
        assert Thread.holdsLock(ownerInventory) && Thread.holdsLock(inventory);
        if (ownerInventory.rejectPendingItemTransfer())
            throw new IllegalStateException("Item mutation refused while CacheD transfer pending");
		if (item == null || inventory == null) {
			return null;
		}

		// caller가 들고 온 stale object를 믿지 않는다.
		final L1ItemInstance owned = ownerInventory.GetByServerID(item.getId());
		if (owned != item) {
			return null;
		}

        if (!item.IsTransferableAmount(count)) return null;
		if (inventory.CheckAddableToWarehouse(item, count) != CInventory.OK) {
			return null;
		}

        if (!CacheD.isWarehouseApplying()) {
            return Prepare(ownerInventory, item, count, inventory, onCommitted);
        }

        // Warehouse transfers must reserve BOTH DB owner lanes and persist
        // source and destination rows in the same JDBC transaction.
        if (!(ownerInventory instanceof CUserInventory)) return null;
        final int characterId = ((CUserInventory) ownerInventory).getOwner().getId();
        final CacheD.WarehouseMove reservation;
        try { reservation = CacheD.beginWarehouseMove(characterId, inventory.transferOrderKey()); }
        catch (java.util.concurrent.RejectedExecutionException busy) { return null; }
        try (CacheD.WarehouseMove move = reservation) {
        // If the SQL transaction fails, do not let the game client continue
        // mutating memory that is no longer known to match the authoritative DB.
        final CUserSocket session = ((CUserInventory) ownerInventory).getOwner().getNetConnection();
        if (session != null) move.onFailure(() ->
                CIOThreadCallback.getInstance().completeSession(session, session::kick));
        move.expectOriginal(item);
        L1ItemInstance destStack = item.isStackable() ? inventory.findCompatibleStack(item) : null;
        if (destStack != null) move.expectOriginal(destStack);
        final int originalCount = item.getCount();
        final int destinationCount = destStack == null ? 0 : destStack.getCount();
        L1ItemInstance carryItem = null;
        try {
		if (count == item.getCount()) {
			ownerInventory.Pop(item);
			carryItem = item;
		} else {
			carryItem = move.replayCarry(item.getId());
            if (carryItem == null)
                throw new IllegalStateException("Warehouse transfer missing committed item ID");

            // Deposit replay keeps package and expiry from the prepared carry.
            carryItem.CopyItemAttributesFrom(item, count, item.getChargeCount(), false, false, false);

			ownerInventory.removeItemCountCache(item.getItemId(), count);
			item.setCount(item.getCount() - count);
			ownerInventory.updateItem(item);
		}
        L1ItemInstance stored = null;
        RuntimeException transferFailure = null;
        try {
            stored = inventory.storeTradeItem(carryItem);
        } catch (RuntimeException e) {
            transferFailure = e;
        }
        if (stored != null) {
            move.commit();
            if (carryItem == item && stored != item) CWorld.getInstance().removeObject(item);
            return stored;
        }

		// The warehouse rejected the transfer after the source was changed.
		// Restore the source rather than completing a one-sided move.
		if (carryItem == item) {
			ownerInventory._items.Push(item);
			ownerInventory.insertItem(item);
		} else {
			item.setCount(item.getCount() + count);
			ownerInventory.addItemCountCache(item.getItemId(), count);
			ownerInventory.updateItem(item);
		}
        if (transferFailure != null) throw transferFailure;
		return null;
        } catch (RuntimeException failure) {
            // SQL was NEVER submitted. Restore the game state before the
            // reserved FIFO slot is cancelled by WarehouseMove.close().
            try {
                if (carryItem != null && inventory.GetByServerID(carryItem.getId()) == carryItem) {
                    inventory.deleteItem(carryItem);
                } else if (destStack != null && destStack.getCount() != destinationCount) {
                    destStack.setCount(destinationCount);
                    inventory.updateItem(destStack);
                }
                if (ownerInventory.GetByServerID(item.getId()) != item) {
                    item.setCount(originalCount);
                    ownerInventory._items.Push(item);
                    ownerInventory.insertItem(item);
                } else if (item.getCount() != originalCount) {
                    int delta = originalCount - item.getCount();
                    item.setCount(originalCount);
                    ownerInventory.addItemCountCache(item.getItemId(), delta);
                    ownerInventory.updateItem(item);
                }
            } catch (RuntimeException recovery) {
                failure.addSuppressed(recovery);
            }
            throw failure;
        }
        } // warehouse move scope

	}

}
