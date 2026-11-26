package l1j.server.server.model.character.scheduler;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.network.thread.CIOThreadCallback.WheelTask;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SupportInstance;
import l1j.server.server.model.item.L1ItemId;

import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
public final class CInventoryExpiry {

	private static final Logger LOG = Logger.getLogger(CInventoryExpiry.class.getName());
	private static volatile CInventoryExpiry INSTANCE;

	public static CInventoryExpiry getInstance() {
		if (INSTANCE == null) {
			synchronized (CInventoryExpiry.class) {
				if (INSTANCE == null) INSTANCE = new CInventoryExpiry();
			}
		}
		return INSTANCE;
	}

	private final IndexedExpiryHeap queue = new IndexedExpiryHeap();
    // Per-session index avoids scanning every player during logout. Each entry
    // holds only the session's currently registered tasks, not stale logins.
    private final ConcurrentHashMap<L1PcInstance, ConcurrentHashMap<Integer, ExpiryTask>> byPc =
            new ConcurrentHashMap<>();
    // One logical expiry registration for an item object, including in-flight work.
    private final ConcurrentHashMap<Integer, ExpiryTask> scheduled = new ConcurrentHashMap<>();
	private final AtomicBoolean running = new AtomicBoolean(false);
    // Initial pre-start registration is allowed, as in the original scheduler.
    // Explicit stop closes admission until the next start.
    private volatile boolean accepting = true;
    private final AtomicInteger activeRegistrations = new AtomicInteger();

    private boolean beginRegistration() {
        if (!accepting) return false;
        activeRegistrations.incrementAndGet();
        if (accepting) return true;
        activeRegistrations.decrementAndGet();
        return false;
    }

    private void endRegistration() { activeRegistrations.decrementAndGet(); }
    private static final int MAX_EXPIRY_PER_TICK = 32;
    private static final long TIMER_IDLE_MS = 250L;
    private volatile ExpiryTimer timer;

    private CInventoryExpiry() {}

    /** L1 inventory expiry uses the existing CIO timer; no dedicated threads. */
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        accepting = true;
        final ExpiryTimer next = new ExpiryTimer();
        timer = next;
        if (!CIOThreadCallback.getInstance().scheduleServerTimer(next, TIMER_IDLE_MS)) {
            timer = null;
            running.set(false);
            accepting = false;
            throw new IllegalStateException("Inventory expiry timer admission failed");
        }
    }

    public synchronized void stop() {
        accepting = false;
        running.set(false);
        timer = null;
        boolean interrupted = false;
        while (activeRegistrations.get() != 0) {
            LockSupport.parkNanos(1_000_000L);
            if (Thread.interrupted()) interrupted = true;
        }
        if (interrupted) Thread.currentThread().interrupt();
        queue.clear();
        scheduled.clear();
        byPc.clear();
    }

    private final class ExpiryTimer implements WheelTask {
        @Override public boolean isCancelled() { return timer != this || !running.get(); }
        @Override public void run() {
            if (isCancelled()) return;
            try {
                TimerExpired(this);
            } finally {
                if (!isCancelled() && !CIOThreadCallback.getInstance().scheduleServerTimer(
                        this, queue.nextDelayMs(TIMER_IDLE_MS))) {
                    if (!isCancelled()) LOG.severe("Inventory expiry timer rearm rejected");
                }
            }
        }
    }

	/**
	 * 유저의 모든 기간제 아이템 등록
	 */
	public void register(L1PcInstance pc) {
		if (pc == null || pc.isLogoutStarted() || pc.getInventory() == null) return;

		for (L1ItemInstance item : pc.getInventory().getItems()) {
			if (item != null && item.getEndTime() != null) {
				scheduleItem(pc, item);
			}
		}
	}

	/**
	 * 개별 아이템 스케줄 등록
	 */
    private void indexTask(ExpiryTask task) {
        byPc.compute(task.pc, (pc, index) -> {
            if (index == null) index = new ConcurrentHashMap<>();
            index.put(task.item.getId(), task);
            return index;
        });
    }

    private void unindexTask(ExpiryTask task) {
        byPc.computeIfPresent(task.pc, (pc, index) -> {
            index.remove(task.item.getId(), task);
            return index.isEmpty() ? null : index;
        });
    }

    /** Retire a task only if no replacement/new login owns the same item id. */
    private void removeTask(ExpiryTask task) {
        if (scheduled.remove(task.item.getId(), task)) {
            queue.remove(task);
            unindexTask(task);
        }
    }

    public void scheduleItem(L1PcInstance pc, L1ItemInstance item) {
        if (!accepting || pc == null || item == null || item.getEndTime() == null) return;
        // The PC monitor is the per-session admission barrier. Logout marks
        // the PC destroyed *before* unregister(), so every registration that
        // passed the earlier test must finish publishing before unregister
        // drains its heap/index entries. There is no global admission stall.
        synchronized (pc) {
            if (!accepting || pc.isLogoutStarted() || pc.is무인상점() || item.getEndTime() == null) return;
            if (!beginRegistration()) return;
            try {
                final int itemId = item.getId();
                final long end = item.getEndTime().getTime();
                final int session = pc.getSessionStamp();
                final ExpiryTask published = scheduled.compute(itemId, (key, previous) -> {
                    if (previous != null && previous.pc == pc && previous.item == item
                            && previous.endTime == end && previous.sessionStamp == session) return previous;
                    if (previous != null) {
                        queue.remove(previous);
                        unindexTask(previous);
                    }
                    if (!accepting || pc.isLogoutStarted() || pc.is무인상점() || pc.getSessionStamp() != session)
                        return null;
                    final ExpiryTask task = new ExpiryTask(pc, item, end,
                            Math.max(1000L, end - System.currentTimeMillis()), session);
                    indexTask(task);
                    return task;
                });
                if (published != null && published.heapIndex == -1 && published.pendingPublication) publish(published);
            } finally {
                endRegistration();
            }
        }
    }

    /** Publish only after the scheduled identity is visible; cancellation or
     * replacement between map publication and heap insertion is rechecked. */
    private void publish(ExpiryTask task) {
        task.pendingPublication = false;
        queue.offer(task);
        if (scheduled.get(task.item.getId()) != task) queue.remove(task);
    }

    public void cancelItem(L1PcInstance pc, L1ItemInstance item) {
        if (pc == null || item == null) return;
        // A concurrent scheduleItem cannot publish after an explicit cancel.
        synchronized (pc) {
            final ExpiryTask old = scheduled.get(item.getId());
            if (old != null && old.pc == pc && old.item == item) removeTask(old);
        }
    }

    public void unregister(L1PcInstance pc) {
        if (pc == null) return;
        // Only wait for THIS session's admitted registrations/retries. Holding
        // this lock guarantees a producer cannot publish a task after cleanup;
        // the logout flag was already set by L1PcInstance.logout().
        synchronized (pc) {
            final ConcurrentHashMap<Integer, ExpiryTask> index = byPc.remove(pc);
            if (index == null) return;
            for (ExpiryTask task : index.values()) {
                if (scheduled.remove(task.item.getId(), task)) queue.remove(task);
            }
            index.clear();
        }
    }

    private void retryExpiry(ExpiryTask task, long delayMs) {
        // Retry callbacks can arrive from a different worker AFTER logout;
        // they need the same per-session admission boundary as scheduleItem.
        synchronized (task.pc) {
            if (!accepting || task.pc.isLogoutStarted() || task.pc.is무인상점()
                    || task.pc.getSessionStamp() != task.sessionStamp) {
                removeTask(task);
                return;
            }
            if (!beginRegistration()) {
                removeTask(task);
                return;
            }
            try {
                final ExpiryTask published = scheduled.computeIfPresent(task.item.getId(), (key, current) -> {
                    if (current != task) return current;
                    if (!accepting || task.pc.isLogoutStarted() || task.pc.is무인상점()
                            || task.pc.getSessionStamp() != task.sessionStamp) {
                        unindexTask(task);
                        return null;
                    }
                    final ExpiryTask retry = new ExpiryTask(task.pc, task.item, task.endTime,
                            delayMs, task.sessionStamp);
                    indexTask(retry);
                    return retry;
                });
                if (published != null && published != task && published.pendingPublication) publish(published);
            } finally {
                endRegistration();
            }
        }
    }

    /** Bounded deadline dispatch outside the timer's monitor. Cross-shard
     * mutations still go through the original owner-completion API. */
    private void TimerExpired(ExpiryTimer current) {
        for (int i = 0; i < MAX_EXPIRY_PER_TICK && !current.isCancelled(); i++) {
            final ExpiryTask task = queue.poll();
            if (task == null) break;
            if (scheduled.get(task.item.getId()) != task) continue;
            try {
                CIOThreadCallback.getInstance().completeWarehouseMove(
                        task.pc, task.sessionStamp,
                        () -> processExpiry(task),
                        failure -> {
                            if (failure != null) {
                                if (running.get()) LOG.log(Level.WARNING, "Expiry owner admission failed: " + task.item.getId(), failure);
                                retryExpiry(task, 2000L);
                            }
                        });
            } catch (RuntimeException failure) {
                LOG.log(Level.WARNING, "Expiry owner dispatch rejected: " + task.item.getId(), failure);
                retryExpiry(task, 2000L);
            }
        }
    }

    private void processExpiry(ExpiryTask task) {
        final L1PcInstance pc = task.pc;
        final L1ItemInstance item = task.item;
        if (!running.get() || scheduled.get(item.getId()) != task) return;
        if (pc.getSessionStamp() != task.sessionStamp) {
            removeTask(task);
            return;
        }
        if (pc.isLogoutStarted() || pc.getNetConnection() == null
                || !pc.getNetConnection().isConnected()
                || pc.getInventory().GetByServerID(item.getId()) != item) {
            removeTask(task);
            return;
        }
        final long now = System.currentTimeMillis();
        if (item.getEndTime() == null) {
            removeTask(task);
            return;
        }
        if (item.getEndTime().getTime() > now) {
            rescheduleChangedDeadline(task, now);
            return;
        }
        // These items require more than a delete. Use one durable CacheD
        // receipt for the source debit and reward/related SQL. Never retry an
        // ambiguous COMMIT; L1InventoryTransaction quarantines that session.
        if (item.getItemId() == L1ItemId.MERIN_CONTRACT
                || item.getItemId() == L1ItemId.KILLTON_CONTRACT
                || item.getItemId() == 80500) {
            processAtomicSpecialExpiry(task);
            return;
        }
        final var inventory = pc.getInventory();
        if (!inventory.tryBeginEquipmentChange()) {
            retryExpiry(task, 2000L);
            return;
        }
        try {
            if (!validateExpiryUnderReservation(task)) return;
            // Ordinary expiry has no extra durable reward. Contract/key cases
            // take the atomic receipt path above.
            if (inventory.DeleteItemAmount(item) <= 0) {
                throw new IllegalStateException("Expiry debit was not accepted: " + item.getId());
            }
            handleSpecialItems(pc, item);
            SC_SystemMessage.send(pc, item.getName() + "의 사용시간이 만료 되어 소멸되었습니다.");
            LOG.info("[만료삭제] 캐릭터:" + pc.getName() + ", 아이템:" + item.getName());
            removeTask(task);
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Expiry processing failed: " + item.getId(), failure);
            if (pc.getInventory().GetByServerID(item.getId()) == item) {
                retryExpiry(task, 5000L);
            } else {
                // The debit was already applied; never retry a post-debit reward.
                removeTask(task);
            }
        } finally {
            inventory.endEquipmentChange();
        }
    }

    /** A taken task is no longer in the heap, even if its wall-clock
     * deadline became future again without changing the stored timestamp.
     */
    private void rescheduleChangedDeadline(ExpiryTask task, long now) {
        final java.sql.Timestamp expiry = task.item.getEndTime();
        if (expiry == null) { removeTask(task); return; }
        if (expiry.getTime() == task.endTime)
            retryExpiry(task, Math.max(1000L, expiry.getTime() - now));
        else scheduleItem(task.pc, task.item);
    }

    /** Revalidate after acquiring the same reservation as equipment/receipts. */
    private boolean validateExpiryUnderReservation(ExpiryTask task) {
        final L1PcInstance pc = task.pc;
        final L1ItemInstance item = task.item;
        if (scheduled.get(item.getId()) != task) return false;
        final var session = pc.getNetConnection();
        if (!pc.isItemTransferSession(session, task.sessionStamp)
                || pc.getInventory().GetByServerID(item.getId()) != item
                || item.getEndTime() == null) {
            removeTask(task);
            return false;
        }
        final long now = System.currentTimeMillis();
        if (item.getEndTime().getTime() > now) {
            rescheduleChangedDeadline(task, now);
            return false;
        }
        return true;
    }

    private void processAtomicSpecialExpiry(ExpiryTask task) {
        final L1PcInstance pc = task.pc;
        final L1ItemInstance item = task.item;
        final int template = item.getItemId();
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
            if (!validateExpiryUnderReservation(task)) return;
            if (!tx.debit(item, item.getCount()))
                throw new IllegalStateException("Expired special item cannot be debited: " + item.getId());
            if (template == L1ItemId.MERIN_CONTRACT || template == L1ItemId.KILLTON_CONTRACT) {
                final int reward = template == L1ItemId.MERIN_CONTRACT
                        ? L1ItemId.MERIN_PIPE : L1ItemId.KILLTON_PIPE;
                if (tx.grant(reward, 1) == null)
                    throw new IllegalStateException("Expired contract reward unavailable: " + reward);
            } else {
                // This key's auxiliary row is deleted in the SAME SQL commit
                // as the character_items row, not in a separate connection.
                final int keyId = item.getKeyId();
                tx.requireTables("item_key_boss");
                tx.sql(con -> {
                    try (java.sql.PreparedStatement ps = con.prepareStatement(
                            "DELETE FROM item_key_boss WHERE key_id=?")) {
                        ps.setInt(1, keyId);
                        ps.executeUpdate();
                    }
                });
            }
            tx.onFailure(() -> retryExpiry(task, 5000L));
            tx.commit(() -> {
                removeTask(task);
                if (template != 80500) SC_ServerMessage.send(pc, 1823);
                SC_SystemMessage.send(pc, item.getName() + "의 사용시간이 만료 되어 소멸되었습니다.");
                LOG.info("[만료삭제] 캐릭터:" + pc.getName() + ", 아이템:" + item.getName());
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "Expired special item transaction rejected: " + item.getId(), failure);
            retryExpiry(task, 5000L);
        }
    }

	private void handleSpecialItems(L1PcInstance pc, L1ItemInstance item) {
		int itemId = item.getItemId();

		switch (itemId) {
        case 3000048:
            SC_ServerMessage.send(pc, 1823);
            break;
		default:
			cleanupDollAndSupport(pc, item);
			break;
		}
	}

	private void cleanupDollAndSupport(L1PcInstance pc, L1ItemInstance item) {
		// 인형 정리
		if (item.getItemId() >= 30022 && item.getItemId() <= 30025) {
			for (L1DollInstance doll : pc.getDollList().values()) {
				if (doll != null && item.getId() == doll.getItemObjId()) {
					doll.deleteDoll(false);
					SC_SkillIconGFX.send(pc, 56, 0);
				}
			}
		}
		// 서포트 정리
		int itemId = item.getItemId();
		if (itemId == 210095 || (itemId >= 2100950 && itemId <= 2100953)) {
			for (L1SupportInstance support : pc.getSupportList().values()) {
				if (support != null && item.getId() == support.getItemObjId()) {
					SC_SkillSound.broadcast(support, support.getId(), 5936);
					support.deleteSupport();
					SC_OwnCharStatus.send(pc);
					pc.setSupporting(false);
				}
			}
		}
	}

    /** Indexed deadline store with immediate O(log N) cancellation. The shared CIO timer drives it. */
    private static final class IndexedExpiryHeap {
        private final ArrayList<ExpiryTask> heap = new ArrayList<>();

        synchronized void offer(ExpiryTask task) {
            task.heapIndex = heap.size();
            heap.add(task);
            siftUp(task.heapIndex);
        }

        synchronized boolean remove(ExpiryTask task) {
            final int index = task.heapIndex;
            if (index < 0 || index >= heap.size() || heap.get(index) != task)
                return false;
            removeAt(index);
            return true;
        }

        synchronized int size() { return heap.size(); }

        synchronized ExpiryTask poll() {
            if (heap.isEmpty() || heap.get(0).expiryTime > System.currentTimeMillis()) return null;
            final ExpiryTask first = heap.get(0);
            removeAt(0);
            return first;
        }

        synchronized long nextDelayMs(long idleMs) {
            if (heap.isEmpty()) return idleMs;
            return Math.max(1L, Math.min(idleMs, heap.get(0).expiryTime - System.currentTimeMillis()));
        }

        synchronized void clear() {
            for (int i = 0; i < heap.size(); i++) heap.get(i).heapIndex = -1;
            heap.clear();
        }

        private void removeAt(int index) {
            final int lastIndex = heap.size() - 1;
            final ExpiryTask removed = heap.get(index);
            final ExpiryTask last = heap.remove(lastIndex);
            removed.heapIndex = -1;
            if (index == lastIndex) return;
            heap.set(index, last);
            last.heapIndex = index;
            if (index > 0 && heap.get((index - 1) >>> 1).expiryTime > last.expiryTime)
                siftUp(index);
            else siftDown(index);
        }

        private void siftUp(int index) {
            final ExpiryTask entry = heap.get(index);
            while (index > 0) {
                final int parent = (index - 1) >>> 1;
                final ExpiryTask p = heap.get(parent);
                if (p.expiryTime <= entry.expiryTime) break;
                heap.set(index, p);
                p.heapIndex = index;
                index = parent;
            }
            heap.set(index, entry);
            entry.heapIndex = index;
        }

        private void siftDown(int index) {
            final int size = heap.size();
            final ExpiryTask entry = heap.get(index);
            final int half = size >>> 1;
            while (index < half) {
                int child = (index << 1) + 1;
                ExpiryTask c = heap.get(child);
                if (child + 1 < size && heap.get(child + 1).expiryTime < c.expiryTime)
                    c = heap.get(++child);
                if (entry.expiryTime <= c.expiryTime) break;
                heap.set(index, c);
                c.heapIndex = index;
                index = child;
            }
            heap.set(index, entry);
            entry.heapIndex = index;
        }
    }

	private static final class ExpiryTask {
		final L1PcInstance pc;
		final L1ItemInstance item;
        final long expiryTime;
        final long endTime;
        final int sessionStamp;
        int heapIndex = -1;
        boolean pendingPublication = true;

        ExpiryTask(L1PcInstance pc, L1ItemInstance item, long endTime, long delayMs) {
            this(pc, item, endTime, delayMs, pc.getSessionStamp());
        }

        ExpiryTask(L1PcInstance pc, L1ItemInstance item, long endTime,
                   long delayMs, int sessionStamp) {
            this.pc = pc;
            this.item = item;
            this.endTime = endTime;
            this.sessionStamp = sessionStamp;
            final long now = System.currentTimeMillis();
            this.expiryTime = delayMs > Long.MAX_VALUE - now
                    ? Long.MAX_VALUE : now + delayMs;
        }

	}
}