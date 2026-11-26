package l1j.server.server.storage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.storage.cached.CAccount;
import l1j.server.server.storage.cached.CItem;
import l1j.server.server.storage.cached.CPendingState;
import l1j.server.server.storage.cached.CTransaction;
import l1j.server.server.storage.cached.CUser;
import l1j.server.server.storage.cached.CWareHouse;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage;
import l1j.server.server.storage.mysql.MySqlCharactersItemStorage.ItemSnapshot;

public final class CacheD {

    private static final Logger LOG = Logger.getLogger(CacheD.class.getName());

    private static final int WORKER_COUNT = 4;
    private static final int MAX_DB_JOBS = 2048;
    private static final ThreadLocal<DbJob> DB_JOB_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<TradeScope> TRADE_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<WarehouseMove> WAREHOUSE_MOVE_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<WarehouseMove> WAREHOUSE_APPLY_CONTEXT = new ThreadLocal<>();
    private static final int MAX_ACCOUNTS = 16384;
    private static final int MAX_CHARACTERS = 16384;
    private static final int MAX_ITEMS = 131072;
    private static final long COALESCE_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    private static final long MAX_RETRY_NANOS = TimeUnit.SECONDS.toNanos(5);

    /** SQL is confirmed committed, but ownership-index/replay dispatch failed.
     * This is never a rolled-back operation and must retain the owner barriers.
     */
    public static final class CommittedReplayException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public CommittedReplayException(String message, Throwable cause) { super(message, cause); }
    }

    private static final class ItemCommand extends CPendingState {
        final ItemSnapshot snapshot;
        final boolean delete;
        final int ownerId;
        final int previousOwnerId;
        final int auxiliaryTemplateId; // 49016..49025 letter item
        final int mailboxKey;          // second FIFO owner only for physical letters
        final String mailReceiver;
        final String auxiliaryReceiptId;
        boolean finished;
        SQLException error;

        ItemCommand(ItemSnapshot snapshot, boolean delete, int ownerId, int previousOwnerId,
                    int auxiliaryTemplateId, int mailboxKey, String mailReceiver) {
            this.snapshot = snapshot;
            this.delete = delete;
            this.ownerId = ownerId;
            this.previousOwnerId = previousOwnerId;
            this.auxiliaryTemplateId = auxiliaryTemplateId;
            this.mailboxKey = mailboxKey;
            this.mailReceiver = mailReceiver;
            this.auxiliaryReceiptId = auxiliaryTemplateId == 0 ? null : UUID.randomUUID().toString();
        }
    }

    private static final int[] NO_EXTRA_OWNERS = new int[0];
    private static final class DbJob extends CPendingState {
        Runnable task;
        final int ownerId;
        final int secondaryOwnerId;
        final int[] extraOwnerIds;
        final boolean retryOnFailure;
        final boolean failClosedOnError;
        boolean commitConfirmed;
        boolean outcomeUnknown;
        boolean prepared = true;
        // A staged (not yet published) warehouse write reserves both owner lanes.
        // Indexed under the CacheD lock until commit() or close() releases it.
        boolean unpreparedWarehouseCounted;
        // A committed transaction retains its existing FIFO lanes until the
        // CIO owner acknowledges memory replay. No DB worker waits for it.
        boolean ownerApplyRequired;
        boolean awaitingOwnerApply;
        boolean ownerApplyFinished;
        Throwable ownerApplyFailure;
        Consumer<Throwable> afterOwnerReleased;

        DbJob(int ownerId, int secondaryOwnerId, Runnable task, boolean retryOnFailure) {
            this.ownerId = ownerId;
            this.secondaryOwnerId = secondaryOwnerId;
            this.extraOwnerIds = NO_EXTRA_OWNERS;
            this.task = task;
            this.retryOnFailure = retryOnFailure;
            this.failClosedOnError = false;
        }

        DbJob(int ownerId, int secondaryOwnerId, Runnable task, boolean retryOnFailure,
              boolean failClosedOnError) {
            this(ownerId, secondaryOwnerId, NO_EXTRA_OWNERS, task, retryOnFailure, failClosedOnError);
        }
        DbJob(int ownerId, int secondaryOwnerId, int[] extraOwnerIds, Runnable task,
              boolean retryOnFailure, boolean failClosedOnError) {
            this.ownerId = ownerId;
            this.secondaryOwnerId = secondaryOwnerId;
            this.extraOwnerIds = extraOwnerIds;
            this.task = task;
            this.retryOnFailure = retryOnFailure;
            this.failClosedOnError = failClosedOnError;
        }
    }

    /** An SQL-only settlement batch; game state is never touched by a DB worker. */
    public static final class TradeScope implements AutoCloseable {
        private final boolean staging;
        private final String receiptId = UUID.randomUUID().toString();
        private final int firstOwner;
        private final int secondOwner;
        private final LinkedHashMap<Integer, ItemSnapshot> writes = new LinkedHashMap<>();
        private final IntOpenHashSet removes = new IntOpenHashSet();
        private boolean submitted;
        private boolean closed;
        private DbJob submittedJob;
        private volatile Runnable failureAction;

        public void onFailure(Runnable action) {
            if (closed || submitted || staging)
                throw new IllegalStateException("Trade failure handler must precede submission");
            failureAction = Objects.requireNonNull(action);
        }

        private TradeScope(boolean staging, int firstOwner, int secondOwner) {
            this.staging = staging;
            this.firstOwner = firstOwner;
            this.secondOwner = secondOwner;
        }

        private void write(int ownerId, L1ItemInstance item) {
            if (staging) return;
            if (closed || submitted || item == null || item.getId() <= 0
                    || (ownerId != firstOwner && ownerId != secondOwner)) {
                throw new IllegalStateException("Invalid trade item owner or state");
            }
            final ItemSnapshot snapshot = ItemSnapshot.capture(item, ownerId);
            writes.put(snapshot.id, snapshot);
            removes.remove(snapshot.id);
        }

        private void remove(int itemId) {
            if (staging) return;
            if (closed || submitted || itemId <= 0)
                throw new IllegalStateException("Invalid trade item deletion");
            writes.remove(itemId);
            removes.add(itemId);
        }

        /** Add the future SQL row without mutating the current game inventory. */
        public void projectItem(int ownerId, L1ItemInstance item, int finalCount, int finalCharge) {
            if (closed || submitted || staging || item == null || item.getId() <= 0
                    || (ownerId != firstOwner && ownerId != secondOwner))
                throw new IllegalStateException("Invalid projected trade write");
            final ItemSnapshot snapshot = ItemSnapshot.project(item, ownerId, finalCount, finalCharge);
            writes.put(snapshot.id, snapshot);
            removes.remove(snapshot.id);
        }

        /** Captures a split-stack remainder that was held in escrow without an SQL update. */
        public void trackRemainder(int ownerId, L1ItemInstance item) {
            if (closed || staging) throw new IllegalStateException("Not a settlement scope");
            write(ownerId, item);
        }

        /** An escrow item merged into another stack must have its old DB row deleted. */
        public void removeMergedItem(int id) {
            if (closed || staging) throw new IllegalStateException("Not a settlement scope");
            remove(id);
        }

        /** Commit all captured changes on ONE JDBC connection; failed commits remain ordered for retry. */
        public void commit() {
            commit(null);
        }

        public void commit(Runnable onCommitted) {
            if (closed || staging || submitted) throw new IllegalStateException("Invalid settlement scope");
            final ItemSnapshot[] values = writes.values().toArray(new ItemSnapshot[0]);
            final int[] deletes = removes.toIntArray();
            // Native CItem::CommitTransation checks its target owner/warehouse
            // before mutating the transaction. Reject an inconsistent batch
            // BEFORE admission, while the caller can still restore its moves.
            for (ItemSnapshot value : values) {
                if (value == null || value.id <= 0 || value.itemId <= 0 || value.count <= 0
                        || (value.ownerId != firstOwner && value.ownerId != secondOwner)) {
                    throw new IllegalStateException("Invalid settlement item snapshot");
                }
            }
            for (int id : deletes) {
                if (id <= 0 || writes.containsKey(id))
                    throw new IllegalStateException("Conflicting settlement item delete: " + id);
            }
            final CacheD cache = CacheD.getInstance();
            synchronized (cache.lock) {
                cache.checkItemOwner(firstOwner);
                cache.checkItemOwner(secondOwner);
            }
            // Admit the DB job and its ownership index atomically. Otherwise
            // the worker may finish before the second lock acquisition and
            // a stale owner can be reinserted AFTER the committed transaction.
            synchronized (cache.lock) {
                submittedJob = cache.submitDbJobInternal(firstOwner, secondOwner, () -> {
                    try {
                        CTransaction.persist(receiptId, values, deletes, firstOwner, secondOwner);
                    } catch (SQLException | RuntimeException e) {
                        Runnable action = failureAction;
                        failureAction = null;
                        if (action != null) {
                            try { action.run(); }
                            catch (Throwable notifyFailure) { e.addSuppressed(notifyFailure); }
                        }
                        throw new IllegalStateException("Atomic trade persistence failed", e);
                    }
                    // The in-memory ownership index reflects durable DB state,
                    // never a merely submitted asynchronous transaction.
                    synchronized (cache.lock) {
                        for (ItemSnapshot value : values) cache.warehouse.trackItemOwner(value.id, value.ownerId);
                        for (int id : deletes) cache.warehouse.trackItemOwner(id, 0);
                    }
                    // Game-state reapplication is part of durable completion.
                    // A failed apply must freeze both owners; never silently
                    // release a DB-confirmed transaction with stale game memory.
                    if (onCommitted != null) {
                        try { onCommitted.run(); }
                        catch (RuntimeException e) {
                            Runnable action = failureAction;
                            failureAction = null;
                            if (action != null) {
                                try { action.run(); }
                                catch (Throwable notifyFailure) { e.addSuppressed(notifyFailure); }
                            }
                            throw e;
                        }
                    }
                    if (onCommitted == null) failureAction = null;
                }, false, true);
                if (onCommitted != null) cache.requireOwnerApply(submittedJob);
                submitted = true;
            }
        }

        /** Acknowledge the existing CIO owner's asynchronous replay exactly once. */
        public void finishOwnerApply(Throwable failure) {
            final CacheD cache = CacheD.getInstance();
            if (failure != null) {
                final Runnable action = failureAction;
                failureAction = null;
                if (action != null) {
                    try { action.run(); }
                    catch (Throwable notification) { failure.addSuppressed(notification); }
                }
            } else failureAction = null;
            cache.finishOwnerApply(submittedJob, failure);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (TRADE_CONTEXT.get() != this) throw new IllegalStateException("Trade context mismatch");
            TRADE_CONTEXT.remove();
        }
    }

    /**
     * Captures both sides of ONE character <-> warehouse movement. The game
     * side still owns inventory state; only immutable SQL operations cross to
     * an existing ordered CacheD worker. Never submit half of a transfer.
     */
    public static final class WarehouseMove implements AutoCloseable {
        private final int characterId;
        private final String receiptId = UUID.randomUUID().toString();
        private final int warehouseKey;
        private final boolean economy;
        private final int[] extraOwnerIds;
        private java.util.function.Consumer<Throwable> errorAction;
        private final DbJob reservedJob;
        private CTransaction.Mutation[] committedOperations;
        private final ArrayList<CTransaction.Mutation> operations = new ArrayList<>(4);
        private final LinkedHashMap<Integer, Integer> finalOwners = new LinkedHashMap<>();
        private final Int2IntOpenHashMap originalCounts = new Int2IntOpenHashMap();
        // saveItem(COL_COUNT) and updateItem() may both capture the SAME
        // destination stack. Execute its final state once in the SQL tx.
        private final Int2IntOpenHashMap characterMutationIndex = new Int2IntOpenHashMap();
        private boolean submitted;
        private boolean closed;
        private volatile Runnable failureAction;
        private Runnable committedAction;
        private L1ItemInstance preparedCarry;
        private Int2ObjectOpenHashMap<L1ItemInstance> preparedBatchCarries;

        /** Owner-thread callback to disconnect a session after any DB failure.
         * Must only enqueue on the existing CIO owner; never touch game state on a DB worker.
         */
        public void onFailure(Runnable action) {
            if (WAREHOUSE_APPLY_CONTEXT.get() == this) return;
            if (closed || submitted) throw new IllegalStateException("Warehouse move already committed");
            failureAction = Objects.requireNonNull(action);
        }

        private WarehouseMove(int characterId, int warehouseKey) { this(characterId, warehouseKey, false, NO_EXTRA_OWNERS); }

        private WarehouseMove(int characterId, int warehouseKey, boolean economy) {
            this(characterId, warehouseKey, economy, NO_EXTRA_OWNERS);
        }
        private WarehouseMove(int characterId, int warehouseKey, boolean economy, int[] additional) {
            this(characterId, warehouseKey, economy, additional, false);
        }

        /** Mailbox lanes only order SQL; unlike the sender's item owner, they
         * never supply an inventory preimage. Queue behind pending mailbox
         * work instead of rejecting an unrelated sender's mail transaction.
         */
        private WarehouseMove(int characterId, int warehouseKey, boolean economy,
                int[] additional, boolean queueBehindMailboxWork) {
            this.economy = economy;
            this.characterId = characterId;
            this.warehouseKey = warehouseKey;
            if (additional == null || (!economy && additional.length != 0))
                throw new IllegalArgumentException("Invalid economy party owners");
            extraOwnerIds = additional.length == 0 ? NO_EXTRA_OWNERS : additional.clone();
            for (int i = 0; i < extraOwnerIds.length; i++) {
                int id = extraOwnerIds[i];
                if (id <= 0 || id == characterId || id == warehouseKey)
                    throw new IllegalArgumentException("Repeated economy owner");
                if (queueBehindMailboxWork) {
                    // Clan recipient keys are sorted and unique at admission.
                    // Validate in O(n), not O(n^2), on the game-owner thread.
                    if (i > 0 && id <= extraOwnerIds[i - 1])
                        throw new IllegalArgumentException("Unsorted or repeated mail owner");
                } else {
                    for (int j = 0; j < i; j++) if (id == extraOwnerIds[j])
                        throw new IllegalArgumentException("Repeated economy owner");
                }
            }
            final CacheD cache = CacheD.getInstance();
            synchronized (cache.lock) {
                if (!cache.transactionStoreReady)
                    throw new RejectedExecutionException("CacheD atomic storage schema is unavailable");
                cache.checkItemOwner(characterId);
                if (economy) cache.checkItemOwner(warehouseKey);
                for (int owner : extraOwnerIds) cache.checkItemOwner(owner);
                // A second move must not build its pre-image from game memory
                // until the preceding owner's SQL transaction has finished.
                // This also quarantines owners after an uncertain COMMIT.
                if (cache.dbJobsByOwner.get(characterId) != 0
                        || (!queueBehindMailboxWork && cache.dbJobsByOwner.get(warehouseKey) != 0))
                    throw new RejectedExecutionException("Prior warehouse DB write not yet committed");
                if (queueBehindMailboxWork) {
                    cache.rejectQuarantinedOwner(warehouseKey);
                    for (int owner : extraOwnerIds) cache.rejectQuarantinedOwner(owner);
                } else {
                    for (int owner : extraOwnerIds)
                        if (cache.dbJobsByOwner.get(owner) != 0)
                            throw new RejectedExecutionException("Prior party DB write not yet committed");
                }
                if (!cache.accepting) throw new RejectedExecutionException("CacheD is closed");
                cache.checkDbJobCapacity(characterId, warehouseKey, extraOwnerIds);
                // Reserve queue capacity and both FIFO lanes BEFORE touching
                // the game inventory. Failure here leaves memory unchanged.
                reservedJob = new DbJob(characterId, warehouseKey, extraOwnerIds, () -> {
                    try {
                        if (economy) CTransaction.persistEconomy(receiptId, characterId, warehouseKey, committedOperations);
                        else CTransaction.persistWarehouseMove(receiptId, characterId, warehouseKey, committedOperations);
                    } catch (Throwable failure) {
                        // A fatal throwable may have escaped connection cleanup
                        // after COMMIT. Without a returned result do not call it
                        // a confirmed rollback or reopen the economy owner.
                        final Throwable outcome = failure instanceof Error
                                ? new CTransaction.OutcomeUnknownException(
                                    "Transaction result unavailable receipt=" + receiptId, failure)
                                : failure;
                        notifyFailure(outcome);
                        throw new IllegalStateException("Warehouse move persistence", outcome);
                    }
                    markCommitConfirmed();
                    try {
                        // Change the cached ownership index only AFTER a confirmed
                        // durable COMMIT (including receipt-confirmed ACK loss).
                        // Failure must never advertise an uncommitted owner.
                        synchronized (cache.lock) {
                            for (Map.Entry<Integer, Integer> e : finalOwners.entrySet()) {
                                cache.warehouse.trackItemOwner(e.getKey(), e.getValue());
                            }
                        }
                        if (committedAction != null) {
                            final Runnable action = committedAction;
                            committedAction = null;
                            action.run();
                        } else {
                            failureAction = null;
                        }
                    } catch (Throwable failure) {
                        final CommittedReplayException committedFailure = new CommittedReplayException(
                                "Committed warehouse/economy replay dispatch failed receipt=" + receiptId, failure);
                        notifyFailure(committedFailure);
                        throw committedFailure;
                    }
                }, false, !economy);
                reservedJob.prepared = false;
                if (economy) cache.requireOwnerApply(reservedJob);
                else cache.countUnpreparedWarehouse(reservedJob);
                cache.pendingDbJobs++;
                cache.dbJobsByOwner.addTo(characterId, 1);
                if (warehouseKey != characterId) cache.dbJobsByOwner.addTo(warehouseKey, 1);
                for (int owner : extraOwnerIds) cache.dbJobsByOwner.addTo(owner, 1);
                cache.enqueueNext(reservedJob, 0L);
                cache.signalReadyChange();
            }
        }

        private void markCommitConfirmed() {
            // Executed by this job's DB worker; the completion decision follows
            // on that same worker under cache.lock, after task.run returns.
            reservedJob.commitConfirmed = true;
        }

        private void notifyFailure(Throwable failure) {
            final java.util.function.Consumer<Throwable> notify = errorAction;
            errorAction = null;
            committedAction = null;
            if (notify != null) {
                try { notify.accept(failure); }
                catch (Throwable notification) { suppressNotification(failure, notification); }
            }
            final Runnable action = failureAction;
            failureAction = null;
            if (action != null) {
                try { action.run(); }
                catch (Throwable notification) { suppressNotification(failure, notification); }
            }
        }

        private static void suppressNotification(Throwable failure, Throwable notification) {
            if (failure == notification) return;
            try { failure.addSuppressed(notification); }
            catch (Throwable ignored) { /* Preserve the original outcome classification. */ }
        }

        public void onError(java.util.function.Consumer<Throwable> action) {
            if (submitted || closed) throw new IllegalStateException("Already submitted");
            errorAction = Objects.requireNonNull(action);
        }

        private boolean isEconomyOwner(int id) {
            if (id == characterId || id == warehouseKey) return true;
            for (int owner : extraOwnerIds) if (owner == id) return true;
            return false;
        }
        /** Economy batch owns all reserved character/resource lanes. */
        public void prepareOwnedWrite(int ownerId, L1ItemInstance item, int previousOwner) {
            if (!economy || !isEconomyOwner(ownerId))
                throw new IllegalArgumentException("Unreserved economy owner");
            final ItemSnapshot image = ItemSnapshot.capture(item, ownerId);
            stageCharacterMutation(image.id, con -> {
                if (previousOwner == 0) MySqlCharactersItemStorage.insertTradeSnapshot(con, image);
                else MySqlCharactersItemStorage.updateTradeSnapshot(con, image, previousOwner);
            });
            finalOwners.put(image.id, ownerId);
        }

        public void prepareOwnedDelete(int ownerId, int itemId) {
            if (!economy || !isEconomyOwner(ownerId))
                throw new IllegalArgumentException("Unreserved economy owner");
            stageCharacterMutation(itemId, con -> MySqlCharactersItemStorage.deleteTradeSnapshot(con, itemId, ownerId));
            finalOwners.put(itemId, 0);
        }

        /** Call before modifying either the source or an existing destination stack. */
        public void expectOriginal(L1ItemInstance item) {
            if (WAREHOUSE_APPLY_CONTEXT.get() == this) return;
            if (closed || submitted || item == null || item.getId() <= 0 || item.getCount() <= 0)
                throw new IllegalStateException("Invalid warehouse original item");
            final int id = item.getId(), count = item.getCount();
            if (originalCounts.get(id) != 0 && originalCounts.get(id) != count)
                throw new IllegalStateException("Warehouse item changed during prepare: " + id);
            originalCounts.put(id, count);
        }

        public void prepareCharacterWrite(L1ItemInstance item, boolean insert) {
            writeItem(characterId, item, insert);
        }

        public void prepareCharacterDelete(L1ItemInstance item) {
            deleteItem(item);
        }

        public void prepareCarry(L1ItemInstance item) {
            if (closed || submitted) throw new IllegalStateException("Warehouse move closed");
            preparedCarry = item;
        }

        public L1ItemInstance replayCarry() {
            return WAREHOUSE_APPLY_CONTEXT.get() == this ? preparedCarry : null;
        }

        public void prepareCarry(int sourceId, L1ItemInstance carry) {
            if (closed || submitted || WAREHOUSE_APPLY_CONTEXT.get() == this
                    || sourceId <= 0 || carry == null || carry.getId() <= 0)
                throw new IllegalStateException("Invalid batch warehouse carry");
            if (preparedBatchCarries == null)
                preparedBatchCarries = new Int2ObjectOpenHashMap<>();
            if (preparedBatchCarries.putIfAbsent(sourceId, carry) != null)
                throw new IllegalStateException("Repeated batch warehouse source " + sourceId);
        }

        public L1ItemInstance replayCarry(int sourceId) {
            if (WAREHOUSE_APPLY_CONTEXT.get() != this) return null;
            return preparedBatchCarries == null ? preparedCarry : preparedBatchCarries.get(sourceId);
        }

        private int originalCount(int id) {
            if (originalCounts.get(id) == 0)
                throw new IllegalStateException("Untracked warehouse move item: " + id);
            return originalCounts.get(id);
        }

        private void add(CTransaction.Mutation step) {
            if (closed || submitted) throw new IllegalStateException("Warehouse move already closed");
            operations.add(Objects.requireNonNull(step));
        }

        private void writeItem(int ownerId, L1ItemInstance item, boolean insert) {
            if (WAREHOUSE_APPLY_CONTEXT.get() == this) return;
            if (ownerId != characterId || item == null || item.getId() <= 0
                    || item.getItemId() <= 0 || item.getCount() <= 0) {
                throw new IllegalStateException("Invalid warehouse transfer character item");
            }
            final ItemSnapshot snapshot = ItemSnapshot.capture(item, ownerId);
            final int before = insert ? -1 : originalCount(snapshot.id);
            final CTransaction.Mutation mutation = con ->
                    CTransaction.writeCharacterMoveRow(con, snapshot, insert, before);
            stageCharacterMutation(snapshot.id, mutation);
            finalOwners.put(snapshot.id, ownerId);
        }

        private void deleteItem(L1ItemInstance item) {
            if (WAREHOUSE_APPLY_CONTEXT.get() == this) return;
            if (item == null || item.getId() <= 0 || item.getItemId() <= 0)
                throw new IllegalStateException("Invalid warehouse transfer item deletion");
            final int id = item.getId(), template = item.getItemId();
            final int before = originalCount(id);
            stageCharacterMutation(id, con ->
                    CTransaction.deleteCharacterMoveRow(con, id, characterId, template, before));
            finalOwners.put(id, 0);
        }

        private void stageCharacterMutation(int itemId, CTransaction.Mutation mutation) {
            if (characterMutationIndex.get(itemId) != 0) {
                operations.set(characterMutationIndex.get(itemId) - 1, mutation);
            } else {
                characterMutationIndex.put(itemId, operations.size() + 1);
                add(mutation);
            }
        }

        /** Called by warehouse subclasses ONLY for their SQL half of the move. */
        public void stageWarehouseSql(CTransaction.Mutation mutation) {
            if (WAREHOUSE_APPLY_CONTEXT.get() != this) add(mutation);
        }

        public void commit() { commit(null); }

        public void commit(Runnable onCommitted) {
            if (WAREHOUSE_APPLY_CONTEXT.get() == this) return;
            if (submitted || closed || operations.size() < (economy ? 1 : 2))
                throw new IllegalStateException("Incomplete warehouse transfer persistence");
            final CacheD cache = CacheD.getInstance();
            final ArrayList<Runnable> logouts;
            synchronized (cache.lock) {
                committedOperations = operations.toArray(new CTransaction.Mutation[0]);
                committedAction = onCommitted;
                if (onCommitted != null) cache.requireOwnerApply(reservedJob);
                submitted = true;
                reservedJob.prepared = true;
                cache.ready.refresh(reservedJob);
                cache.uncountUnpreparedWarehouse(reservedJob);
                logouts = cache.takeReadyLogouts(reservedJob);
                cache.signalReadyChange();
            }
            resumeLogouts(logouts);
        }

        /** Retire/quarantine the reserved FIFO job after CIO memory replay. */
        public void finishOwnerApply(Throwable failure) {
            finishOwnerApply(failure, null);
        }

        /** The first acknowledgement owns the optional post-release callback.
         * It can run on either the CIO or SQL worker, always outside the queue lock. */
        public void finishOwnerApply(Throwable failure, Consumer<Throwable> afterReleased) {
            final CacheD cache = CacheD.getInstance();
            if (failure != null) {
                final Runnable action = failureAction;
                failureAction = null;
                if (action != null) {
                    try { action.run(); }
                    catch (Throwable notification) { failure.addSuppressed(notification); }
                }
            } else failureAction = null;
            cache.finishOwnerApply(reservedJob, failure, afterReleased);
        }

        @Override public void close() {
            if (WAREHOUSE_APPLY_CONTEXT.get() == this) return;
            if (closed) return;
            closed = true;
            if (WAREHOUSE_MOVE_CONTEXT.get() != this)
                throw new IllegalStateException("Warehouse transfer scope mismatch");
            WAREHOUSE_MOVE_CONTEXT.remove();
            if (!submitted) {
                final CacheD cache = CacheD.getInstance();
                final ArrayList<Runnable> logouts;
                synchronized (cache.lock) {
                    if (!cache.ready.remove(reservedJob))
                        throw new IllegalStateException("Warehouse move reservation lost");
                    cache.uncountUnpreparedWarehouse(reservedJob);
                    cache.completeDbJob(reservedJob);
                    logouts = cache.takeReadyLogouts(reservedJob);
                    cache.signalReadyChange();
                }
                resumeLogouts(logouts);
            }
        }
    }

    public static WarehouseMove beginWarehouseMove(int characterId, int warehouseKey) {
        WarehouseMove replay = WAREHOUSE_APPLY_CONTEXT.get();
        if (replay != null) {
            if (replay.characterId != characterId || replay.warehouseKey != warehouseKey)
                throw new IllegalStateException("Warehouse replay owner mismatch");
            return replay;
        }
        if (characterId <= 0 || warehouseKey <= 0 || characterId == warehouseKey)
            throw new IllegalArgumentException("Invalid warehouse move owners");
        if (TRADE_CONTEXT.get() != null || WAREHOUSE_MOVE_CONTEXT.get() != null)
            throw new IllegalStateException("Nested CacheD persistence scope");
        WarehouseMove scope = new WarehouseMove(characterId, warehouseKey);
        WAREHOUSE_MOVE_CONTEXT.set(scope);
        return scope;
    }

    /** An offline pledge mutation must never overtake a retiring PC snapshot.
     * Check session absence and reserve the existing receipt lanes atomically. */
    public static WarehouseMove beginOfflinePledgeMove(int characterId, int pledgeLane, String name) {
        Objects.requireNonNull(name, "name");
        final CacheD cache = getInstance();
        synchronized (cache.lock) {
            final CUser pending = cache.characters.get(name);
            if (cache.activeUsers.containsKey(characterId)
                    || (pending != null && pending.savedVersion < pending.version))
                throw new RejectedExecutionException("Character session or save still retiring");
            return beginEconomyMove(characterId, pledgeLane);
        }
    }

    /** Check every member's save/session preimage and reserve all lanes in
     * the same admission boundary; no older character save may follow SQL. */
    public static WarehouseMove beginPledgeDismissMove(int leaderId, int vaultLane,
            int[] otherOwners, int[] memberIds, String[] names, boolean[] online) {
        final CacheD cache = getInstance();
        synchronized (cache.lock) {
            if (memberIds.length != names.length || names.length != online.length)
                throw new IllegalArgumentException("Pledge dismissal roster");
            for (int i = 0; i < memberIds.length; i++) {
                if (cache.activeUsers.containsKey(memberIds[i]) != online[i])
                    throw new RejectedExecutionException("Pledge member session changing: " + memberIds[i]);
                if (!cache.flushCharacter(names[i], 0L))
                    throw new RejectedExecutionException("Pledge member save still pending: " + memberIds[i]);
            }
            return beginEconomyMove(leaderId, vaultLane, otherOwners);
        }
    }

    /** Reserve the existing two FIFO lanes for an immutable economic batch. */
    public static WarehouseMove beginEconomyMove(int ownerId, int secondaryKey) {
        return beginEconomyMove(ownerId, secondaryKey, NO_EXTRA_OWNERS);
    }

    public static WarehouseMove beginEconomyMove(int ownerId, int secondaryKey, int[] additionalOwners) {
        if (ownerId <= 0 || secondaryKey <= 0 || ownerId == secondaryKey)
            throw new IllegalArgumentException("Economy owner lanes");
        if (TRADE_CONTEXT.get() != null || WAREHOUSE_MOVE_CONTEXT.get() != null)
            throw new IllegalStateException("Nested CacheD persistence scope");
        WarehouseMove scope = new WarehouseMove(ownerId, secondaryKey, true, additionalOwners);
        WAREHOUSE_MOVE_CONTEXT.set(scope);
        return scope;
    }

    /** Mail-only economy receipt: keep the sender's item preimage admission
     * strict; FIFO-queue the recipient mailbox lanes regardless of prior jobs.
     * A quarantined earlier transaction closes admission for that mailbox.
     */
    public static WarehouseMove beginMailEconomyMove(int senderId, int mailboxKey, int[] otherMailboxKeys) {
        if (senderId <= 0 || mailboxKey <= 0 || senderId == mailboxKey
                || otherMailboxKeys == null)
            throw new IllegalArgumentException("Mail owner lanes");
        if (TRADE_CONTEXT.get() != null || WAREHOUSE_MOVE_CONTEXT.get() != null)
            throw new IllegalStateException("Nested CacheD persistence scope");
        WarehouseMove scope = new WarehouseMove(senderId, mailboxKey, true, otherMailboxKeys, true);
        WAREHOUSE_MOVE_CONTEXT.set(scope);
        return scope;
    }

    public static boolean isWarehouseApplying() { return WAREHOUSE_APPLY_CONTEXT.get() != null; }

    public static void applyCommittedWarehouseMove(WarehouseMove move, Runnable action) {
        if (WAREHOUSE_APPLY_CONTEXT.get() != null || WAREHOUSE_MOVE_CONTEXT.get() != null)
            throw new IllegalStateException("Nested warehouse apply");
        WAREHOUSE_APPLY_CONTEXT.set(move);
        try { action.run(); } finally { WAREHOUSE_APPLY_CONTEXT.remove(); }
    }

    /** Expected DB count recorded on the game owner before in-memory mutation. */
    public static int warehouseMoveOriginalCount(int itemId) {
        WarehouseMove move = WAREHOUSE_MOVE_CONTEXT.get();
        return move == null ? -1 : move.originalCount(itemId);
    }

    /** Warehouse SQL is staged, not independently enqueued, during a move. */
    public static boolean stageWarehouseMoveSql(CTransaction.Mutation operation) {
        WarehouseMove move = WAREHOUSE_MOVE_CONTEXT.get();
        if (move == null) move = WAREHOUSE_APPLY_CONTEXT.get();
        if (move == null) return false;
        move.stageWarehouseSql(operation);
        return true;
    }

    /** Ordinary trade-window escrow is in memory: do not delete persisted items on offer. */
    /** Only the CacheD-suppressed, post-commit inventory replay may bypass an
     * in-flight persistent transfer's item-mutation gate. */
    public static boolean isTradeWindowStaging() {
        final TradeScope scope = TRADE_CONTEXT.get();
        return scope != null && scope.staging;
    }

    public static boolean isItemTransferReplaying() {
        return isWarehouseApplying() || isTradeWindowStaging();
    }

    public static TradeScope openTradeWindow() {
        if (TRADE_CONTEXT.get() != null || WAREHOUSE_MOVE_CONTEXT.get() != null) throw new IllegalStateException("Nested trade capture");
        TradeScope scope = new TradeScope(true, 0, 0);
        TRADE_CONTEXT.set(scope);
        return scope;
    }

    public static TradeScope openTradeSettlement(int firstOwner, int secondOwner) {
        if (firstOwner <= 0 || secondOwner <= 0 || firstOwner == secondOwner)
            throw new IllegalArgumentException("Trade owners");
        if (TRADE_CONTEXT.get() != null || WAREHOUSE_MOVE_CONTEXT.get() != null) throw new IllegalStateException("Nested trade capture");
        if (!getInstance().transactionStoreReady)
            throw new RejectedExecutionException("CacheD atomic storage schema is unavailable");
        TradeScope scope = new TradeScope(false, firstOwner, secondOwner);
        TRADE_CONTEXT.set(scope);
        return scope;
    }

    private static final class Holder {
        static final CacheD INSTANCE = new CacheD();
    }

    public static CacheD getInstance() {
        return Holder.INSTANCE;
    }

    private final Object lock = new Object();
    private final Map<String, CAccount> accounts = new HashMap<>();
    private final Map<String, CUser> characters = new HashMap<>();
    // Active reference design account -> character cache links. Entries reference the
    // SAME CAccount/CUser states used for pending writes, never game objects.
    // Not part of pending SQL counts or worker shutdown conditions.
    private final Map<String, CAccount> activeAccounts = new HashMap<>();
    private final Int2ObjectOpenHashMap<CUser> activeUsers = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<CItem> items = new Int2ObjectOpenHashMap<>();
    private final CWareHouse warehouse = new CWareHouse();
    // Compatibility alias for the existing diagnostic/owner-boundary harness.
    private final Int2IntOpenHashMap itemOwners = warehouse.itemOwners;
    private final Int2IntOpenHashMap itemCommandsByOwner = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap dbJobsByOwner = new Int2IntOpenHashMap();
    // Allocated only for closing caches with outstanding ordered writes.
    // One callback per warehouse identity, keyed by its existing owner lane.
    private final Int2ObjectOpenHashMap<java.util.IdentityHashMap<Object, Runnable>>
            warehouseRetirements = new Int2ObjectOpenHashMap<>();
    private final Int2IntOpenHashMap ownerApplyJobsByOwner = new Int2IntOpenHashMap();
    // Allocated only after an unresolved transaction. Independent of FIFO heads,
    // which can change if a dirty item changes owner while it is being written.
    private IntOpenHashSet quarantinedOwnerIds;
    // Allocated only for a logout that overlaps a durable owner operation.
    private Int2ObjectOpenHashMap<ArrayList<Runnable>> logoutWaiters;
    // Only non-economy warehouse reservations that are still unprepared.
    // Avoid a linear ready-queue scan on every owner/session boundary check.
    private final Int2IntOpenHashMap unpreparedWarehouseJobsByOwner = new Int2IntOpenHashMap();
    private final IntOpenHashSet commandingItemIds = new IntOpenHashSet();
    private final IntOpenHashSet deletingItemIds = new IntOpenHashSet();
    private final HashSet<String> deletingNames = new HashSet<>();
    private final IntOpenHashSet deletingIds = new IntOpenHashSet();
    private final IntOpenHashSet deletedIds = new IntOpenHashSet();
    private int pendingItemCommands;
    private int pendingDbJobs;
    private int activeDbJobs;
    // One intrusive FIFO: O(1) arbitrary removal, no ArrayDeque array shifts
    // and no iterator allocation in the blocked-owner scan. All operations
    // are under CacheD.lock; CPendingState lives for the duration of the job.
    /**
     * All mutations are protected by CacheD.lock. Each pending state belongs
     * to up to six owner lanes (six only for party economy jobs) and one item lane. Only the FIFO head of
     * EVERY occupied lane is a dispatch candidate. Inactive, delayed and
     * unprepared heads still reserve their lanes, just as the old FIFO scan.
     *
     * Candidates also require free resources. A claim removes the selected
     * head, and release rechecks only the affected lane heads.
     * Tree nodes are bounded by the 2,048 admitted DB jobs plus dirty states;
     * there is no per-dispatch collection, iterator or scan-array creation.
     */
    private final class ReadyQueue {
        private static final byte UNLINKED = 0, BLOCKED = 1, SLEEPING = 2, ELIGIBLE = 3;
        CPendingState head;
        private CPendingState tail;
        private int count;
        long mutationVersion;
        private long nextFirstOrder;
        private long nextLastOrder;

        private final java.util.Comparator<CPendingState> fifoOrder =
                (a, b) -> Long.compare(a.readyOrder, b.readyOrder);
        private final java.util.Comparator<CPendingState> dueOrder =
                (a, b) -> {
                    int result = Long.compare(a.dueAt, b.dueAt);
                    return result == 0 ? Long.compare(a.readyOrder, b.readyOrder) : result;
                };
        // Singleton resource lanes retain the state directly: 131k distinct
        // items must not allocate 131k TreeSet wrappers. A lane upgrades to
        // TreeSet only when two or more pending states share its resource.
        private final Int2ObjectOpenHashMap<Object> owners = new Int2ObjectOpenHashMap<>();
        private final Int2ObjectOpenHashMap<Object> itemsById = new Int2ObjectOpenHashMap<>();
        private final java.util.TreeSet<CPendingState> readyJobs = new java.util.TreeSet<>(fifoOrder);
        private final java.util.TreeSet<CPendingState> readyOther = new java.util.TreeSet<>(fifoOrder);
        private final java.util.TreeSet<CPendingState> sleeping = new java.util.TreeSet<>(dueOrder);

        CPendingState peekFirst() { return head; }
        int size() { return count; }

        void addFirst(CPendingState node) {
            if (node == head || node.readyPrev != null || node.readyNext != null || node.readyBucket != UNLINKED)
                throw new IllegalStateException("CacheD duplicate ready enqueue");
            if (head == null) nextFirstOrder = nextLastOrder = 0;
            node.readyOrder = --nextFirstOrder;
            node.readyPrev = null;
            node.readyNext = head;
            if (head != null) head.readyPrev = node;
            else tail = node;
            head = node;
            count++;
            mutationVersion++;
            index(node, true);
        }

        void addLast(CPendingState node) {
            if (node == head || node.readyPrev != null || node.readyNext != null || node.readyBucket != UNLINKED)
                throw new IllegalStateException("CacheD duplicate ready enqueue");
            if (head == null) nextFirstOrder = nextLastOrder = 0;
            node.readyOrder = ++nextLastOrder;
            node.readyNext = null;
            node.readyPrev = tail;
            if (tail != null) tail.readyNext = node;
            else head = node;
            tail = node;
            count++;
            mutationVersion++;
            index(node, true);
        }

        CPendingState removeFirst() {
            final CPendingState first = head;
            if (first != null) remove(first);
            return first;
        }

        boolean remove(CPendingState node) {
            final CPendingState prev = node.readyPrev;
            final CPendingState next = node.readyNext;
            if (node.readyBucket == UNLINKED || (prev == null && head != node)) return false;
            if (prev != null) prev.readyNext = next;
            else head = next;
            if (next != null) next.readyPrev = prev;
            else tail = prev;
            index(node, false);
            node.readyPrev = null;
            node.readyNext = null;
            count--;
            mutationVersion++;
            return true;
        }

        @SuppressWarnings("unchecked")
        private CPendingState laneHead(Object lane) {
            if (lane instanceof CPendingState) return (CPendingState) lane;
            return lane == null ? null : ((java.util.TreeSet<CPendingState>) lane).first();
        }

        @SuppressWarnings("unchecked")
        private void updateLane(Int2ObjectOpenHashMap<Object> map,
                int key, CPendingState node, boolean add) {
            if (key <= 0) return;
            final Object lane = map.get(key);
            final CPendingState before = laneHead(lane);
            final CPendingState after;
            if (add) {
                if (lane == null) {
                    map.put(key, node);
                    after = node;
                } else if (lane instanceof CPendingState) {
                    if (lane == node) throw new IllegalStateException("Duplicate CacheD resource lane");
                    final java.util.TreeSet<CPendingState> set = new java.util.TreeSet<>(fifoOrder);
                    set.add((CPendingState) lane);
                    set.add(node);
                    map.put(key, set);
                    after = set.first();
                } else {
                    final java.util.TreeSet<CPendingState> set = (java.util.TreeSet<CPendingState>) lane;
                    set.add(node);
                    after = set.first();
                }
            } else if (lane == node) {
                map.remove(key);
                after = null;
            } else if (lane instanceof java.util.TreeSet) {
                final java.util.TreeSet<CPendingState> set = (java.util.TreeSet<CPendingState>) lane;
                if (!set.remove(node)) throw new IllegalStateException("Missing CacheD resource lane");
                after = set.first();
                if (set.size() == 1) map.put(key, after);
            } else {
                throw new IllegalStateException("Missing CacheD resource lane");
            }
            if (before != after) {
                if (before != null && before != node) refresh(before);
                if (after != null && after != node) refresh(after);
            }
        }

        private void index(CPendingState node, boolean add) {
            moveCandidate(node, add ? BLOCKED : UNLINKED);
            final int first = add ? firstOwner(node) : node.readyFirstOwner;
            final int second = add ? secondOwner(node) : node.readySecondOwner;
            final int item = add ? itemId(node) : node.readyItemId;
            if (add) {
                node.readyFirstOwner = first;
                node.readySecondOwner = second;
                node.readyItemId = item;
            }
            updateLane(owners, first, node, add);
            if (second > 0 && second != first) updateLane(owners, second, node, add);
            if (node instanceof DbJob) for (int owner : ((DbJob) node).extraOwnerIds)
                updateLane(owners, owner, node, add);
            updateLane(itemsById, item, node, add);
            if (add) refresh(node);
        }

        // Item ownership can change while a dirty record is queued. Keep
        // the FIFO position, but replace its resource-lane membership.
        void reindexResources(CPendingState node) {
            // DB job owners are fixed at admission; only dirty item ownership changes.
            if (node instanceof DbJob || node.readyBucket == UNLINKED) return;
            final int first = firstOwner(node), second = secondOwner(node), item = itemId(node);
            if (first == node.readyFirstOwner && second == node.readySecondOwner
                    && item == node.readyItemId) return;
            moveCandidate(node, BLOCKED);
            updateLane(owners, node.readyFirstOwner, node, false);
            if (node.readySecondOwner > 0 && node.readySecondOwner != node.readyFirstOwner)
                updateLane(owners, node.readySecondOwner, node, false);
            updateLane(itemsById, node.readyItemId, node, false);
            node.readyFirstOwner = first;
            node.readySecondOwner = second;
            node.readyItemId = item;
            updateLane(owners, first, node, true);
            if (second > 0 && second != first) updateLane(owners, second, node, true);
            updateLane(itemsById, item, node, true);
            refresh(node);
        }

        void detachForDeadlineChange(CPendingState node) {
            if (node.readyBucket != UNLINKED) moveCandidate(node, BLOCKED);
        }

        private java.util.TreeSet<CPendingState> eligible(CPendingState node) {
            return node instanceof DbJob ? readyJobs : readyOther;
        }

        private void moveCandidate(CPendingState node, byte next) {
            final byte previous = node.readyBucket;
            if (previous == next) return;
            if (previous == ELIGIBLE) eligible(node).remove(node);
            // Changing dueAt must first detach from this deadline-ordered set.
            else if (previous == SLEEPING) sleeping.remove(node);
            if (next == ELIGIBLE) eligible(node).add(node);
            else if (next == SLEEPING) sleeping.add(node);
            node.readyBucket = next;
        }

        private boolean isLaneHead(Int2ObjectOpenHashMap<Object> map,
                                   int key, CPendingState node) {
            return key <= 0 || laneHead(map.get(key)) == node;
        }

        private boolean extraLanesReady(CPendingState node) {
            if (!(node instanceof DbJob job)) return true;
            for (int owner : job.extraOwnerIds) {
                if (!isLaneHead(owners, owner, node) || isOwnerUnavailable(owner)) return false;
            }
            return true;
        }

        void refresh(CPendingState node) {
            if (node.readyBucket == UNLINKED) return;
            final int first = firstOwner(node), second = secondOwner(node), item = itemId(node);
            if (node.writing || !isLaneHead(owners, first, node)
                    || !isLaneHead(owners, second, node)
                    || !isLaneHead(itemsById, item, node)
                    || isOwnerUnavailable(first)
                    || isOwnerUnavailable(second)
                    || !extraLanesReady(node)
                    || (item > 0 && activeItemIds.contains(item))) {
                moveCandidate(node, BLOCKED);
                return;
            }
            if (node instanceof DbJob) {
                final DbJob job = (DbJob) node;
                if (!job.prepared || job.awaitingOwnerApply || job.outcomeUnknown) {
                    moveCandidate(node, BLOCKED);
                    return;
                }
            }
            moveCandidate(node, node.dueAt > System.nanoTime() && (!node.urgent || node.failureCount > 0)
                    ? SLEEPING : ELIGIBLE);
        }

        void activateDue(long now) {
            while (!sleeping.isEmpty() && sleeping.first().dueAt <= now) {
                final CPendingState state = sleeping.pollFirst();
                eligible(state).add(state);
                state.readyBucket = ELIGIBLE;
            }
        }

        private void refreshHead(Int2ObjectOpenHashMap<Object> lanes, int key) {
            if (key <= 0) return;
            final CPendingState head = laneHead(lanes.get(key));
            if (head != null) refresh(head);
        }

        void refreshReleasedResources(int owner, int previousOwner, int item, CPendingState state) {
            refreshHead(owners, owner);
            if (previousOwner != owner) refreshHead(owners, previousOwner);
            if (state instanceof DbJob job) {
                for (int extra : job.extraOwnerIds) refreshHead(owners, extra);
            }
            refreshHead(itemsById, item);
        }

        CPendingState select(long now) {
            activateDue(now);
            final CPendingState other = readyOther.isEmpty() ? null : readyOther.first();
            if (activeDbJobs >= WORKER_COUNT - 1) return other;
            final CPendingState job = readyJobs.isEmpty() ? null : readyJobs.first();
            if (other == null) return job;
            if (job == null) return other;
            return other.readyOrder < job.readyOrder ? other : job;
        }

        long nextWait(long now) {
            return sleeping.isEmpty() ? Long.MAX_VALUE
                    : Math.max(1L, sleeping.first().dueAt - now);
        }
    }
    private final ReadyQueue ready = new ReadyQueue();
    // CacheD.lock protects resource indexes and result publication.
    private void signalReadyChange() {
        lock.notifyAll();
    }
    private final IntOpenHashSet activeOwnerIds = new IntOpenHashSet();
    private final IntOpenHashSet activeItemIds = new IntOpenHashSet();
    private final Thread[] workers = new Thread[WORKER_COUNT];
    private long nextSessionEpoch;
    private boolean accepting = true;
    private boolean stopping;
    private volatile Throwable lastFailure;
    private volatile boolean transactionStoreReady;
    private final DbJob transactionSchemaProbe;
    private boolean schemaProbeStopped;

    private CacheD() {
        // Record the exact maintenance job before the workers can run it.
        // Receipt admission stays closed until the DB worker verifies the schema.
        transactionSchemaProbe = submitDbJobInternal(0, 0, () -> {
            try {
                CTransaction.verifySchema();
                transactionStoreReady = true;
            } catch (SQLException e) {
                throw new IllegalStateException("CacheD receipt schema not ready", e);
            }
        }, true, false);
        for (int i = 0; i < WORKER_COUNT; i++) {
            workers[i] = new Thread(this::run, "CacheD-Worker-" + i);
            workers[i].setDaemon(true);
        }
        for (Thread worker : workers) worker.start();
    }

    private boolean isWorkerThread() {
        Thread current = Thread.currentThread();
        for (int i = 0; i < WORKER_COUNT; i++) {
            if (workers[i] == current) return true;
        }
        return false;
    }

    private static boolean owns(DbJob job, int ownerId) {
        if (job == null || ownerId <= 0) return false;
        if (job.ownerId == ownerId || job.secondaryOwnerId == ownerId) return true;
        for (int extra : job.extraOwnerIds) if (extra == ownerId) return true;
        return false;
    }

    public void submitDbJob(Runnable task) {
        submitDbJobOrdered(0, task);
    }

    public void submitDbJobOrdered(int ownerId, Runnable task) {
        submitDbJobOrdered(ownerId, 0, task);
    }

    public void submitDbJobOrdered(int ownerId, int secondaryOwnerId, Runnable task) {
        submitDbJobInternal(ownerId, secondaryOwnerId, task, false);
    }

    /** Reserve the owner FIFO before capturing mutable session data. The
     * reservation prevents later writes from overtaking this snapshot while
     * unrelated owners can submit and execute work during capture. The capture
     * must not wait for jobs in the reserved owner FIFO.
     */
    public boolean submitSessionDbJobOrdered(int ownerId, long epoch,
            java.util.function.Supplier<Runnable> capture) {
        Objects.requireNonNull(capture, "capture");
        final DbJob reservation;
        synchronized (lock) {
            final CUser active = activeUsers.get(ownerId);
            if (ownerId <= 0 || active == null || active.sessionEpoch != epoch
                    || hasPendingOwnerApply(ownerId)) return false;
            if (!accepting) throw new RejectedExecutionException("CacheD is closed");
            checkItemOwner(ownerId);
            reservation = submitDbJobInternal(ownerId, 0, NO_EXTRA_OWNERS, null, false, false, false);
        }

        final Runnable task;
        try {
            task = Objects.requireNonNull(capture.get(), "captured task");
        } catch (RuntimeException | Error failure) {
            synchronized (lock) { discardSessionCapture(reservation); }
            throw failure;
        }

        synchronized (lock) {
            final CUser active = activeUsers.get(ownerId);
            if (active == null || active.sessionEpoch != epoch) {
                discardSessionCapture(reservation);
                return false;
            }
            reservation.task = task;
            reservation.prepared = true;
            ready.refresh(reservation);
            signalReadyChange();
            return true;
        }
    }

    // CacheD.lock held. Unprepared work cannot have been selected by a worker.
    private void discardSessionCapture(DbJob reservation) {
        if (!ready.remove(reservation))
            throw new IllegalStateException("Session snapshot reservation lost");
        reservation.queued = false;
        completeDbJob(reservation);
        signalReadyChange();
    }

    // Reserved for idempotent persistence operations. Failures remain ahead of
    // later owner jobs until a worker successfully commits the operation.
    public void submitDbJobRetryOrdered(int ownerId, Runnable task) {
        submitDbJobInternal(ownerId, 0, task, true);
    }

    public void submitDbJobRetryOrdered(int ownerId, int secondaryOwnerId, Runnable task) {
        submitDbJobInternal(ownerId, secondaryOwnerId, task, true);
    }

    private void submitDbJobInternal(int ownerId, int secondaryOwnerId,
                                     Runnable task, boolean retryOnFailure) {
        submitDbJobInternal(ownerId, secondaryOwnerId, task, retryOnFailure, false);
    }

    private DbJob submitDbJobInternal(int ownerId, int secondaryOwnerId,
                                     Runnable task, boolean retryOnFailure, boolean failClosedOnError) {
        return submitDbJobInternal(ownerId, secondaryOwnerId, NO_EXTRA_OWNERS,
                task, retryOnFailure, failClosedOnError);
    }

    /** Login reads all four account/special warehouse snapshots under their
     * existing persistence lanes, including the distinct special owner. */
    public void submitWarehouseLoginJob(int characterId, int accountKey, int specialKey, Runnable task) {
        if (accountKey <= 0 || specialKey <= 0) throw new IllegalArgumentException("Warehouse login owners");
        final int[] extra = specialKey == accountKey || specialKey == characterId
                ? NO_EXTRA_OWNERS : new int[] { specialKey };
        submitDbJobInternal(characterId, accountKey, extra, task, false, false);
    }

    private DbJob submitDbJobInternal(int ownerId, int secondaryOwnerId, int[] extraOwners,
                                     Runnable task, boolean retryOnFailure, boolean failClosedOnError) {
        return submitDbJobInternal(ownerId, secondaryOwnerId, extraOwners, task,
                retryOnFailure, failClosedOnError, true);
    }

    private DbJob submitDbJobInternal(int ownerId, int secondaryOwnerId, int[] extraOwners,
                                     Runnable task, boolean retryOnFailure, boolean failClosedOnError,
                                     boolean prepared) {
        if (prepared) Objects.requireNonNull(task, "task");
        ownerId = Math.max(0, ownerId);
        secondaryOwnerId = Math.max(0, secondaryOwnerId);
        synchronized (lock) {
            if (!accepting) throw new RejectedExecutionException("CacheD is closed");
            // An acquired character-delete barrier must be exclusive: do not
            // admit a new ordered write after the delete has started.
            checkDbJobOwner(ownerId);
            checkDbJobOwner(secondaryOwnerId);
            for (int owner : extraOwners) checkDbJobOwner(owner);
            checkDbJobCapacity(ownerId, secondaryOwnerId, extraOwners);
            pendingDbJobs++;
            if (ownerId > 0) dbJobsByOwner.addTo(ownerId, 1);
            if (secondaryOwnerId > 0 && secondaryOwnerId != ownerId) {
                dbJobsByOwner.addTo(secondaryOwnerId, 1);
            }
            for (int owner : extraOwners) dbJobsByOwner.addTo(owner, 1);
            final DbJob job = new DbJob(ownerId, secondaryOwnerId, extraOwners, task, retryOnFailure, failClosedOnError);
            job.prepared = prepared;
            enqueueNext(job, 0L);
            signalReadyChange();
            return job;
        }
    }

    // Leave one admission per worker for independent owners at saturation.
    // Neither a neutral queue nor a single blocked owner can take those slots.
    private void checkDbJobCapacity(int owner, int second, int[] extra) {
        if (pendingDbJobs < MAX_DB_JOBS - WORKER_COUNT) return;
        if (pendingDbJobs >= MAX_DB_JOBS)
            throw new RejectedExecutionException("CacheD DB request backlog is full");
        if ((owner <= 0 && second <= 0 && extra.length == 0)
                || dbJobsByOwner.containsKey(owner) || dbJobsByOwner.containsKey(second))
            throw new RejectedExecutionException("CacheD backlog reserved for independent owners");
        for (int id : extra) {
            if (dbJobsByOwner.containsKey(id))
                throw new RejectedExecutionException("CacheD backlog reserved for independent owners");
        }
    }

    private void checkDbJobOwner(int ownerId) {
        if (ownerId <= 0) return;
        if (deletingIds.contains(ownerId) || deletedIds.contains(ownerId))
            throw new RejectedExecutionException("CacheD owner is being deleted");
        rejectQuarantinedOwner(ownerId);
    }

    // Quarantine keeps the owner FIFO closed until authoritative reconciliation.
    // New jobs would consume shared capacity without ever reaching a worker.
    private boolean isOwnerQuarantined(int ownerId) {
        return quarantinedOwnerIds != null && quarantinedOwnerIds.contains(ownerId);
    }

    private boolean isOwnerUnavailable(int ownerId) {
        return ownerId > 0 && (activeOwnerIds.contains(ownerId) || isOwnerQuarantined(ownerId));
    }

    private void rejectQuarantinedOwner(int ownerId) {
        if (isOwnerQuarantined(ownerId))
            throw new RejectedExecutionException("CacheD owner is quarantined: " + ownerId);
    }

    /** Called by an existing CIO owner, including an immediate scheduling rejection.
     * The acknowledgement may race the DB worker's return from task.run().
     */
    private void finishOwnerApply(DbJob job, Throwable failure) {
        finishOwnerApply(job, failure, null);
    }

    private void finishOwnerApply(DbJob job, Throwable failure, Consumer<Throwable> afterReleased) {
        String diagnostic = null;
        SQLException persistenceFailure = null;
        final ArrayList<Runnable> logouts;
        synchronized (lock) {
            if (job == null || !job.ownerApplyRequired)
                throw new IllegalStateException("No pending owner replay");
            if (job.ownerApplyFinished) return; // duplicate completion cannot decrement twice
            job.ownerApplyFinished = true;
            job.ownerApplyFailure = failure;
            if (job.outcomeUnknown) {
                if (failure == null) failure = new IllegalStateException("Receipt outcome is quarantined");
            } else if (job.awaitingOwnerApply) {
                if (!ready.remove(job)) throw new IllegalStateException("Owner replay reservation lost");
                job.queued = false;
                job.awaitingOwnerApply = false;
                if (failure == null) {
                    completeDbJob(job);
                } else {
                    persistenceFailure = new SQLException("Committed owner replay failed", failure);
                    diagnostic = quarantineDbJob(job, persistenceFailure);
                }
            } else {
                // The CIO replied before the DB worker returned from dispatch.
                // That worker still owns the SQL lanes and retires them in run().
                job.afterOwnerReleased = afterReleased;
                afterReleased = null;
            }
            logouts = takeReadyLogouts(job);
            signalReadyChange();
        }
        notifyOwnerRelease(afterReleased, failure);
        resumeLogouts(logouts);
        reportFailure(diagnostic, persistenceFailure);
    }

    // All three helpers run only while holding lock. The counter reflects
    // reserved jobs before the SQL operation is published, not executing jobs.
    private void countUnpreparedWarehouse(DbJob job) {
        if (job.unpreparedWarehouseCounted || job.prepared || !job.failClosedOnError)
            throw new IllegalStateException("Invalid warehouse reservation count");
        job.unpreparedWarehouseCounted = true;
        if (job.ownerId > 0) unpreparedWarehouseJobsByOwner.addTo(job.ownerId, 1);
        if (job.secondaryOwnerId > 0 && job.secondaryOwnerId != job.ownerId)
            unpreparedWarehouseJobsByOwner.addTo(job.secondaryOwnerId, 1);
        for (int owner : job.extraOwnerIds) unpreparedWarehouseJobsByOwner.addTo(owner, 1);
    }

    private void uncountUnpreparedWarehouse(DbJob job) {
        if (!job.unpreparedWarehouseCounted) return;
        job.unpreparedWarehouseCounted = false;
        if (job.ownerId > 0 && unpreparedWarehouseJobsByOwner.addTo(job.ownerId, -1) == 1)
            unpreparedWarehouseJobsByOwner.remove(job.ownerId);
        if (job.secondaryOwnerId > 0 && job.secondaryOwnerId != job.ownerId
                && unpreparedWarehouseJobsByOwner.addTo(job.secondaryOwnerId, -1) == 1)
            unpreparedWarehouseJobsByOwner.remove(job.secondaryOwnerId);
        for (int owner : job.extraOwnerIds) if (unpreparedWarehouseJobsByOwner.addTo(owner, -1) == 1)
            unpreparedWarehouseJobsByOwner.remove(owner);
    }

    /** Snapshot installation must not replace memory underneath a durable
     * transaction whose owner replay is pending (or quarantined).
     * Both counters share lock with warehouse reservation commit/abort.
     */
    public boolean hasPendingOwnerApply(int ownerId) {
        synchronized (lock) { return pendingOwnerReplay(ownerId); }
    }

    private boolean pendingOwnerReplay(int ownerId) {
        return ownerApplyJobsByOwner.get(ownerId) != 0
                || unpreparedWarehouseJobsByOwner.get(ownerId) != 0;
    }

    public boolean isOwnerPersistenceQuarantined(int ownerId) {
        synchronized (lock) { return isOwnerQuarantined(ownerId); }
    }

    /** Register a terminal-session continuation atomically with the replay check.
     * A quarantined owner may detach, while its SQL lane remains fenced. */
    public boolean deferLogoutForOwnerApply(int ownerId, Runnable resume) {
        Objects.requireNonNull(resume);
        synchronized (lock) {
            if (isOwnerQuarantined(ownerId) || !pendingOwnerReplay(ownerId)) return false;
            if (logoutWaiters == null) logoutWaiters = new Int2ObjectOpenHashMap<>();
            ArrayList<Runnable> waiting = logoutWaiters.get(ownerId);
            if (waiting == null) {
                waiting = new ArrayList<>(1);
                logoutWaiters.put(ownerId, waiting);
            }
            waiting.add(resume);
            return true;
        }
    }

    // Collect under the CacheD monitor. Schedule the terminal work after unlocking.
    private ArrayList<Runnable> takeReadyLogouts(DbJob job) {
        if (logoutWaiters == null) return null;
        ArrayList<Runnable> ready = takeReadyLogouts(job.ownerId, null);
        if (job.secondaryOwnerId != job.ownerId)
            ready = takeReadyLogouts(job.secondaryOwnerId, ready);
        for (int extra : job.extraOwnerIds) ready = takeReadyLogouts(extra, ready);
        if (logoutWaiters.isEmpty()) logoutWaiters = null;
        return ready;
    }

    private ArrayList<Runnable> takeReadyLogouts(int ownerId, ArrayList<Runnable> ready) {
        if (ownerId <= 0 || (pendingOwnerReplay(ownerId) && !isOwnerQuarantined(ownerId))) return ready;
        ArrayList<Runnable> waiting = logoutWaiters.remove(ownerId);
        if (ready == null) return waiting;
        if (waiting != null) ready.addAll(waiting);
        return ready;
    }

    private static void notifyOwnerRelease(Consumer<Throwable> callback, Throwable failure) {
        if (callback == null) return;
        try { callback.accept(failure); }
        catch (RuntimeException | Error callbackFailure) {
            reportFailure("CacheD owner release callback failed", callbackFailure);
        }
    }

    private static void resumeLogouts(ArrayList<Runnable> logouts) {
        if (logouts == null) return;
        for (Runnable resume : logouts) {
            try { resume.run(); }
            catch (RuntimeException | Error failure) {
                reportFailure("Cannot resume deferred character logout", failure);
            }
        }
    }

    // Caller holds lock, before publishing the prepared job to a DB worker.
    private void requireOwnerApply(DbJob job) {
        if (job.ownerApplyRequired) return;
        job.ownerApplyRequired = true;
        if (job.ownerId > 0) ownerApplyJobsByOwner.addTo(job.ownerId, 1);
        if (job.secondaryOwnerId > 0 && job.secondaryOwnerId != job.ownerId)
            ownerApplyJobsByOwner.addTo(job.secondaryOwnerId, 1);
        for (int owner : job.extraOwnerIds) ownerApplyJobsByOwner.addTo(owner, 1);
    }

    // Caller holds lock. Finish counts only once, after SQL AND memory replay.
    private void completeDbJob(DbJob state) {
        pendingDbJobs--;
        if (state.ownerApplyRequired) {
            if (state.ownerId > 0 && ownerApplyJobsByOwner.addTo(state.ownerId, -1) == 1)
                ownerApplyJobsByOwner.remove(state.ownerId);
            if (state.secondaryOwnerId > 0 && state.secondaryOwnerId != state.ownerId
                    && ownerApplyJobsByOwner.addTo(state.secondaryOwnerId, -1) == 1)
                ownerApplyJobsByOwner.remove(state.secondaryOwnerId);
            for (int owner : state.extraOwnerIds) if (ownerApplyJobsByOwner.addTo(owner, -1) == 1)
                ownerApplyJobsByOwner.remove(owner);
        }
        if (state.ownerId > 0) decrementDbOwner(state.ownerId);
        if (state.secondaryOwnerId > 0 && state.secondaryOwnerId != state.ownerId)
            decrementDbOwner(state.secondaryOwnerId);
        for (int owner : state.extraOwnerIds) decrementDbOwner(owner);
        if (state.ownerId > 0) completeOwnerRelease(state.ownerId);
        if (state.secondaryOwnerId > 0 && state.secondaryOwnerId != state.ownerId)
            completeOwnerRelease(state.secondaryOwnerId);
        for (int owner : state.extraOwnerIds) completeOwnerRelease(owner);
        enqueueWarehouseRetirements(state);
    }

    /** Unlike flushDbOwner, this is a zero-pending predicate even when
     * invoked by a lane-neutral CacheD completion job. No waiting and no SQL. */
    public boolean isWarehouseOwnerIdle(int ownerId) {
        synchronized (lock) {
            return isWarehouseOwnerIdleLocked(ownerId);
        }
    }

    private boolean isWarehouseOwnerIdleLocked(int ownerId) {
        return dbJobsByOwner.get(ownerId) == 0
                && ownerApplyJobsByOwner.get(ownerId) == 0
                && unpreparedWarehouseJobsByOwner.get(ownerId) == 0;
    }

    /** Preserve the explicit-delete fence, including its existing
     * ordered DB job exception, in one CacheD monitor acquisition. */
    public boolean canRetireWarehouseExplicitly(int ownerId) {
        if (ownerId <= 0) throw new IllegalArgumentException("ownerId");
        final DbJob executing = DB_JOB_CONTEXT.get();
        synchronized (lock) {
            if (ownerApplyJobsByOwner.get(ownerId) != 0
                    || unpreparedWarehouseJobsByOwner.get(ownerId) != 0) return false;
            if (executing != null)
                return owns(executing, ownerId) && dbJobsByOwner.get(ownerId) == 1;
            return !isWorkerThread() && dbJobsByOwner.get(ownerId) == 0;
        }
    }

    /** Register atomically with the idle transition. False means the caller
     * may retire now while still holding its warehouse mutation monitor. */
    public boolean deferWarehouseRetirement(int ownerId, Object identity, Runnable action) {
        synchronized (lock) {
            if (isWarehouseOwnerIdleLocked(ownerId)) return false;
            java.util.IdentityHashMap<Object, Runnable> waiters = warehouseRetirements.get(ownerId);
            if (waiters == null) {
                waiters = new java.util.IdentityHashMap<>();
                warehouseRetirements.put(ownerId, waiters);
            }
            waiters.put(identity, action);
            return true;
        }
    }

    // CacheD.lock held. At most one neutral maintenance job replaces this
    // completed DB job, preserving the existing queue capacity. No new worker,
    // timer, lane, or running callback under CacheD.lock.
    private void enqueueWarehouseRetirements(DbJob completed) {
        if (warehouseRetirements.isEmpty()) return;
        ArrayList<Runnable> actions = null;
        actions = collectWarehouseRetirements(completed.ownerId, actions);
        if (completed.secondaryOwnerId != completed.ownerId)
            actions = collectWarehouseRetirements(completed.secondaryOwnerId, actions);
        for (int owner : completed.extraOwnerIds)
            actions = collectWarehouseRetirements(owner, actions);
        if (actions == null) return;
        final ArrayList<Runnable> batch = actions;
        pendingDbJobs++;
        enqueueNext(new DbJob(0, 0, () -> {
            for (Runnable action : batch) {
                try { action.run(); }
                catch (RuntimeException failure) {
                    LOG.log(Level.SEVERE, "Deferred warehouse retirement failed", failure);
                }
            }
        }, false), 0L);
    }

    private ArrayList<Runnable> collectWarehouseRetirements(int ownerId, ArrayList<Runnable> actions) {
        if (ownerId <= 0 || !isWarehouseOwnerIdleLocked(ownerId)) return actions;
        final java.util.IdentityHashMap<Object, Runnable> waiters = warehouseRetirements.remove(ownerId);
        if (waiters == null) return actions;
        if (actions == null) actions = new ArrayList<>(waiters.size());
        actions.addAll(waiters.values());
        return actions;
    }

    private String quarantineDbJob(DbJob state, SQLException failure) {
        lastFailure = failure;
        state.outcomeUnknown = true;
        if (quarantinedOwnerIds == null) quarantinedOwnerIds = new IntOpenHashSet();
        if (state.ownerId > 0) quarantinedOwnerIds.add(state.ownerId);
        if (state.secondaryOwnerId > 0) quarantinedOwnerIds.add(state.secondaryOwnerId);
        for (int owner : state.extraOwnerIds) quarantinedOwnerIds.add(owner);
        // Quarantine keeps owner lanes, not the cancelled receipt payload or
        // its captured character, inventory and world objects.
        state.task = null;
        state.failureCount++;
        state.queued = true;
        state.urgent = false;
        ready.addFirst(state);
        return "CacheD ATOMIC TRANSACTION FAILED / UNRESOLVED: DB writes for owners "
                + state.ownerId + "/" + state.secondaryOwnerId
                + " QUARANTINED. Disconnect affected sessions and reconcile from authoritative MySQL. "
                + "NO automatic replay.";
    }

    // Used at a reload boundary so a fresh SELECT cannot overtake preceding
    // warehouse writes. Never wait for queued work from a CacheD DB worker.
    public boolean flushDbOwner(int ownerId, long timeoutMillis) {
        if (ownerId <= 0) throw new IllegalArgumentException("ownerId");
        final DbJob executing = DB_JOB_CONTEXT.get();
        synchronized (lock) {
            if (executing != null) {
                return owns(executing, ownerId) && dbJobsByOwner.get(ownerId) == 1;
            }
            if (isWorkerThread()) return false;
            long deadline = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
            while (dbJobsByOwner.get(ownerId) != 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return false;
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    public static int accountOrderKey(String account) {
        Objects.requireNonNull(account, "account");
        return (account.hashCode() & 0x3fffffff) | 0x40000000;
    }

    /** Same FIFO key as accountOrderKey(prefix + characterId), without a
     * temporary String/backing array on every item use. Preserve the existing
     * decimal hash, including zero, negative IDs and Integer.MIN_VALUE.
     */
    public static int accountOrderKey(String prefix, int characterId) {
        int hash = Objects.requireNonNull(prefix, "prefix").hashCode();
        long value = characterId;
        if (value < 0) {
            hash = 31 * hash + '-';
            value = -value;
        }
        long divisor = 1;
        while (value / divisor >= 10) divisor *= 10;
        do {
            hash = 31 * hash + (int) ('0' + value / divisor);
            value %= divisor;
            divisor /= 10;
        } while (divisor != 0);
        return (hash & 0x3fffffff) | 0x40000000;
    }

    public void submitDbJobOrdered(String account, Runnable task) {
        submitDbJobOrdered(accountOrderKey(account), task);
    }

    private CAccount getOrCreate(String login) {
        Objects.requireNonNull(login, "login");
        if (!accepting) {
            throw new RejectedExecutionException("CacheD is closed");
        }
        CAccount state = accounts.get(login);
        if (state == null) {
            if (accounts.size() >= MAX_ACCOUNTS) {
                throw new RejectedExecutionException("CacheD account cache is full");
            }
            state = activeAccounts.get(login);
            if (state == null) state = new CAccount(login);
            accounts.put(login, state);
        }
        return state;
    }

    private void changed(CAccount state, int mask) {
        state.changed(mask);
        enqueueNext(state, COALESCE_NANOS);
        signalReadyChange();
    }

    public void saveLastActive(String login, long timestampMillis, String ip) {
        synchronized (lock) {
            CAccount state = getOrCreate(login);
            state.lastActive = timestampMillis;
            state.ip = ip;
            changed(state, CAccount.LAST_ACTIVE);
        }
    }

    public void saveLastLogout(String login, long timestampMillis) {
        synchronized (lock) {
            CAccount state = getOrCreate(login);
            state.lastLogout = timestampMillis;
            changed(state, CAccount.LAST_LOGOUT);
        }
    }

    public void saveBlessOfAin(String login, int value) {
        synchronized (lock) {
            CAccount state = getOrCreate(login);
            state.blessOfAin = value;
            changed(state, CAccount.BLESS_OF_AIN);
        }
    }


    public void saveRaidBuff(String login, long untilMillis) {
        synchronized (lock) {
            CAccount state = getOrCreate(login);
            state.raidBuffUntil = untilMillis;
            changed(state, CAccount.RAID_BUFF);
        }
    }

    public void registerLoadedCharacter(String accountName, String name, int characterId) {
        registerLoadedCharacterSession(accountName, name, characterId);
    }

    /** One epoch per successful session publication; used to reject a stale logout. */
    public long registerLoadedCharacterSession(String accountName, String name, int characterId) {
        Objects.requireNonNull(accountName, "accountName");
        Objects.requireNonNull(name, "name");
        if (accountName.isEmpty() || name.isEmpty() || characterId <= 0)
            throw new IllegalArgumentException("Invalid loaded character");
        synchronized (lock) {
            if (!accepting || deletingIds.contains(characterId) || deletedIds.contains(characterId)
                    || deletingNames.contains(name))
                throw new RejectedExecutionException("Character cache unavailable: " + name);
            CUser user = activeUsers.get(characterId);
            if (user == null) user = characters.get(name);
            if (user == null) {
                if (activeUsers.size() >= MAX_CHARACTERS)
                    throw new RejectedExecutionException("CacheD active character cache is full");
                user = new CUser(name);
            }
            if (!name.equals(user.name) || (user.objectId != 0 && user.objectId != characterId)
                    || (user.accountName != null && !accountName.equals(user.accountName)))
                throw new IllegalStateException("Character ownership collision: " + characterId);
            CAccount account = activeAccounts.get(accountName);
            if (account == null) {
                if (activeAccounts.size() >= MAX_ACCOUNTS)
                    throw new RejectedExecutionException("CacheD active account cache is full");
                account = accounts.get(accountName);
                if (account == null) account = new CAccount(accountName);
            }
            CUser previous = account.character(characterId);
            if (previous != null && previous != user)
                throw new IllegalStateException("Character ID collision: " + characterId);
            user.bind(accountName, characterId);
            activeAccounts.put(accountName, account);
            account.setCharacter(user);
            activeUsers.put(characterId, user);
            if (++nextSessionEpoch == 0L) ++nextSessionEpoch;
            user.sessionEpoch = nextSessionEpoch;
            user.karmaReceiptRevision = 0;
            // Only a published session supersedes the previous owner release.
            warehouse.releasingOwnerIds.remove(characterId);
            return user.sessionEpoch;
        }
    }

    /** Caller holds CacheD's lock; unlink only after durable work is drained. */
    private void unlinkCharacter(int characterId) {
        CUser user = activeUsers.remove(characterId);
        if (user == null) return;
        CAccount account = activeAccounts.get(user.accountName);
        if (account != null) {
            account.deleteCharacter(characterId, user);
            if (!account.hasCharacters()) activeAccounts.remove(account.login, account);
        }
    }

    public void publishKarmaReceipt(int characterId, long sessionEpoch, int revision) {
        synchronized (lock) {
            CUser user = activeUsers.get(characterId);
            if (user != null && user.sessionEpoch == sessionEpoch) user.karmaReceiptRevision = revision;
        }
    }

    /** False means this image must be recaptured after owner replay. */
    public boolean saveCharacter(CharacterSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(snapshot.name, "snapshot.name");
        synchronized (lock) {
            if (!accepting) {
                throw new RejectedExecutionException("CacheD is closed");
            }
            if (deletingNames.contains(snapshot.name) || deletingIds.contains(snapshot.objectId)
                    || deletedIds.contains(snapshot.objectId)) {
                throw new RejectedExecutionException("Character is being deleted: " + snapshot.name);
            }
            CUser active = activeUsers.get(snapshot.objectId);
            // Old logout/timer callbacks must not overwrite a newly published
            // login. Epoch zero belongs only to offline/unpublished objects.
            if ((active != null && active.sessionEpoch != snapshot.sessionEpoch)
                    || (active == null && snapshot.sessionEpoch != 0L))
                throw new RejectedExecutionException("Stale character session save: " + snapshot.name);
            if (ownerApplyJobsByOwner.get(snapshot.objectId) != 0
                    || (active != null && active.karmaReceiptRevision != snapshot.karmaReceiptRevision)) return false;
            CUser state = characters.get(snapshot.name);
            if (state == null) {
                state = activeUsers.get(snapshot.objectId);
                if (state != null && !state.name.equals(snapshot.name))
                    throw new IllegalStateException("Character ID/name collision: " + snapshot.objectId);
            }
            if (state == null) {
                if (characters.size() >= MAX_CHARACTERS) {
                    throw new RejectedExecutionException("CacheD character cache is full");
                }
                state = new CUser(snapshot.name);
            }
            // An identical pending write already covers this state; an identical
            // committed write needs no additional JDBC operation. Failed writes
            // remain pending in CacheD and are never mistaken for a commit.
            if (state.latest != null && state.latest.sameData(snapshot)) return true;
            if (state.latest == null && state.persisted != null
                    && state.persisted.sameData(snapshot)) return true;
            characters.put(state.name, state);
            state.capture(snapshot);
            enqueueNext(state, COALESCE_NANOS);
            signalReadyChange();
            return true;
        }
    }


    private void completeOwnerRelease(int characterId) {
        if (!warehouse.releasingOwnerIds.contains(characterId)
                || deletingIds.contains(characterId) || pendingCharacterPersistence(characterId)) return;
        warehouse.clearCharacterOwners(characterId);
        warehouse.releasingOwnerIds.remove(characterId);
        unlinkCharacter(characterId);
    }

    private void completeItemWrite(CItem state) {
        IntOpenHashSet owners = state.pendingOwners;
        warehouse.completePendingItem(state);
        if (warehouse.releasingOwnerIds.isEmpty()) return;
        if (state.ownerId == 0 || (owners != null && owners.contains(0))) {
            // An unknown owner was a barrier for every pending logout.
            completeReleasesAfterUnknownItems();
        } else if (owners == null) {
            completeOwnerRelease(state.ownerId);
        } else {
            for (IntIterator it = owners.iterator(); it.hasNext();) completeOwnerRelease(it.nextInt());
        }
    }

    private void completeReleasesAfterUnknownItems() {
        if (warehouse.releasingOwnerIds.isEmpty()
                || warehouse.pendingItemIds.get(0) != null
                || itemCommandsByOwner.get(0) != 0
                || itemCommandsByOwner.get(-1) != 0) return;
        for (IntIterator it = warehouse.releasingOwnerIds.iterator(); it.hasNext();) {
            int characterId = it.nextInt();
            if (!deletingIds.contains(characterId) && !pendingCharacterPersistence(characterId)) {
                warehouse.clearCharacterOwners(characterId);
                unlinkCharacter(characterId);
                it.remove();
            }
        }
    }

    private void checkItemOwner(int ownerId) {
        if (ownerId > 0 && (deletingIds.contains(ownerId) || deletedIds.contains(ownerId))) {
            throw new RejectedExecutionException("Character item storage is closed: " + ownerId);
        }
        if (ownerId <= 0 && !deletingIds.isEmpty()) {
            throw new RejectedExecutionException("Item owner unknown during character deletion");
        }
    }

    // Caller holds lock. A detached old inventory must not save over rows
    // restored for a newer login of the same character. Unbound new/ground
    // items have no character epoch and are handled by the owner index.
    private void checkItemSession(L1ItemInstance item) {
        if (item == null || item._cha == null) return;
        final int characterId = item._cha.getId();
        final long epoch = item._cha.getCacheDSessionEpoch();
        final CUser active = activeUsers.get(characterId);
        if ((active != null && active.sessionEpoch != epoch)
                || (active == null && epoch != 0L))
            throw new RejectedExecutionException("Stale item session: " + characterId + "/" + item.getId());
    }

    public void registerLoadedItemOwner(int characterId, int itemId) {
        if (characterId <= 0 || itemId <= 0) return;
        synchronized (lock) {
            checkItemOwner(characterId);
            // Loading DB rows does not prove successful world entry. Do not
            // cancel an earlier logout release until session publication.
            warehouse.trackItemOwner(itemId, characterId);
            CItem state = items.get(itemId);
            if (state != null && state.ownerId != characterId) {
                warehouse.trackPendingItem(state, characterId);
                ready.reindexResources(state);
                signalReadyChange();
            }
        }
    }

    public void saveItem(L1ItemInstance item, long mask) {
        if (WAREHOUSE_APPLY_CONTEXT.get() != null) return;
        WarehouseMove move = WAREHOUSE_MOVE_CONTEXT.get();
        if (move != null) {
            if (mask != 0L) move.writeItem(move.characterId, item, false);
            return;
        }
        TradeScope scope = TRADE_CONTEXT.get();
        if (scope != null) {
            if (!scope.staging && item != null) {
                final int owner;
                synchronized (lock) {
                    owner = item._cha != null ? item._cha.getId() : warehouse.itemOwners.get(item.getId());
                }
                scope.write(owner, item);
            }
            return;
        }
        if (item == null || item.getId() <= 0 || mask == 0L) return;
        synchronized (lock) {
            if (!accepting) throw new RejectedExecutionException("CacheD is closed");
            if (deletingItemIds.contains(item.getId())) {
                throw new RejectedExecutionException("Item is being deleted: " + item.getId());
            }
            int ownerId = warehouse.itemOwners.get(item.getId());
            checkItemSession(item);
            if (item._cha != null && ownerId > 0 && item._cha.getId() != ownerId)
                throw new RejectedExecutionException("Stale transferred item object: " + item.getId());
            if (ownerApplyJobsByOwner.get(ownerId) != 0) return;
            checkItemOwner(ownerId);
            ItemSnapshot snapshot = ItemSnapshot.capture(item, ownerId);
            CItem state = items.get(snapshot.id);
            if (state == null) {
                if (items.size() >= MAX_ITEMS) {
                    throw new RejectedExecutionException("CacheD item cache is full");
                }
                state = new CItem(snapshot.id);
                items.put(state.id, state);
                warehouse.addPendingId(ownerId, state.id);
                state.ownerId = ownerId;
            } else {
                warehouse.trackPendingItem(state, ownerId);
            }
            state.capture(snapshot, mask);
            ready.reindexResources(state);
            enqueueNext(state, COALESCE_NANOS);
            signalReadyChange();
        }
    }

    // Submitting a persistence command never waits for a DB worker. A successful
    // return means accepted by CacheD, NOT committed to MySQL. Flush barriers
    // must be used by logout/reload/close paths when durability is required.
    public void storeItem(int ownerId, L1ItemInstance item) throws Exception {
        if (WAREHOUSE_APPLY_CONTEXT.get() != null) return;
        if (item == null) throw new IllegalArgumentException("item is null");
        WarehouseMove move = WAREHOUSE_MOVE_CONTEXT.get();
        if (move != null) { move.writeItem(ownerId, item, true); return; }
        TradeScope scope = TRADE_CONTEXT.get();
        if (scope != null) { scope.write(ownerId, item); return; }
        submitItemCommand(item, ownerId, false);
    }

    public void deleteItem(L1ItemInstance item) throws Exception {
        if (WAREHOUSE_APPLY_CONTEXT.get() != null) return;
        WarehouseMove move = WAREHOUSE_MOVE_CONTEXT.get();
        if (move != null) { move.deleteItem(item); return; }
        TradeScope scope = TRADE_CONTEXT.get();
        if (scope != null) { if (item != null) scope.remove(item.getId()); return; }
        if (item != null) submitItemCommand(item, -1, true);
    }

    private void submitItemCommand(L1ItemInstance item, int ownerId, boolean delete) {
        final int id = item.getId();
        if (id <= 0) throw new IllegalArgumentException("Invalid item ID: " + id);
        synchronized (lock) {
            if (!accepting) throw new RejectedExecutionException("CacheD is closed");
            if (pendingItemCommands >= MAX_ITEMS) {
                throw new RejectedExecutionException("CacheD item command backlog is full");
            }
            final int currentOwner = warehouse.itemOwners.get(id);
            checkItemSession(item);
            if (delete && (currentOwner <= 0
                    || (item._cha != null && item._cha.getId() != currentOwner)))
                throw new RejectedExecutionException("Item delete owner is unknown or changed: " + id);
            final int resolvedOwner = delete ? currentOwner : ownerId;
            final int previousOwner = delete ? 0 : currentOwner;
            checkItemOwner(resolvedOwner);
            if (previousOwner > 0 && previousOwner != resolvedOwner) {
                checkItemOwner(previousOwner);
            }
            final ItemSnapshot snapshot = delete ? ItemSnapshot.captureId(item, resolvedOwner)
                    : ItemSnapshot.capture(item, ownerId);
            final int templateId = item.getItemId();
            final boolean letterItem = delete && templateId >= 49016 && templateId <= 49025;
            final String mailReceiver;
            final int mailboxKey;
            if (letterItem) {
                if (item._cha == null || item._cha.getId() != resolvedOwner)
                    throw new RejectedExecutionException("Mail item receiver ownership unavailable: " + id);
                mailReceiver = item._cha.getName();
                mailboxKey = l1j.server.server.datatables.LetterTable.mailboxOrderKey(mailReceiver);
            } else {
                mailReceiver = null;
                mailboxKey = 0;
            }
            final ItemCommand command = new ItemCommand(snapshot, delete, resolvedOwner, previousOwner,
                    letterItem ? templateId : 0, mailboxKey, mailReceiver);
            // Publish the latest logical owner immediately. Subsequent item
            // dirty snapshots must not accidentally use the previous owner
            // while the UPDATE/DELETE is still queued for an SQL worker.
            warehouse.trackItemOwner(id, delete ? 0 : ownerId);
            pendingItemCommands++;
            itemCommandsByOwner.addTo(resolvedOwner, 1);
            if (previousOwner > 0 && previousOwner != resolvedOwner) {
                itemCommandsByOwner.addTo(previousOwner, 1);
            }
            enqueueNext(command, 0L);
            signalReadyChange();
        }
    }

    private void decrementDbOwner(int ownerId) {
        int remaining = dbJobsByOwner.addTo(ownerId, -1) - 1;
        if (remaining == 0) dbJobsByOwner.remove(ownerId);
    }

    private void decrementOwnerCount(int ownerId) {
        int remaining = itemCommandsByOwner.addTo(ownerId, -1) - 1;
        if (remaining == 0) itemCommandsByOwner.remove(ownerId);
    }

    private void decrementItemCommandOwner(ItemCommand state) {
        pendingItemCommands--;
        decrementOwnerCount(state.ownerId);
        if (state.previousOwnerId > 0 && state.previousOwnerId != state.ownerId) {
            decrementOwnerCount(state.previousOwnerId);
        }
    }

    private boolean pendingCharacterPersistence(int characterId) {
        CUser user = activeUsers.get(characterId);
        return pendingCharacterItems(characterId) || dbJobsByOwner.get(characterId) != 0
                || (user != null && characters.get(user.name) == user);
    }

    private boolean pendingCharacterItems(int characterId) {
        return itemCommandsByOwner.get(characterId) != 0
                || itemCommandsByOwner.get(0) != 0 || itemCommandsByOwner.get(-1) != 0
                || warehouse.pendingItemIds.get(characterId) != null || warehouse.pendingItemIds.get(0) != null;
    }

    private void expediteCharacterItems(int characterId) {
        expediteOwnerItems(characterId);
        expediteOwnerItems(0);
        signalReadyChange();
    }

    private void expediteOwnerItems(int ownerId) {
        IntOpenHashSet pending = warehouse.pendingItemIds.get(ownerId);
        if (pending == null) return;
        for (IntIterator it = pending.iterator(); it.hasNext();) {
            CItem item = items.get(it.nextInt());
            if (item == null || item.urgent) continue;
            item.urgent = true;
            // The scheduler indexes delayed candidates separately. Update its
            // index as well as the flag so the foreground flush can proceed.
            ready.refresh(item);
        }
    }

    public boolean flushItemsByCharacter(int characterId, long timeoutMillis) {
        if (characterId <= 0) throw new IllegalArgumentException("characterId");
        final DbJob executingJob = DB_JOB_CONTEXT.get();
        if (isWorkerThread() && executingJob == null) {
            throw new IllegalStateException("CacheD worker cannot flush itself");
        }
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        synchronized (lock) {
            expediteCharacterItems(characterId);
            // A CacheD DB job holds its owner resources until it returns.
            // Waiting on queued work here can deadlock, and treating it as
            // flushed would let a login read stale items. Report incomplete;
            // the caller must retry the whole operation after the job ends.
            if (executingJob != null) {
                return !pendingCharacterItems(characterId)
                        && dbJobsByOwner.get(characterId) <= (owns(executingJob, characterId) ? 1 : 0)
                        && !deletingIds.contains(characterId)
                        && !deletedIds.contains(characterId);
            }
            while (pendingCharacterPersistence(characterId) || deletingIds.contains(characterId)) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return false;
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return !deletedIds.contains(characterId);
        }
    }

    // Nonblocking logout request. Ownership is released after pending item
    // writes complete, not when this method returns.
    public void requestCharacterItemOwnerRelease(int characterId) {
        requestCharacterItemOwnerRelease(characterId, -1L);
    }

    /** A delayed old-session logout must never evict a newly published login. */
    public void requestCharacterItemOwnerRelease(int characterId, long sessionEpoch) {
        if (characterId <= 0) throw new IllegalArgumentException("characterId");
        synchronized (lock) {
            CUser active = activeUsers.get(characterId);
            if (sessionEpoch >= 0L && active != null && active.sessionEpoch != sessionEpoch) return;
            warehouse.releasingOwnerIds.add(characterId);
            expediteCharacterItems(characterId);
            completeOwnerRelease(characterId);
        }
    }

    public boolean beginCharacterDelete(String name, int characterId, long timeoutMillis) {
        Objects.requireNonNull(name, "name");
        if (characterId <= 0) throw new IllegalArgumentException("characterId");
        if (isWorkerThread() && DB_JOB_CONTEXT.get() == null) {
            throw new IllegalStateException("CacheD worker cannot delete a character");
        }
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        synchronized (lock) {
            if (!accepting || deletedIds.contains(characterId)
                    || deletingIds.contains(characterId) || deletingNames.contains(name)) {
                return false;
            }
            deletingNames.add(name);
            deletingIds.add(characterId);
            boolean acquired = false;
            try {
                for (;;) {
                    CUser c = characters.get(name);
                    if (c != null) { c.urgent = true; ready.refresh(c); }
                    expediteCharacterItems(characterId);
                    // Ordered SQL writes (trades, warehouses, etc.) belong to
                    // this character's durability boundary too. Waiting only
                    // for snapshots/items can race with an in-flight DB job.
                    final boolean ownDbJob = owns(DB_JOB_CONTEXT.get(), characterId);
                    final int otherDbJobs = dbJobsByOwner.get(characterId) - (ownDbJob ? 1 : 0);
                    final boolean pendingItems = pendingCharacterItems(characterId);
                    if (c == null && !pendingItems && otherDbJobs == 0) {
                        acquired = true;
                        return true;
                    }
                    // A DB worker cannot wait on work queued behind its own
                    // owner reservation. Fail instead of deadlocking.
                    if (ownDbJob) return false;
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) return false;
                    try {
                        TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            } finally {
                if (!acquired) {
                    deletingNames.remove(name);
                    deletingIds.remove(characterId);
                    completeOwnerRelease(characterId);
                    signalReadyChange();
                }
            }
        }
    }

    public void finishCharacterDelete(String name, int characterId, boolean committed) {
        synchronized (lock) {
            if (committed) {
                deletedIds.add(characterId);
                warehouse.clearCharacterOwners(characterId);
                warehouse.releasingOwnerIds.remove(characterId);
                unlinkCharacter(characterId);
            }
            deletingNames.remove(name);
            deletingIds.remove(characterId);
            completeOwnerRelease(characterId);
            signalReadyChange();
        }
    }

    /** Nonblocking admission fence for a dropped row (or its split source).
     * Called outside list monitors. A failed/retrying row stays fenced.
     */
    public boolean isItemPersistencePending(int itemId) {
        if (itemId <= 0) return false;
        synchronized (lock) {
            return activeItemIds.contains(itemId) || ready.itemsById.containsKey(itemId)
                    || items.containsKey(itemId) || deletingItemIds.contains(itemId);
        }
    }

    public int pendingItems() {
        synchronized (lock) { return items.size() + pendingItemCommands; }
    }

    private static int firstOwner(CPendingState state) {
        if (state instanceof CUser) {
            CharacterSnapshot snapshot = ((CUser) state).latest;
            return snapshot == null ? 0 : snapshot.objectId;
        }
        if (state instanceof CItem) return ((CItem) state).ownerId;
        if (state instanceof ItemCommand) return ((ItemCommand) state).ownerId;
        if (state instanceof CAccount) return accountOrderKey(((CAccount) state).login);
        if (state instanceof DbJob) return ((DbJob) state).ownerId;
        return 0;
    }

    private static int secondOwner(CPendingState state) {
        if (state instanceof CItem item) {
            // Reload can discover a new logical owner without replacing the
            // queued UPDATE image. Keep both lanes until that image is written.
            final ItemSnapshot snapshot = item.latest;
            return snapshot == null || snapshot.ownerId == item.ownerId ? 0 : snapshot.ownerId;
        }
        if (state instanceof ItemCommand) {
            final ItemCommand command = (ItemCommand) state;
            return command.mailboxKey != 0 ? command.mailboxKey : command.previousOwnerId;
        }
        if (state instanceof DbJob) return ((DbJob) state).secondaryOwnerId;
        return 0;
    }

    private static int itemId(CPendingState state) {
        if (state instanceof CItem) return ((CItem) state).id;
        if (state instanceof ItemCommand) return ((ItemCommand) state).snapshot.id;
        return 0;
    }

    private void acquireResources(int owner, int previousOwner, int itemId, CPendingState state) {
        if (owner > 0) activeOwnerIds.add(owner);
        if (previousOwner > 0) activeOwnerIds.add(previousOwner);
        if (state instanceof DbJob) for (int extra : ((DbJob) state).extraOwnerIds)
            activeOwnerIds.add(extra);
        if (itemId > 0) activeItemIds.add(itemId);
    }

    private void releaseResources(int owner, int previousOwner, int itemId, CPendingState state) {
        if (owner > 0) activeOwnerIds.remove(owner);
        if (previousOwner > 0) activeOwnerIds.remove(previousOwner);
        if (state instanceof DbJob) for (int extra : ((DbJob) state).extraOwnerIds)
            activeOwnerIds.remove(extra);
        if (itemId > 0) activeItemIds.remove(itemId);
        ready.refreshReleasedResources(owner, previousOwner, itemId, state);
    }

    private void run() {
        for (;;) {
            final CPendingState selected;
            final CAccount.Snapshot accountSnapshot;
            final CharacterSnapshot characterSnapshot;
            final ItemSnapshot itemSnapshot;
            final long itemMask;
            final long targetVersion;
            final int selectedOwnerId;
            final int selectedPreviousOwnerId;
            final int selectedItemId;
            synchronized (lock) {
                for (;;) {
                    if (stopping && accounts.isEmpty() && characters.isEmpty() && items.isEmpty()
                            && pendingItemCommands == 0 && pendingDbJobs == 0) {
                        signalReadyChange();
                        return;
                    }

                    final long now = System.nanoTime();
                    final CPendingState candidateSelected = ready.select(now);
                    final int candidateOwner = candidateSelected == null ? 0 : firstOwner(candidateSelected);
                    final int candidatePreviousOwner = candidateSelected == null ? 0 : secondOwner(candidateSelected);
                    final int candidateItem = candidateSelected == null ? 0 : itemId(candidateSelected);
                    if (candidateSelected != null) {
                        selected = candidateSelected;
                        selectedOwnerId = candidateOwner;
                        selectedPreviousOwnerId = candidatePreviousOwner;
                        selectedItemId = candidateItem;
                        // Removing the selected head can expose new lane heads.
                        // Index those successors with this claim already visible.
                        acquireResources(selectedOwnerId, selectedPreviousOwnerId, selectedItemId, selected);
                        ready.remove(selected);
                        if (selected instanceof DbJob) activeDbJobs++;
                        selected.queued = false;
                        selected.writing = true;
                        selected.urgent = false;
                        targetVersion = selected.version;
                        if (selected instanceof CAccount) {
                            CAccount state = (CAccount) selected;
                            accountSnapshot = new CAccount.Snapshot(state);
                            state.dirty = 0;
                            characterSnapshot = null;
                            itemSnapshot = null;
                            itemMask = 0L;
                        } else if (selected instanceof CUser) {
                            accountSnapshot = null;
                            characterSnapshot = ((CUser) selected).latest;
                            itemSnapshot = null;
                            itemMask = 0L;
                        } else if (selected instanceof CItem) {
                            CItem state = (CItem) selected;
                            accountSnapshot = null;
                            characterSnapshot = null;
                            itemSnapshot = state.latest;
                            itemMask = state.dirty;
                            state.dirty = 0L;
                        } else if (selected instanceof ItemCommand) {
                            accountSnapshot = null;
                            characterSnapshot = null;
                            itemSnapshot = ((ItemCommand) selected).snapshot;
                            itemMask = 0L;
                        } else {
                            accountSnapshot = null;
                            characterSnapshot = null;
                            itemSnapshot = null;
                            itemMask = 0L;
                        }
                        break;
                    }

                    final long minWait = ready.nextWait(System.nanoTime());
                    try {
                        if (minWait == Long.MAX_VALUE) lock.wait();
                        else TimeUnit.NANOSECONDS.timedWait(lock, minWait);
                    } catch (InterruptedException e) {
                        if (stopping && accounts.isEmpty() && characters.isEmpty() && items.isEmpty()
                                && pendingItemCommands == 0 && pendingDbJobs == 0) return;
                    }
                }
            }

            SQLException failure = null;
            try {
                if (accountSnapshot != null) {
                    CAccount.persist(accountSnapshot);
                } else if (characterSnapshot != null) {
                    CUser.persist(characterSnapshot);
                } else if (selected instanceof DbJob) {
                    DB_JOB_CONTEXT.set((DbJob) selected);
                    try {
                        ((DbJob) selected).task.run();
                    } finally {
                        DB_JOB_CONTEXT.remove();
                    }
                } else if (selected instanceof ItemCommand) {
                    final ItemCommand command = (ItemCommand) selected;
                    if (command.delete && command.mailboxKey != 0) {
                        // Physical letter and its mailbox row commit atomically.
                        // The same ItemCommand reserves both character and mailbox
                        // FIFO lanes; failure retries the SAME durable receipt.
                        CTransaction.persistEconomy(command.auxiliaryReceiptId,
                                command.ownerId, command.mailboxKey,
                                new CTransaction.Mutation[]{
                                    con -> CTransaction.requireTransactionalTables(con, "character_items", "letter",
                                            "characters"),
                                    con -> l1j.server.server.datatables.LetterTable.deleteOwnedItemLetter(
                                            con, itemSnapshot.id, command.mailReceiver),
                                    con -> MySqlCharactersItemStorage.deleteLetterItemSnapshot(
                                            con, itemSnapshot.id, command.ownerId, command.auxiliaryTemplateId)
                                });
                    } else if (command.delete) {
                        MySqlCharactersItemStorage.deleteSnapshot(itemSnapshot);
                    } else {
                        MySqlCharactersItemStorage.storeSnapshot(itemSnapshot);
                        itemSnapshot.finish(((1L << 18) - 1L) & ~8L);
                    }
                } else {
                    CItem.persist(itemSnapshot, itemMask);
                }
            } catch (SQLException e) {
                failure = e;
            } catch (Throwable e) {
                failure = new SQLException("CacheD JDBC execution failed", e);
            }

            String diagnostic = null;
            ArrayList<Runnable> logouts = null;
            Consumer<Throwable> afterReleased = null;
            synchronized (lock) {
                selected.writing = false;
                if (selected instanceof CAccount) {
                    CAccount state = (CAccount) selected;
                    if (failure == null) {
                        state.savedVersion = targetVersion;
                        state.failureCount = 0;
                        if (state.dirty == 0) {
                            accounts.remove(state.login, state);
                        } else {
                            enqueueNext(state, COALESCE_NANOS);
                        }
                    } else {
                        state.dirty |= accountSnapshot.mask;
                        diagnostic = retry(state, failure, "account=" + state.login);
                    }
                } else if (selected instanceof CUser) {
                    CUser state = (CUser) selected;
                    if (failure == null) {
                        state.persisted = characterSnapshot;
                        state.savedVersion = targetVersion;
                        state.failureCount = 0;
                        if (state.version == targetVersion) {
                            characters.remove(state.name, state);
                            // Active session keeps only identity/ownership, not
                            // the large persisted character snapshot.
                            state.latest = null;
                        } else {
                            enqueueNext(state, COALESCE_NANOS);
                        }
                        if (state.objectId > 0) completeOwnerRelease(state.objectId);
                    } else {
                        diagnostic = retry(state, failure, "character=" + state.name);
                    }
                } else if (selected instanceof DbJob) {
                    DbJob state = (DbJob) selected;
                    activeDbJobs--;
                    if (failure == null && state.ownerApplyFailure != null)
                        failure = new SQLException("Committed owner replay failed", state.ownerApplyFailure);
                    if (state == transactionSchemaProbe && schemaProbeStopped) {
                        // The schema probe is a readiness check, not a durable operation.
                        completeDbJob(state);
                    } else if (failure != null && (state.commitConfirmed || state.ownerApplyFailure != null
                            || state.failClosedOnError
                            || hasUnknownTransactionOutcome(failure))) {
                        // Retain BOTH owner lanes; never replay uncertain SQL/memory.
                        diagnostic = quarantineDbJob(state, failure);
                    } else if (failure != null && state.retryOnFailure) {
                        diagnostic = retry(state, failure, "ordered DB owner=" + state.ownerId);
                    } else if (failure == null && state.ownerApplyRequired && !state.ownerApplyFinished) {
                        // Keep the existing job as a FIFO barrier, not a running
                        // worker. CIO acknowledgement retires or quarantines it.
                        state.awaitingOwnerApply = true;
                        state.queued = true;
                        ready.addFirst(state);
                    } else {
                        completeDbJob(state);
                        if (failure != null) {
                            lastFailure = failure;
                            diagnostic = "CacheD async DB request failed";
                        }
                    }
                    afterReleased = state.afterOwnerReleased;
                    state.afterOwnerReleased = null;
                    logouts = takeReadyLogouts(state);
                } else if (selected instanceof CItem) {
                    CItem state = (CItem) selected;
                    if (items.get(state.id) == state) {
                        if (failure == null) {
                            state.savedVersion = targetVersion;
                            state.failureCount = 0;
                            if (state.version == targetVersion) {
                                items.remove(state.id);
                                completeItemWrite(state);
                            } else {
                                enqueueNext(state, COALESCE_NANOS);
                            }
                        } else {
                            state.dirty |= itemMask;
                            diagnostic = retry(state, failure, "item=" + state.id);
                        }
                    }
                } else {
                    ItemCommand state = (ItemCommand) selected;
                    if (failure == null) {
                        state.finished = true;
                        decrementItemCommandOwner(state);
                        state.failureCount = 0;
                        if (state.ownerId <= 0) completeReleasesAfterUnknownItems();
                        else completeOwnerRelease(state.ownerId);
                        if (state.previousOwnerId > 0 && state.previousOwnerId != state.ownerId)
                            completeOwnerRelease(state.previousOwnerId);
                    } else {
                        // Never acknowledge a failed insert/delete. Retry it
                        // ahead of later requests for the same item/owner.
                        diagnostic = retry(state, failure, "item command=" + itemSnapshot.id);
                    }
                }
                ready.reindexResources(selected);
                ready.refresh(selected);
                releaseResources(selectedOwnerId, selectedPreviousOwnerId, selectedItemId, selected);
                signalReadyChange();
            }
            notifyOwnerRelease(afterReleased, failure);
            resumeLogouts(logouts);
            reportFailure(diagnostic, failure);
        }
    }

    private static boolean hasUnknownTransactionOutcome(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof CTransaction.OutcomeUnknownException) return true;
        }
        return false;
    }

    private String retry(CPendingState state, SQLException failure, String identity) {
        lastFailure = failure;
        state.failureCount++;
        long retryMillis = Math.min(5000L,
                250L << Math.min(state.failureCount - 1, 4));
        ready.detachForDeadlineChange(state);
        state.dueAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(retryMillis);
        state.urgent = false;
        if (!state.queued) {
            state.queued = true;
            ready.addFirst(state);
        } else ready.refresh(state);
        if (state.failureCount == 1 || state.failureCount % 20 == 0) {
            return "CacheD save failed; retaining dirty data. " + identity + " retry=" + state.failureCount;
        }
        return null;
    }

    private static void reportFailure(String diagnostic, Throwable failure) {
        if (diagnostic == null) return;
        try {
            LOG.log(Level.SEVERE, diagnostic, failure);
        } catch (RuntimeException ignored) {
            // An installed logging handler cannot strand admitted persistence.
        }
    }

    private void enqueueNext(CPendingState state, long delayNanos) {
        if (!state.queued) {
            state.dueAt = System.nanoTime() + Math.min(delayNanos, MAX_RETRY_NANOS);
            state.queued = true;
            ready.addLast(state);
        }
    }

    public boolean flushAccount(String login, long timeoutMillis) {
        Objects.requireNonNull(login, "login");
        final DbJob executingJob = DB_JOB_CONTEXT.get();
        if (isWorkerThread() && executingJob == null) {
            throw new IllegalStateException("CacheD worker cannot flush itself");
        }
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        synchronized (lock) {
            CAccount state = accounts.get(login);
            if (state == null) return true;
            // No CacheD worker may wait on work queued behind its own job.
            // Return failure instead of falsely confirming unpersisted state.
            if (executingJob != null) return state.savedVersion >= state.version;
            long requiredVersion = state.version;
            state.urgent = true;
            ready.refresh(state);
            signalReadyChange();
            while (state.savedVersion < requiredVersion) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    public boolean flushCharacter(String name, long timeoutMillis) {
        Objects.requireNonNull(name, "name");
        final DbJob executingJob = DB_JOB_CONTEXT.get();
        if (isWorkerThread() && executingJob == null) {
            throw new IllegalStateException("CacheD worker cannot flush itself");
        }
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        synchronized (lock) {
            CUser state = characters.get(name);
            if (state == null) return true;
            // A pending character write cannot run while its DB job holds the
            // owner; reporting success here would allow stale SQL reads.
            if (executingJob != null) return state.savedVersion >= state.version;
            long requiredVersion = state.version;
            state.urgent = true;
            ready.refresh(state);
            signalReadyChange();
            while (state.savedVersion < requiredVersion) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    public boolean flushItems(long timeoutMillis) {
        if (isWorkerThread()) {
            throw new IllegalStateException("CacheD worker cannot flush itself");
        }
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        synchronized (lock) {
            for (CItem state : items.values()) { state.urgent = true; ready.refresh(state); }
            signalReadyChange();
            while (!items.isEmpty() || pendingItemCommands != 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return false;
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    public boolean flushAll(long timeoutMillis) {
        if (isWorkerThread()) {
            throw new IllegalStateException("CacheD worker cannot flush itself");
        }
        final long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        synchronized (lock) { return flushUntil(deadline); }
    }

    /** Keep persistence admission open while the live CIO owners complete
     * durable replay and submit any follow-up writes. */
    public boolean drainBeforeOwnerShutdown(long timeoutMillis) {
        if (isWorkerThread()) {
            throw new IllegalStateException("CacheD worker cannot drain itself");
        }
        final long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        synchronized (lock) {
            stopSchemaProbe();
            return flushUntil(deadline);
        }
    }

    // CacheD.lock held. Read-only readiness checks do not prevent shutdown.
    private void stopSchemaProbe() {
        schemaProbeStopped = true;
        if (!transactionSchemaProbe.queued) return;
        if (!ready.remove(transactionSchemaProbe))
            throw new IllegalStateException("CacheD schema probe reservation lost");
        transactionSchemaProbe.queued = false;
        completeDbJob(transactionSchemaProbe);
    }

    // CacheD.lock held; timedWait releases it so workers and owners can finish.
    private boolean flushUntil(long deadline) {
        for (CAccount state : accounts.values()) { state.urgent = true; ready.refresh(state); }
        for (CUser state : characters.values()) { state.urgent = true; ready.refresh(state); }
        for (CItem state : items.values()) { state.urgent = true; ready.refresh(state); }
        signalReadyChange();
        while (!accounts.isEmpty() || !characters.isEmpty() || !items.isEmpty()
                || pendingItemCommands != 0 || pendingDbJobs != 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return false;
            try {
                TimeUnit.NANOSECONDS.timedWait(lock, remaining);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    public boolean shutdown(long timeoutMillis) {
        if (isWorkerThread()) {
            throw new IllegalStateException("CacheD worker cannot shut itself down");
        }
        final long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        final boolean drained;
        synchronized (lock) {
            accepting = false;
            stopSchemaProbe();
            drained = flushUntil(deadline);
            if (drained) {
                stopping = true;
                signalReadyChange();
            }
        }
        if (!drained) {
            LOG.severe("CacheD shutdown incomplete: unsaved accounts=" + pendingAccounts()
                    + " characters=" + pendingCharacters() + " items=" + pendingItems()
                    + " dbJobs=" + pendingDbJobs());
            return false;
        }
        try {
            for (Thread worker : workers) {
                if (!worker.isAlive()) continue;
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return false;
                TimeUnit.NANOSECONDS.timedJoin(worker, remaining);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        for (Thread worker : workers) {
            if (worker.isAlive()) return false;
        }
        return true;
    }

    public int pendingDbJobs() {
        synchronized (lock) { return pendingDbJobs; }
    }

    public int pendingAccounts() {
        synchronized (lock) {
            return accounts.size();
        }
    }

    public int pendingCharacters() {
        synchronized (lock) {
            return characters.size();
        }
    }

    public Throwable lastFailure() {
        return lastFailure;
    }
}
